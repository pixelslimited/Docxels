package com.docscan.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.ImageBlock
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.StyleDefaults
import com.docscan.app.core.word.TableBlock
import com.docscan.app.core.word.PageImage
import com.docscan.app.core.word.WordDoc
import com.docscan.app.ui.write.SpanBridge
import com.docscan.app.ui.write.ZOOM
import java.io.OutputStream

/**
 * Draws a [WordDoc] to a PDF with the same text engine the editor uses, so the PDF matches what is on screen.
 * Layout runs in "zoomed" units (points x [ZOOM]) and the canvas is scaled back, which is what lets the editor's
 * density-based spans measure correctly here.
 *
 * Known limits: merged table cells are drawn unmerged.
 */
object WordPdf {
    fun write(doc: WordDoc, out: OutputStream) {
        Writer(doc).run(out)
    }

    private class Writer(val doc: WordDoc) {
        val page = doc.page
        val pageW = page.sheetWidthTwips / 20f
        val pageH = page.sheetHeightTwips / 20f
        val mL = page.marginLeftTwips / 20f
        val mT = page.marginTopTwips / 20f
        val mB = page.marginBottomTwips / 20f
        val contentW = page.contentWidthTwips / 20f * ZOOM
        val contentH = page.contentHeightTwips / 20f * ZOOM

        val pdf = PdfDocument()
        var pageNo = 0
        var current: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            density = 1f
            textSize = StyleDefaults.BODY_SIZE_PT * ZOOM
            typeface = com.docscan.app.ui.write.WordFonts.body()
            color = Color.rgb(0x1B, 0x1B, 0x1F)
        }

        fun run(out: OutputStream) {
            try {
                newPage()
                var pending = ArrayList<Paragraph>()
                fun flush() {
                    if (pending.isNotEmpty()) {
                        paragraphs(pending)
                        pending = ArrayList()
                    }
                }
                for (b in doc.blocks) {
                    when (b) {
                        is Block.Para -> pending += b.paragraph
                        is Block.Table -> { flush(); table(b.table) }
                        is Block.Image -> { flush(); image(b.image) }
                        is Block.Shape -> { flush(); shape(b.shape) }
                        Block.PageBreak -> { flush(); if (y > 0f) newPage() }
                    }
                }
                flush()
                finishPage()
                pdf.writeTo(out)
            } finally {
                pdf.close()
            }
        }

