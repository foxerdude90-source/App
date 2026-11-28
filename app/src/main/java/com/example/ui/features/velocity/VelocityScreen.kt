package com.example.ui.features.velocity

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.core.media.MediaStoreSaver
import com.example.core.velocity.AutoVelocityFrequency
import com.example.core.velocity.VelocityAspectCrop
import com.example.core.velocity.VelocityCurveMath
import com.example.core.velocity.VelocityCurvePreset
import com.example.core.velocity.VelocityKeyframe
import com.example.core.velocity.VelocitySystemMode
import com.example.ui.components.ChoicePill
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
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
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VelocityScreen(
    viewModel: VelocityViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.loadVideo(uri)
    }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.attachMusic(uri)
    }

    val activeKeyframes = state.activeKeyframes

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioSnow),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("velocity_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Source Video & Attached Music Card + Live Velocity Preview
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            },
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClipperIndigo,
                                contentColor = StudioWhite,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("velocity_pick_video_button"),
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.video == null) "Add Video" else "Change Video")
                        }

                        OutlinedButton(
                            onClick = { musicPicker.launch(arrayOf("audio/*")) },
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (state.musicUri != null) ClipperCyan else StudioBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("velocity_pick_music_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = if (state.musicUri != null) ClipperCyan else ClipperIndigo,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.musicUri == null) "Add Music" else "Swap Music")
                        }
                    }

                    // Attached Music Track Pill
                    state.musicFileName?.let { musicName ->
                        Surface(
                            color = ClipperCyanSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ClipperCyan.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = musicName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = InkObsidian,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "Music • ${MediaStoreSaver.formatDurationMs(state.musicDurationMs)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = ClipperCyan,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = viewModel::removeMusic,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove music", tint = InkSlate, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Live Velocity Video + Synced Music Preview Player
                    val vidUri = state.video?.localUri
                    if (vidUri != null) {
                        VelocityLivePreviewPlayer(
                            videoUri = vidUri,
                            videoTrimStartMs = state.videoTrimStartMs,
                            videoTrimEndMs = state.videoTrimEndMs.takeIf { it > state.videoTrimStartMs }
                                ?: (state.video?.durationMs ?: 10_000L),
                            musicUri = state.musicUri,
                            musicTrimStartMs = state.musicTrimStartMs,
                            keepOriginalAudio = state.keepOriginalVideoAudioIfNoMusic,
                            aspectCrop = state.aspectCrop,
                            keyframes = activeKeyframes,
                            onPlayheadChanged = viewModel::updatePreviewPlayhead,
                        )
                    } else {
                        Surface(
                            color = StudioSnow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ClipperIndigoSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add a video & music track",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkObsidian,
                                    )
                                    Text(
                                        text = "Preview real-time beat-synced smooth slow-mo & custom CapCut velocity curves right on device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSlate,
                                    )
                                }
                            }
                        }
                    }

                    if (state.isAnalyzingAudio) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ClipperIndigo)
                            Text(
                                text = "Scanning audio FFT spectrum for beats & BPM...",
                                style = MaterialTheme.typography.labelMedium,
                                color = ClipperIndigo,
                            )
                        }
                    }
                }
            }

            // 2. Trim & Crop Controls (shown cleanly when video or music is loaded)
            if (state.video != null || state.musicUri != null) {
                item {
                    StudioCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.Crop, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
                                Text("Trim, Music Sync & Crop", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                            }
                            if (state.musicUri != null || state.video != null) {
                                OutlinedButton(
                                    onClick = viewModel::reanalyzeCurrentAudio,
                                    enabled = !state.isAnalyzingAudio,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.75.dp, StudioBorder),
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rescan Beats", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Video Trim Range Slider
                        state.video?.let { vid ->
                            val maxVid = vid.durationMs.coerceAtLeast(500L).toFloat()
                            val vStart = state.videoTrimStartMs.toFloat().coerceIn(0f, maxVid)
                            val vEnd = (state.videoTrimEndMs.takeIf { it > state.videoTrimStartMs }?.toFloat() ?: maxVid)
                                .coerceIn(vStart, maxVid)

                            if (maxVid > 500f) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Video Trim", style = MaterialTheme.typography.labelLarge, color = InkSlate)
                                        Text(
                                            text = "${MediaStoreSaver.formatDurationMs(vStart.roundToLong())} – ${MediaStoreSaver.formatDurationMs(vEnd.roundToLong())}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                            color = ClipperIndigo,
                                        )
                                    }
                                    RangeSlider(
                                        value = vStart..vEnd,
                                        onValueChange = { range ->
                                            viewModel.updateVideoTrim(range.start.roundToLong(), range.endInclusive.roundToLong())
                                        },
                                        valueRange = 0f..maxVid,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ClipperIndigo,
                                            activeTrackColor = ClipperIndigo,
                                        ),
                                    )
                                }
                            }
                        }

                        // Music Trim / Drop Offset Slider (or Keep Original Video Audio switch)
                        if (state.musicUri != null && state.musicDurationMs > 600L) {
                            val maxAudio = state.musicDurationMs.toFloat()
                            val aStart = state.musicTrimStartMs.toFloat().coerceIn(0f, maxAudio)
                            val aEnd = (state.musicTrimEndMs.takeIf { it > state.musicTrimStartMs }?.toFloat() ?: maxAudio)
                                .coerceIn(aStart, maxAudio)

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Music Trim / Drop Offset", style = MaterialTheme.typography.labelLarge, color = InkSlate)
                                    Text(
                                        text = "${MediaStoreSaver.formatDurationMs(aStart.roundToLong())} – ${MediaStoreSaver.formatDurationMs(aEnd.roundToLong())}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                        color = ClipperCyan,
                                    )
                                }
                                RangeSlider(
                                    value = aStart..aEnd,
                                    onValueChange = { range ->
                                        viewModel.updateMusicTrim(range.start.roundToLong(), range.endInclusive.roundToLong())
                                    },
                                    onValueChangeFinished = { viewModel.reanalyzeCurrentAudio() },
                                    valueRange = 0f..maxAudio,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ClipperCyan,
                                        activeTrackColor = ClipperCyan,
                                    ),
                                )
                            }
                        } else if (state.video != null && state.musicUri == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Keep Original Video Audio", style = MaterialTheme.typography.labelLarge, color = InkObsidian)
                                    Text("Turn off for silent export when no external music is attached", style = MaterialTheme.typography.bodySmall, color = InkSlate)
                                }
                                Switch(
                                    checked = state.keepOriginalVideoAudioIfNoMusic,
                                    onCheckedChange = viewModel::setKeepOriginalAudio,
                                    colors = SwitchDefaults.colors(checkedTrackColor = ClipperIndigo),
                                    modifier = Modifier.testTag("velocity_keep_original_audio_switch"),
                                )
                            }
                        }

                        HorizontalDivider(color = StudioBorder)

                        // Aspect Crop & Zoom
                        Text("Aspect Crop", style = MaterialTheme.typography.labelLarge, color = InkSlate)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VelocityAspectCrop.entries.forEach { crop ->
                                ChoicePill(
                                    text = crop.label,
                                    selected = state.aspectCrop == crop,
                                    onClick = { viewModel.selectAspectCrop(crop) },
                                    tag = "velocity_crop_${crop.name.lowercase(Locale.US)}",
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Crop Zoom", style = MaterialTheme.typography.labelLarge, color = InkSlate)
                            Text(
                                text = String.format(Locale.US, "%.2f×", state.cropZoom),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = ClipperIndigo,
                            )
                        }
                        Slider(
                            value = state.cropZoom,
                            onValueChange = viewModel::setCropZoom,
                            valueRange = 1.0f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
                        )
                    }
                }
            }

            // 3. Two-System Mode Selector (Auto Velocity | Custom Curve) + Live Interactive Graph
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoicePill(
                            text = VelocitySystemMode.AUTO_BEAT.label,
                            selected = state.mode == VelocitySystemMode.AUTO_BEAT,
                            onClick = { viewModel.selectMode(VelocitySystemMode.AUTO_BEAT) },
                            icon = Icons.Outlined.AutoAwesome,
                            tag = "velocity_mode_auto",
                        )
                        ChoicePill(
                            text = VelocitySystemMode.CUSTOM_CURVE.label,
                            selected = state.mode == VelocitySystemMode.CUSTOM_CURVE,
                            onClick = { viewModel.selectMode(VelocitySystemMode.CUSTOM_CURVE) },
                            icon = Icons.Outlined.ShowChart,
                            tag = "velocity_mode_curve",
                        )
                    }

                    // Live Velocity Graph Canvas
                    VelocityCurveCanvas(
                        mode = state.mode,
                        keyframes = activeKeyframes,
                        selectedKeyframeId = state.selectedKeyframeId,
                        beats = state.beatAnalysis?.beats.orEmpty(),
                        waveformBars = state.beatAnalysis?.waveformBars.orEmpty(),
                        playheadNormalized = state.previewNormalizedPlayhead,
                        livePreviewSpeed = state.livePreviewSpeed,
                        onSelectKeyframe = viewModel::selectKeyframe,
                        onMoveKeyframe = viewModel::moveKeyframe,
                        onAddKeyframe = viewModel::addKeyframeAt,
                        onToggleManualBeat = viewModel::toggleManualBeatAt,
                    )

                    // Real-time Telemetry Summary
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TelemetryChip(
                            label = "Output",
                            value = MediaStoreSaver.formatDurationMs(state.predictedOutputDurationMs),
                            accentColor = ClipperIndigo,
                            containerColor = ClipperIndigoSoft,
                        )
                        TelemetryChip(
                            label = "Avg Speed",
                            value = String.format(Locale.US, "%.2f×", state.averageSpeed),
                            accentColor = ClipperCyan,
                            containerColor = ClipperCyanSoft,
                        )
                        TelemetryChip(
                            label = "Tempo",
                            value = "${(state.beatAnalysis?.bpm ?: 120f).roundToInt()} BPM",
                            accentColor = ClipperAmber,
                            containerColor = ClipperAmberSoft,
                        )
                        TelemetryChip(
                            label = "Beats",
                            value = "${state.beatAnalysis?.beats?.size ?: 0} hits",
                            accentColor = ClipperMagenta,
                            containerColor = ClipperMagentaSoft,
                        )
                    }
                }
            }

            // 4. Mode-Specific Controls Card
            item {
                if (state.mode == VelocitySystemMode.AUTO_BEAT) {
                    AutoVelocityControlsCard(
                        state = state,
                        onToggleAutoTuning = viewModel::setAutoTuning,
                        onSelectFrequency = viewModel::setAutoFrequency,
                        onSmoothnessChange = viewModel::setAutoSmoothness,
                        onMaxSlowMoChange = viewModel::setAutoMaxSlowMo,
                        onMaxFastChange = viewModel::setAutoMaxFastSpeed,
                        onConvertToCustomCurve = viewModel::convertAutoToCustomCurve,
                    )
                } else {
                    CustomCurveControlsCard(
                        state = state,
                        onApplyPreset = viewModel::applyCurvePreset,
                        onAddPoint = viewModel::addKeyframeAtPlayheadOrGap,
                        onDeletePoint = viewModel::deleteSelectedKeyframe,
                        onToggleSnapBeats = viewModel::setSnapToBeats,
                        onSelectedSpeedChange = viewModel::setSelectedKeyframeSpeed,
                    )
                }
            }

            // 5. Export Progress / Error / Saved Result + Export Action Button
            if (state.isExporting) {
                item {
                    StudioProgressBanner(
                        label = "Rendering smooth velocity video...",
                        progress = state.exportProgress,
                    )
                }
            }

            state.errorMessage?.let { err ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.35f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ClipperRose, modifier = Modifier.size(18.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = InkObsidian)
                        }
                    }
                }
            }

            state.savedResult?.let { saved ->
                item {
                    StudioCard(
                        containerColor = ClipperEmeraldSoft,
                        borderColor = ClipperEmerald.copy(alpha = 0.35f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(22.dp))
                            Column {
                                Text("Saved ${saved.displayName}", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                                Text(
                                    text = "${saved.locationLabel} • ${MediaStoreSaver.formatByteSize(saved.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperEmerald,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::exportVelocityVideo,
                        enabled = state.video != null && !state.isExporting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClipperEmerald,
                            contentColor = StudioWhite,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("velocity_export_button"),
                    ) {
                        Icon(Icons.Outlined.MovieCreation, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isExporting) "Exporting Velocity MP4..." else "Export Velocity Video")
                    }

                    if (state.isExporting) {
                        OutlinedButton(
                            onClick = viewModel::cancelExport,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, ClipperRose.copy(alpha = 0.45f)),
                            modifier = Modifier.height(52.dp),
                        ) {
                            Text("Cancel", color = ClipperRose)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoVelocityControlsCard(
    state: VelocityUiState,
    onToggleAutoTuning: (Boolean) -> Unit,
    onSelectFrequency: (AutoVelocityFrequency) -> Unit,
    onSmoothnessChange: (Float) -> Unit,
    onMaxSlowMoChange: (Float) -> Unit,
    onMaxFastChange: (Float) -> Unit,
    onConvertToCustomCurve: () -> Unit,
) {
    val eff = state.effectiveAutoSettings
    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto Smart Pacing", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                Text(
                    text = if (state.autoSettings.isAutoTuning) {
                        "Auto-tuned from ${(state.beatAnalysis?.bpm ?: 120f).roundToInt()} BPM for silky gentle ramps"
                    } else {
                        "Manual fine-tuning active"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            }
            Switch(
                checked = state.autoSettings.isAutoTuning,
                onCheckedChange = onToggleAutoTuning,
                colors = SwitchDefaults.colors(checkedTrackColor = ClipperIndigo),
                modifier = Modifier.testTag("velocity_auto_tuning_switch"),
            )
        }

        HorizontalDivider(color = StudioBorder)

        Text("Beat Frequency", style = MaterialTheme.typography.labelLarge, color = InkSlate)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutoVelocityFrequency.entries.forEach { freq ->
                ChoicePill(
                    text = freq.label,
                    selected = eff.frequency == freq,
                    onClick = { onSelectFrequency(freq) },
                    tag = "velocity_freq_${freq.name.lowercase(Locale.US)}",
                )
            }
        }

        // Smoothness / Gentleness slider
        VelocitySliderRow(
            label = "Smoothness & Gentleness",
            valueLabel = "${(eff.smoothness * 100f).roundToInt()}%",
            value = eff.smoothness,
            range = 0.15f..1.0f,
            onValueChange = onSmoothnessChange,
            accentColor = ClipperIndigo,
        )

        // Max Slow-Mo slider
        VelocitySliderRow(
            label = "Max Slow-Mo Depth",
            valueLabel = String.format(Locale.US, "%.2f×", eff.maxSlowMoSpeed),
            value = eff.maxSlowMoSpeed,
            range = 0.15f..0.75f,
            onValueChange = onMaxSlowMoChange,
            accentColor = ClipperCyan,
        )

        // Peak Rush Speed slider
        VelocitySliderRow(
            label = "Peak Rush Speed",
            valueLabel = String.format(Locale.US, "%.2f×", eff.maxFastSpeed),
            value = eff.maxFastSpeed,
            range = 1.40f..4.50f,
            onValueChange = onMaxFastChange,
            accentColor = ClipperMagenta,
        )

        OutlinedButton(
            onClick = onConvertToCustomCurve,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("velocity_edit_in_curve_button"),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit Beat Curve Manually", color = ClipperIndigo)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomCurveControlsCard(
    state: VelocityUiState,
    onApplyPreset: (VelocityCurvePreset) -> Unit,
    onAddPoint: () -> Unit,
    onDeletePoint: () -> Unit,
    onToggleSnapBeats: (Boolean) -> Unit,
    onSelectedSpeedChange: (Float) -> Unit,
) {
    val sorted = state.customKeyframes.sortedBy { it.t }
    val selectedIdx = sorted.indexOfFirst { it.id == state.selectedKeyframeId }
    val selectedKf = sorted.getOrNull(selectedIdx)
    val canDeleteSelected = selectedIdx > 0 && selectedIdx < sorted.lastIndex

    StudioCard {
        Text("Curve Presets", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VelocityCurvePreset.entries.forEach { preset ->
                ChoicePill(
                    text = preset.label,
                    selected = state.activePreset == preset,
                    onClick = { onApplyPreset(preset) },
                    tag = "velocity_preset_${preset.name.lowercase(Locale.US)}",
                )
            }
        }

        HorizontalDivider(color = StudioBorder)

        // Add / Delete Point + Snap to Beats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onAddPoint,
                enabled = state.customKeyframes.size < 16,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo, contentColor = StudioWhite),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("velocity_add_point_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Point")
            }

            OutlinedButton(
                onClick = onDeletePoint,
                enabled = canDeleteSelected,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (canDeleteSelected) ClipperRose.copy(alpha = 0.45f) else StudioBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("velocity_delete_point_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = if (canDeleteSelected) ClipperRose else InkMuted,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", color = if (canDeleteSelected) ClipperRose else InkMuted)
            }
        }

        // Precision Speed Slider for the currently selected node
        selectedKf?.let { kf ->
            VelocitySliderRow(
                label = "Selected Point #${selectedIdx + 1} Speed",
                valueLabel = String.format(Locale.US, "%.2f×", kf.speed),
                value = VelocityCurveMath.speedToNormalizedY(kf.speed),
                range = 0f..1f,
                onValueChange = { norm -> onSelectedSpeedChange(VelocityCurveMath.normalizedYToSpeed(norm)) },
                accentColor = ClipperIndigo,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Snap Points to Music Beats", style = MaterialTheme.typography.labelLarge, color = InkObsidian)
                Text("Magnetically align nodes with detected beat markers", style = MaterialTheme.typography.bodySmall, color = InkSlate)
            }
            Switch(
                checked = state.snapToBeats,
                onCheckedChange = onToggleSnapBeats,
                colors = SwitchDefaults.colors(checkedTrackColor = ClipperCyan),
            )
        }
    }
}

@Composable
private fun VelocitySliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = InkSlate)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                color = accentColor,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
        )
    }
}

