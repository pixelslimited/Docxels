package com.docscan.app.core

/**
 * Turns a photo of a signature on white paper into ink on a transparent background. Pure Kotlin on ARGB pixels,
 * so it can be tested without a phone.
 */
object SignatureInk {
    /** White (and near-white) becomes transparent; dark strokes stay solid, with a soft edge in between. */
    fun whiteToAlpha(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        val a0 = p ushr 24
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        val alpha = when {
            lum >= 225 -> 0
            lum <= 140 -> 255
            else -> ((225 - lum) * 255 / 85)
        }
        ((alpha * a0 / 255) shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Smallest rectangle (left, top, right, bottom exclusive) that holds visible ink, with [pad] pixels around it; null if there is none. */
    fun inkBounds(pixels: IntArray, width: Int, height: Int, pad: Int = 4): IntArray? {
        var l = width; var t = height; var r = -1; var b = -1
        for (y in 0 until height) for (x in 0 until width) {
            if ((pixels[y * width + x] ushr 24) > 24) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        if (r < 0) return null
        return intArrayOf((l - pad).coerceAtLeast(0), (t - pad).coerceAtLeast(0), (r + 1 + pad).coerceAtMost(width), (b + 1 + pad).coerceAtMost(height))
    }
}
