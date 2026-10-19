package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Table styles in styles.xml: the font, text colour and shading a table gets without any direct formatting. */
class TableStyleTest {
    private val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

    private fun docx(styles: String, body: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
            put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/_rels/document.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            put("word/document.xml", "<w:document $ns><w:body>$body</w:body></w:document>")
            put("word/styles.xml", "<w:styles $ns>$styles</w:styles>")
        }
        return out.toByteArray()
    }

    private val styles =
        "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
            "<w:style w:type=\"table\" w:styleId=\"Plan\"><w:name w:val=\"Plan\"/>" +
            "<w:rPr><w:rFonts w:ascii=\"Courier New\"/></w:rPr>" +
            "<w:tblStylePr w:type=\"firstRow\"><w:rPr><w:b/><w:color w:val=\"FFFFFF\"/></w:rPr><w:tcPr><w:shd w:val=\"clear\" w:fill=\"000000\"/></w:tcPr></w:tblStylePr>" +
            "<w:tblStylePr w:type=\"band2Horz\"><w:tcPr><w:shd w:val=\"clear\" w:fill=\"EEEEEE\"/></w:tcPr></w:tblStylePr>" +
            "</w:style>"

    private fun cell(text: String, extra: String = "") = "<w:tc><w:tcPr>$extra</w:tcPr><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"

    private fun table(): TableBlock {
        val body = "<w:tbl><w:tblPr><w:tblStyle w:val=\"Plan\"/><w:tblLook w:val=\"04A0\" w:firstRow=\"1\" w:noHBand=\"0\"/></w:tblPr>" +
            "<w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid>" +
            "<w:tr>${cell("Head")}</w:tr><w:tr>${cell("One")}</w:tr><w:tr>${cell("Two")}</w:tr></w:tbl>"
        return (DocxReader.read(docx(styles, body)).blocks.first { it is Block.Table } as Block.Table).table
    }

    @Test fun headerGetsStyleColourBoldAndShading() {
        val head = table().rows[0][0]
        val run = head.paragraphs[0].runs[0].style
        assertEquals(0xFFFFFF, run.color)
        assertTrue(run.bold)
        assertEquals(0x000000, head.shading)
    }

    @Test fun tableFontBeatsDocumentDefaultAndBandsApply() {
        val t = table()
        assertEquals("Courier New", t.rows[1][0].paragraphs[0].runs[0].style.font)
        assertEquals(null, t.rows[1][0].shading)      // first body row is band 1: no fill
        assertEquals(0xEEEEEE, t.rows[2][0].shading)  // second body row is band 2
    }

    @Test fun directFormattingStillWins() {
        val body = "<w:tbl><w:tblPr><w:tblStyle w:val=\"Plan\"/></w:tblPr><w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid>" +
            "<w:tr><w:tc><w:tcPr><w:shd w:val=\"clear\" w:fill=\"FF0000\"/></w:tcPr><w:p><w:r><w:rPr><w:color w:val=\"00FF00\"/></w:rPr><w:t>X</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
        val t = (DocxReader.read(docx(styles, body)).blocks.first { it is Block.Table } as Block.Table).table
        assertEquals(0xFF0000, t.rows[0][0].shading)
        assertEquals(0x00FF00, t.rows[0][0].paragraphs[0].runs[0].style.color)
    }
}
