package com.example.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class DecodedMonoAudio(
    val samples: FloatArray,
    val sampleRate: Int,
)

data class SynthesizedVoiceOutput(
    val wavFile: File,
    val durationMs: Long,
    val sampleRate: Int,
    val waveformBars: List<Float>,
)

/**
 * 100% Free, Production-Grade On-Device Voice Synthesis & Acoustic Voice Cloning DSP Engine.
 *
 * Implements from scratch:
 *  1. Universal Audio File Decoder (WAV / MP3 / M4A / AAC / OGG / FLAC via MediaExtractor + MediaCodec)
 *  2. Acoustic Voice Cloning Fingerprint Analyzer:
 *     - Normalized Autocorrelation Pitch Tracker (F0 Hz + pitch variance)
 *     - 3-Band Biquad Spectral Energy Profile (Warmth / Presence / Air)
 *     - Breathiness (Zero-Crossing / HNR proxy) & Syllable Cadence Estimator
 *  3. Punctuation-Aware Phrasing & Prosody Pause Synthesizer via on-device TextToSpeech
 *  4. Full Studio DSP Mastering Chain:
 *     - Cubic Hermite Pitch/Formant Resampling
 *     - 3-Band Audio EQ Cookbook 2nd-Order IIR Biquad Filters (Low Shelf, Mid Peak, High Shelf)
 *     - Stability Prosody Modulation & Analog Tube Warmth (tanh)
 *     - Broadcast RMS Soft-Knee Compressor / Limiter (-1 dBFS peak)
 *     - Schroeder Multi-Tap Comb Studio Room Reverb
 */
object VoiceStudioDspEngine {

    private const val REFERENCE_FEMALE_F0 = 195f
    private const val REFERENCE_MALE_F0 = 122f

    // -------------------------------------------------------------------------
    // 1. REAL VOICE CLONING: ACOUSTIC FINGERPRINT EXTRACTION
    // -------------------------------------------------------------------------

