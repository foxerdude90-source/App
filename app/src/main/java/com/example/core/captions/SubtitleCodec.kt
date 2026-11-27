package com.example.core.captions

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Lossless SRT & WebVTT subtitle codec plus weighted syllable/character script auto-timer.
 */
object SubtitleCodec {

    private val TIMECODE_LINE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )

    fun formatSrtTime(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1000L
        val millis = clamped % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatVttTime(ms: Long): String = formatSrtTime(ms).replace(',', '.')

    fun serializeToSrt(cues: List<CaptionCue>): String {
        val sorted = cues.sortedBy { it.startMs }
        return buildString {
            sorted.forEachIndexed { index, cue ->
                append(index + 1).append('\n')
                append(formatSrtTime(cue.startMs))
                    .append(" --> ")
                    .append(formatSrtTime(cue.endMs))
                    .append('\n')
                append(RichTextTokenizer.stripMarkup(cue.text).trim()).append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    fun serializeToVtt(cues: List<CaptionCue>): String {
        val sorted = cues.sortedBy { it.startMs }
        return buildString {
            append("WEBVTT\n\n")
            sorted.forEach { cue ->
                append(formatVttTime(cue.startMs))
                    .append(" --> ")
                    .append(formatVttTime(cue.endMs))
                    .append('\n')
                append(RichTextTokenizer.stripMarkup(cue.text).trim()).append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    fun parseSubtitles(rawContent: String): List<CaptionCue> {
        val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val blocks = normalized.split(Regex("\n{2,}"))
        val cues = mutableListOf<CaptionCue>()
        var idCounter = 1L

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty() || lines.first().startsWith("WEBVTT", ignoreCase = true)) continue

            val timeIdx = lines.indexOfFirst { TIMECODE_LINE_REGEX.containsMatchIn(it) }
            if (timeIdx < 0) continue

            val match = TIMECODE_LINE_REGEX.find(lines[timeIdx]) ?: continue
            val startMs = parseTimeGroup(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4],
            )
            val endMs = parseTimeGroup(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8],
            )
            val textLines = lines.drop(timeIdx + 1)
            val text = textLines.joinToString("\n").trim()
            if (text.isNotEmpty() && endMs > startMs) {
                cues.add(CaptionCue(id = idCounter++, startMs = startMs, endMs = endMs, text = text))
            }
        }
        return cues
    }

    /**
     * Mathematically distributes a free-form script into clean 4-6 word caption cues across
     * [totalDurationMs] weighted by each segment's non-whitespace character length.
     */
    fun autoDistributeScript(
        script: String,
        totalDurationMs: Long,
        wordsPerCue: Int = 5,
        startOffsetMs: Long = 0L,
    ): List<CaptionCue> {
        val words = script.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val chunkSize = wordsPerCue.coerceIn(2, 10)
        val chunks = words.chunked(chunkSize).map { it.joinToString(" ") }
        val duration = totalDurationMs.coerceAtLeast(chunks.size * 1200L)

        val weights = chunks.map { max(3, it.replace("*", "").length) }
        val totalWeight = weights.sum().toDouble()

        val cues = mutableListOf<CaptionCue>()
        var cursor = startOffsetMs.coerceAtLeast(0L)
        val endLimit = cursor + duration

        chunks.forEachIndexed { idx, chunk ->
            val sliceMs = if (idx == chunks.lastIndex) {
                (endLimit - cursor).coerceAtLeast(500L)
            } else {
                ((weights[idx].toDouble() / totalWeight) * duration).roundToLong().coerceAtLeast(600L)
            }
            val cueEnd = (cursor + sliceMs).coerceAtMost(endLimit)
            cues.add(
                CaptionCue(
                    id = idx + 1L,
                    startMs = cursor,
                    endMs = max(cursor + 400L, cueEnd),
                    text = chunk,
                )
            )
            cursor = cueEnd
        }
        return cues
    }

    private fun parseTimeGroup(h: String, m: String, s: String, ms: String): Long {
        val hh = h.toLongOrNull() ?: 0L
        val mm = m.toLongOrNull() ?: 0L
        val ss = s.toLongOrNull() ?: 0L
        val millis = ms.toLongOrNull() ?: 0L
        return hh * 3_600_000L + mm * 60_000L + ss * 1000L + millis
    }
}
