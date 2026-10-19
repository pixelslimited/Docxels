package com.docscan.app.ui.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.docscan.app.core.CellRect
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import com.docscan.app.ui.fitRect
import kotlin.math.hypot
import kotlin.math.max
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun DrawScope.drawFitted(bitmap: Bitmap, image: androidx.compose.ui.graphics.ImageBitmap) {
    val r = fitRect(size.width, size.height, bitmap.width, bitmap.height)
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
        dstSize = IntSize(r.width.toInt(), r.height.toInt()),
    )
}

/**
 * Photo with a draggable quadrilateral. Eight handles: the four corners and the midpoint of each side
 * (dragging a side moves both of its corners). Changes are committed when the finger lifts.
 */
@Composable
fun CropEditor(
    bitmap: Bitmap,
    corners: List<Pt>,
    onCommit: (List<Pt>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var local by remember(corners) { mutableStateOf(corners) }
    val latestLocal by rememberUpdatedState(local)
    val latestCorners by rememberUpdatedState(corners)
    val latestCommit by rememberUpdatedState(onCommit)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSize by rememberUpdatedState(boxSize)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(bitmap) {
                var active = -1
                val touchRadius = 40.dp.toPx()
                fun handlePositions(): List<Offset> {
                    val r = fitRect(latestSize.width.toFloat(), latestSize.height.toFloat(), bitmap.width, bitmap.height)
                    val c = latestLocal.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
                    val sides = (0 until 4).map { i -> (c[i] + c[(i + 1) % 4]) / 2f }
                    return c + sides
                }
                detectDragGestures(
                    onDragStart = { start ->
                        val handles = handlePositions()
                        val nearest = handles.indices.minByOrNull { hypot(handles[it].x - start.x, handles[it].y - start.y) } ?: -1
                        active = if (nearest >= 0 && hypot(handles[nearest].x - start.x, handles[nearest].y - start.y) <= touchRadius) nearest else -1
                    },
                    onDragEnd = {
                        active = -1
                        if (Geometry.isConvexQuad(latestLocal)) latestCommit(latestLocal) else local = latestCorners
                    },
                    onDragCancel = {
                        active = -1
                        local = latestCorners
                    },
                ) { change, drag ->
                    change.consume()
                    if (active >= 0) {
                        val r = fitRect(latestSize.width.toFloat(), latestSize.height.toFloat(), bitmap.width, bitmap.height)
                        if (r.width > 0f && r.height > 0f) {
                            val dx = drag.x / r.width
                            val dy = drag.y / r.height
                            local = if (active < 4) Geometry.moveCorner(latestLocal, active, dx, dy)
                            else Geometry.moveSide(latestLocal, active - 4, dx, dy)
                        }
                    }
                }
            },
    ) {
        drawFitted(bitmap, image)
        val r = fitRect(size.width, size.height, bitmap.width, bitmap.height)
        val px = local.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
        val path = Path().apply {
            moveTo(px[0].x, px[0].y)
            for (i in 1 until 4) lineTo(px[i].x, px[i].y)
            close()
        }
        drawPath(path, primary.copy(alpha = 0.15f))
        drawPath(path, primary, style = Stroke(width = 3.dp.toPx()))
        px.forEach { drawCircle(Color.White, radius = 12.dp.toPx(), center = it) }
        px.forEach { drawCircle(primary, radius = 9.dp.toPx(), center = it) }
        for (i in 0 until 4) {
            val mid = (px[i] + px[(i + 1) % 4]) / 2f
            drawCircle(Color.White, radius = 9.dp.toPx(), center = mid)
            drawCircle(secondary, radius = 6.dp.toPx(), center = mid)
        }
    }
}

/** Preview image the user can paint over to mark what should be removed. */
@Composable
fun BrushCanvas(
    bitmap: Bitmap,
    strokes: List<List<Pt>>,
    brushFraction: Float,
    onStroke: (List<Pt>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSize by rememberUpdatedState(boxSize)
    val latestOnStroke by rememberUpdatedState(onStroke)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val paint = Color.Red.copy(alpha = 0.5f)

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(bitmap) {
                fun toPt(o: Offset): Pt {
                    val r = fitRect(latestSize.width.toFloat(), latestSize.height.toFloat(), bitmap.width, bitmap.height)
                    return Pt(((o.x - r.left) / r.width).coerceIn(0f, 1f), ((o.y - r.top) / r.height).coerceIn(0f, 1f))
                }
                detectDragGestures(
                    onDragStart = { current = listOf(toPt(it)) },
                    onDragEnd = {
                        if (current.isNotEmpty()) latestOnStroke(current)
                        current = emptyList()
                    },
                    onDragCancel = { current = emptyList() },
                ) { change, _ ->
                    change.consume()
                    current = current + toPt(change.position)
                }
            },
    ) {
        drawFitted(bitmap, image)
        val r = fitRect(size.width, size.height, bitmap.width, bitmap.height)
        val width = 2f * brushFraction * max(r.width, r.height)
        (strokes + listOf(current)).forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            val points = stroke.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
            if (points.size == 1) {
                drawCircle(paint, radius = width / 2f, center = points[0])
            } else {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                }
                drawPath(path, paint, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Image with the detected table cells outlined. */
@Composable
fun TableOverlay(bitmap: Bitmap, cells: List<CellRect>, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val color = MaterialTheme.colorScheme.secondary
    Canvas(modifier.fillMaxSize()) {
        drawFitted(bitmap, image)
        val r = fitRect(size.width, size.height, bitmap.width, bitmap.height)
        val sx = r.width / bitmap.width
        val sy = r.height / bitmap.height
        cells.forEach { c ->
            drawRect(
                color = color,
                topLeft = Offset(r.left + c.x * sx, r.top + c.y * sy),
                size = androidx.compose.ui.geometry.Size(c.w * sx, c.h * sy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
