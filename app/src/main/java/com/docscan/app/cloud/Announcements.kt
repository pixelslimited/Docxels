package com.docscan.app.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One message from the Docxels team (see the Messages page of the admin dashboard). */
data class Announcement(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val imagePath: String?,
    val buttonLabel: String?,
    val buttonUrl: String?,
    val audience: String,
)

/**
 * News pop-ups. At most once every [REFRESH_MS], and only if the person left "Announcements and update notices" on,
 * the app asks the server for the live messages. The request is anonymous: it carries the public key only, no account,
 * no device id and nothing about documents. Messages are cached so nothing is fetched again until the next check.
 */
class Announcements(private val context: Context) {
    private val prefs = context.getSharedPreferences("docxels_announce", Context.MODE_PRIVATE)
    private val imageDir = File(context.cacheDir, "announce").apply { mkdirs() }

    fun cached(): List<Announcement> = parse(prefs.getString("rows", "[]") ?: "[]")

    fun seen(id: String): Boolean = prefs.getStringSet("seen", emptySet())?.contains(id) == true

    fun markSeen(id: String) {
        val s = (prefs.getStringSet("seen", emptySet()) ?: emptySet()).toMutableSet()
        // Keep the list from growing for ever: forget ids that are no longer live.
        s.retainAll(cached().map { it.id }.toSet())
        s.add(id)
        prefs.edit().putStringSet("seen", s).apply()
    }

    /** The build whose "update available" notice was already shown (or dismissed). */
    /** Whether the one-time "allow notifications" question was already shown. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) = prefs.edit().putBoolean("asked_notifications", v).apply()

    var noticedUpdate: Long
        get() = prefs.getLong("noticed_update", 0L)
        set(v) = prefs.edit().putLong("noticed_update", v).apply()

    /** The day (days since 1970) the "complete your profile" reminder was last shown, so it appears at most once a day. */
    var profileNudgeDay: Long
        get() = prefs.getLong("profile_nudge_day", 0L)
        set(v) = prefs.edit().putLong("profile_nudge_day", v).apply()

    /** True when 12 hours have passed since the last check. */
    fun due(): Boolean = System.currentTimeMillis() - prefs.getLong("last_check", 0L) >= REFRESH_MS

    fun checked() = prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()

    /** A random code made on first use. It is not linked to an account, a device or a document. */
    private fun installId(): String =
        prefs.getString("install_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }

    /**
     * Sends the anonymous usage ping (see [Analytics]): a random install code, the build number, Android version, phone
     * brand and model, language, and counts of features used and errors seen since the last ping. No name, email,
     * document or anything typed. Runs with the daily check and obeys the same Settings switch. Failures are ignored.
     */
    private fun ping() {
        runCatching {
            val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            val (features, errors) = Analytics.snapshot()
            val body = org.json.JSONObject()
                .put("i", installId()).put("v", code).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("brand", android.os.Build.MANUFACTURER).put("model", android.os.Build.MODEL)
                .put("lang", java.util.Locale.getDefault().language)
                .put("f", org.json.JSONObject(features)).put("e", org.json.JSONObject(errors))
            val c = URL(CloudConfig.URL + "/functions/v1/ping").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 15000; c.readTimeout = 20000
                c.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
                c.setRequestProperty("Authorization", "Bearer " + CloudConfig.PUBLISHABLE_KEY)
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode in 200..299) Analytics.sent(features, errors)
            } finally { c.disconnect() }
        }
    }

    /** Fetches the live messages and caches them. Returns false if offline or the answer was not understood. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        ping()
        runCatching {
            val q = "/rest/v1/announcements?select=id,kind,title,body,image_path,button_label,button_url,audience&order=created_at.desc&limit=10"
            val c = URL(CloudConfig.URL + q).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000; c.readTimeout = 20000
                c.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
                c.setRequestProperty("Authorization", "Bearer " + CloudConfig.PUBLISHABLE_KEY)
                if (c.responseCode !in 200..299) return@runCatching false
                val text = c.inputStream.use { it.readBytes().take(256 * 1024).toByteArray().toString(Charsets.UTF_8) }
                val rows = parse(text)
                prefs.edit().putString("rows", JSONArray().also { a -> rows.forEach { a.put(toJson(it)) } }.toString()).apply()
                true
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Downloads (once) and decodes the picture of a message; null on any problem. Only this project's public bucket is used. */
    suspend fun image(path: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!PATH.matches(path)) return@withContext null
        runCatching {
            val f = File(imageDir, path)
            if (!f.exists()) {
                val c = URL("${CloudConfig.URL}/storage/v1/object/public/announcements/$path").openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15000; c.readTimeout = 30000
                    if (c.responseCode !in 200..299) return@runCatching null
                    val bytes = c.inputStream.use { it.readBytes().take(MAX_IMAGE + 1).toByteArray() }
                    if (bytes.size > MAX_IMAGE) return@runCatching null
                    f.writeBytes(bytes)
                } finally { c.disconnect() }
            }
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            if (b.outWidth <= 0 || b.outWidth > 4000 || b.outHeight > 4000) { f.delete(); return@runCatching null }
            var sample = 1
            while (b.outWidth / sample > 1400) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** The next message to show: the welcome first (once the first-run intro is done), then newest first. */
    fun next(isPro: Boolean, introDone: Boolean): Announcement? =
        cached().filter { !seen(it.id) }
            .filter { it.audience == "all" || (it.audience == "pro") == isPro }
            .filter { it.kind != "welcome" || introDone }
            .sortedBy { if (it.kind == "welcome") 0 else 1 }
            .firstOrNull()

    private fun parse(text: String): List<Announcement> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("id"); val title = o.optString("title"); val body = o.optString("body")
            if (id.isEmpty() || title.isEmpty() || body.isEmpty()) return@mapNotNull null
            val url = o.optString("button_url").takeIf { it.startsWith("https://") && !o.isNull("button_url") }
            Announcement(
                id, o.optString("kind", "broadcast"), title.take(80), body.take(600),
                o.optString("image_path").takeIf { !o.isNull("image_path") && PATH.matches(it) },
                o.optString("button_label").takeIf { url != null && !o.isNull("button_label") && it.isNotBlank() }?.take(30) ?: if (url != null) "Open" else null,
                url, o.optString("audience", "all"),
            )
        }
    }.getOrDefault(emptyList())

    private fun toJson(a: Announcement) = org.json.JSONObject()
        .put("id", a.id).put("kind", a.kind).put("title", a.title).put("body", a.body)
        .put("image_path", a.imagePath ?: org.json.JSONObject.NULL).put("button_label", a.buttonLabel ?: org.json.JSONObject.NULL)
        .put("button_url", a.buttonUrl ?: org.json.JSONObject.NULL).put("audience", a.audience)

    private companion object {
        const val REFRESH_MS = 12 * 60 * 60 * 1000L
        const val MAX_IMAGE = 1_200_000
        val PATH = Regex("^[0-9a-f-]{36}\\.(png|jpg)$")
    }
}
