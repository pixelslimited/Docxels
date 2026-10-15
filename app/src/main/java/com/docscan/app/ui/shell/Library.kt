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
import com.docscan.app.data.ItemMeta
import com.docscan.app.data.LibraryMeta
import com.docscan.app.data.LibraryItem
import com.docscan.app.export.Mime
import com.docscan.app.export.ShareHelper
import com.docscan.app.imaging.EdgeDetector
import com.docscan.app.imaging.toRgbMat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TRASH_DAYS = 30
private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

/**
 * One library for everything the user has made: scans and Word documents together, newest first. All the
 * actions the Home, Documents and Tools screens share (create, scan, import, convert, share) live here.
 */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val everything: StateFlow<List<LibraryItem>> = combine(container.repository.observeHome(), container.words.items, container.libraryMeta.all) { scans, words, meta ->
        val a = scans.map {
            LibraryItem(DocKind.SCAN, it.document.id, it.document.name, it.document.updatedAt, "${it.pageCount} page${if (it.pageCount == 1) "" else "s"}", it.cover, meta[LibraryMeta.key(DocKind.SCAN, it.document.id)] ?: ItemMeta())
        }
        val b = words.map { LibraryItem(DocKind.DOCX, it.id, it.title, it.modified, "Word document", null, meta[LibraryMeta.key(DocKind.DOCX, it.id)] ?: ItemMeta()) }
        (a + b).sortedByDescending { it.modified }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything that is not in Trash. */
    val items: StateFlow<List<LibraryItem>> = everything.map { l -> l.filter { it.meta.trashedAt == 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What is in Trash, newest first. */
    val trash: StateFlow<List<LibraryItem>> = everything.map { l -> l.filter { it.meta.trashedAt != 0L }.sortedByDescending { it.meta.trashedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFavourite(item: LibraryItem, on: Boolean) = container.libraryMeta.update(item.kind, item.id) { it.copy(favourite = on) }
    fun moveToFolder(item: LibraryItem, folder: String) = container.libraryMeta.update(item.kind, item.id) { it.copy(folder = LibraryMeta.cleanName(folder)) }
    fun setTags(item: LibraryItem, text: String) = container.libraryMeta.update(item.kind, item.id) { m ->
        m.copy(tags = text.split(',', ' ').map { LibraryMeta.cleanName(it) }.filter { it.isNotEmpty() }.toSet())
    }
    fun restore(item: LibraryItem) = container.libraryMeta.update(item.kind, item.id) { it.copy(trashedAt = 0L) }

    /** Items sit in Trash for 30 days, then they are deleted for good. */
    fun emptyOldTrash() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_500) // let the lists load first
            val cutoff = System.currentTimeMillis() - TRASH_DAYS * 86_400_000L
            everything.value.filter { it.meta.trashedAt != 0L && it.meta.trashedAt < cutoff }.forEach { deleteForever(it) }
        }
    }

    fun emptyTrash() { viewModelScope.launch { trash.value.forEach { deleteForever(it) } } }

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

    fun newFromTemplate(id: String, onReady: (String) -> Unit) {
        val t = com.docscan.app.core.word.Templates.byId(id) ?: return
        viewModelScope.launch { onReady(withContext(Dispatchers.IO) { container.words.add(t.build()) }) }
    }

    fun rename(item: LibraryItem, name: String) {
        viewModelScope.launch {
            when (item.kind) {
                DocKind.SCAN -> container.repository.renameDocument(item.id, name)
                DocKind.DOCX -> withContext(Dispatchers.IO) { container.words.rename(item.id, name) }
            }
        }
    }

    /** Moves to Trash; nothing is erased until it is deleted from Trash (or 30 days pass). */
    fun delete(item: LibraryItem) {
        container.libraryMeta.update(item.kind, item.id) { it.copy(trashedAt = System.currentTimeMillis()) }
    }

    fun deleteForever(item: LibraryItem) {
        container.libraryMeta.update(item.kind, item.id) { it.copy(trashedAt = 0L) }
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

    /** Finds the ruled tables on every page of a scan, reads their text, and shares one Excel file with a sheet per table page. */
    fun scanToExcel(context: Context, scanId: String) {
        viewModelScope.launch {
            val pages = container.repository.pages(scanId)
            val name = items.value.firstOrNull { it.id == scanId && it.kind == DocKind.SCAN }?.title ?: "Scan"
            if (pages.isEmpty()) { _message.value = "That scan has no pages."; return@launch }
            val sheets = ArrayList<Pair<String, List<List<String>>>>()
            runCatching {
                pages.forEachIndexed { i, p ->
                    _busy.value = "Reading tables on page ${i + 1} of ${pages.size}…"
                    val bitmap = container.renderer.render(p, 2000)
                    val cells = withContext(Dispatchers.Default) {
                        val mat = bitmap.toRgbMat()
                        try { com.docscan.app.imaging.TableDetector.detectCells(mat) } finally { mat.release() }
                    }
                    if (cells.isNotEmpty()) {
                        val rows = com.docscan.app.core.TableGrid.group(cells).map { row -> row.map { readCell(bitmap, it) } }
                        sheets += "Page ${i + 1}" to com.docscan.app.core.TableGrid.pad(rows)
                    }
                    bitmap.recycle()
                }
            }.onSuccess {
                if (sheets.isEmpty()) {
                    _busy.value = null
                    _message.value = "No tables with ruled lines were found. Try a flatter, brighter scan."
                } else {
                    val file = container.exporter.workbookXlsx(sheets, "$name tables")
                    _busy.value = null
                    ShareHelper.share(context, listOf(file), Mime.XLSX)
                }
            }.onFailure {
                _busy.value = null
                _message.value = "Could not read the tables."
            }
        }
    }

    private suspend fun readCell(bitmap: android.graphics.Bitmap, cell: com.docscan.app.core.CellRect): String {
        val inset = 3
        val x = (cell.x + inset).coerceIn(0, bitmap.width - 1)
        val y = (cell.y + inset).coerceIn(0, bitmap.height - 1)
        val w = (cell.w - 2 * inset).coerceAtMost(bitmap.width - x)
        val h = (cell.h - 2 * inset).coerceAtMost(bitmap.height - y)
        if (w < 4 || h < 4) return ""
        var crop = android.graphics.Bitmap.createBitmap(bitmap, x, y, w, h)
        if (h < 48) crop = android.graphics.Bitmap.createScaledBitmap(crop, w * 2, h * 2, true)
        return container.ocr.cellText(crop)
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
