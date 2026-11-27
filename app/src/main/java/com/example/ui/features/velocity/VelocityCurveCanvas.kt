package com.example.ui.features.velocity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.velocity.DetectedBeat
import com.example.core.velocity.VelocityCurveMath
import com.example.core.velocity.VelocityKeyframe
import com.example.core.velocity.VelocitySystemMode
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale
import kotlin.math.hypot

/**
 * Interactive CapCut-style Velocity Curve & Beat-Sync Graph Canvas.
 *
 * - Displays the real decoded music waveform softly behind the curve
 * - Displays detected & manual beat markers (tappable in Auto Velocity mode to toggle beats)
 * - Renders a smooth C1-continuous log-space Hermite spline with gradient area fill
 * - Supports 2D direct drag of control nodes & tap-to-add points in Custom Curve mode
 * - Renders a live synchronized preview playhead sweeping along the graph
 */
@Composable
fun VelocityCurveCanvas(
    mode: VelocitySystemMode,
    keyframes: List<VelocityKeyframe>,
    selectedKeyframeId: String?,
    beats: List<DetectedBeat>,
    waveformBars: List<Float>,
    playheadNormalized: Float,
    livePreviewSpeed: Float,
    onSelectKeyframe: (String) -> Unit,
    onMoveKeyframe: (String, Float, Float) -> Unit,
    onAddKeyframe: (Float, Float) -> Unit,
    onToggleManualBeat: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestKeyframes by rememberUpdatedState(keyframes)
    val latestMode by rememberUpdatedState(mode)
    var draggingId by remember { mutableStateOf<String?>(null) }

    val selectedKf = keyframes.firstOrNull { it.id == selectedKeyframeId }
    val activeReadoutSpeed = if (mode == VelocitySystemMode.CUSTOM_CURVE && selectedKf != null) {
        selectedKf.speed
    } else {
        livePreviewSpeed
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioSnow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StudioBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top micro-header inside the graph card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (mode == VelocitySystemMode.AUTO_BEAT) {
                        "Tap graph to add/remove beat hits"
                    } else {
                        "Drag nodes or tap curve to add points"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSlate,
                )
                Surface(
                    color = ClipperIndigoSoft,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f×", activeReadoutSpeed),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = ClipperIndigo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Graph area with left speed axis labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Y-axis logarithmic speed ticks
                Column(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    val ticks = listOf(4.0f to "4×", 2.0f to "2×", 1.0f to "1×", 0.5f to "0.5×", 0.2f to "0.2×")
                    ticks.forEach { (spd, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = if (spd == 1.0f) ClipperIndigo else InkMuted,
                        )
                    }
                }

                // Interactive Curve Drawing Surface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioWhite)
                        .testTag("velocity_curve_canvas")
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val padX = 16f
                                val padY = 16f
                                val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                                val plotH = (size.height - padY * 2f).coerceAtLeast(1f)
                                val tapT = ((offset.x - padX) / plotW).coerceIn(0f, 1f)
                                val normY = (1f - ((offset.y - padY) / plotH)).coerceIn(0f, 1f)
                                val tapSpeed = VelocityCurveMath.normalizedYToSpeed(normY)

                                if (latestMode == VelocitySystemMode.AUTO_BEAT) {
                                    onToggleManualBeat(tapT)
                                } else {
                                    // Select node if tapped close to an existing point, otherwise add new point
                                    val hit = latestKeyframes.minByOrNull { kf ->
                                        val kx = padX + kf.t * plotW
                                        val ky = padY + (1f - VelocityCurveMath.speedToNormalizedY(kf.speed)) * plotH
                                        hypot(offset.x - kx, offset.y - ky)
                                    }
                                    val hitDist = hit?.let { kf ->
                                        val kx = padX + kf.t * plotW
                                        val ky = padY + (1f - VelocityCurveMath.speedToNormalizedY(kf.speed)) * plotH
                                        hypot(offset.x - kx, offset.y - ky)
                                    } ?: Float.MAX_VALUE

                                    if (hit != null && hitDist <= 42f) {
                                        onSelectKeyframe(hit.id)
                                    } else {
                                        onAddKeyframe(tapT, tapSpeed)
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    if (latestMode != VelocitySystemMode.CUSTOM_CURVE) return@detectDragGestures
                                    val padX = 16f
                                    val padY = 16f
                                    val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                                    val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                                    val nearest = latestKeyframes.minByOrNull { kf ->
                                        val kx = padX + kf.t * plotW
                                        val ky = padY + (1f - VelocityCurveMath.speedToNormalizedY(kf.speed)) * plotH
                                        hypot(startOffset.x - kx, startOffset.y - ky)
                                    }
                                    if (nearest != null) {
                                        val kx = padX + nearest.t * plotW
                                        val ky = padY + (1f - VelocityCurveMath.speedToNormalizedY(nearest.speed)) * plotH
                                        if (hypot(startOffset.x - kx, startOffset.y - ky) <= 56f) {
                                            draggingId = nearest.id
                                            onSelectKeyframe(nearest.id)
                                        }
                                    }
                                },
                                onDragEnd = { draggingId = null },
                                onDragCancel = { draggingId = null },
                                onDrag = { change, _ ->
                                    val activeId = draggingId ?: return@detectDragGestures
                                    change.consume()
                                    val padX = 16f
                                    val padY = 16f
                                    val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                                    val plotH = (size.height - padY * 2f).coerceAtLeast(1f)
                                    val pos = change.position
                                    val newT = ((pos.x - padX) / plotW).coerceIn(0f, 1f)
                                    val normY = (1f - ((pos.y - padY) / plotH)).coerceIn(0f, 1f)
                                    val newSpeed = VelocityCurveMath.normalizedYToSpeed(normY)
                                    onMoveKeyframe(activeId, newT, newSpeed)
                                },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val padX = 16f
                        val padY = 16f
                        val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

                        fun tToX(t: Float): Float = padX + t.coerceIn(0f, 1f) * plotW
                        fun speedToY(spd: Float): Float =
                            padY + (1f - VelocityCurveMath.speedToNormalizedY(spd)) * plotH

                        // 1. Subtle horizontal log-speed grid lines
                        val gridSpeeds = listOf(4.0f, 2.0f, 1.0f, 0.5f, 0.2f)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        gridSpeeds.forEach { spd ->
                            val y = speedToY(spd)
                            val isUnity = spd == 1.0f
                            drawLine(
                                color = if (isUnity) ClipperIndigo.copy(alpha = 0.35f) else StudioBorder.copy(alpha = 0.65f),
                                start = Offset(padX, y),
                                end = Offset(size.width - padX, y),
                                strokeWidth = if (isUnity) 2f else 1f,
                                pathEffect = if (isUnity) dash else null,
                            )
                        }

                        // 2. Real music waveform silhouette rendered subtly along the bottom half
                        if (waveformBars.isNotEmpty()) {
                            val barCount = waveformBars.size
                            val barSlot = plotW / barCount.toFloat()
                            val barW = (barSlot * 0.58f).coerceAtLeast(1.5f)
                            val baseY = size.height - padY
                            val maxBarH = plotH * 0.42f
                            waveformBars.forEachIndexed { idx, amp ->
                                val h = (amp.coerceIn(0.05f, 1f) * maxBarH)
                                val bx = padX + idx * barSlot
                                drawRoundRect(
                                    color = ClipperCyan.copy(alpha = 0.16f),
                                    topLeft = Offset(bx, baseY - h),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(2f, 2f),
                                )
                            }
                        }

                        // 3. Vertical beat marker guide lines + bottom beat dots
                        beats.forEach { beat ->
                            val bx = tToX(beat.normalizedPos)
                            val markerColor = if (beat.isManual) ClipperMagenta else ClipperAmber
                            drawLine(
                                color = markerColor.copy(alpha = 0.26f + 0.24f * beat.strength),
                                start = Offset(bx, padY),
                                end = Offset(bx, size.height - padY),
                                strokeWidth = if (beat.isManual) 2.2f else 1.5f,
                            )
                            drawCircle(
                                color = markerColor,
                                radius = if (beat.isManual) 5.5f else 4f,
                                center = Offset(bx, size.height - padY),
                            )
                        }

                        // 4. Smooth C1-continuous spline path + soft gradient fill under curve
                        val sampled = VelocityCurveMath.sampleCurveForDisplay(keyframes, samples = 128)
                        val unityY = speedToY(1.0f)
                        val curvePath = Path()
                        val fillPath = Path()
                        sampled.forEachIndexed { idx, spd ->
                            val t = idx.toFloat() / (sampled.size - 1).toFloat()
                            val px = tToX(t)
                            val py = speedToY(spd)
                            if (idx == 0) {
                                curvePath.moveTo(px, py)
                                fillPath.moveTo(px, unityY)
                                fillPath.lineTo(px, py)
                            } else {
                                curvePath.lineTo(px, py)
                                fillPath.lineTo(px, py)
                            }
                        }
                        fillPath.lineTo(tToX(1f), unityY)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ClipperIndigo.copy(alpha = 0.24f),
                                    ClipperCyan.copy(alpha = 0.08f),
                                ),
                                startY = padY,
                                endY = size.height - padY,
                            ),
                        )

                        drawPath(
                            path = curvePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(ClipperIndigo, ClipperCyan, ClipperIndigo),
                            ),
                            style = Stroke(
                                width = 4.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        // 5. Live Preview Playhead Sweep Line
                        if (playheadNormalized > 0.001f) {
                            val headX = tToX(playheadNormalized)
                            drawLine(
                                color = InkObsidian.copy(alpha = 0.72f),
                                start = Offset(headX, padY - 4f),
                                end = Offset(headX, size.height - padY + 4f),
                                strokeWidth = 2.4f,
                            )
                            val headSpeed = VelocityCurveMath.evaluateSplineSpeed(keyframes, playheadNormalized)
                            drawCircle(
                                color = InkObsidian,
                                radius = 5f,
                                center = Offset(headX, speedToY(headSpeed)),
                            )
                        }

                        // 6. Draggable Control Nodes (Custom Curve mode) or subtle anchor dots (Auto mode)
                        if (mode == VelocitySystemMode.CUSTOM_CURVE) {
                            keyframes.forEach { kf ->
                                val cx = tToX(kf.t)
                                val cy = speedToY(kf.speed)
                                val isSelected = kf.id == selectedKeyframeId || kf.id == draggingId
                                if (isSelected) {
                                    drawCircle(
                                        color = ClipperIndigo.copy(alpha = 0.22f),
                                        radius = 18f,
                                        center = Offset(cx, cy),
                                    )
                                }
                                drawCircle(
                                    color = StudioWhite,
                                    radius = if (isSelected) 10.5f else 8.5f,
                                    center = Offset(cx, cy),
                                )
                                drawCircle(
                                    color = if (isSelected) ClipperIndigo else ClipperCyan,
                                    radius = if (isSelected) 10.5f else 8.5f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = if (isSelected) 4f else 3f),
                                )
                            }
                        } else {
                            // Subtle visual glow dots at curve extrema in Auto mode
                            keyframes.filterIndexed { i, _ -> i == 0 || i == keyframes.lastIndex || i % 3 == 0 }
                                .forEach { kf ->
                                    drawCircle(
                                        color = ClipperIndigo.copy(alpha = 0.55f),
                                        radius = 4f,
                                        center = Offset(tToX(kf.t), speedToY(kf.speed)),
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}
