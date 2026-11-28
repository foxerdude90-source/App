package com.example.core.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent local storage (SharedPreferences JSON) for:
 *  1. Identified Song History (tracks detected via Social URL, Live Mic, or Local Files)
 *  2. Offline Acoustic Fingerprint Vault (Shazam-style 2D constellation hashes per saved song
 *     so Clipper can recognize personal/unreleased/saved tracks in <50ms even 100% offline).
 */
object MusicHistoryVaultStore {

    private const val PREFS_NAME = "clipper_music_detector_store"
    private const val KEY_HISTORY_JSON = "detected_song_history_v1"
    private const val KEY_VAULT_JSON = "offline_fingerprint_vault_v1"
    private const val MAX_HISTORY = 40
    private const val MAX_VAULT_TRACKS = 25
    private const val MAX_HASHES_PER_TRACK = 1800

    fun loadHistory(context: Context): List<IdentifiedSong> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val source = runCatching {
                        MusicMatchSource.valueOf(o.optString("matchSource", MusicMatchSource.CATALOG_MATCH.name))
                    }.getOrDefault(MusicMatchSource.CATALOG_MATCH)
                    val bpm = o.optDouble("bpm", 0.0).toFloat()
                    val key = o.optString("musicalKey", "")
                    val camelot = o.optString("camelotCode", "")
                    val telemetry = if (bpm > 0f || key.isNotBlank()) {
                        MusicalTelemetry(
                            bpm = bpm,
                            musicalKey = key.ifBlank { "Unknown" },
                            camelotCode = camelot.ifBlank { "--" },
                            loudnessLufs = o.optDouble("lufs", -14.0).toFloat(),
                            peakDbfs = -1f,
                            dynamicRangeDb = 8f,
                            estimatedSnrDb = 18f,
                            dominantFreqHz = 0f,
                            constellationPeakCount = 0,
                            constellationHashCount = 0,
                        )
                    } else null

