package com.example.core.velocity

import android.net.Uri
import java.util.UUID

enum class VelocitySystemMode(val label: String) {
    AUTO_BEAT("Auto Velocity"),
    CUSTOM_CURVE("Custom Curve"),
}

enum class AutoVelocityFrequency(
    val label: String,
    val subtitle: String,
    val beatStride: Int,
    val minSpacingNormalized: Float,
) {
    EVERY_BEAT("Every Beat", "Punchy hit on every beat", 1, 0.055f),
    EVERY_2_BEATS("Every 2 Beats", "Balanced rhythmic flow", 2, 0.095f),
    STRONG_DROPS("Strong Drops", "Only major bass drops", 1, 0.145f),
    CHILL_WAVE("Chill Wave", "Wide cinematic pacing", 3, 0.18f),
}

enum class VelocityCurvePreset(val label: String) {
    MONTAGE("Montage"),
    HERO("Hero"),
    BULLET("Bullet"),
    JUMP_CUT("Jump Cut"),
    FLASH_IN("Flash In"),
    FLASH_OUT("Flash Out"),
    FLAT("Flat 1.0×"),
}

enum class VelocityAspectCrop(
    val label: String,
    val aspectRatio: Float?,
) {
    ORIGINAL("Original", null),
    VERTICAL_9_16("9:16", 9f / 16f),
    SQUARE_1_1("1:1", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    LANDSCAPE_16_9("16:9", 16f / 9f),
}

data class VelocityKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val t: Float,      // Normalized timeline position in 0f..1f
    val speed: Float,  // Instantaneous speed multiplier in 0.15f..5.0f
)

data class DetectedBeat(
    val timeMs: Long,
    val normalizedPos: Float, // 0f..1f within analyzed audio clip
    val strength: Float,      // 0f..1f relative onset intensity
    val isManual: Boolean = false,
)

data class AudioBeatAnalysis(
    val durationMs: Long,
    val bpm: Float,
    val beats: List<DetectedBeat>,
    val waveformBars: List<Float>, // 0f..1f normalized visual bars
)

data class AutoVelocitySettings(
    val isAutoTuning: Boolean = true,
    val frequency: AutoVelocityFrequency = AutoVelocityFrequency.EVERY_2_BEATS,
    val smoothness: Float = 0.76f,     // 0.15f (snappy) .. 1.0f (ultra-smooth cinematic)
    val maxSlowMoSpeed: Float = 0.30f, // 0.15f .. 0.75f
    val maxFastSpeed: Float = 2.50f,   // 1.40f .. 4.50f
)

data class VelocityExportConfig(
    val videoUri: Uri,
    val videoTrimStartMs: Long,
    val videoTrimEndMs: Long,
    val audioUri: Uri?,
    val audioTrimStartMs: Long,
    val audioDurationMs: Long,
    val keepOriginalVideoAudioIfNoMusic: Boolean,
    val aspectCrop: VelocityAspectCrop,
    val cropZoom: Float, // 1.0f .. 2.0f
    val keyframes: List<VelocityKeyframe>,
)
