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

class ParagraphDirectionTest {
    @Test fun rightToLeftParagraphSurvivesSaving() {
        val doc = WordDoc("t", listOf(Block.Para(Paragraph(listOf(Run("مرحبا")), rtl = true)), Block.Para(Paragraph(listOf(Run("Hello"))))))
        val back = DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(doc))).blocks.filterIsInstance<Block.Para>()
        assertTrue(back[0].paragraph.rtl)
        assertTrue(!back[1].paragraph.rtl)
    }
}

class ColumnsTest {
    @Test fun columnCountSurvivesSaving() {
        val doc = WordDoc("t", listOf(Block.Para(Paragraph(listOf(Run("Hi"))))), page = PageSetup(columns = 3))
        assertEquals(3, DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(doc))).page.columns)
    }
}

class OldTextBoxTest {
    private fun docx(body: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            z.write(
                ("""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                    """xmlns:v="urn:schemas-microsoft-com:vml" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"><w:body>$body</w:body></w:document>""").toByteArray(),
            )
            z.closeEntry()
        }
        return out.toByteArray()
    }

    private val vmlBox = """<w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent><w:p><w:r><w:t>Boxed words</w:t></w:r></w:p></w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p>"""

    @Test fun textInAnOldStyleTextBoxIsKept() {
        val text = DocxReader.read(java.io.ByteArrayInputStream(docx(vmlBox))).blocks
            .filterIsInstance<Block.Para>().joinToString(" ") { p -> p.paragraph.runs.joinToString("") { it.text } }
        assertTrue(text.contains("Boxed words"))
        assertTrue(DocxReader.compatNotes(docx(vmlBox)).any { it.startsWith("text boxes") })
    }

    @Test fun aFallbackCopyIsNotReportedAsLost() {
        val body = """<w:p><w:r><mc:AlternateContent><mc:Choice Requires="wps"><w:drawing/></mc:Choice><mc:Fallback>$vmlBox</mc:Fallback></mc:AlternateContent></w:r></w:p>"""
        assertTrue(DocxReader.compatNotes(docx(body)).none { it.startsWith("text boxes") })
    }
}
