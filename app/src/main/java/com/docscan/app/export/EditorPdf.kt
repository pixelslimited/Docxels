package com.docscan.app.export

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.docscan.app.core.word.WordDoc
import com.docscan.app.ui.write.ChipLimits
import com.docscan.app.ui.write.EditorController
import com.docscan.app.ui.write.RichEditText
import com.docscan.app.ui.write.ViewMode
import com.docscan.app.ui.write.ZOOM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Makes the PDF (and so the page preview, printing and sharing) with the editor itself: the document is laid out by the
 * very same page view that is on screen, and each sheet of it is drawn onto a PDF page. Nothing is laid out a second
 * time by separate code, so a table, a picture, a header or a page break cannot come out differently from the editor.
 *
 * The editor's views only work on the main thread, so this runs there. If anything goes wrong it falls back to the
 * older separate PDF writer ([WordPdf]) so that exporting never fails outright.
 */
object PdfExport {
    suspend fun write(context: Context, doc: WordDoc, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        val bytes = withContext(Dispatchers.Main) {
            runCatching { editorPdf(context.applicationContext, doc, onProgress) }.getOrNull()
        }
        if (bytes != null) out.write(bytes)
        else withContext(Dispatchers.Default) { WordPdf.write(doc, out, onProgress) }
    }

    private fun editorPdf(context: Context, doc: WordDoc, onProgress: (Float) -> Unit): ByteArray {
        val dens = context.resources.displayMetrics.density
        val page = doc.page
        val sheetPt = page.sheetWidthTwips / 20f
        // Lay the page out at exactly one unit per point (times the editor's zoom constant), so no screen size is involved.
        val width = Math.ceil((sheetPt * ZOOM).toDouble()).toInt()
        val gutter = 12f * dens
        val avail = width + Math.round(2f * gutter)
        val usable = avail - 2f * gutter
        val savedLimit = ChipLimits.maxWidthPx
        val ctrl = EditorController(dens, doc.blocks, doc.comments)
        val view = RichEditText(context)
        try {
            ctrl.attach(view)
            ctrl.setViewConfig(ViewMode.PAGE, page)
            ctrl.setAvailableWidth(avail)
            val w = usable.toInt()
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, w, view.measuredHeight)
            val bands = view.sheetBands()
            check(bands.isNotEmpty()) { "no pages" }
            val pw = (page.sheetWidthTwips / 20f).toInt()
            val ph = (page.sheetHeightTwips / 20f).toInt()
            val scale = pw / usable
            val pdf = PdfDocument()
            try {
                bands.forEachIndexed { k, (top, h) ->
                    onProgress(k / bands.size.toFloat())
                    val p = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, k + 1).create())
                    val c = p.canvas
                    c.save()
                    c.scale(scale, scale)
                    c.translate(0f, -top)
                    c.clipRect(0f, top, w.toFloat(), top + h)
                    ChipLimits.exporting = true
                    try { view.draw(c) } finally { ChipLimits.exporting = false }
                    c.restore()
                    pdf.finishPage(p)
                }
                val out = ByteArrayOutputStream()
                pdf.writeTo(out)
                return out.toByteArray()
            } finally { pdf.close() }
        } finally {
            ctrl.detach()
            ChipLimits.maxWidthPx = savedLimit
        }
    }
}
