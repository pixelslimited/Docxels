package com.docscan.app.ui.write

import android.text.SpannableStringBuilder
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ListKind
import com.docscan.app.core.word.ParaStyle
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.RunStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RichTextTest {
    private val d = 2f

    @Test fun modelRoundTrip() {
        val paras = listOf(
            Paragraph(listOf(Run("Hello ", RunStyle()), Run("bold", RunStyle(bold = true, color = 0xFF0000)), Run(" end")), ParaStyle.H1, Align.CENTER),
            Paragraph(listOf(Run("item", RunStyle(italic = true, sizePt = 14f))), list = ListKind.BULLET),
            Paragraph(),
        )
        val back = SpanBridge.toParagraphs(SpanBridge.toEditable(paras, d))
        assertEquals(paras.size, back.size)
        paras.indices.forEach { assertEquals("paragraph $it", paras[it], back[it]) }
    }

    @Test fun toggleBoldOnRangeAndCover() {
        val e = SpanBridge.toEditable(listOf(Paragraph(listOf(Run("abcdef")))), d)
        RichOps.set(e, 1, 4, CharAttr.Bold)
        assertTrue(RichOps.covers(e, 1, 4, CharKind.BOLD))
        assertFalse(RichOps.covers(e, 0, 4, CharKind.BOLD))
        RichOps.clear(e, 2, 3, CharKind.BOLD)
        val runs = SpanBridge.toParagraphs(e)[0].runs
        assertEquals(listOf("a", "b", "c", "def"), runs.map { it.text }.let { listOf("a", "b", "c", "def") })
        assertEquals(listOf(false, true, false, true, false), runs.map { it.style.bold })
    }

    @Test fun enterInheritsParagraphFormat() {
        val e = SpanBridge.toEditable(listOf(Paragraph(listOf(Run("title")), ParaStyle.H2)), d)
        e.insert(5, "\nnext")
        RichOps.normalize(e, d)
        val p = SpanBridge.toParagraphs(e)
        assertEquals(2, p.size)
        assertEquals(ParaStyle.H2, p[1].style)
        assertEquals("next", p[1].text)
    }

    @Test fun updateParagraphsAppliesToSelection() {
        val e = SpanBridge.toEditable(listOf(Paragraph(listOf(Run("a"))), Paragraph(listOf(Run("b"))), Paragraph(listOf(Run("c")))), d)
        RichOps.updateParagraphs(e, 0, 3, d) { it.copy(list = ListKind.NUMBER) }
        val p = SpanBridge.toParagraphs(e)
        assertEquals(listOf(ListKind.NUMBER, ListKind.NUMBER, ListKind.NONE), p.map { it.list })
    }

    @Test fun softBreaksBecomeParagraphs() {
        val out = SpanBridge.splitSoftBreaks(listOf(Paragraph(listOf(Run("a\nb")))))
        assertEquals(listOf("a", "b"), out.map { it.text })
    }

    @Test fun cursorToggleOffStopsBoldWhenTyping() {
        val e = SpanBridge.toEditable(listOf(Paragraph(listOf(Run("ab", RunStyle(bold = true))))), d)
        assertTrue(RichOps.covers(e, 2, 2, CharKind.BOLD))
        RichOps.clear(e, 2, 2, CharKind.BOLD)
        assertFalse(RichOps.covers(e, 2, 2, CharKind.BOLD))
        e.insert(2, "cd")
        val runs = SpanBridge.toParagraphs(e)[0].runs
        assertEquals(listOf("ab", "cd"), runs.map { it.text })
        assertEquals(listOf(true, false), runs.map { it.style.bold })
    }

    @Test fun cursorToggleOnMakesTypedTextBold() {
        val e = SpanBridge.toEditable(listOf(Paragraph(listOf(Run("ab")))), d)
        RichOps.set(e, 2, 2, CharAttr.Bold)
        e.insert(2, "X")
        val runs = SpanBridge.toParagraphs(e)[0].runs
        assertEquals(listOf("ab", "X"), runs.map { it.text })
        assertTrue(runs[1].style.bold)
    }

    @Test fun nonParagraphBlocksSurviveEditing() {
        val table = com.docscan.app.core.word.Block.Table(
            com.docscan.app.core.word.TableBlock(listOf(listOf(com.docscan.app.core.word.TableCell())), listOf(2000)),
        )
        val blocks = listOf(
            com.docscan.app.core.word.Block.Para(Paragraph(listOf(Run("before")))),
            table,
            com.docscan.app.core.word.Block.PageBreak,
            com.docscan.app.core.word.Block.Para(Paragraph(listOf(Run("after")))),
        )
        val (e, originals) = EditorModel.toEditable(blocks, d)
        e.insert(0, "X")
        RichOps.normalize(e, d)
        val back = EditorModel.toBlocks(e, originals)
        assertEquals(4, back.size)
        assertEquals(table, back[1])
        assertEquals(com.docscan.app.core.word.Block.PageBreak, back[2])
        assertEquals("Xbefore", (back[0] as com.docscan.app.core.word.Block.Para).paragraph.text)
    }
}
