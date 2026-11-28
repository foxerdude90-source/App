package com.example.ui.features.captions

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.captions.CaptionAlignment
import com.example.core.captions.CaptionBitmapRenderer
import com.example.core.captions.CaptionCue
import com.example.core.captions.CaptionStyle
import com.example.core.captions.SubtitleCodec
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.media.VideoProcessingEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CaptionsUiState(
    val selectedVideo: ProbedMediaInfo? = null,
    val playbackPositionMs: Long = 0L,
    val scriptInput: String = "Create *crisp* studio captions with *ten* custom offline fonts and *vibrant* multi-color highlights.",
    val cues: List<CaptionCue> = SubtitleCodec.autoDistributeScript(
        script = "Create *crisp* studio captions with *ten* custom offline fonts and *vibrant* multi-color highlights.",
        totalDurationMs = 9000L,
        wordsPerCue = 5,
    ),
    val selectedCueId: Long = 1L,
    val style: CaptionStyle = CaptionStyle(),
    val isDictating: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportLabel: String = "",
    val savedResult: SavedMediaResult? = null,
    val errorMessage: String? = null,
) {
    val activePreviewCue: CaptionCue?
        get() {
            if (selectedVideo != null) {
                val atTime = cues.firstOrNull { playbackPositionMs in it.startMs..it.endMs }
                if (atTime != null) return atTime
            }
            return cues.firstOrNull { it.id == selectedCueId } ?: cues.firstOrNull()
        }
}

class CaptionsViewModel(application: Application) : AndroidViewModel(application) {

    private val inspector = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(CaptionsUiState())
    val uiState: StateFlow<CaptionsUiState> = _uiState.asStateFlow()

