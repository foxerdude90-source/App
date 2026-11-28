package com.example.ui.features.upscale

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.media.VideoFpsPreset
import com.example.core.upscale.UpscaleCodec
import com.example.core.upscale.UpscaleConfig
import com.example.core.upscale.UpscaleFramePreviewResult
import com.example.core.upscale.UpscaleStylePreset
import com.example.core.upscale.UpscaleTargetPreset
import com.example.core.upscale.VideoUpscalerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UpscaleUiState(
    val sourceVideo: ProbedMediaInfo? = null,
    val config: UpscaleConfig = UpscaleConfig(),
    val activeStylePreset: UpscaleStylePreset? = UpscaleStylePreset.CRISP_RESTORE,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val recommendedBitrateMbps: Float = 0f,
    val previewTimeMs: Long = 0L,
    val previewResult: UpscaleFramePreviewResult? = null,
    val isRefreshingPreview: Boolean = false,
    val splitFraction: Float = 0.5f,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportLabel: String = "",
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
)

class UpscaleViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(UpscaleUiState())
    val uiState: StateFlow<UpscaleUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private var exportJob: Job? = null

    fun loadVideo(uri: Uri) {
        exportJob?.cancel()
        previewJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshingPreview = true,
                    errorMessage = null,
                    savedResult = null,
                    previewResult = null,
                )
            }
            try {
                val info = fetcher.inspectLocalUri(getApplication(), uri)
                val cfg = _uiState.value.config
                val (tw, th) = VideoUpscalerEngine.computeTargetDimensions(info.width, info.height, cfg.targetPreset)
                val bps = VideoUpscalerEngine.computeRecommendedBitrateBps(tw, th, info.frameRate, cfg.codec, cfg.bitrateQualityFactor)
                val midMs = (info.durationMs / 4L).coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        sourceVideo = info,
                        targetWidth = tw,
                        targetHeight = th,
                        recommendedBitrateMbps = bps / 1_000_000f,
                        previewTimeMs = midMs,
                    )
                }
                schedulePreviewRefresh(debounceMs = 0L)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshingPreview = false,
                        errorMessage = e.message ?: "Failed to inspect selected video.",
                    )
                }
            }
        }
    }

    fun selectTargetPreset(preset: UpscaleTargetPreset) {
        updateConfig { it.copy(targetPreset = preset) }
    }

    fun applyStylePreset(preset: UpscaleStylePreset) {
        _uiState.update { it.copy(activeStylePreset = preset) }
        updateConfig {
            it.copy(
                sharpness = preset.sharpness,
                denoise = preset.denoise,
                clarity = preset.clarity,
                vibrance = preset.vibrance,
            )
        }
    }

    fun setSharpness(value: Float) {
        _uiState.update { it.copy(activeStylePreset = null) }
        updateConfig { it.copy(sharpness = value.coerceIn(0f, 1f)) }
    }

    fun setDenoise(value: Float) {
        _uiState.update { it.copy(activeStylePreset = null) }
        updateConfig { it.copy(denoise = value.coerceIn(0f, 1f)) }
    }

    fun setClarity(value: Float) {
        _uiState.update { it.copy(activeStylePreset = null) }
        updateConfig { it.copy(clarity = value.coerceIn(0f, 1f)) }
    }

    fun setVibrance(value: Float) {
        _uiState.update { it.copy(activeStylePreset = null) }
        updateConfig { it.copy(vibrance = value.coerceIn(0f, 1f)) }
    }

    fun selectCodec(codec: UpscaleCodec) {
        updateConfig(refreshPreview = false) { it.copy(codec = codec) }
    }

    fun selectFps(fps: VideoFpsPreset) {
        updateConfig(refreshPreview = false) { it.copy(targetFps = fps) }
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun setPreviewTimeMs(timeMs: Long) {
        val dur = _uiState.value.sourceVideo?.durationMs ?: 0L
        val clamped = timeMs.coerceIn(0L, dur.coerceAtLeast(0L))
        _uiState.update { it.copy(previewTimeMs = clamped) }
        schedulePreviewRefresh(debounceMs = 160L)
    }

    fun startGpuUpscaleExport() {
        val state = _uiState.value
        val video = state.sourceVideo ?: return
        val uri = video.localUri ?: return
        val context = getApplication<Application>()

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    exportLabel = "GPU Upscaling to ${state.targetWidth}×${state.targetHeight}...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            val outFile = File(context.cacheDir, "upscaled_${System.currentTimeMillis()}.mp4")
            try {
                VideoUpscalerEngine.upscaleVideo(
                    context = context,
                    inputUri = uri,
                    outputFile = outFile,
                    sourceWidth = video.width,
                    sourceHeight = video.height,
                    sourceFps = video.frameRate,
                    config = state.config,
                    onProgress = { p ->
                        _uiState.update { s ->
                            s.copy(
                                exportProgress = p,
                                exportLabel = "GPU Super-Res ${state.targetWidth}×${state.targetHeight} (${(p * 100).toInt()}%)",
                            )
                        }
                    },
                )
                val baseName = video.fileName.substringBeforeLast('.').ifBlank { "clipper_video" }
                val tag = state.config.targetPreset.label.replace(" ", "_").replace("×", "x")
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = outFile,
                        displayName = "${baseName}_upscaled_${tag}.mp4",
                        mimeType = "video/mp4",
                        category = SaveCategory.VIDEO,
                    )
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        exportLabel = "",
                        savedResult = saved,
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportLabel = "") }
                throw ce
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.message ?: "GPU upscale failed on this video.",
                    )
                }
            } finally {
                outFile.delete()
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportLabel = "") }
    }

    private fun updateConfig(
        refreshPreview: Boolean = true,
        transform: (UpscaleConfig) -> UpscaleConfig,
    ) {
        _uiState.update { state ->
            val nextCfg = transform(state.config)
            val src = state.sourceVideo
            if (src != null) {
                val (tw, th) = VideoUpscalerEngine.computeTargetDimensions(src.width, src.height, nextCfg.targetPreset)
                val fps = nextCfg.targetFps.fps ?: src.frameRate
                val bps = VideoUpscalerEngine.computeRecommendedBitrateBps(tw, th, fps, nextCfg.codec, nextCfg.bitrateQualityFactor)
                state.copy(
                    config = nextCfg,
                    targetWidth = tw,
                    targetHeight = th,
                    recommendedBitrateMbps = bps / 1_000_000f,
                )
            } else {
                state.copy(config = nextCfg)
            }
        }
        if (refreshPreview) {
            schedulePreviewRefresh(debounceMs = 130L)
        }
    }

    private fun schedulePreviewRefresh(debounceMs: Long) {
        val state = _uiState.value
        val uri = state.sourceVideo?.localUri ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            _uiState.update { it.copy(isRefreshingPreview = true) }
            val result = VideoUpscalerEngine.generateRealFramePreview(
                context = getApplication(),
                videoUri = uri,
                positionMs = _uiState.value.previewTimeMs,
                config = _uiState.value.config,
            )
            _uiState.update {
                it.copy(
                    previewResult = result ?: it.previewResult,
                    isRefreshingPreview = false,
                )
            }
        }
    }
}
