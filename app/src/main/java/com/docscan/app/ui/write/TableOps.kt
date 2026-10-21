package com.docscan.app.ui.write

import com.docscan.app.core.word.TableBlock
import com.docscan.app.core.word.TableCell

/** Quick table edits for the floating tools. They refuse (return null) when the table has merged cells. */
object TableOps {
    private fun merged(t: TableBlock) = t.rows.any { r -> r.any { it.colSpan > 1 || it.vMergeStart || it.vMergeContinue } }

    fun rowBelow(t: TableBlock, row: Int): TableBlock? {
        if (merged(t) || row !in t.rows.indices) return null
        val cols = t.columnCount
        return t.copy(rows = t.rows.toMutableList().also { it.add(row + 1, List(cols) { TableCell() }) })
    }

    fun columnRight(t: TableBlock, col: Int): TableBlock? {
        if (merged(t) || col !in 0 until t.columnCount) return null
        val n = t.columnCount
        val total = t.columnWidthsTwips.sum()
        val widths = t.columnWidthsTwips.map { (it.toLong() * n / (n + 1)).toInt().coerceAtLeast(240) }.toMutableList()
        widths.add(col + 1, (total / (n + 1)).coerceAtLeast(240))
        return t.copy(
            rows = t.rows.map { r -> r.toMutableList().also { it.add(col + 1, TableCell()) } },
            columnWidthsTwips = widths,
        )
    }

    fun deleteRow(t: TableBlock, row: Int): TableBlock? {
        if (merged(t) || t.rows.size < 2 || row !in t.rows.indices) return null
        return t.copy(rows = t.rows.filterIndexed { i, _ -> i != row })
    }
}
