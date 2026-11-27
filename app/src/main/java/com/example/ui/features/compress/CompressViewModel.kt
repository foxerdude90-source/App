package com.example.ui.features.compress

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.ImageCompressionConfig
import com.example.core.media.ImageCompressionResult
import com.example.core.media.ImageCompressorEngine
import com.example.core.media.ImageOutputFormat
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

enum class CompressMode(val label: String) {
    VIDEO("Video"),
    IMAGE("Image"),
}

data class VideoCompressResult(
    val outputFile: File,
    val originalBytes: Long,
    val compressedBytes: Long,
    val savedMedia: SavedMediaResult,
)

data class CompressUiState(
    val mode: CompressMode = CompressMode.VIDEO,
    val selectedVideo: ProbedMediaInfo? = null,
    val videoConfig: VideoTranscodeConfig = VideoTranscodeConfig(),
    val videoResult: VideoCompressResult? = null,
    val selectedImage: ProbedMediaInfo? = null,
    val imageConfig: ImageCompressionConfig = ImageCompressionConfig(),
    val imageResult: ImageCompressionResult? = null,
    val imageSavedResult: SavedMediaResult? = null,
    val isWorking: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val errorMessage: String? = null,
)

class CompressViewModel(application: Application) : AndroidViewModel(application) {

    private val inspector = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setMode(mode: CompressMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun selectVideoUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = inspector.inspectLocalUri(getApplication(), uri)
                _uiState.update {
                    it.copy(
                        mode = CompressMode.VIDEO,
                        selectedVideo = info,
                        videoResult = null,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unable to read selected video.") }
            }
        }
    }

    fun selectImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = inspector.inspectLocalUri(getApplication(), uri)
                _uiState.update {
                    it.copy(
                        mode = CompressMode.IMAGE,
                        selectedImage = info,
                        imageResult = null,
                        imageSavedResult = null,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unable to read selected image.") }
            }
        }
    }

    fun updateVideoResolution(preset: VideoResolutionPreset) {
        _uiState.update { it.copy(videoConfig = it.videoConfig.copy(resolution = preset)) }
    }

    fun updateVideoFps(preset: VideoFpsPreset) {
        _uiState.update { it.copy(videoConfig = it.videoConfig.copy(frameRate = preset)) }
    }

    fun updateVideoBitrate(mbps: Float) {
        _uiState.update { it.copy(videoConfig = it.videoConfig.copy(bitrateMbps = mbps)) }
    }

    fun updateRemoveAudio(remove: Boolean) {
        _uiState.update { it.copy(videoConfig = it.videoConfig.copy(removeAudio = remove)) }
    }

    fun updateImageFormat(format: ImageOutputFormat) {
        _uiState.update { it.copy(imageConfig = it.imageConfig.copy(format = format)) }
    }

    fun updateImageQuality(quality: Int) {
        _uiState.update { it.copy(imageConfig = it.imageConfig.copy(quality = quality.coerceIn(10, 100))) }
    }

    fun updateImageMaxDim(maxPx: Int) {
        _uiState.update { it.copy(imageConfig = it.imageConfig.copy(maxDimensionPx = maxPx)) }
    }

    fun compressVideo() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val uri = video.localUri ?: return
        val context = getApplication<Application>()

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWorking = true,
                    progress = 0f,
                    progressLabel = "Encoding video with hardware codec...",
                    videoResult = null,
                    errorMessage = null,
                )
            }
            try {
                val outFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")
                VideoProcessingEngine.transcodeVideo(
                    context = context,
                    inputUri = uri,
                    outputFile = outFile,
                    config = state.videoConfig,
                    onProgress = { p ->
                        _uiState.update { it.copy(progress = p, progressLabel = "Compressing video...") }
                    },
                )
                val baseName = video.fileName.substringBeforeLast('.').ifBlank { "video" }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = outFile,
                        displayName = "${baseName}_compressed_${state.videoConfig.resolution.label}.mp4",
                        mimeType = "video/mp4",
                        category = SaveCategory.VIDEO,
                    )
                }
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        progress = 1f,
                        videoResult = VideoCompressResult(
                            outputFile = outFile,
                            originalBytes = video.sizeBytes,
                            compressedBytes = outFile.length(),
                            savedMedia = saved,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = e.message ?: "Video compression failed.",
                    )
                }
            }
        }
    }

    fun compressImage(saveImmediately: Boolean = false) {
        val state = _uiState.value
        val image = state.selectedImage ?: return
        val uri = image.localUri ?: return
        val context = getApplication<Application>()

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWorking = true,
                    progress = 0.4f,
                    progressLabel = "Optimizing ${state.imageConfig.format.label} image...",
                    errorMessage = null,
                    imageSavedResult = null,
                )
            }
            try {
                val result = ImageCompressorEngine.compressImage(
                    context = context,
                    inputUri = uri,
                    originalBytes = image.sizeBytes,
                    config = state.imageConfig,
                )
                val saved = if (saveImmediately) {
                    saveCompressedImageInternal(image.fileName, result)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        progress = 1f,
                        imageResult = result,
                        imageSavedResult = saved,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = e.message ?: "Image compression failed.",
                    )
                }
            }
        }
    }

    fun saveCompressedImage() {
        val image = _uiState.value.selectedImage ?: return
        val result = _uiState.value.imageResult ?: return
        viewModelScope.launch {
            try {
                val saved = saveCompressedImageInternal(image.fileName, result)
                _uiState.update { it.copy(imageSavedResult = saved, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Unable to save image.") }
            }
        }
    }

    private suspend fun saveCompressedImageInternal(
        originalName: String,
        result: ImageCompressionResult,
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val base = originalName.substringBeforeLast('.').ifBlank { "image" }
        MediaStoreSaver.saveFileToGallery(
            context = getApplication(),
            sourceFile = result.outputFile,
            displayName = "${base}_q${_uiState.value.imageConfig.quality}.${result.format.extension}",
            mimeType = result.format.mimeType,
            category = SaveCategory.IMAGE,
        )
    }
}
