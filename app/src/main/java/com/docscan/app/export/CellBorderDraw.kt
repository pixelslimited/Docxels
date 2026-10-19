package com.docscan.app.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.docscan.app.core.word.CellEdges
import com.docscan.app.core.word.TableBlock
import com.docscan.app.core.word.TableCell

/** Draws a cell's four border lines, each as set on the cell or, failing that, on the table. [pxPerPt] converts points to pixels. */
object CellBorderDraw {
    fun draw(c: Canvas, r: RectF, cell: TableCell, t: TableBlock, pxPerPt: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        for (side in 0..3) {
            val b = CellEdges.line(t, cell, side) ?: continue
            paint.strokeWidth = (b.widthPt * pxPerPt).coerceAtLeast(1f)
            paint.color = 0xFF000000.toInt() or b.color
            when (side) {
                0 -> c.drawLine(r.left, r.top, r.right, r.top, paint)
                1 -> c.drawLine(r.left, r.top, r.left, r.bottom, paint)
                2 -> c.drawLine(r.left, r.bottom, r.right, r.bottom, paint)
                else -> c.drawLine(r.right, r.top, r.right, r.bottom, paint)
            }
        }
    }
}
