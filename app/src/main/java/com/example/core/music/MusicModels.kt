package com.example.core.music

/**
 * Shared domain models for Clipper's Song & Music Detector, Audio File Inspector,
 * Acoustic DSP Lab, Musical Telemetry, Synced Lyrics, and Offline Fingerprint Vault.
 */

enum class AudioDspFilterMode(
    val label: String,
    val shortDescription: String,
) {
    FULL_MIX("Full Mix", "Normalized original audio"),
    CLEAN_DENOISE("Noise Gate", "Spectral subtraction + 80Hz–14kHz bandpass"),
    VOCALS_MID("Vocals Only", "Mid-channel center isolation (280Hz–3.8kHz)"),
    INSTRUMENTAL_SIDE("Instrumental", "Side-channel backing isolation (cancels vocals)"),
}

enum class MusicMatchSource(val badgeLabel: String) {
    ACOUSTIC_FINGERPRINT("Acoustic Match"),
    OFFLINE_VAULT("Offline Vault"),
    VIDEO_METADATA("Video Audio Credits"),
    LYRIC_RECOGNITION("Vocal Lyric Match"),
    CATALOG_MATCH("Catalog Match"),
}

data class DetectedAudioFileInfo(
    val hasAudioTrack: Boolean,
    val formatId: String = "",
    val codec: String = "",
    val containerExt: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val fileSizeBytes: Long = -1L,
    val isAudioOnlyStream: Boolean = false,
    val note: String = "",
    val directStreamUrl: String? = null,
) {
    fun summaryLine(): String {
        if (!hasAudioTrack) return "No audio file detected"
        val parts = mutableListOf<String>()
        if (codec.isNotBlank()) parts += codec.uppercase()
        if (containerExt.isNotBlank()) parts += ".${containerExt.lowercase()}"
        if (bitrateKbps > 0) parts += "${bitrateKbps} kbps"
        if (sampleRateHz > 0) parts += "${String.format(java.util.Locale.US, "%.1f", sampleRateHz / 1000f)} kHz"
        if (channels > 0) parts += if (channels == 1) "Mono" else if (channels == 2) "Stereo" else "${channels}ch"
        return parts.joinToString(" • ").ifBlank { "Audio track present" }
    }
}

data class MusicalTelemetry(
    val bpm: Float,
    val musicalKey: String,
    val camelotCode: String,
    val loudnessLufs: Float,
    val peakDbfs: Float,
    val dynamicRangeDb: Float,
    val estimatedSnrDb: Float,
    val dominantFreqHz: Float,
    val constellationPeakCount: Int,
    val constellationHashCount: Int,
    val windowStartSec: Float = 0f,
    val windowDurationSec: Float = 0f,
)

data class SyncedLyricLine(
    val timeMs: Long,
    val text: String,
)

data class IdentifiedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val releaseYear: String? = null,
    val genre: String? = null,
    val durationSec: Int = 0,
    val artworkUrl: String? = null,
    val previewAudioUrl: String? = null,
    val externalUrl: String? = null,
    val isrc: String? = null,
    val matchSource: MusicMatchSource,
    val confidencePercent: Int,
    val matchedOffsetSec: Float? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: List<SyncedLyricLine> = emptyList(),
    val telemetry: MusicalTelemetry? = null,
    val detectedAtEpochMs: Long = System.currentTimeMillis(),
    val sourceLabel: String = "",
    val sourceUrl: String? = null,
)

data class ConstellationPeak(
    val timeFrame: Int,
    val freqBin: Int,
    val magnitude: Float,
)

data class ConstellationHash(
    val hash: Int,
    val anchorTimeFrame: Int,
)

data class ConstellationLandmarkPair(
    val t1Norm: Float,
    val f1Norm: Float,
    val t2Norm: Float,
    val f2Norm: Float,
)

data class SpectrogramFrameVisual(
    val timeColumns: List<FloatArray>, // Normalized 0..1 magnitudes per column (e.g. 48 cols x 32 bands)
    val peaksNorm: List<Pair<Float, Float>>, // (xNorm, yNorm) constellation stars
    val pairsNorm: List<ConstellationLandmarkPair>, // Anchor-to-target constellation lines
)

data class VaultTrackEntry(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Float,
    val sampleRateHz: Int,
    val createdAtEpochMs: Long,
    val hashes: List<ConstellationHash>,
)

data class VaultMatchResult(
    val entry: VaultTrackEntry,
    val coherentVotes: Int,
    val totalMatches: Int,
    val confidencePercent: Int,
    val matchedOffsetSec: Float,
)

data class SongDetectionResult(
    val primarySong: IdentifiedSong?,
    val alternates: List<IdentifiedSong> = emptyList(),
    val telemetry: MusicalTelemetry? = null,
    val spectrogram: SpectrogramFrameVisual? = null,
    val constellationHashes: List<ConstellationHash> = emptyList(),
    val diagnosticMessage: String = "",
    val vocalTranscriptHint: String? = null,
)
