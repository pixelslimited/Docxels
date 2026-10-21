package com.docscan.app.pdf

import com.docscan.app.ocr.OcrEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Text search for pages that have no text layer (scans): the page is drawn, read with the phone's own text recogniser
 * (no internet), and the result kept per page so each page is only read once while the PDF is open. Positions are the
 * recogniser's line boxes, narrowed to the part of the line that matches.
 */
class ScannedText(private val preview: PdfPreview, private val ocr: OcrEngine) {
    private class Line(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

    private val lock = Mutex()
    private val cache = HashMap<Int, List<Line>>()

    private suspend fun lines(index: Int): List<Line> = lock.withLock {
        cache[index]?.let { return@withLock it }
        val read = runCatching {
            val bmp = preview.render(index, 1600)
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            ocr.recognize(bmp).textBlocks.flatMap { it.lines }.mapNotNull { l ->
                val r = l.boundingBox ?: return@mapNotNull null
                Line(l.text, r.left / w, r.top / h, r.width() / w, r.height() / h)
            }
        }.getOrNull() ?: return@lock emptyList() // a failure is not remembered, so a later search tries again
        cache[index] = read
        read
    }

    suspend fun find(index: Int, query: String): List<PdfPhones.Box> {
        val q = query.lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<PdfPhones.Box>()
        for (l in lines(index)) {
            val lower = l.text.lowercase()
            if (lower.isEmpty()) continue
            var from = 0
            while (true) {
                val at = lower.indexOf(q, from)
                if (at < 0) break
                val x = l.x + l.w * at / lower.length
                val w = l.w * q.length / lower.length
                out += PdfPhones.Box(query, x.coerceIn(0f, 1f), l.y.coerceIn(0f, 1f), w.coerceIn(0f, 1f), l.h.coerceIn(0f, 1f))
                from = at + q.length
            }
        }
        return out
    }
}
