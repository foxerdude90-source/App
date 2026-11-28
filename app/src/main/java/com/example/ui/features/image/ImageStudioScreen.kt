package com.example.ui.features.image

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.image.BackdropColorPreset
import com.example.core.image.ImageRatioPreset
import com.example.core.image.RatioExportFormat
import com.example.core.image.RatioFitMode
import com.example.core.image.RatioOutputResolution
import com.example.core.media.MediaStoreSaver
import com.example.ui.components.ChoicePill
import com.example.ui.components.StudioCard
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioScreen(
    viewModel: ImageStudioViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.loadSinglePhoto(uri)
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addCollagePhotos(uris)
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
                .widthIn(max = 760.dp)
                .testTag("image_studio_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Mode Switcher Bar (Ratio Studio | Collage Grid | Film Filters | Palette & EXIF)
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoicePill(
                        text = ImageStudioMode.RATIO.label,
                        selected = state.activeMode == ImageStudioMode.RATIO,
                        onClick = { viewModel.selectMode(ImageStudioMode.RATIO) },
                        icon = Icons.Outlined.AspectRatio,
                        tag = "img_mode_ratio",
                    )
                    ChoicePill(
                        text = ImageStudioMode.COLLAGE.label,
                        selected = state.activeMode == ImageStudioMode.COLLAGE,
                        onClick = { viewModel.selectMode(ImageStudioMode.COLLAGE) },
                        icon = Icons.Outlined.GridView,
                        tag = "img_mode_collage",
                    )
                    ChoicePill(
                        text = ImageStudioMode.FILTERS.label,
                        selected = state.activeMode == ImageStudioMode.FILTERS,
                        onClick = { viewModel.selectMode(ImageStudioMode.FILTERS) },
                        icon = Icons.Outlined.AutoAwesome,
                        tag = "img_mode_filters",
                    )
                    ChoicePill(
                        text = ImageStudioMode.PALETTE_EXIF.label,
                        selected = state.activeMode == ImageStudioMode.PALETTE_EXIF,
                        onClick = { viewModel.selectMode(ImageStudioMode.PALETTE_EXIF) },
                        icon = Icons.Outlined.Palette,
                        tag = "img_mode_palette",
                    )
                }
            }

            // Shared Status / Error / Saved Banners
            if (state.isWorking) {
                item {
                    StudioCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ClipperIndigo)
                            Text(state.statusLabel.ifBlank { "Processing image..." }, style = MaterialTheme.typography.bodyMedium, color = InkObsidian)
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.35f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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

            // Active Mode Body
            when (state.activeMode) {
                ImageStudioMode.RATIO -> {
                    item {
                        ImageRatioPanel(
                            state = state,
                            viewModel = viewModel,
                            onPickPhoto = {
                                singlePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
                ImageStudioMode.COLLAGE -> {
                    item {
                        CollageMakerPanel(
                            state = state,
                            viewModel = viewModel,
                            onPickPhotos = {
                                multiPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
                ImageStudioMode.FILTERS -> {
                    item {
                        PhotoFilterPanel(
                            state = state,
                            viewModel = viewModel,
                            onPickPhoto = {
                                singlePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
                ImageStudioMode.PALETTE_EXIF -> {
                    item {
                        PaletteAndExifPanel(
                            state = state,
                            viewModel = viewModel,
                            onPickPhoto = {
                                singlePhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageRatioPanel(
    state: ImageStudioUiState,
    viewModel: ImageStudioViewModel,
    onPickPhoto: () -> Unit,
) {
    val cfg = state.ratioConfig
    val (outW, outH) = state.predictedRatioDimensions

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Photo Picker + Live Ratio Preview Card
        StudioCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPickPhoto,
                    enabled = !state.isWorking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo, contentColor = StudioWhite),
                    modifier = Modifier.testTag("ratio_pick_image_button"),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.sourceBitmap == null) "Pick Image" else "Change Image")
                }

                TelemetryChip(
                    label = "Output",
                    value = "${outW}×${outH}",
                    accentColor = ClipperIndigo,
                    containerColor = ClipperIndigoSoft,
                )
            }

            state.ratioPreview?.let { preview ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioSnow),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Image ratio live preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    )
                }
            } ?: Surface(
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
                    Icon(Icons.Outlined.Collections, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Convert Any Image to Any Ratio", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                        Text(
                            text = "Pick a photo to reframe into 1:1, 4:5, 9:16, 16:9, 21:9, or any custom W:H ratio with Blur, Solid, Gradient, or Crop framing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    }
                }
            }
        }

        // Aspect Ratio Presets + Custom W:H Input
        StudioCard {
            Text("Target Aspect Ratio", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ImageRatioPreset.entries.forEach { preset ->
                    ChoicePill(
                        text = preset.label,
                        selected = cfg.preset == preset,
                        onClick = { viewModel.selectRatioPreset(preset) },
                        tag = "ratio_preset_${preset.tag}",
                    )
                }
            }

            if (cfg.preset == ImageRatioPreset.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.customWInput,
                        onValueChange = { viewModel.updateCustomRatioInputs(it, state.customHInput) },
                        label = { Text("Width") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperIndigo,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSnow,
                            unfocusedContainerColor = StudioSnow,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ratio_custom_w_input"),
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge, color = InkSlate)
                    OutlinedTextField(
                        value = state.customHInput,
                        onValueChange = { viewModel.updateCustomRatioInputs(state.customWInput, it) },
                        label = { Text("Height") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperIndigo,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSnow,
                            unfocusedContainerColor = StudioSnow,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ratio_custom_h_input"),
                    )
                }
            }

            HorizontalDivider(color = StudioBorder)

            // Framing Mode
            Text("Framing & Backdrop Mode", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatioFitMode.entries.forEach { mode ->
                    ChoicePill(
                        text = mode.label,
                        selected = cfg.fitMode == mode,
                        onClick = { viewModel.selectFitMode(mode) },
                        accentColor = ClipperCyan,
                        accentSoft = ClipperCyanSoft,
                        tag = "ratio_fit_${mode.name.lowercase(Locale.US)}",
                    )
                }
            }

            if (cfg.fitMode == RatioFitMode.FIT_SOLID) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BackdropColorPreset.entries.forEach { colorPreset ->
                        ChoicePill(
                            text = colorPreset.label,
                            selected = cfg.backdropColor == colorPreset,
                            onClick = { viewModel.selectBackdropColor(colorPreset) },
                        )
                    }
                }
            }

            // Transform buttons: Rotate / Flip H / Flip V
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::rotateRatioImage,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${cfg.rotationDegrees}°", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = viewModel::toggleRatioFlipH,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (cfg.flipHorizontal) ClipperIndigo else StudioBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Flip H", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = viewModel::toggleRatioFlipV,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (cfg.flipVertical) ClipperIndigo else StudioBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Flip V", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Precision Zoom, Pan, Padding, Corner Radius Sliders
            RatioSliderRow(
                label = "Zoom",
                valueText = String.format(Locale.US, "%.2f×", cfg.zoom),
                value = cfg.zoom,
                range = if (cfg.fitMode == RatioFitMode.CROP_FILL) 1.0f..3.0f else 0.85f..2.0f,
                onChange = { z -> viewModel.updateRatioConfig { it.copy(zoom = z) } },
            )
            RatioSliderRow(
                label = "Horizontal Shift",
                valueText = "${(cfg.panX * 100f).roundToInt()}%",
                value = cfg.panX,
                range = -1f..1f,
                onChange = { px -> viewModel.updateRatioConfig { it.copy(panX = px) } },
            )
            RatioSliderRow(
                label = "Vertical Shift",
                valueText = "${(cfg.panY * 100f).roundToInt()}%",
                value = cfg.panY,
                range = -1f..1f,
                onChange = { py -> viewModel.updateRatioConfig { it.copy(panY = py) } },
            )
            RatioSliderRow(
                label = "Frame Padding",
                valueText = "${(cfg.paddingFraction * 100f).roundToInt()}%",
                value = cfg.paddingFraction,
                range = 0f..0.16f,
                onChange = { p -> viewModel.updateRatioConfig { it.copy(paddingFraction = p) } },
            )
            RatioSliderRow(
                label = "Corner Roundness",
                valueText = "${(cfg.cornerRadiusFraction * 100f).roundToInt()}%",
                value = cfg.cornerRadiusFraction,
                range = 0f..0.25f,
                onChange = { r -> viewModel.updateRatioConfig { it.copy(cornerRadiusFraction = r) } },
            )

            HorizontalDivider(color = StudioBorder)

            // Output Resolution & Format
            Text("Output Resolution & File Format", style = MaterialTheme.typography.labelLarge, color = InkSlate)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatioOutputResolution.entries.forEach { res ->
                    ChoicePill(
                        text = res.label,
                        selected = cfg.resolution == res,
                        onClick = { viewModel.selectOutputResolution(res) },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RatioExportFormat.entries.forEach { fmt ->
                    ChoicePill(
                        text = fmt.label,
                        selected = cfg.format == fmt,
                        onClick = { viewModel.selectExportFormat(fmt) },
                        accentColor = ClipperEmerald,
                        accentSoft = ClipperEmeraldSoft,
                    )
                }
            }

            Button(
                onClick = viewModel::exportRatioImage,
                enabled = state.photoUri != null && !state.isWorking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("ratio_export_button"),
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save ${cfg.preset.label} (${cfg.format.ext.uppercase(Locale.US)})")
            }
        }
    }
}

@Composable
internal fun RatioSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    accent: Color = ClipperIndigo,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkSlate)
            Text(valueText, style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono), color = accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
    }
}
