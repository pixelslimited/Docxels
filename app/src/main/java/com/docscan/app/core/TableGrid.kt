package com.docscan.app.core

import kotlin.math.abs

/** Axis-aligned cell rectangle in pixels. */
data class CellRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
}

/** Pure helpers that turn loose cell rectangles into table rows. */
object TableGrid {
    /** Groups cells into rows (top to bottom) and sorts each row left to right. */
    fun group(cells: List<CellRect>): List<List<CellRect>> {
        if (cells.isEmpty()) return emptyList()
        val medianHeight = cells.map { it.h }.sorted()[cells.size / 2]
        val rows = mutableListOf<MutableList<CellRect>>()
        for (cell in cells.sortedBy { it.cy }) {
            val row = rows.lastOrNull()
            if (row != null && abs(cell.cy - row.map { it.cy }.average()) < medianHeight * 0.5) {
                row.add(cell)
            } else {
                rows.add(mutableListOf(cell))
            }
        }
        return rows.map { row -> row.sortedBy { it.x } }
    }

    /** Pads ragged rows with empty strings so every row has the same number of columns. */
    fun pad(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return rows
        val columns = rows.maxOf { it.size }
        return rows.map { it + List(columns - it.size) { "" } }
    }

    /** Smallest rectangle containing every cell, or null when there are none. */
    fun bounds(cells: List<CellRect>): CellRect? {
        if (cells.isEmpty()) return null
        val left = cells.minOf { it.x }
        val top = cells.minOf { it.y }
        val right = cells.maxOf { it.x + it.w }
        val bottom = cells.maxOf { it.y + it.h }
        return CellRect(left, top, right - left, bottom - top)
    }
}
