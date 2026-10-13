package com.docscan.app.imaging

import com.docscan.app.core.Pt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Removes fingers/hands that cover part of a page.
 *
 * Auto mode finds skin-coloured blobs that touch the page edge (how a hand holds a sheet).
 * Brush mode lets the user paint over anything the auto mode missed, which matters because skin
 * colour detection is not equally reliable for every skin tone or lighting condition.
 */
object HandRemover {
    class Detection(val mask: Mat, val coverage: Double)

    /** [rgb] is the straightened page. The returned mask has the same size as [rgb] (255 = remove). */
    fun detectSkinMask(rgb: Mat): Detection {
        val scale = min(1.0, 900.0 / max(rgb.cols(), rgb.rows()))
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA) else rgb.copyTo(small)

        val ycc = Mat()
        Imgproc.cvtColor(small, ycc, Imgproc.COLOR_RGB2YCrCb)
        val skin = Mat()
        // Cr/Cb window that covers light to dark skin; plain paper (Cr~128, Cb~128) falls outside it.
        Core.inRange(ycc, Scalar(0.0, 135.0, 77.0), Scalar(255.0, 180.0, 135.0), skin)
        Imgproc.morphologyEx(skin, skin, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0)))
        Imgproc.morphologyEx(skin, skin, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)))

        val width = small.cols()
        val height = small.rows()
        val margin = max(3, (min(width, height) * 0.02).roundToInt())
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(skin.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val keep = Mat.zeros(height, width, CvType.CV_8UC1)
        val imageArea = width.toDouble() * height
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < imageArea * 0.004 || area > imageArea * 0.45) continue
            val box = Imgproc.boundingRect(contour)
            val touchesEdge = box.x <= margin || box.y <= margin ||
                box.x + box.width >= width - margin || box.y + box.height >= height - margin
            if (touchesEdge) Imgproc.drawContours(keep, listOf(contour), -1, Scalar(255.0), -1)
        }
        // Grow the mask a little so the inpainting also covers soft edges and shadows of the fingers.
        Imgproc.dilate(keep, keep, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(13.0, 13.0)))

        val mask = Mat()
        Imgproc.resize(keep, mask, rgb.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
        val coverage = Core.countNonZero(mask).toDouble() / (rgb.cols().toDouble() * rgb.rows())
        listOf(small, ycc, skin, keep).forEach { it.release() }
        return Detection(mask, coverage)
    }

    /**
     * Builds a mask from brush strokes. Strokes are normalised 0..1 over the image; [radiusFraction]
     * is the brush radius as a fraction of the longer image side.
     */
    fun maskFromStrokes(width: Int, height: Int, strokes: List<List<Pt>>, radiusFraction: Float): Mat {
        val mask = Mat.zeros(height, width, CvType.CV_8UC1)
        val radius = max(2, (radiusFraction * max(width, height)).roundToInt())
        fun toPoint(p: Pt) = Point((p.x * (width - 1)).toDouble(), (p.y * (height - 1)).toDouble())
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            Imgproc.circle(mask, toPoint(stroke.first()), radius, Scalar(255.0), -1)
            for (i in 1 until stroke.size) {
                Imgproc.line(mask, toPoint(stroke[i - 1]), toPoint(stroke[i]), Scalar(255.0), radius * 2)
                Imgproc.circle(mask, toPoint(stroke[i]), radius, Scalar(255.0), -1)
            }
        }
        return mask
    }

    /** Fills the masked area from its surroundings. Works best on thin areas such as fingers at the edge. */
    fun inpaint(rgb: Mat, mask: Mat): Mat {
        val scale = min(1.0, 1600.0 / max(rgb.cols(), rgb.rows()))
        if (scale >= 1.0) {
            val out = Mat()
            Photo.inpaint(rgb, mask, out, 6.0, Photo.INPAINT_TELEA)
            return out
        }
        // Inpaint at reduced size for speed, then copy only the repaired pixels back at full resolution.
        val small = Mat()
        val smallMask = Mat()
        Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
        Imgproc.resize(mask, smallMask, small.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
        val repairedSmall = Mat()
        Photo.inpaint(small, smallMask, repairedSmall, 6.0, Photo.INPAINT_TELEA)
        val repaired = Mat()
        Imgproc.resize(repairedSmall, repaired, rgb.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val out = rgb.clone()
        repaired.copyTo(out, mask)
        listOf(small, smallMask, repairedSmall, repaired).forEach { it.release() }
        return out
    }
}
