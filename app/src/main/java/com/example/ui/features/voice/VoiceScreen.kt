package com.example.ui.features.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.media.MediaStoreSaver
import com.example.core.voice.PhrasingStyle
import com.example.core.voice.StudioRoomPreset
import com.example.core.voice.VoiceCategory
import com.example.core.voice.VoiceProfile
import com.example.ui.components.ChoicePill
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
import com.example.ui.components.TelemetryChip
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanSoft
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.ClipperMagentaSoft
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.cloneFromAudioUri(uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startMicCloneRecording()
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
                .widthIn(max = 740.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.voice_title),
                    subtitle = stringResource(R.string.voice_subtitle),
                    accentColor = ClipperMagenta,
                )
            }

            // Voice Library Card (16 Built-In + User Cloned Voices)
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Voice Library (${state.voices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        TelemetryChip(
                            label = "Active",
                            value = "${state.selectedVoice.name} • ${state.selectedVoice.targetF0Hz.roundToInt()} Hz",
                            accentColor = ClipperMagenta,
                            containerColor = ClipperMagentaSoft,
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(VoiceCategory.entries) { cat ->
                            ChoicePill(
                                text = cat.label,
                                selected = state.selectedCategory == cat,
                                onClick = { viewModel.selectCategory(cat) },
                                accentColor = ClipperMagenta,
                                accentSoft = ClipperMagentaSoft,
                                tag = "voice_cat_${cat.name.lowercase(Locale.US)}",
                            )
                        }
                    }

                    val displayed = state.filteredVoices
                    if (displayed.isEmpty()) {
                        Text(
                            text = "No cloned voices yet. Record your mic or upload an audio file below to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSlate,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(displayed, key = { it.id }) { voice ->
                                VoiceProfileCard(
                                    voice = voice,
                                    selected = voice.id == state.selectedVoiceId,
                                    onClick = { viewModel.selectVoice(voice.id) },
                                )
                            }
                        }
                    }
                }
            }

            // Real Voice Cloning Studio Card (Mic + Audio Upload)
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Instant Voice Cloning",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkObsidian,
                            )
                            Text(
                                text = "Extracts F0 pitch, 3-band spectral timbre & cadence from your mic or any audio file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.cloneNameInput,
                        onValueChange = viewModel::updateCloneName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_clone_name_input"),
                        placeholder = { Text("Voice name (e.g., My Studio Voice)", color = InkMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperMagenta,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSnow,
                            unfocusedContainerColor = StudioSnow,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                if (state.isRecordingMic) {
                                    viewModel.stopMicRecordingAndClone()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startMicCloneRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !state.isAnalyzingClone && !state.isGenerating,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isRecordingMic) ClipperRose else ClipperMagenta,
                                contentColor = StudioWhite,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_clone_mic_button"),
                        ) {
                            Icon(
                                imageVector = if (state.isRecordingMic) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isRecordingMic) "Stop (${state.micElapsedSec}s)" else "Record Mic")
                        }

                        OutlinedButton(
                            onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                            enabled = !state.isRecordingMic && !state.isAnalyzingClone && !state.isGenerating,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("voice_clone_upload_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AudioFile,
                                contentDescription = null,
                                tint = ClipperIndigo,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Audio")
                        }
                    }

                    if (state.isRecordingMic) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Listening... speak naturally for 3–8 seconds then tap Stop.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ClipperRose,
                            )
                            LinearProgressIndicator(
                                progress = { state.micLevel.coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = ClipperRose,
                                trackColor = ClipperRoseSoft,
                            )
                        }
                    }

                    if (state.isAnalyzingClone) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ClipperMagenta,
                            )
                            Text(
                                text = "Extracting acoustic fingerprint (F0 & 3-band timbre)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSlate,
                            )
                        }
                    }

                    state.lastCloneAnalysis?.let { dna ->
                        Surface(
                            color = ClipperMagentaSoft.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, ClipperMagenta.copy(alpha = 0.25f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Acoustic Clone Active • ${dna.voiceDescriptor}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ClipperMagenta,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TelemetryChip("Pitch", "${dna.fundamentalHz.roundToInt()} Hz", ClipperMagenta, StudioWhite)
                                    TelemetryChip("Warmth", "${(dna.lowEnergyRatio * 100).roundToInt()}%", InkObsidian, StudioWhite)
                                    TelemetryChip("Presence", "${(dna.midEnergyRatio * 100).roundToInt()}%", InkObsidian, StudioWhite)
                                    TelemetryChip("Air", "${(dna.highEnergyRatio * 100).roundToInt()}%", InkObsidian, StudioWhite)
                                }
                            }
                        }
                    }
                }
            }

            // Script Composer & Phrasing Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Script & Phrasing",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        Text(
                            text = "${state.scriptText.length} chars",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = InkSlate,
                        )
                    }

                    OutlinedTextField(
                        value = state.scriptText,
                        onValueChange = viewModel::updateScript,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_script_input"),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("Type or paste your narration script here...", color = InkMuted) },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperIndigo,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSnow,
                            unfocusedContainerColor = StudioSnow,
                        ),
                    )

                    Text(
                        text = "Phrasing & Pause Cadence",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSlate,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhrasingStyle.entries.forEach { style ->
                            ChoicePill(
                                text = style.label,
                                selected = state.params.phrasing == style,
                                onClick = { viewModel.selectPhrasing(style) },
                                tag = "voice_phrasing_${style.name.lowercase(Locale.US)}",
                            )
                        }
                    }
                }
            }

            // ElevenLabs-Style Voice Tuning Controls Card
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Voice Engineering",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkObsidian,
                        )
                        IconButton(
                            onClick = viewModel::resetControls,
                            modifier = Modifier.testTag("voice_reset_controls_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.action_reset),
                                tint = InkSlate,
                            )
                        }
                    }

                    VoiceTuningSlider(
                        label = "Speed",
                        valueText = String.format(Locale.US, "%.2f×", state.params.speed),
                        value = state.params.speed,
                        range = 0.60f..1.60f,
                        onValueChange = viewModel::updateSpeed,
                    )
                    VoiceTuningSlider(
                        label = "Pitch Shift",
                        valueText = String.format(Locale.US, "%+.1f st", state.params.pitchSemitones),
                        value = state.params.pitchSemitones,
                        range = -6f..6f,
                        onValueChange = viewModel::updatePitch,
                    )
                    VoiceTuningSlider(
                        label = "Stability / Reliability",
                        valueText = "${(state.params.stability * 100f).roundToInt()}%",
                        value = state.params.stability,
                        range = 0f..1f,
                        onValueChange = viewModel::updateStability,
                    )
                    VoiceTuningSlider(
                        label = "Clarity & Similarity Boost",
                        valueText = "${(state.params.clarityBoost * 100f).roundToInt()}%",
                        value = state.params.clarityBoost,
                        range = 0f..1f,
                        onValueChange = viewModel::updateClarity,
                    )
                    VoiceTuningSlider(
                        label = "Style Exaggeration",
                        valueText = "${(state.params.styleExaggeration * 100f).roundToInt()}%",
                        value = state.params.styleExaggeration,
                        range = 0f..1f,
                        onValueChange = viewModel::updateStyle,
                    )

                    Text(
                        text = "Studio Acoustics",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSlate,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StudioRoomPreset.entries.forEach { room ->
                            ChoicePill(
                                text = room.label,
                                selected = state.params.roomPreset == room,
                                onClick = { viewModel.selectRoom(room) },
                                accentColor = ClipperCyan,
                                accentSoft = ClipperCyanSoft,
                                tag = "voice_room_${room.name.lowercase(Locale.US)}",
                            )
                        }
                    }
                }
            }

            if (state.isGenerating) {
                item {
                    StudioProgressBanner(
                        label = "Synthesizing & mastering ${state.selectedVoice.name}...",
                        progress = state.generationProgress,
                    )
                }
            }

            state.errorMessage?.let { err ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.30f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ClipperRose)
                            Text(err, style = MaterialTheme.typography.bodyMedium, color = InkObsidian)
                        }
                    }
                }
            }

            // Generate Primary Button
            item {
                Button(
                    onClick = viewModel::generateVoice,
                    enabled = !state.isGenerating && !state.isRecordingMic && state.scriptText.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClipperIndigo,
                        contentColor = StudioWhite,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("voice_generate_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Voice (${state.selectedVoice.name})")
                }
            }

            // Generated Audio Waveform Player & WAV Export Card
            if (state.generatedWavFile != null && state.generatedWaveform.isNotEmpty()) {
                item {
                    StudioCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    color = ClipperIndigo,
                                    shape = CircleShape,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    IconButton(
                                        onClick = viewModel::togglePlayback,
                                        modifier = Modifier.testTag("voice_play_pause_button"),
                                    ) {
                                        Icon(
                                            imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                                            tint = StudioWhite,
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "${state.selectedVoice.name} • Mastered WAV",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = InkObsidian,
                                    )
                                    Text(
                                        text = MediaStoreSaver.formatDurationMs(state.generatedDurationMs),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                                        color = InkSlate,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = viewModel::exportWavToDevice,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.testTag("voice_save_wav_button"),
                            ) {
                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save WAV")
                            }
                        }

                        // Real 64-bar RMS Waveform Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioSnow)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            val bars = state.generatedWaveform
                            if (bars.isEmpty()) return@Canvas
                            val slotW = size.width / bars.size
                            val barW = (slotW * 0.62f).coerceAtLeast(2f)
                            val playedCount = (bars.size * state.playbackProgress).toInt()
                            bars.forEachIndexed { idx, amp ->
                                val barH = (amp.coerceIn(0.08f, 1f) * size.height).coerceAtLeast(4f)
                                val top = (size.height - barH) / 2f
                                val x = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(
                                    color = if (idx <= playedCount && state.isPlaying) ClipperIndigo else ClipperIndigo.copy(alpha = 0.30f),
                                    topLeft = Offset(x, top),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                                )
                            }
                        }
                    }
                }
            }

            state.savedResult?.let { saved ->
                item {
                    StudioCard(
                        containerColor = ClipperEmeraldSoft,
                        borderColor = ClipperEmerald.copy(alpha = 0.35f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald)
                            Column {
                                Text("Saved ${saved.displayName}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${saved.locationLabel} • ${MediaStoreSaver.formatByteSize(saved.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                                    color = ClipperEmerald,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceProfileCard(
    voice: VoiceProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag("voice_card_${voice.id}")
            .clickable(onClick = onClick),
        color = if (selected) ClipperIndigoSoft else StudioSnow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 1.25.dp else 0.75.dp,
            color = if (selected) ClipperIndigo else StudioBorder,
        ),
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
                    text = voice.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) ClipperIndigo else InkObsidian,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = if (voice.isCloned) ClipperMagenta else ClipperIndigo,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = voice.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = InkSlate,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${if (voice.isCloned) "CLONE" else voice.category.label.uppercase(Locale.US)} • ${voice.targetF0Hz.roundToInt()} Hz",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = if (voice.isCloned) ClipperMagenta else InkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VoiceTuningSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = InkObsidian)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = ClipperIndigo,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ClipperIndigo,
                activeTrackColor = ClipperIndigo,
                inactiveTrackColor = StudioBorder,
            ),
        )
    }
}
