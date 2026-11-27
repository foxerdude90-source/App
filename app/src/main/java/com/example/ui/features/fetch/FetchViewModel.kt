package com.example.ui.features.fetch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.BatchItemStatus
import com.example.core.media.BatchQualityPreset
import com.example.core.media.BatchQueueHelper
import com.example.core.media.BatchQueueItem
import com.example.core.media.CookieSessionManager
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.FetchWorkspaceMode
import com.example.core.media.FetchedMediaMetadata
import com.example.core.media.MediaQualityOption
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.QualityTrackType
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.media.VideoProcessingEngine
import com.example.core.media.YoutubeDlMediaEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class FetchUiState(
    val mode: FetchWorkspaceMode = FetchWorkspaceMode.SINGLE,
    val urlInput: String = "",
    val isFetchingInfo: Boolean = false,
    val metadata: FetchedMediaMetadata? = null,
    val selectedQuality: MediaQualityOption? = null,
    val showQualitySheet: Boolean = false,
    val qualityFilter: QualityTrackType? = null, // null = All
    val isDownloading: Boolean = false,
    val downloadProgress: Float = -1f, // -1f = indeterminate, 0f..1f = live progress
    val downloadEtaSeconds: Long = -1L,
    val downloadStatusLine: String = "",
    val savedResult: SavedMediaResult? = null,
    val errorMessage: String? = null,
    val showAuthPromptOnError: Boolean = false,
    // Queue-based batch downloader state
    val batchUrlInput: String = "",
    val batchPreset: BatchQualityPreset = BatchQualityPreset.BEST_MP4,
    val batchItems: List<BatchQueueItem> = emptyList(),
    val isBatchRunning: Boolean = false,
    val batchNotice: String? = null,
    // Optional WebView Cookie Manager state
    val hasSavedCookies: Boolean = false,
    val showCookieDialog: Boolean = false,
    val cookieDialogUrl: String = "https://www.instagram.com/accounts/login/",
    val cookieStatusMessage: String? = null,
    // Optional local file import / audio extraction support
    val localProbed: ProbedMediaInfo? = null,
)

class FetchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        FetchUiState(hasSavedCookies = CookieSessionManager.hasSavedCookies(application)),
    )
    val uiState: StateFlow<FetchUiState> = _uiState.asStateFlow()

    private val localInspector = DirectMediaFetcher()
    private var activeJob: Job? = null
    private var activeProcessId: String? = null
    private var activeBatchJob: Job? = null
    private var activeBatchProcessId: String? = null

    fun selectMode(mode: FetchWorkspaceMode) {
        _uiState.update { it.copy(mode = mode, batchNotice = null) }
    }

    fun onUrlChanged(value: String) {
        _uiState.update { it.copy(urlInput = value, errorMessage = null) }
    }

    fun clearUrl() {
        _uiState.update {
            it.copy(
                urlInput = "",
                metadata = null,
                selectedQuality = null,
                errorMessage = null,
                showAuthPromptOnError = false,
                savedResult = null,
            )
        }
    }

    fun openCookieDialog() {
        val suggested = CookieSessionManager.suggestLoginUrlForMediaUrl(_uiState.value.urlInput)
        _uiState.update {
            it.copy(
                showCookieDialog = true,
                cookieDialogUrl = suggested,
            )
        }
    }

    fun openCookieDialogForUrl(url: String) {
        val suggested = CookieSessionManager.suggestLoginUrlForMediaUrl(url)
        _uiState.update {
            it.copy(
                showCookieDialog = true,
                cookieDialogUrl = suggested,
            )
        }
    }

    fun dismissCookieDialog() {
        _uiState.update {
            it.copy(
                showCookieDialog = false,
                hasSavedCookies = CookieSessionManager.hasSavedCookies(getApplication()),
            )
        }
    }

    fun onCookiesUpdated(saved: Boolean, statusMessage: String) {
        _uiState.update {
            it.copy(
                hasSavedCookies = saved,
                cookieStatusMessage = statusMessage,
                showAuthPromptOnError = if (saved) false else it.showAuthPromptOnError,
            )
        }
    }

    fun openQualitySheet() {
        if (_uiState.value.metadata != null) {
            _uiState.update { it.copy(showQualitySheet = true) }
        }
    }

    fun dismissQualitySheet() {
        _uiState.update { it.copy(showQualitySheet = false) }
    }

    fun selectQualityFilter(filter: QualityTrackType?) {
        _uiState.update { it.copy(qualityFilter = filter) }
    }

    fun selectQuality(option: MediaQualityOption) {
        _uiState.update {
            it.copy(
                selectedQuality = option,
                showQualitySheet = false,
            )
        }
    }

    fun fetchMetadata() {
        val rawUrl = _uiState.value.urlInput.trim()
        if (rawUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Paste a media URL to fetch metadata.") }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingInfo = true,
                    errorMessage = null,
                    showAuthPromptOnError = false,
                    savedResult = null,
                    localProbed = null,
                    metadata = null,
                    selectedQuality = null,
                )
            }
            try {
                val info = YoutubeDlMediaEngine.fetchMediaMetadata(getApplication(), rawUrl)
                _uiState.update {
                    it.copy(
                        isFetchingInfo = false,
                        metadata = info,
                        selectedQuality = info.qualities.firstOrNull(),
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val authNeeded = CookieSessionManager.isAuthOrRateLimitError(t.message)
                _uiState.update {
                    it.copy(
                        isFetchingInfo = false,
                        errorMessage = formatFriendlyError(t),
                        showAuthPromptOnError = authNeeded,
                    )
                }
            }
        }
    }

    fun startDownload() {
        val current = _uiState.value
        val metadata = current.metadata
        val quality = current.selectedQuality
        val url = metadata?.sourceUrl ?: current.urlInput.trim()

        if (url.isBlank() || quality == null) {
            _uiState.update { it.copy(errorMessage = "Fetch media info and choose a quality first.") }
            return
        }

        activeJob?.cancel()
        val processId = "clipper_dl_${UUID.randomUUID()}"
        activeProcessId = processId

        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = -1f,
                    downloadEtaSeconds = -1L,
                    downloadStatusLine = "Initializing yt-dlp request...",
                    errorMessage = null,
                    showAuthPromptOnError = false,
                    savedResult = null,
                )
            }
            try {
                val saved = YoutubeDlMediaEngine.downloadMediaToDownloads(
                    context = getApplication(),
                    originalUrl = url,
                    quality = quality,
                    titleHint = metadata?.title ?: "media_download",
                    processId = processId,
                    onProgress = { update ->
                        _uiState.update {
                            it.copy(
                                downloadProgress = update.progressFraction,
                                downloadEtaSeconds = update.etaSeconds,
                                downloadStatusLine = update.statusLine,
                            )
                        }
                    },
                )
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        downloadEtaSeconds = 0L,
                        downloadStatusLine = "Saved to Downloads",
                        savedResult = saved,
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = -1f,
                        downloadStatusLine = "Download cancelled",
                    )
                }
                throw ce
            } catch (t: Throwable) {
                val authNeeded = CookieSessionManager.isAuthOrRateLimitError(t.message)
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = -1f,
                        errorMessage = formatFriendlyError(t),
                        showAuthPromptOnError = authNeeded,
                    )
                }
            } finally {
                if (activeProcessId == processId) activeProcessId = null
            }
        }
    }

    fun cancelActiveTask() {
        activeProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        activeProcessId = null
        activeJob?.cancel()
        activeJob = null
        _uiState.update {
            it.copy(
                isFetchingInfo = false,
                isDownloading = false,
                downloadProgress = -1f,
                downloadStatusLine = "",
            )
        }
    }

    fun importLocalFile(uri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingInfo = true,
                    errorMessage = null,
                    savedResult = null,
                )
            }
            try {
                val probed = localInspector.inspectLocalUri(getApplication(), uri)
                _uiState.update {
                    it.copy(
                        isFetchingInfo = false,
                        localProbed = probed,
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isFetchingInfo = false,
                        errorMessage = t.message ?: "Could not inspect selected local file.",
                    )
                }
            }
        }
    }

    fun extractAudioFromLocalFile() {
        val local = _uiState.value.localProbed ?: return
        val localUri = local.localUri ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0.05f,
                    downloadStatusLine = "Extracting hardware M4A audio...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            val tempOut = File(getApplication<Application>().cacheDir, "audio_${System.currentTimeMillis()}.m4a")
            try {
                VideoProcessingEngine.extractAudioToM4a(
                    context = getApplication(),
                    inputUri = localUri,
                    outputFile = tempOut,
                    onProgress = { p ->
                        _uiState.update { it.copy(downloadProgress = p) }
                    },
                )
                val baseName = local.fileName.substringBeforeLast('.').ifBlank { "audio_track" }
                val saved = MediaStoreSaver.saveFileToGallery(
                    context = getApplication(),
                    sourceFile = tempOut,
                    displayName = "${baseName}_audio.m4a",
                    mimeType = "audio/mp4",
                    category = SaveCategory.DOCUMENT,
                )
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        savedResult = saved,
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = t.message ?: "Audio extraction failed.",
                    )
                }
            } finally {
                tempOut.delete()
            }
        }
    }

    private fun formatFriendlyError(t: Throwable): String {
        val raw = t.message?.trim().orEmpty()
        return when {
            raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Network error: unable to reach the server. Check your internet connection."
            raw.contains("Unsupported URL", ignoreCase = true) ->
                "This URL is not recognized as a downloadable media page."
            raw.isNotBlank() -> raw.lines().lastOrNull { it.isNotBlank() }?.take(220) ?: raw.take(220)
            else -> "Could not process media URL."
        }
    }

    // --- Sequential Batch Queue Management ---

    fun onBatchUrlInputChanged(value: String) {
        _uiState.update { it.copy(batchUrlInput = value, batchNotice = null) }
    }

    fun selectBatchPreset(preset: BatchQualityPreset) {
        _uiState.update { it.copy(batchPreset = preset) }
    }

    fun addUrlsToBatchQueue() {
        val current = _uiState.value
        val parsedUrls = BatchQueueHelper.parseMultipleUrls(current.batchUrlInput)
        if (parsedUrls.isEmpty()) {
            _uiState.update { it.copy(batchNotice = "Enter at least one valid media URL (one per line).") }
            return
        }

        val activeOrQueuedUrls = current.batchItems
            .filter {
                it.status == BatchItemStatus.QUEUED ||
                    it.status == BatchItemStatus.FETCHING_INFO ||
                    it.status == BatchItemStatus.DOWNLOADING
            }
            .map { it.sanitizedUrl }
            .toSet()

        val newItems = parsedUrls
            .filterNot { it in activeOrQueuedUrls }
            .map { sanitized ->
                BatchQueueItem(
                    rawUrl = sanitized,
                    sanitizedUrl = sanitized,
                    preset = current.batchPreset,
                )
            }

        if (newItems.isEmpty()) {
            _uiState.update {
                it.copy(
                    batchUrlInput = "",
                    batchNotice = "Those URLs are already waiting in the queue.",
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                batchUrlInput = "",
                batchItems = it.batchItems + newItems,
                batchNotice = null,
            )
        }
    }

    fun removeBatchItem(itemId: String) {
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.filterNot { item ->
                    item.id == itemId &&
                        item.status != BatchItemStatus.FETCHING_INFO &&
                        item.status != BatchItemStatus.DOWNLOADING
                },
            )
        }
    }

    fun retryFailedBatchItems() {
        if (_uiState.value.isBatchRunning) return
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { item ->
                    if (item.status == BatchItemStatus.FAILED || item.status == BatchItemStatus.CANCELLED) {
                        item.copy(
                            status = BatchItemStatus.QUEUED,
                            progress = 0f,
                            etaSeconds = -1L,
                            statusDetail = "Waiting in queue",
                            errorMessage = null,
                            requiresAuth = false,
                        )
                    } else {
                        item
                    }
                },
                batchNotice = null,
            )
        }
    }

    fun clearCompletedBatchItems() {
        if (_uiState.value.isBatchRunning) return
        _uiState.update { state ->
            state.copy(batchItems = state.batchItems.filterNot { it.status == BatchItemStatus.COMPLETED })
        }
    }

    fun clearAllBatchItems() {
        if (_uiState.value.isBatchRunning) return
        _uiState.update { it.copy(batchItems = emptyList(), batchNotice = null) }
    }

    fun startBatchQueue() {
        if (_uiState.value.isBatchRunning) return

        // Promote any previously cancelled items back to QUEUED so pressing Start resumes them
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { item ->
                    if (item.status == BatchItemStatus.CANCELLED) {
                        item.copy(
                            status = BatchItemStatus.QUEUED,
                            progress = 0f,
                            etaSeconds = -1L,
                            statusDetail = "Waiting in queue",
                            errorMessage = null,
                        )
                    } else {
                        item
                    }
                },
                batchNotice = null,
            )
        }

        if (_uiState.value.batchItems.none { it.status == BatchItemStatus.QUEUED }) return

        activeBatchJob?.cancel()
        activeBatchJob = viewModelScope.launch {
            _uiState.update { it.copy(isBatchRunning = true) }
            try {
                while (isActive) {
                    val nextItem = _uiState.value.batchItems.firstOrNull {
                        it.status == BatchItemStatus.QUEUED
                    } ?: break

                    updateBatchItem(nextItem.id) {
                        it.copy(
                            status = BatchItemStatus.FETCHING_INFO,
                            progress = -1f,
                            etaSeconds = -1L,
                            statusDetail = "Fetching metadata...",
                            errorMessage = null,
                            requiresAuth = false,
                        )
                    }

                    // Resolve title via getInfo first (falls back cleanly if direct stream URL)
                    var resolvedTitle: String? = nextItem.title
                    var authErrorFromInfo: Throwable? = null
                    try {
                        val info = YoutubeDlMediaEngine.fetchMediaMetadata(
                            context = getApplication(),
                            rawUrl = nextItem.sanitizedUrl,
                        )
                        resolvedTitle = info.title
                    } catch (ce: CancellationException) {
                        updateBatchItem(nextItem.id) {
                            it.copy(status = BatchItemStatus.CANCELLED, statusDetail = "Stopped by user")
                        }
                        throw ce
                    } catch (t: Throwable) {
                        if (CookieSessionManager.isAuthOrRateLimitError(t.message)) {
                            authErrorFromInfo = t
                        }
                    }

                    if (authErrorFromInfo != null) {
                        updateBatchItem(nextItem.id) {
                            it.copy(
                                status = BatchItemStatus.FAILED,
                                progress = 0f,
                                etaSeconds = -1L,
                                statusDetail = "Authentication required",
                                errorMessage = formatFriendlyError(authErrorFromInfo),
                                requiresAuth = true,
                            )
                        }
                        continue
                    }

                    val processId = "clipper_batch_${UUID.randomUUID()}"
                    activeBatchProcessId = processId

                    updateBatchItem(nextItem.id) {
                        it.copy(
                            title = resolvedTitle ?: it.title,
                            status = BatchItemStatus.DOWNLOADING,
                            progress = 0f,
                            etaSeconds = -1L,
                            statusDetail = "Starting download...",
                        )
                    }

                    try {
                        val quality = BatchQueueHelper.presetToQualityOption(nextItem.preset)
                        val saved = YoutubeDlMediaEngine.downloadMediaToDownloads(
                            context = getApplication(),
                            originalUrl = nextItem.sanitizedUrl,
                            quality = quality,
                            titleHint = resolvedTitle ?: "clipper_batch",
                            processId = processId,
                            onProgress = { update ->
                                updateBatchItem(nextItem.id) { currentItem ->
                                    currentItem.copy(
                                        progress = update.progressFraction,
                                        etaSeconds = update.etaSeconds,
                                        statusDetail = update.statusLine,
                                    )
                                }
                            },
                        )

                        updateBatchItem(nextItem.id) {
                            it.copy(
                                status = BatchItemStatus.COMPLETED,
                                progress = 1f,
                                etaSeconds = 0L,
                                statusDetail = "Saved to Downloads",
                                savedResult = saved,
                                errorMessage = null,
                                requiresAuth = false,
                            )
                        }
                    } catch (ce: CancellationException) {
                        updateBatchItem(nextItem.id) {
                            it.copy(
                                status = BatchItemStatus.CANCELLED,
                                statusDetail = "Stopped by user",
                            )
                        }
                        throw ce
                    } catch (t: Throwable) {
                        val authNeeded = CookieSessionManager.isAuthOrRateLimitError(t.message)
                        updateBatchItem(nextItem.id) {
                            it.copy(
                                status = BatchItemStatus.FAILED,
                                progress = 0f,
                                etaSeconds = -1L,
                                statusDetail = "Download failed",
                                errorMessage = formatFriendlyError(t),
                                requiresAuth = authNeeded,
                            )
                        }
                    } finally {
                        if (activeBatchProcessId == processId) activeBatchProcessId = null
                    }
                }
            } finally {
                activeBatchProcessId = null
                _uiState.update { it.copy(isBatchRunning = false) }
            }
        }
    }

    fun stopBatchQueue() {
        activeBatchProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        activeBatchProcessId = null
        activeBatchJob?.cancel()
        activeBatchJob = null
        _uiState.update { state ->
            state.copy(
                isBatchRunning = false,
                batchItems = state.batchItems.map { item ->
                    if (item.status == BatchItemStatus.FETCHING_INFO || item.status == BatchItemStatus.DOWNLOADING) {
                        item.copy(
                            status = BatchItemStatus.CANCELLED,
                            statusDetail = "Stopped by user",
                        )
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun updateBatchItem(itemId: String, transform: (BatchQueueItem) -> BatchQueueItem) {
        _uiState.update { state ->
            state.copy(
                batchItems = state.batchItems.map { item ->
                    if (item.id == itemId) transform(item) else item
                },
            )
        }
    }

    override fun onCleared() {
        activeProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        activeBatchProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        super.onCleared()
    }
}
