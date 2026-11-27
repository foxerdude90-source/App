package com.example.core.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class SaveCategory(val directory: String) {
    VIDEO(Environment.DIRECTORY_MOVIES),
    IMAGE(Environment.DIRECTORY_PICTURES),
    AUDIO(Environment.DIRECTORY_MUSIC),
    DOCUMENT(Environment.DIRECTORY_DOWNLOADS),
}

data class SavedMediaResult(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val locationLabel: String,
)

object MediaStoreSaver {

    fun saveFileToGallery(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        category: SaveCategory,
    ): SavedMediaResult {
        val safeName = sanitizeFileName(displayName)
        val sizeBytes = sourceFile.length()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when (category) {
                SaveCategory.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                SaveCategory.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                SaveCategory.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                SaveCategory.DOCUMENT -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = "${category.directory}/Clipper"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: error("Could not create MediaStore entry for $safeName")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: error("Could not open output stream for $safeName")

                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                return SavedMediaResult(
                    uri = uri,
                    displayName = safeName,
                    sizeBytes = sizeBytes,
                    locationLabel = "$relativePath/$safeName",
                )
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val baseDir = context.getExternalFilesDir(category.directory)
                ?: File(context.filesDir, "Clipper")
            val targetDir = File(baseDir, "Clipper").apply { mkdirs() }
            val destFile = File(targetDir, safeName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            return SavedMediaResult(
                uri = Uri.fromFile(destFile),
                displayName = safeName,
                sizeBytes = destFile.length(),
                locationLabel = destFile.absolutePath,
            )
        }
    }

    fun saveTextToDownloads(
        context: Context,
        content: String,
        displayName: String,
        mimeType: String = "text/plain",
    ): SavedMediaResult {
        val tempFile = File(context.cacheDir, "tmp_${System.currentTimeMillis()}_${sanitizeFileName(displayName)}")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            return saveFileToGallery(
                context = context,
                sourceFile = tempFile,
                displayName = displayName,
                mimeType = mimeType,
                category = SaveCategory.DOCUMENT,
            )
        } finally {
            tempFile.delete()
        }
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.ifEmpty { "clipper_${System.currentTimeMillis()}" }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        if (bytes < 1024L) return "$bytes B"
        val unit = 1024.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        val suffix = listOf("B", "KB", "MB", "GB", "TB")[exp]
        val value = bytes / unit.pow(exp.toDouble())
        return String.format(Locale.US, "%.2f %s", value, suffix)
    }

    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun computeSavingsPercent(originalBytes: Long, compressedBytes: Long): Int {
        if (originalBytes <= 0L) return 0
        val diff = originalBytes - compressedBytes
        return ((diff.toDouble() / originalBytes.toDouble()) * 100.0).toInt().coerceIn(-999, 99)
    }
}
