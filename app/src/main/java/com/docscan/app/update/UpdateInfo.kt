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
    /** Optional small patch that turns the build [Patch.fromVersionCode] into this one. */
    val patch: Patch? = null,
    /** Patches from several earlier builds, so a phone that skipped a build still gets a small download. */
    val patches: List<Patch> = emptyList(),
) {
    /** The patch that fits the installed build, if any. */
    fun patchFor(installed: Long): Patch? =
        (patches + listOfNotNull(patch)).firstOrNull { it.fromVersionCode == installed && it.size * 10 < size * 8 }

    /** The notes newer than [installed]: lines written "build|text". Notes without that form are returned whole. */
    fun whatsNew(installed: Long): List<String> {
        val out = ArrayList<String>()
        for (raw in notes.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val bar = line.indexOf('|')
            val code = if (bar > 0) line.substring(0, bar).trim().toLongOrNull() else null
            if (code == null) out += line else if (code > installed) out += line.substring(bar + 1).trim()
        }
        return out
    }

    data class Patch(val fromVersionCode: Long, val url: String, val sha256: String, val size: Long)

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
                notes = o.optString("notes", "").take(8000),
            )
            val parsed = java.net.URI(url)
            val ok = parsed.scheme == "https" && hostAllowed(parsed.host) &&
                Regex("[0-9a-f]{64}").matches(sha) && info.size in 1..MAX_APK_BYTES && info.versionCode > 0
            // A malformed or unsafe patch entry is simply ignored; the full download still works.
            fun readPatch(p: JSONObject): Patch? = runCatching {
                val pu = p.getString("url")
                val ps = p.getString("sha256").lowercase()
                val pj = java.net.URI(pu)
                val candidate = Patch(p.getLong("fromVersionCode"), pu, ps, p.getLong("size"))
                if (pj.scheme == "https" && hostAllowed(pj.host) && Regex("[0-9a-f]{64}").matches(ps) &&
                    candidate.size in 1..MAX_APK_BYTES && candidate.fromVersionCode > 0
                ) candidate else null
            }.getOrNull()
            val patch = o.optJSONObject("patch")?.let(::readPatch)
            val patches = o.optJSONArray("patches")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::readPatch) } } ?: emptyList()
            if (ok) info.copy(patch = patch, patches = patches) else null
        } catch (e: Exception) {
            null
        }

        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    fun isNewerThan(installedCode: Long) = versionCode > installedCode
}
