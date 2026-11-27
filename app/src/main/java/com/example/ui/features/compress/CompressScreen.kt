package com.example.ui.features.compress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.ImageOutputFormat
import com.example.core.media.MediaStoreSaver
import com.example.core.media.VideoFpsPreset
import com.example.core.media.VideoResolutionPreset
import com.example.ui.components.ChoicePill
import com.example.ui.components.ExoVideoPreview
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
import com.example.ui.components.TelemetryChip
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanSoft
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressScreen(
    viewModel: CompressViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.selectVideoUri(uri)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.selectImageUri(uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioWhite),
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
                    title = stringResource(R.string.compress_title),
                    subtitle = stringResource(R.string.compress_subtitle),
                    accentColor = ClipperIndigo,
                )
            }

            // Mode Switcher
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChoicePill(
                        text = stringResource(R.string.compress_mode_video),
                        selected = state.mode == CompressMode.VIDEO,
                        onClick = { viewModel.setMode(CompressMode.VIDEO) },
                        icon = Icons.Outlined.Videocam,
                        modifier = Modifier.weight(1f),
                        tag = "compress_tab_video",
                    )
                    ChoicePill(
                        text = stringResource(R.string.compress_mode_image),
                        selected = state.mode == CompressMode.IMAGE,
                        onClick = { viewModel.setMode(CompressMode.IMAGE) },
                        icon = Icons.Outlined.Image,
                        accentColor = ClipperCyan,
                        accentSoft = ClipperCyanSoft,
                        modifier = Modifier.weight(1f),
                        tag = "compress_tab_image",
                    )
                }
            }

            if (state.isWorking) {
                item {
                    StudioProgressBanner(
                        label = state.progressLabel.ifBlank { "Compressing..." },
                        progress = state.progress,
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

            if (state.mode == CompressMode.VIDEO) {
                // Video Compression Workspace
                item {
                    StudioCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Source Video", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = state.selectedVideo?.fileName ?: "Select a video from your device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.testTag("compress_pick_video_button"),
                            ) {
                                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_pick_video))
                            }
                        }

                        val video = state.selectedVideo
                        if (video?.localUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(InkObsidian),
                            ) {
                                ExoVideoPreview(mediaUri = video.localUri, modifier = Modifier.fillMaxSize())
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TelemetryChip("Original Size", MediaStoreSaver.formatByteSize(video.sizeBytes))
                                if (video.width > 0) {
                                    TelemetryChip("Dimensions", "${video.width}×${video.height}")
                                }
                                if (video.durationMs > 0L) {
                                    TelemetryChip("Duration", MediaStoreSaver.formatDurationMs(video.durationMs))
                                }
                            }
                        }
                    }
                }

                item {
                    StudioCard {
                        Text("Target Resolution", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VideoResolutionPreset.entries.forEach { preset ->
                                ChoicePill(
                                    text = preset.label,
                                    selected = state.videoConfig.resolution == preset,
                                    onClick = { viewModel.updateVideoResolution(preset) },
                                )
                            }
                        }

                        Text("Target Frame Rate", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VideoFpsPreset.entries.forEach { fps ->
                                ChoicePill(
                                    text = fps.label,
                                    selected = state.videoConfig.frameRate == fps,
                                    onClick = { viewModel.updateVideoFps(fps) },
                                    accentColor = ClipperCyan,
                                    accentSoft = ClipperCyanSoft,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Target Bitrate", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format(Locale.US, "%.1f Mbps", state.videoConfig.bitrateMbps),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                color = ClipperIndigo,
                            )
                        }
                        Slider(
                            value = state.videoConfig.bitrateMbps,
                            onValueChange = viewModel::updateVideoBitrate,
                            valueRange = 0.6f..10.0f,
                            colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Strip Audio Track", style = MaterialTheme.typography.titleSmall)
                                Text("Remove sound for ultra-small silent clips", style = MaterialTheme.typography.bodySmall, color = InkSlate)
                            }
                            Switch(
                                checked = state.videoConfig.removeAudio,
                                onCheckedChange = viewModel::updateRemoveAudio,
                            )
                        }

                        Button(
                            onClick = viewModel::compressVideo,
                            enabled = state.selectedVideo != null && !state.isWorking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("compress_video_run_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                        ) {
                            Icon(Icons.Outlined.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.compress_start))
                        }
                    }
                }

                state.videoResult?.let { res ->
                    item {
                        val savedPct = MediaStoreSaver.computeSavingsPercent(res.originalBytes, res.compressedBytes)
                        StudioCard(
                            containerColor = ClipperEmeraldSoft,
                            borderColor = ClipperEmerald.copy(alpha = 0.35f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald)
                                Text("Video Compressed & Saved", style = MaterialTheme.typography.titleMedium)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TelemetryChip("Before", MediaStoreSaver.formatByteSize(res.originalBytes))
                                TelemetryChip("After", MediaStoreSaver.formatByteSize(res.compressedBytes), accentColor = ClipperEmerald)
                                TelemetryChip("Savings", "${if (savedPct >= 0) "-" else "+"}${kotlin.math.abs(savedPct)}%", accentColor = ClipperEmerald)
                            }
                            Text(
                                text = res.savedMedia.locationLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                color = InkSlate,
                            )
                        }
                    }
                }
            } else {
                // Image Compression Workspace
                item {
                    StudioCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Source Image", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = state.selectedImage?.fileName ?: "Select a photo from your device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.testTag("compress_pick_image_button"),
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_pick_image))
                            }
                        }

                        val img = state.selectedImage
                        val compressed = state.imageResult
                        if (compressed != null) {
                            Image(
                                bitmap = compressed.previewBitmap.asImageBitmap(),
                                contentDescription = "Compressed preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StudioSnow),
                            )
                        } else if (img?.localUri != null) {
                            AsyncImage(
                                model = img.localUri,
                                contentDescription = "Selected image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StudioSnow),
                            )
                        }

                        if (img != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TelemetryChip("Original", MediaStoreSaver.formatByteSize(img.sizeBytes))
                                if (img.width > 0) {
                                    TelemetryChip("Source Dim", "${img.width}×${img.height}")
                                }
                                if (compressed != null) {
                                    val pct = MediaStoreSaver.computeSavingsPercent(compressed.originalBytes, compressed.compressedBytes)
                                    TelemetryChip("Optimized", MediaStoreSaver.formatByteSize(compressed.compressedBytes), accentColor = ClipperEmerald)
                                    TelemetryChip("Delta", "${if (pct >= 0) "-" else "+"}${kotlin.math.abs(pct)}%", accentColor = ClipperEmerald)
                                }
                            }
                        }
                    }
                }

                item {
                    StudioCard {
                        Text("Output Format", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImageOutputFormat.entries.forEach { fmt ->
                                ChoicePill(
                                    text = fmt.label,
                                    selected = state.imageConfig.format == fmt,
                                    onClick = { viewModel.updateImageFormat(fmt) },
                                    accentColor = ClipperCyan,
                                    accentSoft = ClipperCyanSoft,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Compression Quality", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${state.imageConfig.quality}%",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                color = ClipperCyan,
                            )
                        }
                        Slider(
                            value = state.imageConfig.quality.toFloat(),
                            onValueChange = { viewModel.updateImageQuality(it.toInt()) },
                            valueRange = 10f..100f,
                            enabled = state.imageConfig.format != ImageOutputFormat.PNG,
                            colors = SliderDefaults.colors(thumbColor = ClipperCyan, activeTrackColor = ClipperCyan),
                        )

                        Text("Maximum Dimension", style = MaterialTheme.typography.titleSmall)
                        val dimOptions = listOf(0 to "Original", 2048 to "2048px", 1920 to "1920px", 1280 to "1280px", 800 to "800px")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            dimOptions.forEach { (px, label) ->
                                ChoicePill(
                                    text = label,
                                    selected = state.imageConfig.maxDimensionPx == px,
                                    onClick = { viewModel.updateImageMaxDim(px) },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { viewModel.compressImage(saveImmediately = false) },
                                enabled = state.selectedImage != null && !state.isWorking,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("compress_image_run_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                            ) {
                                Icon(Icons.Outlined.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (state.imageResult != null) {
                                        viewModel.saveCompressedImage()
                                    } else {
                                        viewModel.compressImage(saveImmediately = true)
                                    }
                                },
                                enabled = state.selectedImage != null && !state.isWorking,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("compress_image_save_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, ClipperEmerald),
                            ) {
                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", color = InkObsidian)
                            }
                        }
                    }
                }

                state.imageSavedResult?.let { saved ->
                    item {
                        StudioCard(
                            containerColor = ClipperEmeraldSoft,
                            borderColor = ClipperEmerald.copy(alpha = 0.35f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald)
                                Column {
                                    Text("Saved ${saved.displayName}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${saved.locationLabel} • ${MediaStoreSaver.formatByteSize(saved.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = ClipperEmerald,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
