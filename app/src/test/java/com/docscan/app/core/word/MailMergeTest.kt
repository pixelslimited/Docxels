package com.docscan.app.core.word

import com.docscan.app.core.RecipientFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MailMergeTest {
    private val bold = RunStyle(bold = true)

    private fun rec() = MailMerge.fromGrid(listOf(listOf("Name", "Phone"), listOf("Imam Musa", "0803"), listOf("Imam Sani", "0701")))!!

    @Test fun fieldSplitOverRunsKeepsFirstStyle() {
        val p = Paragraph(listOf(Run("Dear {{Na"), Run("me}}", bold), Run(",")))
        val out = MailMerge.fillRuns(p.runs, rec().rows[0])
        assertEquals("Dear Imam Musa,", out.joinToString("") { it.text })
        assertTrue(out.none { it.style.bold })
    }

    @Test fun styleOfFieldIsKeptWhenInsideOneRun() {
        val out = MailMerge.fillRuns(listOf(Run("To: "), Run("{{name}}", bold)), rec().rows[1])
        assertEquals("Imam Sani", out.last().text)
        assertTrue(out.last().style.bold)
    }

    @Test fun unknownFieldIsLeftAndReported() {
        val doc = WordDoc("t", listOf(Block.Para(Paragraph(listOf(Run("{{Name}} {{Mosque}}"))))))
        assertEquals(listOf("Mosque"), MailMerge.missingColumns(doc, rec()))
        assertEquals("Imam Musa {{Mosque}}", MailMerge.fillText("{{Name}} {{Mosque}}", rec().rows[0]))
    }

    @Test fun mergeMakesOnePagePerRecipientAndSkipsTheListTable() {
        val table = TableBlock(listOf(listOf(TableCell(listOf(Paragraph(listOf(Run("Name"))))))), listOf(1000))
        val doc = WordDoc("t", listOf(Block.Para(Paragraph(listOf(Run("Dear {{Name}}")))), Block.Table(table)))
        val merged = MailMerge.merge(doc, rec(), "Merged", skipBlock = 1)
        assertEquals(3, merged.blocks.size) // letter, page break, letter
        assertEquals(Block.PageBreak, merged.blocks[1])
        assertEquals("Dear Imam Sani", (merged.blocks[2] as Block.Para).paragraph.text)
        assertEquals("Merged", merged.title)
    }

    @Test fun fieldsInTableCellsAreFilled() {
        val cell = TableCell(listOf(Paragraph(listOf(Run("Tel {{Phone}}")))))
        val b = MailMerge.fillBlock(Block.Table(TableBlock(listOf(listOf(cell)), listOf(1000))), rec().rows[0]) as Block.Table
        assertEquals("Tel 0803", b.table.rows[0][0].paragraphs[0].text)
    }

    @Test fun gridDedupesAndNamesBlankHeaders() {
        val r = MailMerge.fromGrid(listOf(listOf("A", "A", ""), listOf("1", "2", "3")))!!
        assertEquals(3, r.columns.distinct().size)
        assertEquals("3", r.rows[0][r.columns[2].lowercase()])
    }

    @Test fun csvHandlesQuotesAndSemicolons() {
        val g = RecipientFiles.parseCsv("Name;Note\n\"Musa, Imam\";\"said \"\"hi\"\"\"\nSani;ok\n")
        assertEquals(listOf("Musa, Imam", "said \"hi\""), g[1])
        assertEquals(3, g.size)
    }

    @Test fun xlsxFirstSheetIsRead() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            z.write("<sst><si><t>Name</t></si><si><t>Phone</t></si><si><t>Imam Musa</t></si></sst>".toByteArray())
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            z.write(
                ("<worksheet><sheetData>" +
                    "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"s\"><v>1</v></c></row>" +
                    "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"C2\"><v>8.031234567E9</v></c></row>" +
                    "</sheetData></worksheet>").toByteArray(),
            )
        }
        val g = RecipientFiles.parseXlsx(out.toByteArray())
        assertNotNull(g)
        assertEquals(listOf("Name", "", "Phone"), g!![0])
        assertEquals(listOf("Imam Musa", "", "8031234567"), g[1])
    }
}
