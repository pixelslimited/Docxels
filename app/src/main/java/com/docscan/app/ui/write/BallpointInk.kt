package com.docscan.app.ui.write

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One sampled touch point: position, time (ms) and stylus pressure (1 for a finger). */
class InkPt(val x: Float, val y: Float, val t: Long, val p: Float = 1f)

/**
 * Draws a signature the way a blue ball-point pen leaves it on paper:
 *  - the pen lands with a heavy, dark blob of ink that thins out as the hand gets going;
 *  - slow movement lays down a thicker, darker line, fast movement a thinner, paler one;
 *  - ink flow wavers slightly and the line lightens and tapers as the pen lifts;
 *  - a stylus's pressure widens the line (a finger has none).
 * The same routine draws the live preview and the saved image, so what you sign is what you get.
 */
object BallpointInk {
    // Ball-point blue: pale when the ink flows thin, deep when it pools.
    private const val LR = 66; private const val LG = 98; private const val LB = 205
    private const val DR = 14; private const val DG = 34; private const val DB = 140

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private class Dense(val x: Float, val y: Float, val v: Float, val p: Float)

    /** [unit] is the pixels-per-dp of the surface being drawn on, so the pen is the same size on any screen. */
    fun render(c: Canvas, pts: List<InkPt>, unit: Float) {
        if (pts.isEmpty()) return
        val base = 1.9f * unit
        if (pts.size == 1 || pathLength(pts) < 1.5f * unit) {
            val p = pts[0]
            paint.style = Paint.Style.FILL
            paint.color = argb(255, DR, DG, DB)
            c.drawCircle(p.x, p.y, base * 1.05f, paint)
            paint.style = Paint.Style.STROKE
            return
        }
        val dense = densify(pts, unit)
        var total = 0f
        for (i in 1 until dense.size) total += hypot(dense[i].x - dense[i - 1].x, dense[i].y - dense[i - 1].y)
        var d = 0f
        var w = base
        for (i in 1 until dense.size) {
            val a = dense[i - 1]
            val b = dense[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            d += seg
            // Speed: 0.2 px/ms is a careful stroke, 2.5 px/ms a flick.
            val slow = (1f - ((b.v - 0.2f) / 2.3f)).coerceIn(0f, 1f)
            var target = base * (0.62f + 0.78f * slow) * (0.6f + 0.8f * b.p)
            target *= 1f + 0.85f * exp(-d / (6f * unit))                 // heavy landing
            val toEnd = total - d
            if (toEnd < 7f * unit) target *= 0.62f + 0.38f * (toEnd / (7f * unit)) // lifting off
            w += (target - w) * 0.22f                                      // ink does not change width instantly
            // Ink flow: noise along the line, thinner when fast, pooled at the landing.
            val grain = noise((d / (1.6f * unit)).toInt())
            var flow = 0.55f + 0.45f * slow + grain * 0.28f
            flow += 0.5f * exp(-d / (9f * unit))
            if (toEnd < 5f * unit) flow -= 0.25f * (1f - toEnd / (5f * unit))
            // A rare, brief skip where a fast stroke runs dry.
            if (b.v > 1.4f && noise(1000 + (d / (3f * unit)).toInt()) > 0.93f) flow -= 0.35f
            flow = flow.coerceIn(0.05f, 1f)
            paint.strokeWidth = max(w, 0.6f * unit)
            paint.color = mix(flow)
            c.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    private fun pathLength(pts: List<InkPt>): Float {
        var s = 0f
        for (i in 1 until pts.size) s += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return s
    }

    /** Smooths the raw touch points with quadratic curves and resamples them about every [unit] pixels. */
    private fun densify(pts: List<InkPt>, unit: Float): List<Dense> {
        val n = pts.size
        // Speed of each raw point (px/ms), lightly smoothed.
        val v = FloatArray(n)
        for (i in 1 until n) {
            val dt = max(1L, pts[i].t - pts[i - 1].t).toFloat()
            val raw = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y) / dt
            v[i] = v[i - 1] * 0.6f + raw * 0.4f
        }
        v[0] = v[min(1, n - 1)]
        val out = ArrayList<Dense>(n * 3)
        out += Dense(pts[0].x, pts[0].y, v[0], pts[0].p)
        for (i in 0 until n) {
            val ax = if (i == 0) pts[0].x else (pts[i - 1].x + pts[i].x) / 2f
            val ay = if (i == 0) pts[0].y else (pts[i - 1].y + pts[i].y) / 2f
            val bx = if (i == n - 1) pts[i].x else (pts[i].x + pts[i + 1].x) / 2f
            val by = if (i == n - 1) pts[i].y else (pts[i].y + pts[i + 1].y) / 2f
            val cx = pts[i].x
            val cy = pts[i].y
            val len = hypot(cx - ax, cy - ay) + hypot(bx - cx, by - cy)
            val steps = max(1, (len / unit).toInt())
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                val u = 1f - t
                out += Dense(u * u * ax + 2 * u * t * cx + t * t * bx, u * u * ay + 2 * u * t * cy + t * t * by, v[i], pts[i].p)
            }
        }
        return out
    }

    /** Repeatable pseudo-random value in -1..1 for an integer position along the line. */
    private fun noise(n: Int): Float {
        var h = n * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 32767.5f) - 1f
    }

    private fun mix(f: Float): Int {
        val r = (LR + (DR - LR) * f).toInt()
        val g = (LG + (DG - LG) * f).toInt()
        val b = (LB + (DB - LB) * f).toInt()
        return argb(255, r, g, b)
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