    fun analyzeVoiceSamplePcm(
        samples: FloatArray,
        sampleRate: Int,
        voiceName: String,
        sourceLabel: String,
    ): Pair<VoiceProfile, ClonedVoiceAnalysis> {
        require(samples.size > sampleRate / 4) { "Voice sample is too short. Record or upload at least 1 second of clear speech." }

        val safeSr = sampleRate.coerceIn(8000, 48000)
        val durationSec = samples.size.toFloat() / safeSr.toFloat()

        // Extract voiced frames (30 ms window, 15 ms hop) and estimate F0 via normalized autocorrelation
        val winSize = (safeSr * 0.030f).roundToInt().coerceAtLeast(256)
        val hopSize = winSize / 2
        val minLag = (safeSr / 360f).roundToInt().coerceAtLeast(16) // 360 Hz max speech F0
        val maxLag = (safeSr / 65f).roundToInt().coerceAtMost(winSize - 2) // 65 Hz min speech F0

        val detectedF0s = ArrayList<Float>()
        var zcrSum = 0f
        var voicedCount = 0
        var activeSyllableTransitions = 0
        var prevActive = false

        var pos = 0
        while (pos + winSize < samples.size) {
            var energy = 0.0
            var zcr = 0
            for (i in 0 until winSize) {
                val s = samples[pos + i]
                energy += s * s
                if (i > 0 && ((s >= 0f) != (samples[pos + i - 1] >= 0f))) zcr++
            }
            val rms = sqrt(energy / winSize).toFloat()
            val isActive = rms > 0.018f
            if (isActive && !prevActive) activeSyllableTransitions++
            prevActive = isActive

            val zcrRate = zcr.toFloat() / winSize.toFloat()
            if (rms > 0.022f && zcrRate < 0.24f) {
                voicedCount++
                zcrSum += zcrRate
                val f0 = estimateFrameF0(samples, pos, winSize, minLag, maxLag, safeSr)
                if (f0 in 70f..340f) {
                    detectedF0s.add(f0)
                }
            }
            pos += hopSize
        }

        val medianF0 = if (detectedF0s.isNotEmpty()) {
            val sorted = detectedF0s.sorted()
            sorted[sorted.size / 2]
        } else {
            155f
        }
        val pitchStd = if (detectedF0s.size > 4) {
            val mean = detectedF0s.average()
            val variance = detectedF0s.sumOf { (it - mean) * (it - mean) } / detectedF0s.size
            sqrt(variance).toFloat()
        } else {
            18f
        }

        // 3-Band Spectral Energy Measurement using real 2nd-order Biquad filters
        val lowBand = applyBiquadFilter(samples, BiquadCoeffs.lowPass(350f, 0.707f, safeSr))
        val highBand = applyBiquadFilter(samples, BiquadCoeffs.highPass(2400f, 0.707f, safeSr))
        var lowE = 1e-6
        var midE = 1e-6
        var highE = 1e-6
        for (i in samples.indices) {
            val l = lowBand[i]
            val h = highBand[i]
            val m = samples[i] - l - h
            lowE += l * l
            midE += m * m
            highE += h * h
        }
        val totalE = lowE + midE + highE
        val lowRatio = (lowE / totalE).toFloat()
        val midRatio = (midE / totalE).toFloat()
        val highRatio = (highE / totalE).toFloat()

        val avgZcr = if (voicedCount > 0) zcrSum / voicedCount else 0.08f
        val breathiness = ((avgZcr - 0.06f) * 1.4f + highRatio * 0.4f).coerceIn(0.02f, 0.30f)

        val syllablesPerSec = (activeSyllableTransitions / durationSec.coerceAtLeast(0.5f)).coerceIn(1.2f, 6.5f)
        val tempoRatio = (syllablesPerSec / 3.2f).coerceIn(0.84f, 1.18f)

        // Map extracted F0 + 3-band spectral tilt to calibrated synthesis + DSP parameters
        val preferLow = medianF0 < 158f
        val refF0 = if (preferLow) REFERENCE_MALE_F0 else REFERENCE_FEMALE_F0
        val semitoneDelta = (12.0 * ln((medianF0 / refF0).toDouble()) / ln(2.0)).toFloat().coerceIn(-6.5f, 6.5f)
        val ttsPitch = (2.0.pow((semitoneDelta * 0.55f / 12f).toDouble())).toFloat().coerceIn(0.68f, 1.38f)
        val dspPitch = (semitoneDelta * 0.45f).coerceIn(-4.0f, 4.0f)

        // Derive matching 3-band EQ curve relative to neutral vocal reference (low 0.32, mid 0.52, high 0.16)
        val lowShelfDb = ((lowRatio - 0.32f) * 18f).coerceIn(-4.5f, 6.5f)
        val midPeakDb = ((midRatio - 0.50f) * 14f).coerceIn(-3.5f, 5.0f)
        val highShelfDb = ((highRatio - 0.16f) * 20f).coerceIn(-4.0f, 6.0f)
        val warmthDrive = (lowRatio * 0.55f).coerceIn(0.08f, 0.34f)

        val descriptor = when {
            medianF0 < 115f -> "Deep Baritone"
            medianF0 < 152f -> "Warm Tenor"
            medianF0 < 200f -> "Rich Alto"
            else -> "Bright Soprano"
        }

        val analysis = ClonedVoiceAnalysis(
            fundamentalHz = medianF0,
            pitchStdDevHz = pitchStd,
            lowEnergyRatio = lowRatio,
            midEnergyRatio = midRatio,
            highEnergyRatio = highRatio,
            breathiness = breathiness,
            speakingTempoRatio = tempoRatio,
            durationSec = durationSec,
            voiceDescriptor = descriptor,
        )

        val profile = VoiceProfile(
            id = "cloned_${System.currentTimeMillis()}",
            name = voiceName.trim().ifBlank { "Cloned Voice" },
            tagline = "$descriptor • ${medianF0.roundToInt()} Hz acoustic clone",
            category = VoiceCategory.CLONED,
            isCloned = true,
            targetF0Hz = medianF0,
            ttsBasePitch = ttsPitch,
            ttsBaseRate = tempoRatio,
            dspPitchSemitones = dspPitch,
            lowShelfDb = lowShelfDb,
            midPeakDb = midPeakDb,
            highShelfDb = highShelfDb,
            breathiness = breathiness,
            warmthDrive = warmthDrive,
            defaultRoomWet = 0.05f,
            localeTag = Locale.getDefault().toLanguageTag(),
            preferLowVoice = preferLow,
            sourceSummary = "$sourceLabel • F0 ${medianF0.roundToInt()} Hz • $descriptor",
        )

        return Pair(profile, analysis)
    }

