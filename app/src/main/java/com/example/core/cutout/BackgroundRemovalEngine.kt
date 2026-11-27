package com.example.core.cutout

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class CutoutBackgroundStyle(
    val id: String,
    val label: String,
    val primaryColor: Int,
    val secondaryColor: Int? = null,
) {
    TRANSPARENT("transparent", "Transparent", Color.TRANSPARENT),
    PURE_WHITE("white", "White", 0xFFFFFFFF.toInt()),
    OBSIDIAN("obsidian", "Obsidian", 0xFF0F172A.toInt()),
    STUDIO_CYAN("cyan", "Cyan", 0xFFECFEFF.toInt()),
    AURORA_GRADIENT("aurora", "Aurora", 0xFF06B6D4.toInt(), 0xFFC026D3.toInt()),
    INDIGO_MIST("indigo", "Indigo", 0xFFEEF2FF.toInt(), 0xFFE0E7FF.toInt()),
}

enum class LassoMode {
    KEEP_INSIDE,
    CUT_INSIDE,
}

data class NormalizedPoint(val x: Float, val y: Float)

/**
 * 100% offline Background Removal & Mask Geometry Engine.
 *
 * Combines:
 *  - Bundled ML Kit Selfie Segmentation neural network (Portrait AI)
 *  - CIELAB border-seeded K-Means++ + BFS flood-fill segmentation (Smart Object)
 *  - Jordan Curve Theorem Ray-Casting Point-in-Polygon Lasso engine
 *  - CIELAB perceptual Delta-E Magic Wand flood-fill
 *  - Radial-falloff Brush Erase / Restore engine
 *  - Separable Gaussian/Box alpha edge feathering
 */
object BackgroundRemovalEngine {

    fun createFullOpaqueMask(width: Int, height: Int): ByteArray =
        ByteArray(width * height) { 0xFF.toByte() }

