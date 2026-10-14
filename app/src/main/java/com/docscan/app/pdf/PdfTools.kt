package com.docscan.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream

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
