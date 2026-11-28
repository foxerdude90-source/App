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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.example.core.music.DetectedAudioFileInfo
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
    val sourceUrl: String, // Always the original sanitized page URL (never raw temporary stream URLs)
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val extractorName: String?,
    val width: Int,
    val height: Int,
    val qualities: List<MediaQualityOption>,
    val hasAudioTrack: Boolean = true,
    val detectedAudioStreams: List<DetectedAudioFileInfo> = emptyList(),
    val embeddedTrack: String? = null,
    val embeddedArtist: String? = null,
    val embeddedAlbum: String? = null,
    val description: String? = null,
)

data class DownloadProgressUpdate(
    val progressFraction: Float, // 0f..1f (-1f when indeterminate)
    val etaSeconds: Long,
    val statusLine: String,
)

enum class EngineUpdateOutcome {
    DONE,
    ALREADY_UP_TO_DATE,
    ERROR,
}

data class EngineUpdateResult(
    val outcome: EngineUpdateOutcome,
    val message: String,
)

/**
 * Production on-device wrapper around `io.github.junkfood02.youtubedl-android` (yt-dlp + FFmpeg).
 *
 * Includes:
 *  - URL tracking parameter sanitization (strips `igsh`, `fqnt`, `si`, `utm_*`, etc.)
 *  - Automatic Netscape `cookies.txt` attachment when present in `context.cacheDir`
 *  - Long-video anti-403 reliability flags (`--rm-cache-dir`, `-4`, `youtube:player_client=web_embedded,web,tv`)
 *  - Nightly engine updater (`YoutubeDL.UpdateChannel.NIGHTLY`)
 */
object YoutubeDlMediaEngine {

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false
    @Volatile
    private var ffmpegReady = false

    private val TRACKING_QUERY_PARAMS = setOf(
        "igsh",
        "igshid",
        "fqnt",
        "si",
        "feature",
        "pp",
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "ref",
        "ref_src",
        "ref_url",
        "share_id",
        "t_source",
    )

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
     * Strips unnecessary tracking query parameters (`igsh`, `fqnt`, `si`, `utm_*`, etc.)
     * while preserving essential video/media ID parameters (`v`, `t`, `list`, `index`, `id`, etc.).
     */
    fun sanitizeMediaUrl(rawUrl: String): String {
        val normalized = normalizeAndValidateUrl(rawUrl)
        val parsed = normalized.toHttpUrlOrNull() ?: return normalized
        if (parsed.querySize == 0) return parsed.toString()

        val builder = parsed.newBuilder().query(null)
        for (i in 0 until parsed.querySize) {
            val name = parsed.queryParameterName(i)
            val value = parsed.queryParameterValue(i)
            val lower = name.lowercase(Locale.US)
            if (lower !in TRACKING_QUERY_PARAMS && !lower.startsWith("utm_")) {
                builder.addQueryParameter(name, value)
            }
        }
        return builder.build().toString()
    }

    /**
     * Attaches anti-403 stability flags and automatic `cookies.txt` (if present and non-empty)
     * to both `getInfo` and download `YoutubeDLRequest` instances.
     */
    private fun attachCommonReliabilityAndCookieOptions(
        context: Context,
        request: YoutubeDLRequest,
    ) {
        request.addOption("--rm-cache-dir")
        request.addOption("-4")
        request.addOption("--extractor-args", "youtube:player_client=web_embedded,web,tv")

        val cookieFile = CookieSessionManager.cookieFile(context)
        if (cookieFile.exists() && cookieFile.length() > 0L) {
            request.addOption("--cookies", cookieFile.absolutePath)
        }
    }

    /**
     * Fetches rich media metadata & available formats via `YoutubeDL.getInstance().getInfo(request)`.
     * Always stores the original sanitized media URL in [FetchedMediaMetadata.sourceUrl] so
     * subsequent downloads never rely on expiring direct CDN stream URLs.
     */
    suspend fun fetchMediaMetadata(
        context: Context,
        rawUrl: String,
    ): FetchedMediaMetadata = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitizeMediaUrl(rawUrl)
        ensureInitialized(context)

