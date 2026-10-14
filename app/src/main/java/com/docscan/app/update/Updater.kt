package com.docscan.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The only networking in Docxels. It runs only when the user taps "Check for updates" or "Download and install",
 * sends nothing about the user or their documents, and only talks to the hosts in [UpdateInfo.hostAllowed].
 */
class Updater(private val context: Context) {
    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun installedCode(): Long {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        return p.longVersionCode
    }

    /** Returns the newest build if it is newer than this one, null if up to date. Throws on network or format problems. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = open(UpdateInfo.MANIFEST_URL).use { (stream, _) -> readLimited(stream, 64 * 1024).toString(Charsets.UTF_8) }
        val info = UpdateInfo.parse(text) ?: error("The update information was not valid.")
        if (info.isNewerThan(installedCode())) info else null
    }

    /** Downloads and verifies the APK. [progress] gets 0..1. Returns the file, or throws. */
    suspend fun download(info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Docxels-${info.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        open(info.apkUrl).use { (stream, length) ->
            val total = if (length > 0) length else info.size
            var done = 0L
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    done += n
                    if (done > UpdateInfo.MAX_APK_BYTES || done > info.size + 1024) error("The download is larger than expected.")
                    digest.update(buf, 0, n)
                    o.write(buf, 0, n)
                    progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != info.sha256 || out.length() != info.size) {
            out.delete()
            error("The download did not match its fingerprint, so it was discarded.")
        }
        if (!sameSigner(out)) {
            out.delete()
            error("The downloaded file is not signed by Pixels Limited, so it was discarded.")
        }
        out
    }

    /** The downloaded APK must carry exactly the signing certificate of the installed app. */
    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val mine = pm.getPackageInfo(context.packageName, flags).signingInfo?.apkContentsSigners ?: return false
        val theirs = pm.getPackageArchiveInfo(apk.absolutePath, flags)?.signingInfo?.apkContentsSigners ?: return false
        val a = mine.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }.toSet()
        val b = theirs.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toList() }.toSet()
        return a.isNotEmpty() && a == b
    }

    /** True if Android lets Docxels start an install. If not, opens the settings page where the user allows it. */
    fun ensureInstallPermission(): Boolean {
        if (context.packageManager.canRequestPackageInstalls()) return true
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return false
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Opens an HTTPS connection, following up to 5 redirects, each of which must stay on an allowed host. */
    private fun open(start: String): ConnResult {
        var url = start
        repeat(6) {
            val u = URL(url)
            require(u.protocol == "https" && UpdateInfo.hostAllowed(u.host)) { "Blocked address" }
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "Docxels-updater")
            when (val code = c.responseCode) {
                200 -> return ConnResult(c.inputStream, c.contentLengthLong, c)
                301, 302, 303, 307, 308 -> {
                    url = URL(u, c.getHeaderField("Location") ?: error("Bad redirect")).toString()
                    c.disconnect()
                }
                else -> { c.disconnect(); error("The update server answered $code.") }
            }
        }
        error("Too many redirects")
    }

    private class ConnResult(val stream: InputStream, val length: Long, val conn: HttpURLConnection) : AutoCloseable {
        operator fun component1() = stream
        operator fun component2() = length
        override fun close() { runCatching { stream.close() }; conn.disconnect() }
    }

    private fun readLimited(s: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) error("Response too large")
        }
        return out.toByteArray()
    }
}
