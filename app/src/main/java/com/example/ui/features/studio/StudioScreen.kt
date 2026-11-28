package com.example.ui.features.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrandingWatermark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.media.EngineUpdateOutcome
import com.example.core.media.EngineUpdateResult
import com.example.core.media.MediaStoreSaver
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
import com.example.ui.features.captions.CaptionsScreen
import com.example.ui.features.captions.CaptionsViewModel
import com.example.ui.features.compress.CompressScreen
import com.example.ui.features.compress.CompressViewModel
import com.example.ui.features.image.ImageStudioScreen
import com.example.ui.features.image.ImageStudioViewModel
import com.example.ui.features.velocity.VelocityScreen
import com.example.ui.features.velocity.VelocityViewModel
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

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    compressViewModel: CompressViewModel,
    captionsViewModel: CaptionsViewModel,
    velocityViewModel: VelocityViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.activeTool != null) {
        viewModel.openTool(null)
    }

    val singleVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.loadSingleVideo(uri)
    }

    val multiVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addMergeClips(uris)
    }

    AnimatedContent(
        targetState = state.activeTool,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 28 })
                .togetherWith(fadeOut(tween(150)))
        },
        label = "pro_lab_tool_transition",
        modifier = modifier
            .fillMaxSize()
            .background(StudioSnow),
    ) { activeTool ->
        if (activeTool == StudioToolId.VELOCITY_STUDIO) {
            Column(modifier = Modifier.fillMaxSize().background(StudioSnow)) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    ToolWorkspaceHeader(
                        title = activeTool.title,
                        subtitle = activeTool.subtitle,
                        onBack = { viewModel.openTool(null) },
                    )
                }
                VelocityScreen(viewModel = velocityViewModel, modifier = Modifier.weight(1f))
            }
        } else if (activeTool == StudioToolId.IMAGE_STUDIO) {
            Column(modifier = Modifier.fillMaxSize().background(StudioSnow)) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    ToolWorkspaceHeader(
                        title = activeTool.title,
                        subtitle = activeTool.subtitle,
                        onBack = { viewModel.openTool(null) },
                    )
                }
                ImageStudioScreen(viewModel = imageStudioViewModel, modifier = Modifier.weight(1f))
            }
        } else if (activeTool == StudioToolId.COMPRESSOR) {
            Column(modifier = Modifier.fillMaxSize().background(StudioSnow)) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    ToolWorkspaceHeader(
                        title = activeTool.title,
                        subtitle = activeTool.subtitle,
                        onBack = { viewModel.openTool(null) },
                    )
                }
                CompressScreen(viewModel = compressViewModel, modifier = Modifier.weight(1f))
            }
        } else if (activeTool == StudioToolId.CAPTIONS_STUDIO) {
            Column(modifier = Modifier.fillMaxSize().background(StudioSnow)) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    ToolWorkspaceHeader(
                        title = activeTool.title,
                        subtitle = activeTool.subtitle,
                        onBack = { viewModel.openTool(null) },
                    )
                }
                CaptionsScreen(viewModel = captionsViewModel, modifier = Modifier.weight(1f))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioSnow),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (activeTool == null) {
                    ProLabHubGrid(
                        isUpdatingEngine = state.isUpdatingEngine,
                        engineUpdateResult = state.engineUpdateResult,
                        onUpdateEngine = viewModel::updateDownloadEngine,
                        onSelectTool = viewModel::openTool,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                    )
                } else {
                    LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ToolWorkspaceHeader(
                        title = activeTool.title,
                        subtitle = activeTool.subtitle,
                        onBack = { viewModel.openTool(null) },
                    )
                }

                // Shared Video Picker for single-video tools
                if (activeTool != StudioToolId.MERGER) {
                    item {
                        StudioCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        singleVideoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !state.isProcessing,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.testTag("studio_pick_video_button"),
                                ) {
                                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_pick_video))
                                }

                                state.selectedVideo?.let { vid ->
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${vid.width}×${vid.height}",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                            color = ClipperIndigo,
                                        )
                                        Text(
                                            text = "${MediaStoreSaver.formatDurationMs(vid.durationMs)} • ${MediaStoreSaver.formatByteSize(vid.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = InkSlate,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isProcessing) {
                    item {
                        StudioProgressBanner(
                            label = state.progressLabel.ifBlank { "Processing..." },
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

                // Render the selected tool workspace
                item {
                    when (activeTool) {
                        StudioToolId.TRIMMER -> TrimmerWorkspace(state, viewModel)
                        StudioToolId.SPEED_RAMP -> SpeedRampWorkspace(state, viewModel)
                        StudioToolId.COLOR_GRADE -> ColorGradeWorkspace(state, viewModel)
                        StudioToolId.MERGER -> MergerWorkspace(
                            state = state,
                            viewModel = viewModel,
                            onAddClips = {
                                multiVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                        )
                        StudioToolId.REFRAME -> ReframeWorkspace(state, viewModel)
                        StudioToolId.WATERMARK -> WatermarkWorkspace(state, viewModel)
                        StudioToolId.AUDIO_DSP -> AudioDspWorkspace(state, viewModel)
                        StudioToolId.FRAME_GRABBER -> FrameGrabberWorkspace(state, viewModel)
                        StudioToolId.VIDEO_LOOPER -> VideoLooperWorkspace(state, viewModel)
                        StudioToolId.FORENSICS -> ForensicsWorkspace(state, viewModel)
                        StudioToolId.VELOCITY_STUDIO, StudioToolId.IMAGE_STUDIO, StudioToolId.COMPRESSOR, StudioToolId.CAPTIONS_STUDIO -> Unit
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ProLabHubGrid(
    isUpdatingEngine: Boolean,
    engineUpdateResult: EngineUpdateResult?,
    onUpdateEngine: () -> Unit,
    onSelectTool: (StudioToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = StudioToolId.entries.filterNot {
        it == StudioToolId.VELOCITY_STUDIO || it == StudioToolId.IMAGE_STUDIO
    }
    val rows = tools.chunked(2)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.studio_title),
                subtitle = stringResource(R.string.studio_subtitle),
                accentColor = ClipperCyan,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Media Engine Updater Card (yt-dlp Nightly Channel)
        item {
            EngineUpdaterCard(
                isUpdating = isUpdatingEngine,
                result = engineUpdateResult,
                onUpdateClick = onUpdateEngine,
            )
        }

        // Featured Velocity Studio Launcher Card
        item {
            FeaturedVelocityToolCard(
                onClick = { onSelectTool(StudioToolId.VELOCITY_STUDIO) },
            )
        }

        // Featured Image Ratio & Studio Launcher Card
        item {
            FeaturedImageStudioCard(
                onClick = { onSelectTool(StudioToolId.IMAGE_STUDIO) },
            )
        }

        items(rows.size) { rowIndex ->
            val pair = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { tool ->
                    StudioToolCard(
                        tool = tool,
                        onClick = { onSelectTool(tool) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StudioToolCard(
    tool: StudioToolId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, accent, softBg) = toolVisualMeta(tool)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .testTag(tool.tag)
            .clickable(onClick = onClick),
        color = StudioWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StudioBorder),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(softBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tool.title,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = InkObsidian,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ToolWorkspaceHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = StudioSnow,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, StudioBorder),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("studio_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Pro Lab",
                    tint = InkObsidian,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = InkObsidian)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSlate)
        }
    }
}

@Composable
private fun FeaturedVelocityToolCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag(StudioToolId.VELOCITY_STUDIO.tag)
            .clickable(onClick = onClick),
        color = StudioWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.25.dp, ClipperIndigo.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClipperIndigoSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShowChart,
                    contentDescription = StudioToolId.VELOCITY_STUDIO.title,
                    tint = ClipperIndigo,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = StudioToolId.VELOCITY_STUDIO.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkObsidian,
                    )
                    Surface(
                        color = ClipperCyanSoft,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "BEAT + CURVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = ClipperCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "Attach music, trim & crop video, auto-sync silky slow-mo to beats, or sculpt custom CapCut-style speed curves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FeaturedImageStudioCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag(StudioToolId.IMAGE_STUDIO.tag)
            .clickable(onClick = onClick),
        color = StudioWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.25.dp, ClipperMagenta.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClipperMagentaSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AspectRatio,
                    contentDescription = StudioToolId.IMAGE_STUDIO.title,
                    tint = ClipperMagenta,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = StudioToolId.IMAGE_STUDIO.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkObsidian,
                    )
                    Surface(
                        color = ClipperMagentaSoft,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "4 TOOLS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = ClipperMagenta,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "Convert any image to any custom aspect ratio (blur/crop/solid/gradient fit), build photo collages, apply film looks, extract palettes & strip EXIF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun toolVisualMeta(tool: StudioToolId): Triple<ImageVector, Color, Color> = when (tool) {
    StudioToolId.VELOCITY_STUDIO -> Triple(Icons.Outlined.ShowChart, ClipperIndigo, ClipperIndigoSoft)
    StudioToolId.IMAGE_STUDIO -> Triple(Icons.Outlined.AspectRatio, ClipperMagenta, ClipperMagentaSoft)
    StudioToolId.TRIMMER -> Triple(Icons.Outlined.ContentCut, ClipperIndigo, ClipperIndigoSoft)
    StudioToolId.SPEED_RAMP -> Triple(Icons.Outlined.Speed, ClipperCyan, ClipperCyanSoft)
    StudioToolId.COLOR_GRADE -> Triple(Icons.Outlined.ColorLens, ClipperMagenta, ClipperMagentaSoft)
    StudioToolId.MERGER -> Triple(Icons.Outlined.MergeType, ClipperEmerald, ClipperEmeraldSoft)
    StudioToolId.REFRAME -> Triple(Icons.Outlined.AspectRatio, ClipperAmber, ClipperAmberSoft)
    StudioToolId.WATERMARK -> Triple(Icons.Outlined.BrandingWatermark, ClipperIndigo, ClipperIndigoSoft)
    StudioToolId.AUDIO_DSP -> Triple(Icons.Outlined.GraphicEq, ClipperRose, ClipperRoseSoft)
    StudioToolId.FRAME_GRABBER -> Triple(Icons.Outlined.GridView, ClipperCyan, ClipperCyanSoft)
    StudioToolId.VIDEO_LOOPER -> Triple(Icons.Outlined.Repeat, ClipperMagenta, ClipperMagentaSoft)
    StudioToolId.FORENSICS -> Triple(Icons.Outlined.Security, ClipperEmerald, ClipperEmeraldSoft)
    StudioToolId.COMPRESSOR -> Triple(Icons.Outlined.Compress, ClipperAmber, ClipperAmberSoft)
    StudioToolId.CAPTIONS_STUDIO -> Triple(Icons.Outlined.Subtitles, ClipperIndigo, ClipperIndigoSoft)
}

@Composable
private fun EngineUpdaterCard(
    isUpdating: Boolean,
    result: EngineUpdateResult?,
    onUpdateClick: () -> Unit,
) {
    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClipperIndigoSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = ClipperIndigo,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Media Engine Updater",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkObsidian,
                    )
                    Text(
                        text = "yt-dlp Nightly Channel • Fixes extractor changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onUpdateClick,
                enabled = !isUpdating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClipperIndigo,
                    contentColor = StudioWhite,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("pro_lab_update_engine_button"),
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = StudioWhite,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Updating...")
                } else {
                    Text("Update Engine")
                }
            }
        }

        result?.let { res ->
            val (bg, fg, icon) = when (res.outcome) {
                EngineUpdateOutcome.DONE -> Triple(ClipperEmeraldSoft, ClipperEmerald, Icons.Outlined.CheckCircle)
                EngineUpdateOutcome.ALREADY_UP_TO_DATE -> Triple(ClipperCyanSoft, ClipperCyan, Icons.Outlined.CheckCircle)
                EngineUpdateOutcome.ERROR -> Triple(ClipperRoseSoft, ClipperRose, Icons.Outlined.ErrorOutline)
            }
            Surface(
                color = bg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, fg.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pro_lab_engine_status_banner"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = res.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = InkObsidian,
                    )
                }
            }
        }
    }
}

