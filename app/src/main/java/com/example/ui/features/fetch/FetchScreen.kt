package com.example.ui.features.fetch

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.DetectedMediaKind
import com.example.core.media.MediaStoreSaver
import com.example.core.media.VideoFpsPreset
import com.example.core.media.VideoResolutionPreset
import com.example.ui.components.ChoicePill
import com.example.ui.components.ExoVideoPreview
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
fun FetchScreen(
    viewModel: FetchViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.importLocalUri(uri)
        }
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
                    title = stringResource(R.string.fetch_title),
                    subtitle = stringResource(R.string.fetch_subtitle),
                    accentColor = ClipperCyan,
                )
            }

            // URL Input Card
            item {
                StudioCard {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = viewModel::onUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fetch_url_input"),
                        label = { Text("Direct Media URL") },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.fetch_url_placeholder),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = "Link icon",
                                tint = ClipperCyan,
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!pasted.isNullOrBlank()) {
                                        viewModel.onUrlChange(pasted.trim())
                                    }
                                },
                                modifier = Modifier.testTag("fetch_paste_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = stringResource(R.string.action_paste),
                                    tint = InkSlate,
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperCyan,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioWhite,
                            unfocusedContainerColor = StudioSnow,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { viewModel.fetchUrl() }),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::fetchUrl,
                            enabled = !state.isProbing && !state.isProcessing && state.urlInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("fetch_submit_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClipperIndigo,
                                contentColor = StudioWhite,
                            ),
                        ) {
                            if (state.isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = StudioWhite,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.fetch_button))
                        }

                        OutlinedButton(
                            onClick = {
                                mediaPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            enabled = !state.isProbing && !state.isProcessing,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("fetch_import_local_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = InkObsidian,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Device", color = InkObsidian)
                        }
                    }

                    // Transparent Scope Info Pill
                    Surface(
                        color = StudioSnow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = InkSlate,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                            )
                            Text(
                                text = stringResource(R.string.fetch_policy_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }
                    }
                }
            }

            // Progress Banner
            if (state.isProcessing) {
                item {
                    StudioProgressBanner(
                        label = state.progressLabel.ifBlank { "Processing media..." },
                        progress = state.progress,
                    )
                }
            }

            // Error Card
            state.errorMessage?.let { err ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.3f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Error",
                                tint = ClipperRose,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkObsidian,
                            )
                        }
                    }
                }
            }

            // Saved Confirmation Card
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
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Saved",
                                tint = ClipperEmerald,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Saved ${saved.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkObsidian,
                                )
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

            // Live Preview & Export Controls
            val media = state.probedMedia
            if (media != null && media.localUri != null) {
                item {
                    StudioCard {
                        Text(
                            text = "Live Media Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkObsidian,
                        )
                        when (media.kind) {
                            DetectedMediaKind.VIDEO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(InkObsidian),
                                ) {
                                    ExoVideoPreview(
                                        mediaUri = media.localUri,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            DetectedMediaKind.IMAGE -> {
                                AsyncImage(
                                    model = media.localUri,
                                    contentDescription = media.fileName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(StudioSnow),
                                )
                            }

                            DetectedMediaKind.AUDIO -> {
                                Surface(
                                    color = StudioSnow,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AudioFile,
                                                contentDescription = null,
                                                tint = ClipperAmber,
                                            )
                                            Text(
                                                text = media.fileName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InkObsidian,
                                            )
                                        }
                                        ExoVideoPreview(
                                            mediaUri = media.localUri,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(84.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                        )
                                    }
                                }
                            }

                            DetectedMediaKind.OTHER -> Unit
                        }

                        // Real Inspected Telemetry
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TelemetryChip(label = "Type", value = media.kind.name)
                            TelemetryChip(label = "Size", value = MediaStoreSaver.formatByteSize(media.sizeBytes))
                            if (media.width > 0 && media.height > 0) {
                                TelemetryChip(label = "Resolution", value = "${media.width}×${media.height}")
                            }
                            if (media.durationMs > 0L) {
                                TelemetryChip(label = "Duration", value = MediaStoreSaver.formatDurationMs(media.durationMs))
                            }
                            if (media.frameRate > 0f) {
                                TelemetryChip(
                                    label = "Frame Rate",
                                    value = String.format(Locale.US, "%.0f fps", media.frameRate),
                                )
                            }
                        }

                        // Video Output Quality & FPS Options
                        if (media.kind == DetectedMediaKind.VIDEO) {
                            Text(
                                text = "Output Quality",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkObsidian,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VideoResolutionPreset.entries.forEach { preset ->
                                    ChoicePill(
                                        text = preset.label,
                                        selected = state.selectedResolution == preset,
                                        onClick = { viewModel.onResolutionSelected(preset) },
                                        accentColor = ClipperIndigo,
                                        accentSoft = ClipperIndigoSoft,
                                    )
                                }
                            }

                            Text(
                                text = "Target Frame Rate",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkObsidian,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VideoFpsPreset.entries.forEach { fps ->
                                    ChoicePill(
                                        text = fps.label,
                                        selected = state.selectedFps == fps,
                                        onClick = { viewModel.onFpsSelected(fps) },
                                        accentColor = ClipperCyan,
                                        accentSoft = ClipperCyanSoft,
                                    )
                                }
                            }

                            if (state.selectedResolution != VideoResolutionPreset.ORIGINAL ||
                                state.selectedFps != VideoFpsPreset.ORIGINAL
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Target Video Bitrate", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = String.format(Locale.US, "%.1f Mbps", state.targetBitrateMbps),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                        color = ClipperIndigo,
                                    )
                                }
                                Slider(
                                    value = state.targetBitrateMbps,
                                    onValueChange = viewModel::onBitrateChanged,
                                    valueRange = 1.0f..12.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ClipperIndigo,
                                        activeTrackColor = ClipperIndigo,
                                    ),
                                )
                            }
                        }

                        // Primary Export Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { viewModel.exportMedia(extractAudioOnly = false) },
                                enabled = !state.isProcessing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("fetch_download_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.fetch_download_video))
                            }

                            if (media.kind == DetectedMediaKind.VIDEO) {
                                OutlinedButton(
                                    onClick = { viewModel.exportMedia(extractAudioOnly = true) },
                                    enabled = !state.isProcessing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("fetch_extract_audio_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.2.dp, ClipperAmber),
                                ) {
                                    Icon(
                                        Icons.Outlined.MusicNote,
                                        contentDescription = null,
                                        tint = ClipperAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Audio M4A", color = InkObsidian)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