                    add(
                        IdentifiedSong(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            album = o.optString("album").takeIf { it.isNotBlank() },
                            releaseYear = o.optString("releaseYear").takeIf { it.isNotBlank() },
                            genre = o.optString("genre").takeIf { it.isNotBlank() },
                            durationSec = o.optInt("durationSec", 0),
                            artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() },
                            previewAudioUrl = o.optString("previewAudioUrl").takeIf { it.isNotBlank() },
                            externalUrl = o.optString("externalUrl").takeIf { it.isNotBlank() },
                            isrc = o.optString("isrc").takeIf { it.isNotBlank() },
                            matchSource = source,
                            confidencePercent = o.optInt("confidencePercent", 80),
                            plainLyrics = o.optString("plainLyrics").takeIf { it.isNotBlank() },
                            telemetry = telemetry,
                            detectedAtEpochMs = o.optLong("detectedAtEpochMs", System.currentTimeMillis()),
                            sourceLabel = o.optString("sourceLabel"),
                            sourceUrl = o.optString("sourceUrl").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSongToHistory(context: Context, song: IdentifiedSong): List<IdentifiedSong> {
        val current = loadHistory(context).toMutableList()
        // Deduplicate by normalized title + artist
        current.removeAll {
            it.title.equals(song.title, ignoreCase = true) &&
                it.artist.equals(song.artist, ignoreCase = true)
        }
        current.add(0, song)
        val trimmed = current.take(MAX_HISTORY)
        val arr = JSONArray()
        trimmed.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album.orEmpty())
                    put("releaseYear", s.releaseYear.orEmpty())
                    put("genre", s.genre.orEmpty())
                    put("durationSec", s.durationSec)
                    put("artworkUrl", s.artworkUrl.orEmpty())
                    put("previewAudioUrl", s.previewAudioUrl.orEmpty())
                    put("externalUrl", s.externalUrl.orEmpty())
                    put("isrc", s.isrc.orEmpty())
                    put("matchSource", s.matchSource.name)
                    put("confidencePercent", s.confidencePercent)
                    put("plainLyrics", s.plainLyrics.orEmpty())
                    put("bpm", s.telemetry?.bpm?.toDouble() ?: 0.0)
                    put("musicalKey", s.telemetry?.musicalKey.orEmpty())
                    put("camelotCode", s.telemetry?.camelotCode.orEmpty())
                    put("lufs", s.telemetry?.loudnessLufs?.toDouble() ?: -14.0)
                    put("detectedAtEpochMs", s.detectedAtEpochMs)
                    put("sourceLabel", s.sourceLabel)
                    put("sourceUrl", s.sourceUrl.orEmpty())
                },
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, arr.toString())
            .apply()
        return trimmed
    }

    fun deleteHistoryItem(context: Context, songId: String): List<IdentifiedSong> {
        val updated = loadHistory(context).filterNot { it.id == songId }
        val arr = JSONArray()
        updated.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album.orEmpty())
                    put("releaseYear", s.releaseYear.orEmpty())
                    put("genre", s.genre.orEmpty())
                    put("durationSec", s.durationSec)
                    put("artworkUrl", s.artworkUrl.orEmpty())
                    put("previewAudioUrl", s.previewAudioUrl.orEmpty())
                    put("externalUrl", s.externalUrl.orEmpty())
                    put("isrc", s.isrc.orEmpty())
                    put("matchSource", s.matchSource.name)
                    put("confidencePercent", s.confidencePercent)
                    put("plainLyrics", s.plainLyrics.orEmpty())
                    put("bpm", s.telemetry?.bpm?.toDouble() ?: 0.0)
                    put("musicalKey", s.telemetry?.musicalKey.orEmpty())
                    put("camelotCode", s.telemetry?.camelotCode.orEmpty())
                    put("lufs", s.telemetry?.loudnessLufs?.toDouble() ?: -14.0)
                    put("detectedAtEpochMs", s.detectedAtEpochMs)
                    put("sourceLabel", s.sourceLabel)
                    put("sourceUrl", s.sourceUrl.orEmpty())
                },
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_JSON, arr.toString())
            .apply()
        return updated
    }

    fun loadVault(context: Context): List<VaultTrackEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VAULT_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val hashesPacked = o.optString("hashesPacked", "")
                    val hashes = unpackHashes(hashesPacked)
                    add(
                        VaultTrackEntry(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            durationSec = o.optDouble("durationSec", 0.0).toFloat(),
                            sampleRateHz = o.optInt("sampleRateHz", 11025),
                            createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis()),
                            hashes = hashes,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTrackToVault(context: Context, entry: VaultTrackEntry): List<VaultTrackEntry> {
        val current = loadVault(context).toMutableList()
        current.removeAll { it.id == entry.id || (it.title.equals(entry.title, true) && it.artist.equals(entry.artist, true)) }
        val boundedEntry = entry.copy(hashes = entry.hashes.take(MAX_HASHES_PER_TRACK))
        current.add(0, boundedEntry)
        val trimmed = current.take(MAX_VAULT_TRACKS)
        writeVault(context, trimmed)
        return trimmed
    }

    fun deleteVaultTrack(context: Context, trackId: String): List<VaultTrackEntry> {
        val updated = loadVault(context).filterNot { it.id == trackId }
        writeVault(context, updated)
        return updated
    }

    private fun writeVault(context: Context, tracks: List<VaultTrackEntry>) {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("durationSec", t.durationSec.toDouble())
                    put("sampleRateHz", t.sampleRateHz)
                    put("createdAtEpochMs", t.createdAtEpochMs)
                    put("hashesPacked", packHashes(t.hashes))
                },
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VAULT_JSON, arr.toString())
            .apply()
    }

    /** Compact base-36 CSV packing: `hash:time,hash:time,...` */
    internal fun packHashes(hashes: List<ConstellationHash>): String {
        val sb = StringBuilder(hashes.size * 10)
        for (i in hashes.indices) {
            if (i > 0) sb.append(',')
            sb.append(Integer.toUnsignedString(hashes[i].hash, 36))
            sb.append(':')
            sb.append(Integer.toString(hashes[i].anchorTimeFrame, 36))
        }
        return sb.toString()
    }

    internal fun unpackHashes(packed: String): List<ConstellationHash> {
        if (packed.isBlank()) return emptyList()
        return packed.split(',').mapNotNull { token ->
            val sep = token.indexOf(':')
            if (sep <= 0 || sep >= token.lastIndex) return@mapNotNull null
            runCatching {
                val h = Integer.parseUnsignedInt(token.substring(0, sep), 36)
                val t = Integer.parseInt(token.substring(sep + 1), 36)
                ConstellationHash(hash = h, anchorTimeFrame = t)
            }.getOrNull()
        }
    }
}
