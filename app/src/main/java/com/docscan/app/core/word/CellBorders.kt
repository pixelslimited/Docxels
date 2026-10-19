package com.docscan.app.core.word

/** Which line to draw on one side of a cell: the cell's own setting, else the table's. Sides: 0 top, 1 left, 2 bottom, 3 right. */
object CellEdges {
    fun line(t: TableBlock, cell: TableCell, side: Int): CellBorder? {
        val own = cell.borders?.side(side)
        val b = own ?: if (t.borders) CellBorder(true, t.borderColor, t.borderWidthPt) else null
        return b?.takeIf { it.visible }
    }

    /** Height a row asks for: its tallest cell. */
    fun rowHeight(row: List<TableCell>): Int = row.maxOfOrNull { it.rowHeightTwips } ?: 0
}
