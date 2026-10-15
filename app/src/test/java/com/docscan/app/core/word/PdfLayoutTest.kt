package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {
    private fun line(text: String, x: Float, base: Float, size: Float = 12f, adv: Float = 6f): List<Glyph> =
        text.mapIndexed { i, c -> Glyph(c.toString(), x + i * adv, base, adv, size) }

    @Test fun wordsGetSpacesFromGaps() {
        val g = line("Hello", 10f, 50f) + line("world", 10f + 5 * 6f + 3f, 50f)
        val segs = PdfLayout.segments(g)
        assertEquals(1, segs.size)
        assertEquals("Hello world", segs[0].text)
    }

    @Test fun bigGapSplitsColumns() {
        val g = line("Name", 10f, 50f) + line("Qty", 300f, 50f)
        assertEquals(listOf("Name", "Qty"), PdfLayout.segments(g).map { it.text })
    }

    @Test fun linesStackIntoOneParagraphAndGapStartsNew() {
        val segs = PdfLayout.segments(line("first line of text", 10f, 50f)) +
            PdfLayout.segments(line("second line here", 10f, 64f)) +
            PdfLayout.segments(line("new paragraph", 10f, 100f))
        val blocks = PdfLayout.blocks(segs)
        assertEquals(2, blocks.size)
        assertEquals(2, blocks[0].lines.size)
    }

    @Test fun sideBySideColumnsStaySeparate() {
        val segs = PdfLayout.segments(line("left column", 10f, 50f)) + PdfLayout.segments(line("right column", 300f, 50f)) +
            PdfLayout.segments(line("left two", 10f, 64f)) + PdfLayout.segments(line("right two", 300f, 64f))
        val blocks = PdfLayout.blocks(segs)
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.lines.size == 2 })
    }

    @Test fun cellTextCollectsSegmentsInsideRect() {
        val segs = PdfLayout.segments(line("Apple", 12f, 30f)) + PdfLayout.segments(line("5", 212f, 30f))
        assertEquals("Apple", PdfLayout.cellText(segs, 0, 0, 100, 50))
        assertEquals("5", PdfLayout.cellText(segs, 200, 0, 100, 50))
    }

    @Test fun largeTextBecomesHeadingThroughOcrLayout() {
        val segs = PdfLayout.segments(line("Annual Report", 10f, 40f, size = 28f, adv = 14f)) +
            PdfLayout.segments(line("Body text paragraph one", 10f, 90f)) +
            PdfLayout.segments(line("continues here", 10f, 104f)) +
            PdfLayout.segments(line("More body text below", 10f, 150f))
        val out = OcrLayout.page(PdfLayout.blocks(segs), emptyList(), 600, 9000)
        val first = (out.first() as Block.Para).paragraph
        assertTrue(first.style != ParaStyle.NORMAL)
    }

    @Test fun boldWordKeepsItsOwnRunThroughToTheParagraph() {
        val bold = RunStyle(bold = true, sizePt = 12f)
        val plain = RunStyle(sizePt = 12f)
        val g = "Dear ".mapIndexed { i, c -> Glyph(c.toString(), 10f + i * 6f, 50f, 6f, 12f, plain) } +
            "Sir".mapIndexed { i, c -> Glyph(c.toString(), 40f + i * 6f, 50f, 6f, 12f, bold) }
        val segs = PdfLayout.segments(g)
        assertEquals("Dear Sir", segs[0].text)
        assertEquals(listOf("Dear ", "Sir"), segs[0].runs.map { it.text })
        val blocks = OcrLayout.page(PdfLayout.blocks(segs), emptyList(), 600, 9000)
        val para = (blocks.single() as Block.Para).paragraph
        assertEquals(true, para.runs.last().style.bold)
        assertEquals("Dear Sir", para.runs.joinToString("") { it.text })
    }
}
