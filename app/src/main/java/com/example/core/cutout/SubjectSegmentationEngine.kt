package com.example.core.cutout

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DetectedSubjectItem(
    val id: Int,
    val label: String,
    val mask: ByteArray,
    val coveragePercent: Float,
)

data class SubjectDetectionResult(
    val combinedMask: ByteArray,
    val subjects: List<DetectedSubjectItem>,
    val engineUsed: String,
    val foregroundPercent: Float,
)

/**
 * Multi-Subject Neural Background Remover & Edge-Barrier Saliency Engine.
 *
 * Architecture:
 *  1. Primary: Google ML Kit Subject Segmentation (detects ANY salient foreground subject —
 *     people, pets, products, vehicles, objects — plus individual selectable multi-subject masks).
 *  2. Automatic Anti-Wipeout Safeguard: verifies foreground coverage is meaningful (1.5%..98.5%).
 *  3. Fallback A: Bundled offline ML Kit Selfie Segmentation for portraits when Play Services
 *     subject model is unavailable or still downloading.
 *  4. Fallback B: Upgraded Sobel Edge-Barrier + K=4 CIELAB Background + Hole-Fill + Saliency
 *     Object Segmenter — mathematically guaranteed never to erase the entire subject.
 *  5. Pro Refinement: Morphological Expand/Shrink, Mask Invert, Edge Color Decontamination,
 *     and Tight Subject Auto-Crop.
 */
object SubjectSegmentationEngine {

    private const val MIN_VALID_COVERAGE = 0.015f
    private const val MAX_VALID_COVERAGE = 0.985f

    suspend fun detectAnySubject(
        source: Bitmap,
        fallbackSensitivity: Float = 26f,
    ): SubjectDetectionResult = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height

        // Stage 1: Google ML Kit Subject Segmentation (general-purpose neural foreground + multi-subject)
        val mlSubjectResult = tryMlKitSubjectSegmentation(source)
        if (mlSubjectResult != null && mlSubjectResult.foregroundPercent in (MIN_VALID_COVERAGE * 100f)..(MAX_VALID_COVERAGE * 100f)) {
            return@withContext mlSubjectResult
        }

        // Stage 2: Bundled offline ML Kit Selfie Segmentation (works 100% offline for human subjects)
        val portraitMask = try {
            BackgroundRemovalEngine.detectPortraitMask(source)
        } catch (_: Throwable) {
            null
        }
        if (portraitMask != null) {
            val ratio = computeForegroundRatio(portraitMask)
            if (ratio in MIN_VALID_COVERAGE..MAX_VALID_COVERAGE) {
                val pct = ratio * 100f
                return@withContext SubjectDetectionResult(
                    combinedMask = portraitMask,
                    subjects = listOf(DetectedSubjectItem(0, "Portrait Subject", portraitMask, pct)),
                    engineUsed = "ML Kit Portrait AI",
                    foregroundPercent = pct,
                )
            }
        }

