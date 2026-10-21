package com.docscan.app.imaging

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundCleanupTest {
    @Test fun smallPiecesAreDroppedAndTheMainSubjectIsKept() {
        val w = 20
        val h = 20
        val a = ByteArray(w * h)
        for (y in 4..15) for (x in 4..15) a[y * w + x] = 1 // big block, 144 cells
        a[1 * w + 1] = 1 // a speck
        val out = BackgroundRemover.keepMainPieces(a, w, h)
        assertEquals(144, out.count { it.toInt() != 0 })
        assertEquals(0, out[1 * w + 1].toInt())
    }

    @Test fun aSecondLargePieceIsKept() {
        val w = 30
        val h = 10
        val a = ByteArray(w * h)
        for (y in 1..8) for (x in 1..8) a[y * w + x] = 1
        for (y in 1..8) for (x in 20..25) a[y * w + x] = 1
        val out = BackgroundRemover.keepMainPieces(a, w, h)
        assertEquals(a.count { it.toInt() != 0 }, out.count { it.toInt() != 0 })
    }

    @Test fun pinholesAreFilledButEdgeBackgroundIsNot() {
        val w = 40
        val h = 40
        val a = ByteArray(w * h) { 1 }
        a[20 * w + 20] = 0 // a pinhole inside
        for (x in 0 until w) a[x] = 0 // top row is background touching the edge
        val out = BackgroundRemover.fillSmallHoles(a, w, h)
        assertEquals(1, out[20 * w + 20].toInt())
        assertEquals(0, out[5].toInt())
    }
}
