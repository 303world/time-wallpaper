package io.github.world303.timewallpaper.core.update

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal GitHub Releases client — no third-party HTTP stack, one request, no background polling.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    const val REPO = "303world/time-wallpaper"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class ReleaseInfo(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?,
    )

    fun check(): Result<ReleaseInfo> {
        return try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TimeWallpaper-Android")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    return Result.failure(IllegalStateException("HTTP $code"))
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                val notes = json.optString("body")
                val pageUrl = json.optString("html_url").ifBlank { RELEASES_URL }
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                Result.success(ReleaseInfo(tag, notes, pageUrl, apkUrl))
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "check failed", t)
            Result.failure(t)
        }
    }

    fun normalizeVersion(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    /** Returns true when [remote] is newer than [local]. Compares dot-separated numeric parts. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote)
        val l = parse(local)
        if (r.isEmpty() || l.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        return normalizeVersion(version)
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.toIntOrNull() }
    }
}
