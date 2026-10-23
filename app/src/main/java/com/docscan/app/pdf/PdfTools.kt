package com.docscan.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Something to put on a PDF page. Positions are fractions of the page as it is shown: [left] and [top] of the page width and
 * height, [size] of the page width (the picture's width, or the text's font size).
 */
class PdfMark(
    val page: Int,
    val kind: Kind,
    val left: Float,
    val top: Float,
    val size: Float,
    val text: String = "",
    val image: ByteArray? = null,
    /** HIGHLIGHT: height as a fraction of the page height. */
    val height: Float = 0f,
    /** INK: the pen path as (x, y) fractions of the page. */
    val points: List<Pair<Float, Float>> = emptyList(),
) {
    enum class Kind { TEXT, CHECK, IMAGE, HIGHLIGHT, INK }
}

/** Thrown when a PDF needs a password that was not given or was wrong. */
class PdfPasswordException : Exception("This PDF is password protected")

/** A parsed page range such as "1-3, 5, 8-" (1-based, inclusive). */
object PageRanges {
    /** Returns zero-based page indexes in the order written, or null when the text is invalid. */
    fun parse(text: String, pageCount: Int): List<Int>? {
        if (pageCount <= 0) return null
        val out = ArrayList<Int>()
        for (raw in text.split(',')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val bits = part.split('-').map { it.trim() }
            when (bits.size) {
                1 -> {
                    val n = bits[0].toIntOrNull() ?: return null
                    if (n !in 1..pageCount) return null
                    out += n - 1
                }
                2 -> {
                    val a = if (bits[0].isEmpty()) 1 else bits[0].toIntOrNull() ?: return null
                    val b = if (bits[1].isEmpty()) pageCount else bits[1].toIntOrNull() ?: return null
                    if (a !in 1..pageCount || b !in 1..pageCount || a > b) return null
                    for (i in a..b) out += i - 1
                }
                else -> return null
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }
}

/**
 * Lossless PDF operations: pages are copied between documents, never re-drawn, so text stays text and
 * quality does not drop. Everything runs on the phone.
 */
class PdfTools(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private fun load(file: File, password: String? = null): PDDocument = try {
        PDDocument.load(file, password ?: "")
    } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        throw PdfPasswordException()
    }

    fun pageCount(file: File, password: String? = null): Int = load(file, password).use { it.numberOfPages }

    fun isEncrypted(file: File): Boolean = try {
        PDDocument.load(file, "").use { it.isEncrypted }
    } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        true
    }

    fun merge(inputs: List<File>, output: File) {
        require(inputs.size >= 2) { "Choose at least two PDFs" }
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
    }

    /** Writes the given zero-based pages, in the given order, to [output]. Used for split, extract, reorder and delete. */
    fun pick(input: File, pages: List<Int>, output: File, password: String? = null) {
        require(pages.isNotEmpty()) { "No pages chosen" }
        load(input, password).use { src ->
            PDDocument().use { dst ->
                for (i in pages) {
                    require(i in 0 until src.numberOfPages) { "Page out of range" }
                    dst.importPage(src.getPage(i))
                }
                dst.save(output)
            }
        }
    }

    /** Splits into consecutive chunks of [size] pages; returns the files written into [outDir]. */
    fun splitEvery(input: File, size: Int, outDir: File, baseName: String): List<File> {
        require(size >= 1) { "Size must be at least 1" }
        val count = pageCount(input)
        val files = ArrayList<File>()
        var start = 0
        var part = 1
        while (start < count) {
            val end = minOf(count, start + size)
            val f = File(outDir, "$baseName-part$part.pdf")
            pick(input, (start until end).toList(), f)
            files += f
            start = end
            part++
        }
        return files
    }

    /** Adds [degrees] (multiple of 90) to the rotation of the given pages. */
    fun rotate(input: File, pages: List<Int>, degrees: Int, output: File) {
        require(degrees % 90 == 0) { "Rotation must be a multiple of 90" }
        load(input).use { doc ->
            for (i in pages) {
                val page = doc.getPage(i)
                page.rotation = (((page.rotation + degrees) % 360) + 360) % 360
            }
            doc.save(output)
        }
    }

