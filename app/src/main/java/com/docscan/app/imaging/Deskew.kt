package com.docscan.app.imaging

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Levels a page whose text lines are slightly tilted after the perspective straightening.
 * It tries small rotations and keeps the one where text lines line up best (sharpest row profile).
 */
object Deskew {
    private const val MAX_DEGREES = 10.0
    private const val MIN_DEGREES = 0.3

    /** Degrees to rotate (OpenCV convention: positive = counter-clockwise) to level the text. 0 if unsure. */
    fun angle(rgb: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val scale = 700.0 / max(gray.cols(), gray.rows())
        val small = Mat()
        Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        gray.release()

        // Ignore the outer margin where the page edge or background may show.
        val margin = (min(small.cols(), small.rows()) * 0.04).toInt().coerceAtLeast(1)
        val inner = small.submat(Rect(margin, margin, small.cols() - 2 * margin, small.rows() - 2 * margin))
        val ink = Mat()
        Imgproc.adaptiveThreshold(inner, ink, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 15.0)
        inner.release()
        small.release()

        try {
            if (Core.countNonZero(ink) < ink.total() * 0.004) return 0.0
            val centre = Point(ink.cols() / 2.0, ink.rows() / 2.0)
            val rotated = Mat()
            val sums = Mat()

            fun score(degrees: Double): Double {
                val m = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
                Imgproc.warpAffine(ink, rotated, m, ink.size(), Imgproc.INTER_NEAREST)
                m.release()
                Core.reduce(rotated, sums, 1, Core.REDUCE_SUM, CvType.CV_32S)
                val rows = IntArray(sums.rows())
                sums.get(0, 0, rows)
                var total = 0.0
                for (i in 1 until rows.size) {
                    val d = (rows[i] - rows[i - 1]).toDouble()
                    total += d * d
                }
                return total
            }

            var best = 0.0
            var bestScore = score(0.0)
            var a = -MAX_DEGREES
            while (a <= MAX_DEGREES + 1e-9) {
                val s = score(a)
                if (s > bestScore) {
                    bestScore = s
                    best = a
                }
                a += 1.0
            }
            val coarse = best
            a = coarse - 0.9
            while (a <= coarse + 0.9 + 1e-9) {
                val s = score(a)
                if (s > bestScore) {
                    bestScore = s
                    best = a
                }
                a += 0.2
            }
            rotated.release()
            sums.release()
            return if (abs(best) >= MIN_DEGREES) best else 0.0
        } finally {
            ink.release()
        }
    }

    /** Returns a new Mat; the input is not modified. */
    fun apply(rgb: Mat): Mat {
        val degrees = angle(rgb)
        val out = Mat()
        if (degrees == 0.0) {
            rgb.copyTo(out)
            return out
        }
        val m = Imgproc.getRotationMatrix2D(Point(rgb.cols() / 2.0, rgb.rows() / 2.0), degrees, 1.0)
        Imgproc.warpAffine(rgb, out, m, rgb.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        m.release()
        return out
    }
}
