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
    COLOR("Color"),
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
)

/** Document clean-up filters. Every function returns a new Mat and never modifies its input. */
object Enhancer {
    fun apply(rgb: Mat, s: EnhanceSettings): Mat {
        var cur = rgb.clone()
        fun swap(next: Mat) {
            cur.release()
            cur = next
        }

        // The photocopy filter estimates the paper itself, so the shadow step is only for the other looks.
        if (s.removeShadows && s.mode != DocMode.BLACK_WHITE) swap(flattenIllumination(cur))
        when (s.mode) {
            DocMode.ORIGINAL -> Unit
            DocMode.COLOR -> swap(enhanceColor(cur))
            DocMode.GRAYSCALE -> swap(toGrayRgb(cur))
            DocMode.BLACK_WHITE -> swap(binarize(cur, s.brightness))
        }
        if (s.autoLevels && s.mode != DocMode.BLACK_WHITE) swap(autoLevels(cur))
        if (s.mode != DocMode.BLACK_WHITE && (s.brightness != 0 || s.contrast != 1f)) {
            swap(brightnessContrast(cur, s.brightness, s.contrast))
        }
        return cur
    }

    /**
     * Divides the lightness channel by a blurred "paper" estimate. Shadows and uneven light disappear,
     * while the colour channels are left untouched so inks and coloured paper keep their real colours.
     */
    fun flattenIllumination(rgb: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val lightness = channels[0]

        val small8 = Mat()
        Imgproc.resize(lightness, small8, Size(), 0.125, 0.125, Imgproc.INTER_AREA)
        val small = Mat()
        small8.convertTo(small, CvType.CV_32F)
        // The max filter must be wider than thin text but not so wide that it ignores real shadows.
        var k = (min(small.cols(), small.rows()) * 0.12).toInt().coerceAtLeast(7)
        if (k % 2 == 0) k += 1
        Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
        Imgproc.GaussianBlur(small, small, Size(0.0, 0.0), k / 2.5)
        val background = Mat()
        Imgproc.resize(small, background, lightness.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        // Floor the paper estimate: large dark areas (colour bands, photos, logos) must keep their colour
        // instead of being "corrected" to white, while ordinary shadows are still lifted.
        val paperLevel = Core.minMaxLoc(small).maxVal
        Core.max(background, Scalar.all(max(60.0, 0.6 * paperLevel)), background)

        val lightness32 = Mat()
        lightness.convertTo(lightness32, CvType.CV_32F)
        val ratio = Mat()
        Core.divide(lightness32, background, ratio, 255.0)
        val flattened = Mat()
        ratio.convertTo(flattened, CvType.CV_8U)
        flattened.copyTo(channels[0])

        val merged = Mat()
        Core.merge(channels, merged)
        val out = Mat()
        Imgproc.cvtColor(merged, out, Imgproc.COLOR_Lab2RGB)

        listOf(lab, small8, small, background, lightness32, ratio, flattened, merged).forEach { it.release() }
        channels.forEach { it.release() }
        return out
    }

    /** Gentle contrast + sharpening on lightness only, so colours are enhanced but not changed. */
    fun enhanceColor(rgb: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val clahe = Imgproc.createCLAHE(1.5, Size(8.0, 8.0))
        val boosted = Mat()
        clahe.apply(channels[0], boosted)
        boosted.copyTo(channels[0])
        val merged = Mat()
        Core.merge(channels, merged)
        val balanced = Mat()
        Imgproc.cvtColor(merged, balanced, Imgproc.COLOR_Lab2RGB)

        val blur = Mat()
        Imgproc.GaussianBlur(balanced, blur, Size(0.0, 0.0), 2.0)
        val sharpened = Mat()
        Core.addWeighted(balanced, 1.35, blur, -0.35, 0.0, sharpened)

        listOf(lab, boosted, merged, balanced, blur).forEach { it.release() }
        channels.forEach { it.release() }
        return sharpened
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
     * Photocopy look: pure white paper, solid black ink, no speckle, no shadows or page-edge dark lines.
     * The paper brightness is estimated per area and divided out, so a shaded corner is cut exactly like
     * a bright one. Large dark areas (bands, stamps) stay solid black, like on a real copier.
     * [brightness] (-100..100) moves the cut: positive = lighter copy (fainter marks disappear).
     */
    fun binarize(rgb: Mat, brightness: Int = 0): Mat {
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val g32 = Mat()
        gray.convertTo(g32, CvType.CV_32F)

        val small = Mat()
        Imgproc.resize(g32, small, Size(), 0.125, 0.125, Imgproc.INTER_AREA)
        var k = (min(small.cols(), small.rows()) * 0.06).toInt().coerceAtLeast(7)
        if (k % 2 == 0) k += 1
        Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
        Imgproc.GaussianBlur(small, small, Size(0.0, 0.0), k / 2.5)
        val paperLevel = Core.minMaxLoc(small).maxVal
        val background = Mat()
        Imgproc.resize(small, background, g32.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        Core.max(background, Scalar.all(max(40.0, 0.6 * paperLevel)), background)

        // 1.0 = paper. Scaled so paper sits at 200 in an 8-bit image.
        val ratio = Mat()
        Core.divide(g32, background, ratio, 200.0)
        val r8 = Mat()
        ratio.convertTo(r8, CvType.CV_8U)

        val scratch = Mat()
        val otsu = Imgproc.threshold(r8, scratch, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val cut = (otsu.coerceIn(110.0, 175.0) + brightness * 0.4).coerceIn(60.0, 200.0)
        // A touch of blur before the cut makes strokes solid instead of jagged.
        Imgproc.GaussianBlur(r8, r8, Size(0.0, 0.0), 0.8)
        val ink = Mat()
        Imgproc.threshold(r8, ink, cut, 255.0, Imgproc.THRESH_BINARY_INV)

        // Drop specks (dust, sensor noise) smaller than a printed dot.
        val w = ink.cols()
        val h = ink.rows()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(ink, labels, stats, centroids, 8, CvType.CV_32S)
        val statData = IntArray(count * 5)
        stats.get(0, 0, statData)
        val minArea = max(6, (10.0 * (w / 2000.0) * (w / 2000.0)).toInt())
        val keep = BooleanArray(count) { it > 0 && statData[it * 5 + Imgproc.CC_STAT_AREA] >= minArea }
        val labelData = IntArray(w * h)
        labels.get(0, 0, labelData)
        val pixels = ByteArray(w * h)
        for (i in pixels.indices) pixels[i] = if (keep[labelData[i]]) 0.toByte() else 255.toByte()

        // The outermost strip is page edge and table shadow, never content.
        val border = max(2, (min(w, h) * 0.006).toInt())
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
        listOf(gray, g32, small, background, ratio, r8, scratch, ink, labels, stats, centroids, bw).forEach { it.release() }
        return out
    }

    /** Linear levels stretch from the 1st to 99th brightness percentile, applied equally to all channels. */
    fun autoLevels(rgb: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val small = Mat()
        val factor = min(1.0, 256.0 / max(gray.cols(), gray.rows()))
        Imgproc.resize(gray, small, Size(), factor, factor, Imgproc.INTER_AREA)
        val pixels = ByteArray(small.total().toInt())
        small.get(0, 0, pixels)
        gray.release()
        small.release()

        val histogram = IntArray(256)
        for (b in pixels) histogram[b.toInt() and 0xFF]++
        val total = pixels.size
        fun percentile(p: Double): Int {
            var cumulative = 0
            for (i in 0..255) {
                cumulative += histogram[i]
                if (cumulative >= total * p) return i
            }
            return 255
        }
        // Never stretch too aggressively: keep real mid-tones and avoid blowing out photographs.
        val low = min(percentile(0.01), 90)
        val high = max(percentile(0.99), 160)
        if (high - low < 40) return rgb.clone()
        val alpha = 255.0 / (high - low)
        val out = Mat()
        rgb.convertTo(out, -1, alpha, -low * alpha)
        return out
    }

    fun brightnessContrast(rgb: Mat, brightness: Int, contrast: Float): Mat {
        val out = Mat()
        // Pivot around mid-grey so contrast changes do not shift overall brightness.
        rgb.convertTo(out, -1, contrast.toDouble(), brightness + 128.0 * (1.0 - contrast))
        return out
    }
}
