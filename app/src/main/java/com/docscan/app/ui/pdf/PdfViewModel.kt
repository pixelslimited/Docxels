package com.docscan.app.ui.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.export.Mime
import com.docscan.app.export.ShareHelper
import com.docscan.app.pdf.PageRanges
import com.docscan.app.pdf.PdfPasswordException
import com.docscan.app.pdf.PdfTools
import com.docscan.app.pdf.copyStreamToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PdfSource(val file: File, val name: String, val pages: Int)

/** A PDF that needs a password before it can be used. */
class LockedPdf(val file: File, val name: String)

private const val MAX_PDF_BYTES = 150L * 1024 * 1024

class PdfViewModel(private val container: AppContainer, appContext: Context) : ViewModel() {
    private val context = appContext.applicationContext
    private val tools = PdfTools(context)
    private val work = File(context.cacheDir, "pdfwork").apply { mkdirs() }

    private val _sources = MutableStateFlow<List<PdfSource>>(emptyList())
    val sources: StateFlow<List<PdfSource>> = _sources
    private val _results = MutableStateFlow<List<File>>(emptyList())
    val results: StateFlow<List<File>> = _results
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _locked = MutableStateFlow<LockedPdf?>(null)
    val locked: StateFlow<LockedPdf?> = _locked

    fun messageShown() { _message.value = null }
    fun say(text: String) { _message.value = text }

    private fun displayName(uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Document.pdf"
        return raw.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Document" }
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }

