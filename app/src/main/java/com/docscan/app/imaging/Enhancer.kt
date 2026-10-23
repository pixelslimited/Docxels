package com.docscan.app.imaging

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

enum class DocMode(val label: String) {
    ORIGINAL("Original"),
    COLOR("Auto color"),
    GRAYSCALE("Gray"),
    BLACK_WHITE("B&W copy"),
}

data class EnhanceSettings(
    val mode: DocMode = DocMode.COLOR,
    /** -100..100, added to pixel values. */
    val brightness: Int = 0,
    /** 0.5..2.0 multiplier around mid-grey. */
    val contrast: Float = 1f,
    /** Evens out uneven lighting and shadows. */
    val removeShadows: Boolean = true,
    /** Stretches levels so paper becomes white and ink dark. */
    val autoLevels: Boolean = true,
    /** Smooths grain from dim light. */
    val denoise: Boolean = false,
    /** 0..100: sharpens to reduce mild blur. */
    val sharpen: Int = 0,
    /** Fills small blown-out highlights (flash, glossy paper). */
    val glare: Boolean = false,
    /** Flips the finished page left to right. */
    val mirror: Boolean = false,
)

/** Document clean-up filters. Every function returns a new Mat and never modifies its input. */
object Enhancer {
    /**
     * Looks are automatic: shadows, uneven light, glare and the paper colour are handled without any settings.
     * The settings only add the user's own touches (brightness, contrast, sharpen, noise, mirror).
     */
    fun apply(rgb: Mat, s: EnhanceSettings): Mat {
        var cur = rgb.clone()
        fun swap(next: Mat) {
            cur.release()
            cur = next
        }

        if (s.mode != DocMode.ORIGINAL) swap(reduceGlare(cur))
        if (s.denoise) swap(denoise(cur))
        when (s.mode) {
            DocMode.ORIGINAL -> Unit
            DocMode.COLOR -> swap(autoColor(cur, s.removeShadows))
            DocMode.GRAYSCALE -> swap(toGrayRgb(autoColor(cur, s.removeShadows)))
            DocMode.BLACK_WHITE -> swap(binarize(autoColor(cur, s.removeShadows), s.brightness))
        }
        if (s.mode != DocMode.BLACK_WHITE && (s.brightness != 0 || s.contrast != 1f)) {
            swap(brightnessContrast(cur, s.brightness, s.contrast))
        }
        if (s.sharpen > 0 && s.mode != DocMode.BLACK_WHITE) swap(sharpen(cur, s.sharpen))
        if (s.mirror) Core.flip(cur, cur, 1)
        return cur
    }

    /** Smooths camera grain while keeping letter edges (non-local means, small window so it stays quick on a phone). */
    fun denoise(rgb: Mat): Mat {
        val out = Mat()
        org.opencv.photo.Photo.fastNlMeansDenoisingColored(rgb, out, 5f, 5f, 5, 13)
        return out
    }

    /** Unsharp mask: [amount] 1..100 adds up to 1.5x of the difference to a blurred copy. Reduces mild blur; it cannot rebuild a badly blurred photo. */
    fun sharpen(rgb: Mat, amount: Int): Mat {
        val k = amount.coerceIn(0, 100) / 100.0 * 1.5
        val blur = Mat()
        Imgproc.GaussianBlur(rgb, blur, Size(0.0, 0.0), 1.6)
        val out = Mat()
        Core.addWeighted(rgb, 1.0 + k, blur, -k, 0.0, out)
        blur.release()
        return out
    }

