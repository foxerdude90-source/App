package com.example.core.media

import android.content.Context
import android.os.Build
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class QualityTrackType(val label: String) {
    AUTO("Recommended"),
    VIDEO("Video"),
    AUDIO("Audio Only"),
}

data class MediaQualityOption(
    val id: String,
    val formatSelector: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val ext: String,
    val trackType: QualityTrackType,
    val heightPx: Int = 0,
    val fps: Int = 0,
    val fileSizeBytes: Long = -1L,
    val requiresAudioMux: Boolean = false,
    val extractAudioOnly: Boolean = false,
)

data class FetchedMediaMetadata(
    val sourceUrl: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val extractorName: String?,
    val width: Int,
    val height: Int,
    val qualities: List<MediaQualityOption>,
)

data class DownloadProgressUpdate(
    val progressFraction: Float, // 0f..1f (-1f when indeterminate)
    val etaSeconds: Long,
    val statusLine: String,
)

/**
 * Production on-device wrapper around `io.github.junkfood02.youtubedl-android` (yt-dlp + FFmpeg).
 * Uses exclusively the signed binaries bundled inside the APK (zero runtime binary self-updates).
 */
object YoutubeDlMediaEngine {

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false
    @Volatile
    private var ffmpegReady = false

    suspend fun ensureInitialized(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                val appCtx = context.applicationContext
                YoutubeDL.getInstance().init(appCtx)
                try {
                    FFmpeg.getInstance().init(appCtx)
                    ffmpegReady = true
                } catch (_: Throwable) {
                    ffmpegReady = false
                }
                initialized = true
            }
        }
    }

    fun normalizeAndValidateUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotEmpty()) { "Enter a valid media URL first." }
        val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
        require(
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true)
        ) {
            "Only http:// and https:// URLs are supported."
        }
        return normalized
    }

    /**
     * Fetches rich media metadata & available formats via `YoutubeDL.getInstance().getInfo(request)`.
     */
    suspend fun fetchMediaMetadata(
        context: Context,
        rawUrl: String,
    ): FetchedMediaMetadata = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeAndValidateUrl(rawUrl)
        ensureInitialized(context)

        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
        }

        val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
        val rawFormats: List<VideoFormat> = info.formats.orEmpty()
        val parsedFormats = rawFormats.mapNotNull { fmt ->
            val formatId = fmt.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RawStreamDescriptor(
                formatId = formatId,
                ext = fmt.ext?.lowercase(Locale.US)?.ifBlank { "mp4" } ?: "mp4",
                vcodec = fmt.vcodec?.lowercase(Locale.US) ?: "none",
                acodec = fmt.acodec?.lowercase(Locale.US) ?: "none",
                width = fmt.width,
                height = fmt.height,
                fps = fmt.fps,
                fileSizeBytes = when {
                    fmt.fileSize > 0L -> fmt.fileSize
                    fmt.fileSizeApproximate > 0L -> fmt.fileSizeApproximate
                    else -> -1L
                },
                formatNote = fmt.formatNote.orEmpty(),
            )
        }

        val qualityOptions = buildQualityOptions(parsedFormats, hasFfmpeg = ffmpegReady)
        val safeTitle = info.title?.trim()?.takeIf { it.isNotBlank() } ?: "Media Clip"

        FetchedMediaMetadata(
            sourceUrl = cleanUrl,
            title = safeTitle,
            uploader = info.uploader?.trim()?.takeIf { it.isNotBlank() },
            durationSec = info.duration.toLong().coerceAtLeast(0L),
            thumbnailUrl = info.thumbnail?.trim()?.takeIf { it.isNotBlank() },
            extractorName = info.extractorKey?.trim()?.takeIf { it.isNotBlank() }
                ?: info.extractor?.trim()?.takeIf { it.isNotBlank() },
            width = info.width,
            height = info.height,
            qualities = qualityOptions,
        )
    }

    /**
     * Executes `YoutubeDLRequest` in a coroutine, streams live progress, and saves the
     * completed file cleanly into `Environment.DIRECTORY_DOWNLOADS` via MediaStore.
     */
    suspend fun downloadMediaToDownloads(
        context: Context,
        url: String,
        quality: MediaQualityOption,
        titleHint: String,
        processId: String,
        onProgress: (DownloadProgressUpdate) -> Unit,
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeAndValidateUrl(url)
        ensureInitialized(context)

        val jobDir = File(context.cacheDir, "ytdl_job_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        try {
            val outputTemplate = File(jobDir, "%(title).80B.%(ext)s").absolutePath
            val request = YoutubeDLRequest(cleanUrl).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--restrict-filenames")
                addOption("-f", quality.formatSelector)
                addOption("-o", outputTemplate)

                if (quality.extractAudioOnly) {
                    if (ffmpegReady) {
                        addOption("-x")
                        addOption("--audio-format", "m4a")
                        addOption("--audio-quality", "0")
                    }
                } else if (ffmpegReady && quality.requiresAudioMux) {
                    addOption("--merge-output-format", "mp4")
                } else if (ffmpegReady && quality.trackType == QualityTrackType.AUTO) {
                    addOption("--merge-output-format", "mp4")
                }
            }

            onProgress(DownloadProgressUpdate(-1f, -1L, "Starting download engine..."))

            YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val cleanLine = line.trim().take(120)
                val fraction = if (progress >= 0f) (progress / 100f).coerceIn(0f, 1f) else -1f
                onProgress(
                    DownloadProgressUpdate(
                        progressFraction = fraction,
                        etaSeconds = etaInSeconds,
                        statusLine = cleanLine.ifBlank {
                            if (progress >= 0f) String.format(Locale.US, "Downloading %.1f%%", progress)
                            else "Processing media stream..."
                        },
                    ),
                )
            }

            // Locate the completed output media file inside jobDir (ignoring .part / .ytdl temp files)
            val downloadedFile = jobDir.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".temp", ignoreCase = true)
                }
                ?.maxByOrNull { it.length() }
                ?: error("Download completed without producing a playable output file.")

            onProgress(DownloadProgressUpdate(0.99f, 0L, "Saving to Downloads..."))

            val ext = downloadedFile.extension.lowercase(Locale.US).ifBlank { quality.ext.ifBlank { "mp4" } }
            val safeBase = MediaStoreSaver.sanitizeFileName(titleHint.ifBlank { downloadedFile.nameWithoutExtension })
                .take(72)
            val displayName = if (safeBase.endsWith(".$ext", ignoreCase = true)) safeBase else "$safeBase.$ext"
            val mimeType = inferMimeFromExtension(ext)

            saveToPublicDownloads(
                context = context,
                sourceFile = downloadedFile,
                displayName = displayName,
                mimeType = mimeType,
            )
        } finally {
            jobDir.deleteRecursively()
        }
    }

    fun cancelDownload(processId: String) {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }
    }

    private fun saveToPublicDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
    ): SavedMediaResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreSaver.saveFileToGallery(
                context = context,
                sourceFile = sourceFile,
                displayName = displayName,
                mimeType = mimeType,
                category = SaveCategory.DOCUMENT, // Environment.DIRECTORY_DOWNLOADS via MediaStore.Downloads
            )
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val clipperDir = File(downloadsDir, "Clipper").apply { mkdirs() }
            val destFile = File(clipperDir, MediaStoreSaver.sanitizeFileName(displayName))
            sourceFile.copyTo(destFile, overwrite = true)
            SavedMediaResult(
                uri = android.net.Uri.fromFile(destFile),
                displayName = destFile.name,
                sizeBytes = destFile.length(),
                locationLabel = "Downloads/Clipper/${destFile.name}",
            )
        }
    }

    // -------------------------------------------------------------------------
    // PURE FORMAT PARSING & QUALITY SELECTION LOGIC (JVM UNIT-TESTABLE)
    // -------------------------------------------------------------------------

    data class RawStreamDescriptor(
        val formatId: String,
        val ext: String,
        val vcodec: String,
        val acodec: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val fileSizeBytes: Long,
        val formatNote: String = "",
    )

    fun buildQualityOptions(
        formats: List<RawStreamDescriptor>,
        hasFfmpeg: Boolean = true,
    ): List<MediaQualityOption> {
        val options = mutableListOf<MediaQualityOption>()

        // 1. Recommended Auto Presets
        options.add(
            MediaQualityOption(
                id = "auto_best_mp4",
                formatSelector = if (hasFfmpeg) {
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best[ext=mp4]/best"
                } else {
                    "best[ext=mp4]/best"
                },
                title = "Best Quality (Auto MP4)",
                subtitle = if (hasFfmpeg) "Highest resolution + best audio muxed" else "Best single-stream MP4",
                badge = "BEST",
                ext = "mp4",
                trackType = QualityTrackType.AUTO,
                requiresAudioMux = hasFfmpeg,
            ),
        )

        options.add(
            MediaQualityOption(
                id = "auto_best_audio",
                formatSelector = "bestaudio[ext=m4a]/bestaudio/best",
                title = "Best Audio Only (M4A)",
                subtitle = "Clean audio extraction for podcasts & music",
                badge = "AUDIO",
                ext = "m4a",
                trackType = QualityTrackType.AUDIO,
                extractAudioOnly = true,
            ),
        )

        // 2. Deduplicated Video Formats (highest resolution first, preferring MP4/H264 or combined streams)
        val videoCandidates = formats.filter { fmt ->
            val hasVideo = fmt.vcodec != "none" && fmt.vcodec.isNotBlank() && fmt.height > 0
            val usable = hasFfmpeg || (fmt.acodec != "none" && fmt.acodec.isNotBlank())
            hasVideo && usable
        }

        val groupedByHeight = videoCandidates.groupBy { it.height }
        groupedByHeight.entries
            .sortedByDescending { it.key }
            .forEach { (height, group) ->
                val best = group.maxWithOrNull(
                    compareBy<RawStreamDescriptor> { it.ext == "mp4" }
                        .thenBy { it.acodec != "none" && it.acodec.isNotBlank() }
                        .thenBy { it.fps }
                        .thenBy { it.fileSizeBytes },
                ) ?: return@forEach

                val videoOnly = best.acodec == "none" || best.acodec.isBlank()
                val selector = if (videoOnly && hasFfmpeg) {
                    "${best.formatId}+bestaudio[ext=m4a]/${best.formatId}+bestaudio/${best.formatId}"
                } else {
                    best.formatId
                }

                val qualityTag = when {
                    height >= 2160 -> "4K"
                    height >= 1440 -> "2K"
                    height >= 1080 -> "FHD"
                    height >= 720 -> "HD"
                    else -> "SD"
                }
                val fpsLabel = if (best.fps > 30) " ${best.fps}fps" else ""
                val codecShort = best.vcodec.substringBefore('.').uppercase(Locale.US)
                val sizeLabel = if (best.fileSizeBytes > 0L) {
                    " • ${MediaStoreSaver.formatByteSize(best.fileSizeBytes)}"
                } else {
                    ""
                }
                val muxLabel = if (videoOnly) " • Auto Audio Mux" else " • Combined"

                options.add(
                    MediaQualityOption(
                        id = "vid_${best.formatId}",
                        formatSelector = selector,
                        title = "${height}p$fpsLabel (${best.ext.uppercase(Locale.US)})",
                        subtitle = "$codecShort$muxLabel$sizeLabel",
                        badge = qualityTag,
                        ext = if (videoOnly) "mp4" else best.ext,
                        trackType = QualityTrackType.VIDEO,
                        heightPx = height,
                        fps = best.fps,
                        fileSizeBytes = best.fileSizeBytes,
                        requiresAudioMux = videoOnly,
                    ),
                )
            }

        // 3. Dedicated Audio-Only Formats (top 3 distinct extensions)
        val audioCandidates = formats
            .filter { (it.vcodec == "none" || it.vcodec.isBlank()) && it.acodec != "none" && it.acodec.isNotBlank() }
            .sortedByDescending { it.fileSizeBytes }
            .distinctBy { "${it.ext}_${it.acodec.substringBefore('.')}" }
            .take(3)

        audioCandidates.forEach { aud ->
            val codecShort = aud.acodec.substringBefore('.').uppercase(Locale.US)
            val sizeLabel = if (aud.fileSizeBytes > 0L) {
                " • ${MediaStoreSaver.formatByteSize(aud.fileSizeBytes)}"
            } else {
                ""
            }
            options.add(
                MediaQualityOption(
                    id = "aud_${aud.formatId}",
                    formatSelector = aud.formatId,
                    title = "Audio ${aud.ext.uppercase(Locale.US)} ($codecShort)",
                    subtitle = "${aud.formatNote.ifBlank { "Direct audio stream" }}$sizeLabel",
                    badge = aud.ext.uppercase(Locale.US),
                    ext = aud.ext,
                    trackType = QualityTrackType.AUDIO,
                    fileSizeBytes = aud.fileSizeBytes,
                    extractAudioOnly = false,
                ),
            )
        }

        return options
    }

    fun inferMimeFromExtension(ext: String): String = when (ext.lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }
}
