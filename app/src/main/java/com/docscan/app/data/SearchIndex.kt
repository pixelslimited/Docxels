package com.docscan.app.data

import android.content.Context
import com.docscan.app.security.SecureFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Words found inside documents (read from scans with on-device OCR, taken from Word documents), so the library search can
 * find a document by what is written in it. Stored encrypted on the phone like the documents themselves.
 */
class SearchIndex(context: Context) {
    class Entry(val stamp: Long, val text: String)

    private val file = File(File(context.filesDir, "library").apply { mkdirs() }, "search.tsv")
    private val lock = Any()
    private val _entries = MutableStateFlow(runCatching { parse(SecureFiles.read(file)?.toString(Charsets.UTF_8).orEmpty()) }.getOrDefault(emptyMap()))
    val entries: StateFlow<Map<String, Entry>> = _entries

    fun put(key: String, stamp: Long, text: String) = synchronized(lock) {
        val map = _entries.value.toMutableMap()
        map[key] = Entry(stamp, clean(text).take(MAX_CHARS))
        write(map)
    }

    fun clear() = synchronized(lock) { write(emptyMap()) }

    private fun write(map: Map<String, Entry>) {
        SecureFiles.write(file, serialize(map).toByteArray(Charsets.UTF_8))
        _entries.value = map
    }

    companion object {
        const val MAX_CHARS = 30_000
        fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()
        fun serialize(map: Map<String, Entry>): String = map.entries.joinToString("\n") { (k, e) -> "$k\t${e.stamp}\t${clean(e.text)}" }
        fun parse(text: String): Map<String, Entry> = text.lines().mapNotNull { line ->
            val p = line.split('\t', limit = 3)
            if (p.size < 3) null else p[0] to Entry(p[1].toLongOrNull() ?: 0L, p[2])
        }.toMap()
    }
}
