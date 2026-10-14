package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterheadTest {
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8,
    )

    private fun roundTrip(page: PageSetup): PageSetup =
        DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(WordDoc("t", listOf(Block.Para(Paragraph())), page)))).page

    @Test fun headerAndFooterPicturesSurviveSaving() {
        val head = PageImage(png, "png", 0, 0, 11906, 2000, behind = true)
        val foot = PageImage(png, "png", 600, 15000, 10000, 900, behind = false)
        val back = roundTrip(PageSetup(headerImages = listOf(head), footerImages = listOf(foot)))
        assertEquals(1, back.headerImages.size)
        assertEquals(1, back.footerImages.size)
        val h = back.headerImages[0]
        assertEquals(0, h.xTwips); assertEquals(0, h.yTwips)
        assertEquals(11906, h.widthTwips); assertEquals(2000, h.heightTwips)
        assertTrue(h.behind)
        assertTrue(h.bytes.contentEquals(png))
        assertEquals(15000, back.footerImages[0].yTwips)
    }

    @Test fun firstPageLetterheadIsKeptSeparately() {
        val p = PageSetup(differentFirstPage = true, firstHeaderImages = listOf(PageImage(png, "png", 100, 200, 3000, 1000, true)))
        val back = roundTrip(p)
        assertTrue(back.differentFirstPage)
        assertEquals(1, back.firstHeaderImages.size)
        assertEquals(0, back.headerImages.size)
    }

    @Test fun inlinePictureInHeaderPushesBodyDown() {
        // A picture that sits in the flow and reaches y = 2800 twips must not be covered by body text.
        val back = roundTrip(PageSetup(marginTopTwips = 1440, headerImages = listOf(PageImage(png, "png", 1440, 709, 9000, 2100, false))))
        assertTrue(back.marginTopTwips >= 2809)
    }

    @Test fun noPicturesMeansNoExtraParts() {
        val back = roundTrip(PageSetup(headerText = "Hello"))
        assertEquals("Hello", back.headerText)
        assertTrue(back.headerImages.isEmpty())
    }
}
