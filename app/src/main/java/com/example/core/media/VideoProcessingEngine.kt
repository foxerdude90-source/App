package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.core.captions.CaptionBitmapRenderer
import com.example.core.captions.CaptionCue
import com.example.core.captions.CaptionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class VideoResolutionPreset(val label: String, val targetHeight: Int?) {
    ORIGINAL("Original", null),
    HD_1080("1080p", 1080),
    HD_720("720p", 720),
    SD_480("480p", 480),
    SD_360("360p", 360),
}

enum class VideoFpsPreset(val label: String, val fps: Float?) {
    ORIGINAL("Original", null),
    FPS_60("60 fps", 60f),
    FPS_30("30 fps", 30f),
    FPS_24("24 fps", 24f),
}

data class VideoTranscodeConfig(
    val resolution: VideoResolutionPreset = VideoResolutionPreset.HD_720,
    val frameRate: VideoFpsPreset = VideoFpsPreset.ORIGINAL,
    val bitrateMbps: Float = 3.5f,
    val removeAudio: Boolean = false,
)

/**
 * Real hardware-accelerated Media3 Transformer & native MediaExtractor/MediaMuxer engine
 * for video compression, quality/FPS transcoding, lossless audio extraction, and caption burn-in.
 */
object VideoProcessingEngine {

    @OptIn(UnstableApi::class)
    suspend fun transcodeVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        config: VideoTranscodeConfig,
        onProgress: (Float) -> Unit,
    ): File = runTransformer(
        context = context,
        outputFile = outputFile,
        onProgress = onProgress,
        configureBuilder = { builder ->
            val videoSettings = VideoEncoderSettings.Builder()
                .setBitrate((config.bitrateMbps * 1_000_000f).toInt().coerceAtLeast(350_000))
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setEnableFallback(true)
                .build()
            builder
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
        },
        buildEditedMediaItem = {
            val videoEffects = mutableListOf<Effect>()
            config.resolution.targetHeight?.let { h ->
                videoEffects.add(Presentation.createForHeight(h))
            }
            config.frameRate.fps?.let { fps ->
                videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(fps))
            }
            EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setRemoveAudio(config.removeAudio)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        },
    )

    /**
     * Extracts audio track to M4A using native MediaExtractor + MediaMuxer for lossless speed,
     * falling back to Media3 Transformer AAC transcode when the source audio codec is not MP4-muxable.
     */
    @OptIn(UnstableApi::class)
    suspend fun extractAudioToM4a(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): File {
        val fastRemuxWorked = withContext(Dispatchers.IO) {
            tryFastAudioRemux(context, inputUri, outputFile, onProgress)
        }
        if (fastRemuxWorked && outputFile.exists() && outputFile.length() > 0L) {
            onProgress(1f)
            return outputFile
        }
        // Fallback: hardware transcode audio track to AAC in MP4/M4A container via Media3 Transformer
        if (outputFile.exists()) outputFile.delete()
        return runTransformer(
            context = context,
            outputFile = outputFile,
            onProgress = onProgress,
            configureBuilder = { builder ->
                builder.setAudioMimeType(MimeTypes.AUDIO_AAC)
            },
            buildEditedMediaItem = {
                EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                    .setRemoveVideo(true)
                    .build()
            },
        )
    }

    /**
     * Burns styled multi-color captions into a video using Media3 OverlayEffect + BitmapOverlay.
     */
    @OptIn(UnstableApi::class)
    suspend fun burnCaptionsIntoVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        cues: List<CaptionCue>,
        style: CaptionStyle,
        videoWidth: Int,
        videoHeight: Int,
        onProgress: (Float) -> Unit,
    ): File {
        val canvasWidth = videoWidth.takeIf { it > 0 } ?: 1080
        val canvasHeight = videoHeight.takeIf { it > 0 } ?: 1920
        val transparentBlank = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bitmapCache = HashMap<String, Bitmap>()

        val captionOverlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                val timeMs = presentationTimeUs / 1000L
                val activeCue = cues.firstOrNull { timeMs in it.startMs..it.endMs }
                    ?: return transparentBlank
                val cacheKey = "${activeCue.id}_${activeCue.text}_${style.hashCode()}"
                return bitmapCache.getOrPut(cacheKey) {
                    CaptionBitmapRenderer.renderCaptionOverlayBitmap(
                        context = context,
                        text = activeCue.text,
                        style = style,
                        targetWidthPx = canvasWidth,
                        targetHeightPx = canvasHeight,
                    )
                }
            }
        }

        return try {
            runTransformer(
                context = context,
                outputFile = outputFile,
                onProgress = onProgress,
                configureBuilder = { builder ->
                    builder
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                },
                buildEditedMediaItem = {
                    val overlayEffect = OverlayEffect(listOf(captionOverlay))
                    EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                        .setEffects(Effects(emptyList(), listOf(overlayEffect)))
                        .build()
                },
            )
        } finally {
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            if (!transparentBlank.isRecycled) transparentBlank.recycle()
        }
    }

    private fun tryFastAudioRemux(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, inputUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    // MP4/M4A muxer natively accepts AAC (audio/mp4a-latm)
                    if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrack, buffer, info)
                if (durationUs > 0L) {
                    onProgress((info.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 0.99f))
                }
                extractor.advance()
            }
            true
        } catch (_: Exception) {
            if (outputFile.exists()) outputFile.delete()
            false
        } finally {
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {
                // Ignore muxer stop errors on partial failure
            }
            extractor.release()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformer(
        context: Context,
        outputFile: File,
        onProgress: (Float) -> Unit,
        configureBuilder: (Transformer.Builder) -> Transformer.Builder,
        buildEditedMediaItem: () -> EditedMediaItem,
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
                    handler.postDelayed(this, 120L)
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    handler.removeCallbacks(pollRunnable)
                    onProgress(1f)
                    if (continuation.isActive) {
                        continuation.resume(outputFile)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    handler.removeCallbacks(pollRunnable)
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            }

            val transformer = configureBuilder(Transformer.Builder(context))
                .addListener(listener)
                .build()
            transformerRef = transformer

            continuation.invokeOnCancellation {
                finished = true
                handler.removeCallbacks(pollRunnable)
                handler.post { transformer.cancel() }
            }

            try {
                transformer.start(buildEditedMediaItem(), outputFile.absolutePath)
                handler.postDelayed(pollRunnable, 120L)
            } catch (t: Throwable) {
                finished = true
                handler.removeCallbacks(pollRunnable)
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                }
            }
        }
    }
}
