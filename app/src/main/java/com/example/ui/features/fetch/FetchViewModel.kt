package com.example.ui.features.fetch

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DetectedMediaKind
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.media.VideoFpsPreset
import com.example.core.media.VideoProcessingEngine
import com.example.core.media.VideoResolutionPreset
import com.example.core.media.VideoTranscodeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FetchUiState(
    val urlInput: String = "",
    val isProbing: Boolean = false,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val probedMedia: ProbedMediaInfo? = null,
    val selectedResolution: VideoResolutionPreset = VideoResolutionPreset.ORIGINAL,
    val selectedFps: VideoFpsPreset = VideoFpsPreset.ORIGINAL,
    val targetBitrateMbps: Float = 4.5f,
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
)

class FetchViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(FetchUiState())
    val uiState: StateFlow<FetchUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun onUrlChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, errorMessage = null) }
    }

    fun onResolutionSelected(preset: VideoResolutionPreset) {
        _uiState.update { it.copy(selectedResolution = preset) }
    }

    fun onFpsSelected(preset: VideoFpsPreset) {
        _uiState.update { it.copy(selectedFps = preset) }
    }

    fun onBitrateChanged(mbps: Float) {
        _uiState.update { it.copy(targetBitrateMbps = mbps) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSavedResult() {
        _uiState.update { it.copy(savedResult = null) }
    }

    fun fetchUrl() {
        val url = _uiState.value.urlInput.trim()
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProbing = true,
                    errorMessage = null,
                    savedResult = null,
                    probedMedia = null,
                    progress = 0f,
                )
            }
            try {
                val headInfo = fetcher.probeRemoteUrl(url)
                _uiState.update {
                    it.copy(
                        probedMedia = headInfo,
                        isProbing = false,
                        isProcessing = true,
                        progressLabel = "Caching preview stream...",
                    )
                }
                val localInspected = fetcher.downloadRemoteToCache(
                    context = getApplication(),
                    info = headInfo,
                    onProgress = { read, total ->
                        val frac = if (total > 0L) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.35f
                        _uiState.update { state ->
                            state.copy(
                                progress = frac,
                                progressLabel = if (total > 0L) {
                                    "Downloading preview ${MediaStoreSaver.formatByteSize(read)} / ${MediaStoreSaver.formatByteSize(total)}"
                                } else {
                                    "Downloading preview ${MediaStoreSaver.formatByteSize(read)}"
                                },
                            )
                        }
                    },
                )
                _uiState.update {
                    it.copy(
                        probedMedia = localInspected,
                        isProcessing = false,
                        progress = 1f,
                        progressLabel = "",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProbing = false,
                        isProcessing = false,
                        errorMessage = e.message ?: "Unable to fetch media from link.",
                    )
                }
            }
        }
    }

    fun importLocalUri(uri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, errorMessage = null, savedResult = null) }
            try {
                val inspected = fetcher.inspectLocalUri(getApplication(), uri)
                _uiState.update { it.copy(probedMedia = inspected, isProbing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProbing = false,
                        errorMessage = e.message ?: "Failed to inspect selected file.",
                    )
                }
            }
        }
    }

    fun exportMedia(extractAudioOnly: Boolean) {
        val state = _uiState.value
        val media = state.probedMedia ?: return
        val sourceUri = media.localUri ?: return
        val context = getApplication<Application>()

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progress = 0f,
                    progressLabel = if (extractAudioOnly) "Extracting M4A audio track..." else "Preparing export...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            try {
                val baseName = media.fileName.substringBeforeLast('.').ifBlank { "clipper_export" }
                val saved = when {
                    extractAudioOnly && media.kind == DetectedMediaKind.VIDEO -> {
                        val outFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                        VideoProcessingEngine.extractAudioToM4a(
                            context = context,
                            inputUri = sourceUri,
                            outputFile = outFile,
                            onProgress = { p ->
                                _uiState.update { it.copy(progress = p, progressLabel = "Extracting M4A audio...") }
                            },
                        )
                        val res = withContext(Dispatchers.IO) {
                            MediaStoreSaver.saveFileToGallery(
                                context = context,
                                sourceFile = outFile,
                                displayName = "${baseName}_audio.m4a",
                                mimeType = "audio/mp4",
                                category = SaveCategory.AUDIO,
                            )
                        }
                        outFile.delete()
                        res
                    }

                    media.kind == DetectedMediaKind.VIDEO &&
                        (state.selectedResolution != VideoResolutionPreset.ORIGINAL ||
                            state.selectedFps != VideoFpsPreset.ORIGINAL) -> {
                        val outFile = File(context.cacheDir, "transcode_${System.currentTimeMillis()}.mp4")
                        val cfg = VideoTranscodeConfig(
                            resolution = state.selectedResolution,
                            frameRate = state.selectedFps,
                            bitrateMbps = state.targetBitrateMbps,
                            removeAudio = false,
                        )
                        VideoProcessingEngine.transcodeVideo(
                            context = context,
                            inputUri = sourceUri,
                            outputFile = outFile,
                            config = cfg,
                            onProgress = { p ->
                                _uiState.update {
                                    it.copy(
                                        progress = p,
                                        progressLabel = "Transcoding ${cfg.resolution.label} @ ${cfg.frameRate.label}...",
                                    )
                                }
                            },
                        )
                        val suffix = "${cfg.resolution.label}_${cfg.frameRate.label}".replace(" ", "")
                        val res = withContext(Dispatchers.IO) {
                            MediaStoreSaver.saveFileToGallery(
                                context = context,
                                sourceFile = outFile,
                                displayName = "${baseName}_${suffix}.mp4",
                                mimeType = "video/mp4",
                                category = SaveCategory.VIDEO,
                            )
                        }
                        outFile.delete()
                        res
                    }

                    else -> {
                        // Direct lossless save of cached/imported file
                        val fileToSave = media.localFile ?: withContext(Dispatchers.IO) {
                            val tmp = File(context.cacheDir, "copy_${System.currentTimeMillis()}_${media.fileName}")
                            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                                tmp.outputStream().use { out -> input.copyTo(out) }
                            } ?: error("Cannot read source media stream.")
                            tmp
                        }
                        val category = when (media.kind) {
                            DetectedMediaKind.VIDEO -> SaveCategory.VIDEO
                            DetectedMediaKind.IMAGE -> SaveCategory.IMAGE
                            DetectedMediaKind.AUDIO -> SaveCategory.AUDIO
                            DetectedMediaKind.OTHER -> SaveCategory.DOCUMENT
                        }
                        val res = withContext(Dispatchers.IO) {
                            MediaStoreSaver.saveFileToGallery(
                                context = context,
                                sourceFile = fileToSave,
                                displayName = media.fileName,
                                mimeType = media.mimeType,
                                category = category,
                            )
                        }
                        if (media.localFile == null) fileToSave.delete()
                        res
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progress = 1f,
                        progressLabel = "",
                        savedResult = saved,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = e.message ?: "Export failed.",
                    )
                }
            }
        }
    }
}
