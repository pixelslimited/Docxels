package com.docscan.app.ui.write

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

/**
 * Table properties in a panel docked at the bottom: every change shows on the page at once. Drag the table on the page
 * to move it sideways (or up and down between paragraphs); the cell picker chooses which cell the cell tools act on.
 */
@Composable
fun TableEditorDialog(
    initial: TableBlock,
    contentWidthTwips: Int,
    current: TableBlock = initial,
    onSave: (TableBlock) -> Unit,
    onLive: (TableBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var table by remember { mutableStateOf(initial) }
    var tableStarted by remember { mutableStateOf(false) }
    // A position changed by dragging the table on the page shows here too.
    androidx.compose.runtime.LaunchedEffect(current.indentTwips) {
        if (current.indentTwips != table.indentTwips) table = table.copy(indentTwips = current.indentTwips)
    }
    androidx.compose.runtime.LaunchedEffect(table) {
        if (tableStarted) { kotlinx.coroutines.delay(60); onLive(table) } else tableStarted = true
    }
    var sel by remember { mutableStateOf(0 to 0) }
    val merged = remember(initial) { initial.rows.any { r -> r.any { it.colSpan > 1 || it.vMergeStart || it.vMergeContinue } } }
    val (sr, sc) = sel
    val cur = table.rows.getOrNull(sr)?.getOrNull(sc)
    val total = table.columnWidthsTwips.sum()

    fun update(r: Int, c: Int, cell: TableCell) {
        table = table.copy(rows = table.rows.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, old -> if (ci == c) cell else old } else row })
    }
    fun setTotal(newTotal: Int) {
        val n = table.columnCount
        val scaled = table.columnWidthsTwips.map { (it.toLong() * newTotal / total.coerceAtLeast(1)).toInt().coerceAtLeast(240) }
        table = table.copy(columnWidthsTwips = scaled, indentTwips = table.indentTwips.coerceAtMost(maxOf(0, contentWidthTwips - scaled.sum())))
        if (n == 0) return
    }

    var section by remember { mutableIntStateOf(0) }
    // What the cell tools act on: the picked cell, its row, its column or the whole table.
    var scope by remember { mutableIntStateOf(0) }
    fun inScope(r: Int, c: Int) = when (scope) { 0 -> r == sr && c == sc; 1 -> r == sr; 2 -> c == sc; else -> true }
    fun applyScope(f: (TableCell) -> TableCell) {
        table = table.copy(rows = table.rows.mapIndexed { ri, row -> row.mapIndexed { ci, cell -> if (inScope(ri, ci) && !cell.vMergeContinue) f(cell) else cell } })
    }
    fun mapRuns(cell: TableCell, f: (RunStyle) -> RunStyle) = cell.copy(paragraphs = cell.paragraphs.map { p -> p.copy(runs = p.runs.map { it.copy(style = f(it.style)) }) })
    val noMerge = !merged

    /** A ready-made look: header colours, striped rows, borders. */
    fun quick(headerFill: Int?, headerText: Int?, stripe: Int?, borders: Boolean, border: Int, width: Float) {
        table = table.copy(
            rows = table.rows.mapIndexed { ri, row ->
                row.map { cell ->
                    val head = table.headerRow && ri == 0
                    val body = ri - (if (table.headerRow) 1 else 0)
                    val fill = if (head) headerFill else if (stripe != null && body % 2 == 1) stripe else null
                    val c = cell.copy(shading = fill)
                    if (head) mapRuns(c) { it.copy(bold = true, color = headerText) } else c
                }
            },
            borders = borders, borderColor = border, borderWidthPt = width,
        )
    }

    LivePanel(
        title = "Table",
        onDone = { onSave(table) },
        onRemove = onRemove,
        onRevert = { table = initial },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Cells", "Rows and columns", "Style", "Size").forEachIndexed { i, label ->
                FilterChip(section == i, { section = i }, label = { Text(label) })
            }
        }

        if (section == 0 || section == 2) {
            // Tap a cell in this small copy of the table to choose it.
            Text(if (section == 0) "Tap a cell" else "Tap a cell to preview its shade", style = MaterialTheme.typography.labelLarge)
            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                table.rows.forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEachIndexed { ci, cell ->
                            val picked = ri == sr && ci == sc
                            Box(
                                Modifier.width(if (row.size > 6) 36.dp else 52.dp).height(28.dp)
                                    .background(cell.shading?.let { Color(0xFF000000.toInt() or it) } ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(if (picked) 3.dp else 1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                    .clickable { sel = ri to ci },
                            )
                        }
                    }
                }
            }
        }

        if (section == 0) {
            if (cur != null && !cur.vMergeContinue) {
                OutlinedTextField(
                    value = cur.plain(), onValueChange = { update(sr, sc, cur.withText(it)) },
                    label = { Text("Text in row ${sr + 1}, column ${sc + 1}") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Apply to", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("This cell", "This row", "This column", "Whole table").forEachIndexed { i, label -> FilterChip(scope == i, { scope = i }, label = { Text(label) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    val c0 = table.rows[sr][sc]
                    val allBold = c0.paragraphs.all { p -> p.runs.isNotEmpty() && p.runs.all { it.style.bold } }
                    applyScope { mapRuns(it) { st -> st.copy(bold = !allBold) } }
                }) { Text("Bold") }
                OutlinedButton(onClick = {
                    val c0 = table.rows[sr][sc]
                    val allItalic = c0.paragraphs.all { p -> p.runs.isNotEmpty() && p.runs.all { it.style.italic } }
                    applyScope { mapRuns(it) { st -> st.copy(italic = !allItalic) } }
                }) { Text("Italic") }
                listOf(Align.LEFT to "Left", Align.CENTER to "Centre", Align.RIGHT to "Right").forEach { (al, label) ->
                    OutlinedButton(onClick = { applyScope { c -> c.copy(paragraphs = c.paragraphs.map { it.copy(align = al) }) } }) { Text(label) }
                }
            }
            Text("Text colour", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Int?>(null, 0x000000, 0xFFFFFF, 0xC00000, 0x1F4E79, 0x2E7D32, 0x7F6000, 0x595959).forEach { rgb ->
                    Box(
                        Modifier.width(36.dp).height(36.dp).background(if (rgb == null) MaterialTheme.colorScheme.surface else Color(0xFF000000.toInt() or rgb))
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .clickable { applyScope { c -> mapRuns(c) { it.copy(color = rgb) } } },
                        contentAlignment = Alignment.Center,
                    ) { if (rgb == null) Text("A", style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text("Cell shade", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SHADES.forEach { rgb ->
                    Box(
                        Modifier.width(36.dp).height(36.dp).background(if (rgb == null) MaterialTheme.colorScheme.surface else Color(0xFF000000.toInt() or rgb))
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .clickable { applyScope { c -> c.copy(shading = rgb) } },
                        contentAlignment = Alignment.Center,
                    ) { if (rgb == null) Text("∅") }
                }
            }
            Text("Row height", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val now = com.docscan.app.core.word.CellEdges.rowHeight(table.rows.getOrNull(sr).orEmpty())
                listOf(0 to "Fit text", 400 to "0.7 cm", 570 to "1 cm", 850 to "1.5 cm", 1140 to "2 cm", 1700 to "3 cm").forEach { (tw, label) ->
                    FilterChip(now == tw, {
                        table = table.copy(rows = table.rows.mapIndexed { ri, row -> if (ri == sr) row.map { it.copy(rowHeightTwips = tw) } else row })
                    }, label = { Text(label) })
                }
            }
            Text("Cell borders (choose the sides, then the line)", style = MaterialTheme.typography.labelLarge)
            var sides by remember { mutableStateOf(setOf(0, 1, 2, 3)) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Top", 1 to "Left", 2 to "Bottom", 3 to "Right").forEach { (i, label) ->
                    FilterChip(i in sides, { sides = if (i in sides) sides - i else sides + i }, label = { Text(label) })
                }
            }
            fun setSides(b: com.docscan.app.core.word.CellBorder?) = applyScope { c ->
                var bs = c.borders ?: com.docscan.app.core.word.CellBorders()
                for (i in sides) bs = bs.with(i, b)
                c.copy(borders = if (bs == com.docscan.app.core.word.CellBorders()) null else bs)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { setSides(com.docscan.app.core.word.CellBorder(true, 0x000000, 0.5f)) }) { Text("Thin line") }
                OutlinedButton(onClick = { setSides(com.docscan.app.core.word.CellBorder(true, 0x000000, 2f)) }) { Text("Thick line") }
                OutlinedButton(onClick = { setSides(com.docscan.app.core.word.CellBorder(visible = false)) }) { Text("No line") }
                OutlinedButton(onClick = { setSides(null) }) { Text("Like the table") }
            }
            val noVMerge = table.rows.none { r -> r.any { it.vMergeStart || it.vMergeContinue } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = noVMerge && cur != null && sc + 1 < table.rows[sr].size, onClick = {
                    val row = table.rows[sr]
                    val a = row[sc]
                    val b = row[sc + 1]
                    val joined = if (b.plain().isBlank()) a else a.withText((a.plain() + " " + b.plain()).trim())
                    val newRow = row.toMutableList().also { it[sc] = joined.copy(colSpan = a.colSpan + b.colSpan); it.removeAt(sc + 1) }
                    table = table.copy(rows = table.rows.mapIndexed { i, r -> if (i == sr) newRow else r })
                }) { Text("Merge with right cell") }
                OutlinedButton(enabled = noVMerge && cur != null && cur.colSpan > 1, onClick = {
                    val row = table.rows[sr]
                    val a = row[sc]
                    val newRow = row.toMutableList().also {
                        it[sc] = a.copy(colSpan = 1)
                        for (k in 1 until a.colSpan) it.add(sc + k, TableCell(shading = a.shading))
                    }
                    table = table.copy(rows = table.rows.mapIndexed { i, r -> if (i == sr) newRow else r })
                }) { Text("Split cell") }
            }
        }

        if (section == 1) {
            if (merged) Text("This table has merged cells, so rows and columns can't be added or removed here yet.", style = MaterialTheme.typography.bodySmall)
            Text("Rows", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(enabled = noMerge, onClick = {
                    val cols = table.columnCount
                    table = table.copy(rows = table.rows.toMutableList().also { it.add(sr, List(cols) { TableCell() }) })
                    sel = (sr + 1) to sc
                }) { Text("Insert above") }
                OutlinedButton(enabled = noMerge, onClick = {
                    val cols = table.columnCount
                    table = table.copy(rows = table.rows.toMutableList().also { it.add(sr + 1, List(cols) { TableCell() }) })
                }) { Text("Insert below") }
                OutlinedButton(enabled = noMerge && table.rows.size > 1, onClick = {
                    table = table.copy(rows = table.rows.filterIndexed { i, _ -> i != sr })
                    sel = minOf(sr, table.rows.size - 1) to sc
                }) { Text("Delete row") }
            }
            Text("Columns", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fun addColumn(at: Int) {
                    val n = table.columnCount + 1
                    table = table.copy(
                        rows = table.rows.map { row -> row.toMutableList().also { it.add(at, TableCell()) } },
                        columnWidthsTwips = List(n) { total / n },
                    )
                }
                OutlinedButton(enabled = noMerge && table.columnCount < 12, onClick = { addColumn(sc); sel = sr to (sc + 1) }) { Text("Insert left") }
                OutlinedButton(enabled = noMerge && table.columnCount < 12, onClick = { addColumn(sc + 1) }) { Text("Insert right") }
                OutlinedButton(enabled = noMerge && table.columnCount > 1, onClick = {
                    val n = table.columnCount - 1
                    table = table.copy(
                        rows = table.rows.map { row -> row.filterIndexed { i, _ -> i != sc } },
                        columnWidthsTwips = List(n) { total / n },
                    )
                    sel = sr to minOf(sc, n - 1)
                }) { Text("Delete column") }
                OutlinedButton(onClick = {
                    val n = table.columnCount
                    table = table.copy(columnWidthsTwips = List(n) { total / n })
                }) { Text("Equal widths") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(table.headerRow, { table = table.copy(headerRow = it) })
                Text("First row is a header (repeats on each page)")
            }
        }

        if (section == 2) {
            Text("Ready-made styles", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { quick(null, null, null, true, 0x000000, 0.5f) }) { Text("Plain grid") }
                OutlinedButton(onClick = { quick(0x1F3864, 0xFFFFFF, null, true, 0x7F7F7F, 0.5f) }) { Text("Dark header") }
                OutlinedButton(onClick = { quick(0x00B8D4, 0xFFFFFF, 0xEAF9FC, true, 0xBFBFBF, 0.5f) }) { Text("Docxels blue") }
                OutlinedButton(onClick = { quick(0xD9E2F3, null, 0xF2F2F2, true, 0xBFBFBF, 0.5f) }) { Text("Soft stripes") }
                OutlinedButton(onClick = { quick(0xEDEDED, null, null, false, 0x808080, 0.5f) }) { Text("Minimal") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(table.headerRow, { table = table.copy(headerRow = it) })
                Text("Header row   ")
                Checkbox(table.borders, { table = table.copy(borders = it) })
                Text("Borders")
            }
            if (table.borders) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SHAPE_COLORS.plus(0x808080).forEach { c ->
                        Box(Modifier.width(32.dp).padding(2.dp).background(Color(0xFF000000.toInt() or c)).padding(14.dp)) {
                            TextButton(onClick = { table = table.copy(borderColor = c) }, modifier = Modifier.fillMaxSize()) { Text(if (table.borderColor == c) "✓" else "", color = Color.White) }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.25f to "¼ pt", 0.5f to "½ pt", 1f to "1 pt", 2f to "2 pt", 3f to "3 pt").forEach { (w, l) ->
                        FilterChip(table.borderWidthPt == w, { table = table.copy(borderWidthPt = w) }, label = { Text(l) })
                    }
                }
            }
        }

        if (section == 3) {
            val maxTotal = maxOf(contentWidthTwips, total)
            fun cm(t: Int) = "%.1f cm".format(t / 567f)
            Text("Table width ${cm(total)}")
            Slider(total.toFloat(), { setTotal(it.toInt().coerceIn(table.columnCount * 240, maxTotal)) }, valueRange = (table.columnCount * 240f)..maxTotal.toFloat())
            val colW = table.columnWidthsTwips.getOrElse(sc) { 0 }
            Text("Width of column ${sc + 1}: ${cm(colW)} (pick the column in Cells)")
            Slider(colW.toFloat(), { v ->
                val w = v.toInt().coerceIn(240, maxTotal)
                table = table.copy(columnWidthsTwips = table.columnWidthsTwips.mapIndexed { i, x -> if (i == sc) w else x })
            }, valueRange = 240f..maxTotal.toFloat())
            Text("Position", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val room = (contentWidthTwips - total).coerceAtLeast(0)
                FilterChip(table.indentTwips == 0, { table = table.copy(indentTwips = 0) }, label = { Text("Left") })
                FilterChip(table.indentTwips == room / 2 && room > 0, { table = table.copy(indentTwips = room / 2) }, label = { Text("Centre") })
                FilterChip(table.indentTwips == room && room > 0, { table = table.copy(indentTwips = room) }, label = { Text("Right") })
            }
            Text("Or drag the table on the page: sideways to move it, up or down to place it between paragraphs.", style = MaterialTheme.typography.bodySmall)
        }
    }
}


