package com.docscan.app.core.word

import kotlin.math.abs

/** One recognised line of text with its box in page pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val runs: List<Run> = emptyList()) {
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

    /** A drawn line or box from the PDF, in page pixels; [shape] carries size and colours, the offsets are set here. */
    data class OcrShape(val left: Int, val top: Int, val right: Int, val bottom: Int, val shape: ShapeBlock)

    fun page(
        blocks: List<OcrBlockIn>, tables: List<OcrTable>, pageWidth: Int, contentWidthTwips: Int,
        extra: List<Pair<Int, Block>> = emptyList(),
        shapes: List<OcrShape> = emptyList(), twipsPerPx: Float = 0f, marginLeftTwips: Int = 0, marginTopTwips: Int = 0,
    ): List<Block> {
        val text = blocks.filter { b -> b.lines.any { it.text.isNotBlank() } }
            .filter { b -> tables.none { insideTable(b, it) } }
        val allLines = text.flatMap { it.lines }.filter { it.text.isNotBlank() }
        val body = if (allLines.isEmpty()) 1f else median(allLines.map { it.height.toFloat() })
        val bodyLeft = if (text.isEmpty()) 0 else text.map { it.left }.sorted()[text.size / 4]
        val ptPerPx = twipsPerPx / 20f
        val keepSpacing = twipsPerPx > 0f

        class Item(val top: Int, val bottom: Int, val blocks: List<Block>, val floating: Boolean = false, val shape: OcrShape? = null)
        val items = ArrayList<Item>()
        text.forEachIndexed { i, b ->
            var paras = blockToParagraphs(b, body, pageWidth, bodyLeft, firstOnPage = i == 0).map { Block.Para(it) }
            if (keepSpacing && paras.isNotEmpty()) {
                val ls = b.lines.filter { it.text.isNotBlank() }
                val mult = if (ls.size >= 2) {
                    val pitch = (ls.last().top - ls.first().top).toFloat() / (ls.size - 1)
                    val h = ls.map { it.height }.average().toFloat()
                    (pitch / (1.2f * h)).coerceIn(0.8f, 2.5f).let { Math.round(it * 20f) / 20f }.let { if (it in 0.95f..1.1f) null else it }
                } else null
                paras = paras.map { Block.Para(it.paragraph.copy(spaceAfterPt = 0f, lineSpacing = mult ?: it.paragraph.lineSpacing)) }
            }
            items += Item(b.top, b.bottom, paras)
        }
        for (t in tables) items += Item(t.top, t.bottom, listOfNotNull(tableBlock(t, contentWidthTwips)))
        for ((top, b) in extra) {
            val h = if (b is Block.Image && twipsPerPx > 0f) (b.image.heightTwips / twipsPerPx).toInt() else 0
            items += Item(top, top + h, listOf(b))
        }
        for (sh in shapes) items += Item(sh.top, sh.bottom, emptyList(), floating = true, shape = sh)
        val ordered = items.sortedBy { it.top }

        // Paragraph gaps as space before; floating shapes keep their place relative to the content above them.
        val out = ArrayList<Block>()
        var prevBottom: Int? = null
        val topPx = if (keepSpacing) (marginTopTwips / twipsPerPx).toInt() else 0
        for (it in ordered) {
            if (it.floating) {
                val s = it.shape!!
                val anchorY = prevBottom ?: 0
                out += Block.Shape(s.shape.copy(
                    offsetXTwips = Math.round((s.left * twipsPerPx) - marginLeftTwips).coerceAtLeast(0),
                    offsetYTwips = Math.round((s.top - anchorY) * twipsPerPx),
                ))
                continue
            }
            var blocks = it.blocks
            if (keepSpacing && prevBottom != null) {
                val first = blocks.firstOrNull()
                if (first is Block.Para && first.paragraph.spaceAfterPt == 0f) {
                    val gapPt = (it.top - prevBottom) * ptPerPx
                    val pt = if (gapPt > 3f) (gapPt - 2f).coerceAtMost(400f) else 0f
                    blocks = listOf(Block.Para(first.paragraph.copy(spaceBeforePt = pt))) + blocks.drop(1)
                }
            } else if (keepSpacing) {
                // The first line of the page keeps its distance from the top margin (room left for a letterhead).
                val first = blocks.firstOrNull()
                if (first is Block.Para && first.paragraph.spaceAfterPt == 0f) {
                    val pt = ((it.top - topPx) * ptPerPx).coerceIn(0f, 400f)
                    blocks = listOf(Block.Para(first.paragraph.copy(spaceBeforePt = if (pt > 3f) pt else 0f))) + blocks.drop(1)
                }
            }
            out += blocks
            prevBottom = it.bottom
        }
        return out
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
        // Text with its own look (PDF fonts): keep bold, italic, size and font run by run.
        val styled = if (lines.all { it.runs.isNotEmpty() }) joinRuns(lines) else null
        val style = when {
            lines.size <= 3 && content.length <= 90 && ratio >= 2.0f && firstOnPage -> ParaStyle.TITLE
            lines.size <= 3 && content.length <= 90 && ratio >= 1.6f -> ParaStyle.H1
            lines.size <= 3 && content.length <= 90 && ratio >= 1.3f -> ParaStyle.H2
            else -> ParaStyle.NORMAL
        }
        val indent = if (style == ParaStyle.NORMAL && align == Align.LEFT && b.left - bodyLeft > pageW * 0.04f) {
            ((b.left - bodyLeft).toFloat() / pageW * 8000).toInt().coerceIn(0, 2880) / 360 * 360
        } else 0
        val outRuns = if (styled != null && style == ParaStyle.NORMAL) styled else if (styled != null) listOf(Run(content, styled.firstOrNull()?.style?.copy(sizePt = null) ?: RunStyle())) else listOf(Run(content))
        return listOf(Paragraph(outRuns, style, if (style == ParaStyle.NORMAL || align == Align.CENTER) align else Align.LEFT, indentLeftTwips = indent))
    }

    /** The lines' styled runs as one paragraph, joining a hyphenated word break and otherwise separating lines by a space. */
    private fun joinRuns(lines: List<OcrLine>): List<Run> {
        val out = ArrayList<Run>()
        fun add(r: Run) {
            val last = out.lastOrNull()
            if (last != null && last.style == r.style) out[out.size - 1] = Run(last.text + r.text, r.style) else out += r
        }
        for (l in lines) {
            val first = l.runs.first()
            val lastRun = out.lastOrNull()
            if (lastRun != null) {
                val t = lastRun.text
                if (t.endsWith("-") && t.length >= 2 && t[t.length - 2].isLetter() && first.text.firstOrNull()?.isLowerCase() == true) {
                    out[out.size - 1] = Run(t.dropLast(1), lastRun.style)
                } else add(Run(" ", lastRun.style))
            }
            l.runs.forEach { add(it) }
        }
        return out.filter { it.text.isNotEmpty() }
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
