package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrackedChangesTest {
    private fun docx(body: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            put("_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/document.xml", "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>")
        }
        return out.toByteArray()
    }

    private val sample = docx("<w:p><w:r><w:t xml:space=\"preserve\">Keep </w:t></w:r><w:ins w:id=\"1\" w:author=\"A\"><w:r><w:t>new</w:t></w:r></w:ins><w:del w:id=\"2\" w:author=\"A\"><w:r><w:delText>old</w:delText></w:r></w:del></w:p>")

    private fun text(mode: DocxReader.Tracked) =
        DocxReader.read(sample, "t", mode).blocks.filterIsInstance<Block.Para>().joinToString("|") { it.paragraph.text }

    @Test fun countsTrackedChanges() = assertEquals(2, DocxReader.trackedCount(sample))
    @Test fun acceptKeepsInsertionsAndDropsDeletions() = assertEquals("Keep new", text(DocxReader.Tracked.ACCEPT))
    @Test fun rejectKeepsDeletionsAndDropsInsertions() = assertEquals("Keep old", text(DocxReader.Tracked.REJECT))
    @Test fun markupShowsBoth() {
        val runs = DocxReader.read(sample, "t", DocxReader.Tracked.MARKUP).blocks.filterIsInstance<Block.Para>().first().paragraph.runs
        assertEquals("Keep newold", runs.joinToString("") { it.text })
        assertTrue(runs.first { it.text == "old" }.style.strike)
        assertTrue(runs.first { it.text == "new" }.style.underline)
    }
}
