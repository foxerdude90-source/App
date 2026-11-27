package com.example.core.media

import java.util.UUID

enum class FetchWorkspaceMode(val label: String) {
    SINGLE("Single URL"),
    BATCH("Batch Queue"),
}

enum class BatchItemStatus(val label: String) {
    QUEUED("Queued"),
    FETCHING_INFO("Fetching Info"),
    DOWNLOADING("Downloading"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

enum class BatchQualityPreset(
    val label: String,
    val badge: String,
    val ext: String,
) {
    BEST_MP4("Best MP4", "BEST", "mp4"),
    HD_1080("1080p", "1080p", "mp4"),
    HD_720("720p", "720p", "mp4"),
    AUDIO_M4A("Audio M4A", "M4A", "m4a"),
}

data class BatchQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val rawUrl: String,
    val sanitizedUrl: String,
    val title: String? = null,
    val preset: BatchQualityPreset = BatchQualityPreset.BEST_MP4,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val progress: Float = 0f, // 0f..1f (-1f when indeterminate)
    val etaSeconds: Long = -1L,
    val statusDetail: String = "Waiting in queue",
    val savedResult: SavedMediaResult? = null,
    val errorMessage: String? = null,
    val requiresAuth: Boolean = false,
)

data class BatchQueueSummary(
    val totalCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val queuedCount: Int,
    val activeIndex: Int, // 1-based index of currently processing item, or 0 if idle
    val globalProgress: Float, // 0f..1f smooth continuous progress across all items
)

/**
 * Pure, JVM-testable helper functions for parsing multi-URL batches, mapping presets
 * to yt-dlp format selectors, and computing continuous global batch progress.
 */
object BatchQueueHelper {

    fun parseMultipleUrls(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val tokens = rawText
            .split(Regex("[\\r\\n\\s,]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val seen = LinkedHashSet<String>()
        for (token in tokens) {
            if (!token.contains('.')) continue
            val sanitized = runCatching {
                YoutubeDlMediaEngine.sanitizeMediaUrl(token)
            }.getOrNull() ?: continue
            seen.add(sanitized)
        }
        return seen.toList()
    }

    fun presetToQualityOption(preset: BatchQualityPreset): MediaQualityOption = when (preset) {
        BatchQualityPreset.BEST_MP4 -> MediaQualityOption(
            id = "batch_best_mp4",
            formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best",
            title = "Best Quality (Auto MP4)",
            subtitle = "Highest resolution + best audio",
            badge = "BEST",
            ext = "mp4",
            trackType = QualityTrackType.AUTO,
            requiresAudioMux = true,
        )
        BatchQualityPreset.HD_1080 -> MediaQualityOption(
            id = "batch_1080_mp4",
            formatSelector = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
            title = "1080p Full HD (MP4)",
            subtitle = "Up to 1080p + best audio mux",
            badge = "1080p",
            ext = "mp4",
            trackType = QualityTrackType.VIDEO,
            heightPx = 1080,
            requiresAudioMux = true,
        )
        BatchQualityPreset.HD_720 -> MediaQualityOption(
            id = "batch_720_mp4",
            formatSelector = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best",
            title = "720p HD (MP4)",
            subtitle = "Up to 720p + best audio mux",
            badge = "720p",
            ext = "mp4",
            trackType = QualityTrackType.VIDEO,
            heightPx = 720,
            requiresAudioMux = true,
        )
        BatchQualityPreset.AUDIO_M4A -> MediaQualityOption(
            id = "batch_audio_m4a",
            formatSelector = "bestaudio[ext=m4a]/bestaudio/best",
            title = "Audio Only (M4A)",
            subtitle = "Clean M4A audio extraction",
            badge = "M4A",
            ext = "m4a",
            trackType = QualityTrackType.AUDIO,
            extractAudioOnly = true,
        )
    }

    fun computeSummary(items: List<BatchQueueItem>): BatchQueueSummary {
        if (items.isEmpty()) {
            return BatchQueueSummary(
                totalCount = 0,
                completedCount = 0,
                failedCount = 0,
                queuedCount = 0,
                activeIndex = 0,
                globalProgress = 0f,
            )
        }

        var completed = 0
        var failed = 0
        var queued = 0
        var activeIdx = 0
        var progressAccum = 0f

        items.forEachIndexed { idx, item ->
            when (item.status) {
                BatchItemStatus.COMPLETED -> {
                    completed++
                    progressAccum += 1f
                }
                BatchItemStatus.FAILED -> {
                    failed++
                    progressAccum += 1f
                }
                BatchItemStatus.CANCELLED -> {
                    progressAccum += 1f
                }
                BatchItemStatus.FETCHING_INFO -> {
                    activeIdx = idx + 1
                    progressAccum += 0.12f
                }
                BatchItemStatus.DOWNLOADING -> {
                    activeIdx = idx + 1
                    val p = if (item.progress >= 0f) item.progress.coerceIn(0f, 1f) else 0.25f
                    progressAccum += 0.12f + 0.88f * p
                }
                BatchItemStatus.QUEUED -> {
                    queued++
                }
            }
        }

        return BatchQueueSummary(
            totalCount = items.size,
            completedCount = completed,
            failedCount = failed,
            queuedCount = queued,
            activeIndex = activeIdx,
            globalProgress = (progressAccum / items.size.toFloat()).coerceIn(0f, 1f),
        )
    }
}
