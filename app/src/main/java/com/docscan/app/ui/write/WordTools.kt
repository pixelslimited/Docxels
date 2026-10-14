@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.docscan.app.ui.write

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.docscan.app.core.word.FormatDate
import com.docscan.app.core.word.OutlineItem
import com.docscan.app.core.word.Symbols
import com.docscan.app.core.word.WordStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fonts that Word documents commonly use. Android shows the closest of its own fonts; Word shows the real one. */
val WORD_FONTS = listOf(
    "Calibri", "Arial", "Times New Roman", "Cambria", "Georgia", "Verdana", "Tahoma", "Trebuchet MS",
    "Segoe UI", "Garamond", "Palatino Linotype", "Century Gothic", "Courier New", "Consolas",
    "Aptos", "Book Antiqua", "Comic Sans MS", "Impact",
)

private fun previewFamily(name: String): FontFamily = when (FontNameSpan.familyFor(name)) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

@Composable
fun FontPickerDialog(current: String?, recent: List<String>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font") },
        text = {
            Column {
                Text(
                    "Docxels shows the closest font this phone has. The font name is saved in the document, so Word and LibreOffice show the real one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (recent.isNotEmpty()) item { Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    items(recent) { f -> FontRow(f, f == current) { onPick(f) } }
                    item { Text("All fonts", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(WORD_FONTS) { f -> FontRow(f, f == current) { onPick(f) } }
                }
                OutlinedTextField(custom, { custom = it }, singleLine = true, label = { Text("Other font name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (custom.isNotBlank()) onPick(custom.trim()) else onDismiss() }) { Text(if (custom.isBlank()) "Close" else "Use") } },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("Default font") } },
    )
}

@Composable
private fun FontRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontFamily = previewFamily(name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current % 1f == 0f) current.toInt().toString() else current.toString()) }
    val value = text.replace(',', '.').toFloatOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72).forEach { n ->
                        FilterChip(value == n.toFloat(), { text = n.toString() }, label = { Text("$n") })
                    }
                }
                OutlinedTextField(
                    text, { text = it.take(6) }, singleLine = true, label = { Text("Size in points (1 to 400)") },
                    isError = value == null || value < 1f || value > 400f,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onPick) }, enabled = value != null && value in 1f..400f) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ParagraphDialog(initial: ParaAttrs, onApply: (before: Float?, after: Float?, line: Float?, left: Int, first: Int) -> Unit, onDismiss: () -> Unit) {
    fun fmt(f: Float?) = f?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: ""
    var before by remember { mutableStateOf(fmt(initial.spaceBeforePt)) }
    var after by remember { mutableStateOf(fmt(initial.spaceAfterPt)) }
    var line by remember { mutableStateOf(initial.lineSpacing) }
    var left by remember { mutableStateOf(fmt(String.format(Locale.US, "%.2f", initial.indentLeftTwips / 567f).toFloat())) }
    var special by remember { mutableStateOf(if (initial.indentFirstTwips > 0) 1 else if (initial.indentFirstTwips < 0) 2 else 0) }
    var by by remember { mutableStateOf(fmt(String.format(Locale.US, "%.2f", kotlin.math.abs(initial.indentFirstTwips) / 567f).toFloat())) }
    fun num(s: String) = s.replace(',', '.').toFloatOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paragraph") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spacing", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(before, { before = it.take(5) }, singleLine = true, label = { Text("Before (pt)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(after, { after = it.take(5) }, singleLine = true, label = { Text("After (pt)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text("Line spacing", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Default", 1.0f to "1.0", 1.15f to "1.15", 1.5f to "1.5", 2.0f to "2.0", 2.5f to "2.5", 3.0f to "3.0").forEach { (v, l) ->
                        FilterChip(line == v, { line = v }, label = { Text(l) })
                    }
                }
                Text("Indentation (cm)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(left, { left = it.take(5) }, singleLine = true, label = { Text("Left") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None", "First line", "Hanging").forEachIndexed { i, l -> FilterChip(special == i, { special = i }, label = { Text(l) }) }
                }
                if (special != 0) OutlinedTextField(by, { by = it.take(5) }, singleLine = true, label = { Text("By (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = ((num(left) ?: 0f) * 567f).toInt().coerceIn(0, 12000)
                val b = ((num(by) ?: 0f) * 567f).toInt().coerceIn(0, 12000)
                val first = when (special) { 1 -> b; 2 -> -b; else -> 0 }
                onApply(num(before)?.coerceIn(0f, 400f), num(after)?.coerceIn(0f, 400f), line, l, first)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SymbolDialog(onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Symbols") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Symbols.COMMON.forEach { s ->
                        Box(Modifier.size(44.dp).clickable { onInsert(s) }, contentAlignment = Alignment.Center) {
                            Text(s, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { onInsert(Symbols.NON_BREAKING_SPACE) }) { Text("Non-breaking space") }
                TextButton(onClick = { onInsert(Symbols.NON_BREAKING_HYPHEN) }) { Text("Non-breaking hyphen") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun DateTimeDialog(onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    val now = remember { Date() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date and time") },
        text = {
            Column {
                FormatDate.PATTERNS.forEach { p ->
                    val shown = SimpleDateFormat(p, Locale.getDefault()).format(now)
                    Text(shown, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onInsert(shown) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NavigationDialog(items: List<OutlineItem>, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Navigation") },
        text = {
            if (items.isEmpty()) {
                Text("No headings yet. Give a line the Title, Heading 1, 2 or 3 style and it will appear here.")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(items) { o ->
                        Text(
                            o.title,
                            style = if (o.level <= 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onGo(o.offset) }.padding(start = (maxOf(0, o.level - 1) * 16).dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun WordCountDialog(stats: WordStats, pages: Int?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Word count") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pages != null) CountRow("Pages", pages)
                CountRow("Words", stats.words)
                CountRow("Characters (no spaces)", stats.charsNoSpaces)
                CountRow("Characters (with spaces)", stats.charsWithSpaces)
                CountRow("Paragraphs", stats.paragraphs)
                CountRow("Lines", stats.lines)
                Text("Tables, pictures, headers and footers are not counted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun CountRow(label: String, n: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(String.format(Locale.getDefault(), "%,d", n), style = MaterialTheme.typography.titleSmall)
    }
}
