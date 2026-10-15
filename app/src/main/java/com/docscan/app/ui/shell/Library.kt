package com.docscan.app.ui.shell

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.Geometry
import com.docscan.app.data.DocKind
import com.docscan.app.data.ImageIO
import com.docscan.app.data.LibraryItem
import com.docscan.app.export.Mime
import com.docscan.app.export.ShareHelper
import com.docscan.app.imaging.EdgeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

/**
 * One library for everything the user has made: scans and Word documents together, newest first. All the
 * actions the Home, Documents and Tools screens share (create, scan, import, convert, share) live here.
 */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    val items: StateFlow<List<LibraryItem>> = combine(container.repository.observeHome(), container.words.items) { scans, words ->
        val a = scans.map {
            LibraryItem(DocKind.SCAN, it.document.id, it.document.name, it.document.updatedAt, "${it.pageCount} page${if (it.pageCount == 1) "" else "s"}", it.cover)
        }
        val b = words.map { LibraryItem(DocKind.DOCX, it.id, it.title, it.modified, "Word document", null) }
        (a + b).sortedByDescending { it.modified }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun messageShown() { _message.value = null }

    fun say(text: String) { _message.value = text }

    fun cleanUpEmptyDocuments() {
        viewModelScope.launch { container.repository.deleteEmptyDocuments() }
    }

    fun startScan(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(container.repository.createDocument()) }
    }

    fun newWord(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(withContext(Dispatchers.IO) { container.words.create() }) }
    }

    fun rename(item: LibraryItem, name: String) {
        viewModelScope.launch {
            when (item.kind) {
                DocKind.SCAN -> container.repository.renameDocument(item.id, name)
                DocKind.DOCX -> withContext(Dispatchers.IO) { container.words.rename(item.id, name) }
            }
        }
    }

    fun delete(item: LibraryItem) {
        viewModelScope.launch {
            when (item.kind) {
                DocKind.SCAN -> container.repository.deleteDocument(item.id)
                DocKind.DOCX -> withContext(Dispatchers.IO) { container.words.delete(item.id) }
            }
        }
    }

    /** Imports photos from the gallery into a new scan document, detecting page edges for each. */
    fun importImages(context: Context, uris: List<Uri>, onDone: (String) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = "Importing photos…"
            val docId = container.repository.createDocument()
            var added = 0
            for (uri in uris) {
                val ok = runCatching {
                    val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(context, uri, 3000) } ?: error("Unreadable image")
                    val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
                    container.repository.addPage(docId, bitmap, corners)
                    bitmap.recycle()
                }.isSuccess
                if (ok) added++
            }
            _busy.value = null
            if (added == 0) {
                container.repository.deleteDocument(docId)
                _message.value = "Could not read the selected images."
            } else {
                if (added < uris.size) _message.value = "${uris.size - added} image(s) could not be imported."
                onDone(docId)
            }
        }
    }

    fun importWord(context: Context, uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = "Opening document…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var name = "Imported"
                    var size = -1L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                            c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                        }
                    }
                    require(size < 0 || size <= MAX_IMPORT_BYTES) { "That file is too large to import." }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_IMPORT_BYTES.toInt() + 1) }
                        ?: error("Could not read that file.")
                    require(bytes.size <= MAX_IMPORT_BYTES) { "That file is too large to import." }
                    container.words.importDocx(bytes, name.removeSuffix(".docx").removeSuffix(".DOCX"))
                }
            }
            _busy.value = null
            result.onSuccess(onDone).onFailure {
                _message.value = it.message?.takeIf { m -> m.length < 120 } ?: "That file could not be opened as a Word document."
            }
        }
    }

    /** Converts a PDF from the phone's storage into an editable Word document. */
    fun pdfToWord(context: Context, uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = "Opening PDF…"
            val result = runCatching {
                val (file, name) = withContext(Dispatchers.IO) {
                    var n = "PDF"
                    var size = -1L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { n = c.getString(it) ?: n }
                            c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                        }
                    }
                    require(size < 0 || size <= MAX_IMPORT_BYTES) { "That PDF is too large to convert." }
                    val dir = File(context.cacheDir, "pdf2word").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val f = File(dir, "source.pdf")
                    val ok = context.contentResolver.openInputStream(uri)?.use { com.docscan.app.pdf.copyStreamToFile(it, f, MAX_IMPORT_BYTES) } ?: false
                    require(ok) { "Could not read that PDF." }
                    f to n.removeSuffix(".pdf").removeSuffix(".PDF")
                }
                try {
                    container.pdfToWord.convert(file, name, { p, total -> _busy.value = "Converting page $p of $total…" }, { t -> _busy.value = t })
                } finally {
                    file.delete()
                }
            }
            result.onSuccess { conv ->
                val id = withContext(Dispatchers.IO) { container.words.add(conv.doc) }
                _busy.value = null
                if (conv.notes.isNotEmpty()) _message.value = conv.notes.joinToString(" ")
                onDone(id)
            }.onFailure {
                _busy.value = null
                _message.value = when (it) {
                    is com.docscan.app.pdf.PdfPasswordException -> "This PDF is password protected. Remove the password first (Tools → PDF), then convert it."
                    else -> it.message?.takeIf { m -> m.length < 120 } ?: "Could not convert that PDF."
                }
            }
        }
    }

    /** OCRs a scan into an editable Word document. */
    fun scanToWord(scanId: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val pages = container.repository.pages(scanId)
            val name = items.value.firstOrNull { it.id == scanId && it.kind == DocKind.SCAN }?.title ?: "Scan"
            if (pages.isEmpty()) {
                _message.value = "That scan has no pages."
                return@launch
            }
            _busy.value = "Reading page 1 of ${pages.size}…"
            runCatching {
                container.scanToWord.convert(pages, name) { n, total -> _busy.value = "Reading page $n of $total…" }
            }.onSuccess { doc ->
                val id = withContext(Dispatchers.IO) { container.words.add(doc) }
                _busy.value = null
                onDone(id)
            }.onFailure {
                _busy.value = null
                _message.value = "Could not convert to a Word document."
            }
        }
    }

    fun shareDocx(context: Context, item: LibraryItem) {
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val bytes = container.words.docxBytes(item.id) ?: error("missing")
                    File(File(context.cacheDir, "exports").apply { mkdirs() }, safeName(item.title) + ".docx").also { it.writeBytes(bytes) }
                }
                ShareHelper.share(context, listOf(file), Mime.DOCX)
            }.onFailure { _message.value = "Could not share the document." }
        }
    }

    fun sharePdf(context: Context, item: LibraryItem) {
        viewModelScope.launch {
            _busy.value = "Preparing PDF…"
            runCatching {
                val file = withContext(Dispatchers.Default) {
                    val doc = container.words.load(item.id) ?: error("missing")
                    File(File(context.cacheDir, "exports").apply { mkdirs() }, safeName(item.title) + ".pdf").also { f ->
                        f.outputStream().use { com.docscan.app.export.WordPdf.write(doc, it) }
                    }
                }
                ShareHelper.share(context, listOf(file), Mime.PDF)
            }.onFailure { _message.value = "Could not create the PDF." }
            _busy.value = null
        }
    }

    private fun safeName(title: String) = title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
}
