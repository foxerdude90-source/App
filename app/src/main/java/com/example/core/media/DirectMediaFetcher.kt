package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class DetectedMediaKind {
    VIDEO,
    IMAGE,
    AUDIO,
    OTHER,
}

data class ProbedMediaInfo(
    val sourceUrl: String?,
    val localUri: Uri?,
    val localFile: File?,
    val fileName: String,
    val mimeType: String,
    val kind: DetectedMediaKind,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val frameRate: Float = 0f,
    val bitrateBps: Long = 0L,
    val hasAudio: Boolean = false,
    val thumbnail: Bitmap? = null,
)

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult()
    data class Rejected(val reason: String) : UrlValidationResult()
}

/**
 * Compliant direct open-access HTTP/HTTPS media fetcher and local media inspector.
 *
 * Uses a standard honest User-Agent and strictly respects HTTP status codes and
 * platform boundaries — it does not bypass anti-bot protections, scrape protected
 * third-party streaming platforms, or circumvent DRM.
 */
class DirectMediaFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {

    companion object {
        private const val HONEST_USER_AGENT = "ClipperMediaStudio/1.0 (Android)"
        private const val MAX_HTML_PEEK_BYTES = 128 * 1024

        private val RESTRICTED_PLATFORM_HOSTS = setOf(
            "youtube.com",
            "youtu.be",
            "instagram.com",
            "tiktok.com",
            "netflix.com",
            "spotify.com",
            "disneyplus.com",
            "hulu.com",
            "primevideo.com",
            "hbomax.com",
            "max.com",
            "x.com",
            "twitter.com",
            "facebook.com",
            "fb.watch",
        )

        private val OG_VIDEO_REGEX = Regex(
            """<meta[^>]+(?:property|name)=["']og:video(?::secure_url|:url)?["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val OG_IMAGE_REGEX = Regex(
            """<meta[^>]+(?:property|name)=["']og:image(?::secure_url|:url)?["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )

        fun validateUrl(rawInput: String): UrlValidationResult {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) {
                return UrlValidationResult.Rejected("Paste a direct http:// or https:// media URL first.")
            }
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return UrlValidationResult.Rejected("Invalid URL syntax. Example: https://example.com/clip.mp4")
            }

            val scheme = uri.scheme?.lowercase(Locale.US)
            if (scheme != "http" && scheme != "https") {
                return UrlValidationResult.Rejected("Only http:// and https:// URLs are supported.")
            }

            val host = uri.host?.lowercase(Locale.US)?.trimEnd('.')
                ?: return UrlValidationResult.Rejected("URL must include a valid host name.")

            if (isPrivateOrLoopbackHost(host)) {
                return UrlValidationResult.Rejected("Local or private network addresses are blocked for security.")
            }

            val isRestrictedPlatform = RESTRICTED_PLATFORM_HOSTS.any { restricted ->
                host == restricted || host.endsWith(".$restricted")
            }
            if (isRestrictedPlatform) {
                return UrlValidationResult.Rejected(
                    "Clipper does not scrape or bypass protections on $host. " +
                        "Paste a direct open-access media file URL (.mp4, .webm, .jpg, .png, .mp3) or import a local file from your device."
                )
            }

            return UrlValidationResult.Valid(trimmed)
        }

        fun isPrivateOrLoopbackHost(host: String): Boolean {
            val normalized = host.lowercase(Locale.US)
            if (normalized == "localhost" || normalized.endsWith(".local") || normalized == "::1") {
                return true
            }
            // Check raw IPv4 literals synchronously without DNS lookups in unit tests
            val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
            val match = ipv4Regex.matchEntire(normalized)
            if (match != null) {
                val a = match.groupValues[1].toIntOrNull() ?: return true
                val b = match.groupValues[2].toIntOrNull() ?: return true
                return a == 0 ||
                    a == 10 ||
                    a == 127 ||
                    (a == 169 && b == 254) ||
                    (a == 172 && b in 16..31) ||
                    (a == 192 && b == 168)
            }
            return false
        }

        fun classifyMimeType(mimeType: String, urlOrName: String): DetectedMediaKind {
            val lowerMime = mimeType.lowercase(Locale.US)
            val lowerPath = urlOrName.lowercase(Locale.US).substringBefore('?')
            return when {
                lowerMime.startsWith("video/") ||
                    lowerPath.endsWith(".mp4") || lowerPath.endsWith(".mov") ||
                    lowerPath.endsWith(".webm") || lowerPath.endsWith(".mkv") ||
                    lowerPath.endsWith(".m4v") -> DetectedMediaKind.VIDEO

                lowerMime.startsWith("image/") ||
                    lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                    lowerPath.endsWith(".png") || lowerPath.endsWith(".webp") ||
                    lowerPath.endsWith(".gif") -> DetectedMediaKind.IMAGE

                lowerMime.startsWith("audio/") ||
                    lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") ||
                    lowerPath.endsWith(".wav") || lowerPath.endsWith(".aac") ||
                    lowerPath.endsWith(".ogg") -> DetectedMediaKind.AUDIO

                else -> DetectedMediaKind.OTHER
            }
        }

