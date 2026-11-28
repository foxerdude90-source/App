package com.example.ui.features.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.media.MediaStoreSaver
import com.example.core.music.AudioDspFilterMode
import com.example.core.music.DetectedAudioFileInfo
import com.example.core.music.IdentifiedSong
import com.example.core.music.MusicRecognitionApiClient
import com.example.core.music.MusicalTelemetry
import com.example.core.music.SpectrogramFrameVisual
import com.example.ui.components.StudioCard
import com.example.ui.components.TelemetryChip
import com.example.ui.theme.ClipperAmber
import com.example.ui.theme.ClipperAmberSoft
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanBright
import com.example.ui.theme.ClipperCyanSoft
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.ClipperMagentaSoft
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicDetectorScreen(
    viewModel: MusicDetectorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showVaultSaveForm by remember { mutableStateOf(false) }
    var vaultTitleInput by remember { mutableStateOf("") }
    var vaultArtistInput by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startLiveMicDetection()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(context, uri)
            viewModel.loadLocalFileAndScan(uri, name)
        }
    }

    val speechLyricLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        val spoken = res.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrBlank()) {
            viewModel.searchByVocalLyrics(spoken)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioSnow),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("music_detector_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Input Source Segmented Selector
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MusicInputMode.entries.forEach { mode ->
                            val selected = state.inputMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .testTag("music_mode_${mode.name.lowercase(Locale.US)}")
                                    .clickable(enabled = !state.isAnalyzing && !state.isListeningMic) {
                                        viewModel.selectInputMode(mode)
                                    },
                                color = if (selected) ClipperIndigo else StudioSnow,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ClipperIndigo else StudioBorder,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 11.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = when (mode) {
                                            MusicInputMode.LIVE_MIC -> Icons.Outlined.Mic
                                            MusicInputMode.SOCIAL_URL -> Icons.Outlined.Link
                                            MusicInputMode.LOCAL_FILE -> Icons.Outlined.AudioFile
                                        },
                                        contentDescription = mode.label,
                                        tint = if (selected) StudioWhite else InkSlate,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selected) StudioWhite else InkObsidian,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Mode-Specific Action Panel
                    when (state.inputMode) {
                        MusicInputMode.LIVE_MIC -> {
                            LiveMicCapturePanel(
                                isListening = state.isListeningMic,
                                isAnalyzing = state.isAnalyzing,
                                elapsedSec = state.micElapsedSec,
                                targetDurationSec = state.listenDurationSec,
                                rmsLevel = state.liveRmsLevel,
                                spectrumBars = state.liveSpectrumBars,
                                onDurationChange = viewModel::setListenDurationSec,
                                onStartOrStop = {
                                    if (state.isListeningMic || state.isAnalyzing) {
                                        viewModel.cancelActiveScan()
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            viewModel.startLiveMicDetection()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                            )
                        }

                        MusicInputMode.SOCIAL_URL -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.urlInput,
                                    onValueChange = viewModel::onUrlInputChanged,
                                    label = { Text("YouTube, Shorts, Reels, TikTok or X URL") },
                                    placeholder = { Text("https://youtube.com/shorts/...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ClipperIndigo,
                                        unfocusedBorderColor = StudioBorder,
                                        focusedContainerColor = StudioSnow,
                                        unfocusedContainerColor = StudioSnow,
                                    ),
                                    trailingIcon = {
                                        if (state.urlInput.isNotBlank()) {
                                            IconButton(onClick = { viewModel.onUrlInputChanged("") }) {
                                                Icon(Icons.Outlined.Close, contentDescription = "Clear URL")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("music_url_input"),
                                )
                                Button(
                                    onClick = {
                                        if (state.isAnalyzing) viewModel.cancelActiveScan()
                                        else viewModel.scanSocialUrl()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (state.isAnalyzing) ClipperRose else ClipperIndigo,
                                        contentColor = StudioWhite,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("music_scan_url_button"),
                                ) {
                                    Icon(
                                        imageVector = if (state.isAnalyzing) Icons.Outlined.Stop else Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (state.isAnalyzing) "Stop Scan" else "Detect Audio File & Identify Song")
                                }
                            }
                        }

                        MusicInputMode.LOCAL_FILE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Pick any local video or audio clip (MP4, M4A, MP3, WAV, OGG) and target any 2–4 second musical or vocal snippet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkSlate,
                                )
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("audio/*", "video/*"))
                                    },
                                    enabled = !state.isAnalyzing,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ClipperIndigo,
                                        contentColor = StudioWhite,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("music_pick_local_file_button"),
                                ) {
                                    Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(19.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick Audio or Video File")
                                }
                            }
                        }
                    }

                    // Active Status Banner
                    AnimatedVisibility(visible = state.isAnalyzing && state.statusLine.isNotBlank()) {
                        Surface(
                            color = ClipperIndigoSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ClipperIndigo.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ClipperIndigo,
                                )
                                Text(
                                    text = state.statusLine,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperIndigo,
                                )
                            }
                        }
                    }

                    // Error Banner
                    state.errorMessage?.let { err ->
                        Surface(
                            color = ClipperRoseSoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ClipperRose.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ClipperRose, modifier = Modifier.size(18.dp))
                                Text(err, style = MaterialTheme.typography.bodySmall, color = ClipperRose)
                            }
                        }
                    }
                }
            }

            // 2. Detected Embedded Audio File Status Card (Explicitly shows "No audio file detected" when none!)
            state.detectedAudioFile?.let { audioInfo ->
                item {
                    DetectedAudioTrackBanner(audioInfo = audioInfo, sourceTitle = state.loadedSourceTitle)
                }
            }

            // 3. Real-Time STFT Spectrogram & Shazam Constellation Landmarks Visualizer
            if (state.spectrogram != null || state.isListeningMic) {
                item {
                    SpectrogramConstellationCard(
                        spectrogram = state.spectrogram,
                        liveBars = state.liveSpectrumBars,
                        isLive = state.isListeningMic,
                        telemetry = state.telemetry,
                    )
                }
            }

            // 4. DSP Noise Reduction, Vocal/Instrumental Isolator & 2–4s Peak Window Lab
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = ClipperCyan, modifier = Modifier.size(19.dp))
                            Text(
                                text = "DSP Noise & Vocal Isolator Lab",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = InkObsidian,
                            )
                        }
                        Surface(color = ClipperCyanSoft, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "2–4s ENGINE",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                color = ClipperCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AudioDspFilterMode.entries.forEach { mode ->
                            val selected = state.selectedDspMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectDspMode(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ClipperIndigoSoft,
                                    selectedLabelColor = ClipperIndigo,
                                ),
                                modifier = Modifier.testTag("music_dsp_${mode.name.lowercase(Locale.US)}"),
                            )
                        }
                    }

                    Text(
                        text = state.selectedDspMode.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                    )

                    if (state.hasLoadedAudio && state.loadedDurationSec > 2.1f) {
                        HorizontalDivider(color = StudioBorder)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Target Window: ${String.format(Locale.US, "%.1f", state.windowStartSec)}s – ${
                                        String.format(Locale.US, "%.1f", (state.windowStartSec + state.windowDurationSec).coerceAtMost(state.loadedDurationSec))
                                    }s (${String.format(Locale.US, "%.1f", state.windowDurationSec)}s)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = InkObsidian,
                                )
                                Surface(
                                    color = ClipperAmberSoft,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .testTag("music_auto_best_window_button")
                                        .clickable { viewModel.autoSelectBestMusicalWindow() },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = ClipperAmber, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Auto Best 3.5s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ClipperAmber,
                                        )
                                    }
                                }
                            }

                            val maxStart = (state.loadedDurationSec - 2.0f).coerceAtLeast(0.1f)
                            Slider(
                                value = state.windowStartSec.coerceIn(0f, maxStart),
                                onValueChange = viewModel::setWindowStartSec,
                                valueRange = 0f..maxStart,
                                colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::toggleDspAudioPreview,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_preview_cleaned_audio_button"),
                                ) {
                                    Icon(
                                        imageVector = if (state.isPlayingDspPreview) Icons.Outlined.Stop else Icons.Outlined.Headphones,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (state.isPlayingDspPreview) "Stop Audio" else "Hear Cleaned")
                                }
                                Button(
                                    onClick = viewModel::rescanSelectedWindow,
                                    enabled = !state.isAnalyzing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ClipperCyan,
                                        contentColor = StudioWhite,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_rescan_window_button"),
                                ) {
                                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-Scan Clip")
                                }
                            }
                        }
                    }

                    // Vocal Lyric Phrase-to-Song Rescue Bar (for buzzy/noisy vocal snippets)
                    HorizontalDivider(color = StudioBorder)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Vocal Lyric Match (for noisy / buzzy vocals)",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkObsidian,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.lyricInput,
                                onValueChange = viewModel::onLyricInputChanged,
                                placeholder = { Text("Type or dictate heard lyrics...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ClipperIndigo,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedContainerColor = StudioSnow,
                                    unfocusedContainerColor = StudioSnow,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("music_lyric_input"),
                            )
                            Surface(
                                color = ClipperMagentaSoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ClipperMagenta.copy(alpha = 0.3f)),
                            ) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sing or speak a lyric line...")
                                        }
                                        runCatching { speechLyricLauncher.launch(intent) }
                                    },
                                    modifier = Modifier.testTag("music_dictate_lyrics_button"),
                                ) {
                                    Icon(Icons.Outlined.RecordVoiceOver, contentDescription = "Dictate lyrics", tint = ClipperMagenta)
                                }
                            }
                            Surface(
                                color = ClipperIndigo,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                IconButton(
                                    onClick = { viewModel.searchByVocalLyrics() },
                                    modifier = Modifier.testTag("music_search_lyrics_button"),
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Match lyrics", tint = StudioWhite)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Identified Song Hero Card (Artwork + 30s Preview + BPM/Key Telemetry + Synced Lyrics + External Links + Save to Vault)
            state.identifiedSong?.let { song ->
                item {
                    IdentifiedSongHeroCard(
                        song = song,
                        telemetry = song.telemetry ?: state.telemetry,
                        isPlayingPreview = state.playingPreviewSongId == song.id,
                        showLyrics = state.showLyricsExpanded,
                        canSaveToVault = state.lastHashes.isNotEmpty(),
                        onTogglePreview = { viewModel.toggleSongPreviewPlayback(song) },
                        onToggleLyrics = viewModel::toggleLyricsExpanded,
                        onCopyLyrics = { text ->
                            clipboard.setText(AnnotatedString(text))
                        },
                        onOpenUrl = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        onSaveToVault = {
                            viewModel.saveCurrentAudioToVault(song.title, song.artist)
                        },
                    )
                }
            }

            // Diagnostic / Honest Non-Match Feedback + Manual Vault Indexing Option
            if (state.identifiedSong == null && !state.diagnosticMessage.isNullOrBlank()) {
                item {
                    StudioCard(
                        containerColor = ClipperAmberSoft,
                        borderColor = ClipperAmber.copy(alpha = 0.35f),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = state.diagnosticMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkObsidian,
                            )
                            if (state.lastHashes.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        vaultTitleInput = state.loadedSourceTitle
                                        vaultArtistInput = ""
                                        showVaultSaveForm = !showVaultSaveForm
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ClipperAmber.copy(alpha = 0.5f)),
                                ) {
                                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = ClipperAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Index Into Offline Fingerprint Vault", color = ClipperAmber)
                                }
                            }
                        }
                    }
                }
            }

            // Inline Custom Offline Vault Save Form
            if (showVaultSaveForm && state.lastHashes.isNotEmpty()) {
                item {
                    StudioCard {
                        Text(
                            text = "Save Custom Track to Offline Vault (${state.lastHashes.size} landmarks)",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        OutlinedTextField(
                            value = vaultTitleInput,
                            onValueChange = { vaultTitleInput = it },
                            label = { Text("Song / Audio Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = vaultArtistInput,
                            onValueChange = { vaultArtistInput = it },
                            label = { Text("Artist / Creator") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                viewModel.saveCurrentAudioToVault(vaultTitleInput, vaultArtistInput)
                                showVaultSaveForm = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperEmerald, contentColor = StudioWhite),
                        ) {
                            Text("Save Fingerprint Offline")
                        }
                    }
                }
            }

            // Vault Notice Banner
            state.vaultNotice?.let { notice ->
                item {
                    StudioCard(
                        containerColor = ClipperEmeraldSoft,
                        borderColor = ClipperEmerald.copy(alpha = 0.35f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(18.dp))
                            Text(notice, style = MaterialTheme.typography.bodySmall, color = InkObsidian)
                        }
                    }
                }
            }

            // Alternate Candidate Matches
            if (state.alternateMatches.isNotEmpty()) {
                item {
                    StudioCard {
                        Text(
                            text = "Alternate Matches",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        state.alternateMatches.forEach { alt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectAlternateMatch(alt) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(alt.title, style = MaterialTheme.typography.titleSmall, color = InkObsidian, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(alt.artist, style = MaterialTheme.typography.bodySmall, color = InkSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = "${alt.confidencePercent}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperIndigo,
                                )
                            }
                        }
                    }
                }
            }

            // 6. Offline Acoustic Fingerprint Vault Summary Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(19.dp))
                            Text(
                                text = "Offline Fingerprint Vault (${state.vaultTracks.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = InkObsidian,
                            )
                        }
                        if (state.lastHashes.isNotEmpty()) {
                            Text(
                                text = "+ Save Current",
                                style = MaterialTheme.typography.labelMedium,
                                color = ClipperEmerald,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        vaultTitleInput = state.identifiedSong?.title ?: state.loadedSourceTitle
                                        vaultArtistInput = state.identifiedSong?.artist.orEmpty()
                                        showVaultSaveForm = !showVaultSaveForm
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (state.vaultTracks.isEmpty()) {
                        Text(
                            text = "Index any custom, unreleased, or favorite track here. Clipper stores its 2D spectral constellation hashes to identify it in <50ms offline from any noisy 2–4s sample.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    } else {
                        state.vaultTracks.take(6).forEach { vt ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(vt.title, style = MaterialTheme.typography.titleSmall, color = InkObsidian, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${vt.artist} • ${vt.hashes.size} landmarks",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                        color = InkSlate,
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteVaultTrack(vt.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove from vault", tint = InkSlate, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 7. Detection History Card
            if (state.history.isNotEmpty()) {
                item {
                    StudioCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(19.dp))
                            Text(
                                text = "Recent Song Detections (${state.history.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = InkObsidian,
                            )
                        }
                        state.history.take(8).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectAlternateMatch(item) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ClipperIndigoSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!item.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.artworkUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Album, contentDescription = null, tint = ClipperIndigo)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = InkObsidian, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${item.artist} • ${item.matchSource.badgeLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSlate,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!item.previewAudioUrl.isNullOrBlank()) {
                                    IconButton(onClick = { viewModel.toggleSongPreviewPlayback(item) }) {
                                        Icon(
                                            imageVector = if (state.playingPreviewSongId == item.id) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                            contentDescription = "Play preview",
                                            tint = ClipperIndigo,
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteHistoryItem(item.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete history", tint = InkSlate, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMicCapturePanel(
    isListening: Boolean,
    isAnalyzing: Boolean,
    elapsedSec: Float,
    targetDurationSec: Float,
    rmsLevel: Float,
    spectrumBars: FloatArray,
    onDurationChange: (Float) -> Unit,
    onStartOrStop: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "mic_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "mic_pulse_scale",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(126.dp)
                .testTag("music_live_mic_button")
                .clip(CircleShape)
                .clickable(onClick = onStartOrStop),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseR = size.minDimension * 0.36f
                val activeScale = if (isListening) pulse + rmsLevel * 0.25f else 1f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (isListening) ClipperCyanBright else ClipperIndigo).copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    ),
                    radius = (baseR * activeScale * 1.35f).coerceAtMost(size.minDimension * 0.5f),
                )
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = if (isListening) listOf(ClipperCyan, ClipperIndigo) else listOf(ClipperIndigo, ClipperMagenta),
                    ),
                    radius = baseR,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start listening",
                    tint = StudioWhite,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = if (isListening) String.format(Locale.US, "%.1fs", elapsedSec) else "DETECT",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                    color = StudioWhite,
                )
            }
        }

        Text(
            text = when {
                isListening -> "Capturing ambient audio — handles noisy rooms, intros & buzzy vocals..."
                isAnalyzing -> "Running STFT constellation fingerprint scan..."
                else -> "Tap to identify any song playing around you from a 3–10s sample"
            },
            style = MaterialTheme.typography.bodySmall,
            color = InkSlate,
        )

        if (!isListening && !isAnalyzing) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Capture Length", style = MaterialTheme.typography.labelSmall, color = InkSlate)
                    Text(
                        text = "${targetDurationSec.toInt()} seconds",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = ClipperIndigo,
                    )
                }
                Slider(
                    value = targetDurationSec,
                    onValueChange = onDurationChange,
                    valueRange = 3f..10f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
                )
            }
        }
    }
}

