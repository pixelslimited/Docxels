package com.docscan.app.imaging

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Flattens curled, wavy or tilted-in-places paper so text lines run level like a flatbed scan.
 *
 * It measures which way the text is running at every part of the page (a structure-tensor "orientation field" of the
 * ink, smoothed and filled in where there is no text), adds that slope up across each row to get how far each point
 * sits above or below its line, and shifts pixels vertically to cancel it. Because the first pass leaves a little
 * error (the lines were followed along a straight row, not along themselves), it is repeated a few times on the
 * small working copy; the real picture is resampled only once.
 *
 * Pages without enough text (photos, mostly blank pages) are returned unchanged.
 */
object Dewarp {
    private const val WORK = 700.0
    private const val ITERATIONS = 3
    private const val MIN_SHIFT = 1.0            // working pixels; below this a pass is not worth applying
    private const val MAX_SHIFT_FRACTION = 0.08  // of the page height
    private const val MIN_TEXT_FRACTION = 0.03   // share of the page that must show clear text lines
    private const val MAX_SLOPE = 0.6

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
        val g0 = Mat()
        Imgproc.resize(gray, g0, Size(), scale, scale, Imgproc.INTER_AREA)
        gray.release()
        val w = g0.cols()
        val h = g0.rows()
        val n = w * h

        val rowIndex = FloatArray(n) { (it / w).toFloat() }
        val mapX = Mat(h, w, CvType.CV_32F)
        mapX.put(0, 0, FloatArray(n) { (it % w).toFloat() })
        var rows = Mat(h, w, CvType.CV_32F)
        rows.put(0, 0, rowIndex)
        var current = g0.clone()
        var changed = false

        for (pass in 0 until ITERATIONS) {
            val shift = slopeIntegral(current, w, h) ?: break
            if (shift.maxOf { abs(it) } < MIN_SHIFT) break
            changed = true
            val mapY = Mat(h, w, CvType.CV_32F)
            mapY.put(0, 0, FloatArray(n) { rowIndex[it] + shift[it] })
            val composed = Mat()
            Imgproc.remap(rows, composed, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            rows.release()
            rows = composed
            val next = Mat()
            Imgproc.remap(g0, next, mapX, rows, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            current.release()
            current = next
            mapY.release()
        }
        val total = FloatArray(n)
        rows.get(0, 0, total)
        listOf(g0, mapX, rows, current).forEach { it.release() }
        if (!changed) return null

        val cap = (h * MAX_SHIFT_FRACTION).toFloat()
        for (i in 0 until n) total[i] = (total[i] - rowIndex[i]).coerceIn(-cap, cap)
        return Field(total, w, h, scale)
    }

    /**
     * One pass: how far each pixel of [gray] sits off its text line, as a vertical shift in working pixels
     * (add it to the row number to find where to read from). Null when the page has too little text to judge.
     */
    private fun slopeIntegral(gray: Mat, w: Int, h: Int): FloatArray? {
        val n = w * h
        val ink = Mat()
        Imgproc.adaptiveThreshold(gray, ink, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 15.0)
        val margin = (min(w, h) * 0.03).toInt().coerceAtLeast(1)
        ink.submat(0, margin, 0, w).setTo(Scalar(0.0))
        ink.submat(h - margin, h, 0, w).setTo(Scalar(0.0))
        ink.submat(0, h, 0, margin).setTo(Scalar(0.0))
        ink.submat(0, h, w - margin, w).setTo(Scalar(0.0))
        val inkF = Mat()
        ink.convertTo(inkF, CvType.CV_32F, 1.0 / 255.0)
        ink.release()

        // Words merge into streaks along the line, so edges of the streaks show the line direction rather than the letters.
        val streak = Mat()
        Imgproc.GaussianBlur(inkF, streak, Size(0.0, 0.0), 9.0, 2.0)
        val gx = Mat()
        val gy = Mat()
        Imgproc.Sobel(streak, gx, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(streak, gy, CvType.CV_32F, 0, 1, 3)
        streak.release()
        val jxx = window(gx, gx)
        val jyy = window(gy, gy)
        val jxy = window(gx, gy)
        listOf(gx, gy).forEach { it.release() }
        val density = blurred(inkF, 12.0)
        val centre = blurred(inkF, 4.0)
        inkF.release()

        val slope = FloatArray(n)
        val weight = FloatArray(n)
        val energy = FloatArray(n) { jxx[it] + jyy[it] }
        val sortedEnergy = energy.copyOf().also { it.sort() }
        val energyFloor = sortedEnergy[n / 2]
        var clear = 0
        for (i in 0 until n) {
            val a = jxx[i] - jyy[i]
            val b = 2f * jxy[i]
            val coherence = Math.sqrt((a * a + b * b).toDouble()).toFloat() / (energy[i] + 1e-9f)
            val theta = 0.5 * atan2(b.toDouble(), a.toDouble())
            val s = sin(theta)
            // The ink streaks run perpendicular to the strongest gradient (cos, sin); their slope is -cos/sin.
            slope[i] = if (abs(s) < 1e-3) 0f else (-cos(theta) / s).coerceIn(-MAX_SLOPE, MAX_SLOPE).toFloat()
            weight[i] = if (energy[i] >= energyFloor && density[i] > 0.01f) coherence * coherence else 0f
            if (weight[i] > 0.3f) clear++
        }
        if (clear < n * MIN_TEXT_FRACTION) return null

        // Normalised smoothing: average the slope where there is text and spread it into the gaps.
        val weighted = FloatArray(n) { slope[it] * weight[it] }
        val num = blurredArray(weighted, w, h, 30.0)
        val den = blurredArray(weight, w, h, 30.0)
        val shift = FloatArray(n)
        for (y in 0 until h) {
            var run = 0f
            val start = y * w
            for (x in 0 until w) {
                val i = start + x
                run += if (den[i] > 1e-3f) num[i] / den[i] else 0f
                shift[i] = run
            }
            // Keep each row's text where it was on average, so only the bend is removed.
            var sum = 0.0
            var mass = 0.0
            for (x in 0 until w) {
                val m = centre[start + x] + 1e-3
                sum += shift[start + x] * m
                mass += m
            }
            val mean = (sum / mass).toFloat()
            for (x in 0 until w) shift[start + x] -= mean
        }
        val smooth = Mat(h, w, CvType.CV_32F)
        smooth.put(0, 0, shift)
        Imgproc.GaussianBlur(smooth, smooth, Size(0.0, 0.0), 3.0, 6.0)
        smooth.get(0, 0, shift)
        smooth.release()
        return shift
    }

    /** Gaussian-weighted sum of the product of two gradient images (one term of the structure tensor). */
    private fun window(p: Mat, q: Mat): FloatArray {
        val prod = Mat()
        Core.multiply(p, q, prod)
        val out = blurred(prod, 12.0)
        prod.release()
        return out
    }

    private fun blurred(src: Mat, sigma: Double): FloatArray {
        val dst = Mat()
        Imgproc.GaussianBlur(src, dst, Size(0.0, 0.0), sigma)
        val data = FloatArray(src.rows() * src.cols())
        dst.get(0, 0, data)
        dst.release()
        return data
    }

    private fun blurredArray(data: FloatArray, w: Int, h: Int, sigma: Double): FloatArray {
        val m = Mat(h, w, CvType.CV_32F)
        m.put(0, 0, data)
        val out = blurred(m, sigma)
        m.release()
        return out
    }
}