    /** Encrypts with AES-256. The owner password is set equal to the user password. */
    fun protect(input: File, password: String, output: File) {
        require(password.length >= 4) { "Use at least 4 characters" }
        load(input).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            doc.save(output)
        }
    }

    /** How hard [compress] squeezes the pictures inside a PDF. */
    enum class Compression(val label: String, val quality: Int, val maxSide: Int) {
        LIGHT("Light (best quality)", 80, 2400),
        MEDIUM("Medium (balanced)", 60, 1700),
        STRONG("Strong (smallest file)", 40, 1200),
        /** Used by Docxels' own PDF export: pages are already sized by the chosen picture size. */
        EXPORT("Export", 70, 2400),
    }

    /**
     * Makes the pictures in a PDF smaller (re-saved as JPEG at lower quality and size). Text, vector drawings and
     * pictures with transparency are left alone. Returns the number of pictures that were replaced.
     */
    fun compress(input: File, level: Compression, output: File, password: String? = null): Int {
        var replaced = 0
        load(input, password).use { doc ->
            val done = HashSet<Any>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                    if (!done.add(x.cosObject)) continue
                    if (x.softMask != null || x.isStencil || x.width * x.height < 40_000) continue
                    val bmp = runCatching { x.image }.getOrNull() ?: continue
                    val scale = minOf(1f, level.maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                    val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
                    val jpeg = runCatching { JPEGFactory.createFromImage(doc, small, level.quality / 100f) }.getOrNull()
                    if (small !== bmp) small.recycle()
                    bmp.recycle()
                    if (jpeg != null) { res.put(name, jpeg); replaced++ }
                }
            }
            doc.save(output)
        }
        return replaced
    }

    /**
     * Draws [marks] (signature pictures, typed text, ticks) onto the pages, keeping everything else as it was. A drawn
     * signature is only a picture: it is not a certified digital signature.
     */
    fun stamp(input: File, marks: List<PdfMark>, output: File, password: String? = null) {
        require(marks.isNotEmpty()) { "Nothing to add yet" }
        load(input, password).use { doc ->
            for ((pageIndex, group) in marks.groupBy { it.page }) {
                require(pageIndex in 0 until doc.numberOfPages) { "Page out of range" }
                val page = doc.getPage(pageIndex)
                require(page.rotation % 360 == 0) { "Page ${pageIndex + 1} is turned. Use Rotate pages to turn it upright first." }
                val box = page.cropBox ?: page.mediaBox
                val w = box.width
                val h = box.height
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (m in group) {
                        val x = box.lowerLeftX + m.left * w
                        val size = m.size * w
                        when (m.kind) {
                            PdfMark.Kind.IMAGE -> {
                                val bytes = m.image ?: continue
                                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                                val ih = size * bmp.height / bmp.width
                                val img = LosslessFactory.createFromImage(doc, bmp)
                                bmp.recycle()
                                cs.drawImage(img, x, box.lowerLeftY + h - m.top * h - ih, size, ih)
                            }
                            PdfMark.Kind.TEXT -> {
                                val line = m.text.map { if (it.code in 32..126 || it.code in 160..255) it else '?' }.joinToString("")
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, size)
                                cs.setNonStrokingColor(0f, 0f, 0f)
                                cs.newLineAtOffset(x, box.lowerLeftY + h - m.top * h - size * 0.8f)
                                cs.showText(line)
                                cs.endText()
                            }
                            PdfMark.Kind.HIGHLIGHT -> {
                                val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                                cs.saveGraphicsState()
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(1f, 0.9f, 0f)
                                val hh = m.height * h
                                cs.addRect(x, box.lowerLeftY + h - m.top * h - hh, size, hh)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }
                            PdfMark.Kind.INK -> {
                                if (m.points.size >= 2) {
                                    cs.saveGraphicsState()
                                    cs.setStrokingColor(0.1f, 0.15f, 0.6f)
                                    cs.setLineWidth(1.6f)
                                    cs.setLineCapStyle(1)
                                    cs.setLineJoinStyle(1)
                                    m.points.forEachIndexed { k, (px, py) ->
                                        val ax = box.lowerLeftX + px * w
                                        val ay = box.lowerLeftY + h - py * h
                                        if (k == 0) cs.moveTo(ax, ay) else cs.lineTo(ax, ay)
                                    }
                                    cs.stroke()
                                    cs.restoreGraphicsState()
                                }
                            }
                            PdfMark.Kind.CHECK -> {
                                cs.beginText()
                                cs.setFont(PDType1Font.ZAPF_DINGBATS, size)
                                cs.setNonStrokingColor(0f, 0f, 0f)
                                cs.newLineAtOffset(x, box.lowerLeftY + h - m.top * h - size * 0.8f)
                                cs.showText("\u2714")
                                cs.endText()
                            }
                        }
                    }
                }
            }
            doc.save(output)
        }
    }

    /** Removes the password from a PDF the user can open. */
    fun unprotect(input: File, password: String, output: File) {
        load(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(output)
        }
    }
}

fun copyStreamToFile(stream: InputStream, target: File, maxBytes: Long): Boolean {
    var total = 0L
    target.outputStream().use { out -> total = copyLimited(stream, out, maxBytes) }
    if (total < 0) {
        target.delete()
        return false
    }
    return true
}

private fun copyLimited(input: InputStream, out: OutputStream, max: Long): Long {
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return -1
        out.write(buf, 0, n)
    }
    return total
}
