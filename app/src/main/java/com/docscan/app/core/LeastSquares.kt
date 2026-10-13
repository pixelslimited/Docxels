package com.docscan.app.core

import kotlin.math.abs

/** Small least-squares helpers (pure Kotlin) used to fit page curvature. */
object LeastSquares {
    /**
     * Solves min |A·x − y|² through the normal equations. [rows] is the design matrix (n rows of m features).
     * Returns null when there are fewer samples than features or the system is degenerate.
     */
    fun solve(rows: List<DoubleArray>, y: DoubleArray): DoubleArray? {
        val n = rows.size
        if (n == 0 || n != y.size) return null
        val m = rows[0].size
        if (n < m) return null
        val ata = Array(m) { DoubleArray(m + 1) }
        for (r in 0 until n) {
            val row = rows[r]
            for (i in 0 until m) {
                for (j in 0 until m) ata[i][j] += row[i] * row[j]
                ata[i][m] += row[i] * y[r]
            }
        }
        for (i in 0 until m) ata[i][i] += 1e-9 // tiny ridge keeps near-singular systems solvable

        // Gaussian elimination with partial pivoting.
        for (col in 0 until m) {
            var pivot = col
            for (r in col + 1 until m) if (abs(ata[r][col]) > abs(ata[pivot][col])) pivot = r
            if (abs(ata[pivot][col]) < 1e-12) return null
            val tmp = ata[col]
            ata[col] = ata[pivot]
            ata[pivot] = tmp
            for (r in col + 1 until m) {
                val f = ata[r][col] / ata[col][col]
                if (f == 0.0) continue
                for (c in col..m) ata[r][c] -= f * ata[col][c]
            }
        }
        val x = DoubleArray(m)
        for (i in m - 1 downTo 0) {
            var sum = ata[i][m]
            for (j in i + 1 until m) sum -= ata[i][j] * x[j]
            x[i] = sum / ata[i][i]
        }
        return x
    }

    /** Polynomial fit; returns coefficients c0..cDegree (c0 is the constant term). Keep x roughly within -1..1. */
    fun polyFit(xs: DoubleArray, ys: DoubleArray, degree: Int): DoubleArray? {
        val rows = xs.map { x ->
            val row = DoubleArray(degree + 1)
            var p = 1.0
            for (i in 0..degree) {
                row[i] = p
                p *= x
            }
            row
        }
        return solve(rows, ys)
    }

    fun polyEval(c: DoubleArray, x: Double): Double {
        var result = 0.0
        for (i in c.indices.reversed()) result = result * x + c[i]
        return result
    }
}
