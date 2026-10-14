package com.docscan.app.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.docscan.app.core.word.ShapeBlock
import com.docscan.app.core.word.ShapePresets

/** Draws a [ShapeBlock] into [r]. Presets the app does not know are drawn as a plain box (the name is still kept in the file). */
object ShapeDraw {
    fun draw(c: Canvas, s: ShapeBlock, r: RectF, pxPerPt: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF000000.toInt() or s.lineColor
            strokeWidth = (s.lineWidthPt * pxPerPt).coerceAtLeast(1f)
            strokeJoin = Paint.Join.MITER
        }
        val lined = s.lineWidthPt > 0f
        val half = if (lined) stroke.strokeWidth / 2f else 0f
        val b = RectF(r.left + half, r.top + half, r.right - half, r.bottom - half)
        val path = Path()
        when {
            ShapePresets.isLine(s.preset) -> {
                if (lined) c.drawLine(r.left, r.centerY(), r.right, r.centerY(), stroke)
                return
            }
            s.preset == "ellipse" -> path.addOval(b, Path.Direction.CW)
            s.preset == "roundRect" -> {
                val rad = minOf(b.width(), b.height()) / 6f
                path.addRoundRect(b, rad, rad, Path.Direction.CW)
            }
            s.preset == "triangle" -> {
                path.moveTo(b.centerX(), b.top); path.lineTo(b.right, b.bottom); path.lineTo(b.left, b.bottom); path.close()
            }
            s.preset == "diamond" -> {
                path.moveTo(b.centerX(), b.top); path.lineTo(b.right, b.centerY()); path.lineTo(b.centerX(), b.bottom)
                path.lineTo(b.left, b.centerY()); path.close()
            }
            s.preset == "rightArrow" -> {
                val shaftTop = b.top + b.height() * 0.25f
                val shaftBottom = b.bottom - b.height() * 0.25f
                val head = b.right - minOf(b.height() * 0.5f, b.width() * 0.5f)
                path.moveTo(b.left, shaftTop); path.lineTo(head, shaftTop); path.lineTo(head, b.top); path.lineTo(b.right, b.centerY())
                path.lineTo(head, b.bottom); path.lineTo(head, shaftBottom); path.lineTo(b.left, shaftBottom); path.close()
            }
            else -> path.addRect(b, Path.Direction.CW)
        }
        s.fill?.let { fill.color = 0xFF000000.toInt() or it; c.drawPath(path, fill) }
        if (lined) c.drawPath(path, stroke)
    }
}
