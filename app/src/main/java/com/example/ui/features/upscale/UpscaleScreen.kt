package com.example.ui.features.upscale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.media.MediaStoreSaver
import com.example.core.media.VideoFpsPreset
import com.example.core.upscale.UpscaleCodec
import com.example.core.upscale.UpscaleStylePreset
import com.example.core.upscale.UpscaleTargetPreset
import com.example.ui.components.ChoicePill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
import com.example.ui.components.TelemetryChip
import com.example.ui.theme.ClipperAmber
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
fun UpscaleScreen(
    viewModel: UpscaleViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.loadVideo(uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioSnow),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.upscale_title),
                    subtitle = stringResource(R.string.upscale_subtitle),
                    accentColor = ClipperIndigo,
                )
            }

            // Video Picker & Interactive Before/After Split-Screen Preview Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.testTag("upscale_pick_video_button"),
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pick_video))
                        }

                        state.previewResult?.let { prev ->
                            val sign = if (prev.gainPercent >= 0) "+" else ""
                            Surface(
                                color = ClipperEmeraldSoft,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ClipperEmerald.copy(alpha = 0.3f)),
                            ) {
                                Text(
                                    text = "Detail $sign${prev.gainPercent}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = ClipperEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }

                    val preview = state.previewResult
                    val video = state.sourceVideo
                    if (video == null) {
                        Surface(
                            color = StudioSnow,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clickable {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.HighQuality,
                                    contentDescription = null,
                                    tint = ClipperIndigo,
                                    modifier = Modifier.size(42.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Select a Video to Upscale",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkObsidian,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "16-Tap Catmull-Rom Bicubic • FidelityFX CAS • Bilateral Denoise",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                            }
                        }
                    } else {
                        // Real Split-Screen Before / After Comparison Loupe
                        val aspect = if (video.width > 0 && video.height > 0) {
                            (video.width.toFloat() / video.height.toFloat()).coerceIn(0.65f, 1.85f)
                        } else {
                            16f / 9f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspect)
                                .clip(RoundedCornerShape(14.dp))
                                .background(InkObsidian)
                                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                                .clipToBounds()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, _ ->
                                        change.consume()
                                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                                        viewModel.setSplitFraction(frac)
                                    }
                                },
                        ) {
                            if (preview != null) {
                                val origImg = preview.originalFrame.asImageBitmap()
                                val enhImg = preview.enhancedFrame.asImageBitmap()
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val splitX = size.width * state.splitFraction
                                    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())

                                    // Left side: Original Frame
                                    clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                                        drawImage(
                                            image = origImg,
                                            dstOffset = IntOffset.Zero,
                                            dstSize = dstSize,
                                        )
                                    }
                                    // Right side: Enhanced Super-Res Frame
                                    clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                                        drawImage(
                                            image = enhImg,
                                            dstOffset = IntOffset.Zero,
                                            dstSize = dstSize,
                                        )
                                    }

                                    // Crisp divider line & drag handle
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(splitX, 0f),
                                        end = Offset(splitX, size.height),
                                        strokeWidth = 3.dp.toPx(),
                                    )
                                    drawCircle(
                                        color = ClipperIndigo,
                                        radius = 13.dp.toPx(),
                                        center = Offset(splitX, size.height * 0.5f),
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 5.dp.toPx(),
                                        center = Offset(splitX, size.height * 0.5f),
                                    )
                                }

                                // Corner labels
                                Surface(
                                    color = InkObsidian.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = "ORIGINAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Surface(
                                    color = ClipperIndigo.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = "UPSCALED • CAS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ClipperCyan)
                                }
                            }

                            if (state.isRefreshingPreview && preview != null) {
                                CircularProgressIndicator(
                                    color = StudioWhite,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .size(20.dp),
                                )
                            }
                        }

                        // Frame Inspection Scrubber
                        if (video.durationMs > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Drag image to compare • Frame Scrub",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkSlate,
                                )
                                Text(
                                    text = "${MediaStoreSaver.formatDurationMs(state.previewTimeMs)} / ${MediaStoreSaver.formatDurationMs(video.durationMs)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = ClipperIndigo,
                                )
                            }
                            Slider(
                                value = state.previewTimeMs.toFloat(),
                                onValueChange = { viewModel.setPreviewTimeMs(it.toLong()) },
                                valueRange = 0f..video.durationMs.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = ClipperCyan,
                                    activeTrackColor = ClipperCyan,
                                ),
                            )
                        }

                        // Real Upscale Telemetry Row
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TelemetryChip(
                                label = "Source",
                                value = "${video.width}×${video.height}",
                            )
                            TelemetryChip(
                                label = "Upscaled",
                                value = "${state.targetWidth}×${state.targetHeight}",
                                accentColor = ClipperIndigo,
                                containerColor = ClipperIndigoSoft,
                            )
                            TelemetryChip(
                                label = "Target Bitrate",
                                value = String.format(Locale.US, "%.1f Mbps", state.recommendedBitrateMbps),
                                accentColor = ClipperCyan,
                            )
                            TelemetryChip(
                                label = "Codec",
                                value = state.config.codec.label,
                            )
                        }
                    }
                }
            }

            if (state.isExporting) {
                item {
                    StudioProgressBanner(
                        label = state.exportLabel.ifBlank { "GPU Upscaling video..." },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ClipperRose)
                            Text(err, style = MaterialTheme.typography.bodyMedium, color = InkObsidian)
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
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald)
                            Column {
                                Text("Upscaled Video Saved", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                                Text(
                                    text = "${saved.displayName} • ${MediaStoreSaver.formatByteSize(saved.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperEmerald,
                                )
                            }
                        }
                    }
                }
            }

            // Target Super-Resolution Scale & Enhancement Presets
            item {
                StudioCard {
                    Text(
                        text = "Super-Resolution Target",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkObsidian,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UpscaleTargetPreset.entries.forEach { preset ->
                            ChoicePill(
                                text = preset.label,
                                selected = state.config.targetPreset == preset,
                                onClick = { viewModel.selectTargetPreset(preset) },
                                icon = Icons.Outlined.Hd,
                                accentColor = ClipperIndigo,
                                accentSoft = ClipperIndigoSoft,
                                tag = "upscale_target_${preset.name.lowercase(Locale.US)}",
                            )
                        }
                    }

                    Text(
                        text = "GPU Restoration Profile",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkObsidian,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UpscaleStylePreset.entries.forEach { style ->
                            ChoicePill(
                                text = style.label,
                                selected = state.activeStylePreset == style,
                                onClick = { viewModel.applyStylePreset(style) },
                                icon = Icons.Outlined.AutoAwesome,
                                accentColor = ClipperMagenta,
                                accentSoft = ClipperMagentaSoft,
                            )
                        }
                    }
                }
            }

            // Fine-Tune GPU Shader Controls
            item {
                StudioCard {
                    Text(
                        text = "GPU Shader Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkObsidian,
                    )

                    ShaderSliderRow(
                        label = "FidelityFX CAS Sharpness",
                        value = state.config.sharpness,
                        accent = ClipperIndigo,
                        onChange = viewModel::setSharpness,
                    )
                    ShaderSliderRow(
                        label = "Bilateral Edge Denoise",
                        value = state.config.denoise,
                        accent = ClipperCyan,
                        onChange = viewModel::setDenoise,
                    )
                    ShaderSliderRow(
                        label = "Micro-Contrast Clarity",
                        value = state.config.clarity,
                        accent = ClipperMagenta,
                        onChange = viewModel::setClarity,
                    )
                    ShaderSliderRow(
                        label = "Perceptual Vibrance",
                        value = state.config.vibrance,
                        accent = ClipperAmber,
                        onChange = viewModel::setVibrance,
                    )

                    Text("Output Encoder & FPS", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UpscaleCodec.entries.forEach { codec ->
                            ChoicePill(
                                text = codec.label,
                                selected = state.config.codec == codec,
                                onClick = { viewModel.selectCodec(codec) },
                                accentColor = ClipperIndigo,
                                accentSoft = ClipperIndigoSoft,
                            )
                        }
                        VideoFpsPreset.entries.forEach { fps ->
                            ChoicePill(
                                text = fps.label,
                                selected = state.config.targetFps == fps,
                                onClick = { viewModel.selectFps(fps) },
                                accentColor = ClipperCyan,
                                accentSoft = ClipperCyanSoft,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::startGpuUpscaleExport,
                            enabled = state.sourceVideo != null && !state.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("upscale_export_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                        ) {
                            Icon(Icons.Outlined.HighQuality, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.sourceVideo != null) {
                                    "Upscale to ${state.targetWidth}×${state.targetHeight}"
                                } else {
                                    stringResource(R.string.upscale_start_button)
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (state.isExporting) {
                            OutlinedButton(
                                onClick = viewModel::cancelExport,
                                modifier = Modifier.height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ClipperRose),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel upscale", tint = ClipperRose)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShaderSliderRow(
    label: String,
    value: Float,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = InkObsidian)
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                color = accent,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
        )
    }
}
