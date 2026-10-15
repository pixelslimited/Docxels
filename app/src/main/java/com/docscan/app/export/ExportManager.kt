package com.docscan.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.docscan.app.core.DocxBuilder
import com.docscan.app.core.XlsxBuilder
import com.docscan.app.data.PageEntity
import com.docscan.app.imaging.PageRenderer
import com.docscan.app.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object Mime {
    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val TXT = "text/plain"
    const val RTF = "application/rtf"
}

enum class DocFormat(val label: String) {
    PDF("PDF"),
    WORD("Word"),
    TXT("Text"),
    JPEG("Image (JPEG)"),
    PNG("Image (PNG)"),
}

data class ExportOptions(
    val format: DocFormat = DocFormat.PDF,
    /** PDF only: fit pages onto A4 sheets instead of using the scan's own proportions. */
    val a4: Boolean = true,
    /** PDF only: also put the recognised text under the picture so the PDF can be searched and copied from (runs OCR; slower). */
    val searchable: Boolean = false,
    /** PDF only: put the first two pages (front and back of a card) at real card size on one A4 sheet. */
    val idCard: Boolean = false,
)

class ExportResult(val files: List<File>, val mime: String)

/** Creates PDF, Word, Excel and image files in the app cache; sharing/saving is separate. */
class ExportManager(
    private val context: Context,
    private val renderer: PageRenderer,
    private val ocr: OcrEngine,
) {
    private val dir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun exportDocument(pages: List<PageEntity>, name: String, options: ExportOptions): ExportResult {
        require(pages.isNotEmpty()) { "There are no pages to export" }
        val base = safeName(name)
        return when (options.format) {
            DocFormat.PDF -> ExportResult(listOf(if (options.idCard) idCardPdf(pages, base) else pdf(pages, base, options.a4, options.searchable)), Mime.PDF)
            // Word is always recognised, editable text (OCR); page pictures are what PDF and image export are for.
            DocFormat.WORD -> ExportResult(listOf(wordText(pages, base)), Mime.DOCX)
            DocFormat.TXT -> ExportResult(listOf(textFile(pages, base)), Mime.TXT)
            DocFormat.JPEG -> ExportResult(images(pages, base, png = false), Mime.JPEG)
            DocFormat.PNG -> ExportResult(images(pages, base, png = true), Mime.PNG)
        }
    }

    private suspend fun pdf(pages: List<PageEntity>, base: String, a4: Boolean, searchable: Boolean = false): File =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$base.pdf")
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, page ->
                    val bitmap = renderer.render(page, EXPORT_SIDE)
                    val text = if (searchable) runCatching { ocr.recognize(bitmap) }.getOrNull() else null
                    drawPdfPage(document, bitmap, index + 1, a4, text)
                    bitmap.recycle()
                }
                FileOutputStream(file).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            file
        }

    /** Front and back of an ID card (85.6 x 54 mm) at true size on one A4 page, ready to print and photocopy. */
    private suspend fun idCardPdf(pages: List<PageEntity>, base: String): File = withContext(Dispatchers.IO) {
        val file = File(dir, "$base-ID.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val cardW = 85.6f / 25.4f * 72f
            val cardH = 54f / 25.4f * 72f
            val left = (595f - cardW) / 2f
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.5f }
            pages.take(2).forEachIndexed { i, p ->
                val top = 150f + i * (cardH + 36f)
                val bmp = renderer.render(p, 1600)
                val rect = RectF(left, top, left + cardW, top + cardH)
                canvas.drawBitmap(bmp, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.drawRect(rect, border)
                bmp.recycle()
            }
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
        } finally { document.close() }
        file
    }

    private fun drawPdfPage(document: PdfDocument, bitmap: Bitmap, number: Int, a4: Boolean, text: com.google.mlkit.vision.text.Text? = null) {
        val landscape = bitmap.width > bitmap.height
        val (pageW, pageH) = when {
            a4 && landscape -> 842 to 595
            a4 -> 595 to 842
            else -> 595 to (595f * bitmap.height / bitmap.width).toInt().coerceAtLeast(1)
        }
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, number).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val scale = min(pageW.toFloat() / bitmap.width, pageH.toFloat() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (pageW - w) / 2f
        val top = (pageH - h) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        if (text != null) drawHiddenText(canvas, text, left, top, scale)
        document.finishPage(page)
    }

    /**
     * Writes the recognised words over the picture in an almost fully transparent colour: nobody sees them, but PDF readers
     * can search and copy them. Each line is stretched to cover the printed line it came from.
     */
    private fun drawHiddenText(canvas: android.graphics.Canvas, text: com.google.mlkit.vision.text.Text, left: Float, top: Float, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(1, 0, 0, 0) }
        for (block in text.textBlocks) for (line in block.lines) {
            val box = line.boundingBox ?: continue
            val str = line.text
            if (str.isBlank()) continue
            val boxH = box.height() * scale
            paint.textSize = (boxH * 0.8f).coerceAtLeast(1f)
            paint.textScaleX = 1f
            val natural = paint.measureText(str)
            if (natural > 0f) paint.textScaleX = ((box.width() * scale) / natural).coerceIn(0.2f, 5f)
            canvas.drawText(str, left + box.left * scale, top + box.bottom * scale - boxH * 0.2f, paint)
        }
    }

    private suspend fun wordText(pages: List<PageEntity>, base: String): File =
        withContext(Dispatchers.IO) {
            val docx = DocxBuilder()
            pages.forEachIndexed { index, page ->
                val bitmap = renderer.render(page, OCR_SIDE)
                val paragraphs = ocr.paragraphs(bitmap)
                bitmap.recycle()
                if (paragraphs.isEmpty()) {
                    docx.addParagraph("[No text recognised on page ${index + 1}]", pageBreakBefore = index > 0)
                } else {
                    paragraphs.forEachIndexed { i, text -> docx.addParagraph(text, pageBreakBefore = index > 0 && i == 0) }
                }
            }
            File(dir, "$base.docx").also { file -> FileOutputStream(file).use { docx.writeTo(it) } }
        }

    /** Plain text of every page (OCR), pages separated by a blank line and a rule. */
    private suspend fun textFile(pages: List<PageEntity>, base: String): File =
        withContext(Dispatchers.IO) {
            val text = StringBuilder()
            pages.forEachIndexed { index, page ->
                val bitmap = renderer.render(page, OCR_SIDE)
                val paragraphs = ocr.paragraphs(bitmap)
                bitmap.recycle()
                if (index > 0) text.append("\n\n----- Page ${index + 1} -----\n\n")
                text.append(if (paragraphs.isEmpty()) "[No text recognised on page ${index + 1}]" else paragraphs.joinToString("\n\n"))
            }
            File(dir, "$base.txt").also { it.writeText(text.toString(), Charsets.UTF_8) }
        }

    private suspend fun images(pages: List<PageEntity>, base: String, png: Boolean): List<File> =
        withContext(Dispatchers.IO) {
            pages.mapIndexed { index, page ->
                val bitmap = renderer.render(page, EXPORT_SIDE)
                val file = File(dir, "${base}_${index + 1}.${if (png) "png" else "jpg"}")
                FileOutputStream(file).use {
                    bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
                }
                bitmap.recycle()
                file
            }
        }

    // ---- Tables -------------------------------------------------------------------------------

    suspend fun tableXlsx(rows: List<List<String>>, name: String): File = withContext(Dispatchers.IO) {
        File(dir, "${safeName(name)}.xlsx").also { f -> FileOutputStream(f).use { XlsxBuilder.build(rows, "Table 1", it) } }
    }

    suspend fun workbookXlsx(sheets: List<Pair<String, List<List<String>>>>, name: String): File = withContext(Dispatchers.IO) {
        File(dir, "${safeName(name)}.xlsx").also { f -> FileOutputStream(f).use { XlsxBuilder.buildSheets(sheets, it) } }
    }

    suspend fun tableDocx(rows: List<List<String>>, name: String): File = withContext(Dispatchers.IO) {
        val docx = DocxBuilder().apply { addTable(rows) }
        File(dir, "${safeName(name)}.docx").also { f -> FileOutputStream(f).use { docx.writeTo(it) } }
    }

    /** [tableImage] is the cropped, cleaned picture of the table region. */
    suspend fun tablePdf(tableImage: Bitmap, name: String): File = withContext(Dispatchers.IO) {
        val file = File(dir, "${safeName(name)}.pdf")
        val document = PdfDocument()
        try {
            drawPdfPage(document, tableImage, 1, a4 = true)
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        file
    }

    suspend fun tableImage(tableImage: Bitmap, name: String, png: Boolean): File = withContext(Dispatchers.IO) {
        File(dir, "${safeName(name)}.${if (png) "png" else "jpg"}").also { f ->
            FileOutputStream(f).use {
                tableImage.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
            }
        }
    }

    // ---- Share / save ---------------------------------------------------------------------------

    /** Copies [files] into Pictures/DocScan (images) or Downloads/DocScan (everything else). Returns count saved. */
    suspend fun saveToDevice(files: List<File>, mime: String): Int = withContext(Dispatchers.IO) {
        val isImage = mime.startsWith("image/")
        val collection = if (isImage) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        var saved = 0
        for (file in files) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/DocScan")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values) ?: continue
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Could not open output stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                saved++
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
            }
        }
        saved
    }

    fun clearCache() {
        dir.listFiles()?.forEach { com.docscan.app.security.SecureFiles.wipe(it) }
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Scan" }

    private companion object {
        const val EXPORT_SIDE = 2400
        const val OCR_SIDE = 2200
    }
}

/** Opens the system share sheet for exported files. */
object ShareHelper {
    fun share(context: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "Share")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
