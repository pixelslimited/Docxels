package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingObjectTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9)

    private fun roundTrip(vararg blocks: Block): List<Block> =
        DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(WordDoc("t", blocks.toList())))).blocks

    @Test fun pagePinnedPictureKeepsItsSheetPosition() {
        val img = ImageBlock(png, "png", 11906, 16838, wrap = ImageWrap.BEHIND, offsetXTwips = 0, offsetYTwips = 0, pageRelative = true)
        val back = roundTrip(Block.Image(img), Block.Para(Paragraph(listOf(Run("Hello")))), Block.PageBreak, Block.Image(img.copy(offsetYTwips = 200)))
            .filterIsInstance<Block.Image>().map { it.image }
        assertEquals(2, back.size)
        assertTrue(back.all { it.pageRelative && it.wrap == ImageWrap.BEHIND })
        assertEquals(200, back[1].offsetYTwips)
    }

    @Test fun pictureKeepsWhereItWasDropped() {
        val img = ImageBlock(png, "png", 3000, 2000, wrap = ImageWrap.IN_FRONT, offsetXTwips = 1700, offsetYTwips = 900)
        val back = roundTrip(Block.Para(Paragraph(listOf(Run("Hello")))), Block.Image(img)).filterIsInstance<Block.Image>().single().image
        assertEquals(ImageWrap.IN_FRONT, back.wrap)
        assertEquals(1700, back.offsetXTwips)
        assertEquals(900, back.offsetYTwips)
    }

    @Test fun shapeKeepsItsPosition() {
        val sh = ShapeBlock("ellipse", 2000, 1000, wrap = ImageWrap.BEHIND, offsetXTwips = 500, offsetYTwips = 1200)
        val back = roundTrip(Block.Shape(sh)).filterIsInstance<Block.Shape>().single().shape
        assertEquals(ImageWrap.BEHIND, back.wrap)
        assertEquals(500, back.offsetXTwips)
        assertEquals(1200, back.offsetYTwips)
    }

    @Test fun inlinePicturesAreNotFloating() {
        assertTrue(!ImageWrap.IN_LINE.floating && !ImageWrap.SQUARE.floating)
        assertTrue(ImageWrap.IN_FRONT.floating && ImageWrap.BEHIND.floating)
    }
}
