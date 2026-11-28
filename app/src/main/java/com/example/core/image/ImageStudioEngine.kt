package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ImageRatioPreset(
    val label: String,
    val tag: String,
    val ratioW: Float,
    val ratioH: Float,
) {
    SQUARE_1_1("1:1 Square", "1_1", 1f, 1f),
    IG_PORTRAIT_4_5("4:5 IG", "4_5", 4f, 5f),
    STORY_9_16("9:16 Reels", "9_16", 9f, 16f),
    YOUTUBE_16_9("16:9 Wide", "16_9", 16f, 9f),
    PORTRAIT_3_4("3:4 Portrait", "3_4", 3f, 4f),
    CLASSIC_4_3("4:3 Classic", "4_3", 4f, 3f),
    PRINT_2_3("2:3 Print", "2_3", 2f, 3f),
    DSLR_3_2("3:2 DSLR", "3_2", 3f, 2f),
    CINEMA_21_9("21:9 Cinema", "21_9", 21f, 9f),
    CUSTOM("Custom", "custom", 1f, 1f),
}

enum class RatioFitMode(val label: String) {
    CROP_FILL("Crop Fill"),
    FIT_BLUR("Blur Fit"),
    FIT_SOLID("Solid Fit"),
    FIT_GRADIENT("Auto Gradient"),
    STRETCH("Stretch"),
}

enum class RatioOutputResolution(val label: String, val longEdgePx: Int) {
    MATCH_SOURCE("Original", 0),
    HD_1080("1080px", 1080),
    QHD_2048("2048px", 2048),
    UHD_4K("4K (3840px)", 3840),
}

enum class RatioExportFormat(val label: String, val ext: String, val mimeType: String) {
    PNG("PNG Lossless", "png", "image/png"),
    JPEG("JPEG 95%", "jpg", "image/jpeg"),
    WEBP("WebP Crisp", "webp", "image/webp"),
}

enum class BackdropColorPreset(val label: String, val argb: Int) {
    WHITE("Pure White", 0xFFFFFFFF.toInt()),
    OBSIDIAN("Obsidian", 0xFF0F172A.toInt()),
    CRIMSON("Crimson", 0xFF760107.toInt()),
    CREAM("Warm Cream", 0xFFF7F4EE.toInt()),
    INDIGO("Studio Indigo", 0xFF4F46E5.toInt()),
    AUTO_DOMINANT("Auto Match", 0x00000000),
}

data class ImageRatioConfig(
    val preset: ImageRatioPreset = ImageRatioPreset.IG_PORTRAIT_4_5,
    val customRatioW: Float = 5f,
    val customRatioH: Float = 7f,
    val fitMode: RatioFitMode = RatioFitMode.CROP_FILL,
    val backdropColor: BackdropColorPreset = BackdropColorPreset.WHITE,
    val blurRadius: Int = 18,
    val zoom: Float = 1.0f,
    val panX: Float = 0f, // -1f..1f
    val panY: Float = 0f, // -1f..1f
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val paddingFraction: Float = 0f,      // 0f..0.16f
    val cornerRadiusFraction: Float = 0f, // 0f..0.25f
    val resolution: RatioOutputResolution = RatioOutputResolution.QHD_2048,
    val format: RatioExportFormat = RatioExportFormat.PNG,
) {
    val effectiveRatioW: Float
        get() = if (preset == ImageRatioPreset.CUSTOM) customRatioW.coerceIn(0.2f, 50f) else preset.ratioW

    val effectiveRatioH: Float
        get() = if (preset == ImageRatioPreset.CUSTOM) customRatioH.coerceIn(0.2f, 50f) else preset.ratioH

    val aspectRatio: Float
        get() = (effectiveRatioW / effectiveRatioH).coerceIn(0.15f, 6.5f)
}

