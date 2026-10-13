package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableGridTest {
    @Test
    fun group_buildsRowsTopToBottomAndSortsLeftToRight() {
        val cells = listOf(
            CellRect(100, 0, 90, 30), CellRect(0, 0, 90, 30),
            CellRect(100, 32, 90, 30), CellRect(0, 32, 90, 30),
        )
        val rows = TableGrid.group(cells)
        assertEquals(2, rows.size)
        assertEquals(listOf(0, 100), rows[0].map { it.x })
        assertEquals(listOf(0, 100), rows[1].map { it.x })
        assertEquals(0, rows[0][0].y)
    }

    @Test
    fun group_toleratesSmallVerticalJitterWithinARow() {
        val cells = listOf(CellRect(0, 0, 50, 30), CellRect(60, 4, 50, 30), CellRect(0, 40, 50, 30))
        assertEquals(listOf(2, 1), TableGrid.group(cells).map { it.size })
    }

    @Test
    fun group_emptyInput_isEmpty() {
        assertEquals(emptyList<List<CellRect>>(), TableGrid.group(emptyList()))
    }

    @Test
    fun pad_makesRowsRectangular() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "")), TableGrid.pad(listOf(listOf("a", "b"), listOf("c"))))
        assertEquals(emptyList<List<String>>(), TableGrid.pad(emptyList()))
    }

    @Test
    fun bounds_isUnionOfCells() {
        val b = TableGrid.bounds(listOf(CellRect(10, 20, 30, 40), CellRect(50, 5, 10, 10)))!!
        assertEquals(CellRect(10, 5, 50, 55), b)
        assertNull(TableGrid.bounds(emptyList()))
    }
}

class PageNumberParserTest {
    @Test
    fun parsesCommonFooterFormats() {
        assertEquals(3, PageNumberParser.parse("3"))
        assertEquals(3, PageNumberParser.parse("  Page 3 "))
        assertEquals(12, PageNumberParser.parse("p. 12"))
        assertEquals(4, PageNumberParser.parse("4 of 10"))
        assertEquals(4, PageNumberParser.parse("4/10"))
        assertEquals(7, PageNumberParser.parse("- 7 -"))
    }

    @Test
    fun ignoresOrdinaryText() {
        assertNull(PageNumberParser.parse("Invoice 2024"))
        assertNull(PageNumberParser.parse("Total: 300"))
        assertNull(PageNumberParser.parse("1234"))
        assertNull(PageNumberParser.parse(""))
    }
}

class ArrangeLogicTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun sortsByPageNumbersWhenEveryPageHasOne() {
        assertEquals(listOf("c", "a", "d", "b"), ArrangeLogic.order(ids, mapOf("a" to 2, "b" to 4, "c" to 1, "d" to 3)))
    }

    @Test
    fun unnumberedPagesKeepTheirSlot() {
        assertEquals(listOf("c", "b", "a"), ArrangeLogic.order(listOf("a", "b", "c"), mapOf("a" to 3, "c" to 1)))
    }

    @Test
    fun tooFewNumbers_keepsCaptureOrder() {
        assertEquals(ids, ArrangeLogic.order(ids, mapOf("a" to 2)))
        assertEquals(ids, ArrangeLogic.order(ids, emptyMap()))
    }

    @Test
    fun duplicateNumbers_keepCaptureOrder() {
        assertEquals(ids, ArrangeLogic.order(ids, mapOf("a" to 1, "b" to 1, "c" to 2, "d" to 3)))
    }
}
