package com.docscan.app.update

import org.json.JSONObject

/** Where a feature sits: for everyone, only for Pro members, or a fix. */
enum class NoteTier(val label: String) { FREE("Free"), PRO("Pro"), FIX("Fixes") }

/** One line of what's new, written "build|TIER|Title|Sentence". */
data class Note(val code: Long, val tier: NoteTier, val title: String, val detail: String) {
    companion object {
        /** Lines that are not in that form are ignored, so a bad notes text can only ever show fewer cards. */
        fun parse(raw: String): List<Note> = raw.lines().mapNotNull { line ->
            val p = line.trim().split('|', limit = 4)
            val code = p.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
            val tier = NoteTier.entries.firstOrNull { it.name == p.getOrNull(1)?.trim()?.uppercase() } ?: return@mapNotNull null
            val title = p.getOrNull(2)?.trim().orEmpty().ifEmpty { return@mapNotNull null }
            Note(code, tier, title, p.getOrNull(3)?.trim().orEmpty())
        }
    }
}

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

    /** The what's-new lines of builds newer than [installed]. */
    fun whatsNew(installed: Long): List<Note> = Note.parse(notes).filter { it.code > installed }

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
