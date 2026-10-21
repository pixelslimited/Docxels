package com.docscan.app.ui.write

import com.docscan.app.core.word.TableBlock
import com.docscan.app.core.word.TableCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableOpsTest {
    private fun table(rows: Int, cols: Int) = TableBlock(List(rows) { List(cols) { TableCell() } }, List(cols) { 1000 })

    @Test fun rowBelowAddsOneRow() {
        val t = TableOps.rowBelow(table(2, 3), 0)!!
        assertEquals(3, t.rows.size)
        assertEquals(3, t.rows[1].size)
    }

    @Test fun columnRightKeepsTheTotalWidth() {
        val t = TableOps.columnRight(table(2, 3), 1)!!
        assertEquals(4, t.columnCount)
        assertEquals(4, t.rows[0].size)
        assertTrue(kotlin.math.abs(t.columnWidthsTwips.sum() - 3000) <= 4)
    }

    @Test fun lastRowCannotBeDeleted() {
        assertNull(TableOps.deleteRow(table(1, 2), 0))
        assertEquals(1, TableOps.deleteRow(table(2, 2), 1)!!.rows.size)
    }

    @Test fun mergedTablesAreLeftAlone() {
        val merged = TableBlock(listOf(listOf(TableCell(colSpan = 2))), listOf(1000, 1000))
        assertNull(TableOps.rowBelow(merged, 0))
    }
}
