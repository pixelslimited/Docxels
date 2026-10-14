package com.docscan.app.ocr

import android.graphics.Bitmap
import com.docscan.app.core.CellRect
import com.docscan.app.core.TableGrid
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.OcrBlockIn
import com.docscan.app.core.word.OcrLayout
import com.docscan.app.core.word.OcrLine
import com.docscan.app.core.word.OcrTable
import com.docscan.app.core.word.PageSetup
import com.docscan.app.core.word.WordDoc
import com.docscan.app.data.PageEntity
import com.docscan.app.imaging.PageRenderer
import com.docscan.app.imaging.TableDetector
import com.docscan.app.imaging.toRgbMat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Converts scanned pages into an editable Word document: text blocks become headings, paragraphs and lists, and
 * ruled tables become real tables. Everything runs on the phone.
 */
class ScanToWord(private val renderer: PageRenderer, private val ocr: OcrEngine) {
    suspend fun convert(pages: List<PageEntity>, title: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): WordDoc {
        val page = PageSetup()
        val blocks = ArrayList<Block>()
        pages.forEachIndexed { index, entity ->
            onProgress(index + 1, pages.size)
            val bitmap = withContext(Dispatchers.Default) { renderer.render(entity, SIDE) }
            try {
                val text = ocr.recognize(bitmap)
                val tables = readTables(bitmap)
                val input = text.textBlocks.map { b ->
                    OcrBlockIn(
                        b.lines.mapNotNull { l ->
                            val r = l.boundingBox ?: return@mapNotNull null
                            OcrLine(l.text, r.left, r.top, r.right, r.bottom)
                        },
                    )
                }.filter { it.lines.isNotEmpty() }
                val pageBlocks = OcrLayout.page(input, tables, bitmap.width, page.contentWidthTwips)
                if (index > 0) blocks += Block.PageBreak
                if (pageBlocks.isEmpty()) {
                    blocks += Block.Para(com.docscan.app.core.word.Paragraph(listOf(com.docscan.app.core.word.Run("[No text recognised on page ${index + 1}]"))))
                } else {
                    blocks += pageBlocks
                }
            } finally {
                bitmap.recycle()
            }
        }
        return WordDoc(title = title, blocks = blocks.ifEmpty { listOf(Block.Para(com.docscan.app.core.word.Paragraph())) }, page = page)
    }

    private suspend fun readTables(bitmap: Bitmap): List<OcrTable> {
        val cells: List<CellRect> = withContext(Dispatchers.Default) {
            val mat = bitmap.toRgbMat()
            try { TableDetector.detectCells(mat) } finally { mat.release() }
        }
        if (cells.isEmpty()) return emptyList()
        val rows = TableGrid.group(cells)
        val bounds = TableGrid.bounds(cells) ?: return emptyList()
        val texts = TableGrid.pad(rows.map { row -> row.map { readCell(bitmap, it) } })
        val first = rows.maxByOrNull { it.size }
        val total = first?.sumOf { it.w }?.toFloat() ?: 0f
        val fractions = if (first != null && total > 0f) first.map { it.w / total } else null
        return listOf(OcrTable(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h, texts, fractions))
    }

    private suspend fun readCell(bitmap: Bitmap, cell: CellRect): String {
        val inset = 3
        val x = (cell.x + inset).coerceIn(0, bitmap.width - 1)
        val y = (cell.y + inset).coerceIn(0, bitmap.height - 1)
        val w = (cell.w - 2 * inset).coerceAtMost(bitmap.width - x)
        val h = (cell.h - 2 * inset).coerceAtMost(bitmap.height - y)
        if (w < 4 || h < 4) return ""
        var crop = Bitmap.createBitmap(bitmap, x, y, w, h)
        if (h < 48) crop = Bitmap.createScaledBitmap(crop, w * 2, h * 2, true)
        return ocr.cellText(crop)
    }

    companion object {
        const val SIDE = 2200
    }
}
