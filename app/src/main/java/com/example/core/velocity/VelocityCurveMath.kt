package com.example.core.velocity

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Pure Kotlin mathematical core for smooth C1-continuous velocity curves,
 * log-speed Fritsch-Carlson Monotone Cubic Hermite Splines (PCHIP),
 * Auto Beat-Synced curve synthesis, and exact numerical time-remapping integration.
 */
object VelocityCurveMath {

    const val MIN_SPEED = 0.15f
    const val MAX_SPEED = 5.0f

    private val LN_MIN = ln(MIN_SPEED.toDouble())
    private val LN_MAX = ln(MAX_SPEED.toDouble())

    /**
     * Maps speed [0.15x .. 5.0x] logarithmically to visual graph height [0f (bottom) .. 1f (top)]
     * so slow-motion (0.15x..1.0x) and fast-motion (1.0x..5.0x) both have precise touch control.
     */
    fun speedToNormalizedY(speed: Float): Float {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED).toDouble()
        return ((ln(clamped) - LN_MIN) / (LN_MAX - LN_MIN)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Inverse of [speedToNormalizedY]: maps normalized Y [0f..1f] back to speed multiplier.
     */
    fun normalizedYToSpeed(normY: Float): Float {
        val y = normY.coerceIn(0f, 1f).toDouble()
        return exp(LN_MIN + y * (LN_MAX - LN_MIN)).toFloat().coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /**
     * Evaluates a smooth, zero-overshoot C1-continuous velocity curve at normalized time [t] (0..1)
     * using Fritsch-Carlson Monotone Cubic Hermite interpolation in log-speed space.
     */
    fun evaluateSplineSpeed(keyframes: List<VelocityKeyframe>, t: Float): Float {
        if (keyframes.isEmpty()) return 1.0f
        val sorted = sanitizeKeyframes(keyframes)
        if (sorted.size == 1) return sorted[0].speed

        val clampedT = t.coerceIn(0f, 1f)
        if (clampedT <= sorted.first().t) return sorted.first().speed
        if (clampedT >= sorted.last().t) return sorted.last().speed

        val n = sorted.size
        val xs = DoubleArray(n) { sorted[it].t.toDouble() }
        val ys = DoubleArray(n) { ln(sorted[it].speed.coerceIn(MIN_SPEED, MAX_SPEED).toDouble()) }

        // Secant slopes
        val h = DoubleArray(n - 1)
        val delta = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            h[i] = max(1e-6, xs[i + 1] - xs[i])
            delta[i] = (ys[i + 1] - ys[i]) / h[i]
        }

        // Fritsch-Carlson monotone tangents (prevents any overshoot or ringing!)
        val m = DoubleArray(n)
        m[0] = delta[0]
        m[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            if (delta[i - 1] * delta[i] <= 0.0) {
                m[i] = 0.0
            } else {
                val w1 = 2.0 * h[i] + h[i - 1]
                val w2 = h[i] + 2.0 * h[i - 1]
                m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])
            }
        }

        // Enforce monotonicity radius condition (alpha^2 + beta^2 <= 9)
        for (i in 0 until n - 1) {
            if (abs(delta[i]) < 1e-9) {
                m[i] = 0.0
                m[i + 1] = 0.0
            } else {
                val alpha = m[i] / delta[i]
                val beta = m[i + 1] / delta[i]
                val magSq = alpha * alpha + beta * beta
                if (magSq > 9.0) {
                    val tau = 3.0 / kotlin.math.sqrt(magSq)
                    m[i] = tau * alpha * delta[i]
                    m[i + 1] = tau * beta * delta[i]
                }
            }
        }

        // Locate segment containing clampedT
        var seg = 0
        for (i in 0 until n - 1) {
            if (clampedT >= xs[i] && clampedT <= xs[i + 1]) {
                seg = i
                break
            }
        }

        val span = h[seg]
        val u = ((clampedT - xs[seg]) / span).coerceIn(0.0, 1.0)
        val u2 = u * u
        val u3 = u2 * u

        val h00 = 2.0 * u3 - 3.0 * u2 + 1.0
        val h10 = u3 - 2.0 * u2 + u
        val h01 = -2.0 * u3 + 3.0 * u2
        val h11 = u3 - u2

