package com.example.ui.features.captions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.captions.CaptionAlignment
import com.example.core.captions.CaptionStyle
import com.example.core.captions.RichTextTokenizer
import com.example.core.captions.SubtitleCodec
import com.example.core.media.MediaStoreSaver
import com.example.ui.components.ChoicePill
import com.example.ui.components.ExoVideoPreview
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioCard
import com.example.ui.components.StudioProgressBanner
import com.example.ui.theme.CaptionColorSwatches
import com.example.ui.theme.CaptionFontCatalog
import com.example.ui.theme.ClipperCyan
import com.example.ui.theme.ClipperCyanSoft
import com.example.ui.theme.ClipperEmerald
import com.example.ui.theme.ClipperEmeraldSoft
import com.example.ui.theme.ClipperIndigo
import com.example.ui.theme.ClipperIndigoSoft
import com.example.ui.theme.ClipperMagenta
import com.example.ui.theme.ClipperRose
import com.example.ui.theme.ClipperRoseSoft
import com.example.ui.theme.InkObsidian
import com.example.ui.theme.InkSlate
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSnow
import com.example.ui.theme.StudioWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionsScreen(
    viewModel: CaptionsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startVoiceDictation() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            viewModel.reportError("On-device Speech Recognition service is unavailable on this device.")
            return
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = viewModel.setDictating(true)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = viewModel.setDictating(false)
            override fun onError(error: Int) {
                viewModel.reportError("Voice dictation stopped (code $error). Try speaking closer to the mic.")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    viewModel.appendDictatedText(spoken)
                } else {
                    viewModel.setDictating(false)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        viewModel.setDictating(true)
        recognizer.startListening(intent)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceDictation()
        } else {
            viewModel.reportError("Microphone permission is required for voice dictation.")
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.selectVideoUri(uri)
    }

    val srtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importSrtUri(uri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioWhite),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.captions_title),
                    subtitle = stringResource(R.string.captions_subtitle),
                    accentColor = ClipperCyan,
                )
            }

            // Live Video / Studio Canvas Caption Preview Stage
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Live Caption Preview", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("captions_pick_video_button"),
                            ) {
                                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (state.selectedVideo == null) "Attach Video" else "Swap", style = MaterialTheme.typography.labelMedium)
                            }
                            if (state.selectedVideo != null) {
                                IconButton(
                                    onClick = viewModel::clearVideo,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Detach video", tint = InkSlate)
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF164E63))
                                )
                            ),
                    ) {
                        val videoUri = state.selectedVideo?.localUri
                        if (videoUri != null) {
                            ExoVideoPreview(
                                mediaUri = videoUri,
                                modifier = Modifier.fillMaxSize(),
                                onPositionChangedMs = viewModel::onPlaybackPositionChanged,
                            )
                        }

                        // Synchronized Rich Multi-Color Caption Overlay
                        val previewText = state.activePreviewCue?.text ?: state.scriptInput
                        if (previewText.isNotBlank()) {
                            LiveRichCaptionOverlay(
                                text = previewText,
                                style = state.style,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (state.isExporting) {
                item {
                    StudioProgressBanner(
                        label = state.exportLabel.ifBlank { "Exporting..." },
                        progress = state.exportProgress,
                    )
                }
            }

            state.errorMessage?.let { err ->
                item {
                    StudioCard(
                        containerColor = ClipperRoseSoft,
                        borderColor = ClipperRose.copy(alpha = 0.35f),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ClipperRose)
                            Text(err, style = MaterialTheme.typography.bodyMedium)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ClipperEmerald)
                            Column {
                                Text("Exported ${saved.displayName}", style = MaterialTheme.typography.titleSmall)
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

            // 10 Bundled Offline Fonts Selector
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("10 Offline Caption Fonts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${CaptionFontCatalog.size} BUNDLED",
                            style = MaterialTheme.typography.labelSmall,
                            color = ClipperCyan,
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("captions_font_carousel"),
                    ) {
                        items(CaptionFontCatalog, key = { it.id }) { font ->
                            val selected = state.style.fontId == font.id
                            Surface(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .testTag("caption_font_${font.id}")
                                    .clickable { viewModel.selectFont(font.id) },
                                color = if (selected) ClipperIndigoSoft else StudioSnow,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ClipperIndigo else StudioBorder,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Aa Bb",
                                        fontFamily = font.fontFamily,
                                        fontSize = 22.sp,
                                        color = if (selected) ClipperIndigo else InkObsidian,
                                    )
                                    Text(
                                        text = font.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = InkObsidian,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = font.styleTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSlate,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rich Multi-Color Text & Layout Controls
            item {
                StudioCard {
                    Text("Rich Color & Typography", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Tip: Wrap any word in *asterisks* to color it with your Highlight Accent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSlate,
                    )

                    ColorSwatchRow(
                        label = "Primary Text Color",
                        selected = state.style.textColor,
                        onSelect = viewModel::selectTextColor,
                    )
                    ColorSwatchRow(
                        label = "Highlight Accent (*word*)",
                        selected = state.style.accentColor,
                        onSelect = viewModel::selectAccentColor,
                    )
                    ColorSwatchRow(
                        label = "Backdrop Pill Color",
                        selected = state.style.backgroundColor,
                        onSelect = viewModel::selectBackgroundColor,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${state.style.fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                            color = ClipperIndigo,
                        )
                    }
                    Slider(
                        value = state.style.fontSizeSp,
                        onValueChange = viewModel::setFontSize,
                        valueRange = 16f..42f,
                        colors = SliderDefaults.colors(thumbColor = ClipperIndigo, activeTrackColor = ClipperIndigo),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Backdrop Opacity", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(state.style.backgroundAlpha * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        )
                    }
                    Slider(
                        value = state.style.backgroundAlpha,
                        onValueChange = viewModel::setBackgroundAlpha,
                        valueRange = 0f..1f,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Vertical Placement", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(state.style.verticalPosition * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        )
                    }
                    Slider(
                        value = state.style.verticalPosition,
                        onValueChange = viewModel::setVerticalPosition,
                        valueRange = 0.16f..0.88f,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CaptionAlignment.entries.forEach { align ->
                            ChoicePill(
                                text = align.label,
                                selected = state.style.alignment == align,
                                onClick = { viewModel.setAlignment(align) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Crisp Text Outline", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = state.style.strokeEnabled, onCheckedChange = { viewModel.toggleStroke() })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("ALL CAPS Display", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = state.style.uppercase, onCheckedChange = { viewModel.toggleUppercase() })
                    }
                }
            }

            // Script Input, Offline Dictation & SRT Importer
            item {
                StudioCard {
                    Text("Script & Offline Generator", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.scriptInput,
                        onValueChange = viewModel::onScriptChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("captions_script_input"),
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("Caption Script (use *word* for color pop)") },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClipperIndigo,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioWhite,
                            unfocusedContainerColor = StudioSnow,
                        ),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::generateCuesFromScript,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                            modifier = Modifier.testTag("captions_auto_split_button"),
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Time Cues")
                        }

                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startVoiceDictation()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (state.isDictating) ClipperRose else StudioBorder),
                            modifier = Modifier.testTag("captions_dictate_button"),
                        ) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = if (state.isDictating) ClipperRose else InkObsidian,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (state.isDictating) "Listening..." else stringResource(R.string.captions_dictate),
                                color = if (state.isDictating) ClipperRose else InkObsidian,
                            )
                        }

                        OutlinedButton(
                            onClick = { srtPicker.launch(arrayOf("text/*", "application/x-subrip", "*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.testTag("captions_import_srt_button"),
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.captions_import_srt))
                        }
                    }
                }
            }

            // Timed Subtitle Cues List
            item {
                StudioCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Timed Cues (${state.cues.size})", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = viewModel::addBlankCue,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Cue", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    state.cues.forEach { cue ->
                        val isSelected = cue.id == state.selectedCueId
                        Surface(
                            color = if (isSelected) ClipperCyanSoft else StudioSnow,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ClipperCyan else StudioBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCue(cue.id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${SubtitleCodec.formatSrtTime(cue.startMs)}  →  ${SubtitleCodec.formatSrtTime(cue.endMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ClipperCyan,
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteCue(cue.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete cue",
                                            tint = InkSlate,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = cue.text,
                                    onValueChange = { viewModel.updateCueText(cue.id, it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ClipperCyan,
                                        unfocusedBorderColor = StudioBorder,
                                        focusedContainerColor = StudioWhite,
                                        unfocusedContainerColor = StudioWhite,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // Export Actions Card
            item {
                StudioCard {
                    Text("Export Captions", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::exportSrt,
                            enabled = state.cues.isNotEmpty() && !state.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("captions_export_srt_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperIndigo),
                        ) {
                            Icon(Icons.Outlined.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.captions_export_srt))
                        }

                        OutlinedButton(
                            onClick = viewModel::exportStyledCaptionCard,
                            enabled = !state.isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("captions_export_card_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, ClipperMagenta),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = ClipperMagenta, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.captions_export_card), color = InkObsidian)
                        }
                    }

                    if (state.selectedVideo != null) {
                        Button(
                            onClick = viewModel::exportBurnedVideo,
                            enabled = state.cues.isNotEmpty() && !state.isExporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("captions_burn_video_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClipperCyan),
                        ) {
                            Icon(Icons.Outlined.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.captions_export_video))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRichCaptionOverlay(
    text: String,
    style: CaptionStyle,
    modifier: Modifier = Modifier,
) {
    val fontOption = remember(style.fontId) {
        CaptionFontCatalog.firstOrNull { it.id == style.fontId } ?: CaptionFontCatalog.first()
    }
    val annotated = remember(text, style.uppercase, style.textColor, style.accentColor) {
        val spans = RichTextTokenizer.parseWords(text, style.uppercase)
        buildAnnotatedString {
            spans.forEach { span ->
                withStyle(
                    SpanStyle(
                        color = if (span.isHighlighted) style.accentColor else style.textColor,
                    )
                ) {
                    append(span.text)
                }
            }
        }
    }

    val textAlign = when (style.alignment) {
        CaptionAlignment.LEFT -> TextAlign.Start
        CaptionAlignment.CENTER -> TextAlign.Center
        CaptionAlignment.RIGHT -> TextAlign.End
    }

    val boxAlignment = when {
        style.verticalPosition < 0.36f -> Alignment.TopCenter
        style.verticalPosition < 0.64f -> Alignment.Center
        else -> Alignment.BottomCenter
    }

    Box(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = boxAlignment,
    ) {
        Surface(
            color = style.backgroundColor.copy(alpha = style.backgroundAlpha),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = annotated,
                textAlign = textAlign,
                style = TextStyle(
                    fontFamily = fontOption.fontFamily,
                    fontSize = (style.fontSizeSp * 0.82f).sp,
                    lineHeight = (style.fontSizeSp * 1.05f).sp,
                    shadow = if (style.strokeEnabled) {
                        Shadow(
                            color = style.strokeColor,
                            offset = Offset(2f, 2f),
                            blurRadius = 3f,
                        )
                    } else {
                        null
                    },
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    selected: Color,
    onSelect: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = InkObsidian)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CaptionColorSwatches, key = { it.first }) { (name, color) ->
                val isSelected = selected == color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) ClipperIndigo else StudioBorder,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(color) },
                )
            }
        }
    }
}
