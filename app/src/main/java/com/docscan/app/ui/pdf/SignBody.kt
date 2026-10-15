package com.docscan.app.ui.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.docscan.app.pdf.PdfMark
import com.docscan.app.pdf.PdfPreview
import com.docscan.app.ui.kit.GradientButton
import com.docscan.app.ui.settings.SavedAssetsDialog
import kotlin.math.roundToInt

/** A mark being placed; the numbers are fractions of the page (see [PdfMark]). */
private class Placed(
    val id: Int,
    val page: Int,
    val kind: PdfMark.Kind,
    left: Float,
    top: Float,
    size: Float,
    val text: String = "",
    val image: ByteArray? = null,
) {
    var left by mutableStateOf(left)
    var top by mutableStateOf(top)
    var size by mutableStateOf(size)
}

private enum class Tool(val label: String) { SIGNATURE("Signature"), TEXT("Text"), DATE("Today's date"), CHECK("Tick") }

/** Sign and fill a PDF: pick what to add, tap the page to put it there, drag to adjust, then save a copy. */
@Composable
fun SignBody(vm: PdfViewModel, s: PdfSource) {
    val preview = remember(s.file.path) { runCatching { PdfPreview(s.file) }.getOrNull() }
    DisposableEffect(preview) { onDispose { preview?.close() } }
    if (preview == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This PDF could not be displayed.") }
        return
    }
    val marks = remember(s.file.path) { mutableStateListOf<Placed>() }
    var nextId by remember { mutableStateOf(1) }
    var tool by remember { mutableStateOf<Tool?>(null) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var signature by remember { mutableStateOf<ByteArray?>(null) }
    var pickSignature by remember { mutableStateOf(false) }
    var askText by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    fun place(page: Int, fx: Float, fy: Float) {
        val t = tool ?: run { selected = null; return }
        val m = when (t) {
            Tool.SIGNATURE -> signature?.let { Placed(nextId, page, PdfMark.Kind.IMAGE, fx - 0.125f, fy - 0.04f, 0.25f, image = it) }
            Tool.TEXT -> typed.takeIf { it.isNotBlank() }?.let { Placed(nextId, page, PdfMark.Kind.TEXT, fx, fy - 0.01f, 0.025f, text = it) }
            Tool.DATE -> Placed(nextId, page, PdfMark.Kind.TEXT, fx, fy - 0.01f, 0.025f, text = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date()))
            Tool.CHECK -> Placed(nextId, page, PdfMark.Kind.CHECK, fx - 0.01f, fy - 0.01f, 0.03f)
        } ?: return
        nextId++
        marks.add(m)
        selected = m.id
        if (t == Tool.SIGNATURE || t == Tool.TEXT) tool = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tool.entries.forEach { t ->
                FilterChip(selected = tool == t, onClick = {
                    selected = null
                    when (t) {
                        Tool.SIGNATURE -> pickSignature = true
                        Tool.TEXT -> askText = true
                        else -> tool = if (tool == t) null else t
                    }
                }, label = { Text(t.label) })
            }
        }
        Text(
            when {
                tool == null && marks.isEmpty() -> "Choose Signature, Text, Date or Tick, then tap the page where it goes."
                tool != null -> "Tap the page to place it."
                else -> "Drag to move. Tap one to change its size or remove it."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(preview.pageCount) { i ->
                    val bmp by produceState<Bitmap?>(null, preview, i) { value = runCatching { preview.render(i, widthPx * 2) }.getOrNull() }
                    val ratio by produceState(0.707f, preview, i) { value = runCatching { preview.pageSize(i).let { it.first.toFloat() / it.second } }.getOrDefault(0.707f) }
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)
                            .pointerInput(i, tool) { detectTapGestures { o -> place(i, o.x / size.width, o.y / size.height) } },
                    ) {
                        val pw = with(LocalDensity.current) { maxWidth.toPx() }
                        val ph = with(LocalDensity.current) { maxHeight.toPx() }
                        bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
                        marks.filter { it.page == i }.forEach { m ->
                            MarkView(m, pw, ph, m.id == selected, onSelect = { selected = m.id; tool = null }) { dx, dy ->
                                m.left = (m.left + dx / pw).coerceIn(-0.1f, 1f)
                                m.top = (m.top + dy / ph).coerceIn(-0.05f, 1f)
                            }
                        }
                    }
                    Text("Page ${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sel = marks.firstOrNull { it.id == selected }
            if (sel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { sel.size = (sel.size * 0.85f).coerceAtLeast(0.01f) }) { Text("Smaller") }
                    TextButton(onClick = { sel.size = (sel.size * 1.18f).coerceAtMost(0.9f) }) { Text("Bigger") }
                    TextButton(onClick = { marks.remove(sel); selected = null }) { Text("Remove") }
                }
            }
            GradientButton(
                if (marks.isEmpty()) "Save a copy" else "Save a copy (${marks.size} added)",
                onClick = { vm.stamp(marks.map { PdfMark(it.page, it.kind, it.left, it.top, it.size, it.text, it.image) }) },
                enabled = marks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("A drawn signature is only a picture. It is not a certified digital signature.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (pickSignature) {
        SavedAssetsDialog(signature = true, signLabel = "Use this signature", onPick = { bytes, _ ->
            signature = bytes; tool = Tool.SIGNATURE; pickSignature = false
        }, onDismiss = { pickSignature = false })
    }
    if (askText) {
        AlertDialog(
            onDismissRequest = { askText = false },
            title = { Text("Text to add") },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("Name, number, anything") }) },
            confirmButton = { TextButton(onClick = { askText = false; if (typed.isNotBlank()) tool = Tool.TEXT }) { Text("Place on page") } },
            dismissButton = { TextButton(onClick = { askText = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MarkView(m: Placed, pagePxW: Float, pagePxH: Float, selected: Boolean, onSelect: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current
    val sizePx = m.size * pagePxW
    val frame = Modifier
        .offset { IntOffset((m.left * pagePxW).roundToInt(), (m.top * pagePxH).roundToInt()) }
        .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
        .pointerInput(m.id) { detectTapGestures { onSelect() } }
        .pointerInput(m.id) { detectDragGestures(onDragStart = { onSelect() }) { change, d -> change.consume(); onDrag(d.x, d.y) } }
    when (m.kind) {
        PdfMark.Kind.IMAGE -> {
            val bmp = remember(m.id) { m.image?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            if (bmp != null) {
                val wDp = with(density) { sizePx.toDp() }
                val hDp = with(density) { (sizePx * bmp.height / bmp.width).toDp() }
                Image(bmp.asImageBitmap(), "Signature", frame.size(wDp, hDp), contentScale = ContentScale.FillBounds)
            }
        }
        PdfMark.Kind.TEXT -> Text(m.text, frame, color = Color.Black, fontSize = with(density) { sizePx.toSp() }, maxLines = 1, softWrap = false)
        PdfMark.Kind.CHECK -> Text("✔", frame, color = Color.Black, fontSize = with(density) { sizePx.toSp() })
    }
}
