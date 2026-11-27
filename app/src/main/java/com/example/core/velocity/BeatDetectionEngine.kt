package com.example.core.velocity

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Genuine on-device Music Beat & Onset Detection DSP Engine.
 *
 * 1. Decodes real audio files (MP3, M4A, WAV, OGG, AAC, or video audio track) via MediaExtractor + MediaCodec.
 * 2. Runs a 1024-pt Radix-2 Cooley-Tukey FFT with Hann windowing and sub-band weighted Spectral Flux
 *    (kick/sub-bass 40-180 Hz + snare/percussion 180-2800 Hz).
 * 3. Applies adaptive local-mean peak picking to locate true musical beat hits + autocorrelation BPM estimation.
 */
object BeatDetectionEngine {

    private const val TARGET_ANALYSIS_SR = 22050
    private const val MAX_ANALYSIS_SECONDS = 45.0
    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512

    suspend fun analyzeAudioUri(
        context: Context,
        uri: Uri,
        trimStartMs: Long = 0L,
        trimEndMs: Long = -1L,
    ): AudioBeatAnalysis = withContext(Dispatchers.Default) {
        val decoded = decodeAudioToMonoPcm(
            context = context,
            uri = uri,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
        )
        analyzePcmForBeats(
            samples = decoded.samples,
            sampleRate = decoded.sampleRate,
            durationMs = decoded.durationMs,
        )
    }

    /**
     * Pure JVM-testable beat & BPM detection pipeline on mono float PCM [-1..1].
     */
    fun analyzePcmForBeats(
        samples: FloatArray,
        sampleRate: Int,
        durationMs: Long,
    ): AudioBeatAnalysis {
        val safeDurMs = durationMs.coerceAtLeast(1000L)
        if (samples.size < FFT_SIZE * 2 || sampleRate <= 0) {
            return AudioBeatAnalysis(
                durationMs = safeDurMs,
                bpm = 120f,
                beats = emptyList(),
                waveformBars = List(96) { 0.15f },
            )
        }

        // 1. Build 96-bar visual waveform envelope directly from real PCM RMS + peak
        val waveformBars = computeWaveformBars(samples, barCount = 96)

        // 2. Compute Hann window and sub-band weighted Spectral Flux Onset Detection Function (ODF)
        val numFrames = ((samples.size - FFT_SIZE) / HOP_SIZE).coerceAtLeast(1)
        val hann = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        val freqPerBin = sampleRate.toFloat() / FFT_SIZE.toFloat()
        val bassStart = max(1, (40f / freqPerBin).roundToInt())
        val bassEnd = min(FFT_SIZE / 2 - 1, (180f / freqPerBin).roundToInt())
        val midEnd = min(FFT_SIZE / 2 - 1, (2800f / freqPerBin).roundToInt())

        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        val prevMag = DoubleArray(FFT_SIZE / 2)
        val odf = DoubleArray(numFrames)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = (samples[offset + i] * hann[i]).toDouble()
                imag[i] = 0.0
            }
            fftInPlace(real, imag)