    /**
     * Bundled offline ML Kit Selfie Segmentation producing a smooth per-pixel alpha mask.
     */
    suspend fun detectPortraitMask(source: Bitmap): ByteArray = withContext(Dispatchers.Default) {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(options)
        val inputImage = InputImage.fromBitmap(source, 0)

        try {
            val result = suspendCancellableCoroutine { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { mask ->
                        if (cont.isActive) cont.resume(mask)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }

            val maskW = result.width
            val maskH = result.height
            val buffer = result.buffer
            buffer.rewind()
            val confidences = FloatArray(maskW * maskH)
            for (i in confidences.indices) {
                confidences[i] = buffer.float
            }

            val dstW = source.width
            val dstH = source.height
            val outMask = ByteArray(dstW * dstH)

            for (y in 0 until dstH) {
                val fy = (y.toFloat() / max(1, dstH - 1)) * (maskH - 1)
                val y0 = floor(fy).toInt().coerceIn(0, maskH - 1)
                val y1 = min(y0 + 1, maskH - 1)
                val dy = fy - y0
                for (x in 0 until dstW) {
                    val fx = (x.toFloat() / max(1, dstW - 1)) * (maskW - 1)
                    val x0 = floor(fx).toInt().coerceIn(0, maskW - 1)
                    val x1 = min(x0 + 1, maskW - 1)
                    val dx = fx - x0

                    val c00 = confidences[y0 * maskW + x0]
                    val c10 = confidences[y0 * maskW + x1]
                    val c01 = confidences[y1 * maskW + x0]
                    val c11 = confidences[y1 * maskW + x1]
                    val top = c00 + (c10 - c00) * dx
                    val bottom = c01 + (c11 - c01) * dx
                    val conf = top + (bottom - top) * dy

                    // Smooth S-curve sharpening around 0.45 confidence for crisp hair/edges
                    val refined = smoothStep(0.28f, 0.68f, conf)
                    outMask[y * dstW + x] = (refined * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
            }
            outMask
        } finally {
            segmenter.close()
        }
    }

    /**
     * Pure mathematical offline Smart Object background remover:
     * 1. Converts image pixels to perceptual CIE L*a*b* space.
     * 2. Samples perimeter pixels and fits K=3 background color centroids via K-Means.
     * 3. Runs border-seeded 4-connected BFS flood-fill using perceptual Delta-E distance.
     * 4. Computes soft transition alpha on boundary fringe pixels and removes stray 1px noise.
     */
    suspend fun detectSmartObjectMask(
        source: Bitmap,
        sensitivity: Float = 26f,
    ): ByteArray = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        computeSmartObjectMaskFromPixels(pixels, w, h, sensitivity)
    }

    fun computeSmartObjectMaskFromPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        sensitivity: Float = 26f,
    ): ByteArray {
        val total = width * height
        val labL = FloatArray(total)
        val labA = FloatArray(total)
        val labB = FloatArray(total)

        val tmpLab = FloatArray(3)
        for (i in 0 until total) {
            rgbToLab(pixels[i], tmpLab)
            labL[i] = tmpLab[0]
            labA[i] = tmpLab[1]
            labB[i] = tmpLab[2]
        }

        // Collect border pixel indices
        val borderIndices = ArrayList<Int>(2 * (width + height))
        for (x in 0 until width) {
            borderIndices.add(x)
            borderIndices.add((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            borderIndices.add(y * width)
            borderIndices.add(y * width + (width - 1))
        }

        // Fit K=3 background centroids in CIELAB space
        val k = 3
        val cL = FloatArray(k)
        val cA = FloatArray(k)
        val cB = FloatArray(k)
        val step = max(1, borderIndices.size / k)
        for (c in 0 until k) {
            val idx = borderIndices[min(c * step, borderIndices.lastIndex)]
            cL[c] = labL[idx]
            cA[c] = labA[idx]
            cB[c] = labB[idx]
        }

        val sumL = FloatArray(k)
        val sumA = FloatArray(k)
        val sumB = FloatArray(k)
        val counts = IntArray(k)
        repeat(6) {
            sumL.fill(0f)
            sumA.fill(0f)
            sumB.fill(0f)
            counts.fill(0)
            for (idx in borderIndices) {
                var bestC = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until k) {
                    val d = deltaESq(labL[idx], labA[idx], labB[idx], cL[c], cA[c], cB[c])
                    if (d < bestDist) {
                        bestDist = d
                        bestC = c
                    }
                }
                sumL[bestC] += labL[idx]
                sumA[bestC] += labA[idx]
                sumB[bestC] += labB[idx]
                counts[bestC]++
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    cL[c] = sumL[c] / counts[c]
                    cA[c] = sumA[c] / counts[c]
                    cB[c] = sumB[c] / counts[c]
                }
            }
        }

        val visited = BooleanArray(total)
        val mask = ByteArray(total) { 0xFF.toByte() }
        val queue = ArrayDeque<Int>(borderIndices.size * 2)
        val hardThresh = sensitivity.coerceIn(6f, 80f)
        val softBand = hardThresh + 12f
        val hardThreshSq = hardThresh * hardThresh
        val softBandSq = softBand * softBand

        fun minBgDistSq(idx: Int): Float {
            var minD = Float.MAX_VALUE
            for (c in 0 until k) {
                val d = deltaESq(labL[idx], labA[idx], labB[idx], cL[c], cA[c], cB[c])
                if (d < minD) minD = d
            }
            return minD
        }

        for (idx in borderIndices) {
            if (!visited[idx] && minBgDistSq(idx) <= softBandSq) {
                visited[idx] = true
                queue.add(idx)
            }
        }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val dSq = minBgDistSq(cur)
            if (dSq <= hardThreshSq) {
                mask[cur] = 0
            } else {
                val d = sqrt(dSq)
                val alpha = smoothStep(hardThresh, softBand, d)
                mask[cur] = (alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
            }

            // Expand only from confident background pixels so flood doesn't leak into subject
            if (dSq <= hardThreshSq * 1.15f) {
                val x = cur % width
                val y = cur / width
                if (x > 0) {
                    val n = cur - 1
                    if (!visited[n] && minBgDistSq(n) <= softBandSq) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (x + 1 < width) {
                    val n = cur + 1
                    if (!visited[n] && minBgDistSq(n) <= softBandSq) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (y > 0) {
                    val n = cur - width
                    if (!visited[n] && minBgDistSq(n) <= softBandSq) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (y + 1 < height) {
                    val n = cur + width
                    if (!visited[n] && minBgDistSq(n) <= softBandSq) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
            }
        }

        return featherMask(mask, width, height, radius = 1)
    }

    /**
     * Applies a closed polygon Lasso selection to an alpha mask using exact Ray-Casting
     * Point-in-Polygon geometry (Jordan Curve Theorem).
     */
    fun applyLassoSelection(
        currentMask: ByteArray,
        width: Int,
        height: Int,
        polygon: List<NormalizedPoint>,
        mode: LassoMode,
        featherRadius: Int = 2,
    ): ByteArray {
        if (polygon.size < 3) return currentMask.copyOf()
        val result = currentMask.copyOf()
        val n = polygon.size
        val px = FloatArray(n) { (polygon[it].x.coerceIn(0f, 1f)) * (width - 1) }
        val py = FloatArray(n) { (polygon[it].y.coerceIn(0f, 1f)) * (height - 1) }

        var minX = width - 1
        var maxX = 0
        var minY = height - 1
        var maxY = 0
        for (i in 0 until n) {
            minX = min(minX, floor(px[i]).toInt().coerceIn(0, width - 1))
            maxX = max(maxX, ceil(px[i]).toInt().coerceIn(0, width - 1))
            minY = min(minY, floor(py[i]).toInt().coerceIn(0, height - 1))
            maxY = max(maxY, ceil(py[i]).toInt().coerceIn(0, height - 1))
        }

        if (mode == LassoMode.KEEP_INSIDE) {
            result.fill(0)
            for (y in minY..maxY) {
                val fy = y + 0.5f
                for (x in minX..maxX) {
                    val fx = x + 0.5f
                    if (isPointInPolygon(fx, fy, px, py)) {
                        val idx = y * width + x
                        result[idx] = currentMask[idx]
                    }
                }
            }
        } else {
            for (y in minY..maxY) {
                val fy = y + 0.5f
                for (x in minX..maxX) {
                    val fx = x + 0.5f
                    if (isPointInPolygon(fx, fy, px, py)) {
                        result[y * width + x] = 0
                    }
                }
            }
        }

        return if (featherRadius > 0) featherMask(result, width, height, featherRadius) else result
    }

    /**
     * Exact Ray-Casting Point-in-Polygon test (even-odd parity rule).
     */
    fun isPointInPolygon(
        testX: Float,
        testY: Float,
        polyX: FloatArray,
        polyY: FloatArray,
    ): Boolean {
        var inside = false
        var j = polyX.size - 1
        for (i in polyX.indices) {
            val yi = polyY[i]
            val yj = polyY[j]
            val intersectsY = (yi > testY) != (yj > testY)
            if (intersectsY) {
                val intersectX = polyX[i] + (testY - yi) * (polyX[j] - polyX[i]) / (yj - yi)
                if (testX < intersectX) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    /**
     * Magic Wand: CIELAB Delta-E 4-connected BFS flood-fill starting from tapped pixel.
     */
    fun applyMagicWandFloodFill(
        pixels: IntArray,
        currentMask: ByteArray,
        width: Int,
        height: Int,
        seedNormX: Float,
        seedNormY: Float,
        tolerance: Float,
    ): ByteArray {
        val sx = (seedNormX.coerceIn(0f, 1f) * (width - 1)).roundToInt()
        val sy = (seedNormY.coerceIn(0f, 1f) * (height - 1)).roundToInt()
        val startIdx = sy * width + sx

        val seedLab = FloatArray(3)
        rgbToLab(pixels[startIdx], seedLab)
        val tolSq = tolerance.coerceIn(4f, 95f).let { it * it }
        val softTol = tolerance + 8f
        val softTolSq = softTol * softTol

        val result = currentMask.copyOf()
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>(4096)
        visited[startIdx] = true
        queue.add(startIdx)

        val curLab = FloatArray(3)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            rgbToLab(pixels[cur], curLab)
            val dSq = deltaESq(seedLab[0], seedLab[1], seedLab[2], curLab[0], curLab[1], curLab[2])
            if (dSq <= tolSq) {
                result[cur] = 0
                val x = cur % width
                val y = cur / width
                if (x > 0 && !visited[cur - 1]) {
                    visited[cur - 1] = true
                    queue.add(cur - 1)
                }
                if (x + 1 < width && !visited[cur + 1]) {
                    visited[cur + 1] = true
                    queue.add(cur + 1)
                }
                if (y > 0 && !visited[cur - width]) {
                    visited[cur - width] = true
                    queue.add(cur - width)
                }
                if (y + 1 < height && !visited[cur + width]) {
                    visited[cur + width] = true
                    queue.add(cur + width)
                }
            } else if (dSq <= softTolSq) {
                val factor = smoothStep(tolerance, softTol, sqrt(dSq))
                val existing = result[cur].toInt() and 0xFF
                result[cur] = (existing * factor).roundToInt().coerceIn(0, 255).toByte()
            }
        }
        return result
    }

    /**
     * Paints a smooth circular brush stroke along a sequence of normalized touch points.
     */
    fun applyBrushStroke(
        currentMask: ByteArray,
        width: Int,
        height: Int,
        points: List<NormalizedPoint>,
        radiusFraction: Float,
        erase: Boolean,
    ): ByteArray {
        if (points.isEmpty()) return currentMask.copyOf()
        val result = currentMask.copyOf()
        val radiusPx = max(3f, radiusFraction * min(width, height))
        val radiusInt = ceil(radiusPx).toInt()
        val innerRadius = radiusPx * 0.65f

        fun stamp(cx: Float, cy: Float) {
            val minX = max(0, floor(cx - radiusInt).toInt())
            val maxX = min(width - 1, ceil(cx + radiusInt).toInt())
            val minY = max(0, floor(cy - radiusInt).toInt())
            val maxY = min(height - 1, ceil(cy + radiusInt).toInt())
            for (y in minY..maxY) {
                val dy = y - cy
                for (x in minX..maxX) {
                    val dx = x - cx
                    val dist = hypot(dx, dy)
                    if (dist <= radiusPx) {
                        val strength = 1f - smoothStep(innerRadius, radiusPx, dist)
                        val idx = y * width + x
                        val curAlpha = (result[idx].toInt() and 0xFF) / 255f
                        val nextAlpha = if (erase) {
                            curAlpha * (1f - strength)
                        } else {
                            curAlpha + (1f - curAlpha) * strength
                        }
                        result[idx] = (nextAlpha * 255f).roundToInt().coerceIn(0, 255).toByte()
                    }
                }
            }
        }

        if (points.size == 1) {
            stamp(points[0].x * (width - 1), points[0].y * (height - 1))
            return result
        }

        val stepDist = max(1f, radiusPx * 0.25f)
        for (i in 0 until points.lastIndex) {
            val x0 = points[i].x * (width - 1)
            val y0 = points[i].y * (height - 1)
            val x1 = points[i + 1].x * (width - 1)
            val y1 = points[i + 1].y * (height - 1)
            val segLen = hypot(x1 - x0, y1 - y0)
            val steps = max(1, ceil(segLen / stepDist).toInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps.toFloat()
                stamp(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            }
        }
        return result
    }

    /**
     * Fast separable box-blur feathering on the alpha channel for anti-aliased cutout edges.
     */
    fun featherMask(mask: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        val r = radius.coerceIn(0, 8)
        if (r == 0) return mask.copyOf()
        val temp = ByteArray(width * height)
        val out = ByteArray(width * height)
        val kernelSize = 2 * r + 1

        // Horizontal pass
        for (y in 0 until height) {
            val rowOffset = y * width
            var sum = 0
            for (k in -r..r) {
                val sx = k.coerceIn(0, width - 1)
                sum += mask[rowOffset + sx].toInt() and 0xFF
            }
            for (x in 0 until width) {
                temp[rowOffset + x] = (sum / kernelSize).coerceIn(0, 255).toByte()
                val removeX = (x - r).coerceIn(0, width - 1)
                val addX = (x + r + 1).coerceIn(0, width - 1)
                sum += (mask[rowOffset + addX].toInt() and 0xFF) - (mask[rowOffset + removeX].toInt() and 0xFF)
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            var sum = 0
            for (k in -r..r) {
                val sy = k.coerceIn(0, height - 1)
                sum += temp[sy * width + x].toInt() and 0xFF
            }
            for (y in 0 until height) {
                out[y * width + x] = (sum / kernelSize).coerceIn(0, 255).toByte()
                val removeY = (y - r).coerceIn(0, height - 1)
                val addY = (y + r + 1).coerceIn(0, height - 1)
                sum += (temp[addY * width + x].toInt() and 0xFF) - (temp[removeY * width + x].toInt() and 0xFF)
            }
        }
        return out
    }

    /**
     * Composites the source bitmap with the current alpha mask onto Transparent, Solid, or Gradient background.
     */
    fun composeResultBitmap(
        source: Bitmap,
        alphaMask: ByteArray,
        backgroundStyle: CutoutBackgroundStyle,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)

        val isTransparent = backgroundStyle == CutoutBackgroundStyle.TRANSPARENT
        val c1 = backgroundStyle.primaryColor
        val c2 = backgroundStyle.secondaryColor
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        val r2 = if (c2 != null) Color.red(c2) else r1
        val g2 = if (c2 != null) Color.green(c2) else g1
        val b2 = if (c2 != null) Color.blue(c2) else b1

        for (y in 0 until h) {
            val rowOff = y * w
            val ty = y.toFloat() / max(1, h - 1)
            for (x in 0 until w) {
                val idx = rowOff + x
                val fgPixel = srcPixels[idx]
                val maskAlpha = alphaMask[idx].toInt() and 0xFF
                val srcAlpha = (fgPixel ushr 24) and 0xFF
                val a = (maskAlpha * srcAlpha) / 255

                if (isTransparent) {
                    dstPixels[idx] = (a shl 24) or (fgPixel and 0x00FFFFFF)
                } else {
                    val t = if (c2 != null) (x.toFloat() / max(1, w - 1) + ty) * 0.5f else 0f
                    val bgR = (r1 + (r2 - r1) * t).roundToInt()
                    val bgG = (g1 + (g2 - g1) * t).roundToInt()
                    val bgB = (b1 + (b2 - b1) * t).roundToInt()

                    val fgR = (fgPixel ushr 16) and 0xFF
                    val fgG = (fgPixel ushr 8) and 0xFF
                    val fgB = fgPixel and 0xFF
                    val invA = 255 - a

                    val outR = (fgR * a + bgR * invA) / 255
                    val outG = (fgG * a + bgG * invA) / 255
                    val outB = (fgB * a + bgB * invA) / 255
                    dstPixels[idx] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Exact sRGB -> Linear RGB -> CIE XYZ (D65) -> CIE L*a*b* color space transform.
     */
    fun rgbToLab(argb: Int, outLab: FloatArray) {
        val sr = ((argb ushr 16) and 0xFF) / 255.0
        val sg = ((argb ushr 8) and 0xFF) / 255.0
        val sb = (argb and 0xFF) / 255.0

        val r = if (sr <= 0.04045) sr / 12.92 else ((sr + 0.055) / 1.055).pow(2.4)
        val g = if (sg <= 0.04045) sg / 12.92 else ((sg + 0.055) / 1.055).pow(2.4)
        val b = if (sb <= 0.04045) sb / 12.92 else ((sb + 0.055) / 1.055).pow(2.4)

        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883

        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)

        outLab[0] = (116.0 * fy - 16.0).toFloat()
        outLab[1] = (500.0 * (fx - fy)).toFloat()
        outLab[2] = (200.0 * (fy - fz)).toFloat()
    }

    fun deltaE(argb1: Int, argb2: Int): Float {
        val l1 = FloatArray(3)
        val l2 = FloatArray(3)
        rgbToLab(argb1, l1)
        rgbToLab(argb2, l2)
        return sqrt(deltaESq(l1[0], l1[1], l1[2], l2[0], l2[1], l2[2]))
    }

    private fun labF(t: Double): Double =
        if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)

    private fun deltaESq(
        l1: Float, a1: Float, b1: Float,
        l2: Float, a2: Float, b2: Float,
    ): Float {
        val dl = l1 - l2
        val da = a1 - a2
        val db = b1 - b2
        return dl * dl + da * da + db * db
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val denom = abs(edge1 - edge0).coerceAtLeast(1e-5f)
        val t = ((x - edge0) / denom).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