    private fun estimateFrameF0(
        samples: FloatArray,
        offset: Int,
        winSize: Int,
        minLag: Int,
        maxLag: Int,
        sampleRate: Int,
    ): Float {
        val limit = winSize - maxLag
        if (limit < 32 || maxLag <= minLag) return 0f

        val count = maxLag - minLag + 1
        val corrs = DoubleArray(count)
        var globalMax = 0.0

        for (lag in minLag..maxLag) {
            var num = 0.0
            var denomA = 0.0
            var denomB = 0.0
            for (i in 0 until limit) {
                val a = samples[offset + i].toDouble()
                val b = samples[offset + i + lag].toDouble()
                num += a * b
                denomA += a * a
                denomB += b * b
            }
            val denom = sqrt(denomA * denomB)
            val corr = if (denom > 1e-6) num / denom else 0.0
            corrs[lag - minLag] = corr
            if (corr > globalMax) globalMax = corr
        }

        if (globalMax < 0.38) return 0f

        // Pick the FIRST local peak >= 88% of globalMax (prevents octave-halving on 2x period subharmonics)
        val threshold = globalMax * 0.88
        var bestLag = -1
        for (i in 1 until count - 1) {
            if (corrs[i] >= threshold && corrs[i] >= corrs[i - 1] && corrs[i] >= corrs[i + 1]) {
                bestLag = minLag + i
                break
            }
        }
        if (bestLag < 0) {
            var idxMax = 0
            for (i in 1 until count) {
                if (corrs[i] > corrs[idxMax]) idxMax = i
            }
            bestLag = minLag + idxMax
        }
        return if (bestLag > 0) sampleRate.toFloat() / bestLag.toFloat() else 0f
    }

