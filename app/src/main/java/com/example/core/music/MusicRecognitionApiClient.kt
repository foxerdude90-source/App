package com.example.core.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 100% Free, Zero-Paid-API, Real Multi-Stage Song & Music Recognition Engine:
 *
 *  Stage 0: Instant Offline Local Acoustic Fingerprint Vault (<50ms via Shazam-style constellation delta-T voting)
 *  Stage 1: Open Acoustic Shazam v2 Fingerprint Recognition (`amp.shazam.com/discovery/v5/...`)
 *           with automatic multi-window retry (full conditioned clip + peak 3.5s musical window)
 *  Stage 2: Social Media Video Credits & Description Mining (`track`, `artist`, `Music in this video`,
 *           `Song: ...`, `Artist - Title`, TikTok/Reels/Shorts noise cleanup) verified against Deezer & iTunes
 *  Stage 3: Vocal Lyric Phrase-to-Song Recognition via LRCLIB (`lrclib.net/api/search`) + Deezer/iTunes
 *  Stage 4: Rich Real Metadata Enrichment: Album Art, 30-second MP3 Preview Stream, Synced `.lrc` Lyrics,
 *           Release Year, ISRC, and Direct Music Platform Links.
 */
object MusicRecognitionApiClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(16, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val USER_AGENTS = arrayOf(
        "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/AP1A.240305.019.A1)",
        "Dalvik/2.1.0 (Linux; U; Android 13; SM-S918B Build/TP1A.220624.014)",
    )

    // -------------------------------------------------------------------------
    // PURE TEXT & SOCIAL MEDIA CREDIT PARSERS (JVM UNIT-TESTABLE)
    // -------------------------------------------------------------------------

    data class ParsedMusicCandidate(
        val title: String,
        val artist: String?,
        val sourceHint: String,
    )

    private val NOISE_BRACKET_REGEX = Regex(
        """[\(\[\{][^)\]\}]*(official|video|audio|lyrics?|mv|hd|4k|hq|shorts?|reels?|tiktok|slowed|reverb|sped\s*up|nightcore|remix|edit|status|whatsapp|trending|viral|full\s*song|bass\s*boost|8d|clip|teaser|trailer|live|cover)[^)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )
    private val HASHTAG_REGEX = Regex("""#\S+""")
    private val EMOJI_PUNCT_REGEX = Regex("""[🎵🎶🎧🔥✨💯🔊🎤🎸🎹⚡❤️💔🥀⭐🌟]+""")
    private val CREDIT_LINE_REGEX = Regex(
        """(?i)^(?:song|music|track|audio|bgm|sound|original\s*sound|♪)\s*[:\-–—]\s*(.+)$""",
    )

    /**
     * Extracts clean `(title, artist)` candidates from social media metadata (yt-dlp fields,
     * description credits, or YouTube/Reels/TikTok video titles).
     */
    fun extractMusicCandidatesFromMetadata(
        videoTitle: String,
        uploader: String?,
        embeddedTrack: String?,
        embeddedArtist: String?,
        description: String?,
    ): List<ParsedMusicCandidate> {
        val candidates = mutableListOf<ParsedMusicCandidate>()

        // 1. Direct yt-dlp embedded track/artist metadata (highest fidelity on YouTube/TikTok/SoundCloud)
        val cleanTrack = embeddedTrack?.trim()?.takeIf { it.length >= 2 }
        val cleanArtist = embeddedArtist?.trim()?.takeIf { it.length >= 2 }
        if (cleanTrack != null) {
            candidates += ParsedMusicCandidate(
                title = stripNoiseTokens(cleanTrack),
                artist = cleanArtist?.let { stripNoiseTokens(it) },
                sourceHint = "Embedded audio track metadata",
            )
        }

        // 2. Parse YouTube "Music in this video" / description "Song:" / "Music:" / "Track:" lines
        if (!description.isNullOrBlank()) {
            val lines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (i in lines.indices) {
                val line = lines[i]
                // YouTube auto-generated "Music" / "Provided to YouTube by ..." block
                if (line.equals("Song", ignoreCase = true) && i + 1 < lines.size) {
                    val songLine = stripNoiseTokens(lines[i + 1])
                    var artistLine: String? = null
                    if (i + 3 < lines.size && lines[i + 2].equals("Artist", ignoreCase = true)) {
                        artistLine = stripNoiseTokens(lines[i + 3])
                    }
                    if (songLine.length >= 2) {
                        candidates += ParsedMusicCandidate(songLine, artistLine, "Video description credits")
                    }
                }
                val creditMatch = CREDIT_LINE_REGEX.find(line)
                if (creditMatch != null) {
                    val raw = stripNoiseTokens(creditMatch.groupValues[1])
                    parseDashSeparatedTitle(raw)?.let { (a, t) ->
                        candidates += ParsedMusicCandidate(t, a, "Description credit line")
                    } ?: run {
                        if (raw.length in 2..90) {
                            candidates += ParsedMusicCandidate(raw, null, "Description credit line")
                        }
                    }
                }
                if (line.contains("Provided to YouTube by", ignoreCase = true) && i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (next.contains(" · ")) {
                        val parts = next.split(" · ").map { it.trim() }
                        if (parts.size >= 2) {
                            candidates += ParsedMusicCandidate(
                                title = stripNoiseTokens(parts[0]),
                                artist = stripNoiseTokens(parts[1]),
                                sourceHint = "YouTube Auto-Topic Track",
                            )
                        }
                    }
                }
            }
        }

        // 3. Clean and parse the video title itself (`Artist - Song`, `Song by Artist`, or clean short title)
        val cleanedTitle = stripNoiseTokens(videoTitle)
        parseDashSeparatedTitle(cleanedTitle)?.let { (left, right) ->
            candidates += ParsedMusicCandidate(title = right, artist = left, sourceHint = "Title artist-track parse")
            // Also try reversed order in case creator wrote `Song - Artist`
            candidates += ParsedMusicCandidate(title = left, artist = right, sourceHint = "Title track-artist parse")
        } ?: run {
            val byMatch = Regex("""(?i)^(.{2,60})\s+by\s+(.{2,45})$""").find(cleanedTitle)
            if (byMatch != null) {
                candidates += ParsedMusicCandidate(
                    title = byMatch.groupValues[1].trim(),
                    artist = byMatch.groupValues[2].trim(),
                    sourceHint = "Title 'by' credit",
                )
            } else if (cleanedTitle.length in 3..75) {
                // Only use uploader as artist hint if it doesn't look like a generic clips/shorts channel
                val cleanUploader = uploader?.trim()?.takeIf { up ->
                    up.length in 2..40 &&
                        !up.contains("shorts", ignoreCase = true) &&
                        !up.contains("edits", ignoreCase = true) &&
                        !up.contains("clips", ignoreCase = true) &&
                        !up.contains("memes", ignoreCase = true)
                }
                candidates += ParsedMusicCandidate(
                    title = cleanedTitle,
                    artist = cleanUploader,
                    sourceHint = "Cleaned media title",
                )
            }
        }

        return candidates
            .filter { it.title.length >= 2 }
            .distinctBy { "${it.title.lowercase(Locale.US)}|${it.artist?.lowercase(Locale.US).orEmpty()}" }
            .take(5)
    }

    fun stripNoiseTokens(raw: String): String {
        var s = raw
        s = NOISE_BRACKET_REGEX.replace(s, " ")
        s = HASHTAG_REGEX.replace(s, " ")
        s = EMOJI_PUNCT_REGEX.replace(s, " ")
        s = s.replace(Regex("""(?i)\b(official\s*music\s*video|official\s*video|official\s*audio|lyric\s*video|full\s*video|4k\s*uhd|hd\s*video|slowed\s*\+?\s*reverb|sped\s*up|whatsapp\s*status|reels?|shorts?)\b"""), " ")
        s = s.replace(Regex("""\|.*$"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim()
        s = s.trim('-', '–', '—', ':', '"', '\'', '•', '|')
        return s.trim()
    }

    private fun parseDashSeparatedTitle(text: String): Pair<String, String>? {
        val seps = listOf(" - ", " – ", " — ", " ― ")
        for (sep in seps) {
            val idx = text.indexOf(sep)
            if (idx > 1 && idx + sep.length < text.length - 1) {
                val left = text.substring(0, idx).trim()
                val right = text.substring(idx + sep.length).trim()
                if (left.length in 2..55 && right.length in 2..75) {
                    return left to right
                }
            }
        }
        return null
    }

    /**
     * Parses standard LRC synced lyrics (`[00:12.34] Line text`) into sorted [SyncedLyricLine] objects.
     */
    fun parseLrcSyncedLyrics(lrc: String?): List<SyncedLyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)""")
        return lrc.lines().mapNotNull { line ->
            val m = lineRegex.find(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val sec = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fracStr = m.groupValues[3]
            val msPart = when (fracStr.length) {
                0 -> 0L
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                else -> (fracStr.take(3).toLongOrNull() ?: 0L)
            }
            val text = m.groupValues[4].trim()
            if (text.isBlank()) null
            else SyncedLyricLine(timeMs = (min * 60L + sec) * 1000L + msPart, text = text)
        }.sortedBy { it.timeMs }
    }

    // -------------------------------------------------------------------------
    // FULL MULTI-STAGE ACOUSTIC + METADATA + LYRIC RECOGNITION PIPELINE
    // -------------------------------------------------------------------------

    /**
     * Runs the complete acoustic recognition pipeline on decoded PCM audio:
     *  1. DSP conditioning (denoise / vocal / instrumental)
     *  2. 2D Constellation Fingerprint extraction + real Spectrogram + Musical Telemetry (BPM/Key/LUFS)
     *  3. Offline Fingerprint Vault lookup (<50ms)
     *  4. Open Shazam v2 acoustic recognition (on conditioned full clip AND smart best 3.5s window!)
     *  5. Optional metadata/lyric cross-check + Deezer/iTunes/LRCLIB enrichment.
     */
    suspend fun recognizeFromPcm(
        left: FloatArray,
        right: FloatArray?,
        sampleRateHz: Int,
        filterMode: AudioDspFilterMode,
        vaultTracks: List<VaultTrackEntry>,
        sourceLabel: String,
        sourceUrl: String? = null,
        windowStartSec: Float = 0f,
        metadataFallbackCandidates: List<ParsedMusicCandidate> = emptyList(),
        vocalLyricHint: String? = null,
        onStatus: (String) -> Unit = {},
    ): SongDetectionResult = withContext(Dispatchers.IO) {
        if (left.isEmpty()) {
            return@withContext SongDetectionResult(
                primarySong = null,
                diagnosticMessage = "No audio samples available to analyze.",
            )
        }

        onStatus("Applying ${filterMode.label} DSP conditioning...")
        val conditioned = AudioAcousticDspEngine.applyDspConditioning(left, right, sampleRateHz, filterMode)

        onStatus("Computing STFT spectrogram & constellation landmarks...")
        val constellation = AudioAcousticDspEngine.extractConstellationFingerprint(conditioned, sampleRateHz)
        val telemetry = AudioAcousticDspEngine.analyzeMusicalTelemetry(
            samples = conditioned,
            sampleRateHz = sampleRateHz,
            constellation = constellation,
            windowStartSec = windowStartSec,
        )

        // Stage 0: Check Offline Local Fingerprint Vault first (instant <50ms match)
        val vaultMatch = AudioAcousticDspEngine.matchConstellationHashes(
            queryHashes = constellation.hashes,
            vaultTracks = vaultTracks,
            minCoherentVotes = 8,
        )
        if (vaultMatch != null) {
            onStatus("Matched offline vault track: ${vaultMatch.entry.title}")
            val baseSong = IdentifiedSong(
                id = "vault_${vaultMatch.entry.id}",
                title = vaultMatch.entry.title,
                artist = vaultMatch.entry.artist,
                durationSec = vaultMatch.entry.durationSec.toInt(),
                matchSource = MusicMatchSource.OFFLINE_VAULT,
                confidencePercent = vaultMatch.confidencePercent,
                matchedOffsetSec = vaultMatch.matchedOffsetSec,
                telemetry = telemetry,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )
            val enriched = enrichSongWithFreeApis(baseSong)
            return@withContext SongDetectionResult(
                primarySong = enriched,
                telemetry = telemetry,
                spectrogram = constellation.spectrogram,
                constellationHashes = constellation.hashes,
                diagnosticMessage = "Matched in Offline Vault (${vaultMatch.coherentVotes} coherent landmarks at ${String.format(Locale.US, "%.1f", vaultMatch.matchedOffsetSec)}s).",
                vocalTranscriptHint = vocalLyricHint,
            )
        }

        // Stage 1: Real Open Acoustic Fingerprint Recognition (try full clip, then best 3.5s musical window)
        onStatus("Encoding 16 kHz acoustic landmark signature...")
        var shazamSong = queryShazamOpenEndpoint(conditioned, sampleRateHz, telemetry, sourceLabel, sourceUrl)

        if (shazamSong == null && conditioned.size > sampleRateHz * 4.5f) {
            onStatus("Isolating cleanest 3.5s musical peak window...")
            val bestWindow = AudioAcousticDspEngine.selectBestMusicWindow(conditioned, sampleRateHz, windowDurationSec = 3.6f)
            shazamSong = queryShazamOpenEndpoint(bestWindow.samples, sampleRateHz, telemetry, sourceLabel, sourceUrl)
        }

        // If noise gate wasn't used yet and still no match, try aggressive spectral denoised pass
        if (shazamSong == null && filterMode == AudioDspFilterMode.FULL_MIX) {
            onStatus("Retrying acoustic scan with Spectral Subtraction noise gate...")
            val denoised = AudioAcousticDspEngine.applyDspConditioning(left, right, sampleRateHz, AudioDspFilterMode.CLEAN_DENOISE)
            shazamSong = queryShazamOpenEndpoint(denoised, sampleRateHz, telemetry, sourceLabel, sourceUrl)
        }

        if (shazamSong != null) {
            onStatus("Fetching synced lyrics & 30s audio preview...")
            val enriched = enrichSongWithFreeApis(shazamSong)
            return@withContext SongDetectionResult(
                primarySong = enriched,
                telemetry = telemetry,
                spectrogram = constellation.spectrogram,
                constellationHashes = constellation.hashes,
                diagnosticMessage = "Identified via acoustic frequency-peak fingerprint.",
                vocalTranscriptHint = vocalLyricHint,
            )
        }

        // Stage 2: Vocal Lyric Phrase-to-Song Recognition (LRCLIB + Deezer/iTunes)
        if (!vocalLyricHint.isNullOrBlank() && vocalLyricHint.trim().length >= 6) {
            onStatus("Matching vocal lyrics against LRCLIB database...")
            val lyricMatches = searchSongByLyrics(vocalLyricHint, telemetry, sourceLabel, sourceUrl)
            if (lyricMatches.isNotEmpty()) {
                val primary = enrichSongWithFreeApis(lyricMatches.first())
                return@withContext SongDetectionResult(
                    primarySong = primary,
                    alternates = lyricMatches.drop(1).take(3),
                    telemetry = telemetry,
                    spectrogram = constellation.spectrogram,
                    constellationHashes = constellation.hashes,
                    diagnosticMessage = "Matched from vocal lyrics phrase.",
                    vocalTranscriptHint = vocalLyricHint,
                )
            }
        }

        // Stage 3: Social Media Credit / Description Fallback Verification (Deezer + iTunes)
        if (metadataFallbackCandidates.isNotEmpty()) {
            onStatus("Verifying embedded video audio credits...")
            val metaMatches = identifyFromMetadataCandidates(
                candidates = metadataFallbackCandidates,
                telemetry = telemetry,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )
            if (metaMatches.isNotEmpty()) {
                val primary = enrichSongWithFreeApis(metaMatches.first())
                return@withContext SongDetectionResult(
                    primarySong = primary,
                    alternates = metaMatches.drop(1).take(3),
                    telemetry = telemetry,
                    spectrogram = constellation.spectrogram,
                    constellationHashes = constellation.hashes,
                    diagnosticMessage = "Identified from video music credits & catalog verification.",
                    vocalTranscriptHint = vocalLyricHint,
                )
            }
        }

        SongDetectionResult(
            primarySong = null,
            telemetry = telemetry,
            spectrogram = constellation.spectrogram,
            constellationHashes = constellation.hashes,
            diagnosticMessage = "No exact song match found in this clip. Try switching DSP to Noise Gate / Vocals Only, moving the 2–4s window to the chorus, or matching a vocal lyric phrase.",
            vocalTranscriptHint = vocalLyricHint,
        )
    }

    /**
     * Quick non-downloading metadata credit check for `FetchScreen` right after inspecting a social URL.
     */
    suspend fun quickIdentifyFromSocialMetadata(
        videoTitle: String,
        uploader: String?,
        embeddedTrack: String?,
        embeddedArtist: String?,
        description: String?,
        sourceUrl: String,
    ): List<IdentifiedSong> = withContext(Dispatchers.IO) {
        val candidates = extractMusicCandidatesFromMetadata(
            videoTitle = videoTitle,
            uploader = uploader,
            embeddedTrack = embeddedTrack,
            embeddedArtist = embeddedArtist,
            description = description,
        )
        if (candidates.isEmpty()) return@withContext emptyList()
        val matches = identifyFromMetadataCandidates(
            candidates = candidates,
            telemetry = null,
            sourceLabel = "Social Media URL",
            sourceUrl = sourceUrl,
        )
        if (matches.isEmpty()) emptyList()
        else listOf(enrichSongWithFreeApis(matches.first())) + matches.drop(1).take(2)
    }

    /**
     * Queries Shazam's open v5 discovery endpoint with our mathematically generated binary frequency-peak signature.
     * Gracefully returns null on network error or non-match without throwing.
     */
    private fun queryShazamOpenEndpoint(
        pcm: FloatArray,
        sampleRateHz: Int,
        telemetry: MusicalTelemetry,
        sourceLabel: String,
        sourceUrl: String?,
    ): IdentifiedSong? {
        val (sigUri, durationMs) = AudioAcousticDspEngine.encodeShazamSignature(pcm, sampleRateHz)
            ?: return null
        return runCatching {
            val uuid1 = UUID.randomUUID().toString().uppercase(Locale.US)
            val uuid2 = UUID.randomUUID().toString().uppercase(Locale.US)
            val endpoint = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2" +
                "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

            val payload = JSONObject().apply {
                put("geolocation", JSONObject().apply {
                    put("altitude", 300)
                    put("latitude", 37.7749)
                    put("longitude", -122.4194)
                })
                put("signature", JSONObject().apply {
                    put("samplems", durationMs)
                    put("timestamp", System.currentTimeMillis())
                    put("uri", sigUri)
                })
                put("timestamp", System.currentTimeMillis())
                put("timezone", "UTC")
            }

            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", USER_AGENTS.random())
                .header("Content-Language", "en_US")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                val json = JSONObject(body)
                val track = json.optJSONObject("track") ?: return@runCatching null
                val title = track.optString("title").trim()
                val artist = track.optString("subtitle").trim()
                if (title.isBlank() || artist.isBlank()) return@runCatching null

                val images = track.optJSONObject("images")
                val artwork = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("coverart")?.takeIf { it.isNotBlank() }
                val genres = track.optJSONObject("genres")?.optString("primary")?.takeIf { it.isNotBlank() }
                val isrc = track.optString("isrc").takeIf { it.isNotBlank() }
                val shazamUrl = track.optString("url").takeIf { it.isNotBlank() }

                var album: String? = null
                var releaseYear: String? = null
                var previewUrl: String? = null
                var plainLyrics: String? = null

                val hub = track.optJSONObject("hub")
                val actions = hub?.optJSONArray("actions")
                if (actions != null) {
                    for (i in 0 until actions.length()) {
                        val act = actions.optJSONObject(i) ?: continue
                        if (act.optString("type") == "uri") {
                            val u = act.optString("uri")
                            if (u.startsWith("http")) {
                                previewUrl = u
                                break
                            }
                        }
                    }
                }

                val sections = track.optJSONArray("sections")
                if (sections != null) {
                    for (i in 0 until sections.length()) {
                        val sec = sections.optJSONObject(i) ?: continue
                        when (sec.optString("type")) {
                            "SONG" -> {
                                val meta = sec.optJSONArray("metadata") ?: continue
                                for (m in 0 until meta.length()) {
                                    val entry = meta.optJSONObject(m) ?: continue
                                    when (entry.optString("title").lowercase(Locale.US)) {
                                        "album" -> album = entry.optString("text").takeIf { it.isNotBlank() }
                                        "released" -> releaseYear = entry.optString("text").takeIf { it.isNotBlank() }
                                    }
                                }
                            }
                            "LYRICS" -> {
                                val textArr = sec.optJSONArray("text")
                                if (textArr != null && textArr.length() > 0) {
                                    plainLyrics = (0 until textArr.length()).joinToString("\n") { textArr.optString(it) }
                                }
                            }
                        }
                    }
                }

                val matchesArr = json.optJSONArray("matches")
                val offsetSec = matchesArr?.optJSONObject(0)?.optDouble("offset", -1.0)?.toFloat()
                    ?.takeIf { it >= 0f }

                IdentifiedSong(
                    id = "shazam_${track.optString("key", UUID.randomUUID().toString())}",
                    title = title,
                    artist = artist,
                    album = album,
                    releaseYear = releaseYear,
                    genre = genres,
                    artworkUrl = artwork,
                    previewAudioUrl = previewUrl,
                    externalUrl = shazamUrl,
                    isrc = isrc,
                    matchSource = MusicMatchSource.ACOUSTIC_FINGERPRINT,
                    confidencePercent = 96,
                    matchedOffsetSec = offsetSec,
                    plainLyrics = plainLyrics,
                    telemetry = telemetry,
                    sourceLabel = sourceLabel,
                    sourceUrl = sourceUrl,
                )
            }
        }.getOrNull()
    }

    /**
     * Identifies songs from vocal lyrics phrase via LRCLIB (free 3M+ song lyric index)
     * and cross-enriches with Deezer/iTunes.
     */
    suspend fun searchSongByLyrics(
        lyricPhrase: String,
        telemetry: MusicalTelemetry? = null,
        sourceLabel: String = "Vocal Lyrics",
        sourceUrl: String? = null,
    ): List<IdentifiedSong> = withContext(Dispatchers.IO) {
        val clean = lyricPhrase.trim()
        if (clean.length < 4) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val results = mutableListOf<IdentifiedSong>()

        runCatching {
            val req = Request.Builder()
                .url("https://lrclib.net/api/search?q=$encoded")
                .header("User-Agent", "ClipperAndroid/1.0 (https://clipper.app)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val arr = JSONArray(resp.body?.string().orEmpty())
                for (i in 0 until minOf(arr.length(), 4)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val trackName = o.optString("trackName").trim()
                    val artistName = o.optString("artistName").trim()
                    if (trackName.isBlank() || artistName.isBlank()) continue
                    val syncedRaw = o.optString("syncedLyrics").takeIf { it.isNotBlank() }
                    val plainRaw = o.optString("plainLyrics").takeIf { it.isNotBlank() }
                    results += IdentifiedSong(
                        id = "lrc_${o.optLong("id", i.toLong())}",
                        title = trackName,
                        artist = artistName,
                        album = o.optString("albumName").takeIf { it.isNotBlank() },
                        durationSec = o.optDouble("duration", 0.0).toInt(),
                        matchSource = MusicMatchSource.LYRIC_RECOGNITION,
                        confidencePercent = (88 - i * 6).coerceAtLeast(65),
                        plainLyrics = plainRaw,
                        syncedLyrics = parseLrcSyncedLyrics(syncedRaw),
                        telemetry = telemetry,
                        sourceLabel = sourceLabel,
                        sourceUrl = sourceUrl,
                    )
                }
            }
        }

        // Also query Deezer if LRCLIB had zero hits (handles instrumental titles or hummed track names)
        if (results.isEmpty()) {
            results += searchDeezerCatalog(clean, null, MusicMatchSource.LYRIC_RECOGNITION, telemetry, sourceLabel, sourceUrl)
        }
        results.distinctBy { "${it.title.lowercase(Locale.US)}|${it.artist.lowercase(Locale.US)}" }
    }

    private fun identifyFromMetadataCandidates(
        candidates: List<ParsedMusicCandidate>,
        telemetry: MusicalTelemetry?,
        sourceLabel: String,
        sourceUrl: String?,
    ): List<IdentifiedSong> {
        val found = mutableListOf<IdentifiedSong>()
        for (cand in candidates) {
            val query = listOfNotNull(cand.artist, cand.title).joinToString(" ").trim()
            if (query.length < 3) continue
            val deezer = searchDeezerCatalog(
                titleQuery = cand.title,
                artistQuery = cand.artist,
                matchSource = MusicMatchSource.VIDEO_METADATA,
                telemetry = telemetry,
                sourceLabel = sourceLabel,
                sourceUrl = sourceUrl,
            )
            found += deezer
            if (found.isEmpty()) {
                found += searchItunesCatalog(
                    query = query,
                    matchSource = MusicMatchSource.VIDEO_METADATA,
                    telemetry = telemetry,
                    sourceLabel = sourceLabel,
                    sourceUrl = sourceUrl,
                )
            }
            if (found.size >= 3) break
        }
        return found
            .distinctBy { "${it.title.lowercase(Locale.US)}|${it.artist.lowercase(Locale.US)}" }
            .take(4)
    }

    /**
     * Free Deezer Public Search API (`https://api.deezer.com/search`) — returns real artwork,
     * album, duration, ISRC, and 30-second MP3 preview URL with zero API key!
     */
    private fun searchDeezerCatalog(
        titleQuery: String,
        artistQuery: String?,
        matchSource: MusicMatchSource,
        telemetry: MusicalTelemetry?,
        sourceLabel: String,
        sourceUrl: String?,
    ): List<IdentifiedSong> {
        val q = if (!artistQuery.isNullOrBlank()) {
            "$artistQuery $titleQuery"
        } else {
            titleQuery
        }.trim()
        val encoded = URLEncoder.encode(q, "UTF-8")
        return runCatching {
            val req = Request.Builder()
                .url("https://api.deezer.com/search?q=$encoded&limit=4")
                .header("User-Agent", "ClipperAndroid/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val title = item.optString("title").trim()
                        val artist = item.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
                        if (title.isBlank() || artist.isBlank()) continue
                        val albumObj = item.optJSONObject("album")
                        val artwork = albumObj?.optString("cover_xl")?.takeIf { it.isNotBlank() }
                            ?: albumObj?.optString("cover_big")?.takeIf { it.isNotBlank() }
                            ?: albumObj?.optString("cover_medium")?.takeIf { it.isNotBlank() }
                        val preview = item.optString("preview").takeIf { it.startsWith("http") }
                        val link = item.optString("link").takeIf { it.startsWith("http") }
                        val scoreBoost = tokenOverlapScore(titleQuery, title)
                        if (scoreBoost < 0.25f && i > 0) continue

                        add(
                            IdentifiedSong(
                                id = "deezer_${item.optLong("id")}",
                                title = title,
                                artist = artist,
                                album = albumObj?.optString("title")?.takeIf { it.isNotBlank() },
                                durationSec = item.optInt("duration", 0),
                                artworkUrl = artwork,
                                previewAudioUrl = preview,
                                externalUrl = link,
                                matchSource = matchSource,
                                confidencePercent = (72 + (scoreBoost * 24f).toInt()).coerceIn(66, 95),
                                telemetry = telemetry,
                                sourceLabel = sourceLabel,
                                sourceUrl = sourceUrl,
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Free Apple iTunes Search API (`https://itunes.apple.com/search`) — zero API key, returns
     * high-res artwork, genre, release year, and 30-second AAC preview stream.
     */
    private fun searchItunesCatalog(
        query: String,
        matchSource: MusicMatchSource,
        telemetry: MusicalTelemetry?,
        sourceLabel: String,
        sourceUrl: String?,
    ): List<IdentifiedSong> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return runCatching {
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&entity=song&limit=4")
                .header("User-Agent", "ClipperAndroid/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val arr = root.optJSONArray("results") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val title = item.optString("trackName").trim()
                        val artist = item.optString("artistName").trim()
                        if (title.isBlank() || artist.isBlank()) continue
                        val art = item.optString("artworkUrl100")
                            .takeIf { it.startsWith("http") }
                            ?.replace("100x100bb", "600x600bb")
                        val year = item.optString("releaseDate").take(4).takeIf { it.length == 4 }
                        add(
                            IdentifiedSong(
                                id = "itunes_${item.optLong("trackId")}",
                                title = title,
                                artist = artist,
                                album = item.optString("collectionName").takeIf { it.isNotBlank() },
                                releaseYear = year,
                                genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() },
                                durationSec = (item.optLong("trackTimeMillis", 0L) / 1000L).toInt(),
                                artworkUrl = art,
                                previewAudioUrl = item.optString("previewUrl").takeIf { it.startsWith("http") },
                                externalUrl = item.optString("trackViewUrl").takeIf { it.startsWith("http") },
                                matchSource = matchSource,
                                confidencePercent = (82 - i * 5).coerceAtLeast(66),
                                telemetry = telemetry,
                                sourceLabel = sourceLabel,
                                sourceUrl = sourceUrl,
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Enriches any identified song with missing artwork, 30s preview stream (Deezer/iTunes),
     * and time-synced `.lrc` lyrics (LRCLIB).
     */
    suspend fun enrichSongWithFreeApis(song: IdentifiedSong): IdentifiedSong = withContext(Dispatchers.IO) {
        var enriched = song

        // 1. Fill artwork, 30s preview URL, release year, genre via Deezer / iTunes if missing
        if (enriched.artworkUrl.isNullOrBlank() || enriched.previewAudioUrl.isNullOrBlank() || enriched.releaseYear.isNullOrBlank()) {
            val catalogHits = searchDeezerCatalog(
                titleQuery = enriched.title,
                artistQuery = enriched.artist,
                matchSource = enriched.matchSource,
                telemetry = enriched.telemetry,
                sourceLabel = enriched.sourceLabel,
                sourceUrl = enriched.sourceUrl,
            )
            val bestDeezer = catalogHits.firstOrNull()
            if (bestDeezer != null) {
                enriched = enriched.copy(
                    artworkUrl = enriched.artworkUrl ?: bestDeezer.artworkUrl,
                    previewAudioUrl = enriched.previewAudioUrl ?: bestDeezer.previewAudioUrl,
                    album = enriched.album ?: bestDeezer.album,
                    durationSec = if (enriched.durationSec > 0) enriched.durationSec else bestDeezer.durationSec,
                    externalUrl = enriched.externalUrl ?: bestDeezer.externalUrl,
                )
            }
            if (enriched.previewAudioUrl.isNullOrBlank() || enriched.releaseYear.isNullOrBlank() || enriched.genre.isNullOrBlank()) {
                val itunes = searchItunesCatalog(
                    query = "${enriched.artist} ${enriched.title}",
                    matchSource = enriched.matchSource,
                    telemetry = enriched.telemetry,
                    sourceLabel = enriched.sourceLabel,
                    sourceUrl = enriched.sourceUrl,
                ).firstOrNull()
                if (itunes != null) {
                    enriched = enriched.copy(
                        artworkUrl = enriched.artworkUrl ?: itunes.artworkUrl,
                        previewAudioUrl = enriched.previewAudioUrl ?: itunes.previewAudioUrl,
                        releaseYear = enriched.releaseYear ?: itunes.releaseYear,
                        genre = enriched.genre ?: itunes.genre,
                        album = enriched.album ?: itunes.album,
                        durationSec = if (enriched.durationSec > 0) enriched.durationSec else itunes.durationSec,
                    )
                }
            }
        }

        // 2. Fetch synced & plain lyrics via LRCLIB if missing
        if (enriched.syncedLyrics.isEmpty() && enriched.plainLyrics.isNullOrBlank()) {
            runCatching {
                val tEnc = URLEncoder.encode(enriched.title, "UTF-8")
                val aEnc = URLEncoder.encode(enriched.artist, "UTF-8")
                val req = Request.Builder()
                    .url("https://lrclib.net/api/get?track_name=$tEnc&artist_name=$aEnc")
                    .header("User-Agent", "ClipperAndroid/1.0 (https://clipper.app)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val o = JSONObject(resp.body?.string().orEmpty())
                        val syncedRaw = o.optString("syncedLyrics").takeIf { it.isNotBlank() }
                        val plainRaw = o.optString("plainLyrics").takeIf { it.isNotBlank() }
                        enriched = enriched.copy(
                            plainLyrics = enriched.plainLyrics ?: plainRaw,
                            syncedLyrics = if (enriched.syncedLyrics.isEmpty()) parseLrcSyncedLyrics(syncedRaw) else enriched.syncedLyrics,
                        )
                    }
                }
            }
        }

        enriched
    }

    private fun tokenOverlapScore(a: String, b: String): Float {
        val tokA = a.lowercase(Locale.US).split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }.toSet()
        val tokB = b.lowercase(Locale.US).split(Regex("""[^a-z0-9]+""")).filter { it.length >= 2 }.toSet()
        if (tokA.isEmpty() || tokB.isEmpty()) return 0f
        val overlap = tokA.intersect(tokB).size
        return overlap.toFloat() / maxOf(tokA.size, tokB.size).toFloat()
    }

    fun buildYoutubeMusicSearchUrl(song: IdentifiedSong): String {
        val q = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        return "https://music.youtube.com/search?q=$q"
    }

    fun buildSpotifySearchUrl(song: IdentifiedSong): String {
        val q = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        return "https://open.spotify.com/search/$q"
    }
}
