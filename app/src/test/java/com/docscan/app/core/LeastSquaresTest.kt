package com.docscan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LeastSquaresTest {
    @Test
    fun polyFit_recoversQuadratic() {
        val xs = DoubleArray(20) { -0.5 + it / 19.0 }
        val ys = DoubleArray(20) { 3.0 - 2.0 * xs[it] + 5.0 * xs[it] * xs[it] }
        val c = LeastSquares.polyFit(xs, ys, 2)
        assertNotNull(c)
        assertEquals(3.0, c!![0], 1e-6)
        assertEquals(-2.0, c[1], 1e-6)
        assertEquals(5.0, c[2], 1e-6)
        assertEquals(3.0 - 2.0 * 0.25 + 5.0 * 0.0625, LeastSquares.polyEval(c, 0.25), 1e-6)
    }

    @Test
    fun solve_recoversLinearModelWithTwoFeatures() {
        val rows = ArrayList<DoubleArray>()
        val y = ArrayList<Double>()
        for (a in 0..4) for (b in 0..4) {
            rows += doubleArrayOf(1.0, a.toDouble(), b.toDouble())
            y += 1.5 + 2.0 * a - 0.5 * b
        }
        val x = LeastSquares.solve(rows, y.toDoubleArray())
        assertNotNull(x)
        assertEquals(1.5, x!![0], 1e-6)
        assertEquals(2.0, x[1], 1e-6)
        assertEquals(-0.5, x[2], 1e-6)
    }

    @Test
    fun solve_returnsNullWhenUnderdetermined() {
        assertNull(LeastSquares.solve(listOf(doubleArrayOf(1.0, 2.0, 3.0)), doubleArrayOf(1.0)))
    }
}
