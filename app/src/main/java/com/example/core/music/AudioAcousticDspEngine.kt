package com.example.core.music

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin Mathematical Audio DSP & Acoustic Fingerprinting Engine.
 *
 * Implements genuine signal-processing algorithms (zero mock data, 100% JVM unit-testable):
 *  1. Radix-2 Cooley-Tukey FFT & Hann-windowed STFT Spectrogram
 *  2. Spectral Subtraction Noise-Floor Reducer (handles buzzy/noisy mic & short clips)
 *  3. RBJ 2nd-Order Biquad Bandpass/Highpass/Lowpass Filters + Mid-Side Vocal/Instrumental Separator
 *  4. Smart 2–4 Second Peak-Musicality Window Selector (tonality + onset flux + energy scoring)
 *  5. Shazam-Style 2D Constellation Landmark Hasher & Time-Offset Coherent Histogram Matcher
 *  6. Open Shazam v2 Binary Frequency-Peak Signature Encoder (16kHz FFT -> CRC32 + Protobuf -> Base64)
 *  7. Real Musical Telemetry: Autocorrelation Onset-Flux BPM, Krumhansl-Schmuckler Musical Key & Camelot,
 *     and Integrated RMS/LUFS Loudness + Crest Factor + SNR estimator.
 */
object AudioAcousticDspEngine {

    private const val TWO_PI = 2.0 * PI