enum class CollageLayout(val label: String, val slotCount: Int) {
    SIDE_2("2 Side", 2),
    STACK_2("2 Stack", 2),
    HERO_3("3 Hero", 3),
    STRIP_3("3 Strip", 3),
    GRID_4("4 Grid", 4),
    GRID_6("6 Grid", 6),
}

data class CollageConfig(
    val layout: CollageLayout = CollageLayout.GRID_4,
    val aspectPreset: ImageRatioPreset = ImageRatioPreset.SQUARE_1_1,
    val spacingPx: Float = 18f,
    val outerMarginPx: Float = 20f,
    val cornerRadiusPx: Float = 22f,
    val backgroundArgb: Int = 0xFFFFFFFF.toInt(),
    val longEdgePx: Int = 1800,
)

enum class PhotoFilterLook(
    val label: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val vignette: Float,
    val grain: Float,
) {
    ORIGINAL("Original", 0f, 1.0f, 1.0f, 0f, 0f, 0f),
    KODAK_WARM("Kodak Warm", 0.04f, 1.12f, 1.14f, 0.28f, 0.25f, 0.16f),
    FUJI_TEAL("Fuji Teal", -0.02f, 1.15f, 1.08f, -0.22f, 0.22f, 0.12f),
    NOIR_BW("Noir B&W", 0.02f, 1.32f, 0.0f, 0f, 0.42f, 0.24f),
    MATTE_FADE("Matte Fade", 0.08f, 0.88f, 0.85f, 0.10f, 0.15f, 0.18f),
    VELVET_ROSE("Velvet Rose", 0.03f, 1.10f, 1.18f, 0.18f, 0.30f, 0.10f),
    GOLDEN_HOUR("Golden Hour", 0.06f, 1.14f, 1.24f, 0.40f, 0.20f, 0.08f),
    CYBER_CRISP("Cyber Crisp", 0.0f, 1.24f, 1.28f, -0.28f, 0.28f, 0.05f),
}

data class PhotoFilterConfig(
    val look: PhotoFilterLook = PhotoFilterLook.KODAK_WARM,
    val brightness: Float = PhotoFilterLook.KODAK_WARM.brightness, // -0.4..0.4
    val contrast: Float = PhotoFilterLook.KODAK_WARM.contrast,       // 0.5..1.8
    val saturation: Float = PhotoFilterLook.KODAK_WARM.saturation,   // 0.0..2.0
    val warmth: Float = PhotoFilterLook.KODAK_WARM.warmth,           // -0.6..0.6
    val vignette: Float = PhotoFilterLook.KODAK_WARM.vignette,       // 0.0..0.9
    val grain: Float = PhotoFilterLook.KODAK_WARM.grain,             // 0.0..0.6
)

data class PaletteSwatch(
    val argb: Int,
    val hex: String,
    val sharePercent: Int,
)

data class PhotoExifReport(
    val width: Int,
    val height: Int,
    val cameraMakeModel: String,
    val dateTaken: String,
    val exposureSummary: String,
    val hasGpsLocation: Boolean,
    val orientationLabel: String,
    val metadataTagCount: Int,
)

/**
 * Genuine on-device Image Processing & Creator Suite Engine.
 * Powers Image Ratio Studio (any format/custom ratio), Multi-Photo Collage Maker,
 * Film Photo Filter Studio, Dominant Color Palette Extraction, and EXIF Privacy Scrubbing.
 */
object ImageStudioEngine {

    /**
     * Pure JVM-testable target output pixel dimension solver for any aspect ratio and resolution tier.
     */
    fun computeRatioOutputDimensions(
        srcWidth: Int,
        srcHeight: Int,
        ratioW: Float,
        ratioH: Float,
        resolution: RatioOutputResolution,
    ): Pair<Int, Int> {
        val safeRatio = (ratioW.coerceAtLeast(0.1f) / ratioH.coerceAtLeast(0.1f)).coerceIn(0.15f, 6.5f)
        val baseLongEdge = if (resolution.longEdgePx > 0) {
            resolution.longEdgePx
        } else {
            max(srcWidth, srcHeight).coerceIn(512, 4096)
        }
        val outW: Int
        val outH: Int
        if (safeRatio >= 1f) {
            outW = baseLongEdge
            outH = (baseLongEdge / safeRatio).roundToInt()
        } else {
            outH = baseLongEdge
            outW = (baseLongEdge * safeRatio).roundToInt()
        }
        return alignEven(outW) to alignEven(outH)
    }

