package com.docscan.app.core.word

import kotlin.math.abs

/** One glyph of embedded PDF text, in page pixels: [x] left edge, [baseline] y of the baseline, [w] advance width, [size] font size. */
data class Glyph(val ch: String, val x: Float, val baseline: Float, val w: Float, val size: Float)

/** A run of text on one line with no large gap inside it (so table cells and columns stay apart). */
data class TextSeg(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val size: Float) {
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2
}

/**
 * Turns the text a PDF carries (not pixels) into the line model that [OcrLayout] already understands, so a PDF with
 * real text converts exactly, with no recognition errors. Pure Kotlin.
 */
object PdfLayout {
    /** Splits one line of glyphs where a gap wider than ~1.6 em appears (columns, table cells). */
    fun segments(line: List<Glyph>): List<TextSeg> {
        val out = ArrayList<TextSeg>()
        var cur = StringBuilder()
        var first: Glyph? = null
        var last: Glyph? = null
        var maxSize = 0f
        fun flush() {
            val f = first ?: return
            val l = last ?: return
            val text = cur.toString().trim()
            if (text.isNotEmpty()) {
                val fs = maxSize.coerceAtLeast(1f)
                out += TextSeg(text, f.x.toInt(), (f.baseline - 0.85f * fs).toInt(), (l.x + l.w).toInt(), (f.baseline + 0.25f * fs).toInt(), fs)
            }
            cur = StringBuilder(); first = null; last = null; maxSize = 0f
        }
        for (g in line) {
            val prev = last
            if (prev != null) {
                val gap = g.x - (prev.x + prev.w)
                if (gap > 1.6f * maxOf(g.size, prev.size)) flush()
                else if (gap > 0.18f * g.size && !cur.endsWith(" ") && g.ch != " ") cur.append(' ')
            }
            if (first == null) first = g
            cur.append(g.ch)
            last = g
            maxSize = maxOf(maxSize, g.size)
        }
        flush()
        return out
    }

    /** Groups segments into blocks (paragraph-like stacks of lines) in reading order. */
    fun blocks(segs: List<TextSeg>): List<OcrBlockIn> {
        class B(val lines: MutableList<TextSeg>, var left: Int, var right: Int)
        val open = ArrayList<B>()
        for (s in segs.sortedWith(compareBy({ it.top }, { it.left }))) {
            val home = open.lastOrNull { b ->
                val l = b.lines.last()
                val gap = s.top - l.bottom
                s.top > l.top + 0.5f * l.size &&
                    gap < 0.45f * l.size && gap > -0.6f * l.size &&
                    abs(s.size - l.size) <= 0.2f * l.size &&
                    s.right > b.left && s.left < b.right
            }
            if (home == null) open += B(mutableListOf(s), s.left, s.right)
            else {
                home.lines += s
                home.left = minOf(home.left, s.left)
                home.right = maxOf(home.right, s.right)
            }
        }
        return open.map { b -> OcrBlockIn(b.lines.map { OcrLine(it.text, it.left, it.top, it.right, it.bottom) }) }
    }

    /** Text of every segment whose centre lies inside the cell, top-to-bottom then left-to-right. */
    fun cellText(segs: List<TextSeg>, x: Int, y: Int, w: Int, h: Int): String =
        segs.filter { it.cx in x until x + w && it.cy in y until y + h }
            .sortedWith(compareBy({ it.top / 4 }, { it.left }))
            .joinToString(" ") { it.text }
            .trim()
}
