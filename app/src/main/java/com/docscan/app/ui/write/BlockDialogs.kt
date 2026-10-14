package com.docscan.app.ui.write

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ImageBlock
import com.docscan.app.core.word.Paragraph
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.RunStyle
import com.docscan.app.core.word.TableBlock
import com.docscan.app.core.word.TableCell

private const val HEADER_SHADE = 0xD9E2F3

/** A fresh table with equal columns, optionally with a bold, shaded header row. */
fun newTable(rows: Int, cols: Int, header: Boolean, contentWidthTwips: Int): TableBlock {
    val w = (contentWidthTwips / cols).coerceAtLeast(360)
    val grid = List(rows) { r ->
        List(cols) {
            if (header && r == 0) TableCell(listOf(Paragraph(listOf(Run("", RunStyle(bold = true))))), shading = HEADER_SHADE)
            else TableCell()
        }
    }
    return TableBlock(grid, List(cols) { w }, headerRow = header)
}

private fun TableCell.plain() = paragraphs.joinToString("\n") { it.text }

private fun TableCell.withText(s: String): TableCell {
    if (s == plain()) return this
    val lines = s.split("\n")
    val firstStyle = paragraphs.firstOrNull()?.runs?.firstOrNull()?.style ?: RunStyle()
    val paras = lines.mapIndexed { i, line ->
        val base = paragraphs.getOrNull(i) ?: paragraphs.firstOrNull() ?: Paragraph()
        base.copy(runs = if (line.isEmpty()) emptyList() else listOf(Run(line, base.runs.firstOrNull()?.style ?: firstStyle)))
    }
    return copy(paragraphs = paras)
}

