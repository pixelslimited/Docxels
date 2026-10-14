package com.docscan.app.imaging

import android.graphics.Bitmap
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cuts the subject of a picture out of its background, on the phone (nothing is uploaded).
 *
 * Uses GrabCut: everything outside a rectangle slightly inside the picture edge is treated as background, and
 * the colour statistics of that region separate the subject from it. It works best on a clear subject against a
 * fairly plain background; busy or similar-coloured backgrounds leave rough edges, so the result is a preview
 * the user can accept or discard.
 */
object BackgroundRemover {
    /** Returns a copy of [src] whose background pixels are transparent. */
    fun remove(src: Bitmap, marginFraction: Float = 0.04f): Bitmap {
        val full = src.toRgbMat()
        try {
            val scale = min(1.0, WORK_SIDE / max(full.cols(), full.rows()))
            val small = Mat()
            if (scale < 1.0) Imgproc.resize(full, small, Size(), scale, scale, Imgproc.INTER_AREA) else full.copyTo(small)

            val w = small.cols()
            val h = small.rows()
            val mx = max(2, (w * marginFraction).roundToInt())
            val my = max(2, (h * marginFraction).roundToInt())
            val rect = Rect(mx, my, (w - 2 * mx).coerceAtLeast(2), (h - 2 * my).coerceAtLeast(2))

            val mask = Mat(h, w, CvType.CV_8UC1, Scalar(Imgproc.GC_BGD.toDouble()))
            val bgd = Mat()
            val fgd = Mat()
            Imgproc.grabCut(small, mask, rect, bgd, fgd, 4, Imgproc.GC_INIT_WITH_RECT)

            val sure = Mat()
            val probable = Mat()
            Core.compare(mask, Scalar(Imgproc.GC_FGD.toDouble()), sure, Core.CMP_EQ)
            Core.compare(mask, Scalar(Imgproc.GC_PR_FGD.toDouble()), probable, Core.CMP_EQ)
            val fg = Mat()
            Core.bitwise_or(sure, probable, fg)

            // Remove specks and fill pinholes, then soften the edge a little on the full-size mask.
            Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0)))
            Imgproc.morphologyEx(fg, fg, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)))
            val alpha = Mat()
            Imgproc.resize(fg, alpha, Size(full.cols().toDouble(), full.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.GaussianBlur(alpha, alpha, Size(5.0, 5.0), 0.0)

            val pixels = IntArray(src.width * src.height)
            src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            val a = ByteArray(pixels.size)
            alpha.get(0, 0, a)
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] and 0x00FFFFFF) or ((a[i].toInt() and 0xFF) shl 24)
            }
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            return out
        } finally {
            full.release()
        }
    }

    private const val WORK_SIDE = 640.0
}