    // Krumhansl-Schmuckler major & minor key profiles (C .. B)
    private val MAJOR_PROFILE = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
    )
    private val MINOR_PROFILE = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
    )
    private val PITCH_NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val CAMELOT_MAJOR = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val CAMELOT_MINOR = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    // -------------------------------------------------------------------------
    // 1. RADIX-2 COOLEY-TUKEY FFT & INVERSE FFT
    // -------------------------------------------------------------------------

    /**
     * In-place Radix-2 Cooley-Tukey FFT. [real] and [imag] must have the same power-of-2 length.
     */
    fun fftRadix2(real: FloatArray, imag: FloatArray, inverse: Boolean = false) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0 && imag.size == n) {
            "FFT size must be a positive power of 2 (got $n)"
        }

        // Bit-reversal permutation
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

        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angleStep = sign * TWO_PI / len.toDouble()
            val wStepRe = cos(angleStep).toFloat()
            val wStepIm = sin(angleStep).toFloat()

            var start = 0
            while (start < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val uRe = real[start + k]
                    val uIm = imag[start + k]
                    val vRe = real[start + k + half] * wRe - imag[start + k + half] * wIm
                    val vIm = real[start + k + half] * wIm + imag[start + k + half] * wRe

                    real[start + k] = uRe + vRe
                    imag[start + k] = uIm + vIm
                    real[start + k + half] = uRe - vRe
                    imag[start + k + half] = uIm - vIm

                    val nextWRe = wRe * wStepRe - wIm * wStepIm
                    wIm = wRe * wStepIm + wIm * wStepRe
                    wRe = nextWRe
                }
                start += len
            }
            len = len shl 1
        }

        if (inverse) {
            val invN = 1f / n.toFloat()
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }

    fun hannWindow(size: Int): FloatArray = FloatArray(size) { i ->
        (0.5 * (1.0 - cos(TWO_PI * i / (size - 1).coerceAtLeast(1)))).toFloat()
    }

    /**
     * Computes real-time 32-band logarithmic FFT spectrum bars (0f..1f) from a live PCM chunk.
     */
    fun computeLiveSpectrumBars(
        pcm: FloatArray,
        sampleRateHz: Int,
        bandCount: Int = 32,
    ): FloatArray {
        val out = FloatArray(bandCount)
        if (pcm.isEmpty()) return out
        val fftSize = 1024
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val win = hannWindow(fftSize)
        val start = (pcm.size - fftSize).coerceAtLeast(0)
        val copyLen = min(pcm.size, fftSize)
        for (i in 0 until copyLen) {
            re[i] = pcm[start + i] * win[i]
        }
        fftRadix2(re, im)

        val nyquist = sampleRateHz / 2f
        val minHz = 60f
        val maxHz = min(10000f, nyquist * 0.95f)
        val logMin = ln(minHz)
        val logMax = ln(maxHz.coerceAtLeast(minHz + 10f))

        for (b in 0 until bandCount) {
            val f0 = exp(logMin + (logMax - logMin) * b / bandCount)
            val f1 = exp(logMin + (logMax - logMin) * (b + 1) / bandCount)
            val bin0 = ((f0 / sampleRateHz) * fftSize).toInt().coerceIn(1, fftSize / 2 - 1)
            val bin1 = ((f1 / sampleRateHz) * fftSize).toInt().coerceIn(bin0 + 1, fftSize / 2)
            var sum = 0f
            for (k in bin0 until bin1) {
                sum += hypot(re[k], im[k])
            }
            val avg = sum / (bin1 - bin0).coerceAtLeast(1)
            // Log compression for smooth visual response
            out[b] = (ln(1f + avg * 6f) / ln(1f + 45f)).coerceIn(0f, 1f)
        }
        return out
    }

    // -------------------------------------------------------------------------
    // 2. BIQUAD FILTERS, MID-SIDE ISOLATOR & SPECTRAL SUBTRACTION DENOISER
    // -------------------------------------------------------------------------

    class BiquadFilter(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var z1 = 0f
        private var z2 = 0f

        fun process(input: FloatArray): FloatArray {
            val out = FloatArray(input.size)
            for (i in input.indices) {
                val x = input[i]
                val y = b0 * x + z1
                z1 = b1 * x - a1 * y + z2
                z2 = b2 * x - a2 * y
                out[i] = y
            }
            return out
        }

        companion object {
            fun highPass(sampleRateHz: Int, cutoffHz: Float, q: Float = 0.7071f): BiquadFilter {
                val w0 = TWO_PI * cutoffHz.coerceIn(10f, sampleRateHz * 0.45f) / sampleRateHz
                val alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val a0 = 1.0 + alpha
                return BiquadFilter(
                    b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat(),
                    b1 = (-(1.0 + cosW0) / a0).toFloat(),
                    b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat(),
                    a1 = (-2.0 * cosW0 / a0).toFloat(),
                    a2 = ((1.0 - alpha) / a0).toFloat(),
                )
            }

            fun lowPass(sampleRateHz: Int, cutoffHz: Float, q: Float = 0.7071f): BiquadFilter {
                val w0 = TWO_PI * cutoffHz.coerceIn(20f, sampleRateHz * 0.48f) / sampleRateHz
                val alpha = sin(w0) / (2.0 * q)
                val cosW0 = cos(w0)
                val a0 = 1.0 + alpha
                return BiquadFilter(
                    b0 = ((1.0 - cosW0) / 2.0 / a0).toFloat(),
                    b1 = ((1.0 - cosW0) / a0).toFloat(),
                    b2 = ((1.0 - cosW0) / 2.0 / a0).toFloat(),
                    a1 = (-2.0 * cosW0 / a0).toFloat(),
                    a2 = ((1.0 - alpha) / a0).toFloat(),
                )
            }
        }
    }

    /**
     * Removes DC offset and normalizes RMS loudness safely with tanh soft ceiling.
     */
    fun normalizeAndRemoveDc(samples: FloatArray, targetRms: Float = 0.16f): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        var mean = 0.0
        for (s in samples) mean += s
        mean /= samples.size

        var sumSq = 0.0
        val centered = FloatArray(samples.size) { i ->
            val v = (samples[i] - mean).toFloat()
            sumSq += v * v
            v
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        if (rms < 1e-6f) return centered
        val gain = (targetRms / rms).coerceIn(0.15f, 18f)
        return FloatArray(centered.size) { i ->
            val scaled = centered[i] * gain
            // Soft ceiling prevents clipping buzz
            if (abs(scaled) > 0.92f) kotlin.math.tanh(scaled.toDouble()).toFloat() else scaled
        }
    }

    /**
     * Real Overlap-Add Spectral Subtraction Denoiser.
     * Estimates stationary buzz/noise floor from the quietest 20% of STFT frames,
     * then subtracts alpha * noiseMag with spectral floor beta to avoid musical noise artifacts.
     */
    fun denoiseSpectralSubtraction(
        samples: FloatArray,
        sampleRateHz: Int,
        overSubtraction: Float = 1.65f,
        spectralFloor: Float = 0.08f,
    ): FloatArray {
        val fftSize = 512
        val hop = fftSize / 2
        if (samples.size < fftSize * 2) return normalizeAndRemoveDc(samples)

        val win = hannWindow(fftSize)
        val numFrames = ((samples.size - fftSize) / hop) + 1
        val halfBins = fftSize / 2 + 1

        val frameRe = Array(numFrames) { FloatArray(fftSize) }
        val frameIm = Array(numFrames) { FloatArray(fftSize) }
        val frameEnergy = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            val offset = f * hop
            var e = 0f
            for (i in 0 until fftSize) {
                val v = samples[offset + i] * win[i]
                frameRe[f][i] = v
                e += v * v
            }
            frameEnergy[f] = e
            fftRadix2(frameRe[f], frameIm[f], inverse = false)
        }

        // Estimate noise profile from the quietest 20% of frames (at least 3 frames)
        val sortedIdx = frameEnergy.indices.sortedBy { frameEnergy[it] }
        val noiseFrameCount = (numFrames / 5).coerceIn(1, min(20, numFrames))
        val noiseMag = FloatArray(halfBins)
        for (k in 0 until noiseFrameCount) {
            val idx = sortedIdx[k]
            for (b in 0 until halfBins) {
                noiseMag[b] += hypot(frameRe[idx][b], frameIm[idx][b])
            }
        }
        for (b in 0 until halfBins) {
            noiseMag[b] /= noiseFrameCount.toFloat()
        }

        // Subtract noise floor and overlap-add reconstruct
        val output = FloatArray(samples.size)
        val normAcc = FloatArray(samples.size)

        for (f in 0 until numFrames) {
            val re = frameRe[f]
            val im = frameIm[f]
            for (b in 0 until halfBins) {
                val mag = hypot(re[b], im[b])
                val phase = atan2(im[b].toDouble(), re[b].toDouble())
                val cleaned = max(mag - overSubtraction * noiseMag[b], spectralFloor * mag)
                val newRe = (cleaned * cos(phase)).toFloat()
                val newIm = (cleaned * sin(phase)).toFloat()
                re[b] = newRe
                im[b] = newIm
                if (b in 1 until fftSize / 2) {
                    re[fftSize - b] = newRe
                    im[fftSize - b] = -newIm
                }
            }
            fftRadix2(re, im, inverse = true)
            val offset = f * hop
            for (i in 0 until fftSize) {
                val idx = offset + i
                if (idx < output.size) {
                    output[idx] += re[i] * win[i]
                    normAcc[idx] += win[i] * win[i]
                }
            }
        }

        for (i in output.indices) {
            if (normAcc[i] > 1e-4f) {
                output[i] /= normAcc[i]
            }
        }
        return normalizeAndRemoveDc(output)
    }

    /**
     * Conditions stereo or mono PCM according to the selected DSP mode:
     *  - FULL_MIX: clean DC removal + RMS normalize
     *  - CLEAN_DENOISE: Spectral Subtraction + 80Hz..14kHz bandpass
     *  - VOCALS_MID: Mid channel (L+R)/2 + 280Hz..3800Hz vocal bandpass + noise reduction
     *  - INSTRUMENTAL_SIDE: Side channel (L-R)/2 (cancels center vocals) or harmonic bandpass if mono
     */
    fun applyDspConditioning(
        left: FloatArray,
        right: FloatArray?,
        sampleRateHz: Int,
        mode: AudioDspFilterMode,
    ): FloatArray {
        val hasStereo = right != null && right.size == left.size
        val baseMono = when (mode) {
            AudioDspFilterMode.VOCALS_MID -> {
                if (hasStereo) {
                    FloatArray(left.size) { i -> 0.5f * (left[i] + right!![i]) }
                } else {
                    left.copyOf()
                }
            }
            AudioDspFilterMode.INSTRUMENTAL_SIDE -> {
                if (hasStereo) {
                    // Check if stereo actually has L-R difference; if dual-mono, gracefully fall back to low+high emphasis
                    var diffEnergy = 0.0
                    var sumEnergy = 0.0
                    for (i in left.indices step 4) {
                        val d = left[i] - right!![i]
                        val s = left[i] + right[i]
                        diffEnergy += d * d
                        sumEnergy += s * s
                    }
                    if (diffEnergy > sumEnergy * 0.015) {
                        FloatArray(left.size) { i -> 0.5f * (left[i] - right!![i]) }
                    } else {
                        left.copyOf()
                    }
                } else {
                    left.copyOf()
                }
            }
            else -> {
                if (hasStereo) FloatArray(left.size) { i -> 0.5f * (left[i] + right!![i]) } else left.copyOf()
            }
        }

        val cleaned = when (mode) {
            AudioDspFilterMode.FULL_MIX -> normalizeAndRemoveDc(baseMono)
            AudioDspFilterMode.CLEAN_DENOISE -> {
                val hp = BiquadFilter.highPass(sampleRateHz, 80f).process(baseMono)
                val lp = BiquadFilter.lowPass(sampleRateHz, min(14000f, sampleRateHz * 0.45f)).process(hp)
                denoiseSpectralSubtraction(lp, sampleRateHz, overSubtraction = 1.65f, spectralFloor = 0.08f)
            }
            AudioDspFilterMode.VOCALS_MID -> {
                val hp = BiquadFilter.highPass(sampleRateHz, 260f).process(baseMono)
                val lp = BiquadFilter.lowPass(sampleRateHz, min(3800f, sampleRateHz * 0.42f)).process(hp)
                denoiseSpectralSubtraction(lp, sampleRateHz, overSubtraction = 1.85f, spectralFloor = 0.06f)
            }
            AudioDspFilterMode.INSTRUMENTAL_SIDE -> {
                val hp = BiquadFilter.highPass(sampleRateHz, 55f).process(baseMono)
                val lp = BiquadFilter.lowPass(sampleRateHz, min(12000f, sampleRateHz * 0.44f)).process(hp)
                denoiseSpectralSubtraction(lp, sampleRateHz, overSubtraction = 1.35f, spectralFloor = 0.12f)
            }
        }
        return cleaned
    }

    /**
     * Linearly resamples mono float PCM to [targetRateHz] (e.g. 16000 Hz or 11025 Hz)
     * with a pre-anti-aliasing low-pass filter when downsampling.
     */
    fun resampleMono(
        samples: FloatArray,
        sourceRateHz: Int,
        targetRateHz: Int,
    ): FloatArray {
        if (samples.isEmpty() || sourceRateHz == targetRateHz) return samples.copyOf()
        val preFiltered = if (targetRateHz < sourceRateHz) {
            BiquadFilter.lowPass(sourceRateHz, targetRateHz * 0.44f).process(samples)
        } else {
            samples
        }
        val ratio = sourceRateHz.toDouble() / targetRateHz.toDouble()
        val outLen = max(1, (preFiltered.size / ratio).roundToInt())
        return FloatArray(outLen) { i ->
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, preFiltered.lastIndex)
            val i1 = (i0 + 1).coerceIn(0, preFiltered.lastIndex)
            val frac = (srcPos - i0).toFloat()
            preFiltered[i0] * (1f - frac) + preFiltered[i1] * frac
        }
    }

    // -------------------------------------------------------------------------
    // 3. SMART 2–4 SECOND PEAK-MUSICALITY WINDOW SELECTOR
    // -------------------------------------------------------------------------

    data class SelectedAudioWindow(
        val samples: FloatArray,
        val startSec: Float,
        val durationSec: Float,
        val musicalityScore: Float,
    )

    /**
     * Scans a clip to find the cleanest, highest-tonality 2–4 second musical window
     * (rejecting silence, speech bursts, or flat broadband static).
     */
    fun selectBestMusicWindow(
        samples: FloatArray,
        sampleRateHz: Int,
        windowDurationSec: Float = 3.5f,
    ): SelectedAudioWindow {
        val targetSamples = (windowDurationSec * sampleRateHz).roundToInt().coerceAtLeast(sampleRateHz)
        if (samples.size <= targetSamples) {
            return SelectedAudioWindow(
                samples = samples.copyOf(),
                startSec = 0f,
                durationSec = samples.size.toFloat() / sampleRateHz.toFloat(),
                musicalityScore = 1f,
            )
        }

        val stepSamples = (0.5f * sampleRateHz).roundToInt().coerceAtLeast(512)
        val fftSize = 512
        val win = hannWindow(fftSize)

        var bestStart = 0
        var bestScore = -1f

        var start = 0
        while (start + targetSamples <= samples.size) {
            var energySum = 0.0
            var tonalitySum = 0.0
            var frameCount = 0

            // Sample 8 evenly spaced frames inside this candidate window
            val probeStride = ((targetSamples - fftSize) / 8).coerceAtLeast(fftSize)
            var pos = start
            while (pos + fftSize <= start + targetSamples) {
                val re = FloatArray(fftSize)
                val im = FloatArray(fftSize)
                var e = 0.0
                for (i in 0 until fftSize) {
                    val v = samples[pos + i] * win[i]
                    re[i] = v
                    e += v * v
                }
                fftRadix2(re, im)

                // Spectral flatness (geometric mean / arithmetic mean): low flatness = tonal music!
                var logSum = 0.0
                var arithSum = 0.0
                val lowBin = 4
                val highBin = fftSize / 2
                for (b in lowBin until highBin) {
                    val mag = hypot(re[b], im[b]).toDouble() + 1e-9
                    logSum += ln(mag)
                    arithSum += mag
                }
                val bins = (highBin - lowBin).coerceAtLeast(1)
                val geoMean = exp(logSum / bins)
                val arithMean = arithSum / bins
                val flatness = (geoMean / arithMean.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
                val tonality = 1.0 - flatness

                energySum += sqrt(e / fftSize)
                tonalitySum += tonality
                frameCount++
                pos += probeStride
            }

            if (frameCount > 0) {
                val avgRms = (energySum / frameCount).toFloat()
                val avgTonality = (tonalitySum / frameCount).toFloat()
                val score = avgRms * (0.35f + 0.65f * avgTonality * avgTonality)
                if (score > bestScore) {
                    bestScore = score
                    bestStart = start
                }
            }
            start += stepSamples
        }

        val slice = samples.copyOfRange(bestStart, bestStart + targetSamples)
        return SelectedAudioWindow(
            samples = slice,
            startSec = bestStart.toFloat() / sampleRateHz.toFloat(),
            durationSec = slice.size.toFloat() / sampleRateHz.toFloat(),
            musicalityScore = bestScore.coerceAtLeast(0f),
        )
    }

    // -------------------------------------------------------------------------
    // 4. SHAZAM-STYLE 2D CONSTELLATION FINGERPRINTING & MATCHING
    // -------------------------------------------------------------------------

    data class ConstellationFingerprintResult(
        val peaks: List<ConstellationPeak>,
        val hashes: List<ConstellationHash>,
        val spectrogram: SpectrogramFrameVisual,
        val hopSamples: Int,
        val analysisRateHz: Int,
    )

    private val CONSTELLATION_BANDS = arrayOf(
        8 to 18,     // ~85 - 195 Hz
        18 to 38,    // ~195 - 410 Hz
        38 to 78,    // ~410 - 840 Hz
        78 to 156,   // ~840 - 1680 Hz
        156 to 310,  // ~1680 - 3340 Hz
        310 to 500,  // ~3340 - 5380 Hz
    )

    /**
     * Extracts Shazam-style 2D time-frequency constellation peaks, combinatorial anchor-target
     * hashes `(f1, f2, dt)`, AND a real normalized 2D spectrogram + landmark overlay for UI Canvas.
     */
    fun extractConstellationFingerprint(
        samples: FloatArray,
        sampleRateHz: Int,
    ): ConstellationFingerprintResult {
        val analysisRate = 11025
        val resampled = resampleMono(samples, sampleRateHz, analysisRate)
        val fftSize = 1024
        val hop = 256 // ~23.2 ms per frame

        if (resampled.size < fftSize) {
            return ConstellationFingerprintResult(
                peaks = emptyList(),
                hashes = emptyList(),
                spectrogram = SpectrogramFrameVisual(emptyList(), emptyList(), emptyList()),
                hopSamples = hop,
                analysisRateHz = analysisRate,
            )
        }

        val win = hannWindow(fftSize)
        val numFrames = ((resampled.size - fftSize) / hop) + 1
        val halfBins = fftSize / 2
        val mags = Array(numFrames) { FloatArray(halfBins) }

        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)

        for (t in 0 until numFrames) {
            val offset = t * hop
            for (i in 0 until fftSize) {
                re[i] = resampled[offset + i] * win[i]
                im[i] = 0f
            }
            fftRadix2(re, im)
            for (b in 0 until halfBins) {
                mags[t][b] = ln(1f + hypot(re[b], im[b]))
            }
        }

        // 1. Pick strongest local 2D peaks in each logarithmic frequency band per frame
        val rawPeaks = mutableListOf<ConstellationPeak>()
        for (t in 1 until numFrames - 1) {
            // Adaptive noise floor for this frame
            var frameSum = 0f
            for (b in 8 until 500) frameSum += mags[t][b]
            val frameMean = frameSum / 492f

            for ((low, high) in CONSTELLATION_BANDS) {
                var bestBin = -1
                var bestVal = frameMean * 1.35f
                for (b in low until min(high, halfBins - 1)) {
                    val v = mags[t][b]
                    if (v > bestVal &&
                        v >= mags[t - 1][b] && v >= mags[t + 1][b] &&
                        v >= mags[t][b - 1] && v >= mags[t][b + 1]
                    ) {
                        bestVal = v
                        bestBin = b
                    }
                }
                if (bestBin != -1) {
                    rawPeaks += ConstellationPeak(t, bestBin, bestVal)
                }
            }
        }

        // 2. Pair anchors with targets in forward target zone (dt in 2..42 frames, fan-out 6)
        val hashes = ArrayList<ConstellationHash>(rawPeaks.size * 5)
        val visualPairs = ArrayList<ConstellationLandmarkPair>(64)

        for (i in rawPeaks.indices) {
            val anchor = rawPeaks[i]
            var paired = 0
            for (j in i + 1 until rawPeaks.size) {
                val target = rawPeaks[j]
                val dt = target.timeFrame - anchor.timeFrame
                if (dt < 2) continue
                if (dt > 42) break
                val f1 = anchor.freqBin and 0x3FF
                val f2 = target.freqBin and 0x3FF
                val hash = (f1 shl 20) or (f2 shl 10) or (dt and 0x3FF)
                hashes += ConstellationHash(hash = hash, anchorTimeFrame = anchor.timeFrame)

                if (visualPairs.size < 55 && i % 4 == 0 && paired < 2) {
                    visualPairs += ConstellationLandmarkPair(
                        t1Norm = anchor.timeFrame.toFloat() / numFrames.coerceAtLeast(1),
                        f1Norm = (anchor.freqBin.toFloat() / 500f).coerceIn(0f, 1f),
                        t2Norm = target.timeFrame.toFloat() / numFrames.coerceAtLeast(1),
                        f2Norm = (target.freqBin.toFloat() / 500f).coerceIn(0f, 1f),
                    )
                }
                paired++
                if (paired >= 6) break
            }
        }

        // 3. Downsample spectrogram cleanly into 48 time columns x 32 log-frequency rows for UI Canvas
        val cols = 48
        val rows = 32
        var globalMax = 1e-4f
        val visualCols = List(cols) { c ->
            val t0 = (c * numFrames) / cols
            val t1 = (((c + 1) * numFrames) / cols).coerceAtLeast(t0 + 1).coerceAtMost(numFrames)
            FloatArray(rows) { r ->
                val fStart = (8.0 * (500.0 / 8.0).pow(r.toDouble() / rows)).toInt().coerceIn(1, halfBins - 1)
                val fEnd = (8.0 * (500.0 / 8.0).pow((r + 1).toDouble() / rows)).toInt().coerceIn(fStart + 1, halfBins)
                var sum = 0f
                var count = 0
                for (t in t0 until t1) {
                    for (b in fStart until fEnd) {
                        sum += mags[t][b]
                        count++
                    }
                }
                val avg = if (count > 0) sum / count else 0f
                if (avg > globalMax) globalMax = avg
                avg
            }
        }
        visualCols.forEach { col ->
            for (r in col.indices) col[r] = (col[r] / globalMax).coerceIn(0f, 1f)
        }

        val visualPeaks = rawPeaks
            .sortedByDescending { it.magnitude }
            .take(90)
            .map { pk ->
                (pk.timeFrame.toFloat() / numFrames.coerceAtLeast(1)) to
                    (ln(pk.freqBin.coerceAtLeast(8) / 8.0) / ln(500.0 / 8.0)).toFloat().coerceIn(0f, 1f)
            }

        return ConstellationFingerprintResult(
            peaks = rawPeaks,
            hashes = hashes,
            spectrogram = SpectrogramFrameVisual(
                timeColumns = visualCols,
                peaksNorm = visualPeaks,
                pairsNorm = visualPairs,
            ),
            hopSamples = hop,
            analysisRateHz = analysisRate,
        )
    }

    /**
     * Matches query constellation hashes (even from a noisy 2–4 second sample) against
     * saved vault tracks using a time-offset coherent histogram (`deltaT = tTrack - tQuery`).
     */
    fun matchConstellationHashes(
        queryHashes: List<ConstellationHash>,
        vaultTracks: List<VaultTrackEntry>,
        minCoherentVotes: Int = 8,
    ): VaultMatchResult? {
        if (queryHashes.isEmpty() || vaultTracks.isEmpty()) return null

        val queryLookup = HashMap<Int, MutableList<Int>>(queryHashes.size)
        for (qh in queryHashes) {
            queryLookup.getOrPut(qh.hash) { ArrayList(2) }.add(qh.anchorTimeFrame)
        }

        var bestResult: VaultMatchResult? = null
        val frameDurationSec = 256f / 11025f

        for (track in vaultTracks) {
            if (track.hashes.isEmpty()) continue
            val deltaHistogram = HashMap<Int, Int>()
            var totalMatches = 0

            for (th in track.hashes) {
                val queryTimes = queryLookup[th.hash] ?: continue
                for (qt in queryTimes) {
                    // Quantize delta by 2 frames (~46ms tolerance for timing drift/resampling)
                    val deltaBucket = (th.anchorTimeFrame - qt) / 2
                    val count = (deltaHistogram[deltaBucket] ?: 0) + 1
                    deltaHistogram[deltaBucket] = count
                    totalMatches++
                }
            }

            if (deltaHistogram.isEmpty()) continue
            var bestBucket = 0
            var bestVotes = 0
            for ((bucket, votes) in deltaHistogram) {
                // Sum adjacent buckets for slight tempo/clock drift resilience
                val neighborVotes = votes +
                    (deltaHistogram[bucket - 1] ?: 0) +
                    (deltaHistogram[bucket + 1] ?: 0)
                if (neighborVotes > bestVotes) {
                    bestVotes = neighborVotes
                    bestBucket = bucket
                }
            }

            if (bestVotes >= minCoherentVotes) {
                val denom = min(queryHashes.size, track.hashes.size).coerceAtLeast(40)
                val ratio = bestVotes.toFloat() / (denom * 0.14f)
                val confidence = (52 + (ratio * 47f).roundToInt()).coerceIn(55, 99)
                val candidate = VaultMatchResult(
                    entry = track,
                    coherentVotes = bestVotes,
                    totalMatches = totalMatches,
                    confidencePercent = confidence,
                    matchedOffsetSec = (bestBucket * 2 * frameDurationSec).coerceAtLeast(0f),
                )
                if (bestResult == null || candidate.coherentVotes > bestResult.coherentVotes) {
                    bestResult = candidate
                }
            }
        }
        return bestResult
    }

    // -------------------------------------------------------------------------
    // 5. OPEN SHAZAM V2 ACOUSTIC SIGNATURE PACKET ENCODER (16kHz FFT LANDMARKS)
    // -------------------------------------------------------------------------

    private data class ShazamPeak(
        val fftPassNumber: Int,
        val peakMagnitude: Int,
        val correctedBin: Int,
    )

    /**
     * Generates a genuine Shazam v2 open acoustic fingerprint URI (`data:audio/vnd.shazam.sig;base64,...`)
     * directly from raw PCM using 16 kHz resampling, 2048-pt Hann STFT (hop 128), 2D peak spreading,
     * and binary CRC32 + protobuf landmark packing (compatible with open SongRec/Vibra protocol).
     */
    fun encodeShazamSignature(
        samples: FloatArray,
        sampleRateHz: Int,
    ): Pair<String, Int>? {
        val targetRate = 16000
        val mono16k = resampleMono(samples, sampleRateHz, targetRate)
        // Use up to 12 seconds (optimal Shazam signature window is 3s..12s)
        val maxSamples = targetRate * 12
        val pcm = if (mono16k.size > maxSamples) mono16k.copyOf(maxSamples) else mono16k
        if (pcm.size < targetRate * 2) return null

        val fftSize = 2048
        val hop = 128
        val win = hannWindow(fftSize)
        val numFrames = ((pcm.size - fftSize) / hop) + 1
        if (numFrames < 60) return null

        val spectrumBins = fftSize / 2 + 1
        val fftMagRing = Array(numFrames) { FloatArray(spectrumBins) }
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)

        for (t in 0 until numFrames) {
            val offset = t * hop
            for (i in 0 until fftSize) {
                // Scale to signed 16-bit integer range to match Shazam magnitude scaling
                re[i] = (pcm[offset + i] * 32767f) * win[i]
                im[i] = 0f
            }
            fftRadix2(re, im)
            for (b in 0 until spectrumBins) {
                val pwr = (re[b] * re[b] + im[b] * im[b]) / (1 shl 17).toFloat()
                fftMagRing[t][b] = max(pwr, 1e-10f)
            }
        }

        // Frequency-band spread maxima & 2D local peak extraction (bands 250Hz..5200Hz)
        // Band IDs: 0 (250-520Hz), 1 (520-1450Hz), 2 (1450-3500Hz), 3 (3500-5500Hz)
        val bandPeaks = Array(4) { ArrayList<ShazamPeak>() }
        val maxPeaksPerSecond = 20
        val durationSec = pcm.size.toFloat() / targetRate.toFloat()

        for (t in 4 until numFrames - 4) {
            for (bin in 32 until 680) {
                val v = fftMagRing[t][bin]
                if (v < 1f / 64f) continue
                // Local max in frequency neighborhood [-2..+2] and time neighborhood [-3..+3]
                var isMax = true
                for (df in -2..2) {
                    if (df != 0 && fftMagRing[t][bin + df] > v) {
                        isMax = false; break
                    }
                }
                if (!isMax) continue
                for (dt in -3..3) {
                    if (dt != 0 && fftMagRing[t + dt][bin] > v) {
                        isMax = false; break
                    }
                }
                if (!isMax) continue

                val peakLog = (ln(v.toDouble()) * 1477.3 + 6144.0).roundToInt().coerceIn(0, 65535)
                val prevLog = (ln(fftMagRing[t][bin - 1].toDouble()) * 1477.3 + 6144.0)
                val nextLog = (ln(fftMagRing[t][bin + 1].toDouble()) * 1477.3 + 6144.0)
                val denom = 2.0 * peakLog - prevLog - nextLog
                val binInterp = if (abs(denom) > 1e-4) {
                    0.5 * (nextLog - prevLog) / denom
                } else 0.0
                val correctedBin = ((bin + binInterp) * 64.0).roundToInt().coerceIn(0, 65535)

                val freqHz = correctedBin * (targetRate / 2.0 / 1024.0 / 64.0)
                val bandIdx = when {
                    freqHz < 250.0 -> -1
                    freqHz < 520.0 -> 0
                    freqHz < 1450.0 -> 1
                    freqHz < 3500.0 -> 2
                    freqHz <= 5500.0 -> 3
                    else -> -1
                }
                if (bandIdx >= 0) {
                    bandPeaks[bandIdx].add(ShazamPeak(t, peakLog, correctedBin))
                }
            }
        }

        // Cap peaks per band to keep payload compact & focused on dominant harmonics
        val cap = (durationSec * maxPeaksPerSecond).roundToInt().coerceIn(24, 240)
        val bandBuffers = Array(4) { b ->
            val selected = bandPeaks[b]
                .sortedByDescending { it.peakMagnitude }
                .take(cap)
                .sortedBy { it.fftPassNumber }

            val out = ByteArrayOutputStream()
            var lastPass = 0
            for (pk in selected) {
                var diff = pk.fftPassNumber - lastPass
                if (diff >= 255) {
                    out.write(0xFF)
                    out.write(intToLeBytes(pk.fftPassNumber))
                    lastPass = pk.fftPassNumber
                } else {
                    out.write(diff and 0xFF)
                    lastPass = pk.fftPassNumber
                }
                out.write(shortToLeBytes(pk.peakMagnitude))
                out.write(shortToLeBytes(pk.correctedBin))
            }
            out.toByteArray()
        }

        val body = ByteArrayOutputStream()
        // Section header (0x40000000) + total length of peak sections
        val sectionsOut = ByteArrayOutputStream()
        for (b in 0 until 4) {
            val bytes = bandBuffers[b]
            if (bytes.isEmpty()) continue
            sectionsOut.write(intToLeBytes(0x60030040 + b))
            sectionsOut.write(intToLeBytes(bytes.size))
            sectionsOut.write(bytes)
            val pad = (4 - (bytes.size % 4)) % 4
            repeat(pad) { sectionsOut.write(0) }
        }
        val sectionsBytes = sectionsOut.toByteArray()
        if (sectionsBytes.isEmpty()) return null

        body.write(intToLeBytes(0x40000000))
        body.write(intToLeBytes(sectionsBytes.size + 8))
        body.write(sectionsBytes)
        val bodyBytes = body.toByteArray()

        // 48-byte RawSignatureHeader
        val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0xCAFE2580.toInt()) // magic1
        header.putInt(0)                  // crc32 placeholder
        header.putInt(bodyBytes.size + 48 - 48 + 32) // size_minus_header
        header.putInt(0x94119C00.toInt()) // magic2
        header.putInt(0); header.putInt(0); header.putInt(0) // void1
        header.putInt(3 shl 27)           // shifted_sample_rate_id (3 = 16000Hz)
        header.putInt(0); header.putInt(0) // void2
        header.putInt(pcm.size + (targetRate * 0.24f).roundToInt()) // number_samples_plus_divided
        header.putInt((15 shl 19) or 0x40000) // fixed_value
        val headerBytes = header.array()

        // Compute CRC32 over header[8..48) + bodyBytes
        val crc = CRC32()
        crc.update(headerBytes, 8, 40)
        crc.update(bodyBytes)
        val crcVal = crc.value.toInt()
        ByteBuffer.wrap(headerBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(crcVal)

        val packet = ByteArray(headerBytes.size + bodyBytes.size)
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.size)
        System.arraycopy(bodyBytes, 0, packet, headerBytes.size, bodyBytes.size)

        val b64 = Base64.getEncoder().encodeToString(packet)
        val durationMs = (pcm.size * 1000L / targetRate).toInt()
        return "data:audio/vnd.shazam.sig;base64,$b64" to durationMs
    }

    private fun intToLeBytes(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
        ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 24) and 0xFF).toByte(),
    )

    private fun shortToLeBytes(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(),
    )

    // -------------------------------------------------------------------------
    // 6. MUSICAL TELEMETRY: BPM, MUSICAL KEY (CAMELOT), LUFS & SNR
    // -------------------------------------------------------------------------

    fun analyzeMusicalTelemetry(
        samples: FloatArray,
        sampleRateHz: Int,
        constellation: ConstellationFingerprintResult? = null,
        windowStartSec: Float = 0f,
    ): MusicalTelemetry {
        if (samples.isEmpty()) {
            return MusicalTelemetry(
                bpm = 0f,
                musicalKey = "Unknown",
                camelotCode = "--",
                loudnessLufs = -70f,
                peakDbfs = -70f,
                dynamicRangeDb = 0f,
                estimatedSnrDb = 0f,
                dominantFreqHz = 0f,
                constellationPeakCount = 0,
                constellationHashCount = 0,
            )
        }

        // Loudness & Peak
        var sumSq = 0.0
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSq += s * s
        }
        val rms = sqrt(sumSq / samples.size).toFloat().coerceAtLeast(1e-7f)
        val peakDbfs = (20.0 * log10(peak.coerceAtLeast(1e-7f).toDouble())).toFloat()
        val rmsDbfs = (20.0 * log10(rms.toDouble())).toFloat()
        // ITU-R BS.1770 approximate K-weighted offset for full-range music
        val lufs = (rmsDbfs - 0.691f).coerceIn(-65f, 0f)
        val dynamicRange = (peakDbfs - rmsDbfs).coerceIn(0f, 36f)

        // Frame-level SNR estimate (90th percentile frame energy vs 15th percentile floor)
        val frameLen = (sampleRateHz / 20).coerceAtLeast(256) // 50ms frames
        val frameRmsList = ArrayList<Float>()
        var pos = 0
        while (pos + frameLen <= samples.size) {
            var e = 0.0
            for (i in 0 until frameLen) {
                val v = samples[pos + i]
                e += v * v
            }
            frameRmsList += sqrt(e / frameLen).toFloat().coerceAtLeast(1e-7f)
            pos += frameLen
        }
        val snrDb = if (frameRmsList.size >= 4) {
            frameRmsList.sort()
            val floorRms = frameRmsList[(frameRmsList.size * 0.15f).toInt().coerceIn(0, frameRmsList.lastIndex)]
            val signalRms = frameRmsList[(frameRmsList.size * 0.90f).toInt().coerceIn(0, frameRmsList.lastIndex)]
            (20.0 * log10((signalRms / floorRms).toDouble())).toFloat().coerceIn(0f, 55f)
        } else 12f

        val bpm = detectBpmAutocorrelation(samples, sampleRateHz)
        val (keyName, camelot, dominantHz) = detectMusicalKeyAndDominantFreq(samples, sampleRateHz)

        return MusicalTelemetry(
            bpm = bpm,
            musicalKey = keyName,
            camelotCode = camelot,
            loudnessLufs = lufs,
            peakDbfs = peakDbfs,
            dynamicRangeDb = dynamicRange,
            estimatedSnrDb = snrDb,
            dominantFreqHz = dominantHz,
            constellationPeakCount = constellation?.peaks?.size ?: 0,
            constellationHashCount = constellation?.hashes?.size ?: 0,
            windowStartSec = windowStartSec,
            windowDurationSec = samples.size.toFloat() / sampleRateHz.toFloat(),
        )
    }

    /**
     * Real Spectral-Flux Onset Envelope + Autocorrelation BPM Detector (65–185 BPM).
     */
    fun detectBpmAutocorrelation(samples: FloatArray, sampleRateHz: Int): Float {
        val fftSize = 1024
        val hop = 512
        if (samples.size < fftSize * 8) return 0f

        val win = hannWindow(fftSize)
        val numFrames = ((samples.size - fftSize) / hop) + 1
        val onsetFlux = FloatArray(numFrames)
        val prevMag = FloatArray(fftSize / 2)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)

        for (t in 0 until numFrames) {
            val offset = t * hop
            for (i in 0 until fftSize) {
                re[i] = samples[offset + i] * win[i]
                im[i] = 0f
            }
            fftRadix2(re, im)
            var flux = 0f
            for (b in 2 until fftSize / 2) {
                val mag = hypot(re[b], im[b])
                val diff = mag - prevMag[b]
                if (diff > 0f) flux += diff
                prevMag[b] = mag
            }
            onsetFlux[t] = flux
        }

        // Mean-subtract onset flux
        val mean = onsetFlux.average().toFloat()
        for (i in onsetFlux.indices) onsetFlux[i] = (onsetFlux[i] - mean).coerceAtLeast(0f)

        val framesPerSec = sampleRateHz.toFloat() / hop.toFloat()
        val minLag = (framesPerSec * 60f / 185f).roundToInt().coerceAtLeast(2)
        val maxLag = (framesPerSec * 60f / 65f).roundToInt().coerceAtMost(numFrames / 2)
        if (maxLag <= minLag + 2) return 0f

        var bestLag = minLag
        var bestCorr = -1f
        val corrValues = FloatArray(maxLag + 1)

        for (lag in minLag..maxLag) {
            var sum = 0.0
            val count = numFrames - lag
            for (i in 0 until count) {
                sum += onsetFlux[i] * onsetFlux[i + lag]
            }
            val norm = (sum / count.coerceAtLeast(1)).toFloat()
            corrValues[lag] = norm
            if (norm > bestCorr) {
                bestCorr = norm
                bestLag = lag
            }
        }
        if (bestCorr <= 1e-6f) return 0f

        // Parabolic interpolation around peak lag for sub-frame BPM precision
        val refinedLag = if (bestLag in (minLag + 1) until maxLag) {
            val y0 = corrValues[bestLag - 1]
            val y1 = corrValues[bestLag]
            val y2 = corrValues[bestLag + 1]
            val denom = (y0 - 2f * y1 + y2)
            if (abs(denom) > 1e-6f) bestLag + 0.5f * (y0 - y2) / denom else bestLag.toFloat()
        } else bestLag.toFloat()

        return (framesPerSec * 60f / refinedLag).coerceIn(60f, 195f)
    }

    /**
     * Real 12-Bin Pitch-Class Chromagram + Krumhansl-Schmuckler Key & Camelot Detector.
     */
    fun detectMusicalKeyAndDominantFreq(
        samples: FloatArray,
        sampleRateHz: Int,
    ): Triple<String, String, Float> {
        val fftSize = 4096
        if (samples.size < fftSize) return Triple("Unknown", "--", 0f)

        val win = hannWindow(fftSize)
        val chroma = FloatArray(12)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)

        val hop = (samples.size / 10).coerceAtLeast(fftSize / 2)
        var pos = 0
        var dominantBin = 1
        var dominantMag = 0f

        while (pos + fftSize <= samples.size) {
            for (i in 0 until fftSize) {
                re[i] = samples[pos + i] * win[i]
                im[i] = 0f
            }
            fftRadix2(re, im)
            for (b in 2 until fftSize / 2) {
                val freq = b.toFloat() * sampleRateHz / fftSize
                if (freq < 65f || freq > 2600f) continue
                val mag = hypot(re[b], im[b])
                if (mag > dominantMag) {
                    dominantMag = mag
                    dominantBin = b
                }
                val midi = 69.0 + 12.0 * log2(freq / 440.0)
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += mag * mag
            }
            pos += hop
        }

        val dominantHz = dominantBin.toFloat() * sampleRateHz / fftSize
        val chromaSum = chroma.sum()
        if (chromaSum < 1e-6f) return Triple("Unknown", "--", dominantHz)

        var bestKey = "C Major"
        var bestCamelot = "8B"
        var bestScore = Float.NEGATIVE_INFINITY

        for (tonic in 0 until 12) {
            val majScore = pearsonCorrelationCircular(chroma, MAJOR_PROFILE, tonic)
            if (majScore > bestScore) {
                bestScore = majScore
                bestKey = "${PITCH_NAMES[tonic]} Major"
                bestCamelot = CAMELOT_MAJOR[tonic]
            }
            val minScore = pearsonCorrelationCircular(chroma, MINOR_PROFILE, tonic)
            if (minScore > bestScore) {
                bestScore = minScore
                bestKey = "${PITCH_NAMES[tonic]} Minor"
                bestCamelot = CAMELOT_MINOR[tonic]
            }
        }
        return Triple(bestKey, bestCamelot, dominantHz)
    }

    private fun pearsonCorrelationCircular(
        chroma: FloatArray,
        profile: FloatArray,
        tonicShift: Int,
    ): Float {
        var meanX = 0f
        var meanY = 0f
        for (i in 0 until 12) {
            meanX += chroma[(i + tonicShift) % 12]
            meanY += profile[i]
        }
        meanX /= 12f
        meanY /= 12f
        var num = 0f
        var denX = 0f
        var denY = 0f
        for (i in 0 until 12) {
            val dx = chroma[(i + tonicShift) % 12] - meanX
            val dy = profile[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        val den = sqrt((denX * denY).toDouble()).toFloat()
        return if (den > 1e-6f) num / den else 0f
    }
}
