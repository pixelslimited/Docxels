package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FontsTest {
    private val ns = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""

    private fun docx(styles: String, theme: String?, body: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>")
            put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/_rels/document.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                (if (theme != null) "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>" else "") + "</Relationships>")
            put("word/document.xml", "<w:document $ns><w:body>$body</w:body></w:document>")
            put("word/styles.xml", "<w:styles $ns>$styles</w:styles>")
            if (theme != null) put("word/theme/theme1.xml", "<a:theme $ns><a:themeElements><a:fontScheme><a:majorFont><a:latin typeface=\"Cooper Black\"/></a:majorFont><a:minorFont><a:latin typeface=\"$theme\"/></a:minorFont></a:fontScheme></a:themeElements></a:theme>")
        }
        return out.toByteArray()
    }

    private fun fontOf(bytes: ByteArray): String? = ((DocxReader.read(bytes).blocks[0] as Block.Para).paragraph.runs.first().style.font)

    private val para = "<w:p><w:r><w:t>Hello</w:t></w:r></w:p>"

    @Test fun themeFontOfNormalStyleIsUsed() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
        assertEquals("Bookman Old Style", fontOf(docx(styles, "Bookman Old Style", para)))
    }

    @Test fun normalStyleFontBeatsDefaults() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:rPr><w:rFonts w:ascii=\"Garamond\"/></w:rPr></w:style>"
        assertEquals("Garamond", fontOf(docx(styles, null, para)))
    }

    @Test fun calibriDefaultStaysUnset() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\"/></w:rPr></w:rPrDefault></w:docDefaults>"
        assertNull(fontOf(docx(styles, null, para)))
    }

    @Test fun directFontWinsAndHeadingUsesItsStyleFont() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\"/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:rPr><w:rFonts w:asciiTheme=\"majorHAnsi\"/></w:rPr></w:style>"
        val body = "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Head</w:t></w:r></w:p>" +
            "<w:p><w:r><w:rPr><w:rFonts w:ascii=\"Verdana\"/></w:rPr><w:t>Direct</w:t></w:r></w:p>"
        val doc = DocxReader.read(docx(styles, "Calibri", body))
        assertEquals("Cooper Black", (doc.blocks[0] as Block.Para).paragraph.runs.first().style.font)
        assertEquals("Verdana", (doc.blocks[1] as Block.Para).paragraph.runs.first().style.font)
    }
}
