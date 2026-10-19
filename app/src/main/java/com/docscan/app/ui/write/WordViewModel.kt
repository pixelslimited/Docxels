package com.docscan.app.ui.write

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.word.WordDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordViewModel(private val container: AppContainer, val id: String) : ViewModel() {
    var doc by mutableStateOf<WordDoc?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Things the imported file had that cannot be kept yet; shown once when the document opens. */
    var compatNotes by mutableStateOf<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { container.words.load(id) }.getOrNull() }
            if (loaded == null) failed = true else {
                doc = loaded
                compatNotes = container.words.takeCompatNotes(id)
            }
        }
    }

    /** Saves even if the screen is closing right now. */
    fun save(doc: WordDoc) {
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { container.words.save(id, doc) } }
        }
    }

    /** Keeps a second copy of the document in the library and returns its id. */
    suspend fun saveCopy(doc: WordDoc): String = withContext(Dispatchers.IO) { container.words.add(doc.copy(title = doc.title + " copy")) }

    /** Makes a PDF of the document and keeps it in the library's PDF list. */
    suspend fun savePdfToLibrary(doc: WordDoc, context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val f = pdfFile(doc, context)
            container.pdfs.add(f, doc.title, runCatching { com.docscan.app.pdf.PdfTools(context).pageCount(f) }.getOrDefault(0))
        }.isSuccess
    }

    suspend fun docxFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val name = doc.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
        java.io.File(dir, "$name.docx").also { f ->
            f.writeBytes(com.docscan.app.core.word.DocxWriter.toBytes(doc)) // the shared copy must be readable by other apps
        }
    }

    private fun safeName(doc: WordDoc) = com.docscan.app.core.safeFileName(doc.title)

    suspend fun txtFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        java.io.File(dir, "${safeName(doc)}.txt").also { it.writeText(com.docscan.app.core.word.PlainExport.toText(doc), Charsets.UTF_8) }
    }

    suspend fun rtfFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        java.io.File(dir, "${safeName(doc)}.rtf").also { it.writeText(com.docscan.app.core.word.PlainExport.toRtf(doc), Charsets.US_ASCII) }
    }

    suspend fun pdfFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.Default) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val name = doc.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
        java.io.File(dir, "$name.pdf").also { f -> f.outputStream().use { com.docscan.app.export.WordPdf.write(doc, it) } }
    }
}
