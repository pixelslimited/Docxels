package com.docscan.app.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point in normalized image space (0..1 on both axes) unless stated otherwise. */
data class Pt(val x: Float, val y: Float)

/** Pure geometry helpers (no Android dependencies, unit-testable on the JVM). */
object Geometry {
    /** Slightly inset rectangle used when nothing better is known. */
    val DefaultCorners = listOf(Pt(0.05f, 0.05f), Pt(0.95f, 0.05f), Pt(0.95f, 0.95f), Pt(0.05f, 0.95f))

    /** The whole image: no cropping. */
    val FullImage = listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))

    /** Orders four points as top-left, top-right, bottom-right, bottom-left. */
    fun orderCorners(points: List<Pt>): List<Pt> {
        require(points.size == 4) { "Exactly four points are required" }
        val sums = points.map { it.x + it.y }
        val diffs = points.map { it.y - it.x }
        val ordered = listOf(
            points[sums.indices.minByOrNull { sums[it] }!!],
            points[diffs.indices.minByOrNull { diffs[it] }!!],
            points[sums.indices.maxByOrNull { sums[it] }!!],
            points[diffs.indices.maxByOrNull { diffs[it] }!!],
        )
        return if (ordered.toSet().size == 4) ordered else points
    }

    fun distance(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)

    /** Shoelace area of a polygon given in order. */
    fun area(c: List<Pt>): Float {
        var sum = 0f
        for (i in c.indices) {
            val a = c[i]
            val b = c[(i + 1) % c.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    /** True when the four corners form a convex, non-degenerate quadrilateral. */
    fun isConvexQuad(c: List<Pt>): Boolean {
        if (c.size != 4) return false
        var sign = 0
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            val s = when {
                cross > 1e-6f -> 1
                cross < -1e-6f -> -1
                else -> 0
            }
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return area(c) > 0.01f
    }

    /**
     * Output size for a perspective warp. [corners] are TL, TR, BR, BL in pixels.
     * The longer side is capped at [maxSide].
     */
    fun outputSize(corners: List<Pt>, maxSide: Int): Pair<Int, Int> {
        val (tl, tr, br, bl) = corners
        val w = max(distance(tl, tr), distance(bl, br))
        val h = max(distance(tl, bl), distance(tr, br))
        val longest = max(w, h)
        val scale = if (longest > maxSide && longest > 0f) maxSide / longest else 1f
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

    fun moveCorner(c: List<Pt>, index: Int, dx: Float, dy: Float): List<Pt> =
        c.mapIndexed { i, p -> if (i == index) Pt(clamp01(p.x + dx), clamp01(p.y + dy)) else p }

    /** Sides: 0 = top (corners 0,1), 1 = right (1,2), 2 = bottom (2,3), 3 = left (3,0). */
    fun moveSide(c: List<Pt>, side: Int, dx: Float, dy: Float): List<Pt> {
        val a = side % 4
        val b = (side + 1) % 4
        return c.mapIndexed { i, p -> if (i == a || i == b) Pt(clamp01(p.x + dx), clamp01(p.y + dy)) else p }
    }

    fun encode(c: List<Pt>): String = c.joinToString(";") { "${it.x},${it.y}" }

    fun decode(s: String?): List<Pt>? {
        if (s.isNullOrBlank()) return null
        val pts = s.split(";").mapNotNull { part ->
            val xy = part.split(",")
            val x = xy.getOrNull(0)?.toFloatOrNull()
            val y = xy.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) Pt(x, y) else null
        }
        return if (pts.size == 4) pts else null
    }

    /**
     * Maps a normalized point seen on an image that was rotated clockwise by [rotation] degrees
     * back to the coordinates of the un-rotated image.
     */
    fun unrotate(p: Pt, rotation: Int): Pt = when (((rotation % 360) + 360) % 360) {
        90 -> Pt(p.y, 1f - p.x)
        180 -> Pt(1f - p.x, 1f - p.y)
        270 -> Pt(1f - p.y, p.x)
        else -> p
    }
}
