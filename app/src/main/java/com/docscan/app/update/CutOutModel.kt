package com.docscan.app.update

import android.content.Context
import com.docscan.app.imaging.BackgroundRemover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The small subject model (U^2-Net-P) that makes Remove background cut cleanly. It is not inside the app: it is fetched
 * once, on the person's say-so, from the Docxels site, checked against a fixed fingerprint and kept on the phone.
 * Nothing about the person or their pictures is sent; the pictures are cut on the phone.
 */
object CutOutModel {
    const val URL = "https://github.com/iCode010101/docxels-site/releases/download/models/u2netp.onnx"
    private const val SHA256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"
    const val SIZE = 4_574_861L

    fun file(context: Context) = File(File(context.filesDir, "models"), "u2netp.onnx")

    fun ready(context: Context): Boolean = file(context).let { it.isFile && it.length() == SIZE }

    /** Downloads and verifies the model. Returns null when it worked, otherwise a sentence saying what went wrong. */
    suspend fun download(context: Context, progress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val out = file(context)
        val part = File(out.parentFile, "u2netp.onnx.part")
        try {
            out.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            Https.open(URL).use { (stream, _) ->
                var done = 0L
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > SIZE) error("The download is larger than expected.")
                        digest.update(buf, 0, n)
                        o.write(buf, 0, n)
                        progress((done.toFloat() / SIZE).coerceIn(0f, 1f))
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex != SHA256 || part.length() != SIZE) error("The download did not match its fingerprint, so it was discarded.")
            if (!part.renameTo(out)) error("Couldn't save the model on this phone.")
            BackgroundRemover.reload()
            null
        } catch (e: Exception) {
            part.delete()
            e.message?.takeIf { it.isNotBlank() } ?: "The download failed. Check your connection and try again."
        }
    }
}
