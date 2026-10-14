package com.docscan.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureInkTest {
    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF101020.toInt()

    @Test fun whiteBecomesTransparentAndInkStaysSolid() {
        val out = SignatureInk.whiteToAlpha(intArrayOf(white, ink))
        assertEquals(0, out[0] ushr 24)
        assertEquals(255, out[1] ushr 24)
    }

    @Test fun boundsTrimEmptyMargins() {
        // 6x4 image with ink only at (2,1) and (3,2)
        val px = IntArray(24)
        px[1 * 6 + 2] = ink
        px[2 * 6 + 3] = ink
        assertArrayEquals(intArrayOf(1, 0, 5, 4), SignatureInk.inkBounds(px, 6, 4, pad = 1))
    }

    @Test fun blankImageHasNoBounds() {
        assertNull(SignatureInk.inkBounds(IntArray(16), 4, 4))
    }
}
