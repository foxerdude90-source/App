package com.example.ui.features.fetch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.media.BatchItemStatus
import com.example.core.media.BatchQualityPreset
import com.example.core.media.BatchQueueHelper
import com.example.core.media.BatchQueueItem
import com.example.core.media.MediaStoreSaver
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
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkMuted
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
fun BatchAddUrlsCard(
    input: String,
    selectedPreset: BatchQualityPreset,
    isQueueRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSelectPreset: (BatchQualityPreset) -> Unit,
    onAddUrls: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add Links to Queue",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkObsidian,
                )
                Text(
                    text = "Paste one URL or multiple URLs (one per line) to download sequentially.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            }
            IconButton(
                onClick = {
                    val clip = clipboard.getText()?.text.orEmpty()
                    if (clip.isNotBlank()) {
                        val merged = if (input.isBlank()) clip else "$input\n$clip"
                        onInputChange(merged)
                    }
                },
                modifier = Modifier.testTag("batch_paste_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = "Paste URLs",
                    tint = ClipperIndigo,
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("batch_urls_input"),
            minLines = 2,
            maxLines = 5,
            placeholder = {
                Text(
                    text = "https://example.com/video-1\nhttps://example.com/video-2",
                    color = InkMuted,
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ClipperIndigo,
                unfocusedBorderColor = StudioBorder,
                focusedContainerColor = StudioSnow,
                unfocusedContainerColor = StudioSnow,
            ),
        )

        Text(
            text = "Queue Quality Preset",
            style = MaterialTheme.typography.labelLarge,
            color = InkSlate,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatchQualityPreset.entries.forEach { preset ->
                ChoicePill(
                    text = preset.label,
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    tag = "batch_preset_${preset.name.lowercase(Locale.US)}",
                )
            }
        }

        Button(
            onClick = onAddUrls,
            enabled = input.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClipperIndigo,
                contentColor = StudioWhite,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("batch_add_button"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isQueueRunning) "Append to Active Queue" else "Add to Queue")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchGlobalProgressCard(
    items: List<BatchQueueItem>,
    isRunning: Boolean,
    onStartQueue: () -> Unit,
    onStopQueue: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
) {
    val summary = BatchQueueHelper.computeSummary(items)
    val animatedGlobal by animateFloatAsState(
        targetValue = summary.globalProgress,
        animationSpec = tween(durationMillis = 220),
        label = "batch_global_progress",
    )

    StudioCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) {
                        "Processing Item ${summary.activeIndex.coerceAtLeast(1)} of ${summary.totalCount}"
                    } else if (summary.totalCount > 0 && summary.queuedCount == 0 && summary.completedCount > 0) {
                        "Batch Complete"
                    } else {
                        "Global Queue Progress"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = InkObsidian,
                )
                Text(
                    text = "${summary.completedCount} saved • ${summary.queuedCount} waiting${if (summary.failedCount > 0) " • ${summary.failedCount} failed" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            }

            Surface(
                color = if (isRunning) ClipperIndigoSoft else ClipperEmeraldSoft,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = "${(animatedGlobal * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                    color = if (isRunning) ClipperIndigo else ClipperEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        LinearProgressIndicator(
            progress = { animatedGlobal },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .testTag("batch_global_progress_bar"),
            color = if (isRunning) ClipperIndigo else ClipperEmerald,
            trackColor = StudioSnow,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TelemetryChip("Total", "${summary.totalCount}", InkObsidian, StudioSnow)
            TelemetryChip("Queued", "${summary.queuedCount}", ClipperIndigo, ClipperIndigoSoft)
            TelemetryChip("Saved", "${summary.completedCount}", ClipperEmerald, ClipperEmeraldSoft)
            if (summary.failedCount > 0) {
                TelemetryChip("Failed", "${summary.failedCount}", ClipperRose, ClipperRoseSoft)
            }
        }

        HorizontalDivider(color = StudioBorder)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isRunning) {
                Button(
                    onClick = onStopQueue,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClipperRose,
                        contentColor = StudioWhite,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("batch_stop_button"),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Queue")
                }
            } else {
                val runnableCount = items.count {
                    it.status == BatchItemStatus.QUEUED || it.status == BatchItemStatus.CANCELLED
                }
                Button(
                    onClick = onStartQueue,
                    enabled = runnableCount > 0,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClipperEmerald,
                        contentColor = StudioWhite,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("batch_start_button"),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (runnableCount > 0) "Start Queue ($runnableCount)" else "Queue Finished")
                }
            }

            if (!isRunning && summary.failedCount > 0) {
                OutlinedButton(
                    onClick = onRetryFailed,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("batch_retry_failed_button"),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Retry failed", modifier = Modifier.size(18.dp))
                }
            }

            if (!isRunning && (summary.completedCount > 0 || summary.totalCount > 0)) {
                OutlinedButton(
                    onClick = if (summary.completedCount > 0 && summary.queuedCount > 0) onClearCompleted else onClearAll,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("batch_clear_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear queue",
                        tint = InkSlate,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BatchQueueItemCard(
    index: Int,
    item: BatchQueueItem,
    onRemove: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    val isActive = item.status == BatchItemStatus.FETCHING_INFO || item.status == BatchItemStatus.DOWNLOADING
    val (badgeBg, badgeFg) = when (item.status) {
        BatchItemStatus.QUEUED -> StudioSnow to InkSlate
        BatchItemStatus.FETCHING_INFO -> ClipperCyanSoft to ClipperCyan
        BatchItemStatus.DOWNLOADING -> ClipperIndigoSoft to ClipperIndigo
        BatchItemStatus.COMPLETED -> ClipperEmeraldSoft to ClipperEmerald
        BatchItemStatus.FAILED -> ClipperRoseSoft to ClipperRose
        BatchItemStatus.CANCELLED -> ClipperAmberSoft to ClipperAmber
    }

    StudioCard(
        containerColor = if (isActive) StudioWhite else StudioWhite,
        borderColor = if (isActive) ClipperIndigo.copy(alpha = 0.45f) else StudioBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("batch_item_row_$index"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Leading status indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                when (item.status) {
                    BatchItemStatus.FETCHING_INFO, BatchItemStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = badgeFg,
                        )
                    }
                    BatchItemStatus.COMPLETED -> {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = "Completed", tint = badgeFg, modifier = Modifier.size(19.dp))
                    }
                    BatchItemStatus.FAILED -> {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = "Failed", tint = badgeFg, modifier = Modifier.size(19.dp))
                    }
                    else -> {
                        Icon(Icons.Outlined.Schedule, contentDescription = "Queued", tint = badgeFg, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: item.sanitizedUrl,
                    style = MaterialTheme.typography.titleSmall,
                    color = InkObsidian,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.title.isNullOrBlank()) {
                    Text(
                        text = item.sanitizedUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = item.status.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Surface(color = StudioSnow, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = item.preset.badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = InkSlate,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (isActive && item.etaSeconds > 0L) {
                        Text(
                            text = "ETA ${item.etaSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = InkSlate,
                        )
                    }
                }
            }

            if (!isActive) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("batch_item_remove_$index"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove item",
                        tint = InkMuted,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // Per-item live progress bar while active
        if (isActive) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.status == BatchItemStatus.DOWNLOADING && item.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = ClipperIndigo,
                        trackColor = StudioSnow,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = ClipperCyan,
                        trackColor = StudioSnow,
                    )
                }
                if (item.statusDetail.isNotBlank()) {
                    Text(
                        text = item.statusDetail,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = InkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Saved path on completion
        item.savedResult?.let { saved ->
            Text(
                text = "Saved • ${saved.locationLabel} (${MediaStoreSaver.formatByteSize(saved.sizeBytes)})",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = ClipperEmerald,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Error detail + optional inline auth action
        item.errorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = ClipperRose,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.requiresAuth) {
                OutlinedButton(
                    onClick = onOpenAuth,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ClipperIndigo.copy(alpha = 0.45f)),
                ) {
                    Icon(Icons.Outlined.Login, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign in to authenticate", color = ClipperIndigo)
                }
            }
        }
    }
}

@Composable
fun BatchEmptyQueueCard() {
    StudioCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClipperIndigoSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = ClipperIndigo,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your download queue is empty",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkObsidian,
                )
                Text(
                    text = "Paste one or multiple URLs above and tap Add to Queue to process them one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSlate,
                )
            }
        }
    }
}
