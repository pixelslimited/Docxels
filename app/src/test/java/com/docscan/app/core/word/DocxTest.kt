package com.docscan.app.core.word

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTest {
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5,
    )

    private fun p(text: String, style: RunStyle = RunStyle(), para: Paragraph = Paragraph()) =
        Block.Para(para.copy(runs = listOf(Run(text, style))))

    private fun sample(): WordDoc {
        val table = TableBlock(
            rows = listOf(
                listOf(
                    TableCell(listOf(Paragraph(listOf(Run("Item", RunStyle(bold = true))))), shading = 0xDDDDDD),
                    TableCell(listOf(Paragraph(listOf(Run("Qty")))), shading = 0xDDDDDD),
                ),
                listOf(
                    TableCell(listOf(Paragraph(listOf(Run("Apples & pears <fresh>"))))),
                    TableCell(listOf(Paragraph(listOf(Run("3"))))),
                ),
                listOf(TableCell(listOf(Paragraph(listOf(Run("Merged")))), colSpan = 2)),
            ),
            columnWidthsTwips = listOf(6000, 3000),
            headerRow = true,
        )
        return WordDoc(
            title = "Quarterly notes",
            blocks = listOf(
                p("Report", para = Paragraph(style = ParaStyle.TITLE)),
                p("Intro", para = Paragraph(style = ParaStyle.H1)),
                Block.Para(
                    Paragraph(
                        runs = listOf(
                            Run("Plain "),
                            Run("bold", RunStyle(bold = true)),
                            Run(" and "),
                            Run("fancy", RunStyle(italic = true, underline = true, strike = true, color = 0xFF0000, sizePt = 14f, font = "Arial")),
                            Run(" H", RunStyle()),
                            Run("2", RunStyle(vertical = VerticalAlign.SUBSCRIPT)),
                            Run("O", RunStyle(highlight = 0xFFFF00)),
                            Run("\tTabbed\nNew line"),
                        ),
                        align = Align.JUSTIFY,
                        spaceBeforePt = 6f,
                        spaceAfterPt = 12f,
                        lineSpacing = 1.5f,
                        indentLeftTwips = 720,
                        indentFirstTwips = 360,
                    ),
                ),
                p("one", para = Paragraph(list = ListKind.BULLET)),
                p("sub", para = Paragraph(list = ListKind.BULLET, level = 1)),
                p("first", para = Paragraph(list = ListKind.NUMBER)),
                p("second", para = Paragraph(list = ListKind.NUMBER)),
                p("break in list"),
                p("again first", para = Paragraph(list = ListKind.NUMBER)),
                Block.Table(table),
                Block.Image(ImageBlock(png, "png", 3000, 2000, Align.CENTER)),
                Block.PageBreak,
                p("Quoted", para = Paragraph(style = ParaStyle.QUOTE, align = Align.CENTER)),
            ),
            page = PageSetup(
                landscape = true,
                marginTopTwips = 1000,
                marginBottomTwips = 1100,
                marginLeftTwips = 1200,
                marginRightTwips = 1300,
                headerText = "Confidential",
                footerText = "Acme Ltd",
                pageNumbers = true,
            ),
        )
    }

    /** Leaves a sample file for CI to publish, so the output can be opened in real Word-compatible software. */
    @Test
    fun writesSampleFile() {
        val dir = java.io.File("build/word-sample").apply { mkdirs() }
        java.io.File(dir, "sample.docx").writeBytes(DocxWriter.toBytes(sample()))
    }

    @Test
    fun writerProducesWellFormedParts() {
        val bytes = DocxWriter.toBytes(sample())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val data = zip.readBytes()
                if (e.name.endsWith(".xml") || e.name.endsWith(".rels")) {
                    assertNotNull(e.name, factory.newDocumentBuilder().parse(ByteArrayInputStream(data)))
                    count++
                }
            }
        }
        assertTrue("expected several xml parts, got $count", count >= 8)
    }

    @Test
    fun roundTripKeepsContentAndFormatting() {
        val original = sample()
        val back = DocxReader.read(DocxWriter.toBytes(original))

        assertEquals("Quarterly notes", back.title)
        assertEquals(original.blocks.size, back.blocks.size)

        val title = (back.blocks[0] as Block.Para).paragraph
        assertEquals(ParaStyle.TITLE, title.style)
        assertEquals("Report", title.text)
        assertEquals(ParaStyle.H1, (back.blocks[1] as Block.Para).paragraph.style)

        val rich = (back.blocks[2] as Block.Para).paragraph
        assertEquals(original.blocks[2].let { (it as Block.Para).paragraph }, rich)

        val bullet = (back.blocks[3] as Block.Para).paragraph
        assertEquals(ListKind.BULLET, bullet.list)
        assertEquals(1, (back.blocks[4] as Block.Para).paragraph.level)
        assertEquals(ListKind.NUMBER, (back.blocks[5] as Block.Para).paragraph.list)
        assertEquals(ListKind.NONE, (back.blocks[7] as Block.Para).paragraph.list)

        val table = (back.blocks[9] as Block.Table).table
        assertEquals(listOf(6000, 3000), table.columnWidthsTwips)
        assertTrue(table.headerRow)
        assertTrue(table.borders)
        assertEquals(3, table.rows.size)
        assertEquals(0xDDDDDD, table.rows[0][0].shading)
        assertEquals("Apples & pears <fresh>", table.rows[1][0].paragraphs[0].text)
        assertEquals(2, table.rows[2][0].colSpan)
        assertEquals(true, table.rows[0][0].paragraphs[0].runs[0].style.bold)

        val image = (back.blocks[10] as Block.Image).image
        assertEquals("png", image.extension)
        assertTrue(png.contentEquals(image.bytes))
        assertEquals(3000, image.widthTwips)
        assertEquals(2000, image.heightTwips)
        assertEquals(Align.CENTER, image.align)

        assertEquals(Block.PageBreak, back.blocks[11])
        assertEquals(ParaStyle.QUOTE, (back.blocks[12] as Block.Para).paragraph.style)

        val page = back.page
        assertTrue(page.landscape)
        assertEquals(1000, page.marginTopTwips)
        assertEquals(1100, page.marginBottomTwips)
        assertEquals(1200, page.marginLeftTwips)
        assertEquals(1300, page.marginRightTwips)
        assertEquals("Confidential", page.headerText)
        assertEquals("Acme Ltd", page.footerText)
        assertTrue(page.pageNumbers)
        assertEquals(PaperSize.A4.heightTwips, page.sheetWidthTwips)
    }

    @Test
    fun numberedListsRestartAfterABreak() {
        val xml = String(
            java.util.zip.ZipInputStream(ByteArrayInputStream(DocxWriter.toBytes(sample()))).let { zip ->
                var found = ByteArray(0)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == "word/document.xml") found = zip.readBytes()
                }
                found
            },
            Charsets.UTF_8,
        )
        val ids = Regex("<w:numId w:val=\"(\\d+)\"/>").findAll(xml).map { it.groupValues[1] }.toList()
        // bullet, bullet, number list A, number list A, number list B
        assertEquals(listOf("1", "1", "2", "2", "3"), ids)
    }

    @Test
    fun miniXmlHandlesEntitiesCdataAndPrefixes() {
        val root = MiniXml.parse(
            "<?xml version=\"1.0\"?><!-- c --><w:a xmlns:w=\"x\" w:val=\"1 &amp; 2\"><w:b>&lt;hi&gt; &#65;&#x42;</w:b><w:c/><![CDATA[raw <x>]]></w:a>",
        )
        assertEquals("a", root.local)
        assertEquals("1 & 2", root.attr("val"))
        assertEquals("<hi> AB", root.child("b")?.text.toString())
        assertNotNull(root.child("c"))
        assertEquals("raw <x>", root.text.toString())
    }

    @Test
    fun readsForeignStylesAndFormatting() {
        // A hand-made file in the style Word writes: custom style, prefixed namespaces, hyperlink, hyphen, sdt.
        val doc = """<?xml version="1.0"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
            <w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>Head</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Fancy"/></w:pPr><w:r><w:t>Styled </w:t></w:r><w:hyperlink><w:r><w:rPr><w:b/></w:rPr><w:t>link</w:t></w:r></w:hyperlink></w:p>
            <w:sdt><w:sdtContent><w:p><w:r><w:t>inside control</w:t></w:r></w:p></w:sdtContent></w:sdt>
            <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720"/></w:sectPr>
            </w:body></w:document>""".trimIndent()
        val styles = """<?xml version="1.0"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
            <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/></w:style>
            <w:style w:type="paragraph" w:styleId="Fancy"><w:name w:val="Fancy"/><w:basedOn w:val="Normal"/>
              <w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:i/><w:color w:val="0000FF"/></w:rPr></w:style>
            </w:styles>""".trimIndent()
        val rels = """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
        val rootRels = """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""

        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { z ->
            fun add(name: String, text: String) {
                z.putNextEntry(java.util.zip.ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
            add("_rels/.rels", rootRels)
            add("word/document.xml", doc)
            add("word/styles.xml", styles)
            add("word/_rels/document.xml.rels", rels)
        }
        val back = DocxReader.read(bos.toByteArray(), "Fallback")
        assertEquals("Fallback", back.title)
        assertEquals(3, back.blocks.size)
        assertEquals(ParaStyle.H2, (back.blocks[0] as Block.Para).paragraph.style)

        val fancy = (back.blocks[1] as Block.Para).paragraph
        assertEquals(Align.CENTER, fancy.align)
        assertEquals("Styled link", fancy.text)
        assertEquals(true, fancy.runs[0].style.italic)
        assertEquals(0x0000FF, fancy.runs[0].style.color)
        assertEquals(true, fancy.runs[1].style.bold)

        assertEquals("inside control", (back.blocks[2] as Block.Para).paragraph.text)
        assertEquals(720, back.page.marginLeftTwips)
        assertEquals(PaperSize.LETTER.widthTwips, back.page.widthTwips)
    }

    @Test(expected = DocxReader.DocxException::class)
    fun rejectsNonZipInput() {
        DocxReader.read("this is not a docx".toByteArray())
    }
}
