package com.docscan.app.data

import android.content.Context
import com.docscan.app.security.SecureFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

data class PdfItem(val id: String, val title: String, val modified: Long, val pages: Int)

/**
 * PDFs kept inside Docxels (encrypted at rest like scans and Word files), so results of the PDF tools, signed copies and
 * exports can be saved in the library and opened again later.
 */
class PdfStore(private val context: Context) {
    private val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
    private val indexFile = File(dir, "index.tsv")
    private val lock = Any()

    private val _items = MutableStateFlow(readIndex())
    val items: StateFlow<List<PdfItem>> = _items

    private fun file(id: String) = File(dir, "$id.pdf")

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun readIndex(): List<PdfItem> = runCatching {
        val raw = SecureFiles.read(indexFile)?.toString(Charsets.UTF_8) ?: return@runCatching emptyList()
        raw.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) null else PdfItem(p[0], p[3], p[1].toLongOrNull() ?: 0L, p[2].toIntOrNull() ?: 0)
        }.filter { file(it.id).exists() }.sortedByDescending { it.modified }
    }.getOrDefault(emptyList())

    private fun writeIndex(list: List<PdfItem>) {
        SecureFiles.write(indexFile, list.joinToString("\n") { "${it.id}\t${it.modified}\t${it.pages}\t${clean(it.title)}" }.toByteArray(Charsets.UTF_8))
        _items.value = list.sortedByDescending { it.modified }
    }

    /** Stores a copy of [source] and returns its id. */
    fun add(source: File, title: String, pages: Int): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        SecureFiles.write(file(id), source.readBytes())
        writeIndex(_items.value + PdfItem(id, title.ifBlank { "Document" }, System.currentTimeMillis(), pages))
        id
    }

    /** A readable copy in the cache (the stored one is encrypted); the caller may delete it when done. */
    fun open(id: String): File? = synchronized(lock) {
        val bytes = SecureFiles.read(file(id)) ?: return null
        val title = _items.value.firstOrNull { it.id == id }?.title ?: "Document"
        val out = File(File(context.cacheDir, "pdfview").apply { mkdirs() }, "${id.take(8)}-${title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(40)}.pdf")
        out.writeBytes(bytes)
        out
    }

    fun rename(id: String, title: String) = synchronized(lock) {
        writeIndex(_items.value.map { if (it.id == id) it.copy(title = title.ifBlank { "Document" }) else it })
    }

    fun delete(id: String) = synchronized(lock) {
        SecureFiles.wipe(file(id))
        writeIndex(_items.value.filterNot { it.id == id })
    }
}
