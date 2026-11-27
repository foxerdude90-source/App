package com.example.ui.features.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.media.MediaStoreSaver
import com.example.core.studio.AspectRatioPreset
import com.example.core.studio.ColorLutPreset
import com.example.ui.components.ChoicePill
import com.example.ui.components.ExoVideoPreview
import com.example.ui.components.StudioCard
import com.example.ui.components.TelemetryChip
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperAmberSoft
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanSoft
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.ClipperMagentaSoft
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import java.util.Locale

@Composable
internal fun TrimmerWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    val video = state.selectedVideo
    StudioCard {
        Text("Timeline & Frame Cutter", style = MaterialTheme.typography.titleMedium)
        if (video?.localUri == null) {
            EmptyToolHint("Pick a video above to extract its real filmstrip timeline and trim with millisecond precision.")
            return@StudioCard
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(InkObsidian),
        ) {
            ExoVideoPreview(mediaUri = video.localUri, modifier = Modifier.fillMaxWidth())
        }

        if (state.filmstripFrames.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.75.dp, StudioBorder, RoundedCornerShape(10.dp)),
            ) {
                state.filmstripFrames.forEach { thumb ->
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                    )
                }
            }
        }

        val dur = video.durationMs.coerceAtLeast(1000L).toFloat()
        val startVal = state.trimStartMs.toFloat().coerceIn(0f, dur)
        val endVal = state.trimEndMs.toFloat().coerceIn(startVal, dur)
        RangeSlider(
            value = startVal..endVal,
            onValueChange = { range -> viewModel.setTrimRange(range.start.toLong(), range.endInclusive.toLong()) },
            valueRange = 0f..dur,
            colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TelemetryChip(label = "Start", value = MediaStoreSaver.formatDurationMs(state.trimStartMs))
            TelemetryChip(
                label = "Clip Length",
                value = MediaStoreSaver.formatDurationMs((state.trimEndMs - state.trimStartMs).coerceAtLeast(0L)),
                accentColor = ClipperIndigo,
                containerColor = ClipperIndigoSoft,
            )
            TelemetryChip(label = "End", value = MediaStoreSaver.formatDurationMs(state.trimEndMs))
        }

        Button(
            onClick = viewModel::exportTrimmedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_trim_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
        ) {
            Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Trimmed MP4")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeedRampWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    val video = state.selectedVideo
    StudioCard {
        Text("Variable Speed & Sonic Audio", style = MaterialTheme.typography.titleMedium)
        if (video?.localUri == null) {
            EmptyToolHint("Pick a video above to render smooth 0.25× slow-motion or up to 4× time-lapse with pitch protection.")
            return@StudioCard
        }

        val presets = listOf(0.25f, 0.5f, 0.75f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { spd ->
                ChoicePill(
                    text = "${spd}×",
                    selected = kotlin.math.abs(state.speedFactor - spd) < 0.02f,
                    onClick = { viewModel.setSpeedFactor(spd) },
                    accentColor = ClipperCyan,
                    accentSoft = ClipperCyanSoft,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Fine Speed Multiplier", style = MaterialTheme.typography.labelLarge)
            Text(
                text = String.format(Locale.US, "%.2f×", state.speedFactor),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                color = ClipperCyan,
            )
        }
        Slider(
            value = state.speedFactor,
            onValueChange = viewModel::setSpeedFactor,
            valueRange = 0.25f..4.0f,
            colors = SliderDefaults.colors(thumbColor = ClipperCyan, activeTrackColor = ClipperCyan),
        )

        val estDuration = (video.durationMs / state.speedFactor.coerceAtLeast(0.1f)).toLong()
        TelemetryChip(
            label = "Estimated Output Duration",
            value = MediaStoreSaver.formatDurationMs(estDuration),
            accentColor = ClipperCyan,
            containerColor = ClipperCyanSoft,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                text = if (state.preservePitch) "Pitch-Lock ON" else "Pitch-Shift (Varispeed)",
                selected = state.preservePitch,
                onClick = viewModel::togglePreservePitch,
                accentColor = ClipperIndigo,
                accentSoft = ClipperIndigoSoft,
            )
            ChoicePill(
                text = if (state.speedMuteAudio) "Audio Muted" else "Keep Audio",
                selected = state.speedMuteAudio,
                onClick = viewModel::toggleSpeedMuteAudio,
                accentColor = ClipperAmber,
                accentSoft = ClipperAmberSoft,
            )
        }

        Button(
            onClick = viewModel::exportSpeedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_speed_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperCyan),
        ) {
            Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Render ${String.format(Locale.US, "%.2f×", state.speedFactor)} Video")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorGradeWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    StudioCard {
        Text("GPU LUT & Film Color Grader", style = MaterialTheme.typography.titleMedium)
        if (state.selectedVideo?.localUri == null) {
            EmptyToolHint("Pick a video above to preview real-time LUT grading on your frame and export via GPU shader.")
            return@StudioCard
        }

        state.gradedPreviewFrame?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Color graded preview frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkObsidian),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorLutPreset.entries.forEach { lut ->
                ChoicePill(
                    text = lut.label,
                    selected = state.activeLutPreset == lut,
                    onClick = { viewModel.selectLutPreset(lut) },
                    accentColor = ClipperMagenta,
                    accentSoft = ClipperMagentaSoft,
                )
            }
        }

        val p = state.colorParams
        GradeSlider("Exposure (EV)", p.exposure, -0.8f..0.8f, String.format(Locale.US, "%+.2f", p.exposure)) { v ->
            viewModel.updateColorParams { it.copy(exposure = v) }
        }
        GradeSlider("Contrast", p.contrast, 0.6f..1.6f, String.format(Locale.US, "%.2f", p.contrast)) { v ->
            viewModel.updateColorParams { it.copy(contrast = v) }
        }
        GradeSlider("Saturation", p.saturation, 0f..1.8f, String.format(Locale.US, "%.0f%%", p.saturation * 100f)) { v ->
            viewModel.updateColorParams { it.copy(saturation = v) }
        }
        GradeSlider("Warmth / Temp", p.temperature, -0.5f..0.5f, String.format(Locale.US, "%+.2f", p.temperature)) { v ->
            viewModel.updateColorParams { it.copy(temperature = v) }
        }
        GradeSlider("Optical Vignette", p.vignette, 0f..0.8f, String.format(Locale.US, "%.0f%%", p.vignette * 100f)) { v ->
            viewModel.updateColorParams { it.copy(vignette = v) }
        }

        Button(
            onClick = viewModel::exportColorGradedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_color_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperMagenta),
        ) {
            Icon(Icons.Outlined.ColorLens, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export GPU Graded Video")
        }
    }
}