    fun selectVideoUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = inspector.inspectLocalUri(getApplication(), uri)
                val duration = info.durationMs.takeIf { it > 0L } ?: 10_000L
                val redistributed = if (_uiState.value.scriptInput.isNotBlank()) {
                    SubtitleCodec.autoDistributeScript(_uiState.value.scriptInput, duration)
                } else {
                    _uiState.value.cues
                }
                _uiState.update {
                    it.copy(
                        selectedVideo = info,
                        cues = redistributed,
                        selectedCueId = redistributed.firstOrNull()?.id ?: 1L,
                        playbackPositionMs = 0L,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load video.") }
            }
        }
    }

    fun clearVideo() {
        _uiState.update { it.copy(selectedVideo = null, playbackPositionMs = 0L) }
    }

    fun onPlaybackPositionChanged(positionMs: Long) {
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun onScriptChanged(newScript: String) {
        _uiState.update { it.copy(scriptInput = newScript) }
    }

    fun generateCuesFromScript() {
        val script = _uiState.value.scriptInput.trim()
        if (script.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter or dictate caption text first.") }
            return
        }
        val durationMs = _uiState.value.selectedVideo?.durationMs?.takeIf { it > 0L }
            ?: (script.split(Regex("\\s+")).size * 520L).coerceAtLeast(4000L)
        val generated = SubtitleCodec.autoDistributeScript(script, durationMs, wordsPerCue = 5)
        _uiState.update {
            it.copy(
                cues = generated,
                selectedCueId = generated.firstOrNull()?.id ?: 1L,
                errorMessage = null,
            )
        }
    }

    fun appendDictatedText(recognizedText: String) {
        if (recognizedText.isBlank()) return
        val combined = buildString {
            val current = _uiState.value.scriptInput.trim()
            if (current.isNotEmpty()) {
                append(current).append(' ')
            }
            append(recognizedText.trim())
        }
        _uiState.update { it.copy(scriptInput = combined, isDictating = false) }
        generateCuesFromScript()
    }

    fun setDictating(active: Boolean) {
        _uiState.update { it.copy(isDictating = active) }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isDictating = false) }
    }

    fun selectCue(cueId: Long) {
        _uiState.update { it.copy(selectedCueId = cueId) }
    }

    fun updateCueText(cueId: Long, newText: String) {
        _uiState.update { state ->
            state.copy(
                cues = state.cues.map { cue ->
                    if (cue.id == cueId) cue.copy(text = newText) else cue
                }
            )
        }
    }

    fun deleteCue(cueId: Long) {
        _uiState.update { state ->
            val next = state.cues.filterNot { it.id == cueId }
            state.copy(cues = next, selectedCueId = next.firstOrNull()?.id ?: -1L)
        }
    }

    fun addBlankCue() {
        _uiState.update { state ->
            val lastEnd = state.cues.maxOfOrNull { it.endMs } ?: 0L
            val nextId = (state.cues.maxOfOrNull { it.id } ?: 0L) + 1L
            val cue = CaptionCue(
                id = nextId,
                startMs = lastEnd,
                endMs = lastEnd + 2500L,
                text = "New *highlight* line",
            )
            state.copy(cues = state.cues + cue, selectedCueId = nextId)
        }
    }

    fun importSrtUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Could not read subtitle file.")
                }
                val parsed = SubtitleCodec.parseSubtitles(content)
                if (parsed.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "No valid SRT/VTT cues found in file.") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        cues = parsed,
                        selectedCueId = parsed.first().id,
                        scriptInput = parsed.joinToString(" ") { c -> c.text },
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to import SRT.") }
            }
        }
    }

    // Styling Controls
    fun selectFont(fontId: String) {
        _uiState.update { it.copy(style = it.style.copy(fontId = fontId)) }
    }

    fun selectTextColor(color: Color) {
        _uiState.update { it.copy(style = it.style.copy(textColor = color)) }
    }

    fun selectAccentColor(color: Color) {
        _uiState.update { it.copy(style = it.style.copy(accentColor = color)) }
    }

    fun selectBackgroundColor(color: Color) {
        _uiState.update { it.copy(style = it.style.copy(backgroundColor = color)) }
    }

    fun setBackgroundAlpha(alpha: Float) {
        _uiState.update { it.copy(style = it.style.copy(backgroundAlpha = alpha.coerceIn(0f, 1f))) }
    }

    fun setFontSize(sp: Float) {
        _uiState.update { it.copy(style = it.style.copy(fontSizeSp = sp.coerceIn(16f, 44f))) }
    }

    fun setVerticalPosition(pos: Float) {
        _uiState.update { it.copy(style = it.style.copy(verticalPosition = pos.coerceIn(0.15f, 0.88f))) }
    }

    fun toggleStroke() {
        _uiState.update { it.copy(style = it.style.copy(strokeEnabled = !it.style.strokeEnabled)) }
    }

    fun toggleUppercase() {
        _uiState.update { it.copy(style = it.style.copy(uppercase = !it.style.uppercase)) }
    }

    fun setAlignment(alignment: CaptionAlignment) {
        _uiState.update { it.copy(style = it.style.copy(alignment = alignment)) }
    }

    fun exportSrt() {
        val cues = _uiState.value.cues
        if (cues.isEmpty()) return
        viewModelScope.launch {
            try {
                val srtText = SubtitleCodec.serializeToSrt(cues)
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveTextToDownloads(
                        context = getApplication(),
                        content = srtText,
                        displayName = "clipper_captions_${System.currentTimeMillis()}.srt",
                        mimeType = "application/x-subrip",
                    )
                }
                _uiState.update { it.copy(savedResult = saved, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to export SRT.") }
            }
        }
    }

    fun exportStyledCaptionCard() {
        val activeText = _uiState.value.activePreviewCue?.text ?: _uiState.value.scriptInput
        if (activeText.isBlank()) return
        val style = _uiState.value.style
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportProgress = 0.5f, exportLabel = "Rendering 1080×1350 caption card...", errorMessage = null) }
            val tempFile = File(context.cacheDir, "caption_card_${System.currentTimeMillis()}.png")
            try {
                val saved = withContext(Dispatchers.Default) {
                    val cardBitmap = CaptionBitmapRenderer.renderStandaloneCardBitmap(context, activeText, style)
                    try {
                        FileOutputStream(tempFile).use { out ->
                            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                        }
                    } finally {
                        cardBitmap.recycle()
                    }
                    MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = tempFile,
                        displayName = "clipper_card_${System.currentTimeMillis()}.png",
                        mimeType = "image/png",
                        category = SaveCategory.IMAGE,
                    )
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        savedResult = saved,
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0f, exportLabel = "") }
                throw ce
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, errorMessage = e.message ?: "Card export failed.") }
            } finally {
                tempFile.delete()
            }
        }
    }

    fun exportBurnedVideo() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val uri = video.localUri ?: return
        if (state.cues.isEmpty()) return
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    exportLabel = "Burning styled captions into video...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            val outFile = File(context.cacheDir, "captioned_${System.currentTimeMillis()}.mp4")
            try {
                VideoProcessingEngine.burnCaptionsIntoVideo(
                    context = context,
                    inputUri = uri,
                    outputFile = outFile,
                    cues = state.cues,
                    style = state.style,
                    videoWidth = video.width,
                    videoHeight = video.height,
                    onProgress = { p ->
                        _uiState.update { it.copy(exportProgress = p, exportLabel = "Burning captions into MP4...") }
                    },
                )
                val baseName = video.fileName.substringBeforeLast('.').ifBlank { "clip" }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = outFile,
                        displayName = "${baseName}_captioned.mp4",
                        mimeType = "video/mp4",
                        category = SaveCategory.VIDEO,
                    )
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
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
                        errorMessage = e.message ?: "Caption burn-in failed.",
                    )
                }
            } finally {
                outFile.delete()
            }
        }
    }
}
