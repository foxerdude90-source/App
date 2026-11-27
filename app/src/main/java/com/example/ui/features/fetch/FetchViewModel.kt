package com.example.ui.features.fetch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DirectMediaFetcher
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class FetchUiState(
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
    // Optional local file import / audio extraction support
    val localProbed: ProbedMediaInfo? = null,
)

class FetchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FetchUiState())
    val uiState: StateFlow<FetchUiState> = _uiState.asStateFlow()

    private val localInspector = DirectMediaFetcher()
    private var activeJob: Job? = null
    private var activeProcessId: String? = null

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
                savedResult = null,
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
                _uiState.update {
                    it.copy(
                        isFetchingInfo = false,
                        errorMessage = formatFriendlyError(t),
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
                    savedResult = null,
                )
            }
            try {
                val saved = YoutubeDlMediaEngine.downloadMediaToDownloads(
                    context = getApplication(),
                    url = url,
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
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = -1f,
                        errorMessage = formatFriendlyError(t),
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

    override fun onCleared() {
        activeProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        super.onCleared()
    }
}
