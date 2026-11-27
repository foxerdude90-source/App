package com.example.ui.features.velocity

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.velocity.AudioBeatAnalysis
import com.example.core.velocity.AutoVelocityFrequency
import com.example.core.velocity.AutoVelocitySettings
import com.example.core.velocity.BeatDetectionEngine
import com.example.core.velocity.DetectedBeat
import com.example.core.velocity.VelocityAspectCrop
import com.example.core.velocity.VelocityCurveMath
import com.example.core.velocity.VelocityCurvePreset
import com.example.core.velocity.VelocityExportConfig
import com.example.core.velocity.VelocityExportEngine
import com.example.core.velocity.VelocityKeyframe
import com.example.core.velocity.VelocitySystemMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

data class VelocityUiState(
    // Source Video
    val video: ProbedMediaInfo? = null,
    val videoTrimStartMs: Long = 0L,
    val videoTrimEndMs: Long = 0L,
    // Attached Music Track
    val musicUri: Uri? = null,
    val musicFileName: String? = null,
    val musicDurationMs: Long = 0L,
    val musicTrimStartMs: Long = 0L,
    val musicTrimEndMs: Long = 0L,
    val keepOriginalVideoAudioIfNoMusic: Boolean = true,
    // Crop & Reframe
    val aspectCrop: VelocityAspectCrop = VelocityAspectCrop.ORIGINAL,
    val cropZoom: Float = 1.0f,
    // Beat Detection DSP
    val isAnalyzingAudio: Boolean = false,
    val beatAnalysis: AudioBeatAnalysis? = null,
    // 2-System Mode
    val mode: VelocitySystemMode = VelocitySystemMode.AUTO_BEAT,
    // System 1: Auto Velocity Settings
    val autoSettings: AutoVelocitySettings = AutoVelocitySettings(),
    // System 2: Custom CapCut-Style Velocity Curve
    val activePreset: VelocityCurvePreset? = VelocityCurvePreset.MONTAGE,
    val customKeyframes: List<VelocityKeyframe> = VelocityCurveMath.presetKeyframes(VelocityCurvePreset.MONTAGE),
    val selectedKeyframeId: String? = "p2",
    val snapToBeats: Boolean = true,
    // Live Preview State
    val previewNormalizedPlayhead: Float = 0f,
    val livePreviewSpeed: Float = 1.0f,
    // Export State
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
) {
    val trimmedVideoDurationMs: Long
        get() {
            val rawDur = video?.durationMs ?: 10_000L
            val end = if (videoTrimEndMs > videoTrimStartMs) videoTrimEndMs else rawDur
            return (end - videoTrimStartMs).coerceAtLeast(300L)
        }

    val effectiveAutoSettings: AutoVelocitySettings
        get() = VelocityCurveMath.resolveEffectiveAutoSettings(
            settings = autoSettings,
            bpm = beatAnalysis?.bpm ?: 120f,
        )

    val activeKeyframes: List<VelocityKeyframe>
        get() = when (mode) {
            VelocitySystemMode.AUTO_BEAT -> VelocityCurveMath.generateAutoVelocityCurve(
                beats = beatAnalysis?.beats.orEmpty(),
                rawSettings = autoSettings,
                bpm = beatAnalysis?.bpm ?: 120f,
            )
            VelocitySystemMode.CUSTOM_CURVE -> VelocityCurveMath.sanitizeKeyframes(customKeyframes)
        }

    val predictedOutputDurationMs: Long
        get() = VelocityCurveMath.buildTimeRemapLut(
            keyframes = activeKeyframes,
            trimmedSourceDurationMs = trimmedVideoDurationMs,
        ).totalOutputDurationMs

    val averageSpeed: Float
        get() = (trimmedVideoDurationMs.toFloat() / predictedOutputDurationMs.coerceAtLeast(1L).toFloat())
            .coerceIn(0.15f, 5.0f)
}

class VelocityViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(VelocityUiState())
    val uiState: StateFlow<VelocityUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var exportJob: Job? = null

    fun selectMode(mode: VelocitySystemMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
    }

    fun loadVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, savedResult = null) }
            try {
                val info = fetcher.inspectLocalUri(getApplication(), uri)
                val safeDur = info.durationMs.coerceAtLeast(1000L)
                _uiState.update {
                    it.copy(
                        video = info,
                        videoTrimStartMs = 0L,
                        videoTrimEndMs = safeDur,
                        previewNormalizedPlayhead = 0f,
                    )
                }
                // If user hasn't attached external music yet, analyze video's own audio track automatically!
                if (_uiState.value.musicUri == null) {
                    analyzeAudioForBeats(uri, trimStartMs = 0L, trimEndMs = safeDur)
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = t.message ?: "Could not load selected video.") }
            }
        }
    }

    fun attachMusic(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, savedResult = null) }
            try {
                val (name, durationMs) = probeAudioMeta(uri)
                val safeDur = durationMs.coerceAtLeast(1000L)
                val defaultEnd = minOf(safeDur, 30_000L)
                _uiState.update {
                    it.copy(
                        musicUri = uri,
                        musicFileName = name,
                        musicDurationMs = safeDur,
                        musicTrimStartMs = 0L,
                        musicTrimEndMs = defaultEnd,
                    )
                }
                analyzeAudioForBeats(uri, trimStartMs = 0L, trimEndMs = defaultEnd)
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = t.message ?: "Could not load selected music track.") }
            }
        }
    }

    fun removeMusic() {
        val vid = _uiState.value.video
        _uiState.update {
            it.copy(
                musicUri = null,
                musicFileName = null,
                musicDurationMs = 0L,
                musicTrimStartMs = 0L,
                musicTrimEndMs = 0L,
                beatAnalysis = null,
            )
        }
        val vidUri = vid?.localUri
        if (vidUri != null) {
            analyzeAudioForBeats(vidUri, _uiState.value.videoTrimStartMs, _uiState.value.videoTrimEndMs)
        }
    }

    fun updateVideoTrim(startMs: Long, endMs: Long) {
        val dur = _uiState.value.video?.durationMs ?: return
        val s = startMs.coerceIn(0L, (dur - 400L).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + 400L, dur)
        _uiState.update { it.copy(videoTrimStartMs = s, videoTrimEndMs = e) }
    }

    fun updateMusicTrim(startMs: Long, endMs: Long) {
        val dur = _uiState.value.musicDurationMs
        if (dur <= 0L) return
        val s = startMs.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + 500L, dur)
        _uiState.update { it.copy(musicTrimStartMs = s, musicTrimEndMs = e) }
    }

    fun reanalyzeCurrentAudio() {
        val state = _uiState.value
        val music = state.musicUri
        if (music != null) {
            analyzeAudioForBeats(music, state.musicTrimStartMs, state.musicTrimEndMs)
        } else {
            val vidUri = state.video?.localUri ?: return
            analyzeAudioForBeats(vidUri, state.videoTrimStartMs, state.videoTrimEndMs)
        }
    }

    private fun analyzeAudioForBeats(uri: Uri, trimStartMs: Long, trimEndMs: Long) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingAudio = true, errorMessage = null) }
            try {
                val analysis = BeatDetectionEngine.analyzeAudioUri(
                    context = getApplication(),
                    uri = uri,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                )
                _uiState.update { it.copy(isAnalyzingAudio = false, beatAnalysis = analysis) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzingAudio = false,
                        errorMessage = "Beat scan note: ${t.message ?: "no audio track detected"}",
                    )
                }
            }
        }
    }

    fun selectAspectCrop(crop: VelocityAspectCrop) {
        _uiState.update { it.copy(aspectCrop = crop) }
    }

    fun setCropZoom(zoom: Float) {
        _uiState.update { it.copy(cropZoom = zoom.coerceIn(1.0f, 2.0f)) }
    }

    // --- System 1: Auto Velocity Controls ---

    fun setAutoTuning(enabled: Boolean) {
        _uiState.update { state ->
            val resolved = if (!enabled) state.effectiveAutoSettings.copy(isAutoTuning = false) else state.autoSettings.copy(isAutoTuning = true)
            state.copy(autoSettings = resolved)
        }
    }

    fun setAutoFrequency(frequency: AutoVelocityFrequency) {
        _uiState.update {
            it.copy(autoSettings = it.effectiveAutoSettings.copy(isAutoTuning = false, frequency = frequency))
        }
    }

    fun setAutoSmoothness(value: Float) {
        _uiState.update {
            it.copy(autoSettings = it.effectiveAutoSettings.copy(isAutoTuning = false, smoothness = value.coerceIn(0.15f, 1.0f)))
        }
    }

    fun setAutoMaxSlowMo(value: Float) {
        _uiState.update {
            it.copy(autoSettings = it.effectiveAutoSettings.copy(isAutoTuning = false, maxSlowMoSpeed = value.coerceIn(0.15f, 0.75f)))
        }
    }

    fun setAutoMaxFastSpeed(value: Float) {
        _uiState.update {
            it.copy(autoSettings = it.effectiveAutoSettings.copy(isAutoTuning = false, maxFastSpeed = value.coerceIn(1.40f, 4.50f)))
        }
    }

    fun toggleManualBeatAt(normalizedPos: Float) {
        val pos = normalizedPos.coerceIn(0.04f, 0.96f)
        _uiState.update { state ->
            val existing = state.beatAnalysis
            val durMs = existing?.durationMs ?: state.trimmedVideoDurationMs
            val beats = existing?.beats?.toMutableList() ?: mutableListOf()
            val nearIdx = beats.indexOfFirst { abs(it.normalizedPos - pos) < 0.035f }
            if (nearIdx >= 0) {
                beats.removeAt(nearIdx)
            } else {
                beats.add(
                    DetectedBeat(
                        timeMs = (pos * durMs).roundToLong(),
                        normalizedPos = pos,
                        strength = 1.0f,
                        isManual = true,
                    ),
                )
                beats.sortBy { it.normalizedPos }
            }
            val updated = (existing ?: AudioBeatAnalysis(durMs, 120f, emptyList(), List(96) { 0.2f }))
                .copy(beats = beats)
            state.copy(beatAnalysis = updated)
        }
    }

    /**
     * Transfers the currently synthesized Auto Beat curve directly into Custom Curve mode
     * so users can fine-tune any individual peak, pause, or slow-mo valley by hand.
     */
    fun convertAutoToCustomCurve() {
        val autoCurve = _uiState.value.activeKeyframes
        // Thin to 7-11 clean editable control points while preserving peaks & valleys
        val stride = (autoCurve.size / 9).coerceAtLeast(1)
        val picked = autoCurve.filterIndexed { idx, _ ->
            idx == 0 || idx == autoCurve.lastIndex || idx % stride == 0
        }.mapIndexed { idx, kf ->
            kf.copy(id = "custom_${idx}_${UUID.randomUUID().toString().take(4)}")
        }
        _uiState.update {
            it.copy(
                mode = VelocitySystemMode.CUSTOM_CURVE,
                activePreset = null,
                customKeyframes = picked,
                selectedKeyframeId = picked.getOrNull(picked.size / 2)?.id,
            )
        }
    }

    // --- System 2: Custom Velocity Curve Controls ---

    fun applyCurvePreset(preset: VelocityCurvePreset) {
        val kfs = VelocityCurveMath.presetKeyframes(preset)
        _uiState.update {
            it.copy(
                mode = VelocitySystemMode.CUSTOM_CURVE,
                activePreset = preset,
                customKeyframes = kfs,
                selectedKeyframeId = kfs.getOrNull(kfs.size / 2)?.id,
            )
        }
    }

    fun selectKeyframe(id: String) {
        _uiState.update { it.copy(selectedKeyframeId = id) }
    }

    fun setSnapToBeats(enabled: Boolean) {
        _uiState.update { it.copy(snapToBeats = enabled) }
    }

    fun moveKeyframe(id: String, rawT: Float, rawSpeed: Float) {
        _uiState.update { state ->
            val sorted = state.customKeyframes.sortedBy { it.t }
            val index = sorted.indexOfFirst { it.id == id }
            if (index < 0) return@update state

            val isFirst = index == 0
            val isLast = index == sorted.lastIndex
            val minT = if (isFirst) 0f else (sorted[index - 1].t + 0.025f)
            val maxT = if (isLast) 1f else (sorted[index + 1].t - 0.025f)

            var targetT = when {
                isFirst -> 0f
                isLast -> 1f
                else -> rawT.coerceIn(minT, maxT)
            }

            // Optional magnetic snap to nearby beat markers
            if (!isFirst && !isLast && state.snapToBeats) {
                val beats = state.beatAnalysis?.beats.orEmpty()
                val nearestBeat = beats.minByOrNull { abs(it.normalizedPos - targetT) }
                if (nearestBeat != null && abs(nearestBeat.normalizedPos - targetT) < 0.022f) {
                    targetT = nearestBeat.normalizedPos.coerceIn(minT, maxT)
                }
            }

            // Subtle magnetic snap to 1.0x baseline for clean pauses/normal speed
            val snappedSpeed = if (abs(rawSpeed - 1.0f) < 0.06f) {
                1.0f
            } else {
                rawSpeed.coerceIn(VelocityCurveMath.MIN_SPEED, VelocityCurveMath.MAX_SPEED)
            }

            val updated = sorted.mapIndexed { idx, kf ->
                if (idx == index) kf.copy(t = targetT, speed = snappedSpeed) else kf
            }
            state.copy(
                activePreset = null,
                customKeyframes = updated,
                selectedKeyframeId = id,
            )
        }
    }

    fun addKeyframeAt(rawT: Float, rawSpeed: Float? = null) {
        _uiState.update { state ->
            if (state.customKeyframes.size >= 16) return@update state
            val t = rawT.coerceIn(0.03f, 0.97f)
            // Avoid stacking directly on top of an existing node
            if (state.customKeyframes.any { abs(it.t - t) < 0.022f }) return@update state
            val speed = (rawSpeed ?: VelocityCurveMath.evaluateSplineSpeed(state.customKeyframes, t))
                .coerceIn(VelocityCurveMath.MIN_SPEED, VelocityCurveMath.MAX_SPEED)
            val newKf = VelocityKeyframe(
                id = "pt_${UUID.randomUUID().toString().take(6)}",
                t = t,
                speed = speed,
            )
            val updated = (state.customKeyframes + newKf).sortedBy { it.t }
            state.copy(
                activePreset = null,
                customKeyframes = updated,
                selectedKeyframeId = newKf.id,
            )
        }
    }

    fun addKeyframeAtPlayheadOrGap() {
        val state = _uiState.value
        val sorted = state.customKeyframes.sortedBy { it.t }
        val playhead = state.previewNormalizedPlayhead
        val targetT = if (playhead in 0.04f..0.96f && sorted.none { abs(it.t - playhead) < 0.04f }) {
            playhead
        } else {
            // Find the largest gap between consecutive points and bisect it
            var bestMid = 0.5f
            var maxGap = 0f
            for (i in 0 until sorted.lastIndex) {
                val gap = sorted[i + 1].t - sorted[i].t
                if (gap > maxGap) {
                    maxGap = gap
                    bestMid = (sorted[i].t + sorted[i + 1].t) * 0.5f
                }
            }
            bestMid
        }
        addKeyframeAt(targetT)
    }

    fun deleteSelectedKeyframe() {
        _uiState.update { state ->
            val id = state.selectedKeyframeId ?: return@update state
            val sorted = state.customKeyframes.sortedBy { it.t }
            val index = sorted.indexOfFirst { it.id == id }
            // Keep start and end anchors intact
            if (index <= 0 || index >= sorted.lastIndex) return@update state
            val remaining = sorted.filterNot { it.id == id }
            state.copy(
                activePreset = null,
                customKeyframes = remaining,
                selectedKeyframeId = remaining.getOrNull((index - 1).coerceAtLeast(0))?.id,
            )
        }
    }

    fun setSelectedKeyframeSpeed(speed: Float) {
        val id = _uiState.value.selectedKeyframeId ?: return
        val kf = _uiState.value.customKeyframes.firstOrNull { it.id == id } ?: return
        moveKeyframe(id, kf.t, speed)
    }

    fun updatePreviewPlayhead(normalized: Float, liveSpeed: Float) {
        _uiState.update {
            it.copy(
                previewNormalizedPlayhead = normalized.coerceIn(0f, 1f),
                livePreviewSpeed = liveSpeed,
            )
        }
    }

    // --- Hardware Velocity Export ---

    fun exportVelocityVideo() {
        val state = _uiState.value
        val vidUri = state.video?.localUri
        if (vidUri == null) {
            _uiState.update { it.copy(errorMessage = "Select a video first to export your velocity edit.") }
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0.02f,
                    errorMessage = null,
                    savedResult = null,
                )
            }
            val tempOut = File(getApplication<Application>().cacheDir, "clipper_velocity_${System.currentTimeMillis()}.mp4")
            try {
                val config = VelocityExportConfig(
                    videoUri = vidUri,
                    videoTrimStartMs = state.videoTrimStartMs,
                    videoTrimEndMs = state.videoTrimEndMs.takeIf { it > state.videoTrimStartMs }
                        ?: (state.video.durationMs.coerceAtLeast(1000L)),
                    audioUri = state.musicUri,
                    audioTrimStartMs = state.musicTrimStartMs,
                    audioDurationMs = state.musicDurationMs,
                    keepOriginalVideoAudioIfNoMusic = state.keepOriginalVideoAudioIfNoMusic,
                    aspectCrop = state.aspectCrop,
                    cropZoom = state.cropZoom,
                    keyframes = state.activeKeyframes,
                )

                VelocityExportEngine.exportVelocityVideo(
                    context = getApplication(),
                    config = config,
                    outputFile = tempOut,
                    onProgress = { p ->
                        _uiState.update { it.copy(exportProgress = p) }
                    },
                )

                val modeTag = if (state.mode == VelocitySystemMode.AUTO_BEAT) "auto_velocity" else "curve_velocity"
                val base = state.video.fileName.substringBeforeLast('.').ifBlank { "clipper" }
                val saved = MediaStoreSaver.saveFileToGallery(
                    context = getApplication(),
                    sourceFile = tempOut,
                    displayName = "${base}_${modeTag}.mp4",
                    mimeType = "video/mp4",
                    category = SaveCategory.VIDEO,
                )

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 1f,
                        savedResult = saved,
                    )
                }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isExporting = false, exportProgress = 0f) }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgress = 0f,
                        errorMessage = t.message ?: "Velocity video export failed.",
                    )
                }
            } finally {
                tempOut.delete()
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(isExporting = false, exportProgress = 0f) }
    }

    private suspend fun probeAudioMeta(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        var displayName = "music_track.mp3"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val found = c.getString(idx)
                    if (!found.isNullOrBlank()) displayName = found
                }
            }
        }
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(ctx, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
        } catch (_: Exception) {
            15_000L
        } finally {
            retriever.release()
        }
        displayName to durationMs
    }
}