    private fun alignEven(v: Int): Int = ((v.coerceAtLeast(64) / 2) * 2).coerceAtLeast(64)

    /**
     * Pure JVM-testable k-means color quantizer extracting dominant swatches from ARGB pixel arrays.
     */
    fun extractDominantPaletteFromPixels(
        pixels: IntArray,
        swatchCount: Int = 6,
    ): List<PaletteSwatch> {
        if (pixels.isEmpty()) return emptyList()
        val k = swatchCount.coerceIn(2, 8)
        val stride = max(1, pixels.size / 1800)
        val samples = ArrayList<Int>(min(pixels.size, 1800))
        var idx = 0
        while (idx < pixels.size) {
            val c = pixels[idx]
            if (((c ushr 24) and 0xFF) >= 64) {
                samples.add(c)
            }
            idx += stride
        }
        if (samples.isEmpty()) return emptyList()

        // Initialize centroids deterministically spread across luminance-sorted samples
        val sortedByLuma = samples.sortedBy { c ->
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            r * 299 + g * 587 + b * 114
        }
        val centroidsR = FloatArray(k)
        val centroidsG = FloatArray(k)
        val centroidsB = FloatArray(k)
        for (cIdx in 0 until k) {
            val pick = sortedByLuma[((cIdx + 0.5f) / k * (sortedByLuma.size - 1)).toInt().coerceIn(0, sortedByLuma.lastIndex)]
            centroidsR[cIdx] = ((pick ushr 16) and 0xFF).toFloat()
            centroidsG[cIdx] = ((pick ushr 8) and 0xFF).toFloat()
            centroidsB[cIdx] = (pick and 0xFF).toFloat()
        }

        val counts = IntArray(k)
        val sumR = FloatArray(k)
        val sumG = FloatArray(k)
        val sumB = FloatArray(k)

        repeat(7) {
            counts.fill(0)
            sumR.fill(0f)
            sumG.fill(0f)
            sumB.fill(0f)

            for (p in samples) {
                val r = ((p ushr 16) and 0xFF).toFloat()
                val g = ((p ushr 8) and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                var best = 0
                var bestDist = Float.MAX_VALUE
                for (cIdx in 0 until k) {
                    val dr = r - centroidsR[cIdx]
                    val dg = g - centroidsG[cIdx]
                    val db = b - centroidsB[cIdx]
                    val dist = dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f
                    if (dist < bestDist) {
                        bestDist = dist
                        best = cIdx
                    }
                }
                counts[best]++
                sumR[best] += r
                sumG[best] += g
                sumB[best] += b
            }

            for (cIdx in 0 until k) {
                if (counts[cIdx] > 0) {
                    centroidsR[cIdx] = sumR[cIdx] / counts[cIdx]
                    centroidsG[cIdx] = sumG[cIdx] / counts[cIdx]
                    centroidsB[cIdx] = sumB[cIdx] / counts[cIdx]
                }
            }
        }

        val total = counts.sum().coerceAtLeast(1)
        val result = (0 until k)
            .filter { counts[it] > 0 }
            .sortedByDescending { counts[it] }
            .map { cIdx ->
                val r = centroidsR[cIdx].roundToInt().coerceIn(0, 255)
                val g = centroidsG[cIdx].roundToInt().coerceIn(0, 255)
                val b = centroidsB[cIdx].roundToInt().coerceIn(0, 255)
                val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                PaletteSwatch(
                    argb = argb,
                    hex = String.format(Locale.US, "#%02X%02X%02X", r, g, b),
                    sharePercent = ((counts[cIdx] * 100f) / total).roundToInt().coerceAtLeast(1),
                )
            }
        return result
    }

    fun extractDominantPalette(bitmap: Bitmap, swatchCount: Int = 6): List<PaletteSwatch> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return extractDominantPaletteFromPixels(pixels, swatchCount)
    }

