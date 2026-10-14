package com.docscan.app.ui.write

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
    val thumb: Bitmap? = remember(initial) {
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(initial.bytes, 0, initial.bytes.size, o)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                thumb?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().padding(4.dp)) }
                Text("Width ${(fraction * 100).toInt()}% of the page")
                Slider(fraction, { fraction = it }, valueRange = 0.1f..1f)
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
                onSave(initial.copy(widthTwips = w, heightTwips = h, align = align))
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
