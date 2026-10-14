package com.docscan.app.pdf

import android.content.Context
import com.docscan.app.core.TableGrid
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.Glyph
import com.docscan.app.core.word.OcrLayout
import com.docscan.app.core.word.OcrTable
import com.docscan.app.core.word.PageSetup
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.PdfLayout
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.TextSeg
import com.docscan.app.core.word.WordDoc
import com.docscan.app.imaging.TableDetector
import com.docscan.app.imaging.toRgbMat
import com.docscan.app.ocr.ScanToWord
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The converted document plus plain-language notes about anything that could not be carried over. */
class PdfConversion(val doc: WordDoc, val notes: List<String>)

/**
 * PDF to editable Word, entirely on the phone. Pages that carry real text are rebuilt from that text (exact, no
 * recognition mistakes); scanned pages with no text are read with OCR. Ruled tables become Word tables.
 */
class PdfToWord(context: Context, private val scan: ScanToWord) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }

    suspend fun convert(file: File, title: String, onProgress: (Int, Int) -> Unit): PdfConversion {
        val doc = withContext(Dispatchers.IO) {
            try { PDDocument.load(file, "") } catch (e: InvalidPasswordException) { throw PdfPasswordException() }
        }
        doc.use {
            val total = doc.numberOfPages
            require(total > 0) { "That PDF has no pages." }
            val setup = pageSetupOf(doc)
            val blocks = ArrayList<Block>()
            var ocrPages = 0
            var pictures = 0
            PdfPreview(file).use { preview ->
                for (i in 0 until total) {
                    onProgress(i + 1, total)
                    val bitmap = preview.render(i, ScanToWord.SIDE)
                    try {
                        val pdfPage = doc.getPage(i)
                        val dims = pageDims(pdfPage)
                        val scale = bitmap.width / dims.first
                        val segs = withContext(Dispatchers.Default) { extract(doc, i, scale) }
                        val chars = segs.sumOf { it.text.length }
                        val pageBlocks: List<Block> = if (chars >= MIN_TEXT_CHARS) {
                            if (hasPictures(pdfPage)) pictures++
                            textPage(bitmap, segs, setup)
                        } else {
                            ocrPages++
                            scan.pageBlocks(bitmap, setup)
                        }
                        if (i > 0) blocks += Block.PageBreak
                        blocks += pageBlocks.ifEmpty { listOf(Block.Para(Paragraph(listOf(Run("[Nothing readable on page ${i + 1}]"))))) }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            val notes = ArrayList<String>()
            if (ocrPages > 0) notes += "$ocrPages page(s) had no text layer and were read with OCR, so check them for mistakes."
            if (pictures > 0) notes += "Pictures and drawings on $pictures page(s) were not carried over; only the text and tables were."
            val out = WordDoc(title = title, blocks = blocks.ifEmpty { listOf(Block.Para(Paragraph())) }, page = setup)
            return PdfConversion(out, notes)
        }
    }

    private fun pageDims(page: com.tom_roush.pdfbox.pdmodel.PDPage): Pair<Float, Float> {
        val box = page.cropBox ?: page.mediaBox
        val rot = ((page.rotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
    }

    private fun pageSetupOf(doc: PDDocument): PageSetup {
        val (w, h) = pageDims(doc.getPage(0))
        val wt = (w * 20).toInt().coerceIn(2000, 31000)
        val ht = (h * 20).toInt().coerceIn(2000, 31000)
        return PageSetup(widthTwips = minOf(wt, ht), heightTwips = maxOf(wt, ht), landscape = wt > ht)
    }

    private fun hasPictures(page: com.tom_roush.pdfbox.pdmodel.PDPage): Boolean = runCatching {
        val r = page.resources ?: return false
        r.xObjectNames.any { r.getXObject(it) is PDImageXObject }
    }.getOrDefault(false)

    /** Embedded text of one page as line segments in bitmap pixels. */
    private fun extract(doc: PDDocument, index: Int, scale: Float): List<TextSeg> {
        val out = ArrayList<TextSeg>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val glyphs = textPositions.map {
                    Glyph(it.unicode ?: "", it.xDirAdj * scale, it.yDirAdj * scale, it.widthDirAdj * scale, it.fontSizeInPt.coerceAtLeast(1f) * scale)
                }.filter { it.ch.isNotEmpty() }
                out += PdfLayout.segments(glyphs)
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(doc)
        return out
    }

    private suspend fun textPage(bitmap: android.graphics.Bitmap, segs: List<TextSeg>, setup: PageSetup): List<Block> {
        val cells = withContext(Dispatchers.Default) {
            val mat = bitmap.toRgbMat()
            try { TableDetector.detectCells(mat) } finally { mat.release() }
        }
        val tables = ArrayList<OcrTable>()
        if (cells.isNotEmpty()) {
            val rows = TableGrid.group(cells)
            val bounds = TableGrid.bounds(cells)
            if (bounds != null) {
                val texts = TableGrid.pad(rows.map { row -> row.map { PdfLayout.cellText(segs, it.x, it.y, it.w, it.h) } })
                val widest = rows.maxByOrNull { it.size }
                val total = widest?.sumOf { it.w }?.toFloat() ?: 0f
                val fractions = if (widest != null && total > 0f) widest.map { it.w / total } else null
                tables += OcrTable(bounds.x, bounds.y, bounds.x + bounds.w, bounds.y + bounds.h, texts, fractions)
            }
        }
        return OcrLayout.page(PdfLayout.blocks(segs), tables, bitmap.width, setup.contentWidthTwips)
    }

    companion object {
        /** A page with fewer characters than this is treated as a scan. */
        const val MIN_TEXT_CHARS = 25
    }
}
