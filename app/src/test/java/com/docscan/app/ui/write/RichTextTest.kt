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
@Config(sdk = [34])
class RichTextTest {
    private val d = 2f

    @Test fun modelRoundTrip() {
        val paras = listOf(
            Paragraph(listOf(Run("Hello ", RunStyle()), Run("bold", RunStyle(bold = true, color = 0xFF0000)), Run(" end")), ParaStyle.H1, Align.CENTER),
            Paragraph(listOf(Run("item", RunStyle(italic = true, sizePt = 14f))), list = ListKind.BULLET),
            Paragraph(),
        )
        val back = SpanBridge.toParagraphs(SpanBridge.toEditable(paras, d))
        assertEquals(paras, back)
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
}
