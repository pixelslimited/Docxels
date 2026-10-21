package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderFooterTest {
    @Test fun fillsPlaceholders() {
        assertEquals("Page 2 of 5", HeaderFooter.fill("Page {page} of {pages}", 2, 5))
    }

    @Test fun piecesKeepOrder() {
        assertEquals(listOf("Page ", "{page}", " of ", "{pages}"), HeaderFooter.pieces("Page {page} of {pages}"))
        assertEquals(listOf("plain"), HeaderFooter.pieces("plain"))
    }

    @Test fun linesDropBlanks() {
        assertEquals(listOf("a", "b"), HeaderFooter.lines("a\n\n b\n"))
    }

    @Test fun multiLineAlignedFooterSurvivesDocx() {
        val page = PageSetup(headerText = "Acme Ltd\nRC 12345", headerAlign = 2, footerText = "Confidential\nPage {page} of {pages}", footerAlign = 0)
        val bytes = DocxWriter.toBytes(WordDoc("t", listOf(Block.Para(Paragraph())), page))
        val back = DocxReader.read(java.io.ByteArrayInputStream(bytes)).page
        assertEquals("Acme Ltd\nRC 12345", back.headerText)
        assertEquals(2, back.headerAlign)
        assertEquals("Confidential\nPage {page} of {pages}", back.footerText)
        assertEquals(0, back.footerAlign)
        assertTrue(!back.pageNumbers)
    }
}
