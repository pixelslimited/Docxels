package com.docscan.app.imaging

import android.graphics.Bitmap
import com.docscan.app.core.Pt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cuts a photo of two open book pages into a left and a right page. */
object SpreadSplitter {
    /**
     * Straightens the photographed spread ([corners] enclose both pages) and cuts it at the gutter.
     * Returns [left, right], or null when the straightened picture is not clearly wider than tall
     * (the phone was held upright, so a spread cannot be told apart from one page).
     */
    fun split(photo: Bitmap, corners: List<Pt>): List<Bitmap>? {
        val rgb = photo.toRgbMat()
        val flat = Warper.warp(rgb, corners, 3200)
        rgb.release()
        try {
            if (flat.cols() < flat.rows() * 1.05) return null
            val cut = findGutter(flat)
            val left = flat.submat(Rect(0, 0, cut, flat.rows())).clone()
            val right = flat.submat(Rect(cut, 0, flat.cols() - cut, flat.rows())).clone()
            val pages = listOf(left.toBitmap(), right.toBitmap())
            left.release()
            right.release()
            return pages
        } finally {
            flat.release()
        }
    }

    /**
     * X position (pixels) of the spine. The two pages have a blank inner margin next to the spine, so the
     * gutter is the emptiest stretch of columns around the middle. Falls back to the exact middle.
     */
    fun findGutter(rgb: Mat): Int {
        val width = rgb.cols()
        val scale = 600.0 / width
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
        val band = ink.submat((h * 0.05).toInt(), (h * 0.95).toInt(), 0, w)
        val sums = Mat()
        Core.reduce(band, sums, 0, Core.REDUCE_SUM, CvType.CV_32S)
        val columns = IntArray(w)
        sums.get(0, 0, columns)
        band.release()
        sums.release()
        ink.release()

        // Moving average over ~3% of the width so single letters do not decide the cut.
        val half = max(1, (w * 0.015).toInt())
        val lo = (w * 0.35).toInt()
        val hi = (w * 0.65).toInt()
        var bestX = w / 2
        var bestScore = Double.MAX_VALUE
        for (x in lo..hi) {
            var total = 0.0
            var n = 0
            for (i in max(0, x - half)..min(w - 1, x + half)) {
                total += columns[i]
                n++
            }
            // A small pull toward the middle breaks ties on pages that are blank around the spine.
            val score = total / n + abs(x - w / 2) * 0.05
            if (score < bestScore) {
                bestScore = score
                bestX = x
            }
        }
        val full = (bestX / scale).toInt()
        return full.coerceIn((width * 0.3).toInt(), (width * 0.7).toInt())
    }
}
