package com.docscan.app.update

import org.json.JSONObject

/** What the update file on the server says about the newest build. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val sha256: String,
    val size: Long,
    val apkUrl: String,
    val notes: String,
) {
    companion object {
        const val MANIFEST_URL = "https://github.com/iCode010101/docxels-site/releases/latest/download/update.json"

        /** Hosts an update may be fetched from. Everything else is refused, including after redirects. */
        fun hostAllowed(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return h == "github.com" || h.endsWith(".githubusercontent.com") ||
                h == "docxels.pixelslimited.ng"
        }

        /** Returns null for anything malformed or unsafe, so a bad file can never trigger an install. */
        fun parse(text: String): UpdateInfo? = try {
            val o = JSONObject(text)
            val url = o.getString("apkUrl")
            val sha = o.getString("sha256").lowercase()
            val info = UpdateInfo(
                versionCode = o.getLong("versionCode"),
                versionName = o.getString("versionName").take(32),
                sha256 = sha,
                size = o.getLong("size"),
                apkUrl = url,
                notes = o.optString("notes", "").take(600),
            )
            val parsed = java.net.URI(url)
            val ok = parsed.scheme == "https" && hostAllowed(parsed.host) &&
                Regex("[0-9a-f]{64}").matches(sha) && info.size in 1..MAX_APK_BYTES && info.versionCode > 0
            if (ok) info else null
        } catch (e: Exception) {
            null
        }

        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    fun isNewerThan(installedCode: Long) = versionCode > installedCode
}
