package com.docscan.app.imaging

import android.graphics.Bitmap
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the page quadrilateral in a photo. Several independent strategies each propose quads
 * (edges filled into blobs, and bright/dark region segmentation); the best-scoring one wins.
 * Returns null when no confident quad is found.
 */
object EdgeDetector {
    private const val WORK_SIDE = 800.0
    private const val MIN_AREA = 0.20
    private const val MAX_AREA = 0.95

    private class Candidate(val score: Double, val quad: List<Pt>)

    fun detect(bitmap: Bitmap): List<Pt>? {
        val rgb = bitmap.toRgbMat()
        return try {
            detect(rgb)
        } finally {
            rgb.release()
        }
    }

    /** Result is normalised 0..1 in TL,TR,BR,BL order. */
    fun detect(rgb: Mat): List<Pt>? {
        val scale = WORK_SIDE / max(rgb.cols(), rgb.rows())
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA) else rgb.copyTo(small)
        val w = small.cols()
        val h = small.rows()

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val candidates = ArrayList<Candidate>()
        val mean = Core.mean(gray).`val`[0]
        val lowAuto = max(10.0, 0.33 * mean)
        val highAuto = min(255.0, max(60.0, 1.06 * mean))

        // Strategy 1: edges (two sensitivities). Close gaps, then fill outlines so the page becomes one blob.
        for ((lo, hi) in listOf(lowAuto to highAuto, 30.0 to 90.0)) {
            val edges = Mat()
            Imgproc.Canny(gray, edges, lo, hi)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, Mat.ones(7, 7, CvType.CV_8U))
            Imgproc.dilate(edges, edges, Mat.ones(3, 3, CvType.CV_8U))
            val outlines = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges.clone(), outlines, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val filled = Mat.zeros(edges.size(), CvType.CV_8UC1)
            Imgproc.drawContours(filled, outlines, -1, Scalar(255.0), -1)
            candidates += candidatesFrom(filled, w, h)
            filled.release()
            edges.release()
        }

        // Strategy 2: paper is usually brighter than the desk (or occasionally darker): Otsu split, both polarities.
        val bw = Mat()
        Imgproc.threshold(gray, bw, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val inverted = Mat()
        Core.bitwise_not(bw, inverted)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
        for (mask in listOf(bw, inverted)) {
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            candidates += candidatesFrom(mask, w, h)
        }

        bw.release()
        inverted.release()
        small.release()
        gray.release()
        return candidates.maxByOrNull { it.score }?.quad
    }

    private fun candidatesFrom(binary: Mat, w: Int, h: Int): List<Candidate> {
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(binary.clone(), contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val total = w.toDouble() * h
        val result = ArrayList<Candidate>()
        val largest = contours.map { it to Imgproc.contourArea(it) }
            .filter { it.second >= total * MIN_AREA && it.second <= total * MAX_AREA }
            .sortedByDescending { it.second }
            .take(6)
        for ((contour, _) in largest) {
            val fit = fitQuad(contour, w, h) ?: continue
            result += fit
        }
        contours.forEach { it.release() }
        return result
    }

    /** Reduces a blob to a four-corner polygon and scores how rectangle-like the page is. */
    private fun fitQuad(contour: MatOfPoint, w: Int, h: Int): Candidate? {
        val points = contour.toArray()
        val hullIndices = MatOfInt()
        Imgproc.convexHull(contour, hullIndices)
        val hull = MatOfPoint2f(*hullIndices.toArray().map { points[it] }.toTypedArray())
        val hullArea = Imgproc.contourArea(hull) / (w.toDouble() * h)
        val perimeter = Imgproc.arcLength(hull, true)

        var corners: Array<Point>? = null
        var epsilon = 0.01
        while (epsilon <= 0.12) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(hull, approx, epsilon * perimeter, true)
            val n = approx.rows()
            if (n == 4) corners = approx.toArray()
            approx.release()
            if (n <= 4) break
            epsilon += 0.005
        }
        if (corners == null) {
            // Rounded or noisy outline: fall back to the tightest tilted rectangle around it.
            val box = arrayOfNulls<Point>(4)
            Imgproc.minAreaRect(hull).points(box)
            corners = box.requireNoNulls()
        }
        hullIndices.release()
        hull.release()

        val normalised = corners.map {
            Pt((it.x / w).toFloat().coerceIn(0f, 1f), (it.y / h).toFloat().coerceIn(0f, 1f))
        }
        if (normalised.toSet().size != 4) return null
        val ordered = Geometry.orderCorners(normalised)
        if (!Geometry.isConvexQuad(ordered)) return null
        val quadArea = Geometry.area(ordered).toDouble()
        if (quadArea < MIN_AREA || quadArea > MAX_AREA) return null
        if (!anglesPlausible(ordered, w, h)) return null
        val fit = min(hullArea, quadArea) / max(hullArea, quadArea)
        return Candidate(quadArea * fit * fit * fit * fit, ordered)
    }

    /** A page seen in perspective still has corners roughly between 55 and 125 degrees. */
    private fun anglesPlausible(c: List<Pt>, w: Int, h: Int): Boolean {
        for (i in 0 until 4) {
            val p = c[i]
            val a = c[(i + 3) % 4]
            val b = c[(i + 1) % 4]
            val ax = (a.x - p.x) * w
            val ay = (a.y - p.y) * h
            val bx = (b.x - p.x) * w
            val by = (b.y - p.y) * h
            val len = hypot(ax, ay) * hypot(bx, by)
            if (len < 1e-6f) return false
            val cos = ((ax * bx + ay * by) / len).coerceIn(-1f, 1f)
            val degrees = Math.toDegrees(acos(cos).toDouble())
            if (degrees < 55 || degrees > 125) return false
        }
        return true
    }
}