@Composable
fun InsertTableDialog(onInsert: (rows: Int, cols: Int, header: Boolean) -> Unit, onDismiss: () -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    var header by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Rows", rows, 1, 40) { rows = it }
                Stepper("Columns", cols, 1, 8) { cols = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(header, { header = it })
                    Text("Header row")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, cols, header) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(90.dp))
        OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Text("  $value  ")
        OutlinedButton(onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

private val SHADES = listOf(null, 0xD9E2F3, 0xE2EFDA, 0xFFF2CC, 0xFCE4D6, 0xEDEDED, 0xFFFF00)

/** Full-screen table editor: type in cells, add or remove rows and columns, shade cells, header row, borders. */
@Composable
fun TableEditorDialog(
    initial: TableBlock,
    onSave: (TableBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var table by remember { mutableStateOf(initial) }
    var sel by remember { mutableStateOf(0 to 0) }
    val merged = remember(initial) { initial.rows.any { r -> r.any { it.colSpan > 1 || it.vMergeStart || it.vMergeContinue } } }

    fun update(r: Int, c: Int, cell: TableCell) {
        table = table.copy(rows = table.rows.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, old -> if (ci == c) cell else old } else row })
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text("Table", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    TextButton(onClick = onRemove) { Text("Remove") }
                    TextButton(onClick = { onSave(table) }) { Text("Save") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (sr, sc) = sel
                    OutlinedButton(enabled = !merged, onClick = {
                        val cols = table.columnCount
                        table = table.copy(rows = table.rows.toMutableList().also { it.add(sr + 1, List(cols) { TableCell() }) })
                        sel = (sr + 1) to sc
                    }) { Text("Add row") }
                    OutlinedButton(enabled = !merged && table.rows.size > 1, onClick = {
                        table = table.copy(rows = table.rows.filterIndexed { i, _ -> i != sr })
                        sel = minOf(sr, table.rows.size - 1) to sc
                    }) { Text("Delete row") }
                    OutlinedButton(enabled = !merged && table.columnCount < 12, onClick = {
                        val total = table.columnWidthsTwips.sum()
                        val n = table.columnCount + 1
                        table = table.copy(
                            rows = table.rows.map { row -> row.toMutableList().also { it.add(sc + 1, TableCell()) } },
                            columnWidthsTwips = List(n) { total / n },
                        )
                        sel = sr to (sc + 1)
                    }) { Text("Add column") }
                    OutlinedButton(enabled = !merged && table.columnCount > 1, onClick = {
                        val total = table.columnWidthsTwips.sum()
                        val n = table.columnCount - 1
                        table = table.copy(
                            rows = table.rows.map { row -> row.filterIndexed { i, _ -> i != sc } },
                            columnWidthsTwips = List(n) { total / n },
                        )
                        sel = sr to minOf(sc, n - 1)
                    }) { Text("Delete column") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (sr, sc) = sel
                    val noVMerge = table.rows.none { r -> r.any { it.vMergeStart || it.vMergeContinue } }
                    val cur = table.rows.getOrNull(sr)?.getOrNull(sc)
                    OutlinedButton(enabled = !merged, onClick = {
                        val cols = table.columnCount
                        table = table.copy(rows = table.rows.toMutableList().also { it.add(sr, List(cols) { TableCell() }) })
                    }) { Text("Row above") }
                    OutlinedButton(enabled = noVMerge && cur != null && sc + 1 < table.rows[sr].size, onClick = {
                        val row = table.rows[sr]
                        val a = row[sc]
                        val b = row[sc + 1]
                        val joined = if (b.plain().isBlank()) a else a.withText((a.plain() + " " + b.plain()).trim())
                        val newRow = row.toMutableList().also { it[sc] = joined.copy(colSpan = a.colSpan + b.colSpan); it.removeAt(sc + 1) }
                        table = table.copy(rows = table.rows.mapIndexed { i, r -> if (i == sr) newRow else r })
                    }) { Text("Merge right") }
                    OutlinedButton(enabled = noVMerge && cur != null && cur.colSpan > 1, onClick = {
                        val row = table.rows[sr]
                        val a = row[sc]
                        val newRow = row.toMutableList().also {
                            it[sc] = a.copy(colSpan = 1)
                            for (k in 1 until a.colSpan) it.add(sc + k, TableCell(shading = a.shading))
                        }
                        table = table.copy(rows = table.rows.mapIndexed { i, r -> if (i == sr) newRow else r })
                    }) { Text("Split cell") }
                    OutlinedButton(enabled = cur != null && !cur.vMergeContinue, onClick = {
                        val c0 = table.rows[sr][sc]
                        val allBold = c0.paragraphs.all { p -> p.runs.isNotEmpty() && p.runs.all { it.style.bold } }
                        update(sr, sc, c0.copy(paragraphs = c0.paragraphs.map { p ->
                            p.copy(runs = p.runs.map { it.copy(style = it.style.copy(bold = !allBold)) })
                        }))
                    }) { Text("Bold") }
                    listOf(Align.LEFT to "Left", Align.CENTER to "Centre", Align.RIGHT to "Right").forEach { (al, label) ->
                        OutlinedButton(enabled = cur != null && !cur.vMergeContinue, onClick = {
                            val c0 = table.rows[sr][sc]
                            update(sr, sc, c0.copy(paragraphs = c0.paragraphs.map { it.copy(align = al) }))
                        }) { Text(label) }
                    }
                }
                if (merged) Text("This table has merged cells, so rows and columns can't be added or removed here yet.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(table.headerRow, { table = table.copy(headerRow = it) })
                    Text("Header row   ")
                    Checkbox(table.borders, { table = table.copy(borders = it) })
                    Text("Borders")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("Shade cell:")
                    SHADES.forEach { rgb ->
                        Box(
                            Modifier.width(32.dp).padding(2.dp)
                                .background(if (rgb == null) MaterialTheme.colorScheme.surface else Color(0xFF000000.toInt() or rgb))
                                .padding(14.dp)
                                .let { m -> m },
                        ) {
                            TextButton(onClick = {
                                val (r, c) = sel
                                update(r, c, table.rows[r][c].copy(shading = rgb))
                            }, modifier = Modifier.fillMaxSize()) { Text(if (rgb == null) "∅" else "") }
                        }
                    }
                }
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        table.rows.forEachIndexed { r, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEachIndexed { c, cell ->
                                    if (cell.vMergeContinue) {
                                        Box(Modifier.width(150.dp))
                                    } else {
                                        OutlinedTextField(
                                            value = cell.plain(),
                                            onValueChange = { update(r, c, cell.withText(it)) },
                                            modifier = Modifier.width((150 * cell.colSpan).dp)
                                                .background(cell.shading?.let { Color(0xFF000000.toInt() or it) } ?: Color.Transparent),
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            label = if (sel == r to c) ({ Text("Selected") }) else null,
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { src ->
                                                androidx.compose.runtime.LaunchedEffect(src) {
                                                    src.interactions.collect { if (it is androidx.compose.foundation.interaction.FocusInteraction.Focus) sel = r to c }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Size, alignment and removal for an inserted picture. */
@Composable
fun ImageEditorDialog(
    initial: ImageBlock,
    contentWidthTwips: Int,
    onSave: (ImageBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fraction by remember { mutableFloatStateOf((initial.widthTwips.toFloat() / contentWidthTwips).coerceIn(0.1f, 1f)) }
    var align by remember { mutableStateOf(initial.align) }
    var wrap by remember { mutableStateOf(initial.wrap) }
    var cur by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val thumb: Bitmap? = remember(cur) {
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(cur.bytes, 0, cur.bytes.size, o)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(4.dp).background(if (cur !== initial) Color(0xFFCCCCCC) else Color.Transparent),
                    )
                }
                Text("Width ${(fraction * 100).toInt()}% of the page")
                Slider(fraction, { fraction = it }, valueRange = 0.1f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        note = null
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runCatching {
                                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(initial.bytes, 0, initial.bytes.size, o)
                                    var sample = 1
                                    while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
                                    val bmp = BitmapFactory.decodeByteArray(
                                        initial.bytes, 0, initial.bytes.size,
                                        BitmapFactory.Options().apply { inSampleSize = sample },
                                    ) ?: error("decode")
                                    val cut = com.docscan.app.imaging.BackgroundRemover.remove(bmp)
                                    val out = java.io.ByteArrayOutputStream()
                                    cut.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    out.toByteArray()
                                }
                            }
                            busy = false
                            result.onSuccess { cur = initial.copy(bytes = it, extension = "png") }
                                .onFailure { note = "Couldn't remove the background from this picture." }
                        }
                    }) { Text(if (busy) "Working…" else "Remove background") }
                    if (cur !== initial) TextButton(onClick = { cur = initial }) { Text("Restore") }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (cur !== initial) Text("Background removed on this phone. Works best on a clear subject against a plain background.", style = MaterialTheme.typography.bodySmall)
                Text("Wrap text", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    com.docscan.app.core.word.ImageWrap.entries.forEach { w ->
                        FilterChip(wrap == w, { wrap = w }, label = { Text(w.label) })
                    }
                }
                if (wrap.floating) {
                    Text("Drag the picture anywhere on the page with your finger. Tap it to come back here.", style = MaterialTheme.typography.bodySmall)
                } else if (wrap != com.docscan.app.core.word.ImageWrap.IN_LINE) {
                    Text(
                        "Saved in the .docx so Word wraps text around the picture. Here the picture is shown on its own line. Choose In front of text to drag it freely.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Align.LEFT to "Left", Align.CENTER to "Centre", Align.RIGHT to "Right").forEach { (a, l) ->
                        FilterChip(align == a, { align = a }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = (contentWidthTwips * fraction).toInt().coerceAtLeast(720)
                val h = (w.toLong() * initial.heightTwips / initial.widthTwips.coerceAtLeast(1)).toInt().coerceAtLeast(360)
                onSave(cur.copy(widthTwips = w, heightTwips = h, align = align, wrap = wrap))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}


private val SHAPE_COLORS = listOf(0x1F4E79, 0x7F6000, 0x375623, 0xC55A11, 0xC00000, 0x7030A0, 0x404040, 0x000000)
private val SHAPE_FILLS = listOf(0xDDEBF7, 0xFFF2CC, 0xE2EFDA, 0xFCE4D6, 0xF8CBAD, 0xEADCF4, 0xD9D9D9, 0xFFFFFF)

/** Size, colours, outline, alignment and wrap for an inserted shape. */
@Composable
fun ShapeEditorDialog(
    initial: com.docscan.app.core.word.ShapeBlock,
    contentWidthTwips: Int,
    onSave: (com.docscan.app.core.word.ShapeBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLine = com.docscan.app.core.word.ShapePresets.isLine(initial.preset)
    var widthFraction by remember { mutableFloatStateOf((initial.widthTwips.toFloat() / contentWidthTwips).coerceIn(0.05f, 1f)) }
    var heightFraction by remember { mutableFloatStateOf((initial.heightTwips.toFloat() / contentWidthTwips).coerceIn(0.03f, 1f)) }
    var fill by remember { mutableStateOf(initial.fill) }
    var line by remember { mutableIntStateOf(initial.lineColor) }
    var lineWidth by remember { mutableFloatStateOf(initial.lineWidthPt) }
    var align by remember { mutableStateOf(initial.align) }
    var wrap by remember { mutableStateOf(initial.wrap) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.docscan.app.core.word.ShapePresets.label(initial.preset)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Width ${(widthFraction * 100).toInt()}% of the page")
                Slider(widthFraction, { widthFraction = it }, valueRange = 0.05f..1f)
                if (!isLine) {
                    Text("Height ${(heightFraction * 100).toInt()}% of the page width")
                    Slider(heightFraction, { heightFraction = it }, valueRange = 0.03f..1f)
                    Text("Fill", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(fill == null, { fill = null }, label = { Text("None") })
                        SHAPE_FILLS.forEach { c ->
                            Box(
                                Modifier.width(32.dp).padding(2.dp).background(Color(0xFF000000.toInt() or c)).padding(14.dp),
                            ) { TextButton(onClick = { fill = c }, modifier = Modifier.fillMaxSize()) { Text(if (fill == c) "✓" else "") } }
                        }
                    }
                }
                Text("Outline", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHAPE_COLORS.forEach { c ->
                        Box(
                            Modifier.width(32.dp).padding(2.dp).background(Color(0xFF000000.toInt() or c)).padding(14.dp),
                        ) { TextButton(onClick = { line = c }, modifier = Modifier.fillMaxSize()) { Text(if (line == c) "✓" else "", color = Color.White) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0f to "No line", 0.5f to "½ pt", 1f to "1 pt", 2f to "2 pt", 4f to "4 pt", 6f to "6 pt").forEach { (w, l) ->
                        FilterChip(lineWidth == w, { lineWidth = w }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Align.LEFT to "Left", Align.CENTER to "Centre", Align.RIGHT to "Right").forEach { (a, l) ->
                        FilterChip(align == a, { align = a }, label = { Text(l) })
                    }
                }
                Text("Wrap text", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    com.docscan.app.core.word.ImageWrap.entries.forEach { w -> FilterChip(wrap == w, { wrap = w }, label = { Text(w.label) }) }
                }
                if (wrap.floating) {
                    Text("Drag the shape anywhere on the page with your finger. Tap it to come back here.", style = MaterialTheme.typography.bodySmall)
                } else if (wrap != com.docscan.app.core.word.ImageWrap.IN_LINE) {
                    Text(
                        "Saved in the .docx so Word wraps text around the shape. Here it is shown on its own line. Choose In front of text to drag it freely.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = (contentWidthTwips * widthFraction).toInt().coerceAtLeast(240)
                val h = if (isLine) 0 else (contentWidthTwips * heightFraction).toInt().coerceAtLeast(120)
                onSave(initial.copy(widthTwips = w, heightTwips = h, fill = fill, lineColor = line, lineWidthPt = lineWidth, align = align, wrap = wrap))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}


/** A pad to sign with a finger. Returns a transparent PNG of just the ink. */
@androidx.compose.runtime.Composable
fun SignaturePadDialog(onDone: (ByteArray, Int, Int) -> Unit, onDismiss: () -> Unit) {
    val strokes = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    var current by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
    var size by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Sign here") },
        text = {
            androidx.compose.foundation.Canvas(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> change.consume(); current = current + change.position },
                            onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() },
                            onDragCancel = { current = emptyList() },
                        )
                    },
            ) {
                for (st in strokes + listOf(current)) {
                    for (i in 1 until st.size) {
                        drawLine(androidx.compose.ui.graphics.Color(0xFF14213D), st[i - 1], st[i], strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(bmp)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF14213D.toInt(); strokeWidth = 5f; strokeCap = android.graphics.Paint.Cap.ROUND; style = android.graphics.Paint.Style.STROKE
                    }
                    for (st in strokes) for (i in 1 until st.size) cv.drawLine(st[i - 1].x, st[i - 1].y, st[i].x, st[i].y, paint)
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    val b = com.docscan.app.core.SignatureInk.inkBounds(px, w, h)
                    if (b != null) {
                        val cropped = android.graphics.Bitmap.createBitmap(bmp, b[0], b[1], b[2] - b[0], b[3] - b[1])
                        val out = java.io.ByteArrayOutputStream()
                        cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        onDone(out.toByteArray(), cropped.width, cropped.height)
                    } else onDismiss()
                },
            ) { androidx.compose.material3.Text("Use signature") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = { strokes.clear(); current = emptyList() }) { androidx.compose.material3.Text("Clear") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") }
            }
        },
    )
}


/**
 * One-tap table insertion: a grid of cells; tap (or drag over and release) the corner cell of the size you want.
 * "Custom size" opens the number steppers for bigger tables.
 */
@androidx.compose.runtime.Composable
fun TablePickerDialog(onPick: (rows: Int, cols: Int, header: Boolean) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    val maxRows = 8
    val maxCols = 6
    var hot by remember { mutableStateOf(0 to 0) }
    var header by remember { mutableStateOf(true) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    fun cellAt(p: androidx.compose.ui.geometry.Offset): Pair<Int, Int> {
        val c = (p.x / boxSize.width * maxCols).toInt().coerceIn(0, maxCols - 1) + 1
        val r = (p.y / boxSize.height * maxRows).toInt().coerceIn(0, maxRows - 1) + 1
        return r to c
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hot.first == 0) "Insert table" else "${hot.first} rows × ${hot.second} columns") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tap the size you want.", style = MaterialTheme.typography.bodyMedium)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { p -> val (r, c) = cellAt(p); onPick(r, c, header) })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { hot = cellAt(it) },
                                onDrag = { change, _ -> change.consume(); hot = cellAt(change.position) },
                                onDragEnd = { val (r, c) = hot; if (r > 0) onPick(r, c, header) },
                                onDragCancel = { hot = 0 to 0 },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (r in 1..maxRows) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (c in 1..maxCols) {
                                val on = r <= hot.first && c <= hot.second
                                Box(
                                    Modifier.weight(1f).fillMaxSize()
                                        .background(
                                            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                        ),
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(header, { header = it })
                    Text("Header row")
                }
            }
        },
        confirmButton = { TextButton(onClick = onCustom) { Text("Custom size…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