        val lnSpeed = h00 * ys[seg] + h10 * span * m[seg] + h01 * ys[seg + 1] + h11 * span * m[seg + 1]
        return exp(lnSpeed).toFloat().coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /**
     * Sorts keyframes by t, merges near-duplicate timestamps, and ensures clean [0, 1] endpoints.
     */
    fun sanitizeKeyframes(keyframes: List<VelocityKeyframe>): List<VelocityKeyframe> {
        if (keyframes.isEmpty()) {
            return listOf(
                VelocityKeyframe(id = "start", t = 0f, speed = 1.0f),
                VelocityKeyframe(id = "end", t = 1f, speed = 1.0f),
            )
        }
        val sorted = keyframes
            .map { it.copy(t = it.t.coerceIn(0f, 1f), speed = it.speed.coerceIn(MIN_SPEED, MAX_SPEED)) }
            .sortedBy { it.t }

        val dedup = mutableListOf<VelocityKeyframe>()
        for (kf in sorted) {
            val prev = dedup.lastOrNull()
            if (prev == null || (kf.t - prev.t) >= 0.004f) {
                dedup.add(kf)
            }
        }
        if (dedup.first().t > 0.001f) {
            dedup.add(0, VelocityKeyframe(id = "auto_start", t = 0f, speed = dedup.first().speed))
        }
        if (dedup.last().t < 0.999f) {
            dedup.add(VelocityKeyframe(id = "auto_end", t = 1f, speed = dedup.last().speed))
        }
        return dedup
    }

    /**
     * Resolves effective Auto Velocity tuning parameters when Auto Mode is enabled,
     * adapting pacing gentleness and slow-mo depth to the track's detected BPM.
     */
    fun resolveEffectiveAutoSettings(
        settings: AutoVelocitySettings,
        bpm: Float,
    ): AutoVelocitySettings {
        if (!settings.isAutoTuning) return settings
        val safeBpm = bpm.takeIf { it in 55f..210f } ?: 120f
        return when {
            safeBpm >= 140f -> settings.copy(
                frequency = AutoVelocityFrequency.EVERY_2_BEATS,
                smoothness = 0.80f,
                maxSlowMoSpeed = 0.34f,
                maxFastSpeed = 2.65f,
            )
            safeBpm >= 108f -> settings.copy(
                frequency = AutoVelocityFrequency.EVERY_2_BEATS,
                smoothness = 0.78f,
                maxSlowMoSpeed = 0.30f,
                maxFastSpeed = 2.45f,
            )
            else -> settings.copy(
                frequency = AutoVelocityFrequency.EVERY_BEAT,
                smoothness = 0.84f,
                maxSlowMoSpeed = 0.28f,
                maxFastSpeed = 2.20f,
            )
        }
    }

    /**
     * Synthesizes a silky-smooth, gently paced beat-synced velocity curve from detected beats.
     *
     * On each selected beat:
     *  - Gently accelerates into a fast lead-in rush right before the impact
     *  - Smoothly glides into a cushioned slow-motion valley just after the beat hit
     *  - Returns smoothly via C1 Hermite splines so transitions feel cinematic and paced.
     */
    fun generateAutoVelocityCurve(
        beats: List<DetectedBeat>,
        rawSettings: AutoVelocitySettings,
        bpm: Float = 120f,
    ): List<VelocityKeyframe> {
        val settings = resolveEffectiveAutoSettings(rawSettings, bpm)
        val filteredBeats = selectBeatsForFrequency(beats, settings.frequency)

        // If no beats were provided (e.g. before music attached), produce a gentle 4-pulse demo wave
        val beatPositions = if (filteredBeats.isNotEmpty()) {
            filteredBeats.map { it.normalizedPos.coerceIn(0.06f, 0.94f) to it.strength.coerceIn(0.35f, 1.0f) }
        } else {
            listOf(0.20f to 0.9f, 0.45f to 0.85f, 0.70f to 1.0f, 0.88f to 0.8f)
        }

        val smoothness = settings.smoothness.coerceIn(0.15f, 1.0f)
        val slowTarget = settings.maxSlowMoSpeed.coerceIn(MIN_SPEED, 0.85f)
        val fastTarget = settings.maxFastSpeed.coerceIn(1.25f, MAX_SPEED)

        // Blend smooth Gaussian rush/slow-mo kernels in log-speed space, then sample clean knots
        val lnSlow = ln(slowTarget.toDouble())
        val lnFast = ln(fastTarget.toDouble())

        // Higher smoothness = wider, gentler ramp radius around each beat
        val avgGap = if (beatPositions.size >= 2) {
            (beatPositions.last().first - beatPositions.first().first) / (beatPositions.size - 1)
        } else {
            0.25f
        }
        val baseRadius = (avgGap * (0.34f + smoothness * 0.24f)).coerceIn(0.032f, 0.16f)
        val rushLead = baseRadius * (0.58f + smoothness * 0.22f)
        val slowLag = baseRadius * (0.48f + smoothness * 0.26f)
        val rushSigma = baseRadius * (0.46f + smoothness * 0.26f)
        val slowSigma = baseRadius * (0.55f + smoothness * 0.35f)

        val knotCount = (beatPositions.size * 4 + 5).coerceIn(11, 36)
        val knots = mutableListOf<VelocityKeyframe>()

        for (i in 0 until knotCount) {
            val t = i.toFloat() / (knotCount - 1).toFloat()
            var lnSum = 0.0
            var weightSum = 0.35 // gentle pull toward 1.0x between distant beats

            for ((bPos, strength) in beatPositions) {
                val rushCenter = (bPos - rushLead).toDouble()
                val slowCenter = (bPos + slowLag).toDouble()

                val dRush = (t - rushCenter) / rushSigma
                val wRush = exp(-0.5 * dRush * dRush) * (0.65 + 0.35 * strength)

                val dSlow = (t - slowCenter) / slowSigma
                val wSlow = exp(-0.5 * dSlow * dSlow) * (0.72 + 0.28 * strength)

                lnSum += wRush * lnFast + wSlow * lnSlow
                weightSum += wRush + wSlow
            }

            // Extra gentle edge fade near t=0 and t=1 so clip starts/ends gracefully
            val edgeEase = min(1.0, min(t / 0.05, (1.0 - t) / 0.05)).coerceIn(0.25, 1.0)
            val blendedLn = (lnSum / weightSum) * (0.55 + 0.45 * edgeEase)
            val speed = exp(blendedLn).toFloat().coerceIn(slowTarget, fastTarget)

            knots.add(
                VelocityKeyframe(
                    id = "auto_$i",
                    t = t,
                    speed = speed,
                ),
            )
        }

        return sanitizeKeyframes(knots)
    }

