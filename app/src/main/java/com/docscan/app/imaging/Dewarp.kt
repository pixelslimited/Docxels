package com.docscan.app.imaging

import com.docscan.app.core.LeastSquares
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Flattens curled or wavy paper. Text lines are found, each one is measured against a straight
 * line at its own average height, and a smooth surface fitted through all the measurements is
 * used to shift pixels vertically so every line becomes straight.
 *
 * Needs several long text lines. Pages without them (photos, mostly blank pages) are returned unchanged.
 */
object Dewarp {
    private const val WORK = 700.0
    private const val X_TERMS = 5      // x^0..x^4 across the page
    private const val Y_TERMS = 2      // y^0..y^1 down the page
    private const val MIN_LINES = 5
    private const val SAMPLES_PER_LINE = 14
    private const val MIN_SHIFT = 1.5  // working pixels; smaller than this is not worth touching
    private const val MAX_SHIFT_FRACTION = 0.06

    private class Field(val values: FloatArray, val width: Int, val height: Int, val scale: Double)

    /** Returns a new Mat; the input is never modified. */
    fun apply(rgb: Mat): Mat {
        val field = try {
            estimate(rgb)
        } catch (e: Exception) {
            null
        }
        val out = Mat()
        if (field == null) {
            rgb.copyTo(out)
            return out
        }
        val w = rgb.cols()
        val h = rgb.rows()
        val small = Mat(field.height, field.width, CvType.CV_32F)
        small.put(0, 0, field.values)
        val full = Mat()
        Imgproc.resize(small, full, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        Core.multiply(full, Scalar.all(1.0 / field.scale), full)

        val rowIndex = Mat(1, w, CvType.CV_32F)
        rowIndex.put(0, 0, FloatArray(w) { it.toFloat() })
        val colIndex = Mat(h, 1, CvType.CV_32F)
        colIndex.put(0, 0, FloatArray(h) { it.toFloat() })
        val mapX = Mat()
        val gridY = Mat()
        val mapY = Mat()
        Core.repeat(rowIndex, h, 1, mapX)
        Core.repeat(colIndex, 1, w, gridY)
        Core.add(gridY, full, mapY)
        Imgproc.remap(rgb, out, mapX, mapY, Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        listOf(small, full, rowIndex, colIndex, mapX, gridY, mapY).forEach { it.release() }
        return out
    }

    private fun estimate(rgb: Mat): Field? {
        val scale = WORK / max(rgb.cols(), rgb.rows())
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        gray.release()
        val w = small.cols()
        val h = small.rows()

        val ink = Mat()
        Imgproc.adaptiveThreshold(small, ink, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 15.0)
        small.release()
        val margin = (min(w, h) * 0.03).toInt().coerceAtLeast(1)
        ink.submat(0, margin, 0, w).setTo(Scalar(0.0))
        ink.submat(h - margin, h, 0, w).setTo(Scalar(0.0))
        ink.submat(0, h, 0, margin).setTo(Scalar(0.0))
        ink.submat(0, h, w - margin, w).setTo(Scalar(0.0))

        // Merge the letters of a line into one blob (wide, flat kernel).
        var kw = max(15, (w * 0.05).toInt())
        if (kw % 2 == 0) kw += 1
        val blobs = Mat()
        Imgproc.dilate(ink, blobs, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kw.toDouble(), 3.0)))
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(blobs, labels, stats, centroids, 8, CvType.CV_32S)
        val statData = IntArray(count * 5)
        stats.get(0, 0, statData)

        val lineOfLabel = IntArray(count) { -1 }
        var lines = 0
        for (i in 1 until count) {
            val bw = statData[i * 5 + Imgproc.CC_STAT_WIDTH]
            val bh = statData[i * 5 + Imgproc.CC_STAT_HEIGHT]
            if (bw >= w * 0.3 && bh <= h * 0.06) lineOfLabel[i] = lines++
        }
        if (lines < MIN_LINES) {
            listOf(ink, blobs, labels, stats, centroids).forEach { it.release() }
            return null
        }

        val labelData = IntArray(w * h)
        labels.get(0, 0, labelData)
        val inkData = ByteArray(w * h)
        ink.get(0, 0, inkData)
        listOf(ink, blobs, labels, stats, centroids).forEach { it.release() }

        val sums = DoubleArray(lines * w)
        val counts = IntArray(lines * w)
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                if (inkData[rowStart + x].toInt() == 0) continue
                val line = lineOfLabel[labelData[rowStart + x]]
                if (line < 0) continue
                sums[line * w + x] += y.toDouble()
                counts[line * w + x]++
            }
        }

        // Per line: smooth curve through the ink centre of each column, sampled at a few places.
        val sampleX = ArrayList<Double>()
        val sampleY = ArrayList<Double>()
        val sampleShift = ArrayList<Double>()
        for (line in 0 until lines) {
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            for (x in 0 until w) {
                val c = counts[line * w + x]
                if (c > 0) {
                    xs += (x + 0.5) / w - 0.5
                    ys += sums[line * w + x] / c
                }
            }
            if (xs.size < w * 0.2) continue
            val curve = LeastSquares.polyFit(xs.toDoubleArray(), ys.toDoubleArray(), 2) ?: continue
            val x0 = xs.first()
            val x1 = xs.last()
            val gx = DoubleArray(SAMPLES_PER_LINE) { x0 + (x1 - x0) * it / (SAMPLES_PER_LINE - 1) }
            val gy = DoubleArray(SAMPLES_PER_LINE) { LeastSquares.polyEval(curve, gx[it]) }
            val mean = gy.average()
            for (i in gx.indices) {
                sampleX += gx[i]
                sampleY += mean / h - 0.5
                sampleShift += gy[i] - mean
            }
        }
        if (sampleX.size < MIN_LINES * SAMPLES_PER_LINE) return null

        fun features(x: Double, y: Double): DoubleArray {
            val f = DoubleArray(X_TERMS * Y_TERMS)
            var py = 1.0
            for (j in 0 until Y_TERMS) {
                var px = 1.0
                for (i in 0 until X_TERMS) {
                    f[j * X_TERMS + i] = px * py
                    px *= x
                }
                py *= y
            }
            return f
        }

        val rows = sampleX.indices.map { features(sampleX[it], sampleY[it]) }
        val target = sampleShift.toDoubleArray()
        var coef = LeastSquares.solve(rows, target) ?: return null
        // Refit without the outliers (headings, stamps, lines that merged) for a robust surface.
        val residuals = DoubleArray(target.size) { i -> abs(target[i] - rows[i].indices.sumOf { rows[i][it] * coef[it] }) }
        val median = residuals.sorted()[residuals.size / 2]
        val limit = max(1.0, 3 * median)
        val keep = residuals.indices.filter { residuals[it] <= limit }
        if (keep.size >= residuals.size / 2) {
            coef = LeastSquares.solve(keep.map { rows[it] }, keep.map { target[it] }.toDoubleArray()) ?: coef
        }

        val cap = h * MAX_SHIFT_FRACTION
        val values = FloatArray(w * h)
        val rowTerm = Array(Y_TERMS) { DoubleArray(w) }
        for (x in 0 until w) {
            val xn = (x + 0.5) / w - 0.5
            for (j in 0 until Y_TERMS) {
                var px = 1.0
                var sum = 0.0
                for (i in 0 until X_TERMS) {
                    sum += coef[j * X_TERMS + i] * px
                    px *= xn
                }
                rowTerm[j][x] = sum
            }
        }
        var biggest = 0.0
        for (y in 0 until h) {
            val yn = (y + 0.5) / h - 0.5
            for (x in 0 until w) {
                val v = (rowTerm[0][x] + yn * rowTerm[1][x]).coerceIn(-cap, cap)
                values[y * w + x] = v.toFloat()
                if (abs(v) > biggest) biggest = abs(v)
            }
        }
        if (biggest < MIN_SHIFT) return null
        return Field(values, w, h, scale)
    }
}
