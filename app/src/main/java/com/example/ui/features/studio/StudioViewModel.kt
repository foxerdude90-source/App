package com.example.ui.features.studio

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.DirectMediaFetcher
import com.example.core.media.EngineUpdateResult
import com.example.core.media.MediaStoreSaver
import com.example.core.media.ProbedMediaInfo
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.media.YoutubeDlMediaEngine
import com.example.core.studio.AspectRatioPreset
import com.example.core.studio.AudioDspConfig
import com.example.core.studio.ColorGradeParams
import com.example.core.studio.ColorGradePreviewMath
import com.example.core.studio.ColorLutPreset
import com.example.core.studio.ForensicTrackReport
import com.example.core.studio.ProStudioEngine
import com.example.core.studio.WatermarkConfig
import com.example.core.studio.WatermarkPosition
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
import java.io.FileOutputStream

enum class StudioToolId(
    val title: String,
    val subtitle: String,
    val tag: String,
) {
    TRIMMER("Precision Trimmer", "Filmstrip frame-accurate clip cutter", "studio_tool_trimmer"),
    SPEED_RAMP("Speed & Slow-Mo", "0.25×–4× speed with Sonic pitch lock", "studio_tool_speed"),
    COLOR_GRADE("GPU Color Grader", "GLSL LUTs, exposure, contrast & vignette", "studio_tool_color"),
    MERGER("Video Merger", "Multi-clip sequence stitcher & reorder", "studio_tool_merger"),
    REFRAME("Aspect & Rotate", "9:16, 1:1, 4:5 crop/fit, rotate & flip", "studio_tool_reframe"),
    WATERMARK("Brand Watermark", "Hardware-burned text badge overlay", "studio_tool_watermark"),
    AUDIO_DSP("Audio DSP Booster", "+18 dB gain, tanh soft limiter & fades", "studio_tool_audio_dsp"),
    FRAME_GRABBER("Frames & Contact Sheet", "Exact-ms PNG grab & 3×3/4×4 storyboard", "studio_tool_frames"),
    VIDEO_LOOPER("Seamless Looper", "2×–5× continuous loop clip repeater", "studio_tool_looper"),
    FORENSICS("Forensic & Privacy", "Codec/GOP inspector & GPS metadata strip", "studio_tool_forensics"),
    COMPRESSOR("Precision Compressor", "Hardware H.264/HEVC & image optimizer", "studio_tool_compressor"),
    CAPTIONS_STUDIO("Caption Studio", "10 offline fonts, SRT export & video burn-in", "studio_tool_captions"),
}

