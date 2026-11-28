package com.example.core.music

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sqrt

data class DecodedAudioBuffer(
    val left: FloatArray,
    val right: FloatArray?, // null when mono
    val sampleRateHz: Int,
    val totalDurationMs: Long,
    val audioInfo: DetectedAudioFileInfo,
)

/**
 * Real Android Hardware Audio I/O Layer:
 *  - Decodes any local Uri or cached File (MP4/M4A/MP3/WAV/OGG/WebM) into stereo/mono float PCM
 *    via `MediaExtractor` + `MediaCodec` (detects absence of audio tracks cleanly).
 *  - Captures real live microphone PCM via `AudioRecord` with real-time RMS & 32-band FFT callbacks.
 *  - Plays back DSP-conditioned / Vocal-Isolated / Instrumental-Isolated clips via `AudioTrack`.
 */
object AudioPcmDecoder {

    /**
     * Inspects a local file or Uri to check whether an audio track exists and returns real track info.
     */
    suspend fun inspectLocalAudioTrack(
        context: Context,
        uri: Uri? = null,
        file: File? = null,
    ): DetectedAudioFileInfo = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            when {
                file != null -> extractor.setDataSource(file.absolutePath)
                uri != null -> extractor.setDataSource(context, uri, null)
                else -> return@withContext DetectedAudioFileInfo(hasAudioTrack = false)
            }
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    val sampleRate = fmt.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
                    val channels = fmt.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
                    val bitrate = fmt.getIntegerOrDefault(MediaFormat.KEY_BIT_RATE, 0) / 1000
                    val codecName = mime.substringAfter("audio/").uppercase()
                    val sizeBytes = file?.length() ?: -1L
                    return@withContext DetectedAudioFileInfo(
                        hasAudioTrack = true,
                        formatId = "track_$i",
                        codec = codecName,
                        containerExt = file?.extension?.lowercase().orEmpty().ifBlank { codecToExt(codecName) },
                        bitrateKbps = bitrate,
                        sampleRateHz = sampleRate,
                        channels = channels,
                        fileSizeBytes = sizeBytes,
                        isAudioOnlyStream = extractor.trackCount == 1,
                        note = mime,
                    )
                }
            }
            DetectedAudioFileInfo(hasAudioTrack = false, note = "Video contains no audio track")
        } catch (t: Throwable) {
            DetectedAudioFileInfo(hasAudioTrack = false, note = t.message ?: "Unable to read audio track")
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Decodes up to [maxDurationMs] of PCM audio starting at [startMs] using Android MediaExtractor + MediaCodec.
     */
    suspend fun decodeToPcm(
        context: Context,
        uri: Uri? = null,
        file: File? = null,
        startMs: Long = 0L,
        maxDurationMs: Long = 18_000L,
    ): DecodedAudioBuffer? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            when {
                file != null -> extractor.setDataSource(file.absolutePath)
                uri != null -> extractor.setDataSource(context, uri, null)
                else -> return@withContext null
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext DecodedAudioBuffer(
                    left = FloatArray(0),
                    right = null,
                    sampleRateHz = 44100,
                    totalDurationMs = 0L,
                    audioInfo = DetectedAudioFileInfo(hasAudioTrack = false, note = "No audio track found"),
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            var sampleRate = audioFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = audioFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 8)
            val bitrateKbps = audioFormat.getIntegerOrDefault(MediaFormat.KEY_BIT_RATE, 0) / 1000
            val totalDurationMs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                (audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000L).coerceAtLeast(0L)
            } else 0L

            if (startMs > 0L) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val maxFrames = ((maxDurationMs / 1000.0) * sampleRate).toInt().coerceIn(sampleRate, sampleRate * 30)
            val leftOut = FloatArray(maxFrames)
            val rightOut = if (channels >= 2) FloatArray(maxFrames) else null
            var writtenFrames = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val endUs = if (startMs > 0L) (startMs + maxDurationMs) * 1000L else maxDurationMs * 1000L

            while (!outputDone && writtenFrames < maxFrames && currentCoroutineContext().isActive) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val ptsUs = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                            extractor.advance()
                            if (ptsUs > endUs + 500_000L) {
                                inputDone = true
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val totalShorts = shortBuf.remaining()
                            val frameCount = totalShorts / channels
                            val toCopy = min(frameCount, maxFrames - writtenFrames)

                            if (channels == 1) {
                                for (f in 0 until toCopy) {
                                    leftOut[writtenFrames++] = shortBuf.get() / 32768f
                                }
                            } else {
                                val rArr = rightOut
                                for (f in 0 until toCopy) {
                                    val l = shortBuf.get() / 32768f
                                    val r = shortBuf.get() / 32768f
                                    for (extra in 2 until channels) shortBuf.get()
                                    leftOut[writtenFrames] = l
                                    if (rArr != null) rArr[writtenFrames] = r
                                    writtenFrames++
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        sampleRate = newFmt.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = newFmt.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceIn(1, 8)
                    }
                }
            }

            val codecName = mime.substringAfter("audio/").uppercase()
            val info = DetectedAudioFileInfo(
                hasAudioTrack = true,
                formatId = "track_$audioTrackIndex",
                codec = codecName,
                containerExt = file?.extension?.lowercase().orEmpty().ifBlank { codecToExt(codecName) },
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRate,
                channels = channels,
                fileSizeBytes = file?.length() ?: -1L,
                isAudioOnlyStream = extractor.trackCount == 1,
                note = mime,
            )

            DecodedAudioBuffer(
                left = leftOut.copyOf(writtenFrames),
                right = rightOut?.copyOf(writtenFrames),
                sampleRateHz = sampleRate,
                totalDurationMs = totalDurationMs,
                audioInfo = info,
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Captures live ambient/music audio from the device microphone using `AudioRecord`.
     * Streams live normalized RMS (0..1), 32-band FFT bars, and elapsed seconds for real-time visuals.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordMicrophoneSample(
        durationSec: Float = 7.0f,
        sampleRateHz: Int = 16000,
        onLiveMeter: (elapsedSec: Float, rmsLevel: Float, spectrumBars: FloatArray) -> Unit,
    ): FloatArray = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding).coerceAtLeast(4096)
        val bufferSize = minBuf * 2

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED.takeIf { android.os.Build.VERSION.SDK_INT >= 24 }
                ?: MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            channelConfig,
            encoding,
            bufferSize,
        ).let { rec ->
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec
            else {
                rec.release()
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    channelConfig,
                    encoding,
                    bufferSize,
                )
            }
        }

        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Microphone could not be initialized. Check RECORD_AUDIO permission."
        }

        val totalSamples = (durationSec * sampleRateHz).toInt().coerceAtLeast(sampleRateHz * 2)
        val collected = FloatArray(totalSamples)
        var written = 0
        val chunk = ShortArray(1024)
        val chunkFloat = FloatArray(1024)

        try {
            recorder.startRecording()
            while (written < totalSamples && currentCoroutineContext().isActive) {
                val read = recorder.read(chunk, 0, min(chunk.size, totalSamples - written))
                if (read > 0) {
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val f = chunk[i] / 32768f
                        collected[written + i] = f
                        chunkFloat[i] = f
                        sumSq += f * f
                    }
                    written += read
                    val rms = sqrt(sumSq / read).toFloat()
                    val bars = AudioAcousticDspEngine.computeLiveSpectrumBars(
                        pcm = if (read == chunkFloat.size) chunkFloat else chunkFloat.copyOf(read),
                        sampleRateHz = sampleRateHz,
                        bandCount = 32,
                    )
                    onLiveMeter(written.toFloat() / sampleRateHz.toFloat(), (rms * 3.2f).coerceIn(0f, 1f), bars)
                }
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        collected.copyOf(written)
    }

    /**
     * Plays a conditioned/isolated float PCM buffer directly through Android `AudioTrack`
     * so the user can hear their cleaned / vocal-isolated / instrumental-isolated 2–4s sample.
     */
    suspend fun playPcmSamples(
        samples: FloatArray,
        sampleRateHz: Int,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) {
            onFinished()
            return@withContext
        }
        val pcm16 = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            var offset = 0
            val chunkSize = 2048
            while (offset < pcm16.size && currentCoroutineContext().isActive) {
                val count = min(chunkSize, pcm16.size - offset)
                val written = track.write(pcm16, offset, count)
                if (written <= 0) break
                offset += written
            }
            // Wait briefly for trailing buffer drain
            track.stop()
        } finally {
            runCatching { track.release() }
            onFinished()
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(default) else default

    private fun codecToExt(codec: String): String = when {
        codec.contains("MP4A") || codec.contains("AAC") -> "m4a"
        codec.contains("MPEG") -> "mp3"
        codec.contains("OPUS") -> "opus"
        codec.contains("VORBIS") -> "ogg"
        codec.contains("FLAC") -> "flac"
        else -> "wav"
    }
}
