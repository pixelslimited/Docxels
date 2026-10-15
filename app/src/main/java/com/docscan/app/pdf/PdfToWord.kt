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
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ImageBlock
import com.docscan.app.core.word.RunStyle
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
                            val pics = withContext(Dispatchers.Default) { pictureBlocks(doc, i, scale, bitmap.width, bitmap.height, setup) }
                            pictures += pics.size
                            textPage(bitmap, segs, setup, pics)
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
            if (pictures > 0) notes += "$pictures picture(s) were carried over. They sit in reading order between the text; text does not wrap around them."
            val out = WordDoc(title = title, blocks = blocks.ifEmpty { listOf(Block.Para(Paragraph())) }, page = setup)
            return PdfConversion(out, notes)
        }
    }

    private fun pageDims(page: com.tom_roush.pdfbox.pdmodel.PDPage): Pair<Float, Float> {
        val box = page.cropBox ?: page.mediaBox
        val rot = ((page.rotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
    }

    /** Converted documents are always A4; only the orientation follows the PDF's first page. */
    private fun pageSetupOf(doc: PDDocument): PageSetup {
        val (w, h) = pageDims(doc.getPage(0))
        return PageSetup(landscape = w > h)
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
                    Glyph(it.unicode ?: "", it.xDirAdj * scale, it.yDirAdj * scale, it.widthDirAdj * scale, it.fontSizeInPt.coerceAtLeast(1f) * scale, styleOf(it))
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

    /** Bold, italic, size and a matching font name from the font the PDF used for this character. */
    private fun styleOf(tp: TextPosition): RunStyle = runCatching {
        val f: PDFont = tp.font
        val raw = (f.name ?: "").substringAfter('+')
        val low = raw.lowercase()
        val d = f.fontDescriptor
        val bold = "bold" in low || "black" in low || "heavy" in low || d?.isForceBold == true || (d?.fontWeight ?: 0f) >= 700f
        val italic = "italic" in low || "oblique" in low || d?.isItalic == true
        val family = when {
            "times" in low -> "Times New Roman"
            "arial" in low || "helvetica" in low -> "Arial"
            "calibri" in low -> "Calibri"
            "cambria" in low -> "Cambria"
            "courier" in low -> "Courier New"
            "georgia" in low -> "Georgia"
            "verdana" in low -> "Verdana"
            "tahoma" in low -> "Tahoma"
            "garamond" in low -> "Garamond"
            "poppins" in low -> "Poppins"
            "lora" in low -> "Lora"
            else -> null
        }
        RunStyle(bold = bold, italic = italic, font = family, sizePt = (Math.round(tp.fontSizeInPt * 2f) / 2f).coerceIn(6f, 72f))
    }.getOrDefault(RunStyle())

    private class Found(val image: PDImageXObject, val x: Float, val y: Float, val w: Float, val h: Float)

    /** Walks one page's drawing commands and notes where each picture is drawn (PDF points, y from the bottom). */
    private class ImageFinder : PDFStreamEngine() {
        val found = ArrayList<Found>()
        init {
            addOperator(Concatenate())
            addOperator(DrawObject())
            addOperator(SetGraphicsStateParameters())
            addOperator(Save())
            addOperator(Restore())
            addOperator(SetMatrix())
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name == "Do" && operands.isNotEmpty()) {
                val x = resources.getXObject(operands[0] as COSName)
                if (x is PDImageXObject) {
                    val m = graphicsState.currentTransformationMatrix
                    found += Found(x, m.translateX, m.translateY, m.scalingFactorX, m.scalingFactorY)
                } else if (x is PDFormXObject) {
                    showForm(x)
                }
            } else {
                super.processOperator(operator, operands)
            }
        }
    }

    /** The page's pictures as inline picture blocks, each with its top edge in page pixels so it lands in reading order. */
    private fun pictureBlocks(doc: PDDocument, index: Int, scale: Float, pxW: Int, pxH: Int, setup: PageSetup): List<Pair<Int, Block>> = runCatching {
        val page = doc.getPage(index)
        if (((page.rotation % 360) + 360) % 360 != 0) return@runCatching emptyList()
        val box = page.cropBox ?: page.mediaBox
        val finder = ImageFinder()
        finder.processPage(page)
        val out = ArrayList<Pair<Int, Block>>()
        for (f in finder.found.take(MAX_PICTURES_PER_PAGE)) {
            val left = ((f.x - box.lowerLeftX) * scale)
            val top = ((box.upperRightY - (f.y + f.h)) * scale)
            val w = f.w * scale
            val h = f.h * scale
            if (w < 24f || h < 24f) continue
            // A picture covering the whole page is the page's own backdrop (searchable scans); the text is already extracted.
            if (w * h > 0.85f * pxW * pxH) continue
            val bmp = runCatching { f.image.image }.getOrNull() ?: continue
            val bytes = runCatching {
                val longest = maxOf(bmp.width, bmp.height)
                val shown = if (longest > 1400) android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width * 1400 / longest, bmp.height * 1400 / longest, true) else bmp
                val bos = java.io.ByteArrayOutputStream()
                val png = shown.hasAlpha()
                shown.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                Pair(bos.toByteArray(), if (png) "png" else "jpg")
            }.getOrNull() ?: continue
            val fullTwips = setup.sheetWidthTwips.toFloat()
            var wt = (w / pxW * fullTwips).toInt().coerceAtLeast(240)
            var ht = (h / pxW * fullTwips).toInt().coerceAtLeast(240)
            if (wt > setup.contentWidthTwips) { ht = ht * setup.contentWidthTwips / wt; wt = setup.contentWidthTwips }
            val cx = (left + w / 2f) / pxW
            val align = when { cx in 0.4f..0.6f -> Align.CENTER; cx > 0.66f -> Align.RIGHT; else -> Align.LEFT }
            out += top.toInt() to Block.Image(ImageBlock(bytes.first, bytes.second, wt, ht, align))
        }
        out
    }.getOrDefault(emptyList())

    private suspend fun textPage(bitmap: android.graphics.Bitmap, segs: List<TextSeg>, setup: PageSetup, pics: List<Pair<Int, Block>> = emptyList()): List<Block> {
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
        return OcrLayout.page(PdfLayout.blocks(segs), tables, bitmap.width, setup.contentWidthTwips, pics)
    }

    companion object {
        /** A page with fewer characters than this is treated as a scan. */
        const val MIN_TEXT_CHARS = 25
        const val MAX_PICTURES_PER_PAGE = 40
    }
}