@Composable
internal fun MergerWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onAddClips: () -> Unit,
) {
    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Multi-Clip Sequence (${state.mergeClips.size})", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onAddClips,
                enabled = !state.isProcessing,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, StudioBorder),
                modifier = Modifier.testTag("studio_merger_add_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Clips")
            }
        }

        if (state.mergeClips.isEmpty()) {
            EmptyToolHint("Tap Add Clips to select 2 or more videos from your device, reorder them, and stitch seamlessly into one MP4.")
        } else {
            val totalMs = state.mergeClips.sumOf { it.durationMs }
            TelemetryChip(
                label = "Total Combined Timeline",
                value = MediaStoreSaver.formatDurationMs(totalMs),
                accentColor = ClipperEmerald,
                containerColor = ClipperEmeraldSoft,
            )

            state.mergeClips.forEachIndexed { idx, clip ->
                Surface(
                    color = StudioSnow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, StudioBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${idx + 1}. ${clip.fileName}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                text = "${clip.width}×${clip.height} • ${MediaStoreSaver.formatDurationMs(clip.durationMs)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = InkSlate,
                            )
                        }
                        Row {
                            IconButton(onClick = { viewModel.moveMergeClip(idx, -1) }, enabled = idx > 0) {
                                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move clip up")
                            }
                            IconButton(onClick = { viewModel.moveMergeClip(idx, 1) }, enabled = idx < state.mergeClips.lastIndex) {
                                Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move clip down")
                            }
                            IconButton(onClick = { viewModel.removeMergeClip(idx) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove clip", tint = ClipperRose)
                            }
                        }
                    }
                }
            }

            ChoicePill(
                text = if (state.mergeNormalize1080p) "Normalize All Clips to 1080p" else "Keep Native Clip Resolutions",
                selected = state.mergeNormalize1080p,
                onClick = viewModel::toggleMergeNormalize1080p,
                accentColor = ClipperEmerald,
                accentSoft = ClipperEmeraldSoft,
            )

            Button(
                onClick = viewModel::exportMergedVideo,
                enabled = state.mergeClips.size >= 2 && !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("studio_merger_export_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald),
            ) {
                Icon(Icons.Outlined.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Merge ${state.mergeClips.size} Clips Into MP4")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReframeWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    StudioCard {
        Text("Social Aspect Ratio & Transform", style = MaterialTheme.typography.titleMedium)
        if (state.selectedVideo?.localUri == null) {
            EmptyToolHint("Pick a video above to reframe for 9:16 Reels, 1:1 Square, 4:5 Feed, or 16:9 Widescreen with rotation & mirror flip.")
            return@StudioCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AspectRatioPreset.entries.forEach { preset ->
                ChoicePill(
                    text = preset.label,
                    selected = state.aspectPreset == preset,
                    onClick = { viewModel.selectAspectPreset(preset) },
                    icon = Icons.Outlined.AspectRatio,
                    accentColor = ClipperAmber,
                    accentSoft = ClipperAmberSoft,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                text = "Crop-Fill",
                selected = state.cropToFill,
                onClick = { viewModel.setCropToFill(true) },
                modifier = Modifier.weight(1f),
                accentColor = ClipperIndigo,
                accentSoft = ClipperIndigoSoft,
            )
            ChoicePill(
                text = "Fit Letterbox",
                selected = !state.cropToFill,
                onClick = { viewModel.setCropToFill(false) },
                modifier = Modifier.weight(1f),
                accentColor = ClipperIndigo,
                accentSoft = ClipperIndigoSoft,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                text = "Rotate ${state.rotationDegrees.toInt()}°",
                selected = state.rotationDegrees != 0f,
                onClick = viewModel::rotateBy90,
                icon = Icons.Outlined.RotateRight,
                accentColor = ClipperCyan,
                accentSoft = ClipperCyanSoft,
            )
            ChoicePill(
                text = "Flip H",
                selected = state.flipHorizontal,
                onClick = viewModel::toggleFlipHorizontal,
                icon = Icons.Outlined.Flip,
                accentColor = ClipperMagenta,
                accentSoft = ClipperMagentaSoft,
            )
            ChoicePill(
                text = "Flip V",
                selected = state.flipVertical,
                onClick = viewModel::toggleFlipVertical,
                icon = Icons.Outlined.Flip,
                accentColor = ClipperMagenta,
                accentSoft = ClipperMagentaSoft,
            )
        }

        Button(
            onClick = viewModel::exportReframedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_reframe_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperAmber),
        ) {
            Icon(Icons.Outlined.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export ${state.aspectPreset.label} (${state.aspectPreset.width}×${state.aspectPreset.height})")
        }
    }
}

@Composable
internal fun EmptyToolHint(message: String) {
    Surface(
        color = StudioSnow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.75.dp, StudioBorder),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSlate,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = InkObsidian)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                color = ClipperMagenta,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = ClipperMagenta, activeTrackColor = ClipperMagenta),
        )
    }
}
