package com.docscan.app.imaging

import android.graphics.Bitmap
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/** Finds the page quadrilateral in a photo. Returns null when no confident quad is found. */
object EdgeDetector {
    private const val WORK_SIDE = 800.0

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

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        Imgproc.dilate(edges, edges, kernel)
        var quad = findQuad(edges, small.size())

        if (quad == null) {
            // Fallback for low-contrast edges: segment the bright paper region directly.
            val bw = Mat()
            Imgproc.threshold(gray, bw, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            val big = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_CLOSE, big)
            Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_OPEN, big)
            quad = findQuad(bw, small.size())
            bw.release()
        }
        small.release()
        gray.release()
        edges.release()
        return quad
    }

    private fun findQuad(binary: Mat, size: Size): List<Pt>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary.clone(), contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val total = size.width * size.height
        val candidates = contours
            .map { it to Imgproc.contourArea(it) }
            // Too small = noise; ~full frame = the image border, not a page.
            .filter { it.second >= total * 0.15 && it.second <= total * 0.97 }
            .sortedByDescending { it.second }
            .take(8)

        for ((contour, _) in candidates) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
            val points = approx.toArray()
            contour2f.release()
            approx.release()
            if (points.size == 4 && Imgproc.isContourConvex(MatOfPoint(*points))) {
                val normalised = points.map {
                    Pt((it.x / size.width).toFloat().coerceIn(0f, 1f), (it.y / size.height).toFloat().coerceIn(0f, 1f))
                }
                val ordered = Geometry.orderCorners(normalised)
                if (Geometry.isConvexQuad(ordered)) return ordered
            }
        }
        return null
    }
}