@Composable
private fun DetectedAudioTrackBanner(
    audioInfo: DetectedAudioFileInfo,
    sourceTitle: String,
) {
    val hasAudio = audioInfo.hasAudioTrack
    StudioCard(
        containerColor = if (hasAudio) ClipperEmeraldSoft else ClipperRoseSoft,
        borderColor = if (hasAudio) ClipperEmerald.copy(alpha = 0.35f) else ClipperRose.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (hasAudio) "music_detected_audio_file_banner" else "music_no_audio_file_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hasAudio) ClipperEmerald.copy(alpha = 0.14f) else ClipperRose.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hasAudio) Icons.Outlined.AudioFile else Icons.Outlined.MusicOff,
                    contentDescription = null,
                    tint = if (hasAudio) ClipperEmerald else ClipperRose,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (hasAudio) "Embedded Audio File Detected" else "No audio file detected",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (hasAudio) InkObsidian else ClipperRose,
                )
                Text(
                    text = if (hasAudio) {
                        val sizeStr = if (audioInfo.fileSizeBytes > 0L) {
                            " • ${MediaStoreSaver.formatByteSize(audioInfo.fileSizeBytes)}"
                        } else ""
                        "${audioInfo.summaryLine()}$sizeStr"
                    } else {
                        "This video/short contains no audio track."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                    color = if (hasAudio) ClipperEmerald else ClipperRose,
                )
                if (sourceTitle.isNotBlank()) {
                    Text(
                        text = sourceTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpectrogramConstellationCard(
    spectrogram: SpectrogramFrameVisual?,
    liveBars: FloatArray,
    isLive: Boolean,
    telemetry: MusicalTelemetry?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B1120),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("music_spectrogram_card"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isLive) "LIVE 32-BAND FFT SPECTRUM" else "2D STFT CONSTELLATION LANDMARKS",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                    color = Color(0xFF38BDF8),
                )
                telemetry?.let { t ->
                    Text(
                        text = "${t.constellationPeakCount} peaks • ${t.constellationHashCount} hashes",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = Color(0xFF94A3B8),
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060A14)),
            ) {
                if (isLive) {
                    val count = liveBars.size.coerceAtLeast(1)
                    val gap = 4f
                    val barW = ((size.width - gap * (count + 1)) / count).coerceAtLeast(2f)
                    for (i in 0 until count) {
                        val v = liveBars[i].coerceIn(0.03f, 1f)
                        val h = size.height * v
                        val x = gap + i * (barW + gap)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF22D3EE), Color(0xFF6366F1)),
                                startY = size.height - h,
                                endY = size.height,
                            ),
                            topLeft = Offset(x, size.height - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(4f, 4f),
                        )
                    }
                } else if (spectrogram != null && spectrogram.timeColumns.isNotEmpty()) {
                    val cols = spectrogram.timeColumns.size
                    val rows = spectrogram.timeColumns.first().size
                    val cellW = size.width / cols
                    val cellH = size.height / rows

                    // Draw real STFT heatmap cells
                    for (c in 0 until cols) {
                        val col = spectrogram.timeColumns[c]
                        for (r in 0 until rows) {
                            val mag = col[r].coerceIn(0f, 1f)
                            if (mag < 0.08f) continue
                            val cellColor = when {
                                mag > 0.72f -> Color(0xFF22D3EE).copy(alpha = 0.85f)
                                mag > 0.42f -> Color(0xFF6366F1).copy(alpha = 0.65f)
                                else -> Color(0xFF312E81).copy(alpha = 0.45f)
                            }
                            drawRect(
                                color = cellColor,
                                topLeft = Offset(c * cellW, size.height - (r + 1) * cellH),
                                size = Size(cellW + 0.5f, cellH + 0.5f),
                            )
                        }
                    }

                    // Draw Shazam anchor-target constellation lines
                    for (pair in spectrogram.pairsNorm) {
                        drawLine(
                            color = Color(0xFFFACC15).copy(alpha = 0.35f),
                            start = Offset(pair.t1Norm * size.width, size.height * (1f - pair.f1Norm)),
                            end = Offset(pair.t2Norm * size.width, size.height * (1f - pair.f2Norm)),
                            strokeWidth = 1.4f,
                        )
                    }

                    // Draw spectral peak stars
                    for ((tx, fy) in spectrogram.peaksNorm) {
                        drawCircle(
                            color = Color(0xFFFDE047),
                            radius = 3.2f,
                            center = Offset(tx * size.width, size.height * (1f - fy)),
                        )
                    }
                }
            }

            telemetry?.let { t ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (t.bpm > 0f) {
                        DarkTelemetryPill("BPM", String.format(Locale.US, "%.1f", t.bpm), Color(0xFF38BDF8))
                    }
                    DarkTelemetryPill("KEY", "${t.musicalKey} (${t.camelotCode})", Color(0xFFA78BFA))
                    DarkTelemetryPill("LUFS", String.format(Locale.US, "%.1f dB", t.loudnessLufs), Color(0xFF34D399))
                    DarkTelemetryPill("SNR", String.format(Locale.US, "%.1f dB", t.estimatedSnrDb), Color(0xFFFBBF24))
                }
            }
        }
    }
}

