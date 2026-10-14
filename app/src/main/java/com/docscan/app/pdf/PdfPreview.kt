package com.docscan.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Renders pages of a (not password-protected) PDF with the platform renderer. One page is open at a time. */
class PdfPreview(file: File) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /** Page size in PDF points, for working out aspect ratio before rendering. */
    suspend fun pageSize(index: Int): Pair<Int, Int> = lock.withLock {
        withContext(Dispatchers.IO) { renderer.openPage(index).use { it.width to it.height } }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val w = widthPx.coerceIn(64, 2400)
                val h = (w.toLong() * page.height / page.width).toInt().coerceIn(64, 6000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }
}
