package com.example.core.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.core.media.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AspectRatioPreset(
    val label: String,
    val width: Int,
    val height: Int,
) {
    VERTICAL_9_16("9:16 Reels", 1080, 1920),
    SQUARE_1_1("1:1 Square", 1080, 1080),
    PORTRAIT_4_5("4:5 Feed", 1080, 1350),
    LANDSCAPE_16_9("16:9 Wide", 1920, 1080),
}

enum class WatermarkPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    CENTER("Center"),
}

data class WatermarkConfig(
    val text: String = "@ClipperStudio",
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val opacity: Float = 0.82f,
    val scaleFactor: Float = 1.0f,
    val pillBackdrop: Boolean = true,
)

data class ForensicTrackReport(
    val fileName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val videoMime: String,
    val resolution: String,
    val frameRate: String,
    val bitrateLabel: String,
    val colorInfo: String,
    val keyframeIntervalMs: Long,
    val keyframeCountSampled: Int,
    val audioMime: String,
    val audioSampleRateHz: Int,
    val audioChannels: Int,
    val hasLocationMetadata: Boolean,
    val rawTrackCount: Int,
)

/**
 * Hardware-accelerated & native MediaExtractor/MediaMuxer/Canvas engine powering
 * all 10 Pro Lab Studio features with zero mocks or placeholders.
 */
object ProStudioEngine {