        val request = YoutubeDLRequest(sanitizedUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            attachCommonReliabilityAndCookieOptions(context, this)
        }

        val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
        val rawFormats: List<VideoFormat> = info.formats.orEmpty()
        val parsedFormats = rawFormats.mapNotNull { fmt ->
            val formatId = fmt.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val abrVal = readNumericMember(fmt, "getAbr", "abr")?.toInt()?.coerceAtLeast(0)
                ?: readNumericMember(fmt, "getTbr", "tbr")?.toInt()?.coerceAtLeast(0)
                ?: 0
            val asrVal = readNumericMember(fmt, "getAsr", "asr")?.toInt()?.coerceAtLeast(0) ?: 0
            val chVal = readNumericMember(fmt, "getAudioChannels", "audioChannels")?.toInt()?.coerceAtLeast(0) ?: 0
            val directUrl = fmt.url?.takeIf { it.startsWith("http") }
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
                bitrateKbps = abrVal,
                sampleRateHz = asrVal,
                channels = chVal,
                directUrl = directUrl,
            )
        }

        val (hasAudio, audioFiles) = detectAudioFilesFromStreams(
            formats = parsedFormats,
            fallbackAcodec = readStringMember(info, "getAcodec", "acodec"),
            fallbackExt = info.ext,
            fallbackUrl = info.url,
        )
        val qualityOptions = buildQualityOptions(parsedFormats, hasFfmpeg = ffmpegReady)
        val safeTitle = info.title?.trim()?.takeIf { it.isNotBlank() } ?: "Media Clip"

        val embeddedTrack = readStringMember(info, "getTrack", "track")
            ?: readStringMember(info, "getAltTitle", "altTitle")
        val embeddedArtist = readStringMember(info, "getArtist", "artist")
            ?: readStringMember(info, "getCreator", "creator")
        val embeddedAlbum = readStringMember(info, "getAlbum", "album")
        val description = info.description?.trim()?.takeIf { it.isNotBlank() }?.take(2400)

        FetchedMediaMetadata(
            sourceUrl = sanitizedUrl,
            title = safeTitle,
            uploader = info.uploader?.trim()?.takeIf { it.isNotBlank() },
            durationSec = info.duration.toLong().coerceAtLeast(0L),
            thumbnailUrl = info.thumbnail?.trim()?.takeIf { it.isNotBlank() },
            extractorName = info.extractorKey?.trim()?.takeIf { it.isNotBlank() }
                ?: info.extractor?.trim()?.takeIf { it.isNotBlank() },
            width = info.width,
            height = info.height,
            qualities = qualityOptions,
            hasAudioTrack = hasAudio,
            detectedAudioStreams = audioFiles,
            embeddedTrack = embeddedTrack,
            embeddedArtist = embeddedArtist,
            embeddedAlbum = embeddedAlbum,
            description = description,
        )
    }

    /**
     * Downloads a compact audio clip (e.g., first 14–18 seconds of best/smallest audio stream)
     * into a temporary cache file for real on-device PCM DSP & acoustic song recognition.
     * Caller is responsible for deleting the returned file when finished.
     */
    suspend fun extractAudioSampleFile(
        context: Context,
        originalUrl: String,
        processId: String,
        maxDurationSec: Int = 18,
    ): File = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitizeMediaUrl(originalUrl)
        ensureInitialized(context)

        val jobDir = File(context.cacheDir, "clipper_aud_scan_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        val outputTemplate = File(jobDir, "sample.%(ext)s").absolutePath
        val request = YoutubeDLRequest(sanitizedUrl).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio/best[ext=mp4]/best")
            if (ffmpegReady) {
                addOption("--download-sections", "*0-$maxDurationSec")
                addOption("-x")
                addOption("--audio-format", "m4a")
                addOption("--audio-quality", "5")
            }
            addOption("-o", outputTemplate)
            attachCommonReliabilityAndCookieOptions(context, this)
        }

        YoutubeDL.getInstance().execute(request, processId) { _, _, _ -> }

        val produced = jobDir.listFiles()
            ?.filter { f ->
                f.isFile && f.length() > 0L &&
                    !f.name.endsWith(".part", true) &&
                    !f.name.endsWith(".ytdl", true)
            }
            ?.maxByOrNull { it.length() }
            ?: run {
                jobDir.deleteRecursively()
                error("Could not extract audio sample from this media URL.")
            }

        val standalone = File(context.cacheDir, "clipper_scan_${System.currentTimeMillis()}.${produced.extension.ifBlank { "m4a" }}")
        produced.copyTo(standalone, overwrite = true)
        jobDir.deleteRecursively()
        standalone
    }

    private fun readStringMember(target: Any, getterName: String, fieldName: String): String? {
        runCatching {
            val m = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
            val v = m?.invoke(target) as? String
            if (!v.isNullOrBlank()) return v.trim()
        }
        runCatching {
            val f = target.javaClass.declaredFields.firstOrNull { it.name == fieldName }
            f?.isAccessible = true
            val v = f?.get(target) as? String
            if (!v.isNullOrBlank()) return v.trim()
        }
        return null
    }

    private fun readNumericMember(target: Any, getterName: String, fieldName: String): Double? {
        runCatching {
            val m = target.javaClass.methods.firstOrNull { it.name == getterName && it.parameterCount == 0 }
            val v = m?.invoke(target) as? Number
            if (v != null && v.toDouble() > 0.0) return v.toDouble()
        }
        runCatching {
            val f = target.javaClass.declaredFields.firstOrNull { it.name == fieldName }
            f?.isAccessible = true
            val v = f?.get(target) as? Number
            if (v != null && v.toDouble() > 0.0) return v.toDouble()
        }
        return null
    }

    /**
     * Executes `YoutubeDLRequest` in a coroutine using the ORIGINAL sanitized media URL +
     * selected `-f <format_selector>`, anti-403 flags, and automatic `cookies.txt` attachment.
     * Saves the completed file into `Environment.DIRECTORY_DOWNLOADS`.
     */
    suspend fun downloadMediaToDownloads(
        context: Context,
        originalUrl: String,
        quality: MediaQualityOption,
        titleHint: String,
        processId: String,
        onProgress: (DownloadProgressUpdate) -> Unit,
    ): SavedMediaResult = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitizeMediaUrl(originalUrl)
        ensureInitialized(context)

        val jobDir = File(context.cacheDir, "ytdl_job_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        try {
            val outputTemplate = File(jobDir, "%(title).80B.%(ext)s").absolutePath
            val request = YoutubeDLRequest(sanitizedUrl).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--restrict-filenames")
                addOption("-f", quality.formatSelector)
                addOption("-o", outputTemplate)
                attachCommonReliabilityAndCookieOptions(context, this)

                if (quality.extractAudioOnly) {
                    if (ffmpegReady) {
                        addOption("-x")
                        addOption("--audio-format", "m4a")
                        addOption("--audio-quality", "0")
                    }
                } else if (ffmpegReady && (quality.requiresAudioMux || quality.trackType == QualityTrackType.AUTO)) {
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

    /**
     * Updates the yt-dlp engine via the official `NIGHTLY` channel on `Dispatchers.IO`.
     */
    suspend fun updateYtDlpEngine(context: Context): EngineUpdateResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context.applicationContext,
                YoutubeDL.UpdateChannel.NIGHTLY,
            )
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> EngineUpdateResult(
                    outcome = EngineUpdateOutcome.DONE,
                    message = "DONE — Updated yt-dlp engine to the latest Nightly build.",
                )
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> EngineUpdateResult(
                    outcome = EngineUpdateOutcome.ALREADY_UP_TO_DATE,
                    message = "ALREADY_UP_TO_DATE — Your yt-dlp Nightly engine is current.",
                )
                null -> EngineUpdateResult(
                    outcome = EngineUpdateOutcome.ERROR,
                    message = "Engine updater returned no status. Check network connectivity.",
                )
            }
        } catch (t: Throwable) {
            EngineUpdateResult(
                outcome = EngineUpdateOutcome.ERROR,
                message = t.message?.take(180) ?: "Failed to update yt-dlp engine.",
            )
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
                category = SaveCategory.DOCUMENT,
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
        val bitrateKbps: Int = 0,
        val sampleRateHz: Int = 0,
        val channels: Int = 0,
        val directUrl: String? = null,
    )

    /**
     * Pure JVM-testable detector that inspects parsed yt-dlp formats to determine whether
     * a real embedded audio file/stream exists inside the video/short, or if none is present.
     */
    fun detectAudioFilesFromStreams(
        formats: List<RawStreamDescriptor>,
        fallbackAcodec: String? = null,
        fallbackExt: String? = null,
        fallbackUrl: String? = null,
    ): Pair<Boolean, List<DetectedAudioFileInfo>> {
        val withAudio = formats.filter { fmt ->
            val ac = fmt.acodec.trim().lowercase(Locale.US)
            ac.isNotEmpty() && ac != "none" && ac != "null"
        }

        if (withAudio.isEmpty()) {
            val fb = fallbackAcodec?.trim()?.lowercase(Locale.US).orEmpty()
            if (fb.isNotEmpty() && fb != "none" && fb != "null") {
                return true to listOf(
                    DetectedAudioFileInfo(
                        hasAudioTrack = true,
                        formatId = "default",
                        codec = fb.substringBefore('.').uppercase(Locale.US),
                        containerExt = fallbackExt?.lowercase(Locale.US).orEmpty().ifBlank { "m4a" },
                        isAudioOnlyStream = false,
                        note = "Muxed audio stream",
                        directStreamUrl = fallbackUrl?.takeIf { it.startsWith("http") },
                    ),
                )
            }
            return false to emptyList()
        }

        val audioOnly = withAudio
            .filter { it.vcodec.equals("none", true) || it.vcodec.isBlank() }
            .sortedWith(
                compareByDescending<RawStreamDescriptor> { it.bitrateKbps }
                    .thenByDescending { it.fileSizeBytes },
            )

        val prioritized = if (audioOnly.isNotEmpty()) audioOnly else withAudio.sortedByDescending { it.bitrateKbps }
        val detected = prioritized
            .distinctBy { "${it.ext.lowercase(Locale.US)}_${it.acodec.substringBefore('.').lowercase(Locale.US)}_${it.bitrateKbps}" }
            .take(4)
            .map { fmt ->
                val isAudioOnly = fmt.vcodec.equals("none", true) || fmt.vcodec.isBlank()
                DetectedAudioFileInfo(
                    hasAudioTrack = true,
                    formatId = fmt.formatId,
                    codec = fmt.acodec.substringBefore('.').uppercase(Locale.US),
                    containerExt = fmt.ext.lowercase(Locale.US),
                    bitrateKbps = fmt.bitrateKbps,
                    sampleRateHz = fmt.sampleRateHz,
                    channels = fmt.channels,
                    fileSizeBytes = fmt.fileSizeBytes,
                    isAudioOnlyStream = isAudioOnly,
                    note = fmt.formatNote.ifBlank { if (isAudioOnly) "Dedicated audio stream" else "Muxed video+audio track" },
                    directStreamUrl = fmt.directUrl,
                )
            }
        return true to detected
    }

    fun buildQualityOptions(
        formats: List<RawStreamDescriptor>,
        hasFfmpeg: Boolean = true,
    ): List<MediaQualityOption> {
        val options = mutableListOf<MediaQualityOption>()

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
