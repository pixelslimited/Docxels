package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBoxTest {
    private fun roundTrip(vararg blocks: Block): List<Block> =
        DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(WordDoc("t", blocks.toList())))).blocks

    @Test fun textBoxKeepsItsTextAndStyle() {
        val box = ShapeBlock("textbox", 3000, 1000, fill = 0xFFFFFF, text = "Line one\nLine two", textSizePt = 14f, textBold = true, textColor = 0x112233)
        val back = roundTrip(Block.Para(Paragraph(listOf(Run("Hi")))), Block.Shape(box)).filterIsInstance<Block.Shape>().single().shape
        assertTrue(ShapePresets.isTextBox(back.preset))
        assertEquals("Line one\nLine two", back.text)
        assertEquals(14f, back.textSizePt, 0.01f)
        assertTrue(back.textBold)
        assertEquals(0x112233, back.textColor)
    }

    @Test fun shapeWithoutTextStaysEmpty() {
        val back = roundTrip(Block.Shape(ShapeBlock("ellipse", 2000, 1000))).filterIsInstance<Block.Shape>().single().shape
        assertEquals("", back.text)
        assertEquals("ellipse", back.preset)
    }
}
