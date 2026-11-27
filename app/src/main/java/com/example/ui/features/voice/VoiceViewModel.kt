package com.example.ui.features.voice

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.MediaStoreSaver
import com.example.core.media.SaveCategory
import com.example.core.media.SavedMediaResult
import com.example.core.voice.ClonedVoiceAnalysis
import com.example.core.voice.PhrasingStyle
import com.example.core.voice.StudioRoomPreset
import com.example.core.voice.VoiceCategory
import com.example.core.voice.VoiceGenerationParams
import com.example.core.voice.VoicePresetCatalog
import com.example.core.voice.VoiceProfile
import com.example.core.voice.VoiceStudioDspEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class VoiceUiState(
    val voices: List<VoiceProfile> = VoicePresetCatalog.builtInVoices,
    val selectedCategory: VoiceCategory = VoiceCategory.ALL,
    val selectedVoiceId: String = VoicePresetCatalog.builtInVoices.first().id,
    val scriptText: String = "Welcome to Clipper Voice Studio. Adjust phrasing, reliability, and studio acoustics, or clone any voice right on your device.",
    val params: VoiceGenerationParams = VoiceGenerationParams(),
    // Voice cloning state (Mic & File Upload)
    val cloneNameInput: String = "",
    val isRecordingMic: Boolean = false,
    val micLevel: Float = 0f,
    val micElapsedSec: Int = 0,
    val isAnalyzingClone: Boolean = false,
    val lastCloneAnalysis: ClonedVoiceAnalysis? = null,
    // Speech synthesis & playback state
    val isGenerating: Boolean = false,
    val generationProgress: Float = 0f,
    val generatedWavFile: File? = null,
    val generatedDurationMs: Long = 0L,
    val generatedWaveform: List<Float> = emptyList(),
    val isPlaying: Boolean = false,
    val playbackProgress: Float = 0f,
    val savedResult: SavedMediaResult? = null,
    val errorMessage: String? = null,
) {
    val selectedVoice: VoiceProfile
        get() = voices.firstOrNull { it.id == selectedVoiceId } ?: voices.first()

    val filteredVoices: List<VoiceProfile>
        get() = when (selectedCategory) {
            VoiceCategory.ALL -> voices
            VoiceCategory.CLONED -> voices.filter { it.isCloned }
            else -> voices.filter { it.category == selectedCategory }
        }
}

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val recordingActive = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var playbackJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun selectCategory(category: VoiceCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectVoice(voiceId: String) {
        _uiState.update { it.copy(selectedVoiceId = voiceId, errorMessage = null) }
    }

    fun updateScript(text: String) {
        _uiState.update { it.copy(scriptText = text, errorMessage = null) }
    }

    fun updateCloneName(name: String) {
        _uiState.update { it.copy(cloneNameInput = name) }
    }

    fun updateSpeed(speed: Float) {
        _uiState.update { it.copy(params = it.params.copy(speed = speed.coerceIn(0.60f, 1.60f))) }
    }

    fun updatePitch(semitones: Float) {
        _uiState.update { it.copy(params = it.params.copy(pitchSemitones = semitones.coerceIn(-8f, 8f))) }
    }

    fun updateStability(stability: Float) {
        _uiState.update { it.copy(params = it.params.copy(stability = stability.coerceIn(0f, 1f))) }
    }

    fun updateClarity(clarity: Float) {
        _uiState.update { it.copy(params = it.params.copy(clarityBoost = clarity.coerceIn(0f, 1f))) }
    }

    fun updateStyle(style: Float) {
        _uiState.update { it.copy(params = it.params.copy(styleExaggeration = style.coerceIn(0f, 1f))) }
    }

    fun selectPhrasing(phrasing: PhrasingStyle) {
        _uiState.update { it.copy(params = it.params.copy(phrasing = phrasing)) }
    }

    fun selectRoom(room: StudioRoomPreset) {
        _uiState.update { it.copy(params = it.params.copy(roomPreset = room)) }
    }

    fun resetControls() {
        _uiState.update { it.copy(params = VoiceGenerationParams()) }
    }

    // -------------------------------------------------------------------------
    // REAL MICROPHONE VOICE CLONING
    // -------------------------------------------------------------------------

    fun startMicCloneRecording() {
        if (recordingActive.get()) return
        stopPlayback()
        recordingActive.set(true)
        _uiState.update {
            it.copy(
                isRecordingMic = true,
                micLevel = 0f,
                micElapsedSec = 0,
                errorMessage = null,
            )
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var sec = 0
            while (isActive && recordingActive.get() && sec < 12) {
                delay(1000L)
                sec++
                _uiState.update { it.copy(micElapsedSec = sec) }
            }
            if (recordingActive.get()) {
                recordingActive.set(false)
            }
        }

        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            try {
                val decoded = VoiceStudioDspEngine.recordMicSamplePcm(
                    isRecordingFlag = { recordingActive.get() },
                    onLevel = { lvl -> _uiState.update { it.copy(micLevel = lvl) } },
                    sampleRate = 22050,
                    maxSeconds = 12,
                )
                _uiState.update {
                    it.copy(
                        isRecordingMic = false,
                        isAnalyzingClone = true,
                        micLevel = 0f,
                    )
                }
                val customName = _uiState.value.cloneNameInput.trim()
                    .ifBlank { "My Mic Clone #${_uiState.value.voices.count { it.isCloned } + 1}" }
                val (profile, analysis) = VoiceStudioDspEngine.analyzeVoiceSamplePcm(
                    samples = decoded.samples,
                    sampleRate = decoded.sampleRate,
                    voiceName = customName,
                    sourceLabel = "Mic Capture",
                )
                _uiState.update { state ->
                    state.copy(
                        isAnalyzingClone = false,
                        voices = listOf(profile) + state.voices,
                        selectedVoiceId = profile.id,
                        selectedCategory = VoiceCategory.ALL,
                        cloneNameInput = "",
                        lastCloneAnalysis = analysis,
                    )
                }
            } catch (t: Throwable) {
                recordingActive.set(false)
                _uiState.update {
                    it.copy(
                        isRecordingMic = false,
                        isAnalyzingClone = false,
                        micLevel = 0f,
                        errorMessage = t.message ?: "Microphone capture failed. Hold and speak clearly for at least 2 seconds.",
                    )
                }
            } finally {
                timerJob?.cancel()
            }
        }
    }

    fun stopMicRecordingAndClone() {
        recordingActive.set(false)
        timerJob?.cancel()
    }

    // -------------------------------------------------------------------------
    // REAL AUDIO FILE UPLOAD VOICE CLONING
    // -------------------------------------------------------------------------

    fun cloneFromAudioUri(uri: Uri) {
        stopPlayback()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzingClone = true,
                    errorMessage = null,
                )
            }
            try {
                val decoded = VoiceStudioDspEngine.decodeAudioUriToMonoPcm(
                    context = getApplication(),
                    uri = uri,
                    maxDurationSec = 15f,
                )
                val customName = _uiState.value.cloneNameInput.trim()
                    .ifBlank { "Uploaded Clone #${_uiState.value.voices.count { it.isCloned } + 1}" }
                val (profile, analysis) = VoiceStudioDspEngine.analyzeVoiceSamplePcm(
                    samples = decoded.samples,
                    sampleRate = decoded.sampleRate,
                    voiceName = customName,
                    sourceLabel = "Audio Upload",
                )
                _uiState.update { state ->
                    state.copy(
                        isAnalyzingClone = false,
                        voices = listOf(profile) + state.voices,
                        selectedVoiceId = profile.id,
                        selectedCategory = VoiceCategory.ALL,
                        cloneNameInput = "",
                        lastCloneAnalysis = analysis,
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzingClone = false,
                        errorMessage = t.message ?: "Could not decode uploaded audio file for voice cloning.",
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // VOICE GENERATION, PLAYBACK & WAV EXPORT
    // -------------------------------------------------------------------------

    fun generateVoice() {
        val current = _uiState.value
        val script = current.scriptText.trim()
        if (script.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a script to generate speech.") }
            return
        }

        stopPlayback()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    generationProgress = 0.04f,
                    savedResult = null,
                    errorMessage = null,
                )
            }
            try {
                val outWav = File(
                    getApplication<Application>().cacheDir,
                    "voice_${current.selectedVoice.id}_${System.currentTimeMillis()}.wav",
                )
                val output = VoiceStudioDspEngine.synthesizeVoiceToWav(
                    context = getApplication(),
                    script = script,
                    profile = current.selectedVoice,
                    params = current.params,
                    outputWavFile = outWav,
                    onProgress = { p ->
                        _uiState.update { it.copy(generationProgress = p) }
                    },
                )
                // Clean previous cached wav
                current.generatedWavFile?.takeIf { it != outWav }?.delete()
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generationProgress = 1f,
                        generatedWavFile = output.wavFile,
                        generatedDurationMs = output.durationMs,
                        generatedWaveform = output.waveformBars,
                        playbackProgress = 0f,
                    )
                }
                togglePlayback()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = t.message ?: "Voice generation failed on this device.",
                    )
                }
            }
        }
    }

    fun togglePlayback() {
        val wavFile = _uiState.value.generatedWavFile ?: return
        if (!wavFile.exists()) return

        if (_uiState.value.isPlaying) {
            stopPlayback()
            return
        }

        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(wavFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            mediaPlayer = mp
            _uiState.update { it.copy(isPlaying = true, playbackProgress = 0f) }

            playbackJob?.cancel()
            playbackJob = viewModelScope.launch {
                val total = mp.duration.coerceAtLeast(1)
                while (isActive && mediaPlayer === mp && mp.isPlaying) {
                    val pos = mp.currentPosition
                    _uiState.update { it.copy(playbackProgress = (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f)) }
                    delay(45L)
                }
            }
        } catch (t: Throwable) {
            stopPlayback()
            _uiState.update { it.copy(errorMessage = t.message ?: "Unable to play generated audio.") }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
        _uiState.update { it.copy(isPlaying = false, playbackProgress = 0f) }
    }

    fun exportWavToDevice() {
        val current = _uiState.value
        val wavFile = current.generatedWavFile ?: return
        if (!wavFile.exists()) return

        viewModelScope.launch {
            try {
                val safeVoice = MediaStoreSaver.sanitizeFileName(current.selectedVoice.name)
                val saved = MediaStoreSaver.saveFileToGallery(
                    context = getApplication(),
                    sourceFile = wavFile,
                    displayName = "Clipper_${safeVoice}_${System.currentTimeMillis() % 100000}.wav",
                    mimeType = "audio/wav",
                    category = SaveCategory.AUDIO,
                )
                _uiState.update { it.copy(savedResult = saved, errorMessage = null) }
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = t.message ?: "Failed to save WAV file.") }
            }
        }
    }

    override fun onCleared() {
        recordingActive.set(false)
        stopPlayback()
        _uiState.value.generatedWavFile?.delete()
        super.onCleared()
    }
}