        fun newPage() {
            finishPage()
            pageNo++
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNo).create())
            current = p
            canvas = p.canvas
            y = 0f
            // Pictures behind the text (letterhead backgrounds, watermarks) go down first.
            val firstNow = page.differentFirstPage && pageNo == 1
            val c = p.canvas
            for (img in (if (firstNow) page.firstHeaderImages + page.firstFooterImages else page.headerImages + page.footerImages)) {
                if (img.behind) drawPageImage(c, img)
            }
        }

        val decoded = HashMap<PageImage, android.graphics.Bitmap?>()
        fun drawPageImage(c: Canvas, img: PageImage) {
            val bmp = decoded.getOrPut(img) { PageImageBitmaps.decode(img, 1600) } ?: return
            val dst = android.graphics.RectF(img.xTwips / 20f, img.yTwips / 20f, (img.xTwips + img.widthTwips) / 20f, (img.yTwips + img.heightTwips) / 20f)
            c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        fun finishPage() {
            val p = current ?: return
            val c = canvas!!
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.GRAY; typeface = com.docscan.app.ui.write.WordFonts.body() }
            val firstPage = page.differentFirstPage && pageNo == 1
            val headerText = page.headerFor(pageNo)
            val footerText = page.footerFor(pageNo)
            val numbers = page.numbersFor(pageNo)
            for (img in (if (firstPage) page.firstHeaderImages + page.firstFooterImages else page.headerImages + page.footerImages)) {
                if (!img.behind) drawPageImage(c, img)
            }
            if (headerText.isNotEmpty()) c.drawText(headerText, mL, mT / 2f + 3f, small)
            val fy = pageH - mB / 2f + 3f
            if (footerText.isNotEmpty()) c.drawText(footerText, mL, fy, small)
            if (numbers) {
                val label = pageNo.toString()
                c.drawText(label, pageW - mL - small.measureText(label), fy, small)
            }
            pdf.finishPage(p)
            current = null
        }

        /** Runs [draw] in content coordinates (zoomed units) at the current page's top-left of the text area. */
        inline fun inContent(top: Float, draw: Canvas.() -> Unit) {
            val c = canvas!!
            c.save()
            c.translate(mL, mT)
            c.scale(1f / ZOOM, 1f / ZOOM)
            c.translate(0f, top)
            c.draw()
            c.restore()
        }

        fun layoutOf(paragraphs: List<Paragraph>, width: Float, justify: Boolean = false): StaticLayout {
            val text = SpanBridge.toEditable(paragraphs, 1f)
            val b = StaticLayout.Builder.obtain(text, 0, text.length, body, width.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
            // Justified paragraphs are laid out together so the library stretches every line but the last of each.
            if (justify) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            return b.build()
        }

        /** Consecutive paragraphs that are all justified, or all not, so each run can be laid out its own way. */
        fun paragraphs(list: List<Paragraph>) {
            var i = 0
            while (i < list.size) {
                val j = list[i].align == Align.JUSTIFY
                var k = i + 1
                while (k < list.size && (list[k].align == Align.JUSTIFY) == j) k++
                paragraphRun(list.subList(i, k), j)
                i = k
            }
        }

        fun paragraphRun(list: List<Paragraph>, justify: Boolean) {
            val layout = layoutOf(list, contentW, justify)
            var line = 0
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                val remaining = contentH - y
                if (layout.getLineBottom(line) - top > remaining && y > 0f) {
                    newPage()
                    continue
                }
                var last = line
                while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= remaining) last++
                val height = (layout.getLineBottom(last) - top).toFloat()
                inContent(y) {
                    clipRect(0f, 0f, contentW, height)
                    translate(0f, -top.toFloat())
                    layout.draw(this)
                }
                y += height
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
        }

        fun table(t: TableBlock) {
            if (t.rows.isEmpty() || t.columnWidthsTwips.isEmpty()) return
            val pad = 5f * ZOOM
            val total = t.columnWidthsTwips.sum().coerceAtLeast(1) / 20f * ZOOM
            val scale = if (total > contentW) contentW / total else 1f
            val widths = t.columnWidthsTwips.map { it / 20f * ZOOM * scale }
            val indentPx = if (scale < 1f) 0f else t.indentTwips / 20f * ZOOM
            val fill = Paint()
            y += 2f * ZOOM

            class Placed(val x: Float, val w: Float, val cell: com.docscan.app.core.word.TableCell, val layout: StaticLayout)

            fun place(row: List<com.docscan.app.core.word.TableCell>): Pair<List<Placed>, Float> {
                val minH = com.docscan.app.core.word.CellEdges.rowHeight(row) / 20f * ZOOM
                var col = 0
                var x = 0f
                val placed = ArrayList<Placed>()
                var h = 0f
                for (cell in row) {
                    val span = cell.colSpan.coerceAtLeast(1)
                    val w = (col until minOf(col + span, widths.size)).sumOf { widths[it].toDouble() }.toFloat()
                    val paras = if (cell.vMergeContinue) listOf(Paragraph()) else cell.paragraphs
                    val l = layoutOf(paras, (w - 2 * pad).coerceAtLeast(1f), paras.isNotEmpty() && paras.all { it.align == Align.JUSTIFY })
                    h = maxOf(h, l.height + 2 * pad)
                    placed += Placed(x, w, cell, l)
                    x += w
                    col += span
                }
                return placed to maxOf(h, minH)
            }

            fun draw(row: List<Placed>, h: Float) {
                inContent(y) {
                    for (p in row) {
                        p.cell.shading?.let {
                            fill.color = Color.rgb((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
                            drawRect(indentPx + p.x, 0f, indentPx + p.x + p.w, h, fill)
                        }
                        CellBorderDraw.draw(this, RectF(indentPx + p.x, 0f, indentPx + p.x + p.w, h), p.cell, t, ZOOM)
                        save()
                        clipRect(indentPx + p.x, 0f, indentPx + p.x + p.w, h)
                        translate(indentPx + p.x + pad, pad)
                        p.layout.draw(this)
                        restore()
                    }
                }
            }

            var headerPlaced: Pair<List<Placed>, Float>? = null
            t.rows.forEachIndexed { ri, row ->
                val (placed, rawH) = place(row)
                val h = minOf(rawH, contentH)
                if (y + h > contentH && y > 0f) {
                    newPage()
                    headerPlaced?.let { (hp, hh) -> draw(hp, hh); y += hh }
                }
                draw(placed, h)
                y += h
                if (ri == 0 && t.headerRow) headerPlaced = placed to h
            }
            y += 6f * ZOOM
        }

        fun image(img: ImageBlock) {
            val bmp = decode(img.bytes) ?: return
            try {
                if (img.pageRelative && img.wrap.floating) {
                    // Pinned to the sheet: drawn at its place on the page the anchor falls on, whatever the margins.
                    val c = canvas!!
                    val l = img.offsetXTwips / 20f
                    val t = img.offsetYTwips / 20f
                    c.drawBitmap(bmp, null, RectF(l, t, l + img.widthTwips / 20f, t + img.heightTwips / 20f), Paint(Paint.FILTER_BITMAP_FLAG))
                    return
                }
                var w = img.widthTwips / 20f * ZOOM
                var h = img.heightTwips / 20f * ZOOM
                if (w > contentW) { h *= contentW / w; w = contentW }
                if (h > contentH) { w *= contentH / h; h = contentH }
                if (img.wrap.floating) {
                    // Floats where it was dropped, relative to its anchor line, and takes no room in the text.
                    val fx = (img.offsetXTwips / 20f * ZOOM).coerceIn(0f, maxOf(0f, contentW - w))
                    val fy = (y + img.offsetYTwips / 20f * ZOOM).coerceIn(0f, maxOf(0f, contentH - h))
                    inContent(fy) { drawBitmap(bmp, null, RectF(fx, 0f, fx + w, h), Paint(Paint.FILTER_BITMAP_FLAG)) }
                    return
                }
                if (y + h > contentH && y > 0f) newPage()
                val x = when (img.align) {
                    Align.CENTER -> (contentW - w) / 2f
                    Align.RIGHT -> contentW - w
                    else -> 0f
                }
                inContent(y) {
                    drawBitmap(bmp, null, RectF(x, 0f, x + w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                }
                y += h + 6f * ZOOM
            } finally {
                bmp.recycle()
            }
        }

        fun shape(sh: com.docscan.app.core.word.ShapeBlock) {
            var w = sh.widthTwips / 20f * ZOOM
            var h = (if (com.docscan.app.core.word.ShapePresets.isLine(sh.preset)) 4 else sh.heightTwips) / 20f * ZOOM
            if (w > contentW) { h *= contentW / w; w = contentW }
            if (h > contentH) { w *= contentH / h; h = contentH }
            if (sh.wrap.floating) {
                val fx = (sh.offsetXTwips / 20f * ZOOM).coerceIn(0f, maxOf(0f, contentW - w))
                val fy = (y + sh.offsetYTwips / 20f * ZOOM).coerceIn(0f, maxOf(0f, contentH - h))
                inContent(fy) { ShapeDraw.draw(this, sh, RectF(fx, 0f, fx + w, h), ZOOM) }
                return
            }
            if (y + h > contentH && y > 0f) newPage()
            val x = when (sh.align) {
                Align.CENTER -> (contentW - w) / 2f
                Align.RIGHT -> contentW - w
                else -> 0f
            }
            inContent(y) { ShapeDraw.draw(this, sh, RectF(x, 0f, x + w, h), ZOOM) }
            y += h + 6f * ZOOM
        }

        fun decode(bytes: ByteArray): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
