package com.docscan.app.imaging

import com.docscan.app.core.CellRect
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Finds the cells of a ruled (bordered) table by isolating the horizontal and vertical lines and
 * treating the enclosed white regions as cells. Tables without visible lines are not detected.
 * Merged cells are reported as a single larger cell.
 */
object TableDetector {
    private const val MIN_CELLS = 4

    fun detectCells(rgb: Mat): List<CellRect> {
        val width = rgb.cols()
        val height = rgb.rows()
        val gray = Mat()
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
        val ink = Mat()
        Imgproc.adaptiveThreshold(gray, ink, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 15, 10.0)

        val horizontalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT, Size(max(25, width / 35).toDouble(), 1.0),
        )
        val verticalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT, Size(1.0, max(25, height / 35).toDouble()),
        )
        val horizontal = Mat()
        val vertical = Mat()
        Imgproc.morphologyEx(ink, horizontal, Imgproc.MORPH_OPEN, horizontalKernel)
        Imgproc.morphologyEx(ink, vertical, Imgproc.MORPH_OPEN, verticalKernel)

        val grid = Mat()
        Core.bitwise_or(horizontal, vertical, grid)
        Imgproc.dilate(grid, grid, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
        val cellsMask = Mat()
        Core.bitwise_not(grid, cellsMask)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(cellsMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val pageArea = width.toDouble() * height
        val cells = contours.map { Imgproc.boundingRect(it) }
            // Drop slivers and the large background region that surrounds the table.
            .filter { it.width >= 20 && it.height >= 12 && it.width.toDouble() * it.height < pageArea * 0.6 }
            .map { CellRect(it.x, it.y, it.width, it.height) }

        listOf(gray, ink, horizontal, vertical, grid, cellsMask).forEach { it.release() }
        return if (cells.size >= MIN_CELLS) cells else emptyList()
    }
}
