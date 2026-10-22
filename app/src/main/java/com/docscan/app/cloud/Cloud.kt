package com.docscan.app.cloud

import android.content.Context
import com.docscan.app.security.SecureFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Public by design: the project address and the publishable key. Secrets (AI key, Paystack key) live only on the server. */
object CloudConfig {
    const val URL = "https://nakwuhzrcbmfjyscodyr.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_Mzy575TimsBYbty4jdt4tQ_F4ZWBsb9"
}

class CloudException(message: String, val limitReached: Boolean = false) : Exception(message)

data class Session(val email: String, val access: String, val refresh: String, val expiresAtSec: Long, val first: String = "", val last: String = "") {
    val fullName: String get() = "$first $last".trim()
}

data class Plan(val pro: Boolean, val proUntil: String?)

/** Parsed grammar finding with character offsets into the text that was sent. */
data class WritingIssue(val start: Int, val end: Int, val kind: String, val message: String, val replacement: String)

/**
 * Talks to the Docxels backend, and only when the person signs in, uses an assistant action, or upgrades.
 * Document text leaves the phone only for the action they tapped. The only background request is the anonymous
 * once-a-day news check in [Announcements].
 */
class Cloud(context: Context) {
    private val file = File(context.filesDir, "cloud.session")
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _plan = MutableStateFlow<Plan?>(null)
    /** Last known plan of the signed-in person (null when signed out or not yet checked). */
    val planState: StateFlow<Plan?> = _plan

    init { _session.value = load(); com.docscan.app.core.word.DocOwner.set(_session.value?.fullName) }

    suspend fun refreshPlan() {
        if (_session.value == null) { _plan.value = null; return }
        runCatching { plan() }.onSuccess { _plan.value = it }
    }

    private fun load(): Session? = runCatching {
        val j = JSONObject(SecureFiles.read(file)!!.toString(Charsets.UTF_8))
        Session(j.getString("email"), j.getString("access"), j.getString("refresh"), j.getLong("exp"), j.optString("first"), j.optString("last"))
    }.getOrNull()

    private fun store(s: Session?) {
        _session.value = s
        com.docscan.app.core.word.DocOwner.set(s?.fullName)
        if (s == null) file.delete()
        else SecureFiles.write(file, JSONObject().put("email", s.email).put("access", s.access).put("refresh", s.refresh).put("exp", s.expiresAtSec).put("first", s.first).put("last", s.last).toString().toByteArray())
    }

