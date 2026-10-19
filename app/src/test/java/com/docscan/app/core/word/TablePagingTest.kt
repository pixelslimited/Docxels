package com.docscan.app.core.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TablePagingTest {
    @Test fun fitsOnOnePage() {
        val p = TablePaging.plan(listOf(10f, 10f, 10f), header = true, startY = 0f, pageH = 100f, gap = 20f)
        assertTrue(p.breaks.isEmpty())
        assertEquals(30f, p.height, 0.001f)
        assertEquals(listOf(0f, 10f, 20f), p.rowTops)
    }

    @Test fun breaksBetweenRowsAndRepeatsHeader() {
        // Page text area 100 high, table starts 60 down. Header 10 -> y 70, row 1 (20) -> y 90, row 2 (20) does not fit: new page.
        val p = TablePaging.plan(listOf(10f, 20f, 20f, 20f), header = true, startY = 60f, pageH = 100f, gap = 30f)
        assertEquals(1, p.breaks.size)
        val b = p.breaks[0]
        assertEquals(2, b.row)
        assertEquals(40f, b.gapStart, 0.001f)          // 10 + 20 drawn, then the 10 left on the page
        assertEquals(70f, b.headerAt!!, 0.001f)        // gap of 30 after the page end
        assertEquals(80f, p.rowTops[2], 0.001f)        // header repeated (10) then the row
        assertEquals(100f, p.rowTops[3], 0.001f)
        assertEquals(120f, p.height, 0.001f)
    }

    @Test fun noHeaderRepeatWithoutHeaderRow() {
        val p = TablePaging.plan(listOf(40f, 40f, 40f), header = false, startY = 0f, pageH = 100f, gap = 10f)
        assertEquals(1, p.breaks.size)
        assertNull(p.breaks[0].headerAt)
        // 80 used on page one, 20 left unused, then the gap: row 2 starts at 80 + 20 + 10
        assertEquals(110f, p.rowTops[2], 0.001f)
        assertEquals(150f, p.height, 0.001f)
    }

    @Test fun firstRowThatDoesNotFitMovesToNextPage() {
        val p = TablePaging.plan(listOf(30f, 30f), header = false, startY = 80f, pageH = 100f, gap = 10f)
        assertEquals(1, p.breaks.size)
        assertEquals(0, p.breaks[0].row)
        assertEquals(20f, p.breaks[0].gapStart, 0.001f)
        assertEquals(30f, p.rowTops[0], 0.001f)
    }

    @Test fun rowTallerThanPageIsCutToOnePage() {
        val p = TablePaging.plan(listOf(500f), header = false, startY = 0f, pageH = 100f, gap = 10f)
        assertEquals(100f, p.rowHeights[0], 0.001f)
        assertEquals(100f, p.height, 0.001f)
    }
}
