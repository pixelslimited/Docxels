package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParaShadingTest {
    private fun roundTrip(p: Paragraph) =
        (DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(p))))).blocks[0] as Block.Para).paragraph

    @Test fun shadingAndBorderSurviveRoundTrip() {
        val back = roundTrip(Paragraph(listOf(Run("Boxed note")), shading = 0xFFF2CC, borderColor = 0x404040))
        assertEquals("Boxed note", back.text)
        assertEquals(0xFFF2CC, back.shading)
        assertEquals(0x404040, back.borderColor)
    }

    @Test fun plainParagraphHasNeither() {
        val back = roundTrip(Paragraph(listOf(Run("Plain"))))
        assertNull(back.shading)
        assertNull(back.borderColor)
    }
}

class UnderlineStyleTest {
    @Test fun doubleAndWavyUnderlinesSurvive() {
        val p = Paragraph(
            listOf(
                Run("a", RunStyle(underline = true, underlineStyle = "double")),
                Run("b", RunStyle(underline = true)),
                Run("c", RunStyle(underline = true, underlineStyle = "wave")),
            ),
        )
        val back = (DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(p))))).blocks[0] as Block.Para).paragraph
        assertEquals(listOf("double", null, "wave"), back.runs.map { it.style.underlineStyle })
        assertEquals(true, back.runs.all { it.style.underline })
    }
}
