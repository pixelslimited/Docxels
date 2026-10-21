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
    private const val SMART_SIDE = 800.0

    /** The first, simpler method (a rectangle inside the edge), kept as a fallback when the smarter one finds nothing. */
    fun removeWithRect(src: Bitmap, marginFraction: Float = 0.04f): Bitmap {
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

    /**
     * Finds the background by what the picture's own edge looks like and cuts the subject out.
     *
     * 1. The colours along the picture edge are grouped (k-means in Lab colour); every pixel is measured against them.
     * 2. Pixels that match and are joined to the edge are background: a strict match is certain, a looser match
     *    (soft shadows, gradients, paper grain) is probable. Pixels far from every edge colour are certain subject.
     * 3. If the phone's person model finds a person, that outline guides the cut as well.
     * 4. GrabCut settles the undecided pixels using that starting mask, instead of a plain rectangle.
     * 5. Only the main pieces are kept, small holes are filled, and the edge is softened.
     *
     * Returns null when the result is not believable (nothing, or nearly everything, was cut), so the caller can
     * fall back to [removeWithRect].
     */
    fun removeSmart(src: Bitmap): Bitmap? {
        val full = src.toRgbMat()
        try {
            val scale = min(1.0, SMART_SIDE / max(full.cols(), full.rows()))
            val small = Mat()
            if (scale < 1.0) Imgproc.resize(full, small, Size(), scale, scale, Imgproc.INTER_AREA) else full.copyTo(small)
            val w = small.cols()
            val h = small.rows()
            val n = w * h
            if (w < 16 || h < 16) return null

            val lab = Mat()
            Imgproc.cvtColor(small, lab, Imgproc.COLOR_RGB2Lab)
            val bytes = ByteArray(n * 3)
            lab.get(0, 0, bytes)
            fun ch(i: Int, c: Int) = (bytes[i * 3 + c].toInt() and 0xFF).toFloat()

            // The ring of pixels along the picture edge.
            val ring = max(3, (min(w, h) * 0.03).roundToInt())
            val onRing = BooleanArray(n)
            var ringCount = 0
            for (y in 0 until h) for (x in 0 until w) {
                if (x < ring || y < ring || x >= w - ring || y >= h - ring) { onRing[y * w + x] = true; ringCount++ }
            }
            val samples = Mat(ringCount, 3, CvType.CV_32F)
            val sampleData = FloatArray(ringCount * 3)
            var k = 0
            for (i in 0 until n) if (onRing[i]) { sampleData[k * 3] = ch(i, 0); sampleData[k * 3 + 1] = ch(i, 1); sampleData[k * 3 + 2] = ch(i, 2); k++ }
            samples.put(0, 0, sampleData)
            val clusters = 3
            val labels = Mat()
            val centers = Mat()
            Core.kmeans(samples, clusters, labels, org.opencv.core.TermCriteria(org.opencv.core.TermCriteria.EPS + org.opencv.core.TermCriteria.COUNT, 20, 1.0), 2, Core.KMEANS_PP_CENTERS, centers)
            val cen = FloatArray(clusters * 3)
            centers.get(0, 0, cen)

            // Distance of every pixel to the nearest edge colour.
            val dist = FloatArray(n)
            for (i in 0 until n) {
                val l = ch(i, 0); val a = ch(i, 1); val b = ch(i, 2)
                var best = Float.MAX_VALUE
                for (c in 0 until clusters) {
                    val dl = l - cen[c * 3]; val da = a - cen[c * 3 + 1]; val db = b - cen[c * 3 + 2]
                    val d = dl * dl + da * da + db * db
                    if (d < best) best = d
                }
                dist[i] = kotlin.math.sqrt(best)
            }
            val ringDist = FloatArray(ringCount)
            k = 0
            for (i in 0 until n) if (onRing[i]) ringDist[k++] = dist[i]
            ringDist.sort()
            val strict = ringDist[(ringCount * 0.95f).toInt().coerceAtMost(ringCount - 1)] * 1.3f + 6f
            val loose = strict * 1.8f

            // Background = matching pixels joined to the edge.
            fun joinedToEdge(limit: Float): BooleanArray {
                val out = BooleanArray(n)
                val queue = IntArray(n)
                var head = 0
                var tail = 0
                for (i in 0 until n) if (onRing[i] && dist[i] <= limit) { out[i] = true; queue[tail++] = i }
                while (head < tail) {
                    val i = queue[head++]
                    val x = i % w
                    val y = i / w
                    if (x > 0 && !out[i - 1] && dist[i - 1] <= limit) { out[i - 1] = true; queue[tail++] = i - 1 }
                    if (x < w - 1 && !out[i + 1] && dist[i + 1] <= limit) { out[i + 1] = true; queue[tail++] = i + 1 }
                    if (y > 0 && !out[i - w] && dist[i - w] <= limit) { out[i - w] = true; queue[tail++] = i - w }
                    if (y < h - 1 && !out[i + w] && dist[i + w] <= limit) { out[i + w] = true; queue[tail++] = i + w }
                }
                return out
            }
            val bgStrict = joinedToEdge(strict)
            val bgLoose = joinedToEdge(loose)

            val m = ByteArray(n) { Imgproc.GC_PR_FGD.toByte() }
            for (i in 0 until n) {
                if (bgLoose[i]) m[i] = Imgproc.GC_PR_BGD.toByte()
                if (bgStrict[i]) m[i] = Imgproc.GC_BGD.toByte()
            }
            // Pixels very unlike the edge colours are certainly the subject (shrunk a little so thin bridges are not trusted).
            val far = Mat(h, w, CvType.CV_8UC1)
            far.put(0, 0, ByteArray(n) { if (dist[it] > loose * 2.5f && !onRing[it]) 1 else 0 })
            Imgproc.erode(far, far, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
            val farBytes = ByteArray(n)
            far.get(0, 0, farBytes)
            for (i in 0 until n) if (farBytes[i].toInt() != 0) m[i] = Imgproc.GC_FGD.toByte()

            // A person found by the phone's model guides the cut where the colours alone are unsure.
            val person = personPrior(src, w, h)
            if (person != null) for (i in 0 until n) {
                val p = person[i]
                if (p > 0.97f && m[i].toInt() != Imgproc.GC_BGD) m[i] = Imgproc.GC_FGD.toByte()
                else if (p < 0.05f && m[i].toInt() == Imgproc.GC_PR_FGD) m[i] = Imgproc.GC_PR_BGD.toByte()
            }
            for (i in 0 until n) if (onRing[i]) m[i] = Imgproc.GC_BGD.toByte()

            val mask = Mat(h, w, CvType.CV_8UC1)
            mask.put(0, 0, m)
            val bgd = Mat()
            val fgd = Mat()
            Imgproc.grabCut(small, mask, Rect(0, 0, w, h), bgd, fgd, 5, Imgproc.GC_INIT_WITH_MASK)
            mask.get(0, 0, m)

            var fg = ByteArray(n) { if (m[it].toInt() == Imgproc.GC_FGD || m[it].toInt() == Imgproc.GC_PR_FGD) 1 else 0 }
            fg = keepMainPieces(fg, w, h)
            fg = fillSmallHoles(fg, w, h)
            val area = fg.count { it.toInt() != 0 }
            if (area < n * 0.01 || area > n * 0.985) return null

            val fgMat = Mat(h, w, CvType.CV_8UC1)
            fgMat.put(0, 0, ByteArray(n) { if (fg[it].toInt() != 0) 255.toByte() else 0 })
            Imgproc.morphologyEx(fgMat, fgMat, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0)))
            val alpha = Mat()
            Imgproc.resize(fgMat, alpha, Size(full.cols().toDouble(), full.rows().toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.GaussianBlur(alpha, alpha, Size(5.0, 5.0), 0.0)
            return applyAlpha(src, alpha)
        } finally {
            full.release()
        }
    }

    /** The smart method, falling back to the rectangle method when it finds nothing believable. */
    fun remove(src: Bitmap): Bitmap = runCatching { removeSmart(src) }.getOrNull() ?: removeWithRect(src)

    private fun applyAlpha(src: Bitmap, alpha: Mat): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val a = ByteArray(pixels.size)
        alpha.get(0, 0, a)
        for (i in pixels.indices) pixels[i] = (pixels[i] and 0x00FFFFFF) or ((a[i].toInt() and 0xFF) shl 24)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return out
    }

    /** Keeps the biggest piece and any piece at least an eighth of its size (a subject can be in several parts). */
    internal fun keepMainPieces(fg: ByteArray, w: Int, h: Int): ByteArray {
        val label = IntArray(w * h)
        val sizes = ArrayList<Int>()
        sizes += 0
        val queue = IntArray(w * h)
        for (start in fg.indices) {
            if (fg[start].toInt() == 0 || label[start] != 0) continue
            val id = sizes.size
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = id
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (fg[j].toInt() != 0 && label[j] == 0) { label[j] = id; queue[tail++] = j }
                }
            }
            sizes += tail
        }
        val biggest = sizes.drop(1).maxOrNull() ?: return fg
        return ByteArray(w * h) { if (label[it] != 0 && sizes[label[it]] * 8 >= biggest) 1 else 0 }
    }

    /** Fills background pockets smaller than 0.3% of the picture that do not touch the edge (pinholes inside the subject). */
    internal fun fillSmallHoles(fg: ByteArray, w: Int, h: Int): ByteArray {
        val out = fg.copyOf()
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val limit = (w * h * 0.003).toInt()
        for (start in fg.indices) {
            if (fg[start].toInt() != 0 || seen[start]) continue
            var head = 0
            var tail = 0
            var touchesEdge = false
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touchesEdge = true
                if (x > 0 && !seen[i - 1] && fg[i - 1].toInt() == 0) { seen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < w - 1 && !seen[i + 1] && fg[i + 1].toInt() == 0) { seen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && !seen[i - w] && fg[i - w].toInt() == 0) { seen[i - w] = true; queue[tail++] = i - w }
                if (y < h - 1 && !seen[i + w] && fg[i + w].toInt() == 0) { seen[i + w] = true; queue[tail++] = i + w }
            }
            if (!touchesEdge && tail < limit) for (q in 0 until tail) out[queue[q]] = 1
        }
        return out
    }

    /**
     * The phone's person model: for each pixel (at the working size) how sure it is that it belongs to a person.
     * Null when no person is found or the model is unavailable. Blocks, so call it off the main thread.
     */
    private fun personPrior(src: Bitmap, w: Int, h: Int): FloatArray? = runCatching {
        val options = com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.Builder()
            .setDetectorMode(com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = com.google.mlkit.vision.segmentation.Segmentation.getClient(options)
        try {
            val result = com.google.android.gms.tasks.Tasks.await(
                segmenter.process(com.google.mlkit.vision.common.InputImage.fromBitmap(src, 0)), 10, java.util.concurrent.TimeUnit.SECONDS,
            )
            val mw = result.width
            val mh = result.height
            val buf = result.buffer.asFloatBuffer()
            val conf = FloatArray(mw * mh)
            buf.get(conf)
            val mat = Mat(mh, mw, CvType.CV_32F)
            mat.put(0, 0, conf)
            val scaled = Mat()
            Imgproc.resize(mat, scaled, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            val out = FloatArray(w * h)
            scaled.get(0, 0, out)
            val covered = out.count { it > 0.5f } / out.size.toFloat()
            if (covered in 0.04f..0.9f) out else null
        } finally {
            segmenter.close()
        }
    }.getOrNull()
}
