package com.docscan.app.ui.write

import com.docscan.app.core.word.Block
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.RunStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanReviewTest {
    private fun para(t: String, bold: Boolean = false) = Block.Para(Paragraph(listOf(Run(t, RunStyle(bold = bold)))))

    @Test fun pagesSplitAtPageBreaks() {
        val pages = blocksByPage(listOf(para("a"), para("b"), Block.PageBreak, para("c")))
        assertEquals(listOf(2, 1), pages.map { it.size })
    }

    @Test fun editedParagraphKeepsLookAndOthersAreUntouched() {
        val pages = blocksByPage(listOf(para("Helo", bold = true), para("same"), Block.PageBreak, para("c")))
        val out = applyReviewEdits(pages, mapOf((0 to 0) to "Hello\nWorld"))
        val texts = out.filterIsInstance<Block.Para>().map { it.paragraph.text }
        assertEquals(listOf("Hello", "World", "same", "c"), texts)
        assertEquals(true, (out[0] as Block.Para).paragraph.runs.first().style.bold)
        assertEquals(1, out.count { it is Block.PageBreak })
    }
}
