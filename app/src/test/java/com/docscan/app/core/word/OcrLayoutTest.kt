package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLayoutTest {
    private val pageW = 1000
    private val twips = 9000

    private fun line(t: String, l: Int, top: Int, r: Int, h: Int = 20) = OcrLine(t, l, top, r, top + h)
    private fun block(vararg lines: OcrLine) = OcrBlockIn(lines.toList())
    private fun paras(b: List<Block>) = b.filterIsInstance<Block.Para>().map { it.paragraph }

    @Test fun bigCentredTextBecomesTitleThenBody() {
        val blocks = listOf(
            block(line("ANNUAL REPORT", 300, 50, 700, 48)),
            block(line("This is a normal paragraph of body text", 100, 200, 900), line("that continues on the next line.", 100, 225, 600)),
            block(line("Another paragraph here", 100, 300, 500)),
            block(line("And one more line of text", 100, 340, 560)),
        )
        val p = paras(OcrLayout.page(blocks, emptyList(), pageW, twips))
        assertEquals(ParaStyle.TITLE, p[0].style)
        assertEquals(Align.CENTER, p[0].align)
        assertEquals(ParaStyle.NORMAL, p[1].style)
        assertEquals("This is a normal paragraph of body text that continues on the next line.", p[1].text)
    }

    @Test fun bulletsAndNumbersBecomeLists() {
        val blocks = listOf(
            block(line("• first item", 100, 100, 400), line("• second item", 100, 125, 400)),
            block(line("1. step one", 100, 200, 400), line("2. step two", 100, 225, 400), line("wrapped words", 120, 250, 400)),
            block(line("filler paragraph", 100, 300, 500)),
        )
        val p = paras(OcrLayout.page(blocks, emptyList(), pageW, twips))
        assertEquals(listOf(ListKind.BULLET, ListKind.BULLET, ListKind.NUMBER, ListKind.NUMBER, ListKind.NONE), p.map { it.list })
        assertEquals("first item", p[0].text)
        assertEquals("step two wrapped words", p[3].text)
    }

    @Test fun hyphenatedWordsAreJoined() {
        val blocks = listOf(
            block(line("The informa-", 100, 100, 500), line("tion is joined", 100, 125, 500)),
            block(line("x", 100, 300, 200)),
        )
        assertEquals("The information is joined", paras(OcrLayout.page(blocks, emptyList(), pageW, twips))[0].text)
    }

    @Test fun tableIsPlacedInReadingOrderAndItsTextIsNotDuplicated() {
        val table = OcrTable(100, 300, 900, 500, listOf(listOf("Item", "Qty"), listOf("Pen", "2")))
        val blocks = listOf(
            block(line("Before the table", 100, 100, 500)),
            block(line("Item", 120, 310, 200), line("Pen", 120, 400, 200)),
            block(line("After the table", 100, 600, 500)),
        )
        val out = OcrLayout.page(blocks, listOf(table), pageW, twips)
        assertEquals(3, out.size)
        assertTrue(out[1] is Block.Table)
        val t = (out[1] as Block.Table).table
        assertEquals(2, t.columnCount)
        assertEquals("Pen", t.rows[1][0].paragraphs[0].text)
        assertEquals("After the table", (out[2] as Block.Para).paragraph.text)
    }

    @Test fun emptyPageGivesNothing() {
        assertTrue(OcrLayout.page(emptyList(), emptyList(), pageW, twips).isEmpty())
    }
}

class OcrLayoutShapesTest {
    private fun line(t: String, top: Int) = OcrLine(t, 100, top, 400, top + 20)

    @org.junit.Test
    fun gapsBecomeSpaceBeforeAndShapesFloatBehind() {
        val blocks = listOf(OcrBlockIn(listOf(line("First paragraph", 100))), OcrBlockIn(listOf(line("Second paragraph", 300))))
        val rule = OcrLayout.OcrShape(100, 200, 500, 202, ShapeBlock("rect", 8000, 40, 0, 0, 0.25f, wrap = ImageWrap.BEHIND))
        val out = OcrLayout.page(blocks, emptyList(), 1000, 9000, shapes = listOf(rule), twipsPerPx = 12f, marginLeftTwips = 1000)
        val paras = out.filterIsInstance<Block.Para>()
        org.junit.Assert.assertEquals(2, paras.size)
        org.junit.Assert.assertTrue((paras[1].paragraph.spaceBeforePt ?: 0f) > 10f)
        val shape = out.filterIsInstance<Block.Shape>().single().shape
        org.junit.Assert.assertEquals(200, shape.offsetXTwips)
        org.junit.Assert.assertEquals(ImageWrap.BEHIND, shape.wrap)
        org.junit.Assert.assertTrue(out.indexOf(Block.Shape(shape)) in 1 until out.size)
    }
}