@Composable
private fun DarkTelemetryPill(label: String, value: String, accent: Color) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = Color(0xFF94A3B8),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentifiedSongHeroCard(
    song: IdentifiedSong,
    telemetry: MusicalTelemetry?,
    isPlayingPreview: Boolean,
    showLyrics: Boolean,
    canSaveToVault: Boolean,
    onTogglePreview: () -> Unit,
    onToggleLyrics: () -> Unit,
    onCopyLyrics: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSaveToVault: () -> Unit,
) {
    StudioCard(
        borderColor = ClipperIndigo.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("music_identified_song_card"),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ClipperIndigoSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (!song.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Outlined.Album, contentDescription = null, tint = ClipperIndigo, modifier = Modifier.size(36.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(color = ClipperEmeraldSoft, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = "${song.matchSource.badgeLabel} • ${song.confidencePercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = ClipperEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkObsidian,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ClipperIndigo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaParts = listOfNotNull(
                    song.album,
                    song.releaseYear,
                    song.genre,
                    song.matchedOffsetSec?.let { "at ${String.format(Locale.US, "%.1f", it)}s" },
                )
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Musical Telemetry Chips
        telemetry?.let { t ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (t.bpm > 0f) {
                    TelemetryChip("Tempo", "${String.format(Locale.US, "%.0f", t.bpm)} BPM", ClipperCyan, ClipperCyanSoft)
                }
                TelemetryChip("Key", "${t.musicalKey} • ${t.camelotCode}", ClipperMagenta, ClipperMagentaSoft)
                TelemetryChip("Loudness", "${String.format(Locale.US, "%.1f", t.loudnessLufs)} LUFS", ClipperEmerald, ClipperEmeraldSoft)
            }
        }

        // Action Row: 30s Preview + Lyrics + Save to Offline Vault
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!song.previewAudioUrl.isNullOrBlank()) {
                Button(
                    onClick = onTogglePreview,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingPreview) ClipperRose else ClipperIndigo,
                        contentColor = StudioWhite,
                    ),
                    modifier = Modifier.testTag("music_play_preview_button"),
                ) {
                    Icon(
                        imageVector = if (isPlayingPreview) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (isPlayingPreview) "Pause 30s" else "30s Preview")
                }
            }

            if (song.syncedLyrics.isNotEmpty() || !song.plainLyrics.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onToggleLyrics,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.testTag("music_toggle_lyrics_button"),
                ) {
                    Icon(Icons.Outlined.Lyrics, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (showLyrics) "Hide Lyrics" else "Synced Lyrics")
                }
            }

            if (canSaveToVault) {
                OutlinedButton(
                    onClick = onSaveToVault,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ClipperEmerald.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = ClipperEmerald, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Save Vault", color = ClipperEmerald)
                }
            }
        }

        // Streaming Quick-Open Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onOpenUrl(MusicRecognitionApiClient.buildYoutubeMusicSearchUrl(song)) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("YouTube Music", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = { onOpenUrl(MusicRecognitionApiClient.buildSpotifySearchUrl(song)) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Spotify", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        // Expandable Synced / Plain Lyrics Drawer
        AnimatedVisibility(
            visible = showLyrics && (song.syncedLyrics.isNotEmpty() || !song.plainLyrics.isNullOrBlank()),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                color = StudioSnow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (song.syncedLyrics.isNotEmpty()) "Time-Coded Synced Lyrics (LRCLIB)" else "Lyrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = ClipperIndigo,
                        )
                        val fullText = if (song.syncedLyrics.isNotEmpty()) {
                            song.syncedLyrics.joinToString("\n") { it.text }
                        } else song.plainLyrics.orEmpty()
                        IconButton(onClick = { onCopyLyrics(fullText) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy lyrics", modifier = Modifier.size(17.dp))
                        }
                    }

                    if (song.syncedLyrics.isNotEmpty()) {
                        song.syncedLyrics.take(24).forEach { line ->
                            val mm = (line.timeMs / 60000L).toInt()
                            val ss = ((line.timeMs % 60000L) / 1000L).toInt()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperCyan,
                                )
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkObsidian,
                                )
                            }
                        }
                    } else if (!song.plainLyrics.isNullOrBlank()) {
                        Text(
                            text = song.plainLyrics.lines().take(24).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkObsidian,
                        )
                    }
                }
            }
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Selected Media"
}
