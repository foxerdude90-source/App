package com.example.ui.features.image

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.BackdropColorPreset
import com.example.core.image.CollageConfig
import com.example.core.image.CollageLayout
import com.example.core.image.ImageRatioConfig
import com.example.core.image.ImageRatioPreset
import com.example.core.image.ImageStudioEngine
import com.example.core.image.PaletteSwatch
import com.example.core.image.PhotoExifReport
import com.example.core.image.PhotoFilterConfig
import com.example.core.image.PhotoFilterLook
import com.example.core.image.RatioExportFormat
import com.example.core.image.RatioFitMode
import com.example.core.image.RatioOutputResolution
import com.example.core.media.MediaStoreSaver
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
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

enum class ImageStudioMode(val label: String) {
    RATIO("Ratio Studio"),
    COLLAGE("Collage Grid"),
    FILTERS("Film Filters"),
    PALETTE_EXIF("Palette & EXIF"),
}

data class ImageStudioUiState(
    val activeMode: ImageStudioMode = ImageStudioMode.RATIO,
    // Shared single photo (Ratio / Filters / Palette & EXIF)
    val photoUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    // 1. Image Ratio Studio
    val ratioConfig: ImageRatioConfig = ImageRatioConfig(),
    val customWInput: String = "5",
    val customHInput: String = "7",
    val ratioPreview: Bitmap? = null,
    // 2. Collage Maker
    val collageBitmaps: List<Bitmap> = emptyList(),
    val collageConfig: CollageConfig = CollageConfig(),
    val collagePreview: Bitmap? = null,
    // 3. Film Photo Filters
    val filterConfig: PhotoFilterConfig = PhotoFilterConfig(),
    val filterPreview: Bitmap? = null,
    // 4. Palette & EXIF Scrubber
    val paletteSwatches: List<PaletteSwatch> = emptyList(),
    val exifReport: PhotoExifReport? = null,
    // Shared Export State
    val isWorking: Boolean = false,
    val statusLabel: String = "",
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
) {
    val predictedRatioDimensions: Pair<Int, Int>
        get() = ImageStudioEngine.computeRatioOutputDimensions(
            srcWidth = sourceWidth.coerceAtLeast(1080),
            srcHeight = sourceHeight.coerceAtLeast(1080),
            ratioW = ratioConfig.effectiveRatioW,
            ratioH = ratioConfig.effectiveRatioH,
            resolution = ratioConfig.resolution,
        )
}

class ImageStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null
    private var exportJob: Job? = null

    fun selectMode(mode: ImageStudioMode) {
        _uiState.update { it.copy(activeMode = mode, errorMessage = null, savedResult = null) }
        when (mode) {
            ImageStudioMode.RATIO -> refreshRatioPreview(debounceMs = 0L)
            ImageStudioMode.COLLAGE -> refreshCollagePreview()
            ImageStudioMode.FILTERS -> refreshFilterPreview(debounceMs = 0L)
            ImageStudioMode.PALETTE_EXIF -> Unit
        }
    }

    fun loadSinglePhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Loading photo...", errorMessage = null, savedResult = null) }
            try {
                val bmp = ImageStudioEngine.decodeOrientedBitmap(getApplication(), uri, maxEdge = 1600)
                val exif = ImageStudioEngine.inspectPhotoExif(getApplication(), uri)
                val palette = withContext(Dispatchers.Default) {
                    ImageStudioEngine.extractDominantPalette(bmp, swatchCount = 6)
                }
                _uiState.update {
                    it.copy(
                        photoUri = uri,
                        sourceBitmap = bmp,
                        sourceWidth = exif.width.takeIf { w -> w > 0 } ?: bmp.width,
                        sourceHeight = exif.height.takeIf { h -> h > 0 } ?: bmp.height,
                        exifReport = exif,
                        paletteSwatches = palette,
                        isWorking = false,
                        statusLabel = "",
                    )
                }
                refreshRatioPreview(debounceMs = 0L)
                refreshFilterPreview(debounceMs = 0L)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, errorMessage = t.message ?: "Could not load photo.") }
            }
        }
    }

    // --- 1. Image Ratio Controls ---

    fun selectRatioPreset(preset: ImageRatioPreset) {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(preset = preset), savedResult = null) }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun updateCustomRatioInputs(wText: String, hText: String) {
        val wVal = wText.toFloatOrNull()?.coerceIn(0.2f, 50f) ?: _uiState.value.ratioConfig.customRatioW
        val hVal = hText.toFloatOrNull()?.coerceIn(0.2f, 50f) ?: _uiState.value.ratioConfig.customRatioH
        _uiState.update {
            it.copy(
                customWInput = wText.take(6),
                customHInput = hText.take(6),
                ratioConfig = it.ratioConfig.copy(
                    preset = ImageRatioPreset.CUSTOM,
                    customRatioW = wVal,
                    customRatioH = hVal,
                ),
            )
        }
        refreshRatioPreview(debounceMs = 90L)
    }

    fun selectFitMode(mode: RatioFitMode) {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(fitMode = mode)) }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun selectBackdropColor(color: BackdropColorPreset) {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(backdropColor = color)) }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun updateRatioConfig(transform: (ImageRatioConfig) -> ImageRatioConfig) {
        _uiState.update { it.copy(ratioConfig = transform(it.ratioConfig)) }
        refreshRatioPreview(debounceMs = 70L)
    }

    fun rotateRatioImage() {
        _uiState.update {
            it.copy(ratioConfig = it.ratioConfig.copy(rotationDegrees = (it.ratioConfig.rotationDegrees + 90) % 360))
        }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun toggleRatioFlipH() {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(flipHorizontal = !it.ratioConfig.flipHorizontal)) }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun toggleRatioFlipV() {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(flipVertical = !it.ratioConfig.flipVertical)) }
        refreshRatioPreview(debounceMs = 0L)
    }

    fun selectOutputResolution(res: RatioOutputResolution) {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(resolution = res)) }
    }

    fun selectExportFormat(fmt: RatioExportFormat) {
        _uiState.update { it.copy(ratioConfig = it.ratioConfig.copy(format = fmt)) }
    }

    private fun refreshRatioPreview(debounceMs: Long) {
        val src = _uiState.value.sourceBitmap ?: return
        val cfg = _uiState.value.ratioConfig
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val rendered = withContext(Dispatchers.Default) {
                ImageStudioEngine.renderImageRatio(src, cfg, previewMaxEdge = 720)
            }
            _uiState.update { it.copy(ratioPreview = rendered) }
        }
    }

    fun exportRatioImage() {
        val uri = _uiState.value.photoUri ?: return
        val cfg = _uiState.value.ratioConfig
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Rendering ${cfg.preset.label} image...", errorMessage = null, savedResult = null) }
            val temp = File(getApplication<Application>().cacheDir, "ratio_${System.currentTimeMillis()}.${cfg.format.ext}")
            var fullSource: Bitmap? = null
            var rendered: Bitmap? = null
            try {
                val maxDecode = if (cfg.resolution == RatioOutputResolution.UHD_4K) 3840 else 2400
                fullSource = ImageStudioEngine.decodeOrientedBitmap(getApplication(), uri, maxEdge = maxDecode)
                rendered = withContext(Dispatchers.Default) {
                    ImageStudioEngine.renderImageRatio(fullSource, cfg, previewMaxEdge = null)
                }
                withContext(Dispatchers.IO) {
                    ImageStudioEngine.writeBitmapToFile(rendered, temp, cfg.format)
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = getApplication(),
                        sourceFile = temp,
                        displayName = "clipper_ratio_${cfg.preset.tag}_${System.currentTimeMillis()}.${cfg.format.ext}",
                        mimeType = cfg.format.mimeType,
                        category = SaveCategory.IMAGE,
                    )
                }
                _uiState.update { it.copy(isWorking = false, statusLabel = "", savedResult = saved) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "", errorMessage = t.message ?: "Image ratio export failed.") }
            } finally {
                rendered?.let { if (!it.isRecycled) it.recycle() }
                fullSource?.let { if (!it.isRecycled) it.recycle() }
                temp.delete()
            }
        }
    }

    // --- 2. Collage Maker Controls ---

    fun addCollagePhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Loading collage photos...", errorMessage = null) }
            try {
                val loaded = uris.take(6).map { uri ->
                    ImageStudioEngine.decodeOrientedBitmap(getApplication(), uri, maxEdge = 1080)
                }
                val combined = (_uiState.value.collageBitmaps + loaded).take(6)
                val autoLayout = when (combined.size) {
                    2 -> CollageLayout.SIDE_2
                    3 -> CollageLayout.HERO_3
                    4 -> CollageLayout.GRID_4
                    else -> if (combined.size >= 5) CollageLayout.GRID_6 else _uiState.value.collageConfig.layout
                }
                _uiState.update {
                    it.copy(
                        collageBitmaps = combined,
                        collageConfig = it.collageConfig.copy(layout = autoLayout),
                        isWorking = false,
                        statusLabel = "",
                    )
                }
                refreshCollagePreview()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, errorMessage = t.message ?: "Failed to load collage photos.") }
            }
        }
    }

    fun clearCollagePhotos() {
        _uiState.update { it.copy(collageBitmaps = emptyList(), collagePreview = null, savedResult = null) }
    }

    fun shuffleCollageOrder() {
        _uiState.update { state ->
            if (state.collageBitmaps.size < 2) return@update state
            val rotated = state.collageBitmaps.drop(1) + state.collageBitmaps.first()
            state.copy(collageBitmaps = rotated)
        }
        refreshCollagePreview()
    }

    fun updateCollageConfig(transform: (CollageConfig) -> CollageConfig) {
        _uiState.update { it.copy(collageConfig = transform(it.collageConfig)) }
        refreshCollagePreview()
    }

    private fun refreshCollagePreview() {
        val photos = _uiState.value.collageBitmaps
        if (photos.isEmpty()) return
        val cfg = _uiState.value.collageConfig
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                ImageStudioEngine.renderCollage(photos, cfg, previewMaxEdge = 720)
            }
            _uiState.update { it.copy(collagePreview = rendered) }
        }
    }

    fun exportCollage() {
        val photos = _uiState.value.collageBitmaps
        if (photos.size < 2) {
            _uiState.update { it.copy(errorMessage = "Add at least 2 photos to export a collage.") }
            return
        }
        val cfg = _uiState.value.collageConfig
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Rendering high-res collage...", errorMessage = null, savedResult = null) }
            val temp = File(getApplication<Application>().cacheDir, "collage_${System.currentTimeMillis()}.png")
            var rendered: Bitmap? = null
            try {
                rendered = withContext(Dispatchers.Default) {
                    ImageStudioEngine.renderCollage(photos, cfg, previewMaxEdge = null)
                }
                withContext(Dispatchers.IO) {
                    ImageStudioEngine.writeBitmapToFile(rendered, temp, RatioExportFormat.PNG)
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = getApplication(),
                        sourceFile = temp,
                        displayName = "clipper_collage_${cfg.layout.name.lowercase()}_${System.currentTimeMillis()}.png",
                        mimeType = "image/png",
                        category = SaveCategory.IMAGE,
                    )
                }
                _uiState.update { it.copy(isWorking = false, statusLabel = "", savedResult = saved) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "", errorMessage = t.message ?: "Collage export failed.") }
            } finally {
                rendered?.let { if (!it.isRecycled) it.recycle() }
                temp.delete()
            }
        }
    }

    // --- 3. Film Photo Filter Controls ---

    fun selectFilterLook(look: PhotoFilterLook) {
        _uiState.update {
            it.copy(
                filterConfig = PhotoFilterConfig(
                    look = look,
                    brightness = look.brightness,
                    contrast = look.contrast,
                    saturation = look.saturation,
                    warmth = look.warmth,
                    vignette = look.vignette,
                    grain = look.grain,
                ),
            )
        }
        refreshFilterPreview(debounceMs = 0L)
    }

    fun updateFilterConfig(transform: (PhotoFilterConfig) -> PhotoFilterConfig) {
        _uiState.update { it.copy(filterConfig = transform(it.filterConfig)) }
        refreshFilterPreview(debounceMs = 80L)
    }

    private fun refreshFilterPreview(debounceMs: Long) {
        val src = _uiState.value.sourceBitmap ?: return
        val cfg = _uiState.value.filterConfig
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val rendered = withContext(Dispatchers.Default) {
                val previewBase = if (maxOf(src.width, src.height) > 760) {
                    val scale = 760f / maxOf(src.width, src.height).toFloat()
                    Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(32), (src.height * scale).toInt().coerceAtLeast(32), true)
                } else {
                    src
                }
                val out = ImageStudioEngine.renderFilteredPhoto(previewBase, cfg)
                if (previewBase !== src) previewBase.recycle()
                out
            }
            _uiState.update { it.copy(filterPreview = rendered) }
        }
    }

    fun exportFilteredPhoto() {
        val uri = _uiState.value.photoUri ?: return
        val cfg = _uiState.value.filterConfig
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Exporting ${cfg.look.label} grade...", errorMessage = null, savedResult = null) }
            val temp = File(getApplication<Application>().cacheDir, "filter_${System.currentTimeMillis()}.jpg")
            var fullSource: Bitmap? = null
            var rendered: Bitmap? = null
            try {
                fullSource = ImageStudioEngine.decodeOrientedBitmap(getApplication(), uri, maxEdge = 2400)
                rendered = withContext(Dispatchers.Default) {
                    ImageStudioEngine.renderFilteredPhoto(fullSource, cfg)
                }
                withContext(Dispatchers.IO) {
                    ImageStudioEngine.writeBitmapToFile(rendered, temp, RatioExportFormat.JPEG)
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = getApplication(),
                        sourceFile = temp,
                        displayName = "clipper_${cfg.look.name.lowercase()}_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg",
                        category = SaveCategory.IMAGE,
                    )
                }
                _uiState.update { it.copy(isWorking = false, statusLabel = "", savedResult = saved) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "", errorMessage = t.message ?: "Filter export failed.") }
            } finally {
                rendered?.let { if (!it.isRecycled) it.recycle() }
                fullSource?.let { if (!it.isRecycled) it.recycle() }
                temp.delete()
            }
        }
    }

    // --- 4. Privacy EXIF Scrubber Export ---

    fun exportPrivacyCleanPhoto() {
        val uri = _uiState.value.photoUri ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, statusLabel = "Scrubbing GPS & camera EXIF metadata...", errorMessage = null, savedResult = null) }
            val temp = File(getApplication<Application>().cacheDir, "clean_exif_${System.currentTimeMillis()}.jpg")
            var cleanBmp: Bitmap? = null
            try {
                cleanBmp = ImageStudioEngine.decodeOrientedBitmap(getApplication(), uri, maxEdge = 3840)
                withContext(Dispatchers.IO) {
                    ImageStudioEngine.writeBitmapToFile(cleanBmp, temp, RatioExportFormat.JPEG)
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = getApplication(),
                        sourceFile = temp,
                        displayName = "clipper_privacy_clean_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg",
                        category = SaveCategory.IMAGE,
                    )
                }
                _uiState.update { it.copy(isWorking = false, statusLabel = "", savedResult = saved) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update { it.copy(isWorking = false, statusLabel = "", errorMessage = t.message ?: "EXIF scrub failed.") }
            } finally {
                cleanBmp?.let { if (!it.isRecycled) it.recycle() }
                temp.delete()
            }
        }
    }
}
