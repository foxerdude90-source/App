package com.example.ui.features.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.media.MediaStoreSaver
import com.example.core.studio.WatermarkPosition
import com.example.ui.components.ChoicePill
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
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WatermarkWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    StudioCard {
        Text("Hardware Watermark Burner", style = MaterialTheme.typography.titleMedium)
        if (state.selectedVideo?.localUri == null) {
            EmptyToolHint("Pick a video above to burn a permanent custom brand text badge directly into every video frame.")
            return@StudioCard
        }

        val cfg = state.watermarkConfig
        OutlinedTextField(
            value = cfg.text,
            onValueChange = { txt -> viewModel.updateWatermark { it.copy(text = txt) } },
            label = { Text("Watermark Label") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ClipperIndigo,
                unfocusedBorderColor = StudioBorder,
                focusedContainerColor = StudioWhite,
                unfocusedContainerColor = StudioSnow,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Placement", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatermarkPosition.entries.forEach { pos ->
                ChoicePill(
                    text = pos.label,
                    selected = cfg.position == pos,
                    onClick = { viewModel.updateWatermark { it.copy(position = pos) } },
                    accentColor = ClipperIndigo,
                    accentSoft = ClipperIndigoSoft,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Opacity", style = MaterialTheme.typography.labelLarge)
            Text("${(cfg.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
        }
        Slider(
            value = cfg.opacity,
            onValueChange = { v -> viewModel.updateWatermark { it.copy(opacity = v) } },
            valueRange = 0.2f..1.0f,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Size Scale", style = MaterialTheme.typography.labelLarge)
            Text(String.format(Locale.US, "%.1f×", cfg.scaleFactor), style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
        }
        Slider(
            value = cfg.scaleFactor,
            onValueChange = { v -> viewModel.updateWatermark { it.copy(scaleFactor = v) } },
            valueRange = 0.6f..1.8f,
        )

        ChoicePill(
            text = if (cfg.pillBackdrop) "Translucent Badge Pill ON" else "Plain Text Shadow",
            selected = cfg.pillBackdrop,
            onClick = { viewModel.updateWatermark { it.copy(pillBackdrop = !it.pillBackdrop) } },
            accentColor = ClipperCyan,
            accentSoft = ClipperCyanSoft,
        )

        Button(
            onClick = viewModel::exportWatermarkedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_watermark_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
        ) {
            Icon(Icons.Outlined.BrandingWatermark, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Burn Watermark & Export")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioDspWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    StudioCard {
        Text("PCM Audio DSP Booster & Limiter", style = MaterialTheme.typography.titleMedium)
        if (state.selectedVideo?.localUri == null) {
            EmptyToolHint("Pick a video above to amplify quiet audio up to +18 dB with an analog tanh soft-clipping limiter and smooth fade envelopes.")
            return@StudioCard
        }

        val cfg = state.audioDspConfig
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Gain Level", style = MaterialTheme.typography.labelLarge)
            Text(
                text = String.format(Locale.US, "%+.1f dB", cfg.gainDb),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                color = ClipperRose,
            )
        }
        Slider(
            value = cfg.gainDb,
            onValueChange = { v -> viewModel.updateAudioDsp { it.copy(gainDb = v) } },
            valueRange = -24f..18f,
            enabled = !cfg.muteAudio,
            colors = SliderDefaults.colors(thumbColor = ClipperRose, activeTrackColor = ClipperRose),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fade-In Envelope", style = MaterialTheme.typography.labelLarge)
            Text("${cfg.fadeInMs} ms", style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
        }
        Slider(
            value = cfg.fadeInMs.toFloat(),
            onValueChange = { v -> viewModel.updateAudioDsp { it.copy(fadeInMs = v.toLong()) } },
            valueRange = 0f..4000f,
            enabled = !cfg.muteAudio,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fade-Out Envelope", style = MaterialTheme.typography.labelLarge)
            Text("${cfg.fadeOutMs} ms", style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
        }
        Slider(
            value = cfg.fadeOutMs.toFloat(),
            onValueChange = { v -> viewModel.updateAudioDsp { it.copy(fadeOutMs = v.toLong()) } },
            valueRange = 0f..4000f,
            enabled = !cfg.muteAudio,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoicePill(
                text = if (cfg.softLimiter) "tanh Soft Limiter ON" else "Hard Clip",
                selected = cfg.softLimiter,
                onClick = { viewModel.updateAudioDsp { it.copy(softLimiter = !it.softLimiter) } },
                accentColor = ClipperEmerald,
                accentSoft = ClipperEmeraldSoft,
            )
            ChoicePill(
                text = if (cfg.muteAudio) "Mute / Strip Audio ON" else "Keep & Process Audio",
                selected = cfg.muteAudio,
                onClick = { viewModel.updateAudioDsp { it.copy(muteAudio = !it.muteAudio) } },
                accentColor = ClipperRose,
                accentSoft = ClipperRoseSoft,
            )
        }

        Button(
            onClick = viewModel::exportAudioDspVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_audio_dsp_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperRose),
        ) {
            Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (cfg.muteAudio) "Strip Audio Track & Save" else "Apply PCM Audio DSP & Save")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FrameGrabberWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    val video = state.selectedVideo
    StudioCard {
        Text("Frame Grabber & Storyboard Contact Sheet", style = MaterialTheme.typography.titleMedium)
        if (video?.localUri == null) {
            EmptyToolHint("Pick a video above to scrub to any exact millisecond for a lossless PNG frame or generate a 3×3 / 4×4 timestamped storyboard sheet.")
            return@StudioCard
        }

        state.grabbedFrame?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Extracted video frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkObsidian),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Exact Timestamp", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "${MediaStoreSaver.formatDurationMs(state.grabPositionMs)} (${state.grabPositionMs} ms)",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                color = ClipperCyan,
            )
        }
        Slider(
            value = state.grabPositionMs.toFloat(),
            onValueChange = { viewModel.setGrabPositionMs(it.toLong()) },
            valueRange = 0f..video.durationMs.coerceAtLeast(1000L).toFloat(),
            colors = SliderDefaults.colors(thumbColor = ClipperCyan, activeTrackColor = ClipperCyan),
        )

        Button(
            onClick = viewModel::saveGrabbedFramePng,
            enabled = state.grabbedFrame != null && !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("studio_grab_frame_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperCyan),
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Lossless PNG Still Frame")
        }

        Text("Storyboard Contact Sheet Grid", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4).forEach { g ->
                ChoicePill(
                    text = "${g}×${g} (${g * g} frames)",
                    selected = state.contactSheetGrid == g,
                    onClick = { viewModel.setContactSheetGrid(g) },
                    accentColor = ClipperIndigo,
                    accentSoft = ClipperIndigoSoft,
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::generateAndSaveContactSheet,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("studio_contact_sheet_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.5f)),
        ) {
            Icon(Icons.Outlined.GridView, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate & Save ${state.contactSheetGrid}×${state.contactSheetGrid} Contact Sheet", color = InkObsidian)
        }

        state.contactSheetPreview?.let { sheet ->
            Image(
                bitmap = sheet.asImageBitmap(),
                contentDescription = "Storyboard contact sheet",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoLooperWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    val video = state.selectedVideo
    StudioCard {
        Text("Seamless Video Looper", style = MaterialTheme.typography.titleMedium)
        if (video?.localUri == null) {
            EmptyToolHint("Pick a short video above to stitch 2×–5× seamless continuous repeats into a single MP4 clip.")
            return@StudioCard
        }

        Text("Repeat Count", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4, 5).forEach { n ->
                ChoicePill(
                    text = "${n}× Loop",
                    selected = state.loopCount == n,
                    onClick = { viewModel.setLoopCount(n) },
                    accentColor = ClipperMagenta,
                    accentSoft = ClipperMagentaSoft,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Loop Playback Speed", style = MaterialTheme.typography.labelLarge)
            Text(String.format(Locale.US, "%.2f×", state.loopSpeed), style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
        }
        Slider(
            value = state.loopSpeed,
            onValueChange = viewModel::setLoopSpeed,
            valueRange = 0.5f..2.5f,
            colors = SliderDefaults.colors(thumbColor = ClipperMagenta, activeTrackColor = ClipperMagenta),
        )

        ChoicePill(
            text = if (state.loopMuteAudio) "Silent GIF-Style Loop" else "Keep Loop Audio",
            selected = state.loopMuteAudio,
            onClick = viewModel::toggleLoopMute,
            accentColor = ClipperAmber,
            accentSoft = ClipperAmberSoft,
        )

        val outMs = ((video.durationMs * state.loopCount) / state.loopSpeed.coerceAtLeast(0.5f)).toLong()
        TelemetryChip(
            label = "Final Looped Duration",
            value = MediaStoreSaver.formatDurationMs(outMs),
            accentColor = ClipperMagenta,
            containerColor = ClipperMagentaSoft,
        )

        Button(
            onClick = viewModel::exportLoopedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_looper_export_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperMagenta),
        ) {
            Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export ${state.loopCount}× Looped MP4")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ForensicsWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
) {
    StudioCard {
        Text("Forensic Stream Inspector & Privacy Stripper", style = MaterialTheme.typography.titleMedium)
        val report = state.forensicReport
        if (state.selectedVideo?.localUri == null || report == null) {
            EmptyToolHint("Pick a video above to inspect raw container tracks, color space, measured GOP keyframe interval, and strip GPS/camera metadata losslessly.")
            return@StudioCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TelemetryChip(label = "Video Codec", value = report.videoMime)
            TelemetryChip(label = "Resolution", value = report.resolution)
            TelemetryChip(label = "Frame Rate", value = report.frameRate)
            TelemetryChip(label = "Bitrate", value = report.bitrateLabel)
            TelemetryChip(label = "Color Space", value = report.colorInfo)
            TelemetryChip(
                label = "Measured GOP",
                value = if (report.keyframeIntervalMs > 0L) "${report.keyframeIntervalMs} ms" else "Single Keyframe",
                accentColor = ClipperIndigo,
                containerColor = ClipperIndigoSoft,
            )
            TelemetryChip(label = "Audio Codec", value = report.audioMime)
            if (report.audioSampleRateHz > 0) {
                TelemetryChip(label = "Audio PCM", value = "${report.audioSampleRateHz} Hz • ${report.audioChannels}ch")
            }
            TelemetryChip(
                label = "GPS Location Tag",
                value = if (report.hasLocationMetadata) "DETECTED" else "Clean",
                accentColor = if (report.hasLocationMetadata) ClipperRose else ClipperEmerald,
                containerColor = if (report.hasLocationMetadata) ClipperRoseSoft else ClipperEmeraldSoft,
            )
            TelemetryChip(label = "Tracks", value = "${report.rawTrackCount}")
        }

        Surface(
            color = StudioSnow,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.75.dp, StudioBorder),
        ) {
            Text(
                text = "Lossless Privacy Remux copies raw encoded video & audio bitstreams directly into a fresh MP4 container without re-encoding, stripping GPS location coordinates and camera/device atoms with zero quality loss.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate,
                modifier = Modifier.padding(12.dp),
            )
        }

        Button(
            onClick = viewModel::exportPrivacyStrippedVideo,
            enabled = !state.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("studio_privacy_strip_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald),
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Strip Metadata & Lossless Remux")
        }
    }
}
