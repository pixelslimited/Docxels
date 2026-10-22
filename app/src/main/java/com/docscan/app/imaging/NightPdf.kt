package com.docscan.app.imaging

import android.graphics.Bitmap

/**
 * Dark view of a PDF page. Plain colour inversion also turns logos, coloured text and highlights into their opposite
 * colours, so this only flips the greys: white paper becomes black and black text becomes white, while coloured pixels
 * (logos, red or blue text, highlights) keep their own colour, and so does dark text lying on a highlight.
 */
object NightPdf {
    private const val BLOCK = 8
    private const val SAT_FULL = 55      // max-min channel spread at which a pixel counts as coloured
    private const val SAT_NONE = 15      // at or below this a pixel is grey

    /** Returns the dark version of [src] (changes it in place when it is mutable). */
    fun darken(src: Bitmap): Bitmap {
        val bmp = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width; val h = bmp.height
        val bw = (w + BLOCK - 1) / BLOCK; val bh = (h + BLOCK - 1) / BLOCK
        val row = IntArray(w * BLOCK)

        // Pass 1: how much of each 8x8 block is coloured.
        val frac = FloatArray(bw * bh)
        for (by in 0 until bh) {
            val y0 = by * BLOCK; val rows = minOf(BLOCK, h - y0)
            bmp.getPixels(row, 0, w, 0, y0, w, rows)
            val count = IntArray(bw)
            for (y in 0 until rows) for (x in 0 until w) if (spread(row[y * w + x]) > SAT_FULL / 2) count[x / BLOCK]++
            for (bx in 0 until bw) frac[by * bw + bx] = count[bx] / (minOf(BLOCK, w - bx * BLOCK) * rows).toFloat()
        }
        // The neighbourhood of a block: its own and the eight around it. A grey pixel inside a mostly coloured
        // neighbourhood is text on a highlight (or part of a logo) and is left alone.
        val region = FloatArray(bw * bh)
        for (by in 0 until bh) for (bx in 0 until bw) {
            var sum = 0f; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val x = bx + dx; val y = by + dy
                if (x in 0 until bw && y in 0 until bh) { sum += frac[y * bw + x]; n++ }
            }
            region[by * bw + bx] = sum / n
        }

        // Pass 2: recolour.
        for (by in 0 until bh) {
            val y0 = by * BLOCK; val rows = minOf(BLOCK, h - y0)
            bmp.getPixels(row, 0, w, 0, y0, w, rows)
            for (y in 0 until rows) for (x in 0 until w) {
                val p = row[y * w + x]
                val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
                val reg = region[by * bw + x / BLOCK]
                val own = ((spread(p) - SAT_NONE).toFloat() / (SAT_FULL - SAT_NONE)).coerceIn(0f, 1f)
                val inColour = ((reg - 0.30f) / 0.20f).coerceIn(0f, 1f)
                val keep = maxOf(own, inColour)
                var nr = (r * keep + (255 - r) * (1 - keep)).toInt()
                var ng = (g * keep + (255 - g) * (1 - keep)).toInt()
                var nb = (b * keep + (255 - b) * (1 - keep)).toInt()
                // Coloured text that would almost vanish on black (dark blue links): lift it, keeping its hue.
                if (own > 0.5f && reg < 0.2f) {
                    val lum = (nr * 3 + ng * 6 + nb) / 10
                    if (lum < 70) { val k = (70 - lum) / 70f * 0.6f; nr += ((255 - nr) * k).toInt(); ng += ((255 - ng) * k).toInt(); nb += ((255 - nb) * k).toInt() }
                }
                row[y * w + x] = (p and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
            bmp.setPixels(row, 0, w, 0, y0, w, rows)
        }
        return bmp
    }

    private fun spread(p: Int): Int {
        val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
        return maxOf(r, g, b) - minOf(r, g, b)
    }
}
