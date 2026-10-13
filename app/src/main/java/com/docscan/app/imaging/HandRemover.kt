package com.docscan.app.imaging

import com.docscan.app.core.Pt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
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


    // ---- Whole-page clean-up: hands, objects, staples, folded corners ---------------------------------

    /**
     * Finds everything that does not belong on the sheet: skin, pens and other objects, desk showing at a
     * folded corner or edge, and staple pins. [rgb] is the straightened page; the mask has the same size.
     */
    fun detectJunkMask(rgb: Mat): Detection {
        val scale = min(1.0, 900.0 / max(rgb.cols(), rgb.rows()))
        val small = Mat()
        if (scale < 1.0) Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA) else rgb.copyTo(small)
        val w = small.cols()
        val h = small.rows()

        val lab = Mat()
        Imgproc.cvtColor(small, lab, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val lightness = Mat()
        channels[0].convertTo(lightness, CvType.CV_32F)
        val chromaA = Mat()
        val chromaB = Mat()
        channels[1].convertTo(chromaA, CvType.CV_32F)
        channels[2].convertTo(chromaB, CvType.CV_32F)

        // Local paper brightness. It is floored on the page-wide level so a big dark object cannot become its own "paper".
        var k = (min(w, h) * 0.12).toInt().coerceAtLeast(7)
        if (k % 2 == 0) k += 1
        val bg = Mat()
        Imgproc.dilate(lightness, bg, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble())))
        Imgproc.GaussianBlur(bg, bg, Size(0.0, 0.0), k / 2.5)
        val p95 = percentile(channels[0], 0.95)
        Core.max(bg, Scalar.all(0.85 * p95), bg)

        val threshold = Mat()
        Core.multiply(bg, Scalar.all(0.88), threshold)
        val bright = Mat()
        Core.compare(lightness, threshold, bright, Core.CMP_GT)
        val refA = if (Core.countNonZero(bright) > 100) Core.mean(chromaA, bright).`val`[0] else 128.0
        val refB = if (Core.countNonZero(bright) > 100) Core.mean(chromaB, bright).`val`[0] else 128.0
        Core.subtract(chromaA, Scalar.all(refA), chromaA)
        Core.subtract(chromaB, Scalar.all(refB), chromaB)
        val distance = Mat()
        Core.magnitude(chromaA, chromaB, distance)

        // Non-paper = clearly darker than the paper, or clearly a different colour from it.
        val darkLimit = Mat()
        Core.multiply(bg, Scalar.all(0.8), darkLimit)
        val tooDark = Mat()
        val tooColourful = Mat()
        val nonPaper = Mat()
        Core.compare(lightness, darkLimit, tooDark, Core.CMP_LT)
        Core.compare(distance, Scalar.all(22.0), tooColourful, Core.CMP_GT)
        Core.bitwise_or(tooDark, tooColourful, nonPaper)

        val mask = Mat.zeros(h, w, CvType.CV_8UC1)
        val area = w.toDouble() * h

        // Solid blobs that reach the page edge (hand, pen, desk behind a folded corner). Opening removes text and rules;
        // the border is padded first so blobs on the edge are not eroded away.
        val ko = max(5, (min(w, h) * 0.018).toInt())
        val padded = Mat()
        Core.copyMakeBorder(nonPaper, padded, ko, ko, ko, ko, Core.BORDER_REPLICATE)
        Imgproc.morphologyEx(padded, padded, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(ko.toDouble(), ko.toDouble())))
        val solid = padded.submat(org.opencv.core.Rect(ko, ko, w, h)).clone()
        val edgeMargin = max(3, (min(w, h) * 0.02).roundToInt())
        forEachComponent(solid) { index, labels, box, pixelArea ->
            if (pixelArea < area * 0.002 || pixelArea > area * 0.35) return@forEachComponent
            val touchesEdge = box.x <= edgeMargin || box.y <= edgeMargin ||
                box.x + box.width >= w - edgeMargin || box.y + box.height >= h - edgeMargin
            if (!touchesEdge) return@forEachComponent
            // A tidy full-width band that bleeds off the edge is design, not an object.
            val fill = pixelArea / (box.width.toDouble() * box.height)
            if (fill > 0.9 && pixelArea > area * 0.03 && (box.width > w * 0.6 || box.height > h * 0.6)) return@forEachComponent
            val region = Mat()
            Core.compare(labels, Scalar(index.toDouble()), region, Core.CMP_EQ)
            Core.bitwise_or(mask, region, mask)
            region.release()
        }
        // Grow back over the soft fringe the opening trimmed.
        val grown = Mat()
        Imgproc.dilate(mask, grown, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(ko * 2 + 1.0, ko * 2 + 1.0)))
        Core.bitwise_and(grown, nonPaper, grown)
        Core.bitwise_or(mask, grown, mask)

        // Pale skin can look like paper to the test above: keep the dedicated skin detector as well.
        val skin = detectSkinMask(small)
        Core.bitwise_or(mask, skin.mask, mask)
        skin.mask.release()

        addStaples(lightness, bg, mask, w, h)

        // A margin around everything so shadows of the object go too.
        val margin = (min(w, h) * 0.02).toInt().coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
        Imgproc.dilate(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(margin.toDouble(), margin.toDouble())))

        val full = Mat()
        Imgproc.resize(mask, full, rgb.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
        val coverage = Core.countNonZero(full).toDouble() / (rgb.cols().toDouble() * rgb.rows())
        channels.forEach { it.release() }
        listOf(
            small, lab, lightness, chromaA, chromaB, bg, threshold, bright, distance,
            darkLimit, tooDark, tooColourful, nonPaper, padded, solid, grown, mask,
        ).forEach { it.release() }
        return Detection(full, coverage)
    }

    /** Staple pins: small, dark, bar-shaped, alone, near a corner or the page edge. */
    private fun addStaples(lightness: Mat, bg: Mat, mask: Mat, w: Int, h: Int) {
        val ratio = Mat()
        Core.divide(lightness, bg, ratio)
        val dark = Mat()
        Core.compare(ratio, Scalar.all(0.62), dark, Core.CMP_LT)
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(dark, labels, stats, centroids, 8, CvType.CV_32S)
        val statData = IntArray(count * 5)
        stats.get(0, 0, statData)
        val centroidData = DoubleArray(count * 2)
        centroids.get(0, 0, centroidData)
        val side = min(w, h).toDouble()

        for (i in 1 until count) {
            val bx = statData[i * 5 + Imgproc.CC_STAT_LEFT]
            val by = statData[i * 5 + Imgproc.CC_STAT_TOP]
            val bw = statData[i * 5 + Imgproc.CC_STAT_WIDTH]
            val bh = statData[i * 5 + Imgproc.CC_STAT_HEIGHT]
            val pixelArea = statData[i * 5 + Imgproc.CC_STAT_AREA]
            val longSide = max(bw, bh).toDouble()
            if (pixelArea < 8 || longSide < side * 0.015 || longSide > side * 0.14) continue
            val cx = centroidData[i * 2]
            val cy = centroidData[i * 2 + 1]
            val inCornerX = cx < w * 0.14 || cx > w * 0.86
            val inCornerY = cy < h * 0.14 || cy > h * 0.86
            val nearEdge = cx < w * 0.06 || cx > w * 0.94 || cy < h * 0.06 || cy > h * 0.94
            if (!((inCornerX && inCornerY) || nearEdge)) continue

            // Bar test on the tight tilted rectangle: thin, long and mostly filled.
            val region = Mat()
            Core.compare(labels.submat(org.opencv.core.Rect(bx, by, bw, bh)), Scalar(i.toDouble()), region, Core.CMP_EQ)
            val points = Mat()
            Core.findNonZero(region, points)
            region.release()
            if (points.empty()) continue
            val shifted = MatOfPoint2f(*MatOfPoint(points).toArray().map { Point(it.x + bx, it.y + by) }.toTypedArray())
            points.release()
            val rect = Imgproc.minAreaRect(shifted)
            shifted.release()
            val longRect = max(rect.size.width, rect.size.height)
            val shortRect = max(1.0, min(rect.size.width, rect.size.height))
            if (shortRect < 2 || longRect / shortRect < 2.5 || pixelArea / (rect.size.width * rect.size.height + 1e-6) < 0.6) continue

            // Alone: a staple is not part of a line of text.
            var neighbours = 0
            for (j in 1 until count) {
                if (j == i) continue
                val dx = centroidData[j * 2] - cx
                val dy = centroidData[j * 2 + 1] - cy
                if (kotlin.math.hypot(dx, dy) < longRect * 2.5) neighbours++
            }
            if (neighbours > 3) continue
            val region2 = Mat()
            Core.compare(labels, Scalar(i.toDouble()), region2, Core.CMP_EQ)
            Core.bitwise_or(mask, region2, mask)
            region2.release()
        }
        listOf(ratio, dark, labels, stats, centroids).forEach { it.release() }
    }

    /**
     * Fills the masked area with the paper colour around it (smoothly blended from nearby clean paper).
     * Unlike inpainting this never smears, so a removed hand leaves clean paper rather than a blur.
     */
    fun fillPaper(rgb: Mat, mask: Mat): Mat {
        val scale = min(1.0, 1200.0 / max(rgb.cols(), rgb.rows()))
        val small = Mat()
        val smallMask = Mat()
        if (scale < 1.0) {
            Imgproc.resize(rgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
            Imgproc.resize(mask, smallMask, small.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
        } else {
            rgb.copyTo(small)
            mask.copyTo(smallMask)
        }

        val lab = Mat()
        Imgproc.cvtColor(small, lab, Imgproc.COLOR_RGB2Lab)
        val lightness = Mat()
        Core.extractChannel(lab, lightness, 0)
        val paper = 0.82 * percentile(lightness, 0.95)
        val clean = Mat()
        Core.compare(lightness, Scalar.all(paper), clean, Core.CMP_GE)
        val outside = Mat()
        Core.bitwise_not(smallMask, outside)
        Core.bitwise_and(clean, outside, clean)
        val valid = Mat()
        clean.convertTo(valid, CvType.CV_32F, 1.0 / 255.0)

        val sigma = max(rgb.cols(), rgb.rows()) * scale * 0.04
        val near = blurredPaper(small, valid, sigma)
        val far = blurredPaper(small, valid, sigma * 4)
        val densityNear = Mat()
        Imgproc.GaussianBlur(valid, densityNear, Size(0.0, 0.0), sigma)
        val usableNear = Mat()
        Core.compare(densityNear, Scalar.all(0.02), usableNear, Core.CMP_GT)
        far.copyTo(near, Mat().also { Core.bitwise_not(usableNear, it) })

        val fill = Mat()
        Imgproc.resize(near, fill, rgb.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val out = rgb.clone()
        fill.copyTo(out, mask)
        listOf(small, smallMask, lab, lightness, clean, outside, valid, near, far, densityNear, usableNear, fill).forEach { it.release() }
        return out
    }

    /** Paper colour estimate (8-bit RGB): blur of the valid pixels divided by the blur of the validity itself. */
    private fun blurredPaper(rgb: Mat, valid: Mat, sigma: Double): Mat {
        val channels = ArrayList<Mat>()
        Core.split(rgb, channels)
        val density = Mat()
        Imgproc.GaussianBlur(valid, density, Size(0.0, 0.0), sigma)
        Core.max(density, Scalar.all(1e-4), density)
        val result = ArrayList<Mat>()
        for (channel in channels) {
            val weighted = Mat()
            channel.convertTo(weighted, CvType.CV_32F)
            Core.multiply(weighted, valid, weighted)
            Imgproc.GaussianBlur(weighted, weighted, Size(0.0, 0.0), sigma)
            Core.divide(weighted, density, weighted)
            val eight = Mat()
            weighted.convertTo(eight, CvType.CV_8U)
            result += eight
            weighted.release()
        }
        val merged = Mat()
        Core.merge(result, merged)
        channels.forEach { it.release() }
        result.forEach { it.release() }
        density.release()
        return merged
    }

    /** Brightness percentile (0..1) of an 8-bit single-channel image. */
    private fun percentile(gray8: Mat, p: Double): Double {
        val data = ByteArray(gray8.total().toInt())
        gray8.get(0, 0, data)
        val histogram = IntArray(256)
        for (b in data) histogram[b.toInt() and 0xFF]++
        var cumulative = 0
        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative >= data.size * p) return i.toDouble()
        }
        return 255.0
    }

    /** Calls [action] for each connected blob of a binary image with its label image, box and pixel count. */
    private fun forEachComponent(
        binary: Mat,
        action: (index: Int, labels: Mat, box: org.opencv.core.Rect, area: Double) -> Unit,
    ) {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(binary, labels, stats, centroids, 8, CvType.CV_32S)
        val data = IntArray(count * 5)
        stats.get(0, 0, data)
        for (i in 1 until count) {
            val box = org.opencv.core.Rect(
                data[i * 5 + Imgproc.CC_STAT_LEFT], data[i * 5 + Imgproc.CC_STAT_TOP],
                data[i * 5 + Imgproc.CC_STAT_WIDTH], data[i * 5 + Imgproc.CC_STAT_HEIGHT],
            )
            action(i, labels, box, data[i * 5 + Imgproc.CC_STAT_AREA].toDouble())
        }
        listOf(labels, stats, centroids).forEach { it.release() }
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