    private fun selectBeatsForFrequency(
        beats: List<DetectedBeat>,
        frequency: AutoVelocityFrequency,
    ): List<DetectedBeat> {
        if (beats.isEmpty()) return emptyList()
        val sorted = beats.sortedBy { it.normalizedPos }

        val candidates = if (frequency == AutoVelocityFrequency.STRONG_DROPS) {
            val strong = sorted.filter { it.isManual || it.strength >= 0.58f }
            strong.ifEmpty { sorted.filterIndexed { idx, _ -> idx % 2 == 0 } }
        } else {
            sorted.filterIndexed { idx, b -> b.isManual || idx % frequency.beatStride == 0 }
        }

        // Enforce minimum temporal spacing so curves always stay smooth and never jitter
        val spaced = mutableListOf<DetectedBeat>()
        for (beat in candidates) {
            val last = spaced.lastOrNull()
            if (last == null || beat.isManual || (beat.normalizedPos - last.normalizedPos) >= frequency.minSpacingNormalized) {
                spaced.add(beat)
            }
        }
        return spaced.take(10)
    }

    /**
     * Built-in CapCut-inspired smooth velocity curve presets.
     */
    fun presetKeyframes(preset: VelocityCurvePreset): List<VelocityKeyframe> = when (preset) {
        VelocityCurvePreset.MONTAGE -> listOf(
            VelocityKeyframe("p0", 0.00f, 1.20f),
            VelocityKeyframe("p1", 0.16f, 2.80f),
            VelocityKeyframe("p2", 0.34f, 0.35f),
            VelocityKeyframe("p3", 0.54f, 2.20f),
            VelocityKeyframe("p4", 0.74f, 0.40f),
            VelocityKeyframe("p5", 0.90f, 2.60f),
            VelocityKeyframe("p6", 1.00f, 1.00f),
        )
        VelocityCurvePreset.HERO -> listOf(
            VelocityKeyframe("p0", 0.00f, 1.00f),
            VelocityKeyframe("p1", 0.22f, 3.20f),
            VelocityKeyframe("p2", 0.42f, 0.25f),
            VelocityKeyframe("p3", 0.62f, 0.30f),
            VelocityKeyframe("p4", 0.82f, 3.00f),
            VelocityKeyframe("p5", 1.00f, 1.00f),
        )
        VelocityCurvePreset.BULLET -> listOf(
            VelocityKeyframe("p0", 0.00f, 0.90f),
            VelocityKeyframe("p1", 0.15f, 3.50f),
            VelocityKeyframe("p2", 0.30f, 0.38f),
            VelocityKeyframe("p3", 0.50f, 3.40f),
            VelocityKeyframe("p4", 0.68f, 0.35f),
            VelocityKeyframe("p5", 0.86f, 3.20f),
            VelocityKeyframe("p6", 1.00f, 0.90f),
        )
        VelocityCurvePreset.JUMP_CUT -> listOf(
            VelocityKeyframe("p0", 0.00f, 1.00f),
            VelocityKeyframe("p1", 0.20f, 2.60f),
            VelocityKeyframe("p2", 0.38f, 0.48f),
            VelocityKeyframe("p3", 0.58f, 2.80f),
            VelocityKeyframe("p4", 0.76f, 0.45f),
            VelocityKeyframe("p5", 1.00f, 1.60f),
        )
        VelocityCurvePreset.FLASH_IN -> listOf(
            VelocityKeyframe("p0", 0.00f, 3.60f),
            VelocityKeyframe("p1", 0.22f, 1.60f),
            VelocityKeyframe("p2", 0.52f, 0.42f),
            VelocityKeyframe("p3", 0.82f, 0.72f),
            VelocityKeyframe("p4", 1.00f, 1.00f),
        )
        VelocityCurvePreset.FLASH_OUT -> listOf(
            VelocityKeyframe("p0", 0.00f, 1.00f),
            VelocityKeyframe("p1", 0.24f, 0.55f),
            VelocityKeyframe("p2", 0.56f, 0.40f),
            VelocityKeyframe("p3", 0.82f, 2.40f),
            VelocityKeyframe("p4", 1.00f, 3.60f),
        )
        VelocityCurvePreset.FLAT -> listOf(
            VelocityKeyframe("p0", 0.00f, 1.00f),
            VelocityKeyframe("p1", 0.50f, 1.00f),
            VelocityKeyframe("p2", 1.00f, 1.00f),
        )
    }

