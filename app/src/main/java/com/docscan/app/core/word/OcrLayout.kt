package com.docscan.app.core.word

import kotlin.math.abs

/** One recognised line of text with its box in page pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** A group of lines the recogniser considers one block. */
data class OcrBlockIn(val lines: List<OcrLine>) {
    val left: Int get() = lines.minOf { it.left }
    val right: Int get() = lines.maxOf { it.right }
    val top: Int get() = lines.minOf { it.top }
    val bottom: Int get() = lines.maxOf { it.bottom }
}

/** A table found on the page, already read: its box, the text of each cell, and relative column widths. */
data class OcrTable(
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val rows: List<List<String>>,
    val columnFractions: List<Float>? = null,
)

/**
 * Turns OCR output into editable document blocks: headings from relatively large text, bullets and numbered
 * items, centred or right-aligned lines, hyphenated line breaks joined, and tables placed in reading order.
 * Pure Kotlin so it can be tested without a phone. Bold / italic are not available from OCR and stay plain.
 */
object OcrLayout {
    private val BULLET = Regex("""^\s*([•·▪◦●○■\-–—*])\s+(.*)$""")
    private val NUMBER = Regex("""^\s*(\d{1,3}|[a-zA-Z])[.)]\s+(.*)$""")

    fun page(blocks: List<OcrBlockIn>, tables: List<OcrTable>, pageWidth: Int, contentWidthTwips: Int): List<Block> {
        val text = blocks.filter { b -> b.lines.any { it.text.isNotBlank() } }
            .filter { b -> tables.none { insideTable(b, it) } }
        val allLines = text.flatMap { it.lines }.filter { it.text.isNotBlank() }
        val body = if (allLines.isEmpty()) 1f else median(allLines.map { it.height.toFloat() })
        val bodyLeft = if (text.isEmpty()) 0 else text.map { it.left }.sorted()[text.size / 4]

        class Item(val top: Int, val blocks: List<Block>)
        val items = ArrayList<Item>()
        text.forEachIndexed { i, b -> items += Item(b.top, blockToParagraphs(b, body, pageWidth, bodyLeft, firstOnPage = i == 0).map { Block.Para(it) }) }
        for (t in tables) items += Item(t.top, listOfNotNull(tableBlock(t, contentWidthTwips)))
        return items.sortedBy { it.top }.flatMap { it.blocks }
    }

    private fun insideTable(b: OcrBlockIn, t: OcrTable): Boolean {
        val cx = (b.left + b.right) / 2
        val cy = (b.top + b.bottom) / 2
        return cx in t.left..t.right && cy in t.top..t.bottom
    }

    private fun blockToParagraphs(b: OcrBlockIn, body: Float, pageW: Int, bodyLeft: Int, firstOnPage: Boolean): List<Paragraph> {
        val lines = b.lines.filter { it.text.isNotBlank() }
        val avgH = lines.map { it.height }.average().toFloat()
        val ratio = avgH / body
        val width = b.right - b.left
        val centred = abs((b.left + b.right) / 2 - pageW / 2) < pageW * 0.06f && width < pageW * 0.7f
        val rightAligned = !centred && b.left > pageW * 0.4f && b.right > pageW * 0.85f && width < pageW * 0.5f
        val align = when {
            centred -> Align.CENTER
            rightAligned -> Align.RIGHT
            else -> Align.LEFT
        }

        // Lists: every line that starts with a marker begins an item; other lines continue the previous item.
        val firstMarker = lines.first().text.let { BULLET.matches(it) || NUMBER.matches(it) }
        if (firstMarker && lines.count { BULLET.matches(it.text) || NUMBER.matches(it.text) } >= 1) {
            val out = ArrayList<Paragraph>()
            var cur: StringBuilder? = null
            var kind = ListKind.BULLET
            fun flush() {
                cur?.let { out += Paragraph(listOf(Run(it.toString().trim())), list = kind) }
                cur = null
            }
            for (l in lines) {
                val bm = BULLET.matchEntire(l.text)
                val nm = if (bm == null) NUMBER.matchEntire(l.text) else null
                if (bm != null || nm != null) {
                    flush()
                    kind = if (bm != null) ListKind.BULLET else ListKind.NUMBER
                    cur = StringBuilder((bm ?: nm)!!.groupValues[2])
                } else {
                    cur?.let { join(it, l.text) } ?: run { cur = StringBuilder(l.text) }
                }
            }
            flush()
            return out
        }

        val sb = StringBuilder()
        for (l in lines) join(sb, l.text)
        val content = sb.toString().trim()
        val style = when {
            lines.size <= 3 && content.length <= 90 && ratio >= 2.0f && firstOnPage -> ParaStyle.TITLE
            lines.size <= 3 && content.length <= 90 && ratio >= 1.6f -> ParaStyle.H1
            lines.size <= 3 && content.length <= 90 && ratio >= 1.3f -> ParaStyle.H2
            else -> ParaStyle.NORMAL
        }
        val indent = if (style == ParaStyle.NORMAL && align == Align.LEFT && b.left - bodyLeft > pageW * 0.04f) {
            ((b.left - bodyLeft).toFloat() / pageW * 8000).toInt().coerceIn(0, 2880) / 360 * 360
        } else 0
        return listOf(Paragraph(listOf(Run(content)), style, if (style == ParaStyle.NORMAL || align == Align.CENTER) align else Align.LEFT, indentLeftTwips = indent))
    }

    /** Appends a line, joining a word split by a hyphen at the line end. */
    private fun join(sb: StringBuilder, line: String) {
        val t = line.trim()
        if (t.isEmpty()) return
        if (sb.isEmpty()) {
            sb.append(t)
        } else if (sb.endsWith("-") && sb.length >= 2 && sb[sb.length - 2].isLetter() && t.first().isLowerCase()) {
            sb.setLength(sb.length - 1)
            sb.append(t)
        } else {
            sb.append(' ').append(t)
        }
    }

    private fun tableBlock(t: OcrTable, contentWidthTwips: Int): Block.Table? {
        val cols = t.rows.maxOfOrNull { it.size } ?: return null
        if (cols == 0) return null
        val fractions = t.columnFractions?.takeIf { it.size == cols && it.all { f -> f > 0f } } ?: List(cols) { 1f / cols }
        val sum = fractions.sum()
        val widths = fractions.map { (contentWidthTwips * it / sum).toInt().coerceAtLeast(360) }
        val rows = t.rows.map { r ->
            List(cols) { c ->
                val cell = r.getOrNull(c).orEmpty().trim()
                TableCell(listOf(Paragraph(if (cell.isEmpty()) emptyList() else listOf(Run(cell)))))
            }
        }
        return Block.Table(TableBlock(rows, widths))
    }

    private fun median(values: List<Float>): Float {
        val s = values.sorted()
        return s[s.size / 2]
    }
}
