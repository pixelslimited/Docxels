package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CellBorderTest {
    private fun table(vararg rows: List<TableCell>) = TableBlock(rows.toList(), List(rows[0].size) { 2000 }, borders = true, borderColor = 0x112233, borderWidthPt = 1f)

    @Test fun cellFollowsTableUnlessItSaysOtherwise() {
        val plain = TableCell()
        val none = TableCell(borders = CellBorders(top = CellBorder(visible = false)))
        val t = table(listOf(plain, none))
        assertEquals(0x112233, CellEdges.line(t, plain, 0)!!.color)
        assertNull(CellEdges.line(t, none, 0))
        assertNotNull(CellEdges.line(t, none, 2))
    }

    @Test fun rowHeightIsTheTallestCell() {
        assertEquals(850, CellEdges.rowHeight(listOf(TableCell(rowHeightTwips = 400), TableCell(rowHeightTwips = 850))))
        assertEquals(0, CellEdges.rowHeight(emptyList()))
    }

    @Test fun roundTripsThroughDocx() {
        val cell = TableCell(
            paragraphs = listOf(Paragraph(listOf(Run("A")))),
            borders = CellBorders(top = CellBorder(true, 0xC00000, 2f), left = CellBorder(visible = false)),
            rowHeightTwips = 850,
        )
        val doc = WordDoc(blocks = listOf(Block.Table(table(listOf(cell, TableCell(rowHeightTwips = 850))))))
        val back = DocxReader.read(java.io.ByteArrayInputStream(DocxWriter.toBytes(doc)))
        val c = (back.blocks.filterIsInstance<Block.Table>().first().table.rows[0][0])
        assertEquals(850, c.rowHeightTwips)
        assertEquals(0xC00000, c.borders!!.top!!.color)
        assertEquals(2f, c.borders!!.top!!.widthPt, 0.01f)
        assertEquals(false, c.borders!!.left!!.visible)
        assertNull(c.borders!!.bottom)
    }
}