data class StudioUiState(
    val activeTool: StudioToolId? = null,
    // Shared active single video for tools 1,2,3,5,6,7,8,9,10
    val selectedVideo: ProbedMediaInfo? = null,
    // 1. Trimmer
    val filmstripFrames: List<Bitmap> = emptyList(),
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    // 2. Speed Ramp
    val speedFactor: Float = 1.5f,
    val preservePitch: Boolean = true,
    val speedMuteAudio: Boolean = false,
    // 3. GPU Color Grader
    val activeLutPreset: ColorLutPreset = ColorLutPreset.TEAL_ORANGE,
    val colorParams: ColorGradeParams = ColorGradeParams(
        exposure = ColorLutPreset.TEAL_ORANGE.exposure,
        contrast = ColorLutPreset.TEAL_ORANGE.contrast,
        saturation = ColorLutPreset.TEAL_ORANGE.saturation,
        temperature = ColorLutPreset.TEAL_ORANGE.temperature,
        vignette = ColorLutPreset.TEAL_ORANGE.vignette,
        tealOrange = ColorLutPreset.TEAL_ORANGE.tealOrange,
    ),
    val baseColorFrame: Bitmap? = null,
    val gradedPreviewFrame: Bitmap? = null,
    // 4. Multi-Clip Merger
    val mergeClips: List<ProbedMediaInfo> = emptyList(),
    val mergeNormalize1080p: Boolean = true,
    // 5. Reframe & Rotate
    val aspectPreset: AspectRatioPreset = AspectRatioPreset.VERTICAL_9_16,
    val cropToFill: Boolean = true,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    // 6. Watermark
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    // 7. Audio DSP
    val audioDspConfig: AudioDspConfig = AudioDspConfig(gainDb = 6f, softLimiter = true, fadeInMs = 500L, fadeOutMs = 500L),
    // 8. Frame Grabber & Contact Sheet
    val grabPositionMs: Long = 0L,
    val grabbedFrame: Bitmap? = null,
    val contactSheetGrid: Int = 3,
    val contactSheetPreview: Bitmap? = null,
    // 9. Video Looper
    val loopCount: Int = 3,
    val loopSpeed: Float = 1.0f,
    val loopMuteAudio: Boolean = false,
    // 10. Forensics
    val forensicReport: ForensicTrackReport? = null,
    // Shared Processing State
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val errorMessage: String? = null,
    val savedResult: SavedMediaResult? = null,
    // Media Engine Updater (yt-dlp Nightly)
    val isUpdatingEngine: Boolean = false,
    val engineUpdateResult: EngineUpdateResult? = null,
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = DirectMediaFetcher()
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var colorPreviewJob: Job? = null
    private var grabPreviewJob: Job? = null

    fun openTool(tool: StudioToolId?) {
        _uiState.update {
            it.copy(
                activeTool = tool,
                errorMessage = null,
                savedResult = null,
            )
        }
        val video = _uiState.value.selectedVideo
        if (tool != null && video != null) {
            prepareToolDataForVideo(tool, video)
        }
    }

    fun updateDownloadEngine() {
        if (_uiState.value.isUpdatingEngine) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingEngine = true,
                    engineUpdateResult = null,
                )
            }
            val result = YoutubeDlMediaEngine.updateYtDlpEngine(getApplication())
            _uiState.update {
                it.copy(
                    isUpdatingEngine = false,
                    engineUpdateResult = result,
                )
            }
        }
    }

    fun loadSingleVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progress = 0.2f,
                    progressLabel = "Inspecting video stream...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            try {
                val info = fetcher.inspectLocalUri(getApplication(), uri)
                val dur = info.durationMs.coerceAtLeast(1000L)
                _uiState.update {
                    it.copy(
                        selectedVideo = info,
                        trimStartMs = 0L,
                        trimEndMs = dur,
                        grabPositionMs = dur / 3L,
                        audioDspConfig = it.audioDspConfig.copy(totalDurationMs = info.durationMs),
                        filmstripFrames = emptyList(),
                        baseColorFrame = null,
                        gradedPreviewFrame = null,
                        grabbedFrame = null,
                        contactSheetPreview = null,
                        forensicReport = null,
                        isProcessing = false,
                        progress = 0f,
                        progressLabel = "",
                    )
                }
                _uiState.value.activeTool?.let { tool ->
                    prepareToolDataForVideo(tool, info)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Unable to read video.")
                }
            }
        }
    }

    private fun prepareToolDataForVideo(tool: StudioToolId, video: ProbedMediaInfo) {
        val uri = video.localUri ?: return
        val context = getApplication<Application>()
        when (tool) {
            StudioToolId.TRIMMER -> {
                if (_uiState.value.filmstripFrames.isEmpty()) {
                    viewModelScope.launch {
                        val thumbs = ProStudioEngine.extractFilmstripThumbnails(context, uri, video.durationMs, count = 8)
                        _uiState.update { it.copy(filmstripFrames = thumbs) }
                    }
                }
            }

            StudioToolId.COLOR_GRADE -> {
                if (_uiState.value.baseColorFrame == null) {
                    viewModelScope.launch {
                        val raw = ProStudioEngine.extractFrameAtMs(context, uri, video.durationMs / 3L) ?: return@launch
                        val scaled = scaleDownForPreview(raw, 560)
                        val graded = withContext(Dispatchers.Default) {
                            ColorGradePreviewMath.applyColorGradeCpu(scaled, _uiState.value.colorParams)
                        }
                        _uiState.update { it.copy(baseColorFrame = scaled, gradedPreviewFrame = graded) }
                    }
                }
            }

            StudioToolId.FRAME_GRABBER -> {
                if (_uiState.value.grabbedFrame == null) {
                    refreshGrabbedFrame(_uiState.value.grabPositionMs, debounceMs = 0L)
                }
            }

            StudioToolId.FORENSICS -> {
                if (_uiState.value.forensicReport == null) {
                    viewModelScope.launch {
                        val report = ProStudioEngine.inspectMediaForensics(context, uri, video.fileName, video.sizeBytes)
                        _uiState.update { it.copy(forensicReport = report) }
                    }
                }
            }

            else -> Unit
        }
    }

    // --- Tool 1: Trimmer ---
    fun setTrimRange(startMs: Long, endMs: Long) {
        val dur = _uiState.value.selectedVideo?.durationMs ?: return
        val s = startMs.coerceIn(0L, (dur - 250L).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + 250L, dur.coerceAtLeast(250L))
        _uiState.update { it.copy(trimStartMs = s, trimEndMs = e) }
    }

    fun exportTrimmedVideo() {
        val state = _uiState.value
        val uri = state.selectedVideo?.localUri ?: return
        runExport("Trimming clip...", "trimmed") { outFile, onProg ->
            ProStudioEngine.trimVideo(getApplication(), uri, outFile, state.trimStartMs, state.trimEndMs, onProg)
        }
    }

    // --- Tool 2: Speed Ramp ---
    fun setSpeedFactor(speed: Float) = _uiState.update { it.copy(speedFactor = speed.coerceIn(0.25f, 4.0f)) }
    fun togglePreservePitch() = _uiState.update { it.copy(preservePitch = !it.preservePitch) }
    fun toggleSpeedMuteAudio() = _uiState.update { it.copy(speedMuteAudio = !it.speedMuteAudio) }

    fun exportSpeedVideo() {
        val state = _uiState.value
        val uri = state.selectedVideo?.localUri ?: return
        val tag = String.format(java.util.Locale.US, "speed_%.2fx", state.speedFactor).replace('.', '_')
        runExport("Rendering ${state.speedFactor}× speed...", tag) { outFile, onProg ->
            ProStudioEngine.changeVideoSpeed(
                getApplication(),
                uri,
                outFile,
                state.speedFactor,
                state.preservePitch,
                state.speedMuteAudio,
                onProg,
            )
        }
    }

    // --- Tool 3: GPU Color Grader ---
    fun selectLutPreset(preset: ColorLutPreset) {
        val params = ColorGradeParams(
            exposure = preset.exposure,
            contrast = preset.contrast,
            saturation = preset.saturation,
            temperature = preset.temperature,
            vignette = preset.vignette,
            tealOrange = preset.tealOrange,
        )
        _uiState.update { it.copy(activeLutPreset = preset, colorParams = params) }
        refreshColorPreview()
    }

    fun updateColorParams(transform: (ColorGradeParams) -> ColorGradeParams) {
        _uiState.update { it.copy(colorParams = transform(it.colorParams)) }
        refreshColorPreview()
    }

    private fun refreshColorPreview() {
        val base = _uiState.value.baseColorFrame ?: return
        val params = _uiState.value.colorParams
        colorPreviewJob?.cancel()
        colorPreviewJob = viewModelScope.launch {
            delay(80L)
            val graded = withContext(Dispatchers.Default) {
                ColorGradePreviewMath.applyColorGradeCpu(base, params)
            }
            _uiState.update { it.copy(gradedPreviewFrame = graded) }
        }
    }

    fun exportColorGradedVideo() {
        val state = _uiState.value
        val uri = state.selectedVideo?.localUri ?: return
        runExport("GPU Color Grading...", "graded_${state.activeLutPreset.name.lowercase()}") { outFile, onProg ->
            ProStudioEngine.exportColorGradedVideo(getApplication(), uri, outFile, state.colorParams, onProg)
        }
    }

    // --- Tool 4: Multi-Clip Merger ---
    fun addMergeClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val inspected = uris.mapNotNull { uri ->
                try {
                    fetcher.inspectLocalUri(getApplication(), uri)
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.update { it.copy(mergeClips = it.mergeClips + inspected, errorMessage = null) }
        }
    }

    fun moveMergeClip(index: Int, delta: Int) {
        _uiState.update { state ->
            val list = state.mergeClips.toMutableList()
            val target = index + delta
            if (index in list.indices && target in list.indices) {
                val item = list.removeAt(index)
                list.add(target, item)
            }
            state.copy(mergeClips = list)
        }
    }

    fun removeMergeClip(index: Int) {
        _uiState.update { state ->
            val list = state.mergeClips.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(mergeClips = list)
        }
    }

    fun toggleMergeNormalize1080p() = _uiState.update { it.copy(mergeNormalize1080p = !it.mergeNormalize1080p) }

    fun exportMergedVideo() {
        val state = _uiState.value
        val uris = state.mergeClips.mapNotNull { it.localUri }
        if (uris.size < 2) {
            _uiState.update { it.copy(errorMessage = "Add at least 2 video clips to merge.") }
            return
        }
        runExport("Stitching ${uris.size} clips...", "merged_${uris.size}clips") { outFile, onProg ->
            ProStudioEngine.mergeVideos(getApplication(), uris, outFile, state.mergeNormalize1080p, onProg)
        }
    }

    // --- Tool 5: Aspect Ratio Reframer & Rotate/Flip ---
    fun selectAspectPreset(preset: AspectRatioPreset) = _uiState.update { it.copy(aspectPreset = preset) }
    fun setCropToFill(crop: Boolean) = _uiState.update { it.copy(cropToFill = crop) }
    fun rotateBy90() = _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) }
    fun toggleFlipHorizontal() = _uiState.update { it.copy(flipHorizontal = !it.flipHorizontal) }
    fun toggleFlipVertical() = _uiState.update { it.copy(flipVertical = !it.flipVertical) }

    fun exportReframedVideo() {
        val state = _uiState.value
        val uri = state.selectedVideo?.localUri ?: return
        runExport("Reframing to ${state.aspectPreset.label}...", "reframed_${state.aspectPreset.name.lowercase()}") { outFile, onProg ->
            ProStudioEngine.reframeAndTransformVideo(
                getApplication(),
                uri,
                outFile,
                state.aspectPreset,
                state.cropToFill,
                state.rotationDegrees,
                state.flipHorizontal,
                state.flipVertical,
                onProg,
            )
        }
    }

    // --- Tool 6: Brand Watermark ---
    fun updateWatermark(transform: (WatermarkConfig) -> WatermarkConfig) {
        _uiState.update { it.copy(watermarkConfig = transform(it.watermarkConfig)) }
    }

    fun exportWatermarkedVideo() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val uri = video.localUri ?: return
        runExport("Burning watermark overlay...", "watermarked") { outFile, onProg ->
            ProStudioEngine.burnWatermarkIntoVideo(
                getApplication(),
                uri,
                outFile,
                video.width,
                video.height,
                state.watermarkConfig,
                onProg,
            )
        }
    }

    // --- Tool 7: Audio DSP Booster, Limiter & Fade ---
    fun updateAudioDsp(transform: (AudioDspConfig) -> AudioDspConfig) {
        _uiState.update { it.copy(audioDspConfig = transform(it.audioDspConfig)) }
    }

    fun exportAudioDspVideo() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val uri = video.localUri ?: return
        val cfg = state.audioDspConfig.copy(totalDurationMs = video.durationMs)
        runExport("Applying PCM Audio DSP...", "audio_dsp") { outFile, onProg ->
            ProStudioEngine.processVideoAudioDsp(getApplication(), uri, outFile, cfg, onProg)
        }
    }

    // --- Tool 8: Frame Grabber & Contact Sheet ---
    fun setGrabPositionMs(ms: Long) {
        val dur = _uiState.value.selectedVideo?.durationMs ?: 0L
        val clamped = ms.coerceIn(0L, dur.coerceAtLeast(0L))
        _uiState.update { it.copy(grabPositionMs = clamped) }
        refreshGrabbedFrame(clamped, debounceMs = 130L)
    }

    fun setContactSheetGrid(grid: Int) = _uiState.update { it.copy(contactSheetGrid = grid.coerceIn(2, 4)) }

    private fun refreshGrabbedFrame(ms: Long, debounceMs: Long) {
        val uri = _uiState.value.selectedVideo?.localUri ?: return
        grabPreviewJob?.cancel()
        grabPreviewJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val bmp = ProStudioEngine.extractFrameAtMs(getApplication(), uri, ms)
            if (bmp != null) {
                _uiState.update { it.copy(grabbedFrame = bmp) }
            }
        }
    }

    fun saveGrabbedFramePng() {
        val bmp = _uiState.value.grabbedFrame ?: return
        val pos = _uiState.value.grabPositionMs
        saveBitmapToGallery(bmp, "clipper_frame_${pos}ms.png", "Saving lossless PNG frame...")
    }

    fun generateAndSaveContactSheet() {
        val video = _uiState.value.selectedVideo ?: return
        val uri = video.localUri ?: return
        val grid = _uiState.value.contactSheetGrid
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progress = 0.35f,
                    progressLabel = "Building ${grid}×${grid} storyboard contact sheet...",
                    errorMessage = null,
                    savedResult = null,
                )
            }
            try {
                val sheet = ProStudioEngine.generateContactSheet(getApplication(), uri, video.durationMs, grid)
                _uiState.update { it.copy(contactSheetPreview = sheet, progress = 0.8f) }
                val saved = saveBitmapInternal(sheet, "clipper_storyboard_${grid}x${grid}_${System.currentTimeMillis()}.png")
                _uiState.update { it.copy(isProcessing = false, progress = 1f, progressLabel = "", savedResult = saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = e.message ?: "Contact sheet failed.") }
            }
        }
    }

    // --- Tool 9: Video Looper ---
    fun setLoopCount(count: Int) = _uiState.update { it.copy(loopCount = count.coerceIn(2, 5)) }
    fun setLoopSpeed(speed: Float) = _uiState.update { it.copy(loopSpeed = speed.coerceIn(0.5f, 3.0f)) }
    fun toggleLoopMute() = _uiState.update { it.copy(loopMuteAudio = !it.loopMuteAudio) }

    fun exportLoopedVideo() {
        val state = _uiState.value
        val uri = state.selectedVideo?.localUri ?: return
        runExport("Rendering ${state.loopCount}× seamless loop...", "loop_${state.loopCount}x") { outFile, onProg ->
            ProStudioEngine.createLoopedVideo(
                getApplication(),
                uri,
                outFile,
                state.loopCount,
                state.loopSpeed,
                state.loopMuteAudio,
                onProg,
            )
        }
    }

    // --- Tool 10: Forensic Inspector & Privacy Metadata Stripper ---
    fun exportPrivacyStrippedVideo() {
        val uri = _uiState.value.selectedVideo?.localUri ?: return
        runExport("Lossless remuxing & stripping GPS/EXIF...", "privacy_clean") { outFile, onProg ->
            ProStudioEngine.stripMetadataLosslessRemux(getApplication(), uri, outFile, onProg)
        }
    }

    private fun runExport(
        initialLabel: String,
        fileSuffix: String,
        block: suspend (File, (Float) -> Unit) -> File,
    ) {
        val context = getApplication<Application>()
        val baseName = _uiState.value.selectedVideo?.fileName?.substringBeforeLast('.')?.ifBlank { "clipper" } ?: "clipper"
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progress = 0f,
                    progressLabel = initialLabel,
                    errorMessage = null,
                    savedResult = null,
                )
            }
            try {
                val outFile = File(context.cacheDir, "studio_${fileSuffix}_${System.currentTimeMillis()}.mp4")
                block(outFile) { p ->
                    _uiState.update { s -> s.copy(progress = p, progressLabel = "$initialLabel (${(p * 100).toInt()}%)") }
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaStoreSaver.saveFileToGallery(
                        context = context,
                        sourceFile = outFile,
                        displayName = "${baseName}_${fileSuffix}.mp4",
                        mimeType = "video/mp4",
                        category = SaveCategory.VIDEO,
                    )
                }
                outFile.delete()
                _uiState.update {
                    it.copy(isProcessing = false, progress = 1f, progressLabel = "", savedResult = saved)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Export failed.")
                }
            }
        }
    }

    private fun saveBitmapToGallery(bmp: Bitmap, fileName: String, label: String) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progress = 0.5f, progressLabel = label, errorMessage = null, savedResult = null) }
            try {
                val saved = saveBitmapInternal(bmp, fileName)
                _uiState.update { it.copy(isProcessing = false, progress = 1f, progressLabel = "", savedResult = saved) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = e.message ?: "Failed to save image.") }
            }
        }
    }

    private suspend fun saveBitmapInternal(bmp: Bitmap, fileName: String): SavedMediaResult = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val temp = File(context.cacheDir, "img_${System.currentTimeMillis()}.png")
        FileOutputStream(temp).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        val res = MediaStoreSaver.saveFileToGallery(
            context = context,
            sourceFile = temp,
            displayName = fileName,
            mimeType = "image/png",
            category = SaveCategory.IMAGE,
        )
        temp.delete()
        res
    }

    private fun scaleDownForPreview(bmp: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxEdge) return bmp
        val scale = maxEdge.toFloat() / longest.toFloat()
        val w = (bmp.width * scale).toInt().coerceAtLeast(32)
        val h = (bmp.height * scale).toInt().coerceAtLeast(32)
        return Bitmap.createScaledBitmap(bmp, w, h, true).also {
            if (it !== bmp) bmp.recycle()
        }
    }
}