            var bassFlux = 0.0
            var midFlux = 0.0
            for (k in bassStart..midEnd) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                val diff = mag - prevMag[k]
                if (diff > 0.0) {
                    if (k <= bassEnd) bassFlux += diff else midFlux += diff
                }
                prevMag[k] = mag
            }
            val bassNorm = bassFlux / max(1, bassEnd - bassStart + 1)
            val midNorm = midFlux / max(1, midEnd - bassEnd)
            // Emphasize kick/bass drops + crisp percussion transients
            odf[frame] = bassNorm * 0.68 + midNorm * 0.32
        }

        // 3. Smooth ODF slightly (3-tap binomial filter) and normalize
        val smoothOdf = DoubleArray(numFrames)
        var maxOdf = 1e-9
        for (i in 0 until numFrames) {
            val prev = if (i > 0) odf[i - 1] else odf[i]
            val next = if (i + 1 < numFrames) odf[i + 1] else odf[i]
            smoothOdf[i] = 0.25 * prev + 0.50 * odf[i] + 0.25 * next
            if (smoothOdf[i] > maxOdf) maxOdf = smoothOdf[i]
        }
        for (i in 0 until numFrames) {
            smoothOdf[i] /= maxOdf
        }

        // 4. Autocorrelation BPM estimation (60..190 BPM)
        val framesPerSec = sampleRate.toDouble() / HOP_SIZE.toDouble()
        val estimatedBpm = estimateBpmFromOdf(smoothOdf, framesPerSec)

        // 5. Adaptive threshold peak picking for real beat hits
        val windowRadius = (framesPerSec * 0.35).roundToInt().coerceIn(4, 24)
        val minInterBeatFrames = ((60.0 / (estimatedBpm * 1.45)) * framesPerSec)
            .roundToInt()
            .coerceIn(6, (framesPerSec * 0.45).roundToInt().coerceAtLeast(7))

        val rawBeats = mutableListOf<Pair<Int, Double>>()
        var lastPeakFrame = -minInterBeatFrames

        for (i in 1 until numFrames - 1) {
            val v = smoothOdf[i]
            if (v < smoothOdf[i - 1] || v < smoothOdf[i + 1]) continue
            if (i - lastPeakFrame < minInterBeatFrames) continue

            val lo = max(0, i - windowRadius)
            val hi = min(numFrames - 1, i + windowRadius)
            var sum = 0.0
            for (j in lo..hi) sum += smoothOdf[j]
            val localMean = sum / (hi - lo + 1)
            val threshold = localMean * 1.42 + 0.055

            if (v >= threshold) {
                rawBeats.add(i to v)
                lastPeakFrame = i
            }
        }

        val peakMax = rawBeats.maxOfOrNull { it.second }?.coerceAtLeast(1e-6) ?: 1.0
        val beats = rawBeats.map { (frameIdx, strengthRaw) ->
            val timeMs = ((frameIdx.toDouble() * HOP_SIZE.toDouble() / sampleRate.toDouble()) * 1000.0)
                .roundToLong()
                .coerceIn(0L, safeDurMs)
            val normPos = (timeMs.toFloat() / safeDurMs.toFloat()).coerceIn(0f, 1f)
            DetectedBeat(
                timeMs = timeMs,
                normalizedPos = normPos,
                strength = (strengthRaw / peakMax).toFloat().coerceIn(0.25f, 1f),
                isManual = false,
            )
        }

        return AudioBeatAnalysis(
            durationMs = safeDurMs,
            bpm = estimatedBpm,
            beats = beats,
            waveformBars = waveformBars,
        )
    }

    private fun estimateBpmFromOdf(odf: DoubleArray, framesPerSec: Double): Float {
        if (odf.size < 40) return 120f
        val minLag = ((60.0 / 190.0) * framesPerSec).roundToInt().coerceAtLeast(4)
        val maxLag = ((60.0 / 60.0) * framesPerSec).roundToInt().coerceAtMost(odf.size / 2)
        if (maxLag <= minLag) return 120f

        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var corr = 0.0
            val count = odf.size - lag
            for (i in 0 until count) {
                corr += odf[i] * odf[i + lag]
            }
            val bpmAtLag = 60.0 * framesPerSec / lag.toDouble()
            // Subtle perceptual tempo prior centered near 118 BPM (avoids half/double-time errors)
            val prior = kotlin.math.exp(-0.5 * ((bpmAtLag - 118.0) / 55.0) * ((bpmAtLag - 118.0) / 55.0))
            val score = (corr / max(1, count)) * (0.75 + 0.25 * prior)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return (60.0 * framesPerSec / bestLag.toDouble()).toFloat().coerceIn(60f, 190f)
    }

    private fun computeWaveformBars(samples: FloatArray, barCount: Int): List<Float> {
        val count = barCount.coerceAtLeast(16)
        val step = max(1, samples.size / count)
        val raw = FloatArray(count)
        var peak = 1e-4f
        for (b in 0 until count) {
            val start = b * step
            val end = min(samples.size, start + step)
            var sumSq = 0.0
            var localMax = 0f
            for (i in start until end) {
                val a = abs(samples[i])
                if (a > localMax) localMax = a
                sumSq += a * a
            }
            val rms = sqrt(sumSq / max(1, end - start)).toFloat()
            val combined = 0.60f * rms + 0.40f * localMax
            raw[b] = combined
            if (combined > peak) peak = combined
        }
        return raw.map { (it / peak).coerceIn(0.08f, 1.0f) }
    }

    /**
     * Standard iterative in-place Radix-2 Cooley-Tukey FFT.
     */
    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenR = cos(ang)
            val wLenI = sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                val half = len shr 1
                for (k in 0 until half) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long,
    )

    private fun decodeAudioToMonoPcm(
        context: Context,
        uri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                val mime = tf.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = tf
                    break
                }
            }
            if (audioTrack < 0 || format == null) {
                return DecodedAudio(FloatArray(0), TARGET_ANALYSIS_SR, 1000L)
            }

            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val trackDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val startUs = (trimStartMs.coerceAtLeast(0L)) * 1000L
            val maxSpanUs = (MAX_ANALYSIS_SECONDS * 1_000_000.0).toLong()
            val endUs = if (trimEndMs > trimStartMs) {
                min(trimEndMs * 1000L, startUs + maxSpanUs)
            } else if (trackDurationUs > startUs) {
                min(trackDurationUs, startUs + maxSpanUs)
            } else {
                startUs + maxSpanUs
            }

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val downsampleStep = max(1, (srcSampleRate / TARGET_ANALYSIS_SR))
            val effectiveSr = max(8000, srcSampleRate / downsampleStep)
            val estSamples = (((endUs - startUs) / 1_000_000.0) * effectiveSr).toInt().coerceIn(4096, 1_200_000)
            val out = FloatArray(estSamples)
            var outLen = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var frameCounter = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val ptsUs = extractor.sampleTime
                        if (sampleSize < 0 || ptsUs > endUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs - 50_000L) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val totalShorts = shortBuf.remaining()
                            val totalFrames = totalShorts / channels
                            for (f in 0 until totalFrames) {
                                var mono = 0f
                                for (c in 0 until channels) {
                                    mono += shortBuf.get(f * channels + c) / 32768f
                                }
                                mono /= channels
                                if ((frameCounter % downsampleStep) == 0L && outLen < out.size) {
                                    out[outLen++] = mono
                                }
                                frameCounter++
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || outLen >= out.size) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcSampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                }
            }

            val finalSamples = if (outLen == out.size) out else out.copyOf(outLen)
            val actualDurationMs = if (finalSamples.isNotEmpty()) {
                ((finalSamples.size.toDouble() / effectiveSr.toDouble()) * 1000.0).roundToLong().coerceAtLeast(500L)
            } else {
                ((endUs - startUs) / 1000L).coerceAtLeast(1000L)
            }
            return DecodedAudio(finalSamples, effectiveSr, actualDurationMs)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
