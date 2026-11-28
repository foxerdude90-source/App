package com.example.ui.features.music

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.media.YoutubeDlMediaEngine
import com.example.core.music.AudioAcousticDspEngine
import com.example.core.music.AudioDspFilterMode
import com.example.core.music.AudioPcmDecoder
import com.example.core.music.ConstellationHash
import com.example.core.music.DetectedAudioFileInfo
import com.example.core.music.IdentifiedSong
import com.example.core.music.MusicHistoryVaultStore
import com.example.core.music.MusicRecognitionApiClient
import com.example.core.music.MusicalTelemetry
import com.example.core.music.SpectrogramFrameVisual
import com.example.core.music.VaultTrackEntry
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
import java.util.Locale
import java.util.UUID

enum class MusicInputMode(val label: String) {
    LIVE_MIC("Live Mic"),
    SOCIAL_URL("Social URL"),
    LOCAL_FILE("File / 2–4s Clip"),
}

data class MusicDetectorUiState(
    val inputMode: MusicInputMode = MusicInputMode.LIVE_MIC,
    val urlInput: String = "",
    val lyricInput: String = "",
    val selectedDspMode: AudioDspFilterMode = AudioDspFilterMode.CLEAN_DENOISE,
    val listenDurationSec: Float = 6.0f,
    // Loaded PCM clip window controls (for 2-4s snippet targeting)
    val hasLoadedAudio: Boolean = false,
    val loadedSourceTitle: String = "",
    val loadedDurationSec: Float = 0f,
    val windowStartSec: Float = 0f,
    val windowDurationSec: Float = 3.5f,
    val detectedAudioFile: DetectedAudioFileInfo? = null,
    // Live capture & scanning telemetry
    val isListeningMic: Boolean = false,
    val micElapsedSec: Float = 0f,
    val liveRmsLevel: Float = 0f,
    val liveSpectrumBars: FloatArray = FloatArray(32),
    val isAnalyzing: Boolean = false,
    val statusLine: String = "",
    val errorMessage: String? = null,
    // DSP preview playback & 30s track preview playback
    val isPlayingDspPreview: Boolean = false,
    val playingPreviewSongId: String? = null,
    // Results
    val identifiedSong: IdentifiedSong? = null,
    val alternateMatches: List<IdentifiedSong> = emptyList(),
    val telemetry: MusicalTelemetry? = null,
    val spectrogram: SpectrogramFrameVisual? = null,
    val lastHashes: List<ConstellationHash> = emptyList(),
    val diagnosticMessage: String? = null,
    val showLyricsExpanded: Boolean = false,
    // Offline Fingerprint Vault & History
    val history: List<IdentifiedSong> = emptyList(),
    val vaultTracks: List<VaultTrackEntry> = emptyList(),
    val vaultNotice: String? = null,
)

class MusicDetectorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        MusicDetectorUiState(
            history = MusicHistoryVaultStore.loadHistory(application),
            vaultTracks = MusicHistoryVaultStore.loadVault(application),
        ),
    )
    val uiState: StateFlow<MusicDetectorUiState> = _uiState.asStateFlow()

    // In-memory PCM buffer for instant DSP filter switching, 2–4s window slicing, AudioTrack preview & vault indexing
    private var loadedLeft: FloatArray = FloatArray(0)
    private var loadedRight: FloatArray? = null
    private var loadedSampleRateHz: Int = 16000
    private var loadedMetadataCandidates: List<MusicRecognitionApiClient.ParsedMusicCandidate> = emptyList()
    private var loadedSourceUrl: String? = null

    private var activeScanJob: Job? = null
    private var activeProcessId: String? = null
    private var dspPreviewJob: Job? = null
    private var previewPlayer: MediaPlayer? = null

    fun selectInputMode(mode: MusicInputMode) {
        _uiState.update { it.copy(inputMode = mode, errorMessage = null, vaultNotice = null) }
    }

    fun onUrlInputChanged(url: String) {
        _uiState.update { it.copy(urlInput = url, errorMessage = null) }
    }

    fun onLyricInputChanged(phrase: String) {
        _uiState.update { it.copy(lyricInput = phrase, errorMessage = null) }
    }

    fun selectDspMode(mode: AudioDspFilterMode) {
        _uiState.update { it.copy(selectedDspMode = mode) }
        if (loadedLeft.isNotEmpty() && !_uiState.value.isAnalyzing) {
            refreshSpectrogramForCurrentWindow()
        }
    }

    fun setListenDurationSec(sec: Float) {
        _uiState.update { it.copy(listenDurationSec = sec.coerceIn(3f, 12f)) }
    }

    fun setWindowStartSec(startSec: Float) {
        val maxStart = (_uiState.value.loadedDurationSec - 2.0f).coerceAtLeast(0f)
        val clamped = startSec.coerceIn(0f, maxStart)
        _uiState.update { it.copy(windowStartSec = clamped) }
    }

    fun setWindowDurationSec(durationSec: Float) {
        val clamped = durationSec.coerceIn(2.0f, 6.0f)
        _uiState.update { it.copy(windowDurationSec = clamped) }
    }

    fun toggleLyricsExpanded() {
        _uiState.update { it.copy(showLyricsExpanded = !it.showLyricsExpanded) }
    }

    fun preloadSocialUrlAndScan(url: String) {
        _uiState.update {
            it.copy(
                inputMode = MusicInputMode.SOCIAL_URL,
                urlInput = url,
                errorMessage = null,
            )
        }
        scanSocialUrl()
    }

    /**
     * Automatically finds the highest-tonality 2–4s musical window inside the loaded audio buffer.
     */
    fun autoSelectBestMusicalWindow() {
        if (loadedLeft.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val mono = if (loadedRight != null && loadedRight!!.size == loadedLeft.size) {
                FloatArray(loadedLeft.size) { i -> 0.5f * (loadedLeft[i] + loadedRight!![i]) }
            } else {
                loadedLeft
            }
            val winSec = _uiState.value.windowDurationSec.coerceIn(2.0f, 4.5f)
            val best = AudioAcousticDspEngine.selectBestMusicWindow(mono, loadedSampleRateHz, winSec)
            _uiState.update {
                it.copy(
                    windowStartSec = best.startSec,
                    windowDurationSec = best.durationSec.coerceIn(2.0f, 6.0f),
                    diagnosticMessage = "Auto-locked onto highest-musicality window at ${String.format(Locale.US, "%.1f", best.startSec)}s (${String.format(Locale.US, "%.1f", best.durationSec)}s).",
                )
            }
            refreshSpectrogramForCurrentWindow()
        }
    }

    /**
     * Mode 1: Real Live Microphone Listening via Android `AudioRecord` + live 32-band FFT visualizer.
     */
    fun startLiveMicDetection() {
        stopPreviewPlayers()
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            val targetDuration = _uiState.value.listenDurationSec
            _uiState.update {
                it.copy(
                    isListeningMic = true,
                    isAnalyzing = false,
                    micElapsedSec = 0f,
                    liveRmsLevel = 0f,
                    errorMessage = null,
                    diagnosticMessage = null,
                    identifiedSong = null,
                    alternateMatches = emptyList(),
                    statusLine = "Listening via microphone (${targetDuration.toInt()}s)...",
                )
            }
            try {
                val pcm = AudioPcmDecoder.recordMicrophoneSample(
                    durationSec = targetDuration,
                    sampleRateHz = 16000,
                    onLiveMeter = { elapsed, rms, bars ->
                        _uiState.update {
                            it.copy(
                                micElapsedSec = elapsed,
                                liveRmsLevel = rms,
                                liveSpectrumBars = bars,
                            )
                        }
                    },
                )
                loadedLeft = pcm
                loadedRight = null
                loadedSampleRateHz = 16000
                loadedMetadataCandidates = emptyList()
                loadedSourceUrl = null
                val durSec = pcm.size / 16000f

                _uiState.update {
                    it.copy(
                        isListeningMic = false,
                        isAnalyzing = true,
                        hasLoadedAudio = pcm.isNotEmpty(),
                        loadedSourceTitle = "Live Microphone Capture",
                        loadedDurationSec = durSec,
                        windowStartSec = 0f,
                        windowDurationSec = minOf(4.0f, durSec.coerceAtLeast(2.0f)),
                        detectedAudioFile = DetectedAudioFileInfo(
                            hasAudioTrack = true,
                            codec = "PCM16",
                            containerExt = "wav",
                            sampleRateHz = 16000,
                            channels = 1,
                            note = "Live Microphone Capture",
                        ),
                        statusLine = "Running noise reduction & acoustic fingerprint scan...",
                    )
                }
                runRecognitionOnCurrentBuffer(useFullBuffer = true, sourceLabel = "Live Microphone")
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isListeningMic = false, isAnalyzing = false, statusLine = "Listening stopped") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isListeningMic = false,
                        isAnalyzing = false,
                        errorMessage = t.message ?: "Microphone capture failed.",
                    )
                }
            }
        }
    }

    /**
     * Mode 2: Inspect a Social Media URL -> Detect actual audio file (or report "No audio file detected")
     * -> Extract real audio clip -> Run Noise-Resilient DSP & Multi-Stage Song Detection!
     */
    fun scanSocialUrl() {
        val rawUrl = _uiState.value.urlInput.trim()
        if (rawUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Paste a YouTube, Shorts, Reels, TikTok, or social video link first.") }
            return
        }
        stopPreviewPlayers()
        activeScanJob?.cancel()
        val procId = "clipper_music_url_${UUID.randomUUID()}"
        activeProcessId = procId

        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    isListeningMic = false,
                    errorMessage = null,
                    diagnosticMessage = null,
                    identifiedSong = null,
                    alternateMatches = emptyList(),
                    detectedAudioFile = null,
                    statusLine = "Inspecting social media streams & embedded audio tracks...",
                )
            }
            var tempAudioFile: File? = null
            try {
                val meta = YoutubeDlMediaEngine.fetchMediaMetadata(getApplication(), rawUrl)
                val primaryAudio = meta.detectedAudioStreams.firstOrNull()
                    ?: DetectedAudioFileInfo(hasAudioTrack = meta.hasAudioTrack)
                loadedSourceUrl = meta.sourceUrl
                loadedMetadataCandidates = MusicRecognitionApiClient.extractMusicCandidatesFromMetadata(
                    videoTitle = meta.title,
                    uploader = meta.uploader,
                    embeddedTrack = meta.embeddedTrack,
                    embeddedArtist = meta.embeddedArtist,
                    description = meta.description,
                )

                _uiState.update {
                    it.copy(
                        detectedAudioFile = primaryAudio,
                        loadedSourceTitle = meta.title,
                    )
                }

                if (!meta.hasAudioTrack) {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            hasLoadedAudio = false,
                            statusLine = "",
                            diagnosticMessage = "No audio file detected inside this video/short (video-only stream).",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(statusLine = "Extracting real audio clip for acoustic DSP scan...") }
                tempAudioFile = YoutubeDlMediaEngine.extractAudioSampleFile(
                    context = getApplication(),
                    originalUrl = meta.sourceUrl,
                    processId = procId,
                    maxDurationSec = 18,
                )

                _uiState.update { it.copy(statusLine = "Decoding audio stream to PCM...") }
                val decoded = AudioPcmDecoder.decodeToPcm(
                    context = getApplication(),
                    file = tempAudioFile,
                    startMs = 0L,
                    maxDurationMs = 18_000L,
                )

                if (decoded == null || !decoded.audioInfo.hasAudioTrack || decoded.left.isEmpty()) {
                    // Fall back cleanly to metadata verification if codec couldn't decode
                    val metaMatches = MusicRecognitionApiClient.quickIdentifyFromSocialMetadata(
                        videoTitle = meta.title,
                        uploader = meta.uploader,
                        embeddedTrack = meta.embeddedTrack,
                        embeddedArtist = meta.embeddedArtist,
                        description = meta.description,
                        sourceUrl = meta.sourceUrl,
                    )
                    val first = metaMatches.firstOrNull()
                    val updatedHistory = if (first != null) {
                        MusicHistoryVaultStore.saveSongToHistory(getApplication(), first)
                    } else _uiState.value.history

                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            identifiedSong = first,
                            alternateMatches = metaMatches.drop(1),
                            history = updatedHistory,
                            statusLine = "",
                            diagnosticMessage = if (first != null) {
                                "Identified from video music credits."
                            } else {
                                "Audio file detected (${primaryAudio.summaryLine()}), but PCM decode produced no samples."
                            },
                        )
                    }
                    return@launch
                }

                loadedLeft = decoded.left
                loadedRight = decoded.right
                loadedSampleRateHz = decoded.sampleRateHz
                val durSec = decoded.left.size.toFloat() / decoded.sampleRateHz.toFloat()

                _uiState.update {
                    it.copy(
                        hasLoadedAudio = true,
                        loadedDurationSec = durSec,
                        windowStartSec = 0f,
                        windowDurationSec = minOf(3.6f, durSec.coerceAtLeast(2.0f)),
                        detectedAudioFile = primaryAudio.copy(
                            sampleRateHz = if (primaryAudio.sampleRateHz > 0) primaryAudio.sampleRateHz else decoded.sampleRateHz,
                            channels = if (primaryAudio.channels > 0) primaryAudio.channels else decoded.audioInfo.channels,
                        ),
                    )
                }

                runRecognitionOnCurrentBuffer(
                    useFullBuffer = true,
                    sourceLabel = meta.extractorName ?: "Social URL",
                )
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isAnalyzing = false, statusLine = "Scan cancelled") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusLine = "",
                        errorMessage = t.message?.take(200) ?: "Could not scan social media URL.",
                    )
                }
            } finally {
                if (activeProcessId == procId) activeProcessId = null
                tempAudioFile?.delete()
            }
        }
    }

    /**
     * Mode 3: Load any Local Video or Audio File -> Detect audio track -> Decode PCM -> Scan!
     */
    fun loadLocalFileAndScan(uri: Uri, displayName: String) {
        stopPreviewPlayers()
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    inputMode = MusicInputMode.LOCAL_FILE,
                    isAnalyzing = true,
                    errorMessage = null,
                    diagnosticMessage = null,
                    identifiedSong = null,
                    alternateMatches = emptyList(),
                    statusLine = "Inspecting local media audio tracks...",
                )
            }
            try {
                val decoded = AudioPcmDecoder.decodeToPcm(
                    context = getApplication(),
                    uri = uri,
                    startMs = 0L,
                    maxDurationMs = 24_000L,
                )
                if (decoded == null || !decoded.audioInfo.hasAudioTrack || decoded.left.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            hasLoadedAudio = false,
                            detectedAudioFile = DetectedAudioFileInfo(hasAudioTrack = false),
                            statusLine = "",
                            diagnosticMessage = "No audio file detected in $displayName.",
                        )
                    }
                    return@launch
                }

                loadedLeft = decoded.left
                loadedRight = decoded.right
                loadedSampleRateHz = decoded.sampleRateHz
                loadedMetadataCandidates = emptyList()
                loadedSourceUrl = null
                val durSec = decoded.left.size.toFloat() / decoded.sampleRateHz.toFloat()

                _uiState.update {
                    it.copy(
                        hasLoadedAudio = true,
                        loadedSourceTitle = displayName,
                        loadedDurationSec = durSec,
                        windowStartSec = 0f,
                        windowDurationSec = minOf(3.5f, durSec.coerceAtLeast(2.0f)),
                        detectedAudioFile = decoded.audioInfo,
                    )
                }
                runRecognitionOnCurrentBuffer(useFullBuffer = true, sourceLabel = displayName)
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isAnalyzing = false, statusLine = "Cancelled") }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusLine = "",
                        errorMessage = t.message ?: "Failed to decode local file audio.",
                    )
                }
            }
        }
    }

    /**
     * Re-scans the exact selected 2–4s window with the user's chosen DSP filter
     * (`Noise Gate`, `Vocals Only`, `Instrumental`, `Full Mix`).
     */
    fun rescanSelectedWindow() {
        if (loadedLeft.isEmpty()) return
        stopPreviewPlayers()
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    diagnosticMessage = null,
                    statusLine = "Scanning selected ${String.format(Locale.US, "%.1f", it.windowDurationSec)}s clip with ${it.selectedDspMode.label}...",
                )
            }
            runRecognitionOnCurrentBuffer(
                useFullBuffer = false,
                sourceLabel = _uiState.value.loadedSourceTitle.ifBlank { "Clip Window" },
            )
        }
    }

    /**
     * Searches LRCLIB + Deezer/iTunes using a heard/transcribed vocal lyric line (even from noisy vocals).
     */
    fun searchByVocalLyrics(overridePhrase: String? = null) {
        val phrase = (overridePhrase ?: _uiState.value.lyricInput).trim()
        if (phrase.length < 4) {
            _uiState.update { it.copy(errorMessage = "Type or dictate at least a short lyric phrase (4+ characters).") }
            return
        }
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    lyricInput = phrase,
                    isAnalyzing = true,
                    errorMessage = null,
                    diagnosticMessage = null,
                    statusLine = "Searching LRCLIB 3M+ song vocal lyrics index...",
                )
            }
            try {
                val hits = MusicRecognitionApiClient.searchSongByLyrics(
                    lyricPhrase = phrase,
                    telemetry = _uiState.value.telemetry,
                    sourceLabel = "Vocal Lyrics",
                    sourceUrl = loadedSourceUrl,
                )
                val primary = hits.firstOrNull()?.let { MusicRecognitionApiClient.enrichSongWithFreeApis(it) }
                val updatedHistory = if (primary != null) {
                    MusicHistoryVaultStore.saveSongToHistory(getApplication(), primary)
                } else _uiState.value.history

                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusLine = "",
                        identifiedSong = primary,
                        alternateMatches = hits.drop(1),
                        history = updatedHistory,
                        diagnosticMessage = if (primary != null) {
                            "Matched song from vocal lyrics phrase."
                        } else {
                            "No lyrics matched \"$phrase\". Try a clearer 4–8 word phrase from the hook or chorus."
                        },
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusLine = "",
                        errorMessage = t.message ?: "Lyric search failed.",
                    )
                }
            }
        }
    }

    /**
     * Plays back the currently conditioned/isolated audio clip via Android `AudioTrack` so
     * the user can audibly verify noise reduction, vocal isolation, or instrumental isolation!
     */
    fun toggleDspAudioPreview() {
        if (_uiState.value.isPlayingDspPreview) {
            dspPreviewJob?.cancel()
            dspPreviewJob = null
            _uiState.update { it.copy(isPlayingDspPreview = false) }
            return
        }
        if (loadedLeft.isEmpty()) return
        stopPreviewPlayers()
        dspPreviewJob = viewModelScope.launch {
            _uiState.update { it.copy(isPlayingDspPreview = true) }
            try {
                val (sliceL, sliceR) = sliceCurrentWindow(useFullBuffer = false)
                val conditioned = withContext(Dispatchers.Default) {
                    AudioAcousticDspEngine.applyDspConditioning(
                        left = sliceL,
                        right = sliceR,
                        sampleRateHz = loadedSampleRateHz,
                        mode = _uiState.value.selectedDspMode,
                    )
                }
                AudioPcmDecoder.playPcmSamples(
                    samples = conditioned,
                    sampleRateHz = loadedSampleRateHz,
                    onFinished = {
                        _uiState.update { it.copy(isPlayingDspPreview = false) }
                    },
                )
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isPlayingDspPreview = false) }
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isPlayingDspPreview = false,
                        errorMessage = t.message ?: "Audio preview playback failed.",
                    )
                }
            }
        }
    }

    /**
     * Plays or pauses a 30-second MP3/AAC preview stream from Deezer/iTunes for any identified song.
     */
    fun toggleSongPreviewPlayback(song: IdentifiedSong) {
        val url = song.previewAudioUrl ?: return
        if (_uiState.value.playingPreviewSongId == song.id) {
            stopSongPreviewPlayer()
            return
        }
        stopPreviewPlayers()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(url)
                    setOnCompletionListener {
                        _uiState.update { it.copy(playingPreviewSongId = null) }
                        runCatching { release() }
                        previewPlayer = null
                    }
                    setOnErrorListener { mp, _, _ ->
                        _uiState.update { it.copy(playingPreviewSongId = null) }
                        runCatching { mp.release() }
                        previewPlayer = null
                        true
                    }
                    prepare()
                    start()
                }
                previewPlayer = player
                _uiState.update { it.copy(playingPreviewSongId = song.id) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        playingPreviewSongId = null,
                        errorMessage = "Preview stream unavailable: ${err.message?.take(80)}",
                    )
                }
            }
        }
    }

    /**
     * Indexes the current loaded audio's 2D Shazam-style constellation hashes into the
     * Offline Fingerprint Vault so Clipper can recognize it offline from any 2–4s noisy clip!
     */
    fun saveCurrentAudioToVault(customTitle: String, customArtist: String) {
        val hashes = _uiState.value.lastHashes
        val title = customTitle.trim().ifBlank { _uiState.value.identifiedSong?.title ?: _uiState.value.loadedSourceTitle }
        val artist = customArtist.trim().ifBlank { _uiState.value.identifiedSong?.artist ?: "Custom Audio" }
        if (hashes.isEmpty() || title.isBlank()) {
            _uiState.update { it.copy(vaultNotice = "Analyze or load an audio clip first to index its acoustic fingerprint.") }
            return
        }
        val entry = VaultTrackEntry(
            id = "vt_${UUID.randomUUID().toString().take(8)}",
            title = title,
            artist = artist,
            durationSec = _uiState.value.loadedDurationSec,
            sampleRateHz = 11025,
            createdAtEpochMs = System.currentTimeMillis(),
            hashes = hashes,
        )
        val updated = MusicHistoryVaultStore.saveTrackToVault(getApplication(), entry)
        _uiState.update {
            it.copy(
                vaultTracks = updated,
                vaultNotice = "Saved \"$title\" (${hashes.size} constellation landmarks) to Offline Vault.",
            )
        }
    }

    fun deleteHistoryItem(songId: String) {
        val updated = MusicHistoryVaultStore.deleteHistoryItem(getApplication(), songId)
        _uiState.update { it.copy(history = updated) }
    }

    fun deleteVaultTrack(trackId: String) {
        val updated = MusicHistoryVaultStore.deleteVaultTrack(getApplication(), trackId)
        _uiState.update { it.copy(vaultTracks = updated) }
    }

    fun selectAlternateMatch(song: IdentifiedSong) {
        viewModelScope.launch {
            val enriched = MusicRecognitionApiClient.enrichSongWithFreeApis(song)
            val updatedHistory = MusicHistoryVaultStore.saveSongToHistory(getApplication(), enriched)
            _uiState.update {
                it.copy(
                    identifiedSong = enriched,
                    history = updatedHistory,
                )
            }
        }
    }

    fun cancelActiveScan() {
        activeProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        activeProcessId = null
        activeScanJob?.cancel()
        activeScanJob = null
        _uiState.update { it.copy(isListeningMic = false, isAnalyzing = false, statusLine = "") }
    }

    private suspend fun runRecognitionOnCurrentBuffer(
        useFullBuffer: Boolean,
        sourceLabel: String,
    ) {
        val (sliceL, sliceR) = sliceCurrentWindow(useFullBuffer)
        val stateSnap = _uiState.value
        val result = MusicRecognitionApiClient.recognizeFromPcm(
            left = sliceL,
            right = sliceR,
            sampleRateHz = loadedSampleRateHz,
            filterMode = stateSnap.selectedDspMode,
            vaultTracks = stateSnap.vaultTracks,
            sourceLabel = sourceLabel,
            sourceUrl = loadedSourceUrl,
            windowStartSec = if (useFullBuffer) 0f else stateSnap.windowStartSec,
            metadataFallbackCandidates = loadedMetadataCandidates,
            vocalLyricHint = stateSnap.lyricInput.takeIf { it.isNotBlank() },
            onStatus = { status -> _uiState.update { it.copy(statusLine = status) } },
        )

        val updatedHistory = if (result.primarySong != null) {
            MusicHistoryVaultStore.saveSongToHistory(getApplication(), result.primarySong)
        } else {
            _uiState.value.history
        }

        _uiState.update {
            it.copy(
                isAnalyzing = false,
                statusLine = "",
                identifiedSong = result.primarySong,
                alternateMatches = result.alternates,
                telemetry = result.telemetry,
                spectrogram = result.spectrogram,
                lastHashes = result.constellationHashes,
                diagnosticMessage = result.diagnosticMessage,
                history = updatedHistory,
            )
        }
    }

    private fun refreshSpectrogramForCurrentWindow() {
        viewModelScope.launch(Dispatchers.Default) {
            val (sliceL, sliceR) = sliceCurrentWindow(useFullBuffer = false)
            if (sliceL.isEmpty()) return@launch
            val conditioned = AudioAcousticDspEngine.applyDspConditioning(
                left = sliceL,
                right = sliceR,
                sampleRateHz = loadedSampleRateHz,
                mode = _uiState.value.selectedDspMode,
            )
            val fp = AudioAcousticDspEngine.extractConstellationFingerprint(conditioned, loadedSampleRateHz)
            val tel = AudioAcousticDspEngine.analyzeMusicalTelemetry(
                samples = conditioned,
                sampleRateHz = loadedSampleRateHz,
                constellation = fp,
                windowStartSec = _uiState.value.windowStartSec,
            )
            _uiState.update {
                it.copy(
                    spectrogram = fp.spectrogram,
                    telemetry = tel,
                    lastHashes = fp.hashes,
                )
            }
        }
    }

    private fun sliceCurrentWindow(useFullBuffer: Boolean): Pair<FloatArray, FloatArray?> {
        if (loadedLeft.isEmpty()) return FloatArray(0) to null
        if (useFullBuffer) return loadedLeft to loadedRight
        val startIdx = (_uiState.value.windowStartSec * loadedSampleRateHz).toInt()
            .coerceIn(0, (loadedLeft.size - 1).coerceAtLeast(0))
        val length = (_uiState.value.windowDurationSec * loadedSampleRateHz).toInt()
            .coerceAtLeast(loadedSampleRateHz)
        val endIdx = (startIdx + length).coerceAtMost(loadedLeft.size)
        if (endIdx <= startIdx) return loadedLeft to loadedRight
        val lSlice = loadedLeft.copyOfRange(startIdx, endIdx)
        val rSlice = loadedRight?.let { r ->
            if (r.size >= endIdx) r.copyOfRange(startIdx, endIdx) else null
        }
        return lSlice to rSlice
    }

    private fun stopSongPreviewPlayer() {
        runCatching {
            previewPlayer?.stop()
            previewPlayer?.release()
        }
        previewPlayer = null
        _uiState.update { it.copy(playingPreviewSongId = null) }
    }

    private fun stopPreviewPlayers() {
        dspPreviewJob?.cancel()
        dspPreviewJob = null
        _uiState.update { it.copy(isPlayingDspPreview = false) }
        stopSongPreviewPlayer()
    }

    override fun onCleared() {
        activeProcessId?.let { YoutubeDlMediaEngine.cancelDownload(it) }
        stopPreviewPlayers()
        super.onCleared()
    }
}
