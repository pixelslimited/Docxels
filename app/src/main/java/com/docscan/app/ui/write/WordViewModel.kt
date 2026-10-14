package com.docscan.app.ui.write

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { container.words.load(id) }.getOrNull() }
            if (loaded == null) failed = true else doc = loaded
        }
    }

    /** Saves even if the screen is closing right now. */
    fun save(doc: WordDoc) {
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { runCatching { container.words.save(id, doc) } }
        }
    }

    suspend fun docxFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val name = doc.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
        java.io.File(dir, "$name.docx").also { f ->
            f.writeBytes(com.docscan.app.core.word.DocxWriter.toBytes(doc)) // the shared copy must be readable by other apps
        }
    }

    suspend fun pdfFile(doc: WordDoc, context: android.content.Context): java.io.File = withContext(Dispatchers.Default) {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val name = doc.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
        java.io.File(dir, "$name.pdf").also { f -> f.outputStream().use { com.docscan.app.export.WordPdf.write(doc, it) } }
    }
}