        fun extractOpenGraphMediaUrl(html: String): String? {
            OG_VIDEO_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            OG_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }
    }

    suspend fun probeRemoteUrl(rawUrl: String): ProbedMediaInfo = withContext(Dispatchers.IO) {
        val validated = when (val result = validateUrl(rawUrl)) {
            is UrlValidationResult.Valid -> result.normalizedUrl
            is UrlValidationResult.Rejected -> throw IllegalArgumentException(result.reason)
        }

        // Verify resolved DNS does not point to a private/loopback address (SSRF defense)
        val parsedUri = URI(validated)
        val addresses = try {
            InetAddress.getAllByName(parsedUri.host)
        } catch (_: Exception) {
            emptyArray()
        }
        if (addresses.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }) {
            throw IllegalArgumentException("Resolved host points to a private or loopback network address.")
        }

        var targetUrl = validated
        var headInfo = requestProbeHeaders(targetUrl)

        // If an open public HTML page advertises an open-graph direct media URL, inspect that URL once.
        if (headInfo.mimeType.startsWith("text/html")) {
            val ogMediaUrl = peekPublicOpenGraphMedia(targetUrl)
                ?: throw IllegalArgumentException(
                    "This link returned an HTML webpage with no direct open-access media file. " +
                        "Please paste a direct media URL ending in .mp4, .jpg, .png, or .mp3."
                )
            when (val ogCheck = validateUrl(ogMediaUrl)) {
                is UrlValidationResult.Valid -> {
                    targetUrl = ogCheck.normalizedUrl
                    headInfo = requestProbeHeaders(targetUrl)
                }
                is UrlValidationResult.Rejected -> throw IllegalArgumentException(ogCheck.reason)
            }
        }

        val kind = classifyMimeType(headInfo.mimeType, targetUrl)
        if (kind == DetectedMediaKind.OTHER) {
            throw IllegalArgumentException(
                "Unsupported content type (${headInfo.mimeType}). Provide a direct video, image, or audio link."
            )
        }

        ProbedMediaInfo(
            sourceUrl = targetUrl,
            localUri = null,
            localFile = null,
            fileName = headInfo.fileName,
            mimeType = headInfo.mimeType,
            kind = kind,
            sizeBytes = headInfo.contentLength,
        )
    }

    suspend fun downloadRemoteToCache(
        context: Context,
        info: ProbedMediaInfo,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): ProbedMediaInfo = withContext(Dispatchers.IO) {
        val url = info.sourceUrl ?: throw IllegalStateException("No remote URL to download.")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HONEST_USER_AGENT)
            .get()
            .build()

        val cacheDir = File(context.cacheDir, "fetched_media").apply { mkdirs() }
        val outFile = File(cacheDir, "${System.currentTimeMillis()}_${MediaStoreSaver.sanitizeFileName(info.fileName)}")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(mapHttpError(response.code))
            }
            val body = response.body ?: throw IllegalStateException("Empty response body from server.")
            val total = body.contentLength().takeIf { it > 0L } ?: info.sizeBytes

            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }
        }

        inspectLocalFile(context, outFile, info.sourceUrl, info.mimeType, info.fileName)
    }

    suspend fun inspectLocalUri(context: Context, uri: Uri): ProbedMediaInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var fileName = "imported_media_${System.currentTimeMillis()}"
        var sizeBytes = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val kind = classifyMimeType(mimeType, fileName)
        var width = 0
        var height = 0
        var durationMs = 0L
        var frameRate = 0f
        var bitrateBps = 0L
        var hasAudio = false
        var thumb: Bitmap? = null

        if (kind == DetectedMediaKind.VIDEO || kind == DetectedMediaKind.AUDIO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    ?: estimateFrameRate(retriever, durationMs)
                if (kind == DetectedMediaKind.VIDEO) {
                    thumb = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Exception) {
                // Keep partial metadata if device codec cannot decode some fields
            } finally {
                retriever.release()
            }
        } else if (kind == DetectedMediaKind.IMAGE) {
            val bounds = ImageCompressorEngine.readImageBounds(context, uri)
            width = bounds.first
            height = bounds.second
        }

        ProbedMediaInfo(
            sourceUrl = null,
            localUri = uri,
            localFile = null,
            fileName = fileName,
            mimeType = mimeType,
            kind = kind,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            durationMs = durationMs,
            frameRate = frameRate,
            bitrateBps = bitrateBps,
            hasAudio = hasAudio,
            thumbnail = thumb,
        )
    }

    suspend fun inspectLocalFile(
        context: Context,
        file: File,
        sourceUrl: String? = null,
        fallbackMime: String = "video/mp4",
        displayName: String = file.name,
    ): ProbedMediaInfo = withContext(Dispatchers.IO) {
        val kind = classifyMimeType(fallbackMime, displayName)
        var width = 0
        var height = 0
        var durationMs = 0L
        var frameRate = 0f
        var bitrateBps = 0L
        var hasAudio = false
        var thumb: Bitmap? = null

        if (kind == DetectedMediaKind.VIDEO || kind == DetectedMediaKind.AUDIO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    ?: estimateFrameRate(retriever, durationMs)
                if (kind == DetectedMediaKind.VIDEO) {
                    thumb = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Exception) {
                // Keep file-level metadata
            } finally {
                retriever.release()
            }
        } else if (kind == DetectedMediaKind.IMAGE) {
            val bounds = ImageCompressorEngine.readImageBounds(context, Uri.fromFile(file))
            width = bounds.first
            height = bounds.second
        }

        ProbedMediaInfo(
            sourceUrl = sourceUrl,
            localUri = Uri.fromFile(file),
            localFile = file,
            fileName = displayName,
            mimeType = fallbackMime,
            kind = kind,
            sizeBytes = file.length(),
            width = width,
            height = height,
            durationMs = durationMs,
            frameRate = frameRate,
            bitrateBps = bitrateBps,
            hasAudio = hasAudio,
            thumbnail = thumb,
        )
    }

    private data class HeaderProbe(
        val mimeType: String,
        val contentLength: Long,
        val fileName: String,
    )

    private fun requestProbeHeaders(url: String): HeaderProbe {
        val headReq = Request.Builder()
            .url(url)
            .header("User-Agent", HONEST_USER_AGENT)
            .head()
            .build()

        client.newCall(headReq).execute().use { resp ->
            if (resp.isSuccessful) {
                return parseHeaderProbe(url, resp.header("Content-Type"), resp.header("Content-Length"), resp.header("Content-Disposition"))
            }
            if (resp.code == 405 || resp.code == 501) {
                // Fallback to single-byte Range GET when HEAD is disabled by server
                val rangeReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", HONEST_USER_AGENT)
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
                client.newCall(rangeReq).execute().use { rangeResp ->
                    if (!rangeResp.isSuccessful) {
                        throw IllegalStateException(mapHttpError(rangeResp.code))
                    }
                    val totalFromRange = rangeResp.header("Content-Range")
                        ?.substringAfter('/', "")
                        ?.toLongOrNull()
                    val lengthHeader = totalFromRange?.toString() ?: rangeResp.header("Content-Length")
                    return parseHeaderProbe(
                        url,
                        rangeResp.header("Content-Type"),
                        lengthHeader,
                        rangeResp.header("Content-Disposition"),
                    )
                }
            }
            throw IllegalStateException(mapHttpError(resp.code))
        }
    }

    private fun peekPublicOpenGraphMedia(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HONEST_USER_AGENT)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val bytes = body.byteStream().use { input ->
                val out = ByteArray(MAX_HTML_PEEK_BYTES)
                var total = 0
                while (total < MAX_HTML_PEEK_BYTES) {
                    val r = input.read(out, total, MAX_HTML_PEEK_BYTES - total)
                    if (r == -1) break
                    total += r
                }
                out.copyOf(total)
            }
            val html = String(bytes, Charsets.UTF_8)
            return extractOpenGraphMediaUrl(html)
        }
    }

    private fun parseHeaderProbe(
        url: String,
        contentTypeHeader: String?,
        contentLengthHeader: String?,
        contentDisposition: String?,
    ): HeaderProbe {
        val mime = contentTypeHeader?.substringBefore(';')?.trim()?.lowercase(Locale.US)
            ?: inferMimeFromUrl(url)
        val length = contentLengthHeader?.toLongOrNull() ?: -1L
        val dispName = contentDisposition?.let { disp ->
            Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(disp)?.groupValues?.getOrNull(1)
        }
        val pathSegment = try {
            URI(url).path?.substringAfterLast('/')?.takeIf { it.contains('.') }
        } catch (_: Exception) {
            null
        }
        val ext = mimeToExtension(mime)
        val finalName = MediaStoreSaver.sanitizeFileName(
            dispName ?: pathSegment ?: "clipper_media_${System.currentTimeMillis()}.$ext"
        )
        return HeaderProbe(mimeType = mime, contentLength = length, fileName = finalName)
    }

    private fun estimateFrameRate(retriever: MediaMetadataRetriever, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            ?.toFloatOrNull() ?: return 0f
        return if (frameCount > 0f) (frameCount / (durationMs / 1000f)) else 0f
    }

    private fun mapHttpError(code: Int): String = when (code) {
        401, 403 -> "Server denied access (HTTP $code). Clipper respects server access controls and does not bypass bot protection or authentication."
        404 -> "Media file not found at this URL (HTTP 404)."
        429 -> "Server rate limit reached (HTTP 429). Please try again later."
        else -> "Server returned HTTP $code."
    }

    private fun inferMimeFromUrl(url: String): String {
        val lower = url.lowercase(Locale.US).substringBefore('?')
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".wav") -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private fun mimeToExtension(mime: String): String = when {
        mime.contains("mp4") && mime.startsWith("video") -> "mp4"
        mime.contains("webm") -> "webm"
        mime.contains("quicktime") -> "mov"
        mime.contains("jpeg") -> "jpg"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("gif") -> "gif"
        mime.contains("mpeg") -> "mp3"
        mime.contains("audio/mp4") || mime.contains("aac") -> "m4a"
        mime.contains("wav") -> "wav"
        else -> "bin"
    }
}