/**
 * A panel docked at the bottom of the screen that does not dim or block the page, so every change made in it is
 * seen on the document straight away. There is no Save step: the page is already up to date; Done just closes it.
 */
@Composable
private fun LivePanel(
    title: String,
    onDone: () -> Unit,
    onRemove: () -> Unit,
    onRevert: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            window?.let {
                it.setGravity(android.view.Gravity.BOTTOM)
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                it.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.androidx_navPad().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRevert) { Text("Undo") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                    androidx.compose.material3.Button(onClick = onDone) { Text("Done") }
                }
                Column(
                    Modifier.androidx_maxH().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

private fun Modifier.androidx_navPad(): Modifier = this.then(Modifier.navigationBarsPadding())
private fun Modifier.androidx_maxH(): Modifier = this.then(Modifier.heightIn(max = 300.dp))

/**
 * Width and height sliders. With "Keep proportions" on (the default) moving either one resizes both together; switch it
 * off to change width and height separately.
 */
@Composable
internal fun SizeControls(
    widthTwips: Int,
    heightTwips: Int,
    maxWidth: Int,
    maxHeight: Int,
    onChange: (Int, Int) -> Unit,
    heightToo: Boolean = true,
) {
    var locked by remember { mutableStateOf(true) }
    val ratio = remember { heightTwips.toFloat() / widthTwips.coerceAtLeast(1) }
    fun cm(t: Int) = "%.1f cm".format(t / 567f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Width ${cm(widthTwips)}", modifier = Modifier.weight(1f))
        if (heightToo) {
            Text("Keep proportions")
            androidx.compose.material3.Switch(locked, { locked = it }, modifier = Modifier.padding(start = 8.dp))
        }
    }
    Slider(widthTwips.toFloat(), { v ->
        val w = v.toInt().coerceIn(240, maxWidth)
        onChange(w, if (heightToo && locked) (w * ratio).toInt().coerceIn(120, maxHeight) else heightTwips)
    }, valueRange = 240f..maxWidth.toFloat())
    if (heightToo) {
        Text("Height ${cm(heightTwips)}")
        Slider(heightTwips.toFloat(), { v ->
            val h = v.toInt().coerceIn(120, maxHeight)
            onChange(if (locked) (h / ratio.coerceAtLeast(0.01f)).toInt().coerceIn(240, maxWidth) else widthTwips, h)
        }, valueRange = 120f..maxHeight.toFloat())
    }
}

/** Size, alignment and removal for an inserted picture. */
@Composable
fun ImageEditorDialog(
    initial: ImageBlock,
    current: ImageBlock = initial,
    contentWidthTwips: Int,
    onSave: (ImageBlock) -> Unit,
    onLive: (ImageBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var wTw by remember { mutableIntStateOf(initial.widthTwips) }
    var hTw by remember { mutableIntStateOf(initial.heightTwips) }
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
    fun build(): ImageBlock {
        return cur.copy(widthTwips = wTw, heightTwips = hTw, align = align, wrap = wrap)
    }
    // A size changed by dragging the handles on the page shows here too.
    androidx.compose.runtime.LaunchedEffect(current.widthTwips, current.heightTwips) {
        if (current.widthTwips != wTw) wTw = current.widthTwips
        if (current.heightTwips != hTw) hTw = current.heightTwips
    }
    var started by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(wTw, hTw, align, wrap, cur) {
        if (started) { kotlinx.coroutines.delay(40); onLive(build()) } else started = true
    }
    LivePanel(
        title = "Picture",
        onDone = { onSave(build()) },
        onRemove = onRemove,
        onRevert = { wTw = initial.widthTwips; hTw = initial.heightTwips; align = initial.align; wrap = initial.wrap; cur = initial },
    ) {
        run {
            run {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(4.dp).background(if (cur !== initial) Color(0xFFCCCCCC) else Color.Transparent),
                    )
                }
                SizeControls(wTw, hTw, maxOf(contentWidthTwips * 2, initial.widthTwips), maxOf(contentWidthTwips * 3, initial.heightTwips), { w, h -> wTw = w; hTw = h })
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
                    com.docscan.app.core.word.ImageWrap.entries.reversed().forEach { w ->
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
        }
    }
}

private val SHAPE_COLORS = listOf(0x1F4E79, 0x7F6000, 0x375623, 0xC55A11, 0xC00000, 0x7030A0, 0x404040, 0x000000)
private val SHAPE_FILLS = listOf(0xDDEBF7, 0xFFF2CC, 0xE2EFDA, 0xFCE4D6, 0xF8CBAD, 0xEADCF4, 0xD9D9D9, 0xFFFFFF)

/** Size, colours, outline, alignment and wrap for an inserted shape. */
@Composable
fun ShapeEditorDialog(
    initial: com.docscan.app.core.word.ShapeBlock,
    current: com.docscan.app.core.word.ShapeBlock = initial,
    contentWidthTwips: Int,
    onSave: (com.docscan.app.core.word.ShapeBlock) -> Unit,
    onLive: (com.docscan.app.core.word.ShapeBlock) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLine = com.docscan.app.core.word.ShapePresets.isLine(initial.preset)
    var wTw by remember { mutableIntStateOf(initial.widthTwips) }
    var hTw by remember { mutableIntStateOf(initial.heightTwips) }
    var fill by remember { mutableStateOf(initial.fill) }
    var line by remember { mutableIntStateOf(initial.lineColor) }
    var lineWidth by remember { mutableFloatStateOf(initial.lineWidthPt) }
    var align by remember { mutableStateOf(initial.align) }
    var wrap by remember { mutableStateOf(initial.wrap) }
    var text by remember { mutableStateOf(initial.text) }
    var textSize by remember { mutableFloatStateOf(initial.textSizePt) }
    var textBold by remember { mutableStateOf(initial.textBold) }
    fun build(): com.docscan.app.core.word.ShapeBlock {
        val w = wTw.coerceAtLeast(240)
        val h = if (isLine) 0 else hTw.coerceAtLeast(120)
        return initial.copy(widthTwips = w, heightTwips = h, fill = fill, lineColor = line, lineWidthPt = lineWidth, align = align, wrap = wrap, text = text, textSizePt = textSize, textBold = textBold)
    }
    androidx.compose.runtime.LaunchedEffect(current.widthTwips, current.heightTwips) {
        if (current.widthTwips != wTw) wTw = current.widthTwips
        if (current.heightTwips != hTw && !isLine) hTw = current.heightTwips
    }
    var started by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(wTw, hTw, fill, line, lineWidth, align, wrap, text, textSize, textBold) {
        if (started) { kotlinx.coroutines.delay(40); onLive(build()) } else started = true
    }
    LivePanel(
        title = com.docscan.app.core.word.ShapePresets.label(initial.preset),
        onDone = { onSave(build()) },
        onRemove = onRemove,
        onRevert = {
            wTw = initial.widthTwips; hTw = initial.heightTwips
            fill = initial.fill; line = initial.lineColor; lineWidth = initial.lineWidthPt; align = initial.align; wrap = initial.wrap
            text = initial.text; textSize = initial.textSizePt; textBold = initial.textBold
        },
    ) {
        run {
            run {
                SizeControls(wTw, hTw, maxOf(contentWidthTwips * 2, initial.widthTwips), maxOf(contentWidthTwips * 3, initial.heightTwips), { w, h -> wTw = w; hTw = h }, heightToo = !isLine)
                if (!isLine) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text inside") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(9f, 11f, 14f, 18f, 24f, 36f).forEach { sz -> FilterChip(textSize == sz, { textSize = sz }, label = { Text("${sz.toInt()} pt") }) }
                        FilterChip(textBold, { textBold = !textBold }, label = { Text("Bold") })
                    }
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
                    com.docscan.app.core.word.ImageWrap.entries.reversed().forEach { w -> FilterChip(wrap == w, { wrap = w }, label = { Text(w.label) }) }
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
        }
    }
}



/**
 * Brings in a signature from a picture, a PDF page or a Word file, with a preview. "Remove background" turns the
 * white paper transparent and trims the picture to the ink; switch it off to keep the picture as it is.
 */
@Composable
fun SignatureImportDialog(source: Bitmap, onInsert: (ByteArray, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var removeBg by remember { mutableStateOf(true) }
    val result: Bitmap? = remember(source, removeBg) {
        runCatching {
            val w = source.width
            val h = source.height
            if (!removeBg) {
                source
            } else {
                val px = IntArray(w * h)
                source.getPixels(px, 0, w, 0, 0, w, h)
                val ink = com.docscan.app.core.SignatureInk.whiteToAlpha(px)
                val b = com.docscan.app.core.SignatureInk.inkBounds(ink, w, h)
                if (b == null) null
                else Bitmap.createBitmap(Bitmap.createBitmap(ink, w, h, Bitmap.Config.ARGB_8888), b[0], b[1], b[2] - b[0], b[3] - b[1])
            }
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFD9DCE3)), contentAlignment = Alignment.Center) {
                    if (result != null) Image(result.asImageBitmap(), contentDescription = "Signature preview", modifier = Modifier.fillMaxSize().padding(8.dp))
                    else Text("No signature found in this picture.", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remove background", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(removeBg, { removeBg = it })
                }
                Text(
                    "It goes in front of the text. Drag it where you want it. Best on a dark signature on plain white paper.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = result != null, onClick = {
                val r = result ?: return@TextButton
                val out = java.io.ByteArrayOutputStream()
                r.compress(Bitmap.CompressFormat.PNG, 100, out)
                onInsert(out.toByteArray(), r.width, r.height)
            }) { Text("Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
