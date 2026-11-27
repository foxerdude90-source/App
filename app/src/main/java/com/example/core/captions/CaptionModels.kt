package com.example.core.captions

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CaptionFontCatalog

enum class CaptionAlignment(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right"),
}

data class CaptionCue(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/**
 * Rich styling configuration for offline captions & text cards.
 * Words wrapped in *asterisks* render in [accentColor] for multi-color kinetic emphasis!
 */
data class CaptionStyle(
    val fontId: String = CaptionFontCatalog.first().id,
    val textColor: Color = Color(0xFFFFFFFF),
    val accentColor: Color = Color(0xFF06B6D4),
    val backgroundColor: Color = Color(0xFF0F172A),
    val backgroundAlpha: Float = 0.72f,
    val strokeEnabled: Boolean = true,
    val strokeColor: Color = Color(0xFF0F172A),
    val fontSizeSp: Float = 26f,
    val verticalPosition: Float = 0.80f, // 0.15 (top) .. 0.88 (bottom)
    val uppercase: Boolean = false,
    val alignment: CaptionAlignment = CaptionAlignment.CENTER,
)

data class StyledWordSpan(
    val text: String,
    val isHighlighted: Boolean,
)

object RichTextTokenizer {

    /**
     * Parses rich caption markup where words enclosed in *asterisks* receive [CaptionStyle.accentColor].
     * Example: "Build *faster* with *Clipper* Studio"
     */
    fun parseWords(rawText: String, uppercase: Boolean): List<StyledWordSpan> {
        val source = if (uppercase) rawText.uppercase() else rawText
        val result = mutableListOf<StyledWordSpan>()
        val tokenRegex = Regex("""\*([^*]+)\*|([^*]+)""")
        for (match in tokenRegex.findAll(source)) {
            val highlighted = match.groupValues[1]
            val plain = match.groupValues[2]
            if (highlighted.isNotEmpty()) {
                result.add(StyledWordSpan(text = highlighted, isHighlighted = true))
            } else if (plain.isNotEmpty()) {
                result.add(StyledWordSpan(text = plain, isHighlighted = false))
            }
        }
        return result
    }

    fun stripMarkup(rawText: String): String = rawText.replace("*", "")
}
