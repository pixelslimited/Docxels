package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OddEvenTest {
    private val setup = PageSetup(
        headerText = "Odd header", footerText = "Odd footer", pageNumbers = true,
        differentOddEven = true, evenHeaderText = "Even header", evenFooterText = "Even footer",
    )

    @Test fun pickingByPageNumber() {
        assertEquals("Odd header", setup.headerFor(1))
        assertEquals("Even header", setup.headerFor(2))
        assertEquals("Odd footer", setup.footerFor(3))
        assertEquals("Even footer", setup.footerFor(4))
        val first = setup.copy(differentFirstPage = true, firstHeaderText = "Cover")
        assertEquals("Cover", first.headerFor(1))
        assertEquals("Even header", first.headerFor(2))
    }

    @Test fun roundTrip() {
        val doc = WordDoc("T", listOf(Block.Para(Paragraph(listOf(Run("Hello"))))), setup)
        val back = DocxReader.read(DocxWriter.toBytes(doc)).page
        assertTrue(back.differentOddEven)
        assertEquals("Even header", back.evenHeaderText)
        assertEquals("Even footer", back.evenFooterText)
        assertEquals("Odd header", back.headerText)
        assertEquals("Odd footer", back.footerText)
    }

    @Test fun offByDefault() {
        val back = DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(Paragraph(listOf(Run("x")))))))).page
        assertEquals(false, back.differentOddEven)
    }
}
