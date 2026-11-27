package com.example.core.captions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.example.ui.theme.CaptionFontCatalog
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders multi-color styled captions with exact bundled .ttf typefaces onto ARGB_8888 Bitmaps
 * for both Media3 video burn-in overlays and standalone high-res Caption Cards.
 */
object CaptionBitmapRenderer {

    fun loadCaptionTypeface(context: Context, fontId: String): Typeface {
        val option = CaptionFontCatalog.firstOrNull { it.id == fontId } ?: CaptionFontCatalog.first()
        return try {
            ResourcesCompat.getFont(context, option.fontResId) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    fun renderCaptionOverlayBitmap(
        context: Context,
        text: String,
        style: CaptionStyle,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): Bitmap {
        val w = targetWidthPx.coerceIn(320, 1920)
        val h = targetHeightPx.coerceIn(320, 1920)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawStyledCaptionOnCanvas(
            context = context,
            canvas = canvas,
            text = text,
            style = style,
            canvasWidth = w,
            canvasHeight = h,
        )
        return bitmap
    }

    fun renderStandaloneCardBitmap(
        context: Context,
        text: String,
        style: CaptionStyle,
        cardWidthPx: Int = 1080,
        cardHeightPx: Int = 1350,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(cardWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Crisp studio white-to-ice-alabaster backdrop with subtle corner glow
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, cardWidthPx.toFloat(), cardHeightPx.toFloat(),
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt(), 0xFFEEF2FF.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, cardWidthPx.toFloat(), cardHeightPx.toFloat(), bgPaint)

        // Minimalist frame border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFFE2E8F0.toInt()
        }
        canvas.drawRoundRect(
            RectF(36f, 36f, cardWidthPx - 36f, cardHeightPx - 36f),
            44f,
            44f,
            borderPaint,
        )

        val centeredStyle = style.copy(verticalPosition = 0.50f)
        drawStyledCaptionOnCanvas(
            context = context,
            canvas = canvas,
            text = text,
            style = centeredStyle,
            canvasWidth = cardWidthPx,
            canvasHeight = cardHeightPx,
        )
        return bitmap
    }

    private fun drawStyledCaptionOnCanvas(
        context: Context,
        canvas: Canvas,
        text: String,
        style: CaptionStyle,
        canvasWidth: Int,
        canvasHeight: Int,
    ) {
        val spans = RichTextTokenizer.parseWords(text, style.uppercase)
        if (spans.isEmpty()) return

        val typeface = loadCaptionTypeface(context, style.fontId)
        val scaleFactor = canvasWidth / 390f
        val textSizePx = (style.fontSizeSp * scaleFactor).coerceIn(22f, 140f)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
            this.style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = textSizePx
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(3f, textSizePx * 0.11f)
            color = style.strokeColor.toArgb()
        }

        // Flatten spans into individual drawable words preserving highlight flags
        data class DrawWord(val word: String, val highlighted: Boolean, val widthPx: Float)
        val words = mutableListOf<DrawWord>()
        val spaceWidth = fillPaint.measureText(" ")
        for (span in spans) {
            val parts = span.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            for (part in parts) {
                words.add(DrawWord(part, span.isHighlighted, fillPaint.measureText(part)))
            }
        }
        if (words.isEmpty()) return

        val maxLineWidth = canvasWidth * 0.84f
        val lines = mutableListOf<List<DrawWord>>()
        var currentLine = mutableListOf<DrawWord>()
        var currentWidth = 0f

        for (w in words) {
            val nextWidth = if (currentLine.isEmpty()) w.widthPx else currentWidth + spaceWidth + w.widthPx
            if (currentLine.isNotEmpty() && nextWidth > maxLineWidth) {
                lines.add(currentLine)
                currentLine = mutableListOf(w)
                currentWidth = w.widthPx
            } else {
                currentLine.add(w)
                currentWidth = nextWidth
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)

        val fm = fillPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.24f
        val totalTextHeight = lineHeight * lines.size
        val widestLine = lines.maxOf { line ->
            line.sumOf { it.widthPx.toDouble() }.toFloat() + spaceWidth * max(0, line.size - 1)
        }

        val padH = textSizePx * 0.55f
        val padV = textSizePx * 0.35f
        val centerY = (canvasHeight * style.verticalPosition.coerceIn(0.14f, 0.88f))
            .coerceIn(totalTextHeight / 2f + padV + 20f, canvasHeight - totalTextHeight / 2f - padV - 20f)
        val blockTop = centerY - totalTextHeight / 2f

        // Background rounded pill behind caption block
        if (style.backgroundAlpha > 0.02f) {
            val bgColor = style.backgroundColor.toArgb()
            val alphaByte = (style.backgroundAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alphaByte, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            }
            val pillLeft = when (style.alignment) {
                CaptionAlignment.LEFT -> canvasWidth * 0.08f - padH * 0.5f
                CaptionAlignment.CENTER -> (canvasWidth - widestLine) / 2f - padH
                CaptionAlignment.RIGHT -> canvasWidth * 0.92f - widestLine - padH * 1.5f
            }.coerceAtLeast(16f)
            val pillRight = (pillLeft + widestLine + padH * 2f).coerceAtMost(canvasWidth - 16f)
            val pillRect = RectF(
                pillLeft,
                blockTop - padV,
                pillRight,
                blockTop + totalTextHeight + padV * 0.65f,
            )
            canvas.drawRoundRect(pillRect, textSizePx * 0.42f, textSizePx * 0.42f, pillPaint)
        }

        val primaryArgb = style.textColor.toArgb()
        val accentArgb = style.accentColor.toArgb()

        lines.forEachIndexed { lineIdx, line ->
            val lineW = line.sumOf { it.widthPx.toDouble() }.toFloat() + spaceWidth * max(0, line.size - 1)
            var cursorX = when (style.alignment) {
                CaptionAlignment.LEFT -> canvasWidth * 0.08f
                CaptionAlignment.CENTER -> (canvasWidth - lineW) / 2f
                CaptionAlignment.RIGHT -> canvasWidth * 0.92f - lineW
            }
            val baselineY = blockTop + lineIdx * lineHeight - fm.ascent

            line.forEachIndexed { wordIdx, word ->
                if (style.strokeEnabled) {
                    canvas.drawText(word.word, cursorX, baselineY, strokePaint)
                }
                fillPaint.color = if (word.highlighted) accentArgb else primaryArgb
                canvas.drawText(word.word, cursorX, baselineY, fillPaint)
                cursorX += word.widthPx + if (wordIdx < line.lastIndex) spaceWidth else 0f
            }
        }
    }
}
