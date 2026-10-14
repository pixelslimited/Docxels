package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainExportTest {
    private fun doc(vararg b: Block) = WordDoc("Notes", b.toList())

    @Test fun textKeepsListsAndTables() {
        val d = doc(
            Block.Para(Paragraph(listOf(Run("Title")))),
            Block.Para(Paragraph(listOf(Run("one")), list = ListKind.NUMBER)),
            Block.Para(Paragraph(listOf(Run("two")), list = ListKind.NUMBER)),
            Block.Para(Paragraph(listOf(Run("dot")), list = ListKind.BULLET)),
            Block.Table(TableBlock(listOf(listOf(TableCell(listOf(Paragraph(listOf(Run("a"))))), TableCell(listOf(Paragraph(listOf(Run("b"))))))), listOf(2000, 2000))),
        )
        assertEquals("Title\n1. one\n2. two\n• dot\na\tb\n", PlainExport.toText(d))
    }

    @Test fun rtfEscapesAndFormats() {
        val d = doc(
            Block.Para(Paragraph(listOf(Run("Hi {x}\\ é", RunStyle(bold = true, color = 0xFF0000, sizePt = 14f))), align = Align.CENTER)),
        )
        val rtf = PlainExport.toRtf(d)
        assertTrue(rtf.startsWith("{\\rtf1"))
        assertTrue(rtf.endsWith("}"))
        assertTrue(rtf.contains("Hi \\{x\\}\\\\ \\u233?"))
        assertTrue(rtf.contains("\\b"))
        assertTrue(rtf.contains("\\fs28"))
        assertTrue(rtf.contains("\\red255\\green0\\blue0;"))
        assertTrue(rtf.contains("\\qc"))
        assertFalse(rtf.contains("é"))
    }

    @Test fun rtfBracesBalance() {
        val d = doc(
            Block.Para(Paragraph(listOf(Run("a", RunStyle(italic = true)), Run("b")))),
            Block.Table(TableBlock(listOf(listOf(TableCell(shading = 0xFFFF00), TableCell())), listOf(1000, 1000))),
            Block.PageBreak,
        )
        val rtf = PlainExport.toRtf(d)
        var depth = 0
        var i = 0
        while (i < rtf.length) {
            val c = rtf[i]
            if (c == '\\') i++ else if (c == '{') depth++ else if (c == '}') depth--
            assertTrue(depth >= 0)
            i++
        }
        assertEquals(0, depth)
    }
}