    /**
     * Decodes a gallery image Uri cleanly downsampled to [maxEdge] and auto-rotated via EXIF orientation.
     */
    suspend fun decodeOrientedBitmap(
        context: Context,
        uri: Uri,
        maxEdge: Int = 2048,
    ): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)
        var inSample = 1
        while (max(srcW / inSample, srcH / inSample) > maxEdge * 2) {
            inSample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("Could not decode image.")

        val exifOrientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return@withContext raw
        }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        rotated
    }

    /**
     * Renders the user's photo into ANY target aspect ratio & format with Crop, Blur Backdrop,
     * Solid Backdrop, Auto Gradient, or Stretch framing, plus rotation, flip, pan, zoom, and rounded framing.
     */
    fun renderImageRatio(
        source: Bitmap,
        config: ImageRatioConfig,
        previewMaxEdge: Int? = null,
    ): Bitmap {
        val transformed = applyRotateAndFlip(
            source = source,
            rotationDegrees = config.rotationDegrees,
            flipH = config.flipHorizontal,
            flipV = config.flipVertical,
        )

        val (targetW, targetH) = if (previewMaxEdge != null) {
            val ratio = config.aspectRatio
            if (ratio >= 1f) {
                alignEven(previewMaxEdge) to alignEven((previewMaxEdge / ratio).roundToInt())
            } else {
                alignEven((previewMaxEdge * ratio).roundToInt()) to alignEven(previewMaxEdge)
            }
        } else {
            computeRatioOutputDimensions(
                srcWidth = transformed.width,
                srcHeight = transformed.height,
                ratioW = config.effectiveRatioW,
                ratioH = config.effectiveRatioH,
                resolution = config.resolution,
            )
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val palette by lazy { extractDominantPalette(transformed, swatchCount = 3) }

        // 1. Backdrop Layer
        when (config.fitMode) {
            RatioFitMode.FIT_BLUR -> {
                val blurred = createFastBlurredBackdrop(transformed, targetW, targetH, config.blurRadius)
                canvas.drawBitmap(blurred, 0f, 0f, paint)
                blurred.recycle()
                // Subtle soft scrim so foreground pops cleanly
                canvas.drawColor(Color.argb(38, 12, 16, 28))
            }
            RatioFitMode.FIT_SOLID -> {
                val bgColor = if (config.backdropColor == BackdropColorPreset.AUTO_DOMINANT) {
                    palette.firstOrNull()?.argb ?: Color.WHITE
                } else {
                    config.backdropColor.argb
                }
                canvas.drawColor(bgColor)
            }
            RatioFitMode.FIT_GRADIENT -> {
                val c1 = palette.getOrNull(0)?.argb ?: 0xFF4F46E5.toInt()
                val c2 = palette.getOrNull(1)?.argb ?: 0xFF06B6D4.toInt()
                val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, targetW.toFloat(), targetH.toFloat(), c1, c2, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), gradPaint)
            }
            RatioFitMode.CROP_FILL, RatioFitMode.STRETCH -> {
                canvas.drawColor(Color.BLACK)
            }
        }

        // 2. Foreground Content Bounds (respecting optional frame padding & rounded corners)
        val padPx = (min(targetW, targetH) * config.paddingFraction.coerceIn(0f, 0.18f))
        val contentRect = RectF(padPx, padPx, targetW - padPx, targetH - padPx)
        val cornerPx = min(contentRect.width(), contentRect.height()) * config.cornerRadiusFraction.coerceIn(0f, 0.25f)

        val saveCount = canvas.save()
        when (config.fitMode) {
            RatioFitMode.STRETCH -> {
                if (cornerPx > 1f) {
                    val clip = Path().apply { addRoundRect(contentRect, cornerPx, cornerPx, Path.Direction.CW) }
                    canvas.clipPath(clip)
                }
                canvas.drawBitmap(transformed, null, contentRect, paint)
            }
            RatioFitMode.CROP_FILL -> {
                if (cornerPx > 1f) {
                    val clip = Path().apply { addRoundRect(contentRect, cornerPx, cornerPx, Path.Direction.CW) }
                    canvas.clipPath(clip)
                } else if (padPx > 0.5f) {
                    canvas.clipRect(contentRect)
                }
                val baseScale = max(
                    contentRect.width() / transformed.width.toFloat(),
                    contentRect.height() / transformed.height.toFloat(),
                )
                val zoomScale = baseScale * config.zoom.coerceIn(1.0f, 3.0f)
                val drawW = transformed.width * zoomScale
                val drawH = transformed.height * zoomScale
                val maxShiftX = max(0f, (drawW - contentRect.width()) * 0.5f)
                val maxShiftY = max(0f, (drawH - contentRect.height()) * 0.5f)
                val left = contentRect.centerX() - drawW * 0.5f + config.panX.coerceIn(-1f, 1f) * maxShiftX
                val top = contentRect.centerY() - drawH * 0.5f + config.panY.coerceIn(-1f, 1f) * maxShiftY
                canvas.drawBitmap(transformed, null, RectF(left, top, left + drawW, top + drawH), paint)
            }
            RatioFitMode.FIT_BLUR, RatioFitMode.FIT_SOLID, RatioFitMode.FIT_GRADIENT -> {
                val fitScale = min(
                    contentRect.width() / transformed.width.toFloat(),
                    contentRect.height() / transformed.height.toFloat(),
                ) * config.zoom.coerceIn(0.85f, 2.0f)
                val drawW = transformed.width * fitScale
                val drawH = transformed.height * fitScale
                val left = contentRect.centerX() - drawW * 0.5f + config.panX.coerceIn(-1f, 1f) * contentRect.width() * 0.15f
                val top = contentRect.centerY() - drawH * 0.5f + config.panY.coerceIn(-1f, 1f) * contentRect.height() * 0.15f
                val dst = RectF(left, top, left + drawW, top + drawH)
                if (cornerPx > 1f) {
                    val clip = Path().apply { addRoundRect(dst, cornerPx, cornerPx, Path.Direction.CW) }
                    canvas.clipPath(clip)
                }
                canvas.drawBitmap(transformed, null, dst, paint)
            }
        }
        canvas.restoreToCount(saveCount)

        if (transformed !== source) transformed.recycle()
        return output
    }

    /**
     * Multi-Photo Collage & Grid Renderer (2 to 6 photos with clean aspect-preserving center crop).
     */
    fun renderCollage(
        bitmaps: List<Bitmap>,
        config: CollageConfig,
        previewMaxEdge: Int? = null,
    ): Bitmap {
        val ratio = (config.aspectPreset.ratioW / config.aspectPreset.ratioH).coerceIn(0.4f, 2.5f)
        val longEdge = previewMaxEdge ?: config.longEdgePx.coerceIn(720, 2400)
        val outW = if (ratio >= 1f) alignEven(longEdge) else alignEven((longEdge * ratio).roundToInt())
        val outH = if (ratio >= 1f) alignEven((longEdge / ratio).roundToInt()) else alignEven(longEdge)

        val scaleRatio = outW.toFloat() / config.longEdgePx.toFloat().coerceAtLeast(1f)
        val gap = (config.spacingPx * scaleRatio).coerceAtLeast(0f)
        val margin = (config.outerMarginPx * scaleRatio).coerceAtLeast(0f)
        val radius = (config.cornerRadiusPx * scaleRatio).coerceAtLeast(0f)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(config.backgroundArgb)

        val inner = RectF(margin, margin, outW - margin, outH - margin)
        val cells = computeCollageCells(inner, config.layout, gap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE2E8F0.toInt() }

        cells.forEachIndexed { idx, rect ->
            val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            val saved = canvas.save()
            canvas.clipPath(clip)
            val bmp = bitmaps.getOrNull(idx)
            if (bmp != null && !bmp.isRecycled) {
                val scale = max(rect.width() / bmp.width.toFloat(), rect.height() / bmp.height.toFloat())
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val left = rect.centerX() - dw * 0.5f
                val top = rect.centerY() - dh * 0.5f
                canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
            } else {
                canvas.drawRect(rect, placeholderPaint)
            }
            canvas.restoreToCount(saved)
        }
        return output
    }

    fun computeCollageCells(area: RectF, layout: CollageLayout, gap: Float): List<RectF> {
        val w = area.width()
        val h = area.height()
        val l = area.left
        val t = area.top
        return when (layout) {
            CollageLayout.SIDE_2 -> {
                val cw = (w - gap) / 2f
                listOf(
                    RectF(l, t, l + cw, t + h),
                    RectF(l + cw + gap, t, l + w, t + h),
                )
            }
            CollageLayout.STACK_2 -> {
                val ch = (h - gap) / 2f
                listOf(
                    RectF(l, t, l + w, t + ch),
                    RectF(l, t + ch + gap, l + w, t + h),
                )
            }
            CollageLayout.HERO_3 -> {
                val topH = (h - gap) * 0.56f
                val botT = t + topH + gap
                val cw = (w - gap) / 2f
                listOf(
                    RectF(l, t, l + w, t + topH),
                    RectF(l, botT, l + cw, t + h),
                    RectF(l + cw + gap, botT, l + w, t + h),
                )
            }
            CollageLayout.STRIP_3 -> {
                val cw = (w - gap * 2f) / 3f
                listOf(
                    RectF(l, t, l + cw, t + h),
                    RectF(l + cw + gap, t, l + cw * 2f + gap, t + h),
                    RectF(l + (cw + gap) * 2f, t, l + w, t + h),
                )
            }
            CollageLayout.GRID_4 -> {
                val cw = (w - gap) / 2f
                val ch = (h - gap) / 2f
                listOf(
                    RectF(l, t, l + cw, t + ch),
                    RectF(l + cw + gap, t, l + w, t + ch),
                    RectF(l, t + ch + gap, l + cw, t + h),
                    RectF(l + cw + gap, t + ch + gap, l + w, t + h),
                )
            }
            CollageLayout.GRID_6 -> {
                val cw = (w - gap) / 2f
                val ch = (h - gap * 2f) / 3f
                buildList {
                    for (r in 0 until 3) {
                        val rt = t + r * (ch + gap)
                        add(RectF(l, rt, l + cw, rt + ch))
                        add(RectF(l + cw + gap, rt, l + w, rt + ch))
                    }
                }
            }
        }
    }

    /**
     * Real Photo Filter & Film Studio processor: ColorMatrix grade + Radial Vignette + Deterministic Film Grain.
     */
    fun renderFilteredPhoto(
        source: Bitmap,
        config: PhotoFilterConfig,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Build combined ColorMatrix: Saturation -> Contrast/Brightness -> Warmth
        val satMatrix = ColorMatrix().apply { setSaturation(config.saturation.coerceIn(0f, 2f)) }
        val c = config.contrast.coerceIn(0.4f, 1.9f)
        val bShift = config.brightness.coerceIn(-0.45f, 0.45f) * 255f + (1f - c) * 128f
        val warm = config.warmth.coerceIn(-0.6f, 0.6f)
        val rGain = 1f + warm * 0.28f
        val bGain = 1f - warm * 0.28f

        val gradeMatrix = ColorMatrix(
            floatArrayOf(
                c * rGain, 0f, 0f, 0f, bShift,
                0f, c, 0f, 0f, bShift,
                0f, 0f, c * bGain, 0f, bShift,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        satMatrix.postConcat(gradeMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(satMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        // Radial film vignette
        if (config.vignette > 0.02f) {
            val radius = (max(w, h) * 0.72f).coerceAtLeast(1f)
            val vigAlpha = (config.vignette.coerceIn(0f, 0.9f) * 205f).roundToInt().coerceIn(0, 220)
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.5f,
                    radius,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(vigAlpha, 8, 10, 18)),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vigPaint)
        }

        // Organic film grain pass (fast hash noise directly in ARGB buffer)
        if (config.grain > 0.02f) {
            val px = IntArray(w * h)
            out.getPixels(px, 0, w, 0, 0, w, h)
            val amp = (config.grain.coerceIn(0f, 0.6f) * 44f)
            var seed = 0x9E3779B9.toInt()
            for (i in px.indices) {
                seed = seed * 1664525 + 1013904223
                val n = (((seed ushr 16) and 0xFF) / 127.5f - 1f) * amp
                val col = px[i]
                val a = col and 0xFF000000.toInt()
                val r = (((col ushr 16) and 0xFF) + n).roundToInt().coerceIn(0, 255)
                val g = (((col ushr 8) and 0xFF) + n).roundToInt().coerceIn(0, 255)
                val bl = ((col and 0xFF) + n).roundToInt().coerceIn(0, 255)
                px[i] = a or (r shl 16) or (g shl 8) or bl
            }
            out.setPixels(px, 0, w, 0, 0, w, h)
        }

        return out
    }

    /**
     * Reads real EXIF camera, exposure, and GPS privacy tags from any local photo Uri.
     */
    suspend fun inspectPhotoExif(context: Context, uri: Uri): PhotoExifReport = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var makeModel = "Not embedded"
        var dateTaken = "Not embedded"
        var exposure = "Not embedded"
        var hasGps = false
        var orientLabel = "Normal"
        var tagCount = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                if (make.isNotEmpty() || model.isNotEmpty()) {
                    makeModel = listOf(make, model).filter { it.isNotEmpty() }.distinct().joinToString(" ")
                    tagCount++
                }
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!date.isNullOrBlank()) {
                    dateTaken = date
                    tagCount++
                }
                val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                @Suppress("DEPRECATION")
                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                val exp = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                val parts = mutableListOf<String>()
                if (!fNum.isNullOrBlank()) parts.add("f/$fNum")
                if (!iso.isNullOrBlank()) parts.add("ISO $iso")
                if (!exp.isNullOrBlank()) parts.add("${exp}s")
                if (parts.isNotEmpty()) {
                    exposure = parts.joinToString(" • ")
                    tagCount++
                }

                val latLong = FloatArray(2)
                @Suppress("DEPRECATION")
                hasGps = exif.getLatLong(latLong) ||
                    !exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE).isNullOrBlank()
                if (hasGps) tagCount += 2

                orientLabel = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> "Rotated 90°"
                    ExifInterface.ORIENTATION_ROTATE_180 -> "Rotated 180°"
                    ExifInterface.ORIENTATION_ROTATE_270 -> "Rotated 270°"
                    else -> "Normal (0°)"
                }
            }
        } catch (_: Exception) {
            // Non-EXIF PNG/WebP images cleanly report zero camera tags
        }

        PhotoExifReport(
            width = bounds.outWidth.coerceAtLeast(0),
            height = bounds.outHeight.coerceAtLeast(0),
            cameraMakeModel = makeModel,
            dateTaken = dateTaken,
            exposureSummary = exposure,
            hasGpsLocation = hasGps,
            orientationLabel = orientLabel,
            metadataTagCount = tagCount,
        )
    }

    /**
     * Compresses [bitmap] to [targetFile] using the requested [format] (PNG / JPEG / WebP).
     */
    fun writeBitmapToFile(bitmap: Bitmap, targetFile: File, format: RatioExportFormat) {
        val compressFormat = when (format) {
            RatioExportFormat.PNG -> Bitmap.CompressFormat.PNG
            RatioExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            RatioExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        val quality = when (format) {
            RatioExportFormat.PNG -> 100
            RatioExportFormat.JPEG -> 95
            RatioExportFormat.WEBP -> 92
        }
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(compressFormat, quality, out)
            out.flush()
        }
    }

    private fun applyRotateAndFlip(
        source: Bitmap,
        rotationDegrees: Int,
        flipH: Boolean,
        flipV: Boolean,
    ): Bitmap {
        val normRot = ((rotationDegrees % 360) + 360) % 360
        if (normRot == 0 && !flipH && !flipV) return source
        val m = Matrix()
        if (normRot != 0) m.postRotate(normRot.toFloat())
        if (flipH || flipV) m.postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
    }

    /**
     * Multi-pass separable box blur over a small working copy scaled smoothly to target size
     * for instant, artifact-free Gaussian-style Blur Fit backdrops.
     */
    private fun createFastBlurredBackdrop(source: Bitmap, targetW: Int, targetH: Int, radius: Int): Bitmap {
        val workLong = 140
        val ratio = targetW.toFloat() / targetH.toFloat().coerceAtLeast(1f)
        val workW = if (ratio >= 1f) workLong else max(32, (workLong * ratio).roundToInt())
        val workH = if (ratio >= 1f) max(32, (workLong / ratio).roundToInt()) else workLong

        val small = Bitmap.createBitmap(workW, workH, Bitmap.Config.ARGB_8888)
        val c = Canvas(small)
        val scale = max(workW.toFloat() / source.width, workH.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        c.drawBitmap(
            source,
            null,
            RectF((workW - dw) * 0.5f, (workH - dh) * 0.5f, (workW + dw) * 0.5f, (workH + dh) * 0.5f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )

        val r = (radius / 2).coerceIn(2, 16)
        val pixels = IntArray(workW * workH)
        small.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        val temp = IntArray(workW * workH)

        // 2 passes of separable horizontal + vertical box blur ≈ true Gaussian blur
        repeat(2) {
            boxBlurHorizontal(pixels, temp, workW, workH, r)
            boxBlurVertical(temp, pixels, workW, workH, r)
        }
        small.setPixels(pixels, 0, workW, 0, 0, workW, workH)

        val full = Bitmap.createScaledBitmap(small, targetW, targetH, true)
        if (full !== small) small.recycle()
        return full
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until h) {
            val rowOff = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (dx in -radius..radius) {
                val p = src[rowOff + dx.coerceIn(0, w - 1)]
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
            }
            for (x in 0 until w) {
                dst[rowOff + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
                val addIdx = rowOff + (x + radius + 1).coerceIn(0, w - 1)
                val subIdx = rowOff + (x - radius).coerceIn(0, w - 1)
                val pAdd = src[addIdx]
                val pSub = src[subIdx]
                rSum += ((pAdd ushr 16) and 0xFF) - ((pSub ushr 16) and 0xFF)
                gSum += ((pAdd ushr 8) and 0xFF) - ((pSub ushr 8) and 0xFF)
                bSum += (pAdd and 0xFF) - (pSub and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            for (dy in -radius..radius) {
                val p = src[dy.coerceIn(0, h - 1) * w + x]
                rSum += (p ushr 16) and 0xFF
                gSum += (p ushr 8) and 0xFF
                bSum += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
                val addIdx = (y + radius + 1).coerceIn(0, h - 1) * w + x
                val subIdx = (y - radius).coerceIn(0, h - 1) * w + x
                val pAdd = src[addIdx]
                val pSub = src[subIdx]
                rSum += ((pAdd ushr 16) and 0xFF) - ((pSub ushr 16) and 0xFF)
                gSum += ((pAdd ushr 8) and 0xFF) - ((pSub ushr 8) and 0xFF)
                bSum += (pAdd and 0xFF) - (pSub and 0xFF)
            }
        }
    }
}
