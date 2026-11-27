package com.example.core.studio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

data class AudioDspConfig(
    val gainDb: Float = 0f,           // -24.0 .. +18.0 dB
    val softLimiter: Boolean = true,  // Analog tanh soft-clipping limiter
    val fadeInMs: Long = 0L,          // 0 .. 5000 ms
    val fadeOutMs: Long = 0L,         // 0 .. 5000 ms
    val totalDurationMs: Long = 0L,   // Needed for fade-out envelope timing
    val muteAudio: Boolean = false,
)

/**
 * Genuine sample-level 16-bit PCM Audio DSP processor for Media3 Transformer.
 *
 * Implements:
 *  - Linear gain conversion from decibels: G = 10^(dB / 20)
 *  - Analog hyperbolic-tangent soft-clipping limiter to prevent digital harshness on +18 dB boosts
 *  - Smooth Fade-In and Fade-Out amplitude envelopes
 */
@UnstableApi
class ProAudioDspProcessor(
    private val config: AudioDspConfig,
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    companion object {
        fun dbToLinearGain(db: Float): Float = 10.0.pow(db.coerceIn(-36f, 24f).toDouble() / 20.0).toFloat()

        fun processSample(
            normalizedSample: Float,
            linearGain: Float,
            envelope: Float,
            softLimiter: Boolean,
        ): Float {
            val boosted = normalizedSample * linearGain * envelope
            return if (softLimiter) {
                tanh(boosted.toDouble()).toFloat()
            } else {
                boosted.coerceIn(-1f, 1f)
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        processedFrames = 0L
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val linearGain = dbToLinearGain(config.gainDb)
        val fadeInFrames = (config.fadeInMs * sampleRate) / 1000L
        val fadeOutFrames = (config.fadeOutMs * sampleRate) / 1000L
        val totalFrames = (config.totalDurationMs * sampleRate) / 1000L

        val frameBytes = channels * 2
        val numFrames = remaining / frameBytes

        for (f in 0 until numFrames) {
            val frameIndex = processedFrames + f
            var env = 1.0f
            if (fadeInFrames > 0L && frameIndex < fadeInFrames) {
                env = minOf(env, frameIndex.toFloat() / fadeInFrames.toFloat())
            }
            if (fadeOutFrames > 0L && totalFrames > 0L) {
                val remainingFrames = (totalFrames - frameIndex).coerceAtLeast(0L)
                if (remainingFrames < fadeOutFrames) {
                    env = minOf(env, remainingFrames.toFloat() / fadeOutFrames.toFloat())
                }
            }

            for (ch in 0 until channels) {
                val sampleShort = inputBuffer.short
                val normalized = sampleShort / 32768f
                val outNorm = processSample(normalized, linearGain, env, config.softLimiter)
                val outShort = (outNorm * 32767f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(outShort)
            }
        }
        processedFrames += numFrames
        // Consume any trailing odd byte safely
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }
}
