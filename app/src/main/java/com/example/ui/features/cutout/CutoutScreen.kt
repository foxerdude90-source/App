package com.example.ui.features.cutout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.cutout.CutoutBackgroundStyle
import com.example.core.cutout.LassoMode
import com.example.core.cutout.NormalizedPoint
import com.example.core.media.MediaStoreSaver
import com.example.ui.components.CheckerboardBackground
import com.example.ui.components.ChoicePill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanBright
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
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CutoutScreen(
    viewModel: CutoutViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activePathCanvasPoints = remember { mutableStateListOf<Offset>() }
    val activeNormalizedPoints = remember { mutableStateListOf<NormalizedPoint>() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.loadImage(uri)
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
                    title = stringResource(R.string.cutout_title),
                    subtitle = stringResource(R.string.cutout_subtitle),
                    accentColor = ClipperMagenta,
                )
            }

            // Interactive Canvas Workspace
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.testTag("cutout_pick_image_button"),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_pick_image))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = viewModel::undo,
                                enabled = state.canUndo && !state.isProcessing,
                                modifier = Modifier.testTag("cutout_undo_button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Undo,
                                    contentDescription = stringResource(R.string.action_undo),
                                    tint = if (state.canUndo) InkObsidian else InkSlate.copy(alpha = 0.35f),
                                )
                            }
                            IconButton(
                                onClick = viewModel::redo,
                                enabled = state.canRedo && !state.isProcessing,
                                modifier = Modifier.testTag("cutout_redo_button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Redo,
                                    contentDescription = stringResource(R.string.action_redo),
                                    tint = if (state.canRedo) InkObsidian else InkSlate.copy(alpha = 0.35f),
                                )
                            }
                            IconButton(
                                onClick = viewModel::toggleOriginalComparison,
                                enabled = state.sourceBitmap != null,
                                modifier = Modifier.testTag("cutout_compare_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.Compare,
                                    contentDescription = "Compare original",
                                    tint = if (state.showOriginalComparison) ClipperMagenta else InkObsidian,
                                )
                            }
                            IconButton(
                                onClick = viewModel::resetMask,
                                enabled = state.sourceBitmap != null && !state.isProcessing,
                                modifier = Modifier.testTag("cutout_reset_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = stringResource(R.string.action_reset),
                                    tint = InkObsidian,
                                )
                            }
                        }
                    }

                    val shownBitmap = if (state.showOriginalComparison) state.sourceBitmap else state.previewBitmap
                    if (shownBitmap == null) {
                        Surface(
                            color = StudioSnow,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clickable {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Gesture,
                                    contentDescription = null,
                                    tint = ClipperMagenta,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Tap to Load an Image", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "AI Portrait • Smart Object • Lasso • Magic Wand",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                            }
                        }
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp)),
                        ) {
                            val density = LocalDensity.current
                            val boxW = with(density) { maxWidth.toPx() }
                            val boxH = with(density) { maxHeight.toPx() }
                            val imageRect = remember(boxW, boxH, shownBitmap.width, shownBitmap.height) {
                                computeFittedImageRect(boxW, boxH, shownBitmap.width, shownBitmap.height)
                            }

                            CheckerboardBackground()

                            Image(
                                bitmap = shownBitmap.asImageBitmap(),
                                contentDescription = "Cutout editing canvas",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Interactive Touch Layer mapped strictly to imageRect
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(state.activeTool, imageRect, state.isProcessing) {
                                        if (state.isProcessing) return@pointerInput
                                        if (state.activeTool == CutoutTool.MAGIC_WAND) {
                                            detectTapGestures { offset ->
                                                toNormalizedPoint(offset, imageRect)?.let { pt ->
                                                    viewModel.applyMagicWandTap(pt)
                                                }
                                            }
                                        } else {
                                            detectDragGestures(
                                                onDragStart = { startOffset ->
                                                    activePathCanvasPoints.clear()
                                                    activeNormalizedPoints.clear()
                                                    toNormalizedPoint(startOffset, imageRect)?.let { pt ->
                                                        activePathCanvasPoints.add(startOffset)
                                                        activeNormalizedPoints.add(pt)
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val pos = change.position
                                                    toNormalizedPoint(pos, imageRect)?.let { pt ->
                                                        activePathCanvasPoints.add(pos)
                                                        activeNormalizedPoints.add(pt)
                                                    }
                                                },
                                                onDragEnd = {
                                                    val captured = activeNormalizedPoints.toList()
                                                    activePathCanvasPoints.clear()
                                                    activeNormalizedPoints.clear()
                                                    when (state.activeTool) {
                                                        CutoutTool.LASSO -> viewModel.applyLasso(captured)
                                                        CutoutTool.ERASE_BRUSH,
                                                        CutoutTool.RESTORE_BRUSH -> viewModel.applyBrushStroke(captured)
                                                        CutoutTool.MAGIC_WAND -> Unit
                                                    }
                                                },
                                                onDragCancel = {
                                                    activePathCanvasPoints.clear()
                                                    activeNormalizedPoints.clear()
                                                },
                                            )
                                        }
                                    },
                            ) {
                                // Live Lasso / Brush Stroke Overlay
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (activePathCanvasPoints.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(activePathCanvasPoints.first().x, activePathCanvasPoints.first().y)
                                            for (i in 1 until activePathCanvasPoints.size) {
                                                lineTo(activePathCanvasPoints[i].x, activePathCanvasPoints[i].y)
                                            }
                                        }
                                        val strokeColor = when (state.activeTool) {
                                            CutoutTool.LASSO -> ClipperCyanBright
                                            CutoutTool.ERASE_BRUSH -> ClipperRose
                                            CutoutTool.RESTORE_BRUSH -> ClipperEmerald
                                            CutoutTool.MAGIC_WAND -> ClipperMagenta
                                        }
                                        val strokeWidthPx = if (state.activeTool == CutoutTool.LASSO) {
                                            3.dp.toPx()
                                        } else {
                                            (state.brushSizeFraction * min(imageRect.width, imageRect.height) * 2f)
                                                .coerceAtLeast(8f)
                                        }
                                        drawPath(
                                            path = path,
                                            color = strokeColor.copy(alpha = if (state.activeTool == CutoutTool.LASSO) 0.95f else 0.45f),
                                            style = Stroke(
                                                width = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                                pathEffect = if (state.activeTool == CutoutTool.LASSO) {
                                                    PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                                } else {
                                                    null
                                                },
                                            ),
                                        )
                                    }
                                }

                                if (state.isProcessing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(StudioWhite.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = ClipperMagenta)
                                    }
                                }
                            }
                        }
                    }

                    state.statusMessage?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    }
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
                            Text(err, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // One-Tap AI Subject Background Removal Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("AI Background Remover", style = MaterialTheme.typography.titleMedium)
                        state.activeEngineLabel?.let { engine ->
                            Surface(
                                color = ClipperEmeraldSoft,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, ClipperEmerald.copy(alpha = 0.3f)),
                            ) {
                                Text(
                                    text = engine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ClipperEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Primary Hero Button: General-Purpose ML Kit Subject Segmentation (Any Object/Pet/Person)
                    Button(
                        onClick = viewModel::runAiSubjectDetect,
                        enabled = state.sourceBitmap != null && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("cutout_ai_subject_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Remove Background (Any Subject)")
                    }

                    // Multi-Subject Isolation Chips (when ML Kit finds > 1 distinct subjects)
                    if (state.detectedSubjects.size > 1) {
                        Text(
                            text = "Isolate Individual Subject",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkObsidian,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChoicePill(
                                text = "All (${state.detectedSubjects.size})",
                                selected = state.selectedSubjectId == null,
                                onClick = { viewModel.selectDetectedSubject(null) },
                                accentColor = ClipperIndigo,
                                accentSoft = ClipperIndigoSoft,
                            )
                            state.detectedSubjects.forEach { subject ->
                                ChoicePill(
                                    text = "${subject.label} (${subject.coveragePercent.toInt()}%)",
                                    selected = state.selectedSubjectId == subject.id,
                                    onClick = { viewModel.selectDetectedSubject(subject.id) },
                                    accentColor = ClipperMagenta,
                                    accentSoft = ClipperMagentaSoft,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::runAiPortraitDetect,
                            enabled = state.sourceBitmap != null && !state.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cutout_ai_portrait_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.45f)),
                        ) {
                            Icon(Icons.Outlined.PersonSearch, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cutout_auto_portrait), color = InkObsidian)
                        }

                        OutlinedButton(
                            onClick = viewModel::runSmartObjectDetect,
                            enabled = state.sourceBitmap != null && !state.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cutout_smart_object_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClipperMagenta.copy(alpha = 0.45f)),
                        ) {
                            Icon(Icons.Outlined.Category, contentDescription = null, tint = ClipperMagenta, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edge-Barrier", color = InkObsidian)
                        }
                    }

                    // Pro Mask Edge Refinement Controls
                    Text("Edge Refinement", style = MaterialTheme.typography.labelLarge, color = InkObsidian)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoicePill(
                            text = "Shrink -1px",
                            selected = false,
                            onClick = { viewModel.adjustMaskEdge(-1) },
                            accentColor = ClipperCyan,
                            accentSoft = ClipperCyanSoft,
                        )
                        ChoicePill(
                            text = "Expand +1px",
                            selected = false,
                            onClick = { viewModel.adjustMaskEdge(1) },
                            accentColor = ClipperCyan,
                            accentSoft = ClipperCyanSoft,
                        )
                        ChoicePill(
                            text = "Invert Mask",
                            selected = false,
                            onClick = viewModel::invertCurrentMask,
                            accentColor = ClipperMagenta,
                            accentSoft = ClipperMagentaSoft,
                        )
                        ChoicePill(
                            text = if (state.decontaminateEdges) "De-Fringe ON" else "De-Fringe OFF",
                            selected = state.decontaminateEdges,
                            onClick = viewModel::toggleDecontaminateEdges,
                            accentColor = ClipperEmerald,
                            accentSoft = ClipperEmeraldSoft,
                        )
                        ChoicePill(
                            text = if (state.autoCropExport) "Auto-Crop ON" else "Auto-Crop OFF",
                            selected = state.autoCropExport,
                            onClick = viewModel::toggleAutoCropExport,
                            accentColor = ClipperIndigo,
                            accentSoft = ClipperIndigoSoft,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Edge-Barrier Sensitivity (ΔE)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${state.objectSensitivity.toInt()}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                            color = ClipperMagenta,
                        )
                    }
                    Slider(
                        value = state.objectSensitivity,
                        onValueChange = viewModel::setObjectSensitivity,
                        valueRange = 10f..60f,
                        colors = SliderDefaults.colors(thumbColor = ClipperMagenta, activeTrackColor = ClipperMagenta),
                    )
                }
            }

            // Precision Tools Card (Lasso, Magic Wand, Erase, Restore)
            item {
                StudioCard {
                    Text("Precision Mask Tools", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChoicePill(
                            text = stringResource(R.string.cutout_tool_lasso),
                            selected = state.activeTool == CutoutTool.LASSO,
                            onClick = { viewModel.selectTool(CutoutTool.LASSO) },
                            icon = Icons.Outlined.Gesture,
                            accentColor = ClipperCyan,
                            accentSoft = ClipperCyanSoft,
                            tag = "cutout_tool_lasso",
                        )
                        ChoicePill(
                            text = stringResource(R.string.cutout_tool_wand),
                            selected = state.activeTool == CutoutTool.MAGIC_WAND,
                            onClick = { viewModel.selectTool(CutoutTool.MAGIC_WAND) },
                            icon = Icons.Outlined.AutoFixHigh,
                            accentColor = ClipperMagenta,
                            accentSoft = ClipperMagentaSoft,
                            tag = "cutout_tool_wand",
                        )
                        ChoicePill(
                            text = stringResource(R.string.cutout_tool_erase),
                            selected = state.activeTool == CutoutTool.ERASE_BRUSH,
                            onClick = { viewModel.selectTool(CutoutTool.ERASE_BRUSH) },
                            icon = Icons.Outlined.CleaningServices,
                            accentColor = ClipperRose,
                            accentSoft = ClipperRoseSoft,
                            tag = "cutout_tool_erase",
                        )
                        ChoicePill(
                            text = stringResource(R.string.cutout_tool_restore),
                            selected = state.activeTool == CutoutTool.RESTORE_BRUSH,
                            onClick = { viewModel.selectTool(CutoutTool.RESTORE_BRUSH) },
                            icon = Icons.Outlined.Brush,
                            accentColor = ClipperEmerald,
                            accentSoft = ClipperEmeraldSoft,
                            tag = "cutout_tool_restore",
                        )
                    }

                    when (state.activeTool) {
                        CutoutTool.LASSO -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ChoicePill(
                                    text = "Keep Inside Lasso",
                                    selected = state.lassoMode == LassoMode.KEEP_INSIDE,
                                    onClick = { viewModel.selectLassoMode(LassoMode.KEEP_INSIDE) },
                                    modifier = Modifier.weight(1f),
                                    accentColor = ClipperCyan,
                                    accentSoft = ClipperCyanSoft,
                                )
                                ChoicePill(
                                    text = "Cut Inside Lasso",
                                    selected = state.lassoMode == LassoMode.CUT_INSIDE,
                                    onClick = { viewModel.selectLassoMode(LassoMode.CUT_INSIDE) },
                                    modifier = Modifier.weight(1f),
                                    accentColor = ClipperRose,
                                    accentSoft = ClipperRoseSoft,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Edge Feathering", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${state.featherRadius} px",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                )
                            }
                            Slider(
                                value = state.featherRadius.toFloat(),
                                onValueChange = { viewModel.setFeatherRadius(it.toInt()) },
                                valueRange = 0f..6f,
                                steps = 5,
                            )
                        }

                        CutoutTool.MAGIC_WAND -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Color Tolerance (ΔE)", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${state.wandTolerance.toInt()}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                    color = ClipperMagenta,
                                )
                            }
                            Slider(
                                value = state.wandTolerance,
                                onValueChange = viewModel::setWandTolerance,
                                valueRange = 8f..80f,
                            )
                            Text(
                                text = "Tap any region on the canvas to flood-erase similar colors.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }

                        CutoutTool.ERASE_BRUSH, CutoutTool.RESTORE_BRUSH -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Brush Radius", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${(state.brushSizeFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                )
                            }
                            Slider(
                                value = state.brushSizeFraction,
                                onValueChange = viewModel::setBrushSize,
                                valueRange = 0.015f..0.15f,
                            )
                        }
                    }
                }
            }

            // Backdrop & Export Card
            item {
                StudioCard {
                    Text("Backdrop & Export", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CutoutBackgroundStyle.entries.forEach { bg ->
                            val selected = state.backgroundStyle == bg
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectBackground(bg) }
                                    .padding(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) ClipperIndigo else StudioBorder,
                                            shape = CircleShape,
                                        ),
                                ) {
                                    if (bg == CutoutBackgroundStyle.TRANSPARENT) {
                                        CheckerboardBackground()
                                    } else if (bg.secondaryColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(bg.primaryColor), Color(bg.secondaryColor))
                                                    )
                                                ),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(bg.primaryColor)),
                                        )
                                    }
                                }
                                Text(
                                    text = bg.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) ClipperIndigo else InkSlate,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::exportPng,
                        enabled = state.previewBitmap != null && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cutout_export_png_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                    ) {
                        Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cutout_export_png))
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

private fun computeFittedImageRect(
    containerW: Float,
    containerH: Float,
    bitmapW: Int,
    bitmapH: Int,
): Rect {
    if (bitmapW <= 0 || bitmapH <= 0 || containerW <= 0f || containerH <= 0f) {
        return Rect(0f, 0f, containerW, containerH)
    }
    val scale = min(containerW / bitmapW.toFloat(), containerH / bitmapH.toFloat())
    val drawW = bitmapW * scale
    val drawH = bitmapH * scale
    val left = (containerW - drawW) / 2f
    val top = (containerH - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}

private fun toNormalizedPoint(offset: Offset, imageRect: Rect): NormalizedPoint? {
    if (imageRect.width <= 0f || imageRect.height <= 0f) return null
    val nx = ((offset.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
    val ny = ((offset.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
    return NormalizedPoint(nx, ny)
}
