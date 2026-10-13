package com.docscan.app.imaging

import android.graphics.Bitmap
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** All Mats in this app are 8-bit, 3-channel RGB unless noted. */
fun Bitmap.toRgbMat(): Mat {
    val rgba = Mat()
    Utils.bitmapToMat(this, rgba)
    val rgb = Mat()
    Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
    rgba.release()
    return rgb
}

fun Mat.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(this, bitmap)
    return bitmap
}

object Warper {
    /** Straightens the quadrilateral [corners] (normalised, TL,TR,BR,BL) into a flat rectangle. */
    fun warp(src: Mat, corners: List<Pt>, maxSide: Int): Mat {
        val w = src.cols().toFloat()
        val h = src.rows().toFloat()
        val px = corners.map { Pt(it.x * w, it.y * h) }
        val (outW, outH) = Geometry.outputSize(px, maxSide)
        val from = MatOfPoint2f(*px.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val to = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(from, to)
        val out = Mat()
        Imgproc.warpPerspective(
            src, out, transform, Size(outW.toDouble(), outH.toDouble()),
            Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE,
        )
        from.release()
        to.release()
        transform.release()
        return out
    }

    /** Returns a new Mat rotated clockwise by [degrees] (multiples of 90). */
    fun rotate(src: Mat, degrees: Int): Mat {
        val out = Mat()
        when (((degrees % 360) + 360) % 360) {
            90 -> Core.rotate(src, out, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, out, Core.ROTATE_180)
            270 -> Core.rotate(src, out, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(out)
        }
        return out
    }
}
