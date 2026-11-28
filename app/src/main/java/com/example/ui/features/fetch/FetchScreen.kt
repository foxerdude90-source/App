package com.example.ui.features.fetch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.media.FetchWorkspaceMode
import com.example.core.media.MediaQualityOption
import com.example.core.media.MediaStoreSaver
import com.example.core.media.QualityTrackType
import com.example.ui.components.ChoicePill
import com.example.ui.components.SectionHeader
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
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import com.example.core.media.FetchedMediaMetadata
import com.example.core.music.IdentifiedSong
import com.example.core.music.MusicalTelemetry
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperAmberSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.ClipperMagentaSoft
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FetchScreen(
    viewModel: FetchViewModel,
    modifier: Modifier = Modifier,
    onOpenInSongDetector: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val localVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importLocalFile(uri)
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
                .widthIn(max = 720.dp)
                .testTag("fetch_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.fetch_title),
                    subtitle = stringResource(R.string.fetch_subtitle),
                    accentColor = ClipperIndigo,
                )
            }

            // Mode Switcher: Single URL vs Batch Queue (+ Session Cookies badge)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoicePill(
                        text = FetchWorkspaceMode.SINGLE.label,
                        selected = state.mode == FetchWorkspaceMode.SINGLE,
                        onClick = { viewModel.selectMode(FetchWorkspaceMode.SINGLE) },
                        icon = Icons.Outlined.Link,
                        tag = "fetch_mode_single",
                    )
                    val batchLabel = if (state.batchItems.isNotEmpty()) {
                        "${FetchWorkspaceMode.BATCH.label} (${state.batchItems.size})"
                    } else {
                        FetchWorkspaceMode.BATCH.label
                    }
                    ChoicePill(
                        text = batchLabel,
                        selected = state.mode == FetchWorkspaceMode.BATCH,
                        onClick = { viewModel.selectMode(FetchWorkspaceMode.BATCH) },
                        icon = Icons.Outlined.QueueMusic,
                        tag = "fetch_mode_batch",
                    )
                }
            }

            if (state.mode == FetchWorkspaceMode.BATCH) {
                item {
                    BatchAddUrlsCard(
                        input = state.batchUrlInput,
                        selectedPreset = state.batchPreset,
                        isQueueRunning = state.isBatchRunning,
                        onInputChange = viewModel::onBatchUrlInputChanged,
                        onSelectPreset = viewModel::selectBatchPreset,
                        onAddUrls = viewModel::addUrlsToBatchQueue,
                    )
                }

                state.batchNotice?.let { notice ->
                    item {
                        StudioCard(
                            containerColor = ClipperRoseSoft,
                            borderColor = ClipperRose.copy(alpha = 0.30f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = ClipperRose,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkObsidian,
                                )
                            }
                        }
                    }
                }

                if (state.batchItems.isNotEmpty()) {
                    item {
                        BatchGlobalProgressCard(
                            items = state.batchItems,
                            isRunning = state.isBatchRunning,
                            onStartQueue = viewModel::startBatchQueue,
                            onStopQueue = viewModel::stopBatchQueue,
                            onRetryFailed = viewModel::retryFailedBatchItems,
                            onClearCompleted = viewModel::clearCompletedBatchItems,
                            onClearAll = viewModel::clearAllBatchItems,
                        )
                    }

                    itemsIndexed(
                        items = state.batchItems,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        BatchQueueItemCard(
                            index = index,
                            item = item,
                            onRemove = { viewModel.removeBatchItem(item.id) },
                            onOpenAuth = { viewModel.openCookieDialogForUrl(item.sanitizedUrl) },
                        )
                    }
                } else {
                    item {
                        BatchEmptyQueueCard()
                    }
                }
            } else {
            // URL Input & Fetch Metadata Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Media Link",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .testTag("fetch_cookies_button")
                                .clickable(onClick = viewModel::openCookieDialog),
                            color = if (state.hasSavedCookies) ClipperEmeraldSoft else StudioSnow,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = if (state.hasSavedCookies) ClipperEmerald.copy(alpha = 0.45f) else StudioBorder,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cookie,
                                    contentDescription = "Session Cookies",
                                    tint = if (state.hasSavedCookies) ClipperEmerald else InkSlate,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (state.hasSavedCookies) "Session Active" else "Session Cookies",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.hasSavedCookies) ClipperEmerald else InkSlate,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = viewModel::onUrlChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fetch_url_input"),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.fetch_url_placeholder),
                                color = InkMuted,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                tint = InkSlate,
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.urlInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = viewModel::clearUrl,
                                        modifier = Modifier.testTag("fetch_clear_url_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = "Clear URL",
                                            tint = InkSlate,
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            clipboard.getText()?.text?.let(viewModel::onUrlChanged)
                                        },
                                        modifier = Modifier.testTag("fetch_paste_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentPaste,
                                            contentDescription = stringResource(R.string.action_paste),
                                            tint = ClipperIndigo,
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (!state.isFetchingInfo && !state.isDownloading) {
                                    viewModel.fetchMetadata()
                                }
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperIndigo,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSnow,
                            unfocusedContainerColor = StudioSnow,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::fetchMetadata,
                            enabled = state.urlInput.isNotBlank() && !state.isFetchingInfo && !state.isDownloading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClipperIndigo,
                                contentColor = StudioWhite,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("fetch_inspect_button"),
                        ) {
                            if (state.isFetchingInfo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = StudioWhite,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Fetching Info...")
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.fetch_button))
                            }
                        }

                        if (state.isFetchingInfo) {
                            OutlinedButton(
                                onClick = viewModel::cancelActiveTask,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("fetch_cancel_info_button"),
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }
            }

            // Error Card (with inline "Sign in to authenticate" button on login / rate-limit errors)
            state.errorMessage?.let { error ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.30f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = ClipperRose,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkObsidian,
                            )
                        }
                        if (state.showAuthPromptOnError) {
                            Button(
                                onClick = viewModel::openCookieDialog,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ClipperIndigo,
                                    contentColor = StudioWhite,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("fetch_sign_in_auth_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign in to authenticate")
                            }
                        }
                    }
                }
            }

            // Fetched Media Metadata + Quality Selector + Download Card
            state.metadata?.let { meta ->
                item {
                    StudioCard {
                        // Thumbnail preview if provided by getInfo
                        meta.thumbnailUrl?.let { thumb ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(StudioSnow),
                            ) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = meta.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (meta.durationSec > 0L) {
                                    Surface(
                                        color = InkObsidian.copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp),
                                    ) {
                                        Text(
                                            text = MediaStoreSaver.formatDurationMs(meta.durationSec * 1000L),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = StudioWhite,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = meta.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkObsidian,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                meta.uploader?.let { uploader ->
                                    TelemetryChip(
                                        label = "Creator",
                                        value = uploader.take(24),
                                        accentColor = InkObsidian,
                                        containerColor = StudioSnow,
                                    )
                                }
                                meta.extractorName?.let { extractor ->
                                    TelemetryChip(
                                        label = "Source",
                                        value = extractor,
                                        accentColor = ClipperIndigo,
                                        containerColor = ClipperIndigoSoft,
                                    )
                                }
                                TelemetryChip(
                                    label = "Streams",
                                    value = "${meta.qualities.size} formats",
                                    accentColor = ClipperCyan,
                                    containerColor = ClipperCyanSoft,
                                )
                            }
                        }

                        HorizontalDivider(color = StudioBorder)

                        // Inline Audio File & Song/Music Detector Panel (side feature)
                        InlineAudioAndSongInspectorPanel(
                            meta = meta,
                            isDetectingSong = state.isDetectingSong,
                            detectionStatus = state.songDetectionStatus,
                            detectedSong = state.detectedSong,
                            telemetry = state.detectedSongTelemetry,
                            notice = state.songDetectionNotice,
                            isPlayingPreview = state.isPlayingInlineSongPreview,
                            showLyrics = state.showInlineLyrics,
                            isDownloading = state.isDownloading,
                            onDeepScanSong = viewModel::runDeepSongScanForFetchedMedia,
                            onDownloadAudioOnly = viewModel::selectBestAudioAndDownload,
                            onTogglePreview = viewModel::toggleInlineSongPreview,
                            onToggleLyrics = viewModel::toggleInlineLyrics,
                            onOpenInSongLab = onOpenInSongDetector?.let { cb -> { cb(meta.sourceUrl) } },
                        )

                        HorizontalDivider(color = StudioBorder)

                        // Quality Selector Trigger Surface
                        Text(
                            text = stringResource(R.string.fetch_select_quality),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSlate,
                        )
                        val chosen = state.selectedQuality
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .testTag("fetch_quality_selector_button")
                                .clickable(enabled = !state.isDownloading) {
                                    viewModel.openQualitySheet()
                                },
                            color = StudioSnow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ClipperIndigoSoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (chosen?.trackType == QualityTrackType.AUDIO) {
                                                Icons.Outlined.MusicNote
                                            } else {
                                                Icons.Outlined.HighQuality
                                            },
                                            contentDescription = null,
                                            tint = ClipperIndigo,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chosen?.title ?: "Choose stream quality",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = InkObsidian,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = chosen?.subtitle ?: "Tap to browse video & audio streams",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkSlate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Open quality sheet",
                                    tint = InkSlate,
                                )
                            }
                        }

                        // Live Download Progress Section
                        AnimatedVisibility(
                            visible = state.isDownloading,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Surface(
                                color = ClipperIndigoSoft.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.2f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (state.downloadProgress >= 0f) {
                                                "Downloading • ${(state.downloadProgress * 100f).roundToInt()}%"
                                            } else {
                                                "Preparing stream..."
                                            },
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                            color = ClipperIndigo,
                                        )
                                        if (state.downloadEtaSeconds > 0L) {
                                            Text(
                                                text = "ETA ${state.downloadEtaSeconds}s",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                                color = InkSlate,
                                            )
                                        }
                                    }

                                    if (state.downloadProgress >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { state.downloadProgress.coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .testTag("fetch_download_progress_bar"),
                                            color = ClipperIndigo,
                                            trackColor = StudioWhite,
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .testTag("fetch_download_progress_bar"),
                                            color = ClipperIndigo,
                                            trackColor = StudioWhite,
                                        )
                                    }

                                    if (state.downloadStatusLine.isNotBlank()) {
                                        Text(
                                            text = state.downloadStatusLine,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                            color = InkSlate,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // Download & Cancel Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = viewModel::startDownload,
                                enabled = !state.isDownloading && state.selectedQuality != null,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ClipperEmerald,
                                    contentColor = StudioWhite,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("fetch_download_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isDownloading) "Downloading..." else stringResource(R.string.fetch_download_video),
                                )
                            }

                            if (state.isDownloading) {
                                OutlinedButton(
                                    onClick = viewModel::cancelActiveTask,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, ClipperRose.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("fetch_cancel_download_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Cancel,
                                        contentDescription = "Cancel download",
                                        tint = ClipperRose,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Saved Output Success Banner
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
                                contentDescription = null,
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

            // Local Video Audio Extractor Card (Secondary Convenience)
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Video Audio Extractor",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkObsidian,
                            )
                            Text(
                                text = "Pick any video from your device to extract clean M4A audio into Downloads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = {
                                localVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            },
                            enabled = !state.isDownloading && !state.isFetchingInfo,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.testTag("fetch_pick_local_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }

                    state.localProbed?.let { local ->
                        HorizontalDivider(color = StudioBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = local.fileName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkObsidian,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${local.width}×${local.height} • ${MediaStoreSaver.formatDurationMs(local.durationMs)} • ${MediaStoreSaver.formatByteSize(local.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = InkSlate,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = viewModel::extractAudioFromLocalFile,
                                enabled = !state.isDownloading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                                modifier = Modifier.testTag("fetch_extract_audio_button"),
                            ) {
                                Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("M4A")
                            }
                        }
                    }
                }
            }
            }

            // Subtle Engine Notice
            item {
                Text(
                    text = stringResource(R.string.fetch_policy_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    // Material 3 Quality Selector ModalBottomSheet
    val meta = state.metadata
    if (state.showQualitySheet && meta != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredOptions = meta.qualities.filter { opt ->
            state.qualityFilter == null || opt.trackType == state.qualityFilter ||
                (state.qualityFilter == QualityTrackType.VIDEO && opt.trackType == QualityTrackType.AUTO)
        }

        ModalBottomSheet(
            onDismissRequest = viewModel::dismissQualitySheet,
            sheetState = sheetState,
            containerColor = StudioWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.testTag("quality_selector_sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Select Download Quality",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkObsidian,
                    )
                    Text(
                        text = meta.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Filter pills: All / Video / Audio Only
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill(
                        text = "All",
                        selected = state.qualityFilter == null,
                        onClick = { viewModel.selectQualityFilter(null) },
                        tag = "quality_filter_all",
                    )
                    ChoicePill(
                        text = "Video",
                        selected = state.qualityFilter == QualityTrackType.VIDEO,
                        onClick = { viewModel.selectQualityFilter(QualityTrackType.VIDEO) },
                        tag = "quality_filter_video",
                    )
                    ChoicePill(
                        text = "Audio",
                        selected = state.qualityFilter == QualityTrackType.AUDIO,
                        onClick = { viewModel.selectQualityFilter(QualityTrackType.AUDIO) },
                        tag = "quality_filter_audio",
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredOptions, key = { it.id }) { option ->
                        QualityOptionRow(
                            option = option,
                            selected = state.selectedQuality?.id == option.id,
                            onClick = { viewModel.selectQuality(option) },
                        )
                    }
                }
            }
        }
    }

    // Optional In-App WebView Session Cookie Manager Dialog
    if (state.showCookieDialog) {
        CookieSessionDialog(
            initialUrl = state.cookieDialogUrl,
            hasSavedCookies = state.hasSavedCookies,
            onDismiss = viewModel::dismissCookieDialog,
            onCookiesChanged = viewModel::onCookiesUpdated,
        )
    }
}

@Composable
private fun QualityOptionRow(
    option: MediaQualityOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("quality_option_${option.id}")
            .clickable(onClick = onClick),
        color = if (selected) ClipperIndigoSoft else StudioSnow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 1.25.dp else 0.75.dp,
            color = if (selected) ClipperIndigo else StudioBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = if (option.trackType == QualityTrackType.AUDIO) ClipperCyanSoft else ClipperIndigoSoft,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = option.badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (option.trackType == QualityTrackType.AUDIO) ClipperCyan else ClipperIndigo,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkObsidian,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = ClipperIndigo,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineAudioAndSongInspectorPanel(
    meta: FetchedMediaMetadata,
    isDetectingSong: Boolean,
    detectionStatus: String,
    detectedSong: IdentifiedSong?,
    telemetry: MusicalTelemetry?,
    notice: String?,
    isPlayingPreview: Boolean,
    showLyrics: Boolean,
    isDownloading: Boolean,
    onDeepScanSong: () -> Unit,
    onDownloadAudioOnly: () -> Unit,
    onTogglePreview: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenInSongLab: (() -> Unit)?,
) {
    Surface(
        color = StudioSnow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (!meta.hasAudioTrack) ClipperRose.copy(alpha = 0.35f) else ClipperIndigo.copy(alpha = 0.22f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fetch_audio_song_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (meta.hasAudioTrack) Icons.Outlined.GraphicEq else Icons.Outlined.MusicOff,
                        contentDescription = null,
                        tint = if (meta.hasAudioTrack) ClipperIndigo else ClipperRose,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Audio File & Song Detector",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = InkObsidian,
                    )
                }

                if (onOpenInSongLab != null && meta.hasAudioTrack) {
                    Surface(
                        color = ClipperMagentaSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("fetch_open_song_lab_button")
                            .clickable(onClick = onOpenInSongLab),
                    ) {
                        Text(
                            text = "Song Lab",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ClipperMagenta,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 1. Actual Embedded Audio File Status
            if (!meta.hasAudioTrack) {
                Surface(
                    color = ClipperRoseSoft,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.75.dp, ClipperRose.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_no_audio_badge"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.MusicOff, contentDescription = null, tint = ClipperRose, modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                text = "No audio file detected",
                                style = MaterialTheme.typography.labelLarge,
                                color = ClipperRose,
                            )
                            Text(
                                text = "This video/short contains video-only streams without an audio track.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }
                    }
                }
            } else {
                val primaryAudio = meta.detectedAudioStreams.firstOrNull()
                Surface(
                    color = StudioWhite,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_detected_audio_file_row"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(ClipperEmeraldSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.AudioFile, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (primaryAudio != null) {
                                        "Audio Track: ${primaryAudio.codec.ifBlank { "AUDIO" }} (${primaryAudio.containerExt.uppercase(Locale.US)})"
                                    } else {
                                        "Audio Track Present"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InkObsidian,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = primaryAudio?.let { aud ->
                                        val sz = if (aud.fileSizeBytes > 0L) " • ${MediaStoreSaver.formatByteSize(aud.fileSizeBytes)}" else ""
                                        "${aud.summaryLine()}$sz"
                                    } ?: "Ready for audio extraction",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!isDownloading) {
                            OutlinedButton(
                                onClick = onDownloadAudioOnly,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ClipperEmerald.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("fetch_extract_audio_quick_button"),
                            ) {
                                Text("Save Audio", style = MaterialTheme.typography.labelSmall, color = ClipperEmerald)
                            }
                        }
                    }
                }

                // 2. Active Song Recognition Spinner
                if (isDetectingSong) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = ClipperIndigo,
                        )
                        Text(
                            text = detectionStatus.ifBlank { "Identifying song & vocals..." },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = ClipperIndigo,
                        )
                    }
                }

                // 3. Identified Song Result Card
                detectedSong?.let { song ->
                    Surface(
                        color = StudioWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fetch_detected_song_result"),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ClipperIndigoSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!song.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = song.artworkUrl,
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Album, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(26.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${song.matchSource.badgeLabel} • ${song.confidencePercent}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                        color = ClipperEmerald,
                                    )
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = InkObsidian,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ClipperIndigo,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!song.previewAudioUrl.isNullOrBlank()) {
                                    Surface(
                                        color = if (isPlayingPreview) ClipperRose else ClipperIndigo,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        IconButton(
                                            onClick = onTogglePreview,
                                            modifier = Modifier.size(38.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingPreview) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                                contentDescription = if (isPlayingPreview) "Pause preview" else "Play 30s preview",
                                                tint = StudioWhite,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            val activeTelemetry = telemetry ?: song.telemetry
                            if (activeTelemetry != null) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (activeTelemetry.bpm > 0f) {
                                        TelemetryChip("BPM", String.format(Locale.US, "%.0f", activeTelemetry.bpm), ClipperCyan, ClipperCyanSoft)
                                    }
                                    TelemetryChip("Key", "${activeTelemetry.musicalKey} (${activeTelemetry.camelotCode})", ClipperMagenta, ClipperMagentaSoft)
                                }
                            }

                            if (song.syncedLyrics.isNotEmpty() || !song.plainLyrics.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = onToggleLyrics,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Outlined.Lyrics, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(if (showLyrics) "Hide Lyrics" else "View Synced Lyrics", style = MaterialTheme.typography.labelSmall)
                                }
                                if (showLyrics) {
                                    val previewLines = if (song.syncedLyrics.isNotEmpty()) {
                                        song.syncedLyrics.take(8).joinToString("\n") { it.text }
                                    } else {
                                        song.plainLyrics.orEmpty().lines().take(8).joinToString("\n")
                                    }
                                    Text(
                                        text = previewLines,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSlate,
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Notice & Deep 2–4s Acoustic DSP Scan Button
                if (!notice.isNullOrBlank() && detectedSong == null) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                    )
                }

                OutlinedButton(
                    onClick = onDeepScanSong,
                    enabled = !isDetectingSong && !isDownloading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fetch_deep_song_scan_button"),
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (detectedSong != null) "Re-Scan Audio Waveform (2–4s DSP)" else "Deep Song Scan (2–4s Audio & Vocal DSP)",
                        color = ClipperIndigo,
                    )
                }
            }
        }
    }
}

