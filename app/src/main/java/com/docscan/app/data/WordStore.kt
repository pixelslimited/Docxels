package com.docscan.app.data

import android.content.Context
import com.docscan.app.core.word.DocxReader
import com.docscan.app.core.word.DocxWriter
import com.docscan.app.core.word.WordDoc
import com.docscan.app.security.SecureFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

data class WordItem(val id: String, val title: String, val modified: Long)

/**
 * Word documents are stored as real .docx files (encrypted at rest like scan pages) with a small index
 * for the list. Everything is synchronous and cheap; callers run it off the main thread.
 */
class WordStore(context: Context) {
    private val dir = File(context.filesDir, "words").apply { mkdirs() }
    private val indexFile = File(dir, "index.tsv")
    private val lock = Any()

    private val _items = MutableStateFlow(readIndex())
    val items: StateFlow<List<WordItem>> = _items

    private fun file(id: String) = File(dir, "$id.docx")

    private fun readIndex(): List<WordItem> = runCatching {
        val raw = SecureFiles.read(indexFile)?.toString(Charsets.UTF_8) ?: return@runCatching emptyList()
        raw.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) null else WordItem(p[0], p[2].replace("\\t", " ").replace("\\n", " "), p[1].toLongOrNull() ?: 0L)
        }.filter { file(it.id).exists() }.sortedByDescending { it.modified }
    }.getOrDefault(emptyList())

    private fun writeIndex(list: List<WordItem>) {
        val text = list.joinToString("\n") { "${it.id}\t${it.modified}\t${it.title.replace('\t', ' ').replace('\n', ' ')}" }
        SecureFiles.write(indexFile, text.toByteArray(Charsets.UTF_8))
        _items.value = list.sortedByDescending { it.modified }
    }

    /** What the file had that Docxels cannot keep yet, per document, until the editor has shown it once. */
    private val compatNotes = HashMap<String, List<String>>()

    fun takeCompatNotes(id: String): List<String> = synchronized(lock) { compatNotes.remove(id).orEmpty() }

    fun create(title: String = "Untitled"): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        SecureFiles.write(file(id), DocxWriter.toBytes(WordDoc(title = title)))
        writeIndex(_items.value + WordItem(id, title, System.currentTimeMillis()))
        id
    }

    fun load(id: String): WordDoc? = synchronized(lock) {
        val bytes = SecureFiles.read(file(id)) ?: return null
        val title = _items.value.firstOrNull { it.id == id }?.title ?: "Untitled"
        DocxReader.read(bytes, title).copy(title = title)
    }

    fun save(id: String, doc: WordDoc) = synchronized(lock) {
        SecureFiles.write(file(id), DocxWriter.toBytes(doc))
        val now = System.currentTimeMillis()
        writeIndex(_items.value.map { if (it.id == id) it.copy(title = doc.title, modified = now) else it })
    }

    fun rename(id: String, title: String) = synchronized(lock) {
        val doc = load(id) ?: return
        save(id, doc.copy(title = title.ifBlank { "Untitled" }))
    }

    fun delete(id: String) = synchronized(lock) {
        SecureFiles.wipe(file(id))
        writeIndex(_items.value.filterNot { it.id == id })
    }

    /** Imports a .docx; throws a readable exception when the file is not a usable Word document. */
    fun importDocx(bytes: ByteArray, fallbackTitle: String): String = synchronized(lock) {
        val doc = DocxReader.read(bytes, fallbackTitle)
        val id = UUID.randomUUID().toString()
        val notes = DocxReader.compatNotes(bytes)
        if (notes.isNotEmpty()) compatNotes[id] = notes
        SecureFiles.write(file(id), DocxWriter.toBytes(doc))
        writeIndex(_items.value + WordItem(id, doc.title, System.currentTimeMillis()))
        id
    }

    /** Stores a document built in memory (for example from a scan). */
    fun add(doc: WordDoc): String = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        SecureFiles.write(file(id), DocxWriter.toBytes(doc))
        writeIndex(_items.value + WordItem(id, doc.title, System.currentTimeMillis()))
        id
    }

    fun docxBytes(id: String): ByteArray? = SecureFiles.read(file(id))
}
