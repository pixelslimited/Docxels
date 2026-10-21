package com.docscan.app.pdf

import android.content.Context
import com.docscan.app.core.PhoneNumbers
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds the phone numbers in a PDF's own text, with where each one sits on its page, so the reader can underline
 * them and dial on a tap. Works on PDFs that carry real text; a scanned picture has no text to read.
 */
class PdfPhones(private val context: Context, private val file: File) : AutoCloseable {
    /** A phone number and its box on the page, as fractions (0..1) of the page width and height, top-left origin. */
    class Box(val number: String, val x: Float, val y: Float, val w: Float, val h: Float)

    private val lock = Mutex()
    private var doc: PDDocument? = null
    private var failed = false
    private val cache = HashMap<Int, List<Box>>()

    suspend fun page(index: Int): List<Box> = lock.withLock {
        cache[index]?.let { return@withLock it }
        val found = withContext(Dispatchers.IO) { runCatching { extract(index) }.getOrDefault(emptyList()) }
        cache[index] = found
        found
    }

    private fun open(): PDDocument? {
        if (failed) return null
        doc?.let { return it }
        return runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file, "")
        }.onFailure { failed = true }.getOrNull()?.also { doc = it }
    }

    private fun extract(index: Int): List<Box> {
        val d = open() ?: return emptyList()
        if (index !in 0 until d.numberOfPages) return emptyList()
        val (pw, ph) = dims(d.getPage(index))
        if (pw <= 0f || ph <= 0f) return emptyList()
        val out = ArrayList<Box>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                // One position per character; if a font merged letters (ligatures) the mapping is unsafe, so skip the line.
                if (textPositions.size != text.length) return
                for (f in PhoneNumbers.all(text)) {
                    val ps = textPositions.subList(f.start, f.end)
                    if (ps.isEmpty()) continue
                    val left = ps.minOf { it.xDirAdj }
                    val right = ps.maxOf { it.xDirAdj + it.widthDirAdj }
                    val size = ps.maxOf { it.fontSizeInPt }.coerceAtLeast(4f)
                    val base = ps.maxOf { it.yDirAdj }
                    val top = base - size * 0.85f
                    val bottom = base + size * 0.22f
                    out += Box(f.number, (left / pw).coerceIn(0f, 1f), (top / ph).coerceIn(0f, 1f), ((right - left) / pw).coerceIn(0f, 1f), ((bottom - top) / ph).coerceIn(0f, 1f))
                }
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(d)
        return out
    }

    private val textPages = HashMap<Int, Boolean>()

    /** True when the page has a text layer (a scanned page has none). */
    suspend fun hasText(index: Int): Boolean = lock.withLock {
        textPages[index]?.let { return@withLock it }
        val has = withContext(Dispatchers.IO) {
            runCatching {
                val d = open() ?: return@runCatching false
                if (index !in 0 until d.numberOfPages) return@runCatching false
                val s = PDFTextStripper()
                s.startPage = index + 1
                s.endPage = index + 1
                s.getText(d).any { !it.isWhitespace() }
            }.getOrDefault(false)
        }
        textPages[index] = has
        has
    }

    private val searchCache = HashMap<String, List<Box>>()

    /** Where [query] (case-insensitive) appears on page [index]; empty when the page has no text layer (scans). */
    suspend fun find(index: Int, query: String): List<Box> = lock.withLock {
        val key = index.toString() + "|" + query.lowercase()
        searchCache[key]?.let { return@withLock it }
        val found = withContext(Dispatchers.IO) { runCatching { findIn(index, query) }.getOrDefault(emptyList()) }
        if (searchCache.size > 400) searchCache.clear()
        searchCache[key] = found
        found
    }

    private fun findIn(index: Int, query: String): List<Box> {
        val q = query.lowercase()
        if (q.isEmpty()) return emptyList()
        val d = open() ?: return emptyList()
        if (index !in 0 until d.numberOfPages) return emptyList()
        val (pw, ph) = dims(d.getPage(index))
        if (pw <= 0f || ph <= 0f) return emptyList()
        val out = ArrayList<Box>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val lower = text.lowercase()
                // If a font merged letters (ligatures) the characters cannot be mapped one to one: mark the whole line instead.
                val exact = textPositions.size == text.length && lower.length == text.length
                var from = 0
                while (true) {
                    val at = lower.indexOf(q, from)
                    if (at < 0) break
                    val ps = if (exact) textPositions.subList(at, at + q.length) else textPositions
                    if (ps.isNotEmpty()) {
                        val left = ps.minOf { it.xDirAdj }
                        val right = ps.maxOf { it.xDirAdj + it.widthDirAdj }
                        val size = ps.maxOf { it.fontSizeInPt }.coerceAtLeast(4f)
                        val base = ps.maxOf { it.yDirAdj }
                        val top = base - size * 0.85f
                        val bottom = base + size * 0.22f
                        out += Box(query, (left / pw).coerceIn(0f, 1f), (top / ph).coerceIn(0f, 1f), ((right - left) / pw).coerceIn(0f, 1f), ((bottom - top) / ph).coerceIn(0f, 1f))
                    }
                    if (!exact) break
                    from = at + q.length
                }
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.getText(d)
        return out
    }

    private fun dims(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox ?: page.mediaBox
        val rot = ((page.rotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) box.height to box.width else box.width to box.height
    }

    override fun close() {
        runCatching { doc?.close() }
        doc = null
    }
}