    private suspend fun call(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL(CloudConfig.URL + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
            c.setRequestProperty("Authorization", "Bearer " + (token ?: CloudConfig.PUBLISHABLE_KEY))
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.use { it.readBytes().take(512 * 1024).toByteArray().toString(Charsets.UTF_8) } ?: "")
        } finally { c.disconnect() }
    }

    private fun parseSession(text: String, fallbackEmail: String, previous: Session? = null): Session? {
        val j = JSONObject(text)
        val access = j.optString("access_token").ifEmpty { return null }
        val email = j.optJSONObject("user")?.optString("email")?.ifEmpty { null } ?: fallbackEmail
        val meta = j.optJSONObject("user")?.optJSONObject("user_metadata")
        val first = meta?.optString("first_name").orEmpty().ifEmpty { previous?.first.orEmpty() }
        val last = meta?.optString("last_name").orEmpty().ifEmpty { previous?.last.orEmpty() }
        return Session(email, access, j.getString("refresh_token"), System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600) - 60, first, last)
    }

    private fun friendly(text: String): String = runCatching {
        val j = JSONObject(text)
        j.optString("msg").ifEmpty { j.optString("error_description").ifEmpty { j.optString("error") } }
    }.getOrDefault("").ifEmpty { "Something went wrong. Try again." }

    /** Creates an account. Returns true if signed in at once, false if the person must confirm their email first. */
    suspend fun signUp(email: String, password: String, first: String, last: String): Boolean {
        fun clean(v: String) = v.trim().replace(Regex("\\s+"), " ").take(30)
        if (clean(first).isEmpty() || clean(last).isEmpty()) throw CloudException("Enter both your first and your last name.")
        val data = JSONObject().put("first_name", clean(first)).put("last_name", clean(last))
        val (code, text) = call("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", password).put("data", data), null)
        if (code !in 200..299) throw CloudException(friendly(text))
        val s = parseSession(text, email) ?: return false
        store(s); return true
    }

    /** Emails a password-reset link that opens the Docxels reset page. Always looks successful so nobody can probe which emails have accounts. */
    suspend fun requestPasswordReset(email: String) {
        val (code, text) = call("POST", "/auth/v1/recover?redirect_to=" + java.net.URLEncoder.encode("https://docxels.pixelslimited.ng/reset/", "UTF-8"), JSONObject().put("email", email), null)
        if (code == 429) throw CloudException("Too many requests. Wait a minute and try again.")
        if (code >= 500) throw CloudException(friendly(text))
    }

    suspend fun signIn(email: String, password: String) {
        val (code, text) = call("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), null)
        if (code !in 200..299) throw CloudException(friendly(text))
        store(parseSession(text, email) ?: throw CloudException("Could not sign in."))
    }

    fun signOut() = store(null)

    /** Saves the first and last name on the account (so every phone shows them) and updates this phone at once. */
    suspend fun saveProfile(first: String, last: String) {
        fun clean(v: String) = v.trim().replace(Regex("\\s+"), " ").take(30)
        val f = clean(first); val l = clean(last)
        if (f.isEmpty() || l.isEmpty()) throw CloudException("Enter both your first and your last name.")
        val (code, text) = call("PUT", "/auth/v1/user", JSONObject().put("data", JSONObject().put("first_name", f).put("last_name", l)), token())
        if (code !in 200..299) throw CloudException(friendly(text))
        _session.value?.let { store(it.copy(first = f, last = l)) }
    }

    private suspend fun token(): String {
        val s = _session.value ?: throw CloudException("Sign in first.")
        if (System.currentTimeMillis() / 1000 < s.expiresAtSec) return s.access
        val (code, text) = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refresh), null)
        if (code !in 200..299) { store(null); throw CloudException("Your session ended. Sign in again.") }
        val n = parseSession(text, s.email, s) ?: throw CloudException("Your session ended. Sign in again.")
        store(n); return n.access
    }

    /** Runs one assistant action. [task] is one of the server's task names; the server limits use per day. */
    suspend fun ai(task: String, text: String, instruction: String = "", question: String = ""): String {
        val body = JSONObject().put("task", task).put("text", text).put("instruction", instruction).put("question", question)
        val (code, resp) = call("POST", "/functions/v1/ai", body, token())
        if (code in 200..299) return JSONObject(resp).getString("result")
        throw CloudException(friendly(resp), limitReached = code == 429)
    }

    suspend fun checkWriting(text: String): List<WritingIssue> {
        val raw = ai("grammar", text).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray = runCatching { JSONObject(raw).getJSONArray("issues") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val a = o.optInt("start", -1); val b = o.optInt("end", -1)
            if (a < 0 || b <= a || b > text.length) null
            else WritingIssue(a, b, o.optString("kind"), o.optString("message"), o.optString("replacement"))
        }
    }

    suspend fun plan(): Plan {
        val (code, text) = call("GET", "/rest/v1/entitlements?select=plan,pro_until", null, token())
        if (code !in 200..299) throw CloudException(friendly(text))
        val a = JSONArray(text)
        if (a.length() == 0) return Plan(false, null)
        val o = a.getJSONObject(0)
        val until = o.optString("pro_until").takeIf { it.isNotEmpty() && it != "null" }
        val active = o.optString("plan") == "pro" && (until == null || until > java.time.Instant.now().toString())
        return Plan(active, until)
    }

    /** Address of the Paystack payment page for Pro. Opened in the browser; the webhook then turns Pro on. */
    suspend fun checkoutUrl(): String {
        val (code, text) = call("POST", "/functions/v1/paystack-init", JSONObject(), token())
        if (code !in 200..299) throw CloudException(friendly(text))
        return JSONObject(text).getString("url")
    }
}
