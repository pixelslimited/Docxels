package com.docscan.app.ui

import com.docscan.app.core.readUpTo
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.docscan.app.AppContainer
import com.docscan.app.core.Geometry
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.WordDoc
import com.docscan.app.data.ImageIO
import com.docscan.app.imaging.EdgeDetector
import com.docscan.app.pdf.PdfTools
import com.docscan.app.pdf.copyStreamToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A file another app handed to Docxels ("Open with" or "Share to"). It is copied into the library so it stays saved:
 * PDFs go to the PDF list, Word files and text files become editable documents, pictures become scans.
 */
object Incoming {
    private const val MAX_BYTES = 50L * 1024 * 1024
    private const val MAX_PDF_BYTES = 150L * 1024 * 1024

    private fun nameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "Document"

    /** Returns the screen to open ("write/ID", "pdfdoc/ID", "doc/ID") or null with a [say]ing why not. */
    suspend fun import(context: Context, container: AppContainer, uri: Uri, say: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val name = nameOf(context, uri)
        val lower = name.lowercase()
        val mime = (context.contentResolver.getType(uri) ?: "").lowercase()
        val base = name.substringBeforeLast('.', name).ifBlank { "Imported" }
        try {
            when {
                mime == "application/pdf" || lower.endsWith(".pdf") -> {
                    val tmp = File.createTempFile("incoming", ".pdf", context.cacheDir)
                    try {
                        val ok = context.contentResolver.openInputStream(uri)?.use { copyStreamToFile(it, tmp, MAX_PDF_BYTES) } ?: false
                        if (!ok) { say("That PDF is too large or could not be read."); return@withContext null }
                        val pages = runCatching { PdfTools(context).pageCount(tmp) }.getOrDefault(0)
                        "pdfdoc/" + container.pdfs.add(tmp, base, pages)
                    } finally { com.docscan.app.security.SecureFiles.wipe(tmp) }
                }
                lower.endsWith(".docx") || mime.contains("wordprocessingml") -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(MAX_BYTES.toInt() + 1) } ?: error("unreadable")
                    if (bytes.size > MAX_BYTES) { say("That file is too large to import."); return@withContext null }
                    "write/" + container.words.importDocx(bytes, base)
                }
                mime.startsWith("text/") || lower.endsWith(".txt") -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(2_000_000) } ?: error("unreadable")
                    val paragraphs = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").split('\n').map { Paragraph(listOf(Run(it)), align = Align.LEFT) }
                    "write/" + container.words.add(WordDoc(title = base, blocks = paragraphs.map { com.docscan.app.core.word.Block.Para(it) }.ifEmpty { listOf(com.docscan.app.core.word.Block.Para(Paragraph())) }))
                }
                mime.startsWith("image/") -> {
                    val bitmap = ImageIO.decodeUri(context, uri, 3000) ?: run { say("Could not read that picture."); return@withContext null }
                    val corners = EdgeDetector.detect(bitmap) ?: Geometry.DefaultCorners
                    val docId = container.repository.createDocument()
                    container.repository.addPage(docId, bitmap, corners)
                    bitmap.recycle()
                    "doc/$docId"
                }
                else -> { say("Docxels can open PDF, Word (.docx), text and picture files."); null }
            }
        } catch (e: Exception) {
            say("That file could not be opened.")
            null
        }
    }
}
