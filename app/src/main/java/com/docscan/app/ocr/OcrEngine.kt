package com.docscan.app.ocr

import android.graphics.Bitmap
import com.docscan.app.core.ArrangeLogic
import com.docscan.app.core.PageNumberParser
import com.docscan.app.data.PageEntity
import com.docscan.app.imaging.PageRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** On-device text recognition (Latin script). No internet needed. */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** [rotationDegrees] = degrees the bitmap must be rotated clockwise to be upright. */
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int = 0): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, rotationDegrees)).await()

    /** Text of one table cell as a single line. */
    suspend fun cellText(bitmap: Bitmap): String =
        recognize(bitmap).text.replace('\n', ' ').trim()

    /** Paragraph per recognised block, in reading order. */
    suspend fun paragraphs(bitmap: Bitmap): List<String> =
        recognize(bitmap).textBlocks.map { block -> block.lines.joinToString(" ") { it.text } }
            .filter { it.isNotBlank() }
}

/** Works out upright orientation and page order from the content of the pages. */
class PageArranger(private val renderer: PageRenderer, private val ocr: OcrEngine) {
    data class Result(
        val orderedIds: List<String>,
        /** Extra clockwise rotation to apply to each page id. */
        val rotationDeltas: Map<String, Int>,
        val reordered: Boolean,
    )

    suspend fun arrange(pages: List<PageEntity>): Result {
        val rotations = mutableMapOf<String, Int>()
        val numbers = mutableMapOf<String, Int>()

        for (page in pages) {
            val bitmap = renderer.render(page, 1100)
            val (rotation, text) = bestOrientation(bitmap)
            if (rotation != 0) rotations[page.id] = rotation
            findPageNumber(text, uprightHeight(bitmap, rotation))?.let { numbers[page.id] = it }
            bitmap.recycle()
        }
        val ids = pages.map { it.id }
        val ordered = ArrangeLogic.order(ids, numbers)
        return Result(ordered, rotations, ordered != ids)
    }

    private fun uprightHeight(bitmap: Bitmap, rotation: Int) =
        if (rotation % 180 == 0) bitmap.height else bitmap.width

    /**
     * Which way the page must be turned (clockwise, 0/90/180/270) to read upright. Text read the wrong way round comes
     * out as little, low-confidence rubbish, so each turn is scored by its letters and digits weighted by the
     * recogniser's confidence. A page that already reads well is left alone without trying the other turns.
     */
    suspend fun uprightRotation(bitmap: Bitmap): Int = bestOrientation(bitmap).first

    private fun score(text: Text): Double =
        text.textBlocks.flatMap { it.lines }.sumOf { line -> line.text.count { c -> c.isLetterOrDigit() } * (line.confidence.toDouble().takeIf { it > 0.0 } ?: 0.5) }

    private fun readsWell(text: Text): Boolean {
        val lines = text.textBlocks.flatMap { it.lines }
        val chars = lines.sumOf { line -> line.text.count { c -> c.isLetterOrDigit() } }
        if (chars < 40) return false
        return score(text) / chars >= 0.8
    }

    private suspend fun bestOrientation(bitmap: Bitmap): Pair<Int, Text> {
        val upright = ocr.recognize(bitmap, 0)
        if (readsWell(upright)) return 0 to upright
        var bestRotation = 0
        var best = upright
        var bestScore = score(upright)
        for (rotation in listOf(90, 180, 270)) {
            val candidate = ocr.recognize(bitmap, rotation)
            val s = score(candidate)
            // A turn must clearly beat the upright reading, otherwise a page with little text would flip at random.
            if (s > bestScore * 1.5 + 15) {
                best = candidate
                bestScore = s
                bestRotation = rotation
            }
        }
        return bestRotation to best
    }

    /** Turn needed for [page] as it stands now (its stored rotation is included in what is read). */
    suspend fun orientationOf(page: PageEntity): Int {
        val bitmap = renderer.render(page, 900)
        return try { bestOrientation(bitmap).first } finally { bitmap.recycle() }
    }

    /** Looks for a lone number in the footer (preferred) or header band of the page. */
    private fun findPageNumber(text: Text, uprightHeight: Int): Int? {
        val lines = text.textBlocks.flatMap { it.lines }
        val footer = lines.filter { (it.boundingBox?.top ?: 0) > uprightHeight * 0.85 }
        val header = lines.filter { (it.boundingBox?.bottom ?: uprightHeight) < uprightHeight * 0.12 }
        return (footer + header).firstNotNullOfOrNull { PageNumberParser.parse(it.text) }
    }
}