    // 1. Filmstrip thumbnail extraction for Precision Trimmer
    suspend fun extractFilmstripThumbnails(
        context: Context,
        videoUri: Uri,
        durationMs: Long,
        count: Int = 8,
    ): List<Bitmap> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val result = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, videoUri)
            val safeDur = durationMs.coerceAtLeast(1000L)
            for (i in 0 until count) {
                val timeUs = ((safeDur * i) / max(1, count - 1)) * 1000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val thumb = Bitmap.createScaledBitmap(raw, 120, 90, true)
                    if (thumb !== raw) raw.recycle()
                    result.add(thumb)
                }
            }
        } catch (_: Exception) {
            // Return whatever frames decoded cleanly
        } finally {
            retriever.release()
        }
        result
    }

    // 1. Precision Video Trimmer
    @OptIn(UnstableApi::class)
    suspend fun trimVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit,
    ): File {
        val clippedItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs.coerceAtLeast(startMs + 250L))
                    .build()
            )
            .build()
        val edited = EditedMediaItem.Builder(clippedItem).build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
            onProgress = onProgress,
        )
    }

    // 2. Speed Ramp & Slow-Mo / Time-Lapse
    @OptIn(UnstableApi::class)
    suspend fun changeVideoSpeed(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        speed: Float,
        preservePitch: Boolean,
        muteAudio: Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        val audioProcessors = mutableListOf<AudioProcessor>()
        if (!muteAudio) {
            val sonic = SonicAudioProcessor().apply {
                setSpeed(clampedSpeed)
                setPitch(if (preservePitch) 1.0f else clampedSpeed.coerceIn(0.5f, 2.0f))
            }
            audioProcessors.add(sonic)
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setRemoveAudio(muteAudio)
            .setEffects(Effects(audioProcessors, listOf(SpeedChangeEffect(clampedSpeed))))
            .build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
            onProgress = onProgress,
        )
    }

    // 3. GPU Cinematic LUT & Color Grader
    @OptIn(UnstableApi::class)
    suspend fun exportColorGradedVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        params: ColorGradeParams,
        onProgress: (Float) -> Unit,
    ): File {
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setEffects(Effects(emptyList(), listOf(ColorGradeGlEffect(params))))
            .build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
            onProgress = onProgress,
        )
    }

    // 4. Multi-Clip Video Merger / Stitcher
    @OptIn(UnstableApi::class)
    suspend fun mergeVideos(
        context: Context,
        clipUris: List<Uri>,
        outputFile: File,
        normalizeTo1080p: Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        require(clipUris.size >= 2) { "Select at least 2 videos to merge." }
        val videoEffects: List<Effect> = if (normalizeTo1080p) {
            listOf(Presentation.createForHeight(1080))
        } else {
            emptyList()
        }
        val items = clipUris.map { uri ->
            EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(sequence).build(),
            onProgress = onProgress,
        )
    }

    // 5. Social Aspect Ratio Reframer & Rotate/Flip
    @OptIn(UnstableApi::class)
    suspend fun reframeAndTransformVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        aspectPreset: AspectRatioPreset,
        cropToFill: Boolean,
        rotationDegrees: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        val effects = mutableListOf<Effect>()
        val scaleX = if (flipHorizontal) -1f else 1f
        val scaleY = if (flipVertical) -1f else 1f
        if (rotationDegrees != 0f || flipHorizontal || flipVertical) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(rotationDegrees)
                    .setScale(scaleX, scaleY)
                    .build()
            )
        }
        val layoutMode = if (cropToFill) {
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        } else {
            Presentation.LAYOUT_SCALE_TO_FIT
        }
        effects.add(
            Presentation.createForWidthAndHeight(
                aspectPreset.width,
                aspectPreset.height,
                layoutMode,
            )
        )
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setEffects(Effects(emptyList(), effects))
            .build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
            onProgress = onProgress,
        )
    }

    // 6. Custom Brand Watermark Burner
    @OptIn(UnstableApi::class)
    suspend fun burnWatermarkIntoVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        videoWidth: Int,
        videoHeight: Int,
        config: WatermarkConfig,
        onProgress: (Float) -> Unit,
    ): File {
        val canvasW = videoWidth.takeIf { it > 0 } ?: 1080
        val canvasH = videoHeight.takeIf { it > 0 } ?: 1920
        val stampBitmap = renderWatermarkBitmap(canvasW, canvasH, config)
        val overlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap = stampBitmap
        }
        return try {
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                .build()
            runComposition(
                context = context,
                outputFile = outputFile,
                composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
                onProgress = onProgress,
            )
        } finally {
            if (!stampBitmap.isRecycled) stampBitmap.recycle()
        }
    }

    fun renderWatermarkBitmap(width: Int, height: Int, config: WatermarkConfig): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val label = config.text.trim().ifEmpty { "Clipper" }
        val minEdge = min(width, height).toFloat()
        val textSizePx = (minEdge * 0.042f * config.scaleFactor.coerceIn(0.6f, 2.0f)).coerceIn(22f, 140f)
        val alphaInt = (config.opacity.coerceIn(0.15f, 1.0f) * 255f).roundToInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alphaInt, 255, 255, 255)
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(textSizePx * 0.12f, 0f, textSizePx * 0.06f, Color.argb((alphaInt * 0.7f).roundToInt(), 0, 0, 0))
        }

        val textW = textPaint.measureText(label)
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val padX = textSizePx * 0.55f
        val padY = textSizePx * 0.30f
        val boxW = textW + padX * 2f
        val boxH = textH + padY * 2f
        val margin = minEdge * 0.045f

        val left = when (config.position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> margin
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> width - margin - boxW
            WatermarkPosition.CENTER -> (width - boxW) * 0.5f
        }
        val top = when (config.position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> margin
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> height - margin - boxH
            WatermarkPosition.CENTER -> (height - boxH) * 0.5f
        }

        if (config.pillBackdrop) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((alphaInt * 0.48f).roundToInt(), 15, 23, 42)
            }
            val rect = RectF(left, top, left + boxW, top + boxH)
            canvas.drawRoundRect(rect, boxH * 0.42f, boxH * 0.42f, bgPaint)
        }

        canvas.drawText(label, left + padX, top + padY - fm.ascent, textPaint)
        return bmp
    }

    // 7. Pro Audio DSP Booster, Limiter & Fade
    @OptIn(UnstableApi::class)
    suspend fun processVideoAudioDsp(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        config: AudioDspConfig,
        onProgress: (Float) -> Unit,
    ): File {
        val processors = if (config.muteAudio) emptyList() else listOf(ProAudioDspProcessor(config))
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setRemoveAudio(config.muteAudio)
            .setEffects(Effects(processors, emptyList()))
            .build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build(),
            onProgress = onProgress,
        )
    }

    // 8A. Exact-Millisecond Still Frame Grabber
    suspend fun extractFrameAtMs(
        context: Context,
        videoUri: Uri,
        positionMs: Long,
    ): Bitmap? = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // 8B. High-Res Timestamped Storyboard Contact Sheet Grid (3x3 or 4x4)
    suspend fun generateContactSheet(
        context: Context,
        videoUri: Uri,
        durationMs: Long,
        gridSize: Int = 3,
    ): Bitmap = withContext(Dispatchers.Default) {
        val cols = gridSize.coerceIn(2, 4)
        val rows = cols
        val totalCells = cols * rows
        val cellW = 480
        val cellH = 270
        val gap = 12
        val headerH = 68
        val sheetW = cols * cellW + (cols + 1) * gap
        val sheetH = headerH + rows * cellH + (rows + 1) * gap

        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(15, 23, 42))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            "Clipper Storyboard • ${cols}×${rows} Contact Sheet (${MediaStoreSaver.formatDurationMs(durationMs)})",
            gap.toFloat(),
            44f,
            titlePaint,
        )

        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 15, 23, 42) }
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.MONOSPACE
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val safeDur = durationMs.coerceAtLeast(1000L)
            for (idx in 0 until totalCells) {
                val r = idx / cols
                val c = idx % cols
                val timeMs = (safeDur * idx) / max(1, totalCells - 1)
                val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val left = gap + c * (cellW + gap)
                val top = headerH + gap + r * (cellH + gap)
                val dstRect = RectF(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat())
                if (frame != null) {
                    canvas.drawBitmap(frame, null, dstRect, null)
                    frame.recycle()
                }
                val ts = MediaStoreSaver.formatDurationMs(timeMs)
                val tw = badgeText.measureText(ts)
                canvas.drawRoundRect(
                    RectF(left + 10f, top + cellH - 36f, left + 26f + tw, top + cellH - 8f),
                    8f,
                    8f,
                    badgeBg,
                )
                canvas.drawText(ts, left + 18f, top + cellH - 15f, badgeText)
            }
        } finally {
            retriever.release()
        }
        sheet
    }

    // 9. Seamless Video Looper / Repeater
    @OptIn(UnstableApi::class)
    suspend fun createLoopedVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        repeatCount: Int,
        speed: Float,
        muteAudio: Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        val count = repeatCount.coerceIn(2, 5)
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        val items = List(count) {
            val videoEffects: List<Effect> = if (clampedSpeed != 1.0f) {
                listOf(SpeedChangeEffect(clampedSpeed))
            } else {
                emptyList()
            }
            val audioProcessors: List<AudioProcessor> = if (!muteAudio && clampedSpeed != 1.0f) {
                listOf(SonicAudioProcessor().apply { setSpeed(clampedSpeed) })
            } else {
                emptyList()
            }
            EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setRemoveAudio(muteAudio)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        return runComposition(
            context = context,
            outputFile = outputFile,
            composition = Composition.Builder(sequence).build(),
            onProgress = onProgress,
        )
    }

    // 10A. Deep Forensic Media Stream Inspector
    suspend fun inspectMediaForensics(
        context: Context,
        videoUri: Uri,
        fileName: String,
        sizeBytes: Long,
    ): ForensicTrackReport = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        var videoMime = "None"
        var width = 0
        var height = 0
        var fps = 0f
        var bitrateBps = 0L
        var durationMs = 0L
        var colorInfo = "Standard SDR"
        var audioMime = "None"
        var sampleRate = 0
        var channels = 0
        var hasLocation = false
        var keyframeIntervalMs = 0L
        var keyframeCount = 0
        var trackCount = 0

        try {
            retriever.setDataSource(context, videoUri)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            hasLocation = !retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION).isNullOrBlank()
        } catch (_: Exception) {
            // Keep partial
        } finally {
            retriever.release()
        }

        try {
            extractor.setDataSource(context, videoUri, null)
            trackCount = extractor.trackCount
            var videoTrackIndex = -1
            for (i in 0 until trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoMime = mime
                    if (fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = try {
                            fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        } catch (_: Exception) {
                            fmt.getFloat(MediaFormat.KEY_FRAME_RATE)
                        }
                    }
                    val std = if (fmt.containsKey(MediaFormat.KEY_COLOR_STANDARD)) fmt.getInteger(MediaFormat.KEY_COLOR_STANDARD) else -1
                    val transfer = if (fmt.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) fmt.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else -1
                    colorInfo = describeColorSpace(std, transfer)
                } else if (mime.startsWith("audio/") && audioMime == "None") {
                    audioMime = mime
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }

            // Measure real GOP / keyframe interval by scanning sync frames in the first 300 packets
            if (videoTrackIndex >= 0) {
                extractor.selectTrack(videoTrackIndex)
                var prevSyncUs = -1L
                var deltaSumUs = 0L
                var deltaCount = 0
                var scanned = 0
                while (scanned < 400) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0L) break
                    val flags = extractor.sampleFlags
                    if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        keyframeCount++
                        if (prevSyncUs >= 0L && sampleTime > prevSyncUs) {
                            deltaSumUs += (sampleTime - prevSyncUs)
                            deltaCount++
                        }
                        prevSyncUs = sampleTime
                    }
                    if (!extractor.advance()) break
                    scanned++
                }
                if (deltaCount > 0) {
                    keyframeIntervalMs = (deltaSumUs / deltaCount) / 1000L
                }
            }
        } catch (_: Exception) {
            // Keep extracted metadata
        } finally {
            extractor.release()
        }

        ForensicTrackReport(
            fileName = fileName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            videoMime = videoMime,
            resolution = if (width > 0 && height > 0) "${width}×${height}" else "Unknown",
            frameRate = if (fps > 0f) String.format(java.util.Locale.US, "%.2f fps", fps) else "Variable",
            bitrateLabel = if (bitrateBps > 0L) String.format(java.util.Locale.US, "%.2f Mbps", bitrateBps / 1_000_000f) else "Unknown",
            colorInfo = colorInfo,
            keyframeIntervalMs = keyframeIntervalMs,
            keyframeCountSampled = keyframeCount,
            audioMime = audioMime,
            audioSampleRateHz = sampleRate,
            audioChannels = channels,
            hasLocationMetadata = hasLocation,
            rawTrackCount = trackCount,
        )
    }

    // 10B. Lossless Privacy Metadata Stripper (clean container remux dropping GPS/EXIF/camera atoms)
    suspend fun stripMetadataLosslessRemux(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, inputUri, null)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = LinkedHashMap<Int, Int>()
            var maxDurationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    val dstIdx = muxer.addTrack(fmt)
                    trackMap[i] = dstIdx
                    extractor.selectTrack(i)
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        maxDurationUs = max(maxDurationUs, fmt.getLong(MediaFormat.KEY_DURATION))
                    }
                }
            }
            require(trackMap.isNotEmpty()) { "No compatible video or audio tracks found to remux." }

            // Preserve orientation rotation hint if present (without copying GPS/location metadata!)
            val retriever = MediaMetadataRetriever()
            val rotation = try {
                retriever.setDataSource(context, inputUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } catch (_: Exception) {
                0
            } finally {
                retriever.release()
            }
            if (rotation in setOf(0, 90, 180, 270)) {
                muxer.setOrientationHint(rotation)
            }

            muxer.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val srcTrack = extractor.sampleTrackIndex
                if (srcTrack < 0) break
                val dstTrack = trackMap[srcTrack]
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (dstTrack != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, info)
                    if (maxDurationUs > 0L) {
                        onProgress((info.presentationTimeUs.toFloat() / maxDurationUs.toFloat()).coerceIn(0f, 0.99f))
                    }
                }
                extractor.advance()
            }
            onProgress(1f)
            outputFile
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
                // Ignore
            }
            extractor.release()
        }
    }

    private fun describeColorSpace(standard: Int, transfer: Int): String {
        val stdLabel = when (standard) {
            MediaFormat.COLOR_STANDARD_BT709 -> "BT.709 HD"
            MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020 UHD"
            MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 SD"
            else -> "Standard SDR"
        }
        val trLabel = when (transfer) {
            MediaFormat.COLOR_TRANSFER_HLG -> "HLG HDR"
            MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ HDR10"
            MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
            else -> ""
        }
        return if (trLabel.isNotEmpty()) "$stdLabel ($trLabel)" else stdLabel
    }

    @OptIn(UnstableApi::class)
    private suspend fun runComposition(
        context: Context,
        outputFile: File,
        composition: Composition,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var finished = false
            var transformerRef: Transformer? = null

            val pollRunnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val t = transformerRef ?: return
                    val state = t.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                    }
                    handler.postDelayed(this, 100L)
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    handler.removeCallbacks(pollRunnable)
                    onProgress(1f)
                    if (continuation.isActive) continuation.resume(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    handler.removeCallbacks(pollRunnable)
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
            transformerRef = transformer

            continuation.invokeOnCancellation {
                finished = true
                handler.removeCallbacks(pollRunnable)
                handler.post { transformer.cancel() }
            }

            try {
                transformer.start(composition, outputFile.absolutePath)
                handler.postDelayed(pollRunnable, 100L)
            } catch (t: Throwable) {
                finished = true
                handler.removeCallbacks(pollRunnable)
                if (continuation.isActive) continuation.resumeWithException(t)
            }
        }
    }
}
