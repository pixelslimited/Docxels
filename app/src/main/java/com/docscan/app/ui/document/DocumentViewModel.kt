package com.docscan.app.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.data.DocumentEntity
import com.docscan.app.data.PageEntity
import com.docscan.app.data.settings
import com.docscan.app.export.ExportOptions
import com.docscan.app.imaging.DocMode
import com.docscan.app.ui.UiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocumentViewModel(private val container: AppContainer, private val docId: String) : ViewModel() {
    private val repo = container.repository

    val document: StateFlow<DocumentEntity?> = repo.observeDocument(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pages: StateFlow<List<PageEntity>> = repo.observePages(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busyLabel = MutableStateFlow<String?>(null)
    /** Non-null while a long operation runs; the text says what is happening. */
    val busyLabel: StateFlow<String?> = _busyLabel

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    fun rename(name: String) {
        viewModelScope.launch { repo.renameDocument(docId, name) }
    }

    /** Moves a page one place earlier (-1) or later (+1). */
    fun move(pageId: String, delta: Int) {
        viewModelScope.launch {
            val ids = repo.pages(docId).map { it.id }.toMutableList()
            val from = ids.indexOf(pageId)
            val to = from + delta
            if (from < 0 || to !in ids.indices) return@launch
            ids.add(to, ids.removeAt(from))
            repo.reorder(docId, ids)
        }
    }

    fun rotate(page: PageEntity) {
        viewModelScope.launch { repo.rotate(page, 90) }
    }

    fun delete(page: PageEntity) {
        viewModelScope.launch { repo.deletePage(page) }
    }

    fun applyModeToAll(mode: DocMode) {
        viewModelScope.launch {
            repo.pages(docId).forEach { page -> repo.updateSettings(page, page.settings().copy(mode = mode)) }
        }
    }

    /** Straightens page orientation and, when page numbers are printed, puts the pages in order. */
    fun autoArrange() {
        viewModelScope.launch {
            val current = repo.pages(docId)
            if (current.size < 1) return@launch
            _busyLabel.value = "Arranging pages…"
            runCatching { container.arranger.arrange(current) }
                .onSuccess { result ->
                    result.rotationDeltas.forEach { (id, delta) ->
                        current.firstOrNull { it.id == id }?.let { repo.rotate(it, delta) }
                    }
                    if (result.reordered) repo.reorder(docId, result.orderedIds)
                    val rotated = result.rotationDeltas.size
                    events.send(
                        UiEvent.Message(
                            when {
                                result.reordered && rotated > 0 -> "Reordered pages and fixed $rotated rotated page(s)."
                                result.reordered -> "Pages reordered by their page numbers."
                                rotated > 0 -> "Fixed orientation of $rotated page(s)."
                                else -> "Nothing to change. Pages look upright; no usable page numbers were found."
                            },
                        ),
                    )
                }
                .onFailure { events.send(UiEvent.Message("Auto-arrange failed. You can still reorder pages with the arrows.")) }
            _busyLabel.value = null
        }
    }

    /** OCRs every page into an editable Word document and opens it in the editor. */
    fun convertToWord(onDone: (String) -> Unit) {
        viewModelScope.launch {
            val current = repo.pages(docId)
            val name = document.value?.name ?: "Scan"
            runCatching {
                container.scanToWord.convert(current, name) { n, total -> _busyLabel.value = "Reading page $n of $total…" }
            }.onSuccess { doc ->
                container.pendingReview = com.docscan.app.ui.write.ScanReview(doc, current)
                _busyLabel.value = null
                onDone(com.docscan.app.ui.write.REVIEW_ID)
            }.onFailure {
                _busyLabel.value = null
                events.send(UiEvent.Message("Could not convert to a Word document."))
            }
        }
    }

    fun export(options: ExportOptions, save: Boolean, saveAs: Boolean = false) {
        viewModelScope.launch {
            val current = repo.pages(docId)
            val name = document.value?.name ?: "Scan"
            _busyLabel.value = "Preparing ${options.format.label}…"
            runCatching { container.exporter.exportDocument(current, name, options) }
                .onSuccess { result ->
                    if (saveAs) {
                        events.send(UiEvent.SaveAs(result.files))
                    } else if (save) {
                        val saved = container.exporter.saveToDevice(result.files, result.mime)
                        val where = if (result.mime.startsWith("image/")) "Pictures/DocScan" else "Downloads/DocScan"
                        events.send(
                            UiEvent.Message(
                                if (saved == result.files.size) "Saved to $where" else "Saved $saved of ${result.files.size} file(s).",
                            ),
                        )
                    } else {
                        events.send(UiEvent.Share(result.files, result.mime))
                    }
                }
                .onFailure { events.send(UiEvent.Message("Export failed: ${it.message ?: "unknown error"}")) }
            _busyLabel.value = null
        }
    }
}
