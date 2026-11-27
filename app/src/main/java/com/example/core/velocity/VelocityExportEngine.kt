package com.example.core.velocity

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Hardware-accelerated Media3 Transformer Velocity Exporter.
 *
 * Applies:
 *  - Frame-accurate Video Trim (`ClippingConfiguration`)
 *  - Optional Aspect Ratio Crop & Fine Crop Zoom (`Crop` + `Presentation`)
 *  - Continuous C1-smooth variable-speed time-remapping (`SpeedChangeEffect(SpeedProvider)`)
 *  - Synchronized trimmed external Music Audio track (`Composition` dual-sequence)
 *    OR pitch-coherent variable-speed original audio when no music track is attached.
 */
object VelocityExportEngine {

    @OptIn(UnstableApi::class)
    suspend fun exportVelocityVideo(
        context: Context,
        config: VelocityExportConfig,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val trimStartMs = config.videoTrimStartMs.coerceAtLeast(0L)
        val trimEndMs = config.videoTrimEndMs.coerceAtLeast(trimStartMs + 300L)
        val trimmedSourceMs = trimEndMs - trimStartMs

        val lut = VelocityCurveMath.buildTimeRemapLut(
            keyframes = config.keyframes,
            trimmedSourceDurationMs = trimmedSourceMs,
        )
        val predictedOutputMs = lut.totalOutputDurationMs.coerceAtLeast(300L)

        val speedProvider = SmoothVelocitySpeedProvider(
            keyframes = config.keyframes,
            trimmedDurationUs = trimmedSourceMs * 1000L,
            stepUs = 33_333L, // 30 speed updates/sec for silky-smooth frame-accurate velocity ramps
        )

        val videoEffects = mutableListOf<Effect>()
        val zoom = config.cropZoom.coerceIn(1.0f, 2.0f)
        if (zoom > 1.01f) {
            val halfSpan = 1.0f / zoom
            videoEffects.add(Crop(-halfSpan, halfSpan, -halfSpan, halfSpan))
        }
        config.aspectCrop.aspectRatio?.let { ratio ->
            videoEffects.add(
                Presentation.createForAspectRatio(
                    ratio,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                ),
            )
        }
        videoEffects.add(SpeedChangeEffect(speedProvider))

        val videoMediaItem = MediaItem.Builder()
            .setUri(config.videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build(),
            )
            .build()

        val hasExternalMusic = config.audioUri != null
        val videoAudioProcessors: List<AudioProcessor> = if (!hasExternalMusic && config.keepOriginalVideoAudioIfNoMusic) {
            listOf(SpeedChangingAudioProcessor(speedProvider))
        } else {
            emptyList()
        }

        val editedVideo = EditedMediaItem.Builder(videoMediaItem)
            .setRemoveAudio(hasExternalMusic || !config.keepOriginalVideoAudioIfNoMusic)
            .setEffects(Effects(videoAudioProcessors, videoEffects))
            .build()

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence.Builder(listOf(editedVideo)).build())

        if (config.audioUri != null) {
            val audioStart = config.audioTrimStartMs.coerceAtLeast(0L)
            val maxAudioEnd = if (config.audioDurationMs > 0L) config.audioDurationMs else (audioStart + predictedOutputMs)
            val desiredAudioEnd = (audioStart + predictedOutputMs).coerceAtMost(maxAudioEnd)
            val safeAudioEnd = desiredAudioEnd.coerceAtLeast(audioStart + 250L)

            val musicItem = MediaItem.Builder()
                .setUri(config.audioUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioStart)
                        .setEndPositionMs(safeAudioEnd)
                        .build(),
                )
                .build()

            val editedMusic = EditedMediaItem.Builder(musicItem)
                .setRemoveVideo(true)
                .build()

            sequences.add(EditedMediaItemSequence.Builder(listOf(editedMusic)).build())
        }

        val composition = Composition.Builder(sequences).build()
        runTransformerComposition(
            context = context,
            composition = composition,
            outputFile = outputFile,
            onProgress = onProgress,
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerComposition(
        context: Context,
        composition: Composition,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): File = suspendCancellableCoroutine { continuation ->
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

/**
 * Media3 [SpeedProvider] backed by [VelocityCurveMath.evaluateSplineSpeed] sampled at fine 33ms
 * intervals for buttery-smooth continuous speed transitions across the entire trimmed video.
 */
@UnstableApi
class SmoothVelocitySpeedProvider(
    keyframes: List<VelocityKeyframe>,
    private val trimmedDurationUs: Long,
    private val stepUs: Long = 33_333L,
) : SpeedProvider {

    private val sanitized = VelocityCurveMath.sanitizeKeyframes(keyframes)
    private val safeDurationUs = trimmedDurationUs.coerceAtLeast(250_000L)

    override fun getSpeed(timeUs: Long): Float {
        val u = (timeUs.coerceIn(0L, safeDurationUs).toDouble() / safeDurationUs.toDouble()).toFloat()
        return VelocityCurveMath.evaluateSplineSpeed(sanitized, u)
            .coerceIn(VelocityCurveMath.MIN_SPEED, VelocityCurveMath.MAX_SPEED)
    }

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
        if (timeUs >= safeDurationUs) return C.TIME_UNSET
        val nextStepIndex = (timeUs.coerceAtLeast(0L) / stepUs) + 1L
        val nextUs = nextStepIndex * stepUs
        return if (nextUs >= safeDurationUs) C.TIME_UNSET else nextUs
    }
}
