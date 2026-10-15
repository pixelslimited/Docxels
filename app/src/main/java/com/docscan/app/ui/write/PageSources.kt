package com.docscan.app.ui.write

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.DocxReader
import com.docscan.app.core.word.PageImage
import com.docscan.app.core.word.PageSetup
import com.docscan.app.data.ImageIO
import com.docscan.app.pdf.PdfPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a letterhead or a signature can come from: a picture, a PDF (its first page) or a Word file. */
object PageSources {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    val MIMES = arrayOf("image/*", "application/pdf", DOCX)

    private fun typeOf(context: Context, uri: Uri): String {
        val m = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val name = uri.toString().lowercase()
        return when {
            "pdf" in m || name.endsWith(".pdf") -> "pdf"
            "wordprocessingml" in m || name.endsWith(".docx") -> "docx"
            else -> "image"
        }
    }

    private fun readDocx(context: Context, uri: Uri) = context.contentResolver.openInputStream(uri)?.use { DocxReader.read(it) }

    /** The first page of a PDF as a bitmap, at most [maxSide] wide. */
    private suspend fun pdfPage(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val tmp = java.io.File.createTempFile("pick", ".pdf", context.cacheDir)
        return try {
            context.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            PdfPreview(tmp).use { it.render(0, maxSide) }
        } finally {
            tmp.delete()
        }
    }

    /** A picture from any of the sources. A Word file gives its first picture (header pictures if the body has none). */
    suspend fun bitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            when (typeOf(context, uri)) {
                "pdf" -> pdfPage(context, uri, maxSide)
                "docx" -> {
                    val doc = readDocx(context, uri) ?: return@runCatching null
                    val bytes = doc.blocks.filterIsInstance<Block.Image>().firstOrNull()?.image?.bytes
                        ?: (doc.page.headerImages + doc.page.firstHeaderImages + doc.page.footerImages).firstOrNull()?.bytes
                    bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                else -> ImageIO.decodeUri(context, uri, maxSide)
            }
        }.getOrNull()
    }

    private fun pdfFile(context: Context, uri: Uri): java.io.File {
        val tmp = java.io.File.createTempFile("pick", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        return tmp
    }

    private fun toolkit(context: Context) = (context.applicationContext as com.docscan.app.DocScanApp).container.pdfToWord

    /** Header, footer and watermark pictures of a Word file laid on one white sheet, as the letterhead would print. */
    private fun docxArtwork(doc: com.docscan.app.core.word.WordDoc, maxSide: Int): Bitmap? {
        val page = doc.page
        val imgs = (page.headerImages + page.firstHeaderImages + page.footerImages + page.firstFooterImages)
        if (imgs.isEmpty()) return null
        val w = maxSide.coerceIn(400, 2400)
        val h = (w.toLong() * page.sheetHeightTwips / page.sheetWidthTwips.coerceAtLeast(1)).toInt().coerceIn(400, 4000)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        val kx = w.toFloat() / page.sheetWidthTwips
        val ky = h.toFloat() / page.sheetHeightTwips
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        for (im in imgs) {
            val b = android.graphics.BitmapFactory.decodeByteArray(im.bytes, 0, im.bytes.size) ?: continue
            c.drawBitmap(b, null, android.graphics.RectF(im.xTwips * kx, im.yTwips * ky, (im.xTwips + im.widthTwips) * kx, (im.yTwips + im.heightTwips) * ky), paint)
            b.recycle()
        }
        return out
    }

    /** A letterhead picture from a picture, a PDF or a Word file: the artwork only, with the document's text left out. */
    suspend fun letterheadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            when (typeOf(context, uri)) {
                "pdf" -> {
                    val f = pdfFile(context, uri)
                    try { toolkit(context).letterheadOf(f, maxSide) } finally { f.delete() }
                }
                "docx" -> readDocx(context, uri)?.let { docxArtwork(it, maxSide) } ?: bitmap(context, uri, maxSide)
                else -> bitmap(context, uri, maxSide)
            }
        }.getOrNull()
    }

    /** A signature picture found inside a PDF or Word file (or the picture itself); null falls back to the page picture. */
    suspend fun signatureBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            when (typeOf(context, uri)) {
                "pdf" -> {
                    val f = pdfFile(context, uri)
                    try { toolkit(context).signatureOf(f) } finally { f.delete() }
                }
                "docx" -> {
                    val doc = readDocx(context, uri)
                    val small = doc?.blocks?.filterIsInstance<Block.Image>()?.map { it.image }
                        ?.filter { it.widthTwips <= (doc.page.contentWidthTwips * 0.5f) }?.lastOrNull()
                    small?.let { android.graphics.BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }
                }
                else -> null
            }
        }.getOrNull() ?: bitmap(context, uri, maxSide)
    }

    class Letterhead(val header: List<PageImage>, val footer: List<PageImage>)

    /** A full-page picture: always stretched to fill the whole sheet, edge to edge. */
    fun fullPage(bmp: Bitmap, setup: PageSetup): PageImage {
        val alpha = bmp.hasAlpha()
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, out)
        return PageImage(out.toByteArray(), if (alpha) "png" else "jpg", 0, 0, setup.sheetWidthTwips, setup.sheetHeightTwips, behind = true)
    }

    /**
     * A letterhead from a picture, a PDF or a Word file. Word files bring their own header and footer pictures across,
     * scaled to this document's paper; the other sources become one picture fitted to the paper.
     */
    suspend fun letterhead(context: Context, uri: Uri, setup: PageSetup): Letterhead? = withContext(Dispatchers.IO) {
        runCatching {
            if (typeOf(context, uri) == "docx") {
                val src = (readDocx(context, uri) ?: return@runCatching null).page
                val k = setup.sheetWidthTwips.toFloat() / src.sheetWidthTwips.coerceAtLeast(1)
                fun scaled(l: List<PageImage>) = l.map {
                    PageImage(it.bytes, it.extension, (it.xTwips * k).toInt(), (it.yTwips * k).toInt(), (it.widthTwips * k).toInt(), (it.heightTwips * k).toInt(), it.behind)
                }
                val header = scaled(src.headerImages.ifEmpty { src.firstHeaderImages })
                val footer = scaled(src.footerImages.ifEmpty { src.firstFooterImages })
                if (header.isEmpty() && footer.isEmpty()) null else Letterhead(header, footer)
            } else {
                val bmp = letterheadBitmap(context, uri, 2000) ?: return@runCatching null
                val img = fullPage(bmp, setup)
                bmp.recycle()
                Letterhead(listOf(img), emptyList())
            }
        }.getOrNull()
    }
}
