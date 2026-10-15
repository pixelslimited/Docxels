package com.docscan.app.export

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Sends an already-made PDF to Android's print dialog (printers, "Save as PDF", cloud print services the person has set up). */
object PrintHelper {
    fun printPdf(context: Context, file: File, jobName: String): Boolean {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        pm.print(jobName.ifBlank { "Document" }, PdfAdapter(file, jobName), null)
        return true
    }

    private class PdfAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
        }

        override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
            try {
                FileInputStream(file).use { input -> FileOutputStream(dest.fileDescriptor).use { out -> input.copyTo(out) } }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            }
        }
    }
}