        // Stage 3: Upgraded Edge-Barrier CIELAB Saliency Segmenter (never removes everything)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val salientMask = computeEdgeBarrierSalientMask(pixels, w, h, fallbackSensitivity)
        val pct = computeForegroundRatio(salientMask) * 100f
        SubjectDetectionResult(
            combinedMask = salientMask,
            subjects = listOf(DetectedSubjectItem(0, "Detected Subject", salientMask, pct)),
            engineUsed = "Edge-Barrier Saliency AI",
            foregroundPercent = pct,
        )
    }

    private suspend fun tryMlKitSubjectSegmentation(source: Bitmap): SubjectDetectionResult? {
        val w = source.width
        val h = source.height
        val subjectOptions = SubjectSegmenterOptions.SubjectResultOptions.Builder()
            .enableConfidenceMask()
            .build()
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .enableMultipleSubjects(subjectOptions)
            .build()

        val segmenter = try {
            SubjectSegmentation.getClient(options)
        } catch (_: Throwable) {
            return null
        }

        return try {
            val inputImage = InputImage.fromBitmap(source, 0)
            val result = suspendCancellableCoroutine { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { res ->
                        if (cont.isActive) cont.resume(res)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }

            val fgBuffer = result.foregroundConfidenceMask ?: return null
            fgBuffer.rewind()
            val total = w * h
            if (fgBuffer.remaining() < total) return null

            val combinedMask = ByteArray(total)
            for (i in 0 until total) {
                val conf = fgBuffer.get()
                val refined = smoothStep(0.30f, 0.72f, conf)
                combinedMask[i] = (refined * 255f).roundToInt().coerceIn(0, 255).toByte()
            }

            val subjectItems = mutableListOf<DetectedSubjectItem>()
            result.subjects.forEachIndexed { idx, subject ->
                val sBuf = subject.confidenceMask ?: return@forEachIndexed
                sBuf.rewind()
                val sw = subject.width
                val sh = subject.height
                val sx = subject.startX
                val sy = subject.startY
                if (sw <= 0 || sh <= 0 || sBuf.remaining() < sw * sh) return@forEachIndexed

                val fullSubMask = ByteArray(total)
                for (ry in 0 until sh) {
                    val dstY = sy + ry
                    if (dstY !in 0 until h) continue
                    for (rx in 0 until sw) {
                        val dstX = sx + rx
                        if (dstX !in 0 until w) continue
                        val conf = sBuf.get(ry * sw + rx)
                        val refined = smoothStep(0.30f, 0.72f, conf)
                        fullSubMask[dstY * w + dstX] = (refined * 255f).roundToInt().coerceIn(0, 255).toByte()
                    }
                }
                val cov = computeForegroundRatio(fullSubMask) * 100f
                if (cov >= 0.5f) {
                    subjectItems.add(
                        DetectedSubjectItem(
                            id = idx + 1,
                            label = "Subject ${idx + 1}",
                            mask = fullSubMask,
                            coveragePercent = cov,
                        )
                    )
                }
            }

            val fgPercent = computeForegroundRatio(combinedMask) * 100f
            SubjectDetectionResult(
                combinedMask = combinedMask,
                subjects = subjectItems,
                engineUsed = "ML Kit Subject AI",
                foregroundPercent = fgPercent,
            )
        } catch (_: Throwable) {
            null
        } finally {
            try {
                segmenter.close()
            } catch (_: Throwable) {
                // Ignore close failures
            }
        }
    }

    /**
     * Upgraded Edge-Barrier + K=4 CIELAB Border Clustering + Morphological Hole-Filling +
     * Center-Prior Saliency Safeguard. Mathematically prevents wiping out the entire subject.
     */
    fun computeEdgeBarrierSalientMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        sensitivity: Float = 26f,
    ): ByteArray {
        val total = width * height
        if (total == 0) return ByteArray(0)

        val labL = FloatArray(total)
        val labA = FloatArray(total)
        val labB = FloatArray(total)
        val tmpLab = FloatArray(3)
        for (i in 0 until total) {
            BackgroundRemovalEngine.rgbToLab(pixels[i], tmpLab)
            labL[i] = tmpLab[0]
            labA[i] = tmpLab[1]
            labB[i] = tmpLab[2]
        }

        // 1. Compute Sobel edge magnitude on L* channel to act as a leak barrier
        val edgeMag = FloatArray(total)
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val tl = labL[row - width + x - 1]
                val tc = labL[row - width + x]
                val tr = labL[row - width + x + 1]
                val ml = labL[row + x - 1]
                val mr = labL[row + x + 1]
                val bl = labL[row + width + x - 1]
                val bc = labL[row + width + x]
                val br = labL[row + width + x + 1]
                val gx = -tl - 2f * ml - bl + tr + 2f * mr + br
                val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                edgeMag[row + x] = hypot(gx, gy) * 0.25f
            }
        }

        // 2. Collect border sample pixels (2px rim) and fit K=4 background clusters in CIELAB
        val borderIndices = ArrayList<Int>(4 * (width + height))
        for (x in 0 until width) {
            borderIndices.add(x)
            borderIndices.add((height - 1) * width + x)
            if (height > 4) {
                borderIndices.add(width + x)
                borderIndices.add((height - 2) * width + x)
            }
        }
        for (y in 2 until max(2, height - 2)) {
            borderIndices.add(y * width)
            borderIndices.add(y * width + (width - 1))
        }

        val k = 4
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
        repeat(8) {
            sumL.fill(0f)
            sumA.fill(0f)
            sumB.fill(0f)
            counts.fill(0)
            for (idx in borderIndices) {
                var bestC = 0
                var bestD = Float.MAX_VALUE
                for (c in 0 until k) {
                    val dl = labL[idx] - cL[c]
                    val da = labA[idx] - cA[c]
                    val db = labB[idx] - cB[c]
                    val d = dl * dl + da * da + db * db
                    if (d < bestD) {
                        bestD = d
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

        val bgDist = FloatArray(total)
        for (i in 0 until total) {
            var minD = Float.MAX_VALUE
            for (c in 0 until k) {
                val dl = labL[i] - cL[c]
                val da = labA[i] - cA[c]
                val db = labB[i] - cB[c]
                val d = dl * dl + da * da + db * db
                if (d < minD) minD = d
            }
            bgDist[i] = sqrt(minD)
        }

        // 3. Edge-barrier border-seeded flood-fill (stops at strong object contours!)
        val hardThresh = sensitivity.coerceIn(8f, 65f)
        val softBand = hardThresh + 11f
        val edgeStopThreshold = 14.5f

        val visited = BooleanArray(total)
        val mask = ByteArray(total) { 0xFF.toByte() }
        val queue = ArrayDeque<Int>(borderIndices.size * 2)

        for (idx in borderIndices) {
            if (!visited[idx] && bgDist[idx] <= softBand) {
                visited[idx] = true
                queue.add(idx)
            }
        }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = bgDist[cur]
            if (d <= hardThresh) {
                mask[cur] = 0
            } else {
                val alpha = smoothStep(hardThresh, softBand, d)
                mask[cur] = (alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
            }

            // Halt flood expansion across crisp object edges so subject interiors stay protected
            if (d <= hardThresh * 1.08f && edgeMag[cur] < edgeStopThreshold) {
                val x = cur % width
                val y = cur / width
                if (x > 0) {
                    val n = cur - 1
                    if (!visited[n] && bgDist[n] <= softBand) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (x + 1 < width) {
                    val n = cur + 1
                    if (!visited[n] && bgDist[n] <= softBand) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (y > 0) {
                    val n = cur - width
                    if (!visited[n] && bgDist[n] <= softBand) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
                if (y + 1 < height) {
                    val n = cur + width
                    if (!visited[n] && bgDist[n] <= softBand) {
                        visited[n] = true
                        queue.add(n)
                    }
                }
            }
        }

        // 4. Fill enclosed holes inside the subject
        val holeFilled = fillEnclosedHoles(mask, width, height)

        // 5. Anti-Wipeout Safeguard: NEVER erase everything!
        val fgRatio = computeForegroundRatio(holeFilled)
        if (fgRatio < 0.03f) {
            return buildCenterPriorSaliencyMask(bgDist, width, height)
        }

        return BackgroundRemovalEngine.featherMask(holeFilled, width, height, radius = 1)
    }

    /**
     * Fills transparent holes completely enclosed inside the foreground subject
     * (i.e. transparent pixels not reachable from the image perimeter).
     */
    private fun fillEnclosedHoles(mask: ByteArray, width: Int, height: Int): ByteArray {
        val total = width * height
        val outsideBg = BooleanArray(total)
        val queue = ArrayDeque<Int>(2 * (width + height))

        fun seedIfTransparent(idx: Int) {
            if (!outsideBg[idx] && (mask[idx].toInt() and 0xFF) < 32) {
                outsideBg[idx] = true
                queue.add(idx)
            }
        }
        for (x in 0 until width) {
            seedIfTransparent(x)
            seedIfTransparent((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            seedIfTransparent(y * width)
            seedIfTransparent(y * width + (width - 1))
        }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val x = cur % width
            val y = cur / width
            if (x > 0) seedIfTransparent(cur - 1)
            if (x + 1 < width) seedIfTransparent(cur + 1)
            if (y > 0) seedIfTransparent(cur - width)
            if (y + 1 < height) seedIfTransparent(cur + width)
        }

        val out = mask.copyOf()
        for (i in 0 until total) {
            if (!outsideBg[i] && (out[i].toInt() and 0xFF) < 128) {
                out[i] = 0xFF.toByte()
            }
        }
        return out
    }

    /**
     * Fallback saliency map combining CIELAB contrast from border + Gaussian center prior.
     * Guarantees the primary foreground subject is preserved rather than returning an empty canvas.
     */
    private fun buildCenterPriorSaliencyMask(
        bgDist: FloatArray,
        width: Int,
        height: Int,
    ): ByteArray {
        val total = width * height
        val scores = FloatArray(total)
        var maxScore = 0.001f
        val cx = width * 0.5f
        val cy = height * 0.5f
        val invRx = 1f / max(1f, width * 0.55f)
        val invRy = 1f / max(1f, height * 0.55f)

        for (y in 0 until height) {
            val dy = (y - cy) * invRy
            for (x in 0 until width) {
                val dx = (x - cx) * invRx
                val centerWeight = kotlin.math.exp(-0.5f * (dx * dx + dy * dy))
                val idx = y * width + x
                val s = (bgDist[idx] + 4f) * (0.35f + 0.65f * centerWeight)
                scores[idx] = s
                if (s > maxScore) maxScore = s
            }
        }

        // Otsu-like adaptive threshold around 42% of peak saliency
        val lowEdge = maxScore * 0.32f
        val highEdge = maxScore * 0.52f
        val out = ByteArray(total)
        for (i in 0 until total) {
            val alpha = smoothStep(lowEdge, highEdge, scores[i])
            out[i] = (alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
        return BackgroundRemovalEngine.featherMask(out, width, height, radius = 1)
    }

    fun computeForegroundRatio(mask: ByteArray): Float {
        if (mask.isEmpty()) return 0f
        var fg = 0
        for (b in mask) {
            if ((b.toInt() and 0xFF) >= 128) fg++
        }
        return fg.toFloat() / mask.size.toFloat()
    }

    /**
     * Inverts foreground & background alpha mask.
     */
    fun invertMask(mask: ByteArray): ByteArray =
        ByteArray(mask.size) { i -> (255 - (mask[i].toInt() and 0xFF)).toByte() }

    /**
     * Morphological Expand (deltaPx > 0, dilation) or Shrink (deltaPx < 0, erosion) on alpha mask.
     */
    fun morphologicalAdjustMask(
        mask: ByteArray,
        width: Int,
        height: Int,
        deltaPx: Int,
    ): ByteArray {
        val r = abs(deltaPx).coerceIn(0, 5)
        if (r == 0) return mask.copyOf()
        val dilate = deltaPx > 0
        val temp = ByteArray(width * height)
        val out = ByteArray(width * height)

        // Separable 1D horizontal max/min pass
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var extreme = if (dilate) 0 else 255
                val xStart = max(0, x - r)
                val xEnd = min(width - 1, x + r)
                for (nx in xStart..xEnd) {
                    val v = mask[row + nx].toInt() and 0xFF
                    extreme = if (dilate) max(extreme, v) else min(extreme, v)
                }
                temp[row + x] = extreme.toByte()
            }
        }

        // Separable 1D vertical max/min pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var extreme = if (dilate) 0 else 255
                val yStart = max(0, y - r)
                val yEnd = min(height - 1, y + r)
                for (ny in yStart..yEnd) {
                    val v = temp[ny * width + x].toInt() and 0xFF
                    extreme = if (dilate) max(extreme, v) else min(extreme, v)
                }
                out[y * width + x] = extreme.toByte()
            }
        }
        return out
    }

    /**
     * Edge Color Decontamination (De-fringing):
     * Replaces background color bleed in semi-transparent boundary pixels (alpha 8..240)
     * with the nearest solid interior foreground pixel color so hair/edges look razor clean.
     */
    fun decontaminateEdgeColors(source: Bitmap, alphaMask: ByteArray): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        val radius = 4

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val a = alphaMask[idx].toInt() and 0xFF
                if (a in 10..240) {
                    var sumR = 0L
                    var sumG = 0L
                    var sumB = 0L
                    var weightSum = 0L
                    val y0 = max(0, y - radius)
                    val y1 = min(h - 1, y + radius)
                    val x0 = max(0, x - radius)
                    val x1 = min(w - 1, x + radius)
                    for (ny in y0..y1) {
                        val nRow = ny * w
                        for (nx in x0..x1) {
                            val nIdx = nRow + nx
                            val na = alphaMask[nIdx].toInt() and 0xFF
                            if (na >= 245) {
                                val c = src[nIdx]
                                sumR += (c ushr 16) and 0xFF
                                sumG += (c ushr 8) and 0xFF
                                sumB += c and 0xFF
                                weightSum++
                            }
                        }
                    }
                    if (weightSum > 0L) {
                        val blend = 1f - (a / 245f)
                        val cur = src[idx]
                        val cr = (cur ushr 16) and 0xFF
                        val cg = (cur ushr 8) and 0xFF
                        val cb = cur and 0xFF
                        val fr = (sumR / weightSum).toInt()
                        val fg = (sumG / weightSum).toInt()
                        val fb = (sumB / weightSum).toInt()
                        val nr = (cr + (fr - cr) * blend).roundToInt().coerceIn(0, 255)
                        val ng = (cg + (fg - cg) * blend).roundToInt().coerceIn(0, 255)
                        val nb = (cb + (fb - cb) * blend).roundToInt().coerceIn(0, 255)
                        dst[idx] = (cur and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
                    }
                }
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(dst, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Crops a cutout bitmap tightly to the bounding box of opaque/semi-opaque subject pixels.
     */
    fun autoCropToSubjectBounds(
        composed: Bitmap,
        alphaMask: ByteArray,
        paddingPx: Int = 12,
    ): Bitmap {
        val w = composed.width
        val h = composed.height
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((alphaMask[row + x].toInt() and 0xFF) >= 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return composed

        val left = (minX - paddingPx).coerceAtLeast(0)
        val top = (minY - paddingPx).coerceAtLeast(0)
        val right = (maxX + paddingPx).coerceAtMost(w - 1)
        val bottom = (maxY + paddingPx).coerceAtMost(h - 1)
        val cropW = (right - left + 1).coerceAtLeast(1)
        val cropH = (bottom - top + 1).coerceAtLeast(1)
        return Bitmap.createBitmap(composed, left, top, cropW, cropH)
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val denom = abs(edge1 - edge0).coerceAtLeast(1e-5f)
        val t = ((x - edge0) / denom).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
