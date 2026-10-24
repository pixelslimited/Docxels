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
import kotlin.math.roundToInt

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

        // Strategy 0 (preferred): the page is the bright, colourless area. This ignores a coloured desk, hands and a second
        // page lying behind, which fooled the edge-based strategies below.
        val paper = runCatching { paperCandidates(small, gray, w, h) }.getOrDefault(emptyList())
        if (paper.isNotEmpty()) {
            val best = paper.maxByOrNull { it.score }?.quad?.let { refine(gray, it, w, h) }
            small.release()
            gray.release()
            return best
        }

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

        val best = candidates.maxByOrNull { it.score }?.quad?.let { refine(gray, it, w, h) }
        bw.release()
        inverted.release()
        small.release()
        gray.release()
        return best
    }

    /**
     * The blob outline that the quad came from is rounded at the corners and bumpy where a thumb, sticker or shadow touches
     * the edge, which leaves crooked corners. Each side is therefore re-fitted to where the picture really changes from page
     * to background: sample points along the side, look a little way either side for the strongest change, fit one straight
     * line through them, and put the corners where neighbouring lines cross. A side with too few clear points, a line that
     * moves too far or turns too much, or a result that is not a sensible four-sided shape keeps the original.
     */
    private fun refine(gray: Mat, quad: List<Pt>, w: Int, h: Int): List<Pt> {
        return runCatching { refineSides(gray, quad, w, h) }.getOrNull() ?: quad
    }

    private fun refineSides(gray: Mat, quad: List<Pt>, w: Int, h: Int): List<Pt>? {
        val data = ByteArray(w * h)
        gray.get(0, 0, data)
        fun at(x: Double, y: Double): Int {
            val xi = x.roundToInt().coerceIn(0, w - 1)
            val yi = y.roundToInt().coerceIn(0, h - 1)
            return data[yi * w + xi].toInt() and 0xFF
        }
        val px = quad.map { doubleArrayOf(it.x * w.toDouble(), it.y * h.toDouble()) }
        val cx = px.sumOf { it[0] } / 4
        val cy = px.sumOf { it[1] } / 4
        val reach = max(6.0, 0.04 * min(w, h))
        val diagonal = hypot(w.toDouble(), h.toDouble())

        // One refined line per side: a point on it and its direction.
        val lines = ArrayList<DoubleArray>(4)
        for (i in 0 until 4) {
            val a = px[i]
            val b = px[(i + 1) % 4]
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            val length = hypot(dx, dy)
            if (length < 20) return null
            val ux = dx / length
            val uy = dy / length
            // Normal pointing away from the middle of the quad.
            var nx = -uy
            var ny = ux
            if ((a[0] - cx) * nx + (a[1] - cy) * ny < 0) { nx = -nx; ny = -ny }

            val found = ArrayList<Point>()
            val samples = 28
            for (k in 0 until samples) {
                val t = 0.1 + 0.8 * k / (samples - 1)
                val bx = a[0] + dx * t
                val by = a[1] + dy * t
                var bestStep = Int.MIN_VALUE
                var bestGradient = 0.0
                var step = -reach.toInt()
                while (step <= reach.toInt()) {
                    // Change in brightness across this spot, averaged over a few neighbours along the edge.
                    var g = 0.0
                    for (j in -2..2) {
                        val sx = bx + ux * j * 2 + nx * step
                        val sy = by + uy * j * 2 + ny * step
                        g += at(sx - nx * 2, sy - ny * 2) - at(sx + nx * 2, sy + ny * 2)
                    }
                    val magnitude = kotlin.math.abs(g / 5)
                    if (magnitude > bestGradient) { bestGradient = magnitude; bestStep = step }
                    step++
                }
                if (bestStep != Int.MIN_VALUE && bestGradient >= 12.0) found += Point(bx + nx * bestStep, by + ny * bestStep)
            }
            if (found.size < samples * 0.6) { lines += doubleArrayOf(a[0], a[1], ux, uy); continue }

            val mat = MatOfPoint2f(*found.toTypedArray())
            val fit = Mat()
            Imgproc.fitLine(mat, fit, Imgproc.DIST_HUBER, 0.0, 0.01, 0.01)
            val raw = FloatArray(4)
            fit.get(0, 0, raw)
            val v = DoubleArray(4) { raw[it].toDouble() }
            mat.release()
            fit.release()
            var vx = v[0]
            var vy = v[1]
            if (vx * ux + vy * uy < 0) { vx = -vx; vy = -vy }
            // The line must still be close to the side it replaces, and keep most of the sampled points on it.
            val turned = Math.toDegrees(acos((vx * ux + vy * uy).coerceIn(-1.0, 1.0)))
            val offset = kotlin.math.abs((v[2] - a[0]) * -uy + (v[3] - a[1]) * ux)
            val onLine = found.count { kotlin.math.abs((it.x - v[2]) * -vy + (it.y - v[3]) * vx) <= 2.5 }
            if (turned > 8.0 || offset > reach * 1.5 || onLine < found.size * 0.5) lines += doubleArrayOf(a[0], a[1], ux, uy)
            else lines += doubleArrayOf(v[2], v[3], vx, vy)
        }

        // Corner i is where side i-1 meets side i.
        val corners = ArrayList<Pt>(4)
        for (i in 0 until 4) {
            val l1 = lines[(i + 3) % 4]
            val l2 = lines[i]
            val cross = l1[2] * l2[3] - l1[3] * l2[2]
            if (kotlin.math.abs(cross) < 0.2) return null
            val t = ((l2[0] - l1[0]) * l2[3] - (l2[1] - l1[1]) * l2[2]) / cross
            val x = l1[0] + l1[2] * t
            val y = l1[1] + l1[3] * t
            // A corner may not wander far from where it was.
            if (hypot(x - px[i][0], y - px[i][1]) > 0.08 * diagonal) return null
            corners += Pt((x / w).toFloat().coerceIn(0f, 1f), (y / h).toFloat().coerceIn(0f, 1f))
        }
        if (!Geometry.isConvexQuad(corners) || !anglesPlausible(corners, w, h)) return null
        val before = Geometry.area(quad).toDouble()
        val after = Geometry.area(corners).toDouble()
        if (after < before * 0.85 || after > before * 1.15) return null
        return corners
    }

    /**
     * Paper = clearly bright (relative to the middle of the frame), not strongly blue and not strongly coloured. Strong
     * edges are cut out first so a page lying on another page stays separate; the big pieces are then joined back across
     * those thin cut lines and outlined.
     */
    private fun paperCandidates(small: Mat, gray: Mat, w: Int, h: Int): List<Candidate> {
        val hsv = Mat()
        Imgproc.cvtColor(small, hsv, Imgproc.COLOR_RGB2HSV)
        val ch = ArrayList<Mat>()
        Core.split(hsv, ch)
        val centre = Mat()
        gray.submat(org.opencv.core.Rect(w / 4, h / 4, w / 2, h / 2)).copyTo(centre)
        val bytes = ByteArray(centre.total().toInt())
        centre.get(0, 0, bytes)
        val histogram = IntArray(256)
        for (b in bytes) histogram[b.toInt() and 0xFF]++
        var running = 0
        var ref = 255
        for (i in 0..255) {
            running += histogram[i]
            if (running >= bytes.size * 0.9) { ref = i; break }
        }

        val bright = Mat()
        Imgproc.threshold(gray, bright, 0.55 * ref, 255.0, Imgproc.THRESH_BINARY)
        val lowSat = Mat()
        Imgproc.threshold(ch[1], lowSat, 110.0, 255.0, Imgproc.THRESH_BINARY_INV)
        val satHigh = Mat()
        Imgproc.threshold(ch[1], satHigh, 45.0, 255.0, Imgproc.THRESH_BINARY)
        val hueBlue = Mat()
        Core.inRange(ch[0], Scalar(85.0), Scalar(135.0), hueBlue)
        val blue = Mat()
        Core.bitwise_and(hueBlue, satHigh, blue)
        val notBlue = Mat()
        Core.bitwise_not(blue, notBlue)
        val mask = Mat()
        Core.bitwise_and(bright, lowSat, mask)
        Core.bitwise_and(mask, notBlue, mask)

        val edges = Mat()
        Imgproc.Canny(gray, edges, 25.0, 70.0)
        Imgproc.dilate(edges, edges, Mat.ones(3, 3, CvType.CV_8U))
        mask.setTo(Scalar(0.0), edges)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, Mat.ones(5, 5, CvType.CV_8U))

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids, 4, CvType.CV_32S)
        val statData = IntArray(count * 5)
        stats.get(0, 0, statData)
        val keep = Mat.zeros(mask.size(), CvType.CV_8UC1)
        val piece = Mat()
        for (i in 1 until count) {
            if (statData[i * 5 + Imgproc.CC_STAT_AREA] < 0.02 * w * h) continue
            Core.inRange(labels, Scalar(i.toDouble()), Scalar(i.toDouble()), piece)
            Core.bitwise_or(keep, piece, keep)
        }
        Imgproc.morphologyEx(keep, keep, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 21.0)))
        val result = candidatesFrom(keep, w, h)
        listOf(hsv, centre, bright, lowSat, satHigh, hueBlue, blue, notBlue, mask, edges, labels, stats, centroids, keep, piece).forEach { it.release() }
        ch.forEach { it.release() }
        return result
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
