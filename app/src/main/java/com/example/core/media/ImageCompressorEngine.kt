package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class ImageOutputFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    JPEG("JPEG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp"),
    PNG("PNG", "png", "image/png"),
}

data class ImageCompressionConfig(
    val format: ImageOutputFormat = ImageOutputFormat.WEBP,
    val quality: Int = 80,
    val maxDimensionPx: Int = 1920,
)

data class ImageCompressionResult(
    val outputFile: File,
    val previewBitmap: Bitmap,
    val originalBytes: Long,
    val compressedBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val format: ImageOutputFormat,
)

object ImageCompressorEngine {

    fun readImageBounds(context: Context, uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return (options.outWidth.coerceAtLeast(0)) to (options.outHeight.coerceAtLeast(0))
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, maxEdgePx: Int): Bitmap {
        val (rawW, rawH) = readImageBounds(context, uri)
        val sampleSize = computeInSampleSize(rawW, rawH, maxEdgePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: error("Unable to decode selected image.")

        val longest = max(decoded.width, decoded.height)
        if (maxEdgePx > 0 && longest > maxEdgePx) {
            val scale = maxEdgePx.toFloat() / longest.toFloat()
            val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) decoded.recycle()
            return scaled
        }
        return decoded
    }

    suspend fun compressImage(
        context: Context,
        inputUri: Uri,
        originalBytes: Long,
        config: ImageCompressionConfig,
    ): ImageCompressionResult = withContext(Dispatchers.Default) {
        val (origW, origH) = readImageBounds(context, inputUri)
        val workingBitmap = decodeSampledBitmap(context, inputUri, config.maxDimensionPx)

        val outDir = File(context.cacheDir, "compressed_images").apply { mkdirs() }
        val outFile = File(outDir, "clipper_img_${System.currentTimeMillis()}.${config.format.extension}")

        val compressFormat = when (config.format) {
            ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }

        FileOutputStream(outFile).use { out ->
            val ok = workingBitmap.compress(compressFormat, config.quality.coerceIn(10, 100), out)
            if (!ok) error("Bitmap compression failed for format ${config.format.label}")
            out.flush()
        }

        val decodedPreview = BitmapFactory.decodeFile(outFile.absolutePath) ?: workingBitmap
        if (decodedPreview != workingBitmap) {
            workingBitmap.recycle()
        }

        ImageCompressionResult(
            outputFile = outFile,
            previewBitmap = decodedPreview,
            originalBytes = originalBytes,
            compressedBytes = outFile.length(),
            originalWidth = origW.takeIf { it > 0 } ?: decodedPreview.width,
            originalHeight = origH.takeIf { it > 0 } ?: decodedPreview.height,
            outputWidth = decodedPreview.width,
            outputHeight = decodedPreview.height,
            format = config.format,
        )
    }

    fun computeInSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 1
        var sample = 1
        val longest = max(width, height)
        while ((longest / (sample * 2)) >= maxEdgePx) {
            sample *= 2
        }
        return sample
    }
}