    /**
     * Fills small, completely blown-out spots (every channel at the top of the range) that sit inside darker paper, such as a
     * flash reflection on glossy paper or a laminated card. Large white areas and the paper itself are left alone, and
     * whatever lay under strong glare is gone for good: the spot is filled from its surroundings, not recovered.
     */
    fun reduceGlare(rgb: Mat): Mat {
        val min3 = Mat()
        val channels = ArrayList<Mat>()
        Core.split(rgb, channels)
        Core.min(channels[0], channels[1], min3)
        Core.min(min3, channels[2], min3)
        channels.forEach { it.release() }
        val clipped = Mat()
        Imgproc.threshold(min3, clipped, 250.0, 255.0, Imgproc.THRESH_BINARY)
        min3.release()
        // Only spots with a clearly darker ring around them count; open white paper has no such ring.
        val ringKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
        val wider = Mat()
        Imgproc.dilate(clipped, wider, ringKernel)
        val ring = Mat()
        Core.subtract(wider, clipped, ring)
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(clipped, labels, stats, centroids, 8)
        val total = rgb.rows().toDouble() * rgb.cols()
        val mask = Mat.zeros(clipped.size(), CvType.CV_8U)
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        for (i in 1 until count) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0]
            if (area < total * 0.0002 || area > total * 0.04) continue
            val x = stats.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = stats.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
            val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val pad = 8
            val box = org.opencv.core.Rect(
                (x - pad).coerceAtLeast(0), (y - pad).coerceAtLeast(0),
                (w + 2 * pad).coerceAtMost(rgb.cols() - (x - pad).coerceAtLeast(0)),
                (h + 2 * pad).coerceAtMost(rgb.rows() - (y - pad).coerceAtLeast(0)),
            )
            val ringMean = Core.mean(gray.submat(box), ring.submat(box)).`val`[0]
            if (ringMean < 225.0) Core.bitwise_or(mask.submat(box), clipped.submat(box), mask.submat(box))
        }
        val result: Mat
        if (Core.countNonZero(mask) == 0) {
            result = rgb.clone()
        } else {
            Imgproc.dilate(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0)))
            result = Mat()
            org.opencv.photo.Photo.inpaint(rgb, mask, result, 5.0, org.opencv.photo.Photo.INPAINT_TELEA)
        }
        listOf(clipped, wider, ring, labels, stats, centroids, mask, gray).forEach { it.release() }
        return result
    }

    /**
     * Estimates the paper (its colour and the light falling on it) per channel: a closing removes the thin ink strokes,
     * a median and a blur smooth what is left. Returned at the size of [rgb], 8-bit.
     */
    private fun paperEstimate(rgb: Mat): Mat {
        val scale = 400.0 / max(rgb.cols(), rgb.rows())
        val small = Mat()
        Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
        var k = (max(small.cols(), small.rows()) * 0.03).toInt().coerceAtLeast(7)
        if (k % 2 == 0) k += 1
        val closed = Mat()
        Imgproc.morphologyEx(small, closed, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
        val median = Mat()
        Imgproc.medianBlur(closed, median, k)
        Imgproc.GaussianBlur(median, median, Size(0.0, 0.0), k / 2.0)
        val full = Mat()
        Imgproc.resize(median, full, rgb.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        listOf(small, closed, median).forEach { it.release() }
        return full
    }

    /**
     * The automatic "scanner" look: the paper estimate is divided out, so shadows, uneven light and the colour cast of the
     * room disappear and paper turns white while inks keep their colour; a gentle curve then deepens the ink and a light
     * sharpening crisps the text. With [flatten] off only the curve and sharpening are applied.
     */
    fun autoColor(rgb: Mat, flatten: Boolean = true): Mat {
        val flat = Mat()
        if (flatten) {
            val paper = paperEstimate(rgb)
            // Floor the estimate so large dark areas (photos, colour bands) are not "corrected" to white.
            Core.max(paper, Scalar.all(30.0), paper)
            // 8-bit divide: out = rgb * 255 / paper. Paper sits near 240 after that, so it is stretched to pure white below.
            Core.divide(rgb, paper, flat, 255.0)
            paper.release()
        } else {
            rgb.copyTo(flat)
        }
        val lut = Mat(1, 256, CvType.CV_8U)
        val table = ByteArray(256) { i ->
            val v = min(1.0, i * (255.0 / 240.0) / 255.0)
            (255.0 * Math.pow(v, 1.6)).toInt().coerceIn(0, 255).toByte()
        }
        lut.put(0, 0, table)
        val curved = Mat()
        Core.LUT(flat, lut, curved)
        val blur = Mat()
        Imgproc.GaussianBlur(curved, blur, Size(0.0, 0.0), 1.2)
        val out = Mat()
        Core.addWeighted(curved, 1.5, blur, -0.5, 0.0, out)
        listOf(flat, lut, curved, blur).forEach { it.release() }
        whitenEdge(out)
        return out
    }

    /** The outermost strip is page edge and desk, never content: paint it paper white. */
    private fun whitenEdge(img: Mat) {
        val b = max(2, (min(img.cols(), img.rows()) * 0.008).toInt())
        val white = Scalar.all(255.0)
        img.submat(0, b, 0, img.cols()).setTo(white)
        img.submat(img.rows() - b, img.rows(), 0, img.cols()).setTo(white)
        img.submat(0, img.rows(), 0, b).setTo(white)
        img.submat(0, img.rows(), img.cols() - b, img.cols()).setTo(white)
    }

    fun toGrayRgb(rgb: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val out = Mat()
        Imgproc.cvtColor(gray, out, Imgproc.COLOR_GRAY2RGB)
        gray.release()
        return out
    }

    /**
     * Photocopy look from an already flattened page ([autoColor] output): pure white paper, solid black ink, no speckle.
     * Specks smaller than a printed dot are dropped, and big dark shapes touching the page edge (a thumb, the desk) are
     * removed. [brightness] (-100..100) moves the cut: positive = lighter copy (fainter marks disappear).
     */
    fun binarize(flat: Mat, brightness: Int = 0): Mat {
        val gray = Mat()
        Imgproc.cvtColor(flat, gray, Imgproc.COLOR_RGB2GRAY)
        // A touch of blur before the cut makes strokes solid instead of jagged.
        Imgproc.GaussianBlur(gray, gray, Size(0.0, 0.0), 0.7)
        val scratch = Mat()
        val otsu = Imgproc.threshold(gray, scratch, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val cut = (otsu.coerceIn(120.0, 200.0) + brightness * 0.4).coerceIn(60.0, 220.0)
        val ink = Mat()
        Imgproc.threshold(gray, ink, cut, 255.0, Imgproc.THRESH_BINARY_INV)

        val w = ink.cols()
        val h = ink.rows()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(ink, labels, stats, centroids, 8, CvType.CV_32S)
        val statData = IntArray(count * 5)
        stats.get(0, 0, statData)
        val minArea = max(6, (10.0 * (w / 2000.0) * (w / 2000.0)).toInt())
        val bigArea = w.toDouble() * h * 0.01
        val keep = BooleanArray(count)
        for (i in 1 until count) {
            val area = statData[i * 5 + Imgproc.CC_STAT_AREA]
            val left = statData[i * 5 + Imgproc.CC_STAT_LEFT]
            val top = statData[i * 5 + Imgproc.CC_STAT_TOP]
            val boxW = statData[i * 5 + Imgproc.CC_STAT_WIDTH]
            val boxH = statData[i * 5 + Imgproc.CC_STAT_HEIGHT]
            val touchesEdge = left == 0 || top == 0 || left + boxW >= w || top + boxH >= h
            keep[i] = area >= minArea && !(touchesEdge && area > bigArea)
        }
        val labelData = IntArray(w * h)
        labels.get(0, 0, labelData)
        val pixels = ByteArray(w * h)
        for (i in pixels.indices) pixels[i] = if (keep[labelData[i]]) 0.toByte() else 255.toByte()

        val border = max(2, (min(w, h) * 0.008).toInt())
        for (y in 0 until h) {
            val inBand = y < border || y >= h - border
            for (x in 0 until w) {
                if (inBand || x < border || x >= w - border) pixels[y * w + x] = 255.toByte()
            }
        }
        val bw = Mat(h, w, CvType.CV_8UC1)
        bw.put(0, 0, pixels)
        val out = Mat()
        Imgproc.cvtColor(bw, out, Imgproc.COLOR_GRAY2RGB)
        listOf(gray, scratch, ink, labels, stats, centroids, bw).forEach { it.release() }
        return out
    }

    fun brightnessContrast(rgb: Mat, brightness: Int, contrast: Float): Mat {
        val out = Mat()
        // Pivot around mid-grey so contrast changes do not shift overall brightness.
        rgb.convertTo(out, -1, contrast.toDouble(), brightness + 128.0 * (1.0 - contrast))
        return out
    }
}
