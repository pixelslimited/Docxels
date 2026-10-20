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

class FirstPageHeaderTest {
    @Test fun differentFirstPageSurvives() {
        val page = PageSetup(
            headerText = "Report", footerText = "Confidential", pageNumbers = true,
            differentFirstPage = true, firstHeaderText = "Cover", firstFooterText = "", firstPageNumbers = false,
        )
        val back = DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(Paragraph(listOf(Run("x"))))), page))).page
        assertEquals(true, back.differentFirstPage)
        assertEquals("Cover", back.firstHeaderText)
        assertEquals("", back.firstFooterText)
        assertEquals(false, back.firstPageNumbers)
        assertEquals("Report", back.headerText)
        assertEquals("Confidential", back.footerText)
        assertEquals(true, back.pageNumbers)
    }

    @Test fun plainDocumentHasNoFirstPageSettings() {
        val back = DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(Paragraph(listOf(Run("x")))))))).page
        assertEquals(false, back.differentFirstPage)
    }
}

class ImageWrapTest {
    private val png = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
    )

    private fun roundTrip(wrap: ImageWrap): ImageBlock {
        val doc = WordDoc("T", listOf(Block.Image(ImageBlock(png, "png", 2000, 1500, Align.CENTER, wrap))))
        return (DocxReader.read(DocxWriter.toBytes(doc)).blocks.first { it is Block.Image } as Block.Image).image
    }

    @Test fun everyWrapModeSurvives() {
        for (w in ImageWrap.entries) {
            val back = roundTrip(w)
            assertEquals(w, back.wrap)
            assertEquals(2000, back.widthTwips)
        }
    }
}

class ShapeBlockTest {
    private fun roundTrip(s: ShapeBlock): ShapeBlock =
        (DocxReader.read(DocxWriter.toBytes(WordDoc("T", listOf(Block.Shape(s))))).blocks.first { it is Block.Shape } as Block.Shape).shape

    @Test fun shapeSurvivesRoundTrip() {
        val s = ShapeBlock("ellipse", 3000, 1500, fill = 0xFFF2CC, lineColor = 0x7F6000, lineWidthPt = 2f, align = Align.CENTER, wrap = ImageWrap.SQUARE)
        val back = roundTrip(s)
        assertEquals("ellipse", back.preset)
        assertEquals(3000, back.widthTwips)
        assertEquals(1500, back.heightTwips)
        assertEquals(0xFFF2CC, back.fill)
        assertEquals(0x7F6000, back.lineColor)
        assertEquals(2f, back.lineWidthPt, 0.01f)
        assertEquals(ImageWrap.SQUARE, back.wrap)
    }

    @Test fun noFillAndNoLineSurvive() {
        val back = roundTrip(ShapeBlock("rect", 2000, 800, fill = null, lineWidthPt = 0f))
        assertEquals(null, back.fill)
        assertEquals(0f, back.lineWidthPt, 0.001f)
    }

    @Test fun unknownPresetIsKeptNotChanged() {
        assertEquals("star5", roundTrip(ShapeBlock("star5", 2000, 2000)).preset)
    }
}

class CompatNotesTest {
    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { z ->
            for ((n, t) in parts) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
        }
        return bos.toByteArray()
    }

    @Test fun plainDocumentHasNoNotes() {
        val bytes = DocxWriter.toBytes(WordDoc("T", listOf(Block.Para(Paragraph(listOf(Run("hello")))))))
        assertEquals(emptyList<String>(), DocxReader.compatNotes(bytes))
    }

    @Test fun reportsFootnotesAndTrackedChanges() {
        val bytes = zip(
            "word/document.xml" to "<w:document><w:body><w:p><w:ins w:id=\"1\"><w:r><w:t>x</w:t></w:r></w:ins><w:del w:id=\"2\"/></w:p></w:body></w:document>",
            "word/footnotes.xml" to "<w:footnotes><w:footnote w:type=\"separator\" w:id=\"-1\"/><w:footnote w:id=\"1\"/><w:footnote w:id=\"2\"/></w:footnotes>",
            "word/comments.xml" to "<w:comments><w:comment w:id=\"0\"/></w:comments>",
            "word/charts/chart1.xml" to "<c/>",
        )
        val notes = DocxReader.compatNotes(bytes)
        assertEquals(true, notes.any { it.startsWith("footnotes (2)") })
        assertEquals(false, notes.any { it.startsWith("comments") }) // comments are kept now
        assertEquals(false, notes.any { it.startsWith("tracked changes") }) // handled by the open prompt
        assertEquals(true, notes.contains("charts"))
    }

    @Test fun footnoteTextIsKeptAtTheEnd() {
        val bytes = zip(
            "word/document.xml" to "<w:document><w:body><w:p><w:r><w:t>Claim</w:t></w:r><w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p></w:body></w:document>",
            "word/footnotes.xml" to "<w:footnotes><w:footnote w:type=\"separator\" w:id=\"-1\"/><w:footnote w:id=\"1\"><w:p><w:r><w:t>See the source.</w:t></w:r></w:p></w:footnote></w:footnotes>",
        )
        val text = DocxReader.read(bytes).blocks.filterIsInstance<Block.Para>().joinToString("\n") { p -> p.paragraph.runs.joinToString("") { it.text } }
        assertEquals(true, text.contains("Claim[1]"))
        assertEquals(true, text.contains("Notes"))
        assertEquals(true, text.contains("[1] See the source."))
    }
}
