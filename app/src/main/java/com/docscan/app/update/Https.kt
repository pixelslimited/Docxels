package com.docscan.app.update

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The one place the app opens a download: HTTPS only, hosts limited to [UpdateInfo.hostAllowed], also after redirects. */
internal object Https {
    /** Opens [start], following up to 5 redirects, each of which must stay on an allowed host. */
    fun open(start: String): Conn {
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
                200 -> return Conn(c.inputStream, c.contentLengthLong, c)
                301, 302, 303, 307, 308 -> {
                    url = URL(u, c.getHeaderField("Location") ?: error("Bad redirect")).toString()
                    c.disconnect()
                }
                else -> { c.disconnect(); error("The server answered $code.") }
            }
        }
        error("Too many redirects")
    }

    class Conn(val stream: InputStream, val length: Long, private val conn: HttpURLConnection) : AutoCloseable {
        operator fun component1() = stream
        operator fun component2() = length
        override fun close() { runCatching { stream.close() }; conn.disconnect() }
    }
}
