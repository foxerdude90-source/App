package com.example.ui.features.image

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.image.BackdropColorPreset
import com.example.core.image.CollageLayout
import com.example.core.image.ImageRatioPreset
import com.example.core.image.PhotoFilterLook
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
internal fun CollageMakerPanel(
    state: ImageStudioUiState,
    viewModel: ImageStudioViewModel,
    onPickPhotos: () -> Unit,
) {
    val cfg = state.collageConfig
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPickPhotos,
                    enabled = !state.isWorking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo, contentColor = StudioWhite),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("collage_pick_photos_button"),
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Photos (${state.collageBitmaps.size}/6)")
                }

                if (state.collageBitmaps.size >= 2) {
                    OutlinedButton(
                        onClick = viewModel::shuffleCollageOrder,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                    ) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = "Swap cells", modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = viewModel::clearCollagePhotos,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear photos", modifier = Modifier.size(18.dp))
                    }
                }
            }

            state.collagePreview?.let { preview ->
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
                        contentDescription = "Collage grid preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    )
                }
            } ?: Text(
                text = "Select 2 to 6 photos to assemble a crisp high-res photo grid in any social ratio.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate,
            )
        }

        StudioCard {
            Text("Grid Layout", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CollageLayout.entries.forEach { layout ->
                    ChoicePill(
                        text = layout.label,
                        selected = cfg.layout == layout,
                        onClick = { viewModel.updateCollageConfig { it.copy(layout = layout) } },
                    )
                }
            }

            Text("Collage Ratio", style = MaterialTheme.typography.labelLarge, color = InkSlate)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ImageRatioPreset.SQUARE_1_1,
                    ImageRatioPreset.IG_PORTRAIT_4_5,
                    ImageRatioPreset.STORY_9_16,
                    ImageRatioPreset.YOUTUBE_16_9,
                ).forEach { ratio ->
                    ChoicePill(
                        text = ratio.label,
                        selected = cfg.aspectPreset == ratio,
                        onClick = { viewModel.updateCollageConfig { it.copy(aspectPreset = ratio) } },
                        accentColor = ClipperCyan,
                        accentSoft = ClipperCyanSoft,
                    )
                }
            }

            Text("Frame Backdrop", style = MaterialTheme.typography.labelLarge, color = InkSlate)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    BackdropColorPreset.WHITE,
                    BackdropColorPreset.OBSIDIAN,
                    BackdropColorPreset.CREAM,
                    BackdropColorPreset.CRIMSON,
                ).forEach { bg ->
                    ChoicePill(
                        text = bg.label,
                        selected = cfg.backgroundArgb == bg.argb,
                        onClick = { viewModel.updateCollageConfig { it.copy(backgroundArgb = bg.argb) } },
                    )
                }
            }

            RatioSliderRow(
                label = "Cell Gap",
                valueText = "${cfg.spacingPx.roundToInt()} px",
                value = cfg.spacingPx,
                range = 0f..56f,
                onChange = { v -> viewModel.updateCollageConfig { it.copy(spacingPx = v) } },
            )
            RatioSliderRow(
                label = "Corner Radius",
                valueText = "${cfg.cornerRadiusPx.roundToInt()} px",
                value = cfg.cornerRadiusPx,
                range = 0f..64f,
                onChange = { v -> viewModel.updateCollageConfig { it.copy(cornerRadiusPx = v) } },
            )

            Button(
                onClick = viewModel::exportCollage,
                enabled = state.collageBitmaps.size >= 2 && !state.isWorking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("collage_export_button"),
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save High-Res Collage PNG")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoFilterPanel(
    state: ImageStudioUiState,
    viewModel: ImageStudioViewModel,
    onPickPhoto: () -> Unit,
) {
    val cfg = state.filterConfig
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioCard {
            Button(
                onClick = onPickPhoto,
                enabled = !state.isWorking,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo, contentColor = StudioWhite),
                modifier = Modifier.testTag("filter_pick_image_button"),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.sourceBitmap == null) "Pick Photo to Grade" else "Change Photo")
            }

            state.filterPreview?.let { preview ->
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
                        contentDescription = "Filtered photo preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                    )
                }
            } ?: Text(
                text = "Pick a photo to apply curated film color grading, warm tones, radial vignette, and subtle organic grain.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate,
            )
        }

        StudioCard {
            Text("Film Looks", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhotoFilterLook.entries.forEach { look ->
                    ChoicePill(
                        text = look.label,
                        selected = cfg.look == look,
                        onClick = { viewModel.selectFilterLook(look) },
                        accentColor = ClipperMagenta,
                        accentSoft = ClipperMagentaSoft,
                    )
                }
            }

            HorizontalDivider(color = StudioBorder)

            RatioSliderRow(
                label = "Brightness",
                valueText = String.format(Locale.US, "%+.0f%%", cfg.brightness * 100f),
                value = cfg.brightness,
                range = -0.35f..0.35f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(brightness = v) } },
            )
            RatioSliderRow(
                label = "Contrast",
                valueText = String.format(Locale.US, "%.2f×", cfg.contrast),
                value = cfg.contrast,
                range = 0.60f..1.70f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(contrast = v) } },
            )
            RatioSliderRow(
                label = "Saturation",
                valueText = "${(cfg.saturation * 100f).roundToInt()}%",
                value = cfg.saturation,
                range = 0.0f..1.90f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(saturation = v) } },
            )
            RatioSliderRow(
                label = "Warmth / Tint",
                valueText = String.format(Locale.US, "%+.0f", cfg.warmth * 100f),
                value = cfg.warmth,
                range = -0.55f..0.55f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(warmth = v) } },
                accent = ClipperAmber,
            )
            RatioSliderRow(
                label = "Vignette",
                valueText = "${(cfg.vignette * 100f).roundToInt()}%",
                value = cfg.vignette,
                range = 0f..0.85f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(vignette = v) } },
                accent = ClipperMagenta,
            )
            RatioSliderRow(
                label = "Film Grain",
                valueText = "${(cfg.grain * 100f).roundToInt()}%",
                value = cfg.grain,
                range = 0f..0.55f,
                onChange = { v -> viewModel.updateFilterConfig { it.copy(grain = v) } },
                accent = ClipperCyan,
            )

            Button(
                onClick = viewModel::exportFilteredPhoto,
                enabled = state.photoUri != null && !state.isWorking,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("filter_export_button"),
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Graded Photo (JPG)")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaletteAndExifPanel(
    state: ImageStudioUiState,
    viewModel: ImageStudioViewModel,
    onPickPhoto: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copiedHex by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioCard {
            Button(
                onClick = onPickPhoto,
                enabled = !state.isWorking,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo, contentColor = StudioWhite),
                modifier = Modifier.testTag("palette_pick_image_button"),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.sourceBitmap == null) "Pick Photo to Inspect" else "Change Photo")
            }

            if (state.paletteSwatches.isNotEmpty()) {
                Text("Extracted Color Palette (tap to copy HEX)", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.paletteSwatches.forEach { swatch ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(swatch.hex))
                                    copiedHex = swatch.hex
                                },
                            color = StudioSnow,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(swatch.argb)),
                                )
                                Column {
                                    Text(
                                        text = swatch.hex,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                        color = InkObsidian,
                                    )
                                    Text(
                                        text = "${swatch.sharePercent}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                        color = InkSlate,
                                    )
                                }
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy hex", tint = InkSlate, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                copiedHex?.let { hex ->
                    Text("Copied $hex to clipboard", style = MaterialTheme.typography.labelSmall, color = ClipperEmerald)
                }
            } else {
                Text(
                    text = "Pick a photo to extract its 6 dominant color HEX swatches and inspect or strip GPS/EXIF camera metadata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            }
        }

        state.exifReport?.let { exif ->
            StudioCard {
                Text("EXIF & Privacy Inspector", style = MaterialTheme.typography.titleSmall, color = InkObsidian)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TelemetryChip("Resolution", "${exif.width}×${exif.height}", ClipperIndigo, ClipperIndigoSoft)
                    TelemetryChip(
                        label = "GPS Data",
                        value = if (exif.hasGpsLocation) "Present" else "Clean",
                        accentColor = if (exif.hasGpsLocation) ClipperRose else ClipperEmerald,
                        containerColor = if (exif.hasGpsLocation) ClipperRoseSoft else ClipperEmeraldSoft,
                    )
                    TelemetryChip("Camera", exif.cameraMakeModel.take(22), InkObsidian, StudioSnow)
                    TelemetryChip("Captured", exif.dateTaken.take(19), InkSlate, StudioSnow)
                    TelemetryChip("Exposure", exif.exposureSummary.take(24), ClipperAmber, ClipperAmberSoft)
                }

                Button(
                    onClick = viewModel::exportPrivacyCleanPhoto,
                    enabled = !state.isWorking,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("exif_scrub_export_button"),
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Clean Copy (Strip GPS & EXIF)")
                }
            }
        }
    }
}
