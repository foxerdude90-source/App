package com.example.ui.features.cutout

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.cutout.BackgroundRemovalEngine
import com.example.core.cutout.CutoutBackgroundStyle
import com.example.core.cutout.DetectedSubjectItem
import com.example.core.cutout.LassoMode
import com.example.core.cutout.NormalizedPoint
import com.example.core.cutout.SubjectSegmentationEngine
import com.example.core.media.ImageCompressorEngine
import com.example.core.media.MediaStoreSaver
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class CutoutTool(val label: String) {
    LASSO("Lasso"),
    MAGIC_WAND("Magic Wand"),
    ERASE_BRUSH("Erase"),
    RESTORE_BRUSH("Restore"),
}

data class CutoutUiState(
    val sourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val activeTool: CutoutTool = CutoutTool.LASSO,
    val lassoMode: LassoMode = LassoMode.KEEP_INSIDE,
    val backgroundStyle: CutoutBackgroundStyle = CutoutBackgroundStyle.TRANSPARENT,
    val wandTolerance: Float = 28f,
    val objectSensitivity: Float = 26f,
    val brushSizeFraction: Float = 0.055f,
    val featherRadius: Int = 2,
    val decontaminateEdges: Boolean = true,
    val autoCropExport: Boolean = false,
    val detectedSubjects: List<DetectedSubjectItem> = emptyList(),
    val selectedSubjectId: Int? = null, // null = all subjects combined
    val activeEngineLabel: String? = null,
    val foregroundPercent: Float? = null,
    val showOriginalComparison: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
)

class CutoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CutoutUiState())
    val uiState: StateFlow<CutoutUiState> = _uiState.asStateFlow()

    private var sourcePixels: IntArray? = null
    private var currentMask: ByteArray? = null
    private var combinedDetectedMask: ByteArray? = null
    private val undoStack = ArrayDeque<ByteArray>()
    private val redoStack = ArrayDeque<ByteArray>()

    companion object {
        private const val MAX_WORK_EDGE_PX = 1080
        private const val MAX_HISTORY = 14
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    savedResult = null,
                    detectedSubjects = emptyList(),
                    selectedSubjectId = null,
                    activeEngineLabel = null,
                    foregroundPercent = null,
                )
            }
            try {
                val bmp = withContext(Dispatchers.Default) {
                    ImageCompressorEngine.decodeSampledBitmap(getApplication(), uri, MAX_WORK_EDGE_PX)
                }
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val fullMask = BackgroundRemovalEngine.createFullOpaqueMask(bmp.width, bmp.height)

                sourcePixels = pixels
                currentMask = fullMask
                combinedDetectedMask = null
                undoStack.clear()
                redoStack.clear()

                val preview = withContext(Dispatchers.Default) {
                    BackgroundRemovalEngine.composeResultBitmap(bmp, fullMask, _uiState.value.backgroundStyle)
                }
                _uiState.update {
                    it.copy(
                        sourceBitmap = bmp,
                        previewBitmap = preview,
                        canUndo = false,
                        canRedo = false,
                        isProcessing = false,
                        statusMessage = "Loaded ${bmp.width}×${bmp.height}px. Tap AI Subject Remover to isolate any foreground object.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Failed to decode image.")
                }
            }
        }
    }

    fun selectTool(tool: CutoutTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun selectLassoMode(mode: LassoMode) {
        _uiState.update { it.copy(lassoMode = mode) }
    }

    fun setWandTolerance(tolerance: Float) {
        _uiState.update { it.copy(wandTolerance = tolerance) }
    }

    fun setObjectSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(objectSensitivity = sensitivity) }
    }

    fun setBrushSize(fraction: Float) {
        _uiState.update { it.copy(brushSizeFraction = fraction.coerceIn(0.015f, 0.16f)) }
    }

    fun setFeatherRadius(radius: Int) {
        _uiState.update { it.copy(featherRadius = radius.coerceIn(0, 6)) }
    }

    fun toggleDecontaminateEdges() {
        _uiState.update { it.copy(decontaminateEdges = !it.decontaminateEdges) }
        recomposePreview()
    }

    fun toggleAutoCropExport() {
        _uiState.update { it.copy(autoCropExport = !it.autoCropExport) }
    }

    fun toggleOriginalComparison() {
        _uiState.update { it.copy(showOriginalComparison = !it.showOriginalComparison) }
    }

    fun selectBackground(style: CutoutBackgroundStyle) {
        _uiState.update { it.copy(backgroundStyle = style) }
        recomposePreview()
    }

    /**
     * Primary AI Subject Remover: uses Google ML Kit Subject Segmentation for ANY object/person/pet
     * with automatic anti-wipeout protection and offline Portrait + Edge-Barrier Saliency fallback.
     */
    fun runAiSubjectDetect() {
        val src = _uiState.value.sourceBitmap ?: return
        val sens = _uiState.value.objectSensitivity
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Detecting foreground subjects with ML Kit Subject AI...",
                    errorMessage = null,
                )
            }
            try {
                val res = SubjectSegmentationEngine.detectAnySubject(src, fallbackSensitivity = sens)
                combinedDetectedMask = res.combinedMask
                _uiState.update {
                    it.copy(
                        detectedSubjects = res.subjects,
                        selectedSubjectId = null,
                        activeEngineLabel = res.engineUsed,
                        foregroundPercent = res.foregroundPercent,
                    )
                }
                val pctStr = String.format(Locale.US, "%.1f%%", res.foregroundPercent)
                commitMask(
                    res.combinedMask,
                    "${res.engineUsed}: isolated ${res.subjects.size.coerceAtLeast(1)} subject(s) ($pctStr kept).",
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Subject segmentation failed.")
                }
            }
        }
    }

    fun selectDetectedSubject(subjectId: Int?) {
        val maskToUse = if (subjectId == null) {
            combinedDetectedMask
        } else {
            _uiState.value.detectedSubjects.firstOrNull { it.id == subjectId }?.mask
        } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSubjectId = subjectId, isProcessing = true) }
            val label = if (subjectId == null) "All detected subjects selected." else "Isolated Subject #$subjectId."
            commitMask(maskToUse.copyOf(), label)
        }
    }

    fun runAiPortraitDetect() {
        val src = _uiState.value.sourceBitmap ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, statusMessage = "Running bundled offline ML Portrait AI...", errorMessage = null)
            }
            try {
                val mask = BackgroundRemovalEngine.detectPortraitMask(src)
                val ratio = SubjectSegmentationEngine.computeForegroundRatio(mask)
                if (ratio < 0.015f) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "No human portrait found in this image. Use AI Subject instead to detect general objects.",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        activeEngineLabel = "ML Kit Portrait AI",
                        foregroundPercent = ratio * 100f,
                    )
                }
                commitMask(mask, "Offline Portrait AI mask applied (${String.format(Locale.US, "%.1f%%", ratio * 100f)} kept).")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Portrait segmentation failed.")
                }
            }
        }
    }

    fun runSmartObjectDetect() {
        val src = _uiState.value.sourceBitmap ?: return
        val pixels = sourcePixels ?: return
        val sens = _uiState.value.objectSensitivity
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, statusMessage = "Running Edge-Barrier CIELAB object segmentation...", errorMessage = null)
            }
            try {
                val mask = withContext(Dispatchers.Default) {
                    SubjectSegmentationEngine.computeEdgeBarrierSalientMask(pixels, src.width, src.height, sens)
                }
                val pct = SubjectSegmentationEngine.computeForegroundRatio(mask) * 100f
                _uiState.update {
                    it.copy(
                        activeEngineLabel = "Edge-Barrier Saliency",
                        foregroundPercent = pct,
                    )
                }
                commitMask(mask, "Edge-Barrier cutout applied (ΔE = ${sens.toInt()}, ${String.format(Locale.US, "%.1f%%", pct)} kept).")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Object detection failed.")
                }
            }
        }
    }

    fun adjustMaskEdge(deltaPx: Int) {
        val src = _uiState.value.sourceBitmap ?: return
        val mask = currentMask ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val next = withContext(Dispatchers.Default) {
                SubjectSegmentationEngine.morphologicalAdjustMask(mask, src.width, src.height, deltaPx)
            }
            val label = if (deltaPx > 0) "Expanded mask edges by +${deltaPx}px." else "Shrank mask edges by ${deltaPx}px."
            commitMask(next, label)
        }
    }

    fun invertCurrentMask() {
        val mask = currentMask ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val inverted = withContext(Dispatchers.Default) {
                SubjectSegmentationEngine.invertMask(mask)
            }
            commitMask(inverted, "Inverted foreground & background mask.")
        }
    }

    fun applyLasso(polygon: List<NormalizedPoint>) {
        val src = _uiState.value.sourceBitmap ?: return
        val mask = currentMask ?: return
        if (polygon.size < 3) return
        val mode = _uiState.value.lassoMode
        val feather = _uiState.value.featherRadius

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val nextMask = withContext(Dispatchers.Default) {
                BackgroundRemovalEngine.applyLassoSelection(
                    currentMask = mask,
                    width = src.width,
                    height = src.height,
                    polygon = polygon,
                    mode = mode,
                    featherRadius = feather,
                )
            }
            val label = if (mode == LassoMode.KEEP_INSIDE) "Lasso kept inside region." else "Lasso removed selected region."
            commitMask(nextMask, label)
        }
    }

    fun applyMagicWandTap(point: NormalizedPoint) {
        val src = _uiState.value.sourceBitmap ?: return
        val pixels = sourcePixels ?: return
        val mask = currentMask ?: return
        val tolerance = _uiState.value.wandTolerance

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val nextMask = withContext(Dispatchers.Default) {
                BackgroundRemovalEngine.applyMagicWandFloodFill(
                    pixels = pixels,
                    currentMask = mask,
                    width = src.width,
                    height = src.height,
                    seedNormX = point.x,
                    seedNormY = point.y,
                    tolerance = tolerance,
                )
            }
            commitMask(nextMask, "Magic Wand removed matching color region.")
        }
    }

    fun applyBrushStroke(points: List<NormalizedPoint>) {
        val src = _uiState.value.sourceBitmap ?: return
        val mask = currentMask ?: return
        if (points.isEmpty()) return
        val isErase = _uiState.value.activeTool == CutoutTool.ERASE_BRUSH
        val radius = _uiState.value.brushSizeFraction

        viewModelScope.launch {
            val nextMask = withContext(Dispatchers.Default) {
                BackgroundRemovalEngine.applyBrushStroke(
                    currentMask = mask,
                    width = src.width,
                    height = src.height,
                    points = points,
                    radiusFraction = radius,
                    erase = isErase,
                )
            }
            commitMask(nextMask, if (isErase) "Brush erased area." else "Brush restored area.")
        }
    }

    fun resetMask() {
        val src = _uiState.value.sourceBitmap ?: return
        val full = BackgroundRemovalEngine.createFullOpaqueMask(src.width, src.height)
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSubjectId = null, foregroundPercent = 100f) }
            commitMask(full, "Mask restored to original image.")
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        currentMask?.let { redoStack.addLast(it.copyOf()) }
        currentMask = prev
        recomposePreview("Undid last mask edit.")
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        currentMask?.let { undoStack.addLast(it.copyOf()) }
        currentMask = next
        recomposePreview("Redid mask edit.")
    }

    fun exportPng() {
        val bmp = _uiState.value.previewBitmap ?: return
        val mask = currentMask
        val shouldCrop = _uiState.value.autoCropExport
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, savedResult = null) }
            try {
                val saved = withContext(Dispatchers.IO) {
                    val exportBmp = if (shouldCrop && mask != null) {
                        SubjectSegmentationEngine.autoCropToSubjectBounds(bmp, mask, paddingPx = 12)
                    } else {
                        bmp
                    }
                    val temp = File(context.cacheDir, "cutout_${System.currentTimeMillis()}.png")
                    FileOutputStream(temp).use { out ->
                        exportBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    if (exportBmp !== bmp && !exportBmp.isRecycled) exportBmp.recycle()
                    val res = MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = temp,
                        displayName = "clipper_cutout_${System.currentTimeMillis()}.png",
                        mimeType = "image/png",
                        category = SaveCategory.IMAGE,
                    )
                    temp.delete()
                    res
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        savedResult = saved,
                        statusMessage = "Exported PNG (${MediaStoreSaver.formatByteSize(saved.sizeBytes)})",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Failed to export PNG.")
                }
            }
        }
    }

    private suspend fun commitMask(newMask: ByteArray, message: String) {
        currentMask?.let { existing ->
            undoStack.addLast(existing.copyOf())
            while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
        redoStack.clear()
        currentMask = newMask
        recomposePreview(message)
    }

    private fun recomposePreview(message: String? = null) {
        val src = _uiState.value.sourceBitmap ?: return
        val mask = currentMask ?: return
        val style = _uiState.value.backgroundStyle
        val decontaminate = _uiState.value.decontaminateEdges
        viewModelScope.launch {
            val composed = withContext(Dispatchers.Default) {
                val cleanSource = if (decontaminate) {
                    SubjectSegmentationEngine.decontaminateEdgeColors(src, mask)
                } else {
                    src
                }
                val result = BackgroundRemovalEngine.composeResultBitmap(cleanSource, mask, style)
                if (cleanSource !== src && !cleanSource.isRecycled) cleanSource.recycle()
                result
            }
            val pct = SubjectSegmentationEngine.computeForegroundRatio(mask) * 100f
            _uiState.update {
                it.copy(
                    previewBitmap = composed,
                    foregroundPercent = pct,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    isProcessing = false,
                    statusMessage = message ?: it.statusMessage,
                )
            }
        }
    }
}
