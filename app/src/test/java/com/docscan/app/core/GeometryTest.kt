package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val tl = Pt(0.1f, 0.1f)
    private val tr = Pt(0.9f, 0.15f)
    private val br = Pt(0.85f, 0.9f)
    private val bl = Pt(0.12f, 0.88f)

    @Test
    fun orderCorners_sortsAnyInputOrderIntoTlTrBrBl() {
        val expected = listOf(tl, tr, br, bl)
        assertEquals(expected, Geometry.orderCorners(listOf(br, tl, bl, tr)))
        assertEquals(expected, Geometry.orderCorners(listOf(bl, br, tr, tl)))
        assertEquals(expected, Geometry.orderCorners(expected))
    }

    @Test(expected = IllegalArgumentException::class)
    fun orderCorners_rejectsWrongPointCount() {
        Geometry.orderCorners(listOf(tl, tr, br))
    }

    @Test
    fun isConvexQuad_acceptsNormalQuad_rejectsBowtieAndTinyAndCollinear() {
        assertTrue(Geometry.isConvexQuad(listOf(tl, tr, br, bl)))
        assertFalse(Geometry.isConvexQuad(listOf(tl, br, tr, bl))) // bow-tie
        assertFalse(Geometry.isConvexQuad(listOf(Pt(0.5f, 0.5f), Pt(0.51f, 0.5f), Pt(0.51f, 0.51f), Pt(0.5f, 0.51f)))) // tiny
        assertFalse(Geometry.isConvexQuad(listOf(Pt(0f, 0f), Pt(0.5f, 0f), Pt(1f, 0f), Pt(1f, 1f)))) // collinear
        assertFalse(Geometry.isConvexQuad(listOf(tl, tr, br)))
    }

    @Test
    fun outputSize_usesLongerOppositeSidesAndCapsLongestSide() {
        val px = listOf(Pt(0f, 0f), Pt(1000f, 0f), Pt(1000f, 2000f), Pt(0f, 2000f))
        assertEquals(1000 to 2000, Geometry.outputSize(px, 4000))
        assertEquals(500 to 1000, Geometry.outputSize(px, 1000))
    }

    @Test
    fun outputSize_neverReturnsZero() {
        val degenerate = listOf(Pt(0f, 0f), Pt(0f, 0f), Pt(0f, 0f), Pt(0f, 0f))
        val (w, h) = Geometry.outputSize(degenerate, 100)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test
    fun moveCorner_clampsToImageBounds() {
        val moved = Geometry.moveCorner(Geometry.FullImage, 0, -0.5f, 2f)
        assertEquals(Pt(0f, 1f), moved[0])
        assertEquals(Geometry.FullImage[1], moved[1])
    }

    @Test
    fun moveSide_movesBothAdjacentCornersOnly() {
        val moved = Geometry.moveSide(Geometry.DefaultCorners, 1, -0.1f, 0f) // right side
        assertEquals(Geometry.DefaultCorners[0], moved[0])
        assertEquals(Pt(0.85f, 0.05f), moved[1].let { Pt(Math.round(it.x * 100) / 100f, it.y) })
        assertEquals(Pt(0.85f, 0.95f), moved[2].let { Pt(Math.round(it.x * 100) / 100f, it.y) })
        assertEquals(Geometry.DefaultCorners[3], moved[3])
    }

    @Test
    fun moveSide_leftSideWrapsAroundToCornerZero() {
        val moved = Geometry.moveSide(Geometry.FullImage, 3, 0.2f, 0f)
        assertEquals(0.2f, moved[0].x, 1e-6f)
        assertEquals(0.2f, moved[3].x, 1e-6f)
        assertEquals(1f, moved[1].x, 1e-6f)
    }

    @Test
    fun encodeDecode_roundTrips() {
        val corners = listOf(tl, tr, br, bl)
        assertEquals(corners, Geometry.decode(Geometry.encode(corners)))
    }

    @Test
    fun decode_rejectsMalformedInput() {
        assertNull(Geometry.decode(null))
        assertNull(Geometry.decode(""))
        assertNull(Geometry.decode("0.1,0.2;0.3,0.4"))
        assertNull(Geometry.decode("a,b;c,d;e,f;g,h"))
        assertNotNull(Geometry.decode("0,0;1,0;1,1;0,1"))
    }

    @Test
    fun unrotate_isInverseOfClockwiseRotation() {
        // A point at the top-left of the original ends up at the top-right after a 90 degree clockwise turn.
        assertEquals(Pt(0f, 0f), Geometry.unrotate(Pt(1f, 0f), 90))
        assertEquals(Pt(0f, 0f), Geometry.unrotate(Pt(1f, 1f), 180))
        assertEquals(Pt(0f, 0f), Geometry.unrotate(Pt(0f, 1f), 270))
        assertEquals(Pt(0.3f, 0.7f), Geometry.unrotate(Pt(0.3f, 0.7f), 0))
        assertEquals(Pt(0.3f, 0.7f), Geometry.unrotate(Pt(0.3f, 0.7f), 360))
    }
}