    /**
     * Decodes any user-uploaded audio file (MP3, WAV, M4A, AAC, OGG, FLAC) into mono FloatArray PCM
     * via Android MediaExtractor + MediaCodec. Caps analysis cleanly at first 15 seconds for speed.
     */
    suspend fun decodeAudioUriToMonoPcm(
        context: Context,
        uri: Uri,
        maxDurationSec: Float = 15f,
    ): DecodedMonoAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = fmt
                    break
                }
            }
            require(audioTrack >= 0 && format != null) { "Selected file does not contain a readable audio track." }

            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 22050
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val maxSamples = (sampleRate * maxDurationSec).roundToInt()

            val pcmOut = ArrayList<Float>(min(maxSamples, sampleRate * 6))

            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                val buf = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)
                while (pcmOut.size < maxSamples) {
                    val read = extractor.readSampleData(buf, 0)
                    if (read <= 0) break
                    buf.limit(read)
                    buf.position(0)
                    appendPcm16ToMono(buf, channelCount, pcmOut, maxSamples)
                    buf.clear()
                    if (!extractor.advance()) break
                }
                return@withContext DecodedMonoAudio(pcmOut.toFloatArray(), sampleRate)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && pcmOut.size < maxSamples) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            appendPcm16ToMono(outBuf, channelCount, pcmOut, maxSamples)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
            DecodedMonoAudio(pcmOut.toFloatArray(), sampleRate)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
                // Ignore
            }
            extractor.release()
        }
    }

    private fun appendPcm16ToMono(
        buffer: ByteBuffer,
        channels: Int,
        dst: ArrayList<Float>,
        maxSamples: Int,
    ) {
        val ch = channels.coerceAtLeast(1)
        val frameBytes = ch * 2
        while (buffer.remaining() >= frameBytes && dst.size < maxSamples) {
            var sum = 0f
            for (c in 0 until ch) {
                sum += buffer.short / 32768f
            }
            dst.add(sum / ch)
        }
    }

    // -------------------------------------------------------------------------
    // 2. LIVE MICROPHONE PCM RECORDER FOR VOICE CLONING
    // -------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun recordMicSamplePcm(
        isRecordingFlag: () -> Boolean,
        onLevel: (Float) -> Unit,
        sampleRate: Int = 22050,
        maxSeconds: Int = 12,
    ): DecodedMonoAudio = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Microphone initialization failed on this device."
        }

        val maxSamples = sampleRate * maxSeconds
        val out = ArrayList<Float>(sampleRate * 4)
        val chunk = ShortArray(1024)

        try {
            recorder.startRecording()
            while (isRecordingFlag() && out.size < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val f = chunk[i] / 32768f
                        out.add(f)
                        sumSq += f * f
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    onLevel((rms * 4.5f).coerceIn(0f, 1f))
                }
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
                // Ignore
            }
            recorder.release()
        }
        DecodedMonoAudio(out.toFloatArray(), sampleRate)
    }

    // -------------------------------------------------------------------------
    // 3. TEXT-TO-SPEECH CLAUSE SYNTHESIS + FULL PRODUCTION DSP CHAIN
    // -------------------------------------------------------------------------

    data class ScriptClause(val text: String, val trailingPauseMs: Int)

    fun splitScriptIntoClauses(script: String, phrasing: PhrasingStyle): List<ScriptClause> {
        val normalized = script.trim()
        if (normalized.isEmpty()) return emptyList()
        val clauses = mutableListOf<ScriptClause>()
        val sb = StringBuilder()

        for (i in normalized.indices) {
            val ch = normalized[i]
            if (ch == '\n') {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) clauses.add(ScriptClause(t, phrasing.paragraphPauseMs))
                sb.clear()
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?') {
                    val t = sb.toString().trim()
                    if (t.isNotEmpty()) clauses.add(ScriptClause(t, phrasing.sentencePauseMs))
                    sb.clear()
                } else if (ch == ',' || ch == ';' || ch == ':' || ch == '—') {
                    if (sb.length >= 18) {
                        val t = sb.toString().trim()
                        if (t.isNotEmpty()) clauses.add(ScriptClause(t, phrasing.commaPauseMs))
                        sb.clear()
                    }
                }
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) {
            clauses.add(ScriptClause(tail, 120))
        }
        return clauses
    }

    suspend fun synthesizeVoiceToWav(
        context: Context,
        script: String,
        profile: VoiceProfile,
        params: VoiceGenerationParams,
        outputWavFile: File,
        onProgress: (Float) -> Unit,
    ): SynthesizedVoiceOutput = withContext(Dispatchers.IO) {
        val clauses = splitScriptIntoClauses(script, params.phrasing)
        require(clauses.isNotEmpty()) { "Enter some text to generate speech." }

        val tts = initTextToSpeech(context)
        val tempFiles = mutableListOf<File>()
        try {
            configureTtsVoice(tts, profile, params)
            val stitchedSamples = ArrayList<Float>(44100 * 4)
            var workingSampleRate = 22050

            clauses.forEachIndexed { idx, clause ->
                onProgress(0.08f + (idx.toFloat() / clauses.size.toFloat()) * 0.52f)
                val tempWav = File(context.cacheDir, "tts_clause_${System.currentTimeMillis()}_$idx.wav")
                tempFiles.add(tempWav)
                synthesizeSingleUtteranceToFile(tts, clause.text, tempWav)
                val decoded = readWavToMonoFloat(tempWav)
                if (decoded != null && decoded.samples.isNotEmpty()) {
                    workingSampleRate = decoded.sampleRate
                    val trimmed = trimLeadingTrailingSilence(decoded.samples, workingSampleRate)
                    for (s in trimmed) stitchedSamples.add(s)
                    val pauseSamples = ((clause.trailingPauseMs / 1000f) * workingSampleRate).roundToInt()
                    repeat(pauseSamples) { stitchedSamples.add(0f) }
                }
            }

            require(stitchedSamples.isNotEmpty()) {
                "Device speech engine returned empty audio. Ensure a Text-to-Speech voice is enabled in Android Settings."
            }

            onProgress(0.68f)
            val mastered = applyVoiceStudioDspChain(
                input = stitchedSamples.toFloatArray(),
                sampleRate = workingSampleRate,
                profile = profile,
                params = params,
            )

            onProgress(0.90f)
            writeMonoFloatToWav16(mastered, workingSampleRate, outputWavFile)
            val bars = computeWaveformBars(mastered, barCount = 64)
            val durationMs = ((mastered.size.toLong() * 1000L) / workingSampleRate.toLong()).coerceAtLeast(100L)
            onProgress(1f)

            SynthesizedVoiceOutput(
                wavFile = outputWavFile,
                durationMs = durationMs,
                sampleRate = workingSampleRate,
                waveformBars = bars,
            )
        } finally {
            tts.stop()
            tts.shutdown()
            tempFiles.forEach { it.delete() }
        }
    }

    /**
     * Full Studio DSP Chain (100% pure Kotlin math, zero external paid dependencies):
     *  1. Cubic-interpolated Pitch & Formant Shifting (with tempo compensation)
     *  2. 3-Band Biquad EQ (Low-Shelf Warmth, Mid Presence Peak, High-Shelf Air)
     *  3. Stability Prosody Micro-Modulation + Optional Cyber AI Ring Modulator
     *  4. Analog Tube Warmth (tanh saturation) & Broadcast RMS Soft-Knee Compressor
     *  5. Schroeder Multi-Tap Comb Studio Room Reverb
     *  6. Peak Normalization to -1.0 dBFS
     */
    fun applyVoiceStudioDspChain(
        input: FloatArray,
        sampleRate: Int,
        profile: VoiceProfile,
        params: VoiceGenerationParams,
    ): FloatArray {
        if (input.isEmpty()) return input

        // 1. Pitch / Formant shift (preserves playback duration via overlap-add granular resampling)
        val totalSemitones = (profile.dspPitchSemitones + params.pitchSemitones).coerceIn(-10f, 10f)
        var signal = if (abs(totalSemitones) > 0.12f) {
            pitchShiftGranularOlp(input, sampleRate, totalSemitones)
        } else {
            input.copyOf()
        }

        // 2. 3-Band Audio EQ Cookbook 2nd-order Biquad Filter Bank
        val eqScale = 0.55f + params.clarityBoost * 0.65f + params.styleExaggeration * 0.35f
        val lowDb = (profile.lowShelfDb * eqScale).coerceIn(-9f, 9f)
        val midDb = ((profile.midPeakDb + (params.clarityBoost - 0.5f) * 3.5f) * eqScale).coerceIn(-8f, 8f)
        val highDb = ((profile.highShelfDb + (params.clarityBoost - 0.4f) * 3.0f) * eqScale).coerceIn(-8f, 9f)

        if (abs(lowDb) > 0.15f) {
            signal = applyBiquadFilter(signal, BiquadCoeffs.lowShelf(220f, lowDb, sampleRate))
        }
        if (abs(midDb) > 0.15f) {
            signal = applyBiquadFilter(signal, BiquadCoeffs.peakingEq(1650f, 1.1f, midDb, sampleRate))
        }
        if (abs(highDb) > 0.15f) {
            signal = applyBiquadFilter(signal, BiquadCoeffs.highShelf(5200f, highDb, sampleRate))
        }

        // 3. Stability / Expressiveness Modulation + Breath Air + Optional Cyber AI Ring Mod
        val expressiveness = (1f - params.stability.coerceIn(0f, 1f)) * 0.16f
        val breathAmt = profile.breathiness * (0.5f + params.styleExaggeration)
        val cyberHz = profile.cyberModHz
        var lcg = 0x5A17C3B1

        for (i in signal.indices) {
            var s = signal[i]
            if (expressiveness > 0.005f) {
                // Smooth 1.8 Hz expressive dynamic phrasing contour
                val mod = 1f + expressiveness * sin(2.0 * PI * 1.8 * i / sampleRate).toFloat()
                s *= mod
            }
            if (breathAmt > 0.02f) {
                // Modulated high-frequency vocal breathiness proportional to speech envelope
                lcg = lcg * 1664525 + 1013904223
                val white = ((lcg ushr 16) and 0xFFFF) / 32768f - 1f
                s += white * abs(s) * breathAmt * 0.22f
            }
            if (cyberHz > 1f) {
                val carrier = 0.78f + 0.22f * sin(2.0 * PI * cyberHz * i / sampleRate).toFloat()
                s *= carrier
            }
            signal[i] = s
        }

        // 4. Analog Tube Warmth + Broadcast RMS Compressor/Limiter
        val drive = 1f + (profile.warmthDrive * (0.6f + params.styleExaggeration * 0.9f))
        var env = 0.05f
        val attack = 1f - kotlin.math.exp(-1f / (0.008f * sampleRate))
        val release = 1f - kotlin.math.exp(-1f / (0.120f * sampleRate))
        for (i in signal.indices) {
            val x = signal[i] * drive
            val absX = abs(x)
            env += (absX - env) * if (absX > env) attack else release
            val compGain = if (env > 0.25f) (0.25f + (env - 0.25f) * 0.35f) / env else 1f
            signal[i] = tanh((x * compGain).toDouble()).toFloat()
        }

        // 5. Schroeder Multi-Tap Comb Studio Room Reverb
        val roomWet = max(params.roomPreset.wetMix, profile.defaultRoomWet * params.styleExaggeration).coerceIn(0f, 0.38f)
        if (roomWet > 0.015f) {
            val decay = max(params.roomPreset.decay, 0.36f)
            signal = applyStudioReverb(signal, sampleRate, roomWet, decay)
        }

        // 6. Peak Normalize cleanly to -1.0 dBFS (~0.89)
        var peak = 0.0001f
        for (s in signal) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        val normGain = (0.89f / peak).coerceAtMost(4.5f)
        for (i in signal.indices) {
            signal[i] = (signal[i] * normGain).coerceIn(-0.98f, 0.98f)
        }
        return signal
    }

    /**
     * Granular Hann-windowed Overlap-Add Pitch Shifter with Cubic Interpolation.
     * Shifts voice pitch/formants in semitones while preserving original duration!
     */
    private fun pitchShiftGranularOlp(
        input: FloatArray,
        sampleRate: Int,
        semitones: Float,
    ): FloatArray {
        val ratio = 2.0.pow(semitones.toDouble() / 12.0).toFloat()
        val grainSize = (sampleRate * 0.044f).roundToInt().coerceIn(512, 2048)
        val hop = grainSize / 2
        val output = FloatArray(input.size)
        val norm = FloatArray(input.size)
        val window = FloatArray(grainSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (grainSize - 1)))).toFloat()
        }

        var pos = 0
        while (pos < input.size) {
            for (k in 0 until grainSize) {
                val outIdx = pos + k
                if (outIdx >= output.size) break
                val readPos = pos + (k * ratio)
                val sample = sampleCubic(input, readPos)
                val w = window[k]
                output[outIdx] += sample * w
                norm[outIdx] += w
            }
            pos += hop
        }

        for (i in output.indices) {
            if (norm[i] > 0.001f) {
                output[i] /= norm[i]
            }
        }
        return output
    }

    private fun sampleCubic(data: FloatArray, pos: Float): Float {
        val i = pos.toInt()
        if (i < 1 || i + 2 >= data.size) {
            return data[i.coerceIn(0, data.lastIndex)]
        }
        val t = pos - i
        val y0 = data[i - 1]
        val y1 = data[i]
        val y2 = data[i + 1]
        val y3 = data[i + 2]
        val a0 = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3
        val a1 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3
        val a2 = -0.5f * y0 + 0.5f * y2
        return ((a0 * t + a1) * t + a2) * t + y1
    }

    private fun applyStudioReverb(
        input: FloatArray,
        sampleRate: Int,
        wet: Float,
        decay: Float,
    ): FloatArray {
        val tapDelaysMs = floatArrayOf(19.3f, 27.7f, 36.1f, 44.9f)
        val tapGains = floatArrayOf(decay, decay * 0.88f, decay * 0.76f, decay * 0.65f)
        val wetAccum = FloatArray(input.size)

        for (t in tapDelaysMs.indices) {
            val dSamples = ((tapDelaysMs[t] / 1000f) * sampleRate).roundToInt().coerceAtLeast(16)
            val fb = tapGains[t].coerceIn(0.05f, 0.72f)
            val delayLine = FloatArray(dSamples)
            var ptr = 0
            for (i in input.indices) {
                val delayed = delayLine[ptr]
                delayLine[ptr] = input[i] + delayed * fb
                wetAccum[i] += delayed * 0.25f
                ptr++
                if (ptr >= dSamples) ptr = 0
            }
        }
        val dry = 1f - wet * 0.45f
        return FloatArray(input.size) { i -> input[i] * dry + wetAccum[i] * wet }
    }

    fun computeWaveformBars(samples: FloatArray, barCount: Int = 64): List<Float> {
        if (samples.isEmpty()) return List(barCount) { 0.1f }
        val step = max(1, samples.size / barCount)
        val raw = FloatArray(barCount)
        var maxRms = 0.001f
        for (b in 0 until barCount) {
            val start = b * step
            val end = min(samples.size, start + step)
            var sumSq = 0.0
            for (i in start until end) {
                sumSq += samples[i] * samples[i]
            }
            val rms = sqrt(sumSq / max(1, end - start)).toFloat()
            raw[b] = rms
            if (rms > maxRms) maxRms = rms
        }
        return raw.map { (it / maxRms).coerceIn(0.08f, 1.0f) }
    }

    // -------------------------------------------------------------------------
    // 4. AUDIO EQ COOKBOOK 2ND-ORDER BIQUAD IIR FILTERS
    // -------------------------------------------------------------------------

    data class BiquadCoeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    ) {
        companion object {
            fun lowPass(freqHz: Float, q: Float, sampleRate: Int): BiquadCoeffs {
                val w0 = 2.0 * PI * freqHz / sampleRate
                val alpha = sin(w0) / (2.0 * q)
                val c = cos(w0)
                val a0 = 1.0 + alpha
                return BiquadCoeffs((1.0 - c) / 2.0 / a0, (1.0 - c) / a0, (1.0 - c) / 2.0 / a0, (-2.0 * c) / a0, (1.0 - alpha) / a0)
            }

            fun highPass(freqHz: Float, q: Float, sampleRate: Int): BiquadCoeffs {
                val w0 = 2.0 * PI * freqHz / sampleRate
                val alpha = sin(w0) / (2.0 * q)
                val c = cos(w0)
                val a0 = 1.0 + alpha
                return BiquadCoeffs((1.0 + c) / 2.0 / a0, -(1.0 + c) / a0, (1.0 + c) / 2.0 / a0, (-2.0 * c) / a0, (1.0 - alpha) / a0)
            }

            fun lowShelf(freqHz: Float, gainDb: Float, sampleRate: Int): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val c = cos(w0)
                val alpha = sin(w0) / 2.0 * sqrt(2.0)
                val sqA2 = 2.0 * sqrt(a) * alpha
                val a0 = (a + 1.0) + (a - 1.0) * c + sqA2
                return BiquadCoeffs(
                    b0 = (a * ((a + 1.0) - (a - 1.0) * c + sqA2)) / a0,
                    b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * c)) / a0,
                    b2 = (a * ((a + 1.0) - (a - 1.0) * c - sqA2)) / a0,
                    a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * c)) / a0,
                    a2 = ((a + 1.0) + (a - 1.0) * c - sqA2) / a0,
                )
            }

            fun highShelf(freqHz: Float, gainDb: Float, sampleRate: Int): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val c = cos(w0)
                val alpha = sin(w0) / 2.0 * sqrt(2.0)
                val sqA2 = 2.0 * sqrt(a) * alpha
                val a0 = (a + 1.0) - (a - 1.0) * c + sqA2
                return BiquadCoeffs(
                    b0 = (a * ((a + 1.0) + (a - 1.0) * c + sqA2)) / a0,
                    b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * c)) / a0,
                    b2 = (a * ((a + 1.0) + (a - 1.0) * c - sqA2)) / a0,
                    a1 = (2.0 * ((a - 1.0) - (a + 1.0) * c)) / a0,
                    a2 = ((a + 1.0) - (a - 1.0) * c - sqA2) / a0,
                )
            }

            fun peakingEq(freqHz: Float, q: Float, gainDb: Float, sampleRate: Int): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val alpha = sin(w0) / (2.0 * q)
                val c = cos(w0)
                val a0 = 1.0 + alpha / a
                return BiquadCoeffs(
                    b0 = (1.0 + alpha * a) / a0,
                    b1 = (-2.0 * c) / a0,
                    b2 = (1.0 - alpha * a) / a0,
                    a1 = (-2.0 * c) / a0,
                    a2 = (1.0 - alpha / a) / a0,
                )
            }
        }
    }

    fun applyBiquadFilter(samples: FloatArray, c: BiquadCoeffs): FloatArray {
        val out = FloatArray(samples.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in samples.indices) {
            val x0 = samples[i].toDouble()
            val y0 = c.b0 * x0 + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
            out[i] = y0.toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    // -------------------------------------------------------------------------
    // 5. ANDROID ON-DEVICE TTS + WAV I/O HELPERS
    // -------------------------------------------------------------------------

    private suspend fun initTextToSpeech(context: Context): TextToSpeech =
        suspendCancellableCoroutine { cont ->
            var ttsRef: TextToSpeech? = null
            ttsRef = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    if (cont.isActive) cont.resume(ttsRef!!)
                } else {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("On-device Text-to-Speech engine is unavailable."))
                }
            }
        }

    private fun configureTtsVoice(tts: TextToSpeech, profile: VoiceProfile, params: VoiceGenerationParams) {
        val targetLocale = Locale.forLanguageTag(profile.localeTag)
        val avail = tts.isLanguageAvailable(targetLocale)
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = targetLocale
        } else {
            tts.language = Locale.US
        }

        // Automatically pick distinct installed local TTS voices matching locale & low/high vocal preference
        try {
            val voices = tts.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == tts.voice?.locale?.language }
            if (!voices.isNullOrEmpty()) {
                val preferred = voices.firstOrNull { v ->
                    val n = v.name.lowercase(Locale.US)
                    if (profile.preferLowVoice) {
                        n.contains("male") || n.contains("iom") || n.contains("iol") || n.contains("tpd") || n.contains("rjs")
                    } else {
                        n.contains("female") || n.contains("iob") || n.contains("iog") || n.contains("sfg") || n.contains("tpf")
                    }
                } ?: voices[abs(profile.id.hashCode()) % voices.size]
                tts.voice = preferred
            }
        } catch (_: Exception) {
            // Keep default selected locale voice
        }

        val effectiveRate = (profile.ttsBaseRate * params.speed).coerceIn(0.45f, 2.2f)
        tts.setSpeechRate(effectiveRate)
        tts.setPitch(profile.ttsBasePitch.coerceIn(0.55f, 1.55f))
    }

    private suspend fun synthesizeSingleUtteranceToFile(
        tts: TextToSpeech,
        text: String,
        destFile: File,
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
            @Deprecated("Deprecated in Android API")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("TTS synthesis failed for clause."))
                }
            }
            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) {
                    cont.resumeWithException(IllegalStateException("TTS synthesis error code $errorCode."))
                }
            }
        })
        val res = tts.synthesizeToFile(text, Bundle(), destFile, utteranceId)
        if (res != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resumeWithException(IllegalStateException("Unable to invoke on-device speech synthesis."))
        }
    }

    private fun trimLeadingTrailingSilence(samples: FloatArray, sampleRate: Int): FloatArray {
        val threshold = 0.008f
        var start = 0
        while (start < samples.size && abs(samples[start]) < threshold) start++
        var end = samples.lastIndex
        while (end > start && abs(samples[end]) < threshold) end--
        val pad = (sampleRate * 0.025f).roundToInt()
        val s = (start - pad).coerceAtLeast(0)
        val e = (end + pad).coerceAtMost(samples.lastIndex)
        return if (e > s) samples.copyOfRange(s, e + 1) else samples
    }

    fun readWavToMonoFloat(file: File): DecodedMonoAudio? {
        if (!file.exists() || file.length() < 44L) return null
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var channels = 1
        var sampleRate = 22050
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLength = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(offset + 4)
            if (chunkId == "fmt " && offset + 24 <= bytes.size) {
                channels = bb.getShort(offset + 10).toInt().coerceAtLeast(1)
                sampleRate = bb.getInt(offset + 12).coerceIn(8000, 96000)
                bitsPerSample = bb.getShort(offset + 22).toInt()
            } else if (chunkId == "data") {
                dataOffset = offset + 8
                dataLength = min(chunkSize, bytes.size - dataOffset).coerceAtLeast(0)
                break
            }
            offset += 8 + chunkSize + (chunkSize and 1)
        }
        if (dataOffset < 0 || dataLength <= 0 || bitsPerSample != 16) return null

        val frameBytes = channels * 2
        val numFrames = dataLength / frameBytes
        val out = FloatArray(numFrames)
        var ptr = dataOffset
        for (f in 0 until numFrames) {
            var sum = 0f
            for (c in 0 until channels) {
                val sample = bb.getShort(ptr)
                sum += sample / 32768f
                ptr += 2
            }
            out[f] = sum / channels
        }
        return DecodedMonoAudio(out, sampleRate)
    }

    fun writeMonoFloatToWav16(samples: FloatArray, sampleRate: Int, outFile: File) {
        outFile.parentFile?.mkdirs()
        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                     // PCM subchunk size
            putShort(1)                    // PCM format
            putShort(1)                    // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2)         // Byte rate
            putShort(2)                    // Block align
            putShort(16)                   // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()

        FileOutputStream(outFile).use { fos ->
            fos.write(header)
            val pcmBuf = ByteBuffer.allocate(min(32768, max(2, dataBytes))).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                if (pcmBuf.remaining() < 2) {
                    fos.write(pcmBuf.array(), 0, pcmBuf.position())
                    pcmBuf.clear()
                }
                val v = (s.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                pcmBuf.putShort(v)
            }
            if (pcmBuf.position() > 0) {
                fos.write(pcmBuf.array(), 0, pcmBuf.position())
            }
            fos.flush()
        }
    }
}