/**
 * Genuine Real-Time Dual-Player Velocity Preview:
 * Dynamically modulates ExoPlayer's live playback speed every 33ms using [VelocityCurveMath.evaluateSplineSpeed]
 * while keeping the attached Music Audio track playing in sync at 1.0x!
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VelocityLivePreviewPlayer(
    videoUri: Uri,
    videoTrimStartMs: Long,
    videoTrimEndMs: Long,
    musicUri: Uri?,
    musicTrimStartMs: Long,
    keepOriginalAudio: Boolean,
    aspectCrop: VelocityAspectCrop,
    keyframes: List<VelocityKeyframe>,
    onPlayheadChanged: (Float, Float) -> Unit,
) {
    val context = LocalContext.current
    val latestKeyframes by rememberUpdatedState(keyframes)
    val latestTrimStart by rememberUpdatedState(videoTrimStartMs)
    val latestTrimEnd by rememberUpdatedState(videoTrimEndMs)

    var isPlaying by remember { mutableStateOf(false) }

    val videoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    val musicPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(videoUri, videoTrimStartMs, videoTrimEndMs, musicUri, keepOriginalAudio) {
        val clipped = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(videoTrimStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(videoTrimEndMs.coerceAtLeast(videoTrimStartMs + 300L))
                    .build(),
            )
            .build()
        videoPlayer.setMediaItem(clipped)
        videoPlayer.volume = if (musicUri != null || !keepOriginalAudio) 0f else 1f
        videoPlayer.prepare()
    }

    LaunchedEffect(musicUri, musicTrimStartMs) {
        if (musicUri != null) {
            val musicItem = MediaItem.Builder()
                .setUri(musicUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(musicTrimStartMs.coerceAtLeast(0L))
                        .build(),
                )
                .build()
            musicPlayer.setMediaItem(musicItem)
            musicPlayer.prepare()
        } else {
            musicPlayer.stop()
            musicPlayer.clearMediaItems()
        }
    }

    // Live 30 Hz velocity curve speed modulation loop
    LaunchedEffect(isPlaying, musicUri) {
        if (isPlaying) {
            videoPlayer.play()
            if (musicUri != null) {
                musicPlayer.seekTo(0L)
                musicPlayer.play()
            }
        } else {
            videoPlayer.pause()
            musicPlayer.pause()
        }

        var prevPosMs = -1L
        while (isPlaying) {
            val spanMs = (latestTrimEnd - latestTrimStart).coerceAtLeast(300L)
            val posMs = videoPlayer.currentPosition.coerceIn(0L, spanMs)
            val norm = (posMs.toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)
            val instantSpeed = VelocityCurveMath.evaluateSplineSpeed(latestKeyframes, norm)
            videoPlayer.setPlaybackParameters(PlaybackParameters(instantSpeed))
            onPlayheadChanged(norm, instantSpeed)
            // Reliable loop wraparound detection at any speed (including 3x-4x fast peaks)
            if (musicUri != null && prevPosMs >= 0L && posMs < prevPosMs - 150L) {
                musicPlayer.seekTo(0L)
            }
            prevPosMs = posMs
            delay(33L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            musicPlayer.release()
        }
    }

    val previewRatio = aspectCrop.aspectRatio?.coerceIn(0.62f, 1.85f) ?: (16f / 9f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewRatio)
                .clip(RoundedCornerShape(14.dp))
                .background(InkObsidian),
            contentAlignment = Alignment.BottomEnd,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = videoPlayer
                        useController = false
                        resizeMode = if (aspectCrop == VelocityAspectCrop.ORIGINAL) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { pv ->
                    pv.resizeMode = if (aspectCrop == VelocityAspectCrop.ORIGINAL) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                onClick = { isPlaying = !isPlaying },
                color = InkObsidian.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(10.dp)
                    .testTag("velocity_preview_play_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play velocity preview",
                        tint = StudioWhite,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isPlaying) "Pause Preview" else "Live Velocity Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioWhite,
                    )
                }
            }
        }
    }
}
