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

/** What the camera screen is doing with the photo just taken: a label and how far along it is (0 to 1). */
data class ScanProgress(val label: String, val fraction: Float)

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

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress

    private fun step(label: String, fraction: Float) { _progress.value = ScanProgress(label, fraction) }

    /** Clears the progress bar once nothing is left to do. */
    private fun settle() { if (_pending.value == 0) _progress.value = null }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** One photo is processed at a time, in the order taken, so pages are always added in order. */
    private val queue = Mutex()

    /**
     * Clean-up and straightening of a saved page run here, one page at a time, so the next photo can be read and
     * saved while the previous page is still being polished instead of waiting for it.
     */
    private val polishQueue = Mutex()

    /** A freshly captured photo: normalise it, find the page edges and add it to the document. */
    fun addCapture(file: File, options: CaptureOptions) {
        _pending.update { it + 1 }
        step("Reading the photo", 0.08f)
        viewModelScope.launch {
            var saved: List<PageEntity> = emptyList()
            queue.withLock {
                runCatching {
                    val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeFile(file, MAX_SIDE) } ?: error("Unreadable photo")
                    saved = addBitmap(bitmap, options)
                }.onFailure { _message.value = "Could not save the photo. Please try again." }
            }
            file.delete()
            polish(saved, options)
            _pending.update { it - 1 }
            settle()
        }
    }

    fun addFromGallery(context: Context, uris: List<Uri>, options: CaptureOptions) {
        if (uris.isEmpty()) return
        _pending.update { it + 1 }
        viewModelScope.launch {
            var failed = 0
            val saved = mutableListOf<PageEntity>()
            queue.withLock {
                for ((n, uri) in uris.withIndex()) {
                    step("Importing image ${n + 1} of ${uris.size}", 0.1f + 0.5f * n / uris.size)
                    runCatching {
                        val bitmap = withContext(Dispatchers.IO) { ImageIO.decodeUri(context, uri, MAX_SIDE) } ?: error("Unreadable image")
                        saved += addBitmap(bitmap, options)
                    }.onFailure { failed++ }
                }
            }
            if (failed > 0) _message.value = "$failed image(s) could not be imported."
            polish(saved, options)
            _pending.update { it - 1 }
            settle()
        }
    }

    /** Finds the page edges and stores the page(s). Returns what was stored, still to be polished by [polish]. */
    private suspend fun addBitmap(bitmap: Bitmap, options: CaptureOptions): List<PageEntity> {
        step("Finding the page edges", 0.3f)
        val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(bitmap) } ?: Geometry.DefaultCorners
        if (options.mode == ScanMode.SPREAD) {
            val halves = withContext(Dispatchers.Default) { runCatching { SpreadSplitter.split(bitmap, corners) }.getOrNull() }
            if (halves != null) {
                bitmap.recycle()
                step("Saving the pages", 0.55f)
                return halves.map { half -> repo.addPage(docId, half, Geometry.FullImage).also { half.recycle() } }
            }
            _message.value = "Turn the phone sideways so both pages fit. Saved as one page this time."
        }
        step("Saving the page", 0.55f)
        val page = repo.addPage(docId, bitmap, corners)
        bitmap.recycle()
        return listOf(page)
    }

    /** Removes fingers (if switched on) and turns each saved page upright, after the next photo has been freed to go. */
    private suspend fun polish(pages: List<PageEntity>, options: CaptureOptions) {
        if (pages.isEmpty()) return
        polishQueue.withLock {
            for (page in pages) {
                if (options.removeFingers) { step("Cleaning the page", 0.75f); autoClean(page) }
                step("Straightening the text", 0.9f)
                autoRotate(page.id)
            }
            step("Done", 1f)
        }
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

    /**
     * Removes fingers, objects, staples and folded corners from a freshly added page (if any are found).
     * Looking for them is done on a small copy of the page (the detector shrinks to this size anyway), so the
     * large straighten-and-flatten pass only runs for the pages that really have something to remove.
     */
    private suspend fun autoClean(page: PageEntity) {
        withContext(Dispatchers.Default) {
            runCatching {
                val detection = container.renderer.warpedBase(page, DETECT_SIDE)?.let { small ->
                    try { HandRemover.detectJunkMask(small) } finally { small.release() }
                } ?: return@runCatching
                try {
                    // Very little is nothing to clean; a third or more means this is probably not a sheet of paper (a scene, an object), and repainting it as paper would wipe it out.
                    if (detection.coverage < MIN_CLEAN_COVERAGE || detection.coverage > MAX_CLEAN_COVERAGE) return@runCatching
                    val base = container.renderer.warpedBase(page, CLEAN_SIDE) ?: return@runCatching
                    try {
                        val fullMask = org.opencv.core.Mat()
                        org.opencv.imgproc.Imgproc.resize(detection.mask, fullMask, base.size(), 0.0, 0.0, org.opencv.imgproc.Imgproc.INTER_NEAREST)
                        try {
                            val repaired = HandRemover.fillPaper(base, fullMask)
                            try {
                                // Foreign things come in from the edges, so nothing in the middle of the page is repainted. As a last
                                // check, if writing was still lost the page is kept as photographed.
                                val confined = HandRemover.confineToEdges(base, repaired)
                                try {
                                    if (HandRemover.inkLost(base, confined) > MAX_INK_LOST) return@runCatching
                                    repo.setCleaned(page, confined.toBitmap())
                                } finally {
                                    confined.release()
                                }
                            } finally {
                                repaired.release()
                            }
                        } finally {
                            fullMask.release()
                        }
                    } finally {
                        base.release()
                    }
                } finally {
                    detection.mask.release()
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
        const val DETECT_SIDE = 900
        const val MIN_CLEAN_COVERAGE = 0.0005
        const val MAX_CLEAN_COVERAGE = 0.33
        const val MAX_INK_LOST = 0.03
    }
}
