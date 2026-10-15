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
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ImageBlock
import com.docscan.app.core.word.ImageWrap
import com.docscan.app.core.word.ShapeBlock
import com.docscan.app.core.word.RunStyle
import com.docscan.app.core.word.PageImage
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

    suspend fun convert(file: File, title: String, onProgress: (Int, Int) -> Unit, onStatus: (String) -> Unit = {}): PdfConversion {
        val doc = withContext(Dispatchers.IO) {
            try { PDDocument.load(file, "") } catch (e: InvalidPasswordException) { throw PdfPasswordException() }
        }
        doc.use {
            val total = doc.numberOfPages
            require(total > 0) { "That PDF has no pages." }
            val setup0 = pageSetupOf(doc)
            // Pictures drawn in the same place on several pages are the letterhead (top) or footer artwork (bottom).
            onStatus("Studying the pages: pictures, lines and boxes…")
            val parts = withContext(Dispatchers.Default) { (0 until total).map { runCatching { findParts(doc, it) }.getOrNull() } }
            val pagePics = parts.map { it?.pics ?: emptyList() }
            val (setup, repeated) = withContext(Dispatchers.Default) { letterheadFrom(pagePics, total, setup0) }
            var letterheadFound = repeated.isNotEmpty()
            val blocks = ArrayList<Block>()
            var ocrPages = 0
            var pictures = 0
            PdfPreview(file).use { preview ->
                for (i in 0 until total) {
                    onProgress(i + 1, total)
                    val nPics = pagePics[i].size
                    val nLines = parts[i]?.shapes?.size ?: 0
                    onStatus("Page ${i + 1} of $total · reading text" + (if (nPics > 0) " · placing $nPics picture(s)" else "") + (if (nLines > 0) " · drawing $nLines line(s)/box(es)" else ""))
                    val bitmap = preview.render(i, ScanToWord.SIDE)
                    try {
                        val pdfPage = doc.getPage(i)
                        val dims = pageDims(pdfPage)
                        val scale = bitmap.width / dims.first
                        val segs = withContext(Dispatchers.Default) { extract(doc, i, scale) }
                        val chars = segs.sumOf { it.text.length }
                        val pageBlocks: List<Block> = if (chars >= MIN_TEXT_CHARS) {
                            val pics = withContext(Dispatchers.Default) { pictureBlocks(pagePics[i], repeated, bitmap.height, bitmap.width, setup) }
                            pictures += pics.size
                            textPage(bitmap, segs, setup, pics, parts[i]?.let { shapesOf(it, scale, bitmap.width, bitmap.height) } ?: emptyList())
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
            if (letterheadFound) notes += "Pictures repeated on every page were kept as the letterhead (header and footer artwork)."
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
        val colours = java.util.IdentityHashMap<TextPosition, Int>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                runCatching { colours[text] = graphicsState.nonStrokingColor.toRGB() and 0xFFFFFF }
                super.processTextPosition(text)
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val glyphs = textPositions.map {
                    Glyph(it.unicode ?: "", it.xDirAdj * scale, it.yDirAdj * scale, it.widthDirAdj * scale, it.fontSizeInPt.coerceAtLeast(1f) * scale, styleOf(it, colours[it]))
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
    private fun styleOf(tp: TextPosition, rgb: Int?): RunStyle = runCatching {
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
        RunStyle(bold = bold, italic = italic, font = family, color = rgb?.takeIf { it != 0 }, sizePt = (Math.round(tp.fontSizeInPt * 2f) / 2f).coerceIn(6f, 72f))
    }.getOrDefault(RunStyle())

    private class Found(val image: PDImageXObject, val x: Float, val y: Float, val w: Float, val h: Float)

    /** A line or box the page draws, in PDF points (origin bottom-left). [fill] / [stroke] are RGB or null. */
    class Vec(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val fill: Int?, val stroke: Int?, val widthPt: Float)

    /** Walks one page's drawing commands and notes every picture, ruled line and box, with where and how it is drawn. */
    private class PageGraphics(page: PDPage) : PDFGraphicsStreamEngine(page) {
        val found = ArrayList<Found>()
        val vecs = ArrayList<Vec>()
        var curvesSkipped = 0
        private var path = ArrayList<ArrayList<PointF>>()
        private var cur = ArrayList<PointF>()
        private var at = PointF(0f, 0f)

        private fun rgb(get: () -> Int): Int? = runCatching { get() and 0xFFFFFF }.getOrNull()

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            flushSub()
            path.add(arrayListOf(PointF(p0.x, p0.y), PointF(p1.x, p1.y), PointF(p2.x, p2.y), PointF(p3.x, p3.y), PointF(p0.x, p0.y)))
            at = p0
        }

        override fun drawImage(pdImage: PDImage) {
            if (pdImage !is PDImageXObject) return
            val m = graphicsState.currentTransformationMatrix
            found += Found(pdImage, m.translateX, m.translateY, m.scalingFactorX, m.scalingFactorY)
        }

        override fun clip(windingRule: Path.FillType) {}
        override fun moveTo(x: Float, y: Float) { flushSub(); cur.add(PointF(x, y)); at = PointF(x, y) }
        override fun lineTo(x: Float, y: Float) { cur.add(PointF(x, y)); at = PointF(x, y) }
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { curvesSkipped++; cur.add(PointF(x3, y3)); at = PointF(x3, y3) }
        override fun getCurrentPoint(): PointF = at
        override fun closePath() { cur.firstOrNull()?.let { cur.add(PointF(it.x, it.y)) } }
        override fun endPath() { path = ArrayList(); cur = ArrayList() }
        override fun shadingFill(shadingName: COSName) {}

        private fun flushSub() {
            if (cur.size >= 2) path.add(cur)
            cur = ArrayList()
        }

        private fun paint(fill: Boolean, stroke: Boolean) {
            flushSub()
            val f = if (fill) rgb { graphicsState.nonStrokingColor.toRGB() } else null
            val st = if (stroke) rgb { graphicsState.strokingColor.toRGB() } else null
            val lw = graphicsState.lineWidth.coerceAtLeast(0.25f)
            for (sub in path) {
                if (vecs.size >= MAX_VECTORS_PER_PAGE) break
                val xs = sub.map { it.x }
                val ys = sub.map { it.y }
                val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
                val axisAligned = sub.size <= 5 && sub.zipWithNext().all { (p, q) -> Math.abs(p.x - q.x) < 0.5f || Math.abs(p.y - q.y) < 0.5f }
                if (!axisAligned) { curvesSkipped++; continue }
                vecs += Vec(minX, minY, maxX, maxY, f, st, lw)
            }
            path = ArrayList()
        }

        override fun strokePath() = paint(false, true)
        override fun fillPath(windingRule: Path.FillType) = paint(true, false)
        override fun fillAndStrokePath(windingRule: Path.FillType) = paint(true, true)
    }

    private class PagePic(val key: Any, val fx: Float, val fy: Float, val fw: Float, val fh: Float, val image: PDImageXObject) {
        /** The same picture in the same place (to a fiftieth of the page) gives the same signature. */
        val sig: String get() = "${System.identityHashCode(key)}:${(fx * 50).toInt()}:${(fy * 50).toInt()}:${(fw * 50).toInt()}:${(fh * 50).toInt()}"
    }

    private class PageParts(val pics: List<PagePic>, val shapes: List<Vec>, val boxW: Float, val boxH: Float, val llx: Float, val ury: Float, val skippedCurves: Int)

    /** Where each picture and drawn line / box is on one page. Pictures are fractions of the page (y from the top). */
    private fun findParts(doc: PDDocument, index: Int): PageParts {
        val page = doc.getPage(index)
        val box = page.cropBox ?: page.mediaBox
        val bw = box.width.coerceAtLeast(1f)
        val bh = box.height.coerceAtLeast(1f)
        if (((page.rotation % 360) + 360) % 360 != 0) return PageParts(emptyList(), emptyList(), bw, bh, box.lowerLeftX, box.upperRightY, 0)
        val g = PageGraphics(page)
        g.processPage(page)
        val pics = g.found.take(MAX_PICTURES_PER_PAGE).map { f ->
            val w = Math.abs(f.w)
            val h = Math.abs(f.h)
            val bottom = minOf(f.y, f.y + f.h)
            PagePic(f.image.cosObject, (f.x - box.lowerLeftX) / bw, (box.upperRightY - (bottom + h)) / bh, w / bw, h / bh, f.image)
        }
        return PageParts(pics, g.vecs, bw, bh, box.lowerLeftX, box.upperRightY, g.curvesSkipped)
    }

    private fun encode(image: PDImageXObject): Pair<ByteArray, String>? = runCatching {
        val bmp = image.image
        val longest = maxOf(bmp.width, bmp.height)
        val shown = if (longest > 1400) android.graphics.Bitmap.createScaledBitmap(bmp, bmp.width * 1400 / longest, bmp.height * 1400 / longest, true) else bmp
        val bos = java.io.ByteArrayOutputStream()
        val png = shown.hasAlpha()
        shown.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
        Pair(bos.toByteArray(), if (png) "png" else "jpg")
    }.getOrNull()

    /**
     * Pictures that sit in the same place on at least two pages (and on a good share of them) become the letterhead
     * (upper half) or footer artwork (lower half). The top and bottom margins grow to clear them, so the text starts
     * below the letterhead as it did on the PDF.
     */
    private fun letterheadFrom(pages: List<List<PagePic>>, total: Int, setup: PageSetup): Pair<PageSetup, Set<String>> {
        if (total < 2) return setup to emptySet()
        val counts = HashMap<String, Int>()
        val first = HashMap<String, PagePic>()
        for (pics in pages) for (sig in pics.map { it.sig }.toSet()) counts[sig] = (counts[sig] ?: 0) + 1
        for (pics in pages) for (p in pics) first.putIfAbsent(p.sig, p)
        val need = maxOf(2, (total * 0.4f).toInt())
        val repeated = counts.filter { it.value >= need }.keys
            .filter { first[it]!!.let { p -> p.fw * p.fh < 0.5f && (p.fy + p.fh <= 0.45f || p.fy >= 0.55f) } }.toSet()
        if (repeated.isEmpty()) return setup to emptySet()
        val sw = setup.sheetWidthTwips
        val sh = setup.sheetHeightTwips
        val header = ArrayList<PageImage>()
        val footer = ArrayList<PageImage>()
        var top = setup.marginTopTwips
        var bottom = setup.marginBottomTwips
        for (sig in repeated) {
            val p = first[sig]!!
            val (bytes, ext) = encode(p.image) ?: continue
            val img = PageImage(bytes, ext, (p.fx * sw).toInt(), (p.fy * sh).toInt(), (p.fw * sw).toInt().coerceAtLeast(120), (p.fh * sh).toInt().coerceAtLeast(120), true)
            if (p.fy + p.fh <= 0.45f) {
                header += img
                top = maxOf(top, img.yTwips + img.heightTwips + 240)
            } else {
                footer += img
                bottom = maxOf(bottom, sh - img.yTwips + 240)
            }
        }
        if (header.isEmpty() && footer.isEmpty()) return setup to emptySet()
        return setup.copy(
            headerImages = header, footerImages = footer,
            marginTopTwips = top.coerceAtMost(sh / 3), marginBottomTwips = bottom.coerceAtMost(sh / 3),
        ) to repeated
    }

    /** This page's pictures as inline picture blocks, each with its top edge in page pixels so it lands in reading order. */
    /** The page's drawn lines and boxes as floating shapes behind the text, in page pixels. */
    private fun shapesOf(p: PageParts, scale: Float, pxW: Int, pxH: Int): List<OcrLayout.OcrShape> {
        val out = ArrayList<OcrLayout.OcrShape>()
        val seen = HashSet<String>()
        val twipsPerPt = 20f
        fun white(c: Int) = (c shr 16 and 255) > 245 && (c shr 8 and 255) > 245 && (c and 255) > 245
        for (v in p.shapes) {
            val wPt = v.x2 - v.x1
            val hPt = v.y2 - v.y1
            if (wPt * hPt > 0.85f * p.boxW * p.boxH) continue
            val thin = minOf(wPt, hPt) < 2.5f
            if (!thin && v.fill != null && white(v.fill) && v.stroke == null) continue
            val shape = if (thin) {
                val c = v.fill ?: v.stroke ?: 0
                if (white(c)) continue
                ShapeBlock("rect", (wPt * twipsPerPt).toInt().coerceAtLeast(20), (hPt * twipsPerPt).toInt().coerceAtLeast(20), c, c, 0.25f, wrap = ImageWrap.BEHIND)
            } else {
                if (v.fill == null && v.stroke == null) continue
                val fill = v.fill?.takeIf { !white(it) }
                if (fill == null && v.stroke == null) continue
                ShapeBlock("rect", (wPt * twipsPerPt).toInt(), (hPt * twipsPerPt).toInt(), fill, v.stroke ?: fill ?: 0, if (v.stroke != null) v.widthPt.coerceIn(0.25f, 6f) else 0.25f, wrap = ImageWrap.BEHIND)
            }
            val left = ((v.x1 - p.llx) * scale).toInt()
            val top = ((p.ury - v.y2) * scale).toInt()
            if (!seen.add("$left,$top,${(wPt).toInt()},${(hPt).toInt()}")) continue
            out += OcrLayout.OcrShape(left, top, left + (wPt * scale).toInt(), top + (hPt * scale).toInt(), shape)
        }
        return out
    }

    private fun pictureBlocks(pics: List<PagePic>, repeated: Set<String>, pxH: Int, pxW: Int, setup: PageSetup): List<Pair<Int, Block>> {
        val out = ArrayList<Pair<Int, Block>>()
        for (p in pics) {
            if (p.sig in repeated) continue
            val w = p.fw * pxW
            val h = p.fh * pxH
            if (w < 24f || h < 24f) continue
            // A picture covering the whole page is the page's own backdrop (searchable scans); the text is already extracted.
            if (p.fw * p.fh > 0.85f) continue
            val (bytes, ext) = encode(p.image) ?: continue
            var wt = (p.fw * setup.sheetWidthTwips).toInt().coerceAtLeast(240)
            var ht = (p.fh * setup.sheetHeightTwips).toInt().coerceAtLeast(240)
            if (wt > setup.contentWidthTwips) { ht = ht * setup.contentWidthTwips / wt; wt = setup.contentWidthTwips }
            val cx = p.fx + p.fw / 2f
            val align = when { cx in 0.4f..0.6f -> Align.CENTER; cx > 0.66f -> Align.RIGHT; else -> Align.LEFT }
            out += (p.fy * pxH).toInt() to Block.Image(ImageBlock(bytes, ext, wt, ht, align))
        }
        return out
    }

    private suspend fun textPage(bitmap: android.graphics.Bitmap, segs: List<TextSeg>, setup: PageSetup, pics: List<Pair<Int, Block>> = emptyList(), shapes: List<OcrLayout.OcrShape> = emptyList()): List<Block> {
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
        val free = shapes.filter { sh ->
            val cx = (sh.left + sh.right) / 2
            val cy = (sh.top + sh.bottom) / 2
            tables.none { cx in it.left..it.right && cy in it.top..it.bottom }
        }
        val twipsPerPx = setup.sheetWidthTwips.toFloat() / bitmap.width
        return OcrLayout.page(PdfLayout.blocks(segs), tables, bitmap.width, setup.contentWidthTwips, pics, free, twipsPerPx, setup.marginLeftTwips)
    }

    companion object {
        /** A page with fewer characters than this is treated as a scan. */
        const val MIN_TEXT_CHARS = 25
        const val MAX_PICTURES_PER_PAGE = 40
        const val MAX_VECTORS_PER_PAGE = 120
    }
}
