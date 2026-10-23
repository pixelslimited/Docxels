package com.docscan.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.Geometry
import com.docscan.app.data.ImageIO
import com.docscan.app.data.PageEntity
import com.docscan.app.imaging.EdgeDetector
import com.docscan.app.imaging.HandRemover
import com.docscan.app.imaging.SpreadSplitter
import com.docscan.app.imaging.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ScanMode(val label: String) {
    /** One sheet of paper. */
    DOCUMENT("Document"),

    /** One page of a book: curved text lines are flattened when the page is rendered. */
    BOOK("Book"),

    /** Both open pages of a book in one shot, cut into two pages at the spine. */
    SPREAD("2 pages"),
}

data class CaptureOptions(val mode: ScanMode = ScanMode.DOCUMENT, val removeFingers: Boolean = false)

class ScanViewModel(private val container: AppContainer, val docId: String) : ViewModel() {
    private val repo = container.repository

    val pageCount: StateFlow<Int> = repo.observePages(docId)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The most recently added page, shown as the thumbnail in the corner of the camera. */
    val lastPage: StateFlow<PageEntity?> = repo.observePages(docId)
        .map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Photos still being processed. The camera stays usable meanwhile so scanning is not interrupted. */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** One photo is processed at a time, in the order taken, so pages are always added in order. */
    private val queue = Mutex()

    /** A freshly captured photo: normalise it, find the page edges and add it to the document. */
    fun addCapture(file: File, options: CaptureOptions) {
        _pending.update { it + 1 }
        viewModelScope.launch {
            queue.withLock {
                runCatching {
                    val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeFile(file, MAX_SIDE) } ?: error("Unreadable photo")
                    addBitmap(bitmap, options)
                }.onFailure { _message.value = "Could not save the photo. Please try again." }
            }
            file.delete()
            _pending.update { it - 1 }
        }
    }

    fun addFromGallery(context: Context, uris: List<Uri>, options: CaptureOptions) {
        if (uris.isEmpty()) return
        _pending.update { it + 1 }
        viewModelScope.launch {
            var failed = 0
            queue.withLock {
                for (uri in uris) {
                    runCatching {
                        val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(context, uri, MAX_SIDE) } ?: error("Unreadable image")
                        addBitmap(bitmap, options)
                    }.onFailure { failed++ }
                }
            }
            if (failed > 0) _message.value = "$failed image(s) could not be imported."
            _pending.update { it - 1 }
        }
    }

    private suspend fun addBitmap(bitmap: Bitmap, options: CaptureOptions) {
        val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
        if (options.mode == ScanMode.SPREAD) {
            val halves = withContext(Dispatchers.Default) { runCatching { SpreadSplitter.split(bitmap, corners) }.getOrNull() }
            if (halves != null) {
                bitmap.recycle()
                for (half in halves) {
                    val page = repo.addPage(docId, half, Geometry.FullImage)
                    half.recycle()
                    if (options.removeFingers) autoClean(page)
                    autoRotate(page.id)
                }
                return
            }
            _message.value = "Turn the phone sideways so both pages fit. Saved as one page this time."
        }
        val page = repo.addPage(docId, bitmap, corners)
        bitmap.recycle()
        if (options.removeFingers) autoClean(page)
        autoRotate(page.id)
    }

    /** Turns a freshly added page upright by reading its text. Works from the stored page so the clean-up result is kept. */
    private suspend fun autoRotate(pageId: String) {
        withContext(Dispatchers.Default) {
            runCatching {
                val fresh = repo.page(pageId) ?: return@runCatching
                val turn = container.arranger.orientationOf(fresh)
                if (turn != 0) repo.rotate(fresh, turn)
            }
        }
    }

    /** Removes fingers, objects, staples and folded corners from a freshly added page (if any are found). */
    private suspend fun autoClean(page: PageEntity) {
        withContext(Dispatchers.Default) {
            runCatching {
                val base = container.renderer.warpedBase(page, CLEAN_SIDE) ?: return@runCatching
                try {
                    val detection = HandRemover.detectJunkMask(base)
                    try {
                        if (detection.coverage >= MIN_CLEAN_COVERAGE) {
                            val repaired = HandRemover.fillPaper(base, detection.mask)
                            try {
                                repo.setCleaned(page, repaired.toBitmap())
                            } finally {
                                repaired.release()
                            }
                        }
                    } finally {
                        detection.mask.release()
                    }
                } finally {
                    base.release()
                }
            }
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val MAX_SIDE = 3000
        const val CLEAN_SIDE = 2400
        const val MIN_CLEAN_COVERAGE = 0.0005
    }
}