    /**
     * Samples the curve uniformly for smooth Canvas path rendering.
     */
    fun sampleCurveForDisplay(keyframes: List<VelocityKeyframe>, samples: Int = 120): FloatArray {
        val count = samples.coerceAtLeast(16)
        return FloatArray(count) { i ->
            val t = i.toFloat() / (count - 1).toFloat()
            evaluateSplineSpeed(keyframes, t)
        }
    }

    /**
     * Computes exact numerical integration of dt_out = dt_src / v(t_src / S)
     * to predict output video duration and map timestamps accurately in both directions.
     */
    fun buildTimeRemapLut(
        keyframes: List<VelocityKeyframe>,
        trimmedSourceDurationMs: Long,
        steps: Int = 512,
    ): VelocityTimeRemapLut {
        val safeSourceMs = trimmedSourceDurationMs.coerceAtLeast(250L)
        val n = steps.coerceAtLeast(32)
        val cumulativeOutputSec = DoubleArray(n + 1)
        val srcDurSec = safeSourceMs / 1000.0
        val du = 1.0 / n.toDouble()

        var prevInvSpeed = 1.0 / evaluateSplineSpeed(keyframes, 0f).toDouble()
        for (i in 1..n) {
            val u = (i.toDouble() / n.toDouble()).toFloat()
            val curInvSpeed = 1.0 / evaluateSplineSpeed(keyframes, u).toDouble()
            // Trapezoidal integration of 1 / v(u)
            val area = 0.5 * (prevInvSpeed + curInvSpeed) * du * srcDurSec
            cumulativeOutputSec[i] = cumulativeOutputSec[i - 1] + area
            prevInvSpeed = curInvSpeed
        }

        val totalOutMs = (cumulativeOutputSec[n] * 1000.0).roundToLong().coerceAtLeast(250L)
        return VelocityTimeRemapLut(
            sourceDurationMs = safeSourceMs,
            totalOutputDurationMs = totalOutMs,
            cumulativeOutputSec = cumulativeOutputSec,
            keyframes = sanitizeKeyframes(keyframes),
        )
    }
}

class VelocityTimeRemapLut(
    val sourceDurationMs: Long,
    val totalOutputDurationMs: Long,
    private val cumulativeOutputSec: DoubleArray,
    private val keyframes: List<VelocityKeyframe>,
) {
    private val steps = cumulativeOutputSec.size - 1

    fun sourceUsToOutputUs(sourceUs: Long): Long {
        val u = (sourceUs.toDouble() / (sourceDurationMs.coerceAtLeast(1L) * 1000.0)).coerceIn(0.0, 1.0)
        val pos = u * steps
        val idx = pos.toInt().coerceIn(0, steps - 1)
        val frac = pos - idx
        val outSec = cumulativeOutputSec[idx] + (cumulativeOutputSec[idx + 1] - cumulativeOutputSec[idx]) * frac
        return (outSec * 1_000_000.0).roundToLong().coerceAtLeast(0L)
    }

    fun speedAtNormalizedSource(u: Float): Float {
        return VelocityCurveMath.evaluateSplineSpeed(keyframes, u.coerceIn(0f, 1f))
    }

    fun averageSpeedFactor(): Float {
        if (totalOutputDurationMs <= 0L) return 1f
        return (sourceDurationMs.toFloat() / totalOutputDurationMs.toFloat()).coerceIn(0.1f, 8f)
    }
}
