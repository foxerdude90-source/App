package com.example.core.media

import android.content.Context
import android.webkit.CookieManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Locale

data class CookieSaveResult(
    val cookieCount: Int,
    val domain: String,
    val filePath: String,
)

/**
 * Manages optional in-app WebView session cookies in standard Netscape HTTP Cookie File format
 * (`File(context.cacheDir, "cookies.txt")`) for authenticated yt-dlp requests.
 */
object CookieSessionManager {

    private const val COOKIE_FILE_NAME = "cookies.txt"
    private const val ONE_YEAR_SECONDS = 365L * 24L * 60L * 60L

    fun cookieFile(context: Context): File = File(context.cacheDir, COOKIE_FILE_NAME)

    fun hasSavedCookies(context: Context): Boolean {
        val file = cookieFile(context)
        return file.exists() && file.length() > 0L
    }

    /**
     * Reads active WebView cookies for [currentUrl] (plus its root domain variants),
     * formats them as standard Netscape HTTP Cookie File lines, and saves to `cacheDir/cookies.txt`.
     */
    fun saveSessionCookies(
        context: Context,
        currentUrl: String,
    ): CookieSaveResult {
        val manager = CookieManager.getInstance()
        manager.flush()

        val normalizedUrl = YoutubeDlMediaEngine.normalizeAndValidateUrl(currentUrl)
        val parsed = normalizedUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid session URL.")
        val baseDomain = extractBaseDomain(parsed.host)

        // Collect cookies from both the active page URL and root domain hosts to ensure complete sessions
        val candidateUrls = linkedSetOf(
            normalizedUrl,
            "https://$baseDomain/",
            "https://www.$baseDomain/",
        )
        if (baseDomain.equals("youtube.com", ignoreCase = true) || baseDomain.equals("google.com", ignoreCase = true)) {
            candidateUrls.add("https://www.youtube.com/")
            candidateUrls.add("https://m.youtube.com/")
            candidateUrls.add("https://youtube.com/")
            candidateUrls.add("https://accounts.google.com/")
        }

        val combinedEntries = LinkedHashMap<Pair<String, String>, String>() // (domain, name) -> value
        candidateUrls.forEach { targetUrl ->
            val rawHeader = manager.getCookie(targetUrl) ?: return@forEach
            val host = targetUrl.toHttpUrlOrNull()?.host ?: parsed.host
            val cookieDomain = ".${extractBaseDomain(host)}"
            parseCookiePairs(rawHeader).forEach { (name, value) ->
                combinedEntries[cookieDomain to name] = value
            }
        }

        require(combinedEntries.isNotEmpty()) {
            "No session cookies found yet. Finish signing in inside the page, then tap Save Session."
        }

        val expiryEpoch = (System.currentTimeMillis() / 1000L) + ONE_YEAR_SECONDS
        val netscapeContent = formatEntriesToNetscape(combinedEntries, expiryEpoch)

        val target = cookieFile(context)
        target.writeText(netscapeContent, Charsets.UTF_8)

        return CookieSaveResult(
            cookieCount = combinedEntries.size,
            domain = baseDomain,
            filePath = target.absolutePath,
        )
    }

    /**
     * Deletes `cacheDir/cookies.txt` and clears Android WebView `CookieManager` cookies.
     */
    fun clearSession(context: Context) {
        runCatching {
            val file = cookieFile(context)
            if (file.exists()) file.delete()
        }
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
        }
    }

    /**
     * Pure JVM-testable converter from a raw `CookieManager.getCookie(url)` string to
     * standard `# Netscape HTTP Cookie File` text.
     */
    fun convertRawCookiesToNetscape(
        url: String,
        rawCookieHeader: String,
        expirationEpochSec: Long = (System.currentTimeMillis() / 1000L) + ONE_YEAR_SECONDS,
    ): String {
        val host = url.toHttpUrlOrNull()?.host
            ?: throw IllegalArgumentException("Invalid cookie URL: $url")
        val cookieDomain = ".${extractBaseDomain(host)}"
        val entries = LinkedHashMap<Pair<String, String>, String>()
        parseCookiePairs(rawCookieHeader).forEach { (name, value) ->
            entries[cookieDomain to name] = value
        }
        return formatEntriesToNetscape(entries, expirationEpochSec)
    }

    internal fun parseCookiePairs(rawCookieHeader: String): List<Pair<String, String>> {
        if (rawCookieHeader.isBlank()) return emptyList()
        return rawCookieHeader.split(';')
            .mapNotNull { token ->
                val trimmed = token.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (name.isEmpty()) null else name to value
            }
    }

    private fun formatEntriesToNetscape(
        entries: Map<Pair<String, String>, String>,
        expirationEpochSec: Long,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# https://curl.se/docs/http-cookies.html")
        sb.appendLine("# This file was generated by Clipper Session Manager.")
        sb.appendLine()
        entries.forEach { (domainAndName, value) ->
            val (domain, name) = domainAndName
            val includeSubdomains = if (domain.startsWith(".")) "TRUE" else "FALSE"
            val path = "/"
            val secure = "TRUE"
            sb.append(domain).append('\t')
                .append(includeSubdomains).append('\t')
                .append(path).append('\t')
                .append(secure).append('\t')
                .append(expirationEpochSec).append('\t')
                .append(name).append('\t')
                .appendLine(value)
        }
        return sb.toString()
    }

    fun extractBaseDomain(host: String): String {
        val clean = host.lowercase(Locale.US).trim().trimEnd('.')
        val parts = clean.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return clean
        val secondLevelTlds = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        return if (parts.last().length == 2 && parts[parts.size - 2] in secondLevelTlds && parts.size >= 3) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    /**
     * Detects whether a yt-dlp error message indicates a login requirement, bot check, or rate-limit
     * that can be resolved by signing in via the optional WebView Cookie Manager.
     */
    fun isAuthOrRateLimitError(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val lower = errorMessage.lowercase(Locale.US)
        val markers = listOf(
            "login required",
            "sign in to confirm",
            "sign in",
            "not a bot",
            "rate-limit",
            "rate limit",
            "http error 429",
            "too many requests",
            "cookies",
            "authentication",
            "logged-in",
            "private video",
            "requested content is not available",
            "age-restricted",
            "401",
        )
        return markers.any { lower.contains(it) }
    }

    fun suggestLoginUrlForMediaUrl(mediaUrl: String): String {
        val host = mediaUrl.trim().toHttpUrlOrNull()?.host?.lowercase(Locale.US).orEmpty()
        return when {
            host.contains("instagram.com") -> "https://www.instagram.com/accounts/login/"
            host.contains("youtube.com") || host.contains("youtu.be") ->
                "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"
            host.contains("x.com") || host.contains("twitter.com") -> "https://x.com/i/flow/login"
            host.contains("tiktok.com") -> "https://www.tiktok.com/login"
            host.contains("facebook.com") || host.contains("fb.watch") -> "https://m.facebook.com/login/"
            host.isNotBlank() -> "https://$host/"
            else -> "https://www.instagram.com/accounts/login/"
        }
    }
}
