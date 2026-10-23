package com.docscan.app.ui.edit

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.AppContainer
import com.docscan.app.core.CellRect
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import com.docscan.app.core.TableGrid
import com.docscan.app.data.ImageIO
import com.docscan.app.data.PageEntity
import com.docscan.app.export.Mime
import com.docscan.app.imaging.EdgeDetector
import com.docscan.app.imaging.EnhanceSettings
import com.docscan.app.imaging.HandRemover
import com.docscan.app.imaging.TableDetector
import com.docscan.app.imaging.toBitmap
import com.docscan.app.imaging.toRgbMat
import com.docscan.app.ui.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import java.io.File

enum class TableExport(val label: String) {
    EXCEL("Excel"),
    WORD("Word"),
    PDF("PDF"),
    IMAGE("Image"),
}

/** Result of table detection on the current rendering of the page. */
data class TableState(
    val bitmap: Bitmap,
    val cells: List<CellRect>,
    /** Recognised text per row/column; empty until the user taps "Read text". */
    val rows: List<List<String>> = emptyList(),
)

internal sealed interface CleanOutcome {
    data class Done(val bitmap: Bitmap) : CleanOutcome
    data object NothingFound : CleanOutcome
    data object Failed : CleanOutcome
}

class EditViewModel(
    private val container: AppContainer,
    private val docId: String,
    private val pageId: String,
) : ViewModel() {
    private val repo = container.repository

    val page: StateFlow<PageEntity?> = repo.observePage(pageId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _source = MutableStateFlow<Bitmap?>(null)
    /** The original photo (uncropped), used by the crop editor. */
    val source: StateFlow<Bitmap?> = _source

    private val _preview = MutableStateFlow<Bitmap?>(null)
    /** The page as it will be exported: cropped, straightened, cleaned and filtered. */
    val preview: StateFlow<Bitmap?> = _preview

    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel

    private val _table = MutableStateFlow<TableState?>(null)
    val table: StateFlow<TableState?> = _table

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val p = repo.page(pageId) ?: return@launch
            _source.value = withContext(Dispatchers.IO) {
                ImageIO.decodeFile(container.fileStore.file(docId, p.sourcePath), SOURCE_SIDE)
            }
        }
        viewModelScope.launch {
            page.filterNotNull().collectLatest { p ->
                runCatching { container.renderer.render(p, PREVIEW_SIDE, _source.value) }
                    .onSuccess { _preview.value = it }
            }
        }
    }

    // ---- Crop ---------------------------------------------------------------------------------

    fun setCorners(corners: List<Pt>) {
        val p = page.value ?: return
        _table.value = null
        viewModelScope.launch { repo.updateCorners(p, corners) }
    }

    fun useFullImage() = setCorners(Geometry.FullImage)

    fun autoDetectEdges() {
        val source = _source.value ?: return
        viewModelScope.launch {
            _busyLabel.value = "Finding page edges…"
            val corners = withContext(Dispatchers.Default) { EdgeDetector.detect(source) }
            _busyLabel.value = null
            if (corners == null) {
                events.send(UiEvent.Message("Could not find the page edges. Drag the corners or sides by hand."))
            } else {
                setCorners(corners)
            }
        }
    }

    fun rotate() {
        val p = page.value ?: return
        _table.value = null
        viewModelScope.launch { repo.rotate(p, 90) }
    }

    // ---- Look ---------------------------------------------------------------------------------

    fun setSettings(settings: EnhanceSettings) {
        val p = page.value ?: return
        _table.value = null
        viewModelScope.launch { repo.updateSettings(p, settings) }
    }

    fun applyLookToAll(settings: EnhanceSettings) {
        viewModelScope.launch {
            repo.pages(docId).forEach { repo.updateSettings(it, settings) }
            events.send(UiEvent.Message("Look applied to every page."))
        }
    }

    // ---- Hand removal -------------------------------------------------------------------------

    fun autoRemoveHand() {
        val p = page.value ?: return
        viewModelScope.launch {
            _busyLabel.value = "Cleaning the page…"
            val outcome = clean(p, repair = HandRemover::fillPaper) { base ->
                val detection = HandRemover.detectJunkMask(base)
                if (detection.coverage < MIN_HAND_COVERAGE) {
                    detection.mask.release()
                    null
                } else {
                    detection.mask
                }
            }
            finishClean(
                p, outcome,
                "Cleaned: hands, objects, staples and folded edges removed.",
                "Nothing found to remove. Paint over it with the brush instead.",
            )
        }
    }

    /** [strokes] are normalised over the preview; [radiusFraction] is brush radius relative to its longer side. */
    fun removePainted(strokes: List<List<Pt>>, radiusFraction: Float) {
        val p = page.value ?: return
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            _busyLabel.value = "Removing…"
            // The preview shows the page after rotation, the mask is applied before it: undo the rotation.
            // The finished page may also be mirrored (applied last), so undo that first.
            val unrotated = strokes.map { stroke -> stroke.map { pt -> Geometry.unrotate(if (p.mirror) Pt(1f - pt.x, pt.y) else pt, p.rotation) } }
            val outcome = clean(p) { base -> HandRemover.maskFromStrokes(base.cols(), base.rows(), unrotated, radiusFraction) }
            finishClean(p, outcome, "Removed.", "Nothing to remove.")
        }
    }

    fun restoreOriginalPage() {
        val p = page.value ?: return
        viewModelScope.launch { repo.setCleaned(p, null) }
    }

    private suspend fun clean(
        p: PageEntity,
        repair: (Mat, Mat) -> Mat = HandRemover::inpaint,
        makeMask: (Mat) -> Mat?,
    ): CleanOutcome =
        withContext(Dispatchers.Default) {
            val base = container.renderer.warpedBase(p, CLEAN_SIDE) ?: return@withContext CleanOutcome.Failed
            try {
                val mask = makeMask(base) ?: return@withContext CleanOutcome.NothingFound
                try {
                    val repaired = repair(base, mask)
                    try {
                        CleanOutcome.Done(repaired.toBitmap())
                    } finally {
                        repaired.release()
                    }
                } finally {
                    mask.release()
                }
            } catch (e: Exception) {
                CleanOutcome.Failed
            } finally {
                base.release()
            }
        }

    private suspend fun finishClean(p: PageEntity, outcome: CleanOutcome, done: String, nothing: String) {
        when (outcome) {
            is CleanOutcome.Done -> {
                repo.setCleaned(p, outcome.bitmap)
                _table.value = null
                events.send(UiEvent.Message(done))
            }
            CleanOutcome.NothingFound -> events.send(UiEvent.Message(nothing))
            CleanOutcome.Failed -> events.send(UiEvent.Message("Could not process this page."))
        }
        _busyLabel.value = null
    }

    // ---- Tables -------------------------------------------------------------------------------

    fun detectTable() {
        val p = page.value ?: return
        viewModelScope.launch {
            _busyLabel.value = "Looking for a table…"
            runCatching {
                withContext(Dispatchers.Default) {
                    val bitmap = container.renderer.render(p, TABLE_SIDE)
                    val mat = bitmap.toRgbMat()
                    val cells = TableDetector.detectCells(mat)
                    mat.release()
                    TableState(bitmap, cells)
                }
            }.onSuccess {
                if (it.cells.isEmpty()) {
                    _table.value = null
                    events.send(UiEvent.Message("No table with ruled lines found on this page."))
                } else {
                    _table.value = it
                }
            }.onFailure { events.send(UiEvent.Message("Table detection failed.")) }
            _busyLabel.value = null
        }
    }

    fun readTableText() {
        val t = _table.value ?: return
        viewModelScope.launch {
            _busyLabel.value = "Reading text…"
            runCatching {
                val grouped = TableGrid.group(t.cells)
                TableGrid.pad(grouped.map { row -> row.map { cell -> readCell(t.bitmap, cell) } })
            }.onSuccess { rows -> _table.value = t.copy(rows = rows) }
                .onFailure { events.send(UiEvent.Message("Could not read the table text.")) }
            _busyLabel.value = null
        }
    }

    fun editCell(row: Int, column: Int, text: String) {
        val t = _table.value ?: return
        _table.value = t.copy(
            rows = t.rows.mapIndexed { r, cells ->
                if (r != row) cells else cells.mapIndexed { c, old -> if (c == column) text else old }
            },
        )
    }

    private suspend fun readCell(bitmap: Bitmap, cell: CellRect): String {
        val inset = 3 // skip the ruling lines themselves
        val x = (cell.x + inset).coerceIn(0, bitmap.width - 1)
        val y = (cell.y + inset).coerceIn(0, bitmap.height - 1)
        val w = (cell.w - 2 * inset).coerceAtMost(bitmap.width - x)
        val h = (cell.h - 2 * inset).coerceAtMost(bitmap.height - y)
        if (w < 4 || h < 4) return ""
        var crop = Bitmap.createBitmap(bitmap, x, y, w, h)
        if (h < 48) crop = Bitmap.createScaledBitmap(crop, w * 2, h * 2, true)
        return container.ocr.cellText(crop)
    }

    fun exportTable(kind: TableExport, save: Boolean, saveAs: Boolean = false) {
        val t = _table.value ?: return
        if ((kind == TableExport.EXCEL || kind == TableExport.WORD) && t.rows.isEmpty()) {
            viewModelScope.launch { events.send(UiEvent.Message("Tap \"Read text\" first so the table has content.")) }
            return
        }
        viewModelScope.launch {
            _busyLabel.value = "Preparing ${kind.label}…"
            val name = (repo.document(docId)?.name ?: "Scan") + " table"
            runCatching {
                val exporter = container.exporter
                when (kind) {
                    TableExport.EXCEL -> listOf(exporter.tableXlsx(t.rows, name)) to Mime.XLSX
                    TableExport.WORD -> listOf(exporter.tableDocx(t.rows, name)) to Mime.DOCX
                    TableExport.PDF -> listOf(exporter.tablePdf(cropToTable(t), name)) to Mime.PDF
                    TableExport.IMAGE -> listOf(exporter.tableImage(cropToTable(t), name, png = false)) to Mime.JPEG
                }
            }.onSuccess { (files, mime) -> if (saveAs) events.send(UiEvent.SaveAs(files)) else deliver(files, mime, save) }
                .onFailure { events.send(UiEvent.Message("Export failed: ${it.message ?: "unknown error"}")) }
            _busyLabel.value = null
        }
    }

    private suspend fun deliver(files: List<File>, mime: String, save: Boolean) {
        if (save) {
            val saved = container.exporter.saveToDevice(files, mime)
            val where = if (mime.startsWith("image/")) "Pictures/DocScan" else "Downloads/DocScan"
            events.send(UiEvent.Message(if (saved == files.size) "Saved to $where" else "Could not save the file."))
        } else {
            events.send(UiEvent.Share(files, mime))
        }
    }

    private fun cropToTable(t: TableState): Bitmap {
        val bounds = TableGrid.bounds(t.cells) ?: return t.bitmap
        val margin = 12
        val x = (bounds.x - margin).coerceAtLeast(0)
        val y = (bounds.y - margin).coerceAtLeast(0)
        val w = (bounds.w + 2 * margin).coerceAtMost(t.bitmap.width - x)
        val h = (bounds.h + 2 * margin).coerceAtMost(t.bitmap.height - y)
        return Bitmap.createBitmap(t.bitmap, x, y, w, h)
    }

    private companion object {
        const val SOURCE_SIDE = 1800
        const val PREVIEW_SIDE = 1600
        const val CLEAN_SIDE = 2400
        const val TABLE_SIDE = 2000
        const val MIN_HAND_COVERAGE = 0.0005
    }
}