    /** Copies the chosen PDFs into private storage. [replace] swaps the list instead of adding to it. */
    fun addUris(uris: List<Uri>, replace: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = "Opening…"
            if (replace) _sources.value = emptyList()
            for (uri in uris) {
                val copy = File(work, UUID.randomUUID().toString() + ".pdf")
                val name = displayName(uri)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)!!.use { copyStreamToFile(it, copy, MAX_PDF_BYTES) }
                    }.getOrDefault(false)
                }
                if (!ok) {
                    copy.delete()
                    _message.value = "“$name” could not be opened (or is larger than 150 MB)."
                    continue
                }
                accept(copy, name, null)
            }
            _busy.value = null
        }
    }

    private suspend fun accept(copy: File, name: String, password: String?) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                if (password == null && tools.isEncrypted(copy)) return@runCatching -1
                if (password != null) {
                    val plain = File(work, UUID.randomUUID().toString() + ".pdf")
                    tools.unprotect(copy, password, plain)
                    copy.delete()
                    plain.renameTo(copy)
                }
                tools.pageCount(copy)
            }
        }
        val failure = outcome.exceptionOrNull()
        when {
            failure is PdfPasswordException -> {
                _message.value = "That password is not right."
                _locked.value = LockedPdf(copy, name)
            }
            failure != null -> {
                copy.delete()
                _message.value = "“$name” is not a PDF Docxels can read."
            }
            outcome.getOrNull() == -1 -> _locked.value = LockedPdf(copy, name)
            else -> _sources.value = _sources.value + PdfSource(copy, name, outcome.getOrThrow())
        }
    }

    fun unlock(password: String) {
        val l = _locked.value ?: return
        _locked.value = null
        viewModelScope.launch {
            _busy.value = "Unlocking…"
            accept(l.file, l.name, password)
            _busy.value = null
        }
    }

    fun cancelUnlock() {
        _locked.value?.file?.delete()
        _locked.value = null
    }

    fun removeSource(index: Int) {
        _sources.value = _sources.value.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    }

    fun moveSource(index: Int, delta: Int) {
        val list = _sources.value.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        _sources.value = list
    }

    fun clearResults() { _results.value = emptyList() }

    private fun outFile(name: String) = File(File(context.cacheDir, "exports").apply { mkdirs() }, safe(name) + ".pdf")

    private fun run(label: String, failure: String, block: () -> List<File>) {
        viewModelScope.launch {
            _busy.value = label
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            result.onSuccess { _results.value = it }.onFailure {
                _message.value = (it as? IllegalArgumentException)?.message ?: failure
            }
            _busy.value = null
        }
    }

    fun merge() {
        val list = _sources.value
        run("Merging…", "Could not merge these PDFs.") {
            val out = outFile("Merged")
            tools.merge(list.map { it.file }, out)
            listOf(out)
        }
    }

    /** Pages is a text range like "1-3, 5". */
    fun extract(rangeText: String) {
        val src = _sources.value.firstOrNull() ?: return
        val pages = PageRanges.parse(rangeText, src.pages)
        if (pages == null) { say("Enter pages like 1-3, 5 (this PDF has ${src.pages})."); return }
        run("Extracting…", "Could not extract those pages.") {
            val out = outFile(src.name + "-pages")
            tools.pick(src.file, pages, out)
            listOf(out)
        }
    }

    fun splitEvery(size: Int) {
        val src = _sources.value.firstOrNull() ?: return
        if (size < 1) { say("Enter a number of pages of 1 or more."); return }
        run("Splitting…", "Could not split this PDF.") {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            tools.splitEvery(src.file, size, dir, safe(src.name))
        }
    }

    fun rotate(rangeText: String, degrees: Int) {
        val src = _sources.value.firstOrNull() ?: return
        val pages = if (rangeText.isBlank()) (0 until src.pages).toList() else PageRanges.parse(rangeText, src.pages)
        if (pages == null) { say("Enter pages like 1-3, 5 (this PDF has ${src.pages})."); return }
        run("Rotating…", "Could not rotate this PDF.") {
            val out = outFile(src.name + "-rotated")
            tools.rotate(src.file, pages, degrees, out)
            listOf(out)
        }
    }

    /** [order] lists the zero-based pages to keep, in the new order. */
    fun organize(order: List<Int>) {
        val src = _sources.value.firstOrNull() ?: return
        if (order.isEmpty()) { say("Keep at least one page."); return }
        run("Saving…", "Could not save this PDF.") {
            val out = outFile(src.name + "-organized")
            tools.pick(src.file, order, out)
            listOf(out)
        }
    }

    fun protect(password: String) {
        val src = _sources.value.firstOrNull() ?: return
        run("Encrypting…", "Could not protect this PDF.") {
            val out = outFile(src.name + "-protected")
            tools.protect(src.file, password, out)
            listOf(out)
        }
    }

    fun stamp(marks: List<com.docscan.app.pdf.PdfMark>) {
        val src = _sources.value.firstOrNull() ?: return
        if (marks.isEmpty()) { say("Tap the page to place something first."); return }
        run("Saving…", "Could not add these to the PDF.") {
            val out = outFile(src.name + "-signed")
            tools.stamp(src.file, marks, out)
            listOf(out)
        }
    }

    fun compress(level: PdfTools.Compression) {
        val src = _sources.value.firstOrNull() ?: return
        viewModelScope.launch {
            _busy.value = "Compressing…"
            val before = src.file.length()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val out = outFile(src.name + "-compressed")
                    val n = tools.compress(src.file, level, out)
                    Triple(out, n, out.length())
                }
            }
            outcome.onSuccess { (out, n, after) ->
                if (n == 0 || after >= before) {
                    out.delete()
                    _message.value = "This PDF has no pictures that can be made smaller."
                } else {
                    _results.value = listOf(out)
                    val pct = ((before - after) * 100 / before).toInt()
                    _message.value = "Smaller by $pct% (${before / 1024} KB → ${after / 1024} KB)."
                }
            }.onFailure { _message.value = "Could not compress this PDF." }
            _busy.value = null
        }
    }

    fun share(files: List<File>) = ShareHelper.share(context, files, Mime.PDF)

    fun saveToPhone(files: List<File>) {
        viewModelScope.launch {
            val n = container.exporter.saveToDevice(files, Mime.PDF)
            _message.value = if (n > 0) "Saved to Downloads/DocScan." else "Could not save."
        }
    }

    override fun onCleared() {
        work.listFiles()?.forEach { com.docscan.app.security.SecureFiles.wipe(it) }
    }
}
