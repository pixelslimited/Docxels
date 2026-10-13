package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            result[entry.name] = zip.readBytes()
        }
    }
    return result
}

class XmlTest {
    @Test
    fun escape_handlesReservedCharactersAndDropsIllegalControlCharacters() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;", Xml.escape("a & b <c> \"d\" 'e'"))
        assertEquals("ab", Xml.escape("a\u0001b"))
        assertEquals("a\tb\nc", Xml.escape("a\tb\nc"))
    }
}

class XlsxBuilderTest {
    @Test
    fun columnName_followsSpreadsheetNumbering() {
        assertEquals("A", XlsxBuilder.columnName(0))
        assertEquals("Z", XlsxBuilder.columnName(25))
        assertEquals("AA", XlsxBuilder.columnName(26))
        assertEquals("AB", XlsxBuilder.columnName(27))
        assertEquals("AAA", XlsxBuilder.columnName(702))
    }

    @Test
    fun sanitizeSheetName_removesForbiddenCharactersAndLimitsLength() {
        assertEquals("a b c", XlsxBuilder.sanitizeSheetName("a/b:c"))
        assertEquals(31, XlsxBuilder.sanitizeSheetName("x".repeat(80)).length)
        assertEquals("Sheet1", XlsxBuilder.sanitizeSheetName("   "))
    }

    @Test
    fun sheetXml_writesNumbersAsNumbersAndKeepsLeadingZeroTextAsText() {
        val xml = XlsxBuilder.sheetXml(listOf(listOf("Name", "12.5", "007", "-3"), listOf("A & B", "", "x")))
        assertTrue(xml.contains("""<c r="B1"><v>12.5</v></c>"""))
        assertTrue(xml.contains("""<c r="D1"><v>-3</v></c>"""))
        assertTrue(xml.contains("""<c r="C1" t="inlineStr"><is><t xml:space="preserve">007</t></is></c>"""))
        assertTrue(xml.contains("A &amp; B"))
        assertFalse(xml.contains("""r="B2""""))
    }

    @Test
    fun build_producesAZipWithTheRequiredParts() {
        val out = ByteArrayOutputStream()
        XlsxBuilder.build(listOf(listOf("a", "1")), "Table 1", out)
        val parts = unzip(out.toByteArray())
        assertTrue(
            parts.keys.containsAll(
                listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"),
            ),
        )
        assertTrue(String(parts.getValue("xl/workbook.xml")).contains("""name="Table 1""""))
    }
}

class DocxBuilderTest {
    private fun build(block: DocxBuilder.() -> Unit): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        DocxBuilder().apply(block).writeTo(out)
        return unzip(out.toByteArray())
    }

    @Test
    fun imagePages_embedMediaAndRelationships_andBreakBeforeSecondPage() {
        val parts = build {
            addImagePage(byteArrayOf(1, 2, 3), 1000, 1500, "jpg")
            addImagePage(byteArrayOf(4, 5), 1500, 1000, "jpg")
        }
        assertTrue(parts.containsKey("word/media/image1.jpg"))
        assertTrue(parts.containsKey("word/media/image2.jpg"))
        val doc = String(parts.getValue("word/document.xml"))
        assertEquals(1, Regex("<w:pageBreakBefore/>").findAll(doc).count())
        assertTrue(String(parts.getValue("word/_rels/document.xml.rels")).contains("rIdImg2"))
    }

    @Test
    fun imageSize_staysInsideThePrintableArea_andKeepsAspectRatio() {
        val parts = build { addImagePage(byteArrayOf(0), 3000, 6000, "png") }
        val doc = String(parts.getValue("word/document.xml"))
        val match = Regex("""<wp:extent cx="(\d+)" cy="(\d+)"/>""").find(doc)!!
        val cx = match.groupValues[1].toLong()
        val cy = match.groupValues[2].toLong()
        assertTrue(cx <= 6_645_000L && cy <= 9_300_000L)
        assertEquals(2.0, cy.toDouble() / cx, 0.01)
    }

    @Test
    fun table_hasRowsColumnsEscapedTextAndTrailingParagraph() {
        val parts = build { addTable(listOf(listOf("a", "b & c"), listOf("1"))) }
        val doc = String(parts.getValue("word/document.xml"))
        assertEquals(2, Regex("<w:tr>").findAll(doc).count())
        assertEquals(4, Regex("<w:tc>").findAll(doc).count()) // short row is padded to two cells
        assertTrue(doc.contains("b &amp; c"))
        assertTrue(doc.contains("</w:tbl><w:p/>"))
    }

    @Test
    fun paragraph_isEscaped() {
        val parts = build { addParagraph("1 < 2") }
        assertTrue(String(parts.getValue("word/document.xml")).contains("1 &lt; 2"))
    }
}
