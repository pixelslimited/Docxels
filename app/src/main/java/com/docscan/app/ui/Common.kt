package com.docscan.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.docscan.app.LocalContainer
import com.docscan.app.data.PageEntity
import com.docscan.app.export.ExportResult
import java.io.File

/** One-off messages from a ViewModel to the screen. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Share(val files: List<File>, val mime: String) : UiEvent
    /** Ask where on the phone to save these files (file picker). */
    data class SaveAs(val files: List<File>) : UiEvent
}

/** The rectangle an image of [bitmapW]x[bitmapH] occupies when fitted and centred in a [boxW]x[boxH] box. */
fun fitRect(boxW: Float, boxH: Float, bitmapW: Int, bitmapH: Int): Rect {
    if (bitmapW <= 0 || bitmapH <= 0 || boxW <= 0f || boxH <= 0f) return Rect.Zero
    val scale = minOf(boxW / bitmapW, boxH / bitmapH)
    val w = bitmapW * scale
    val h = bitmapH * scale
    val left = (boxW - w) / 2f
    val top = (boxH - h) / 2f
    return Rect(left, top, left + w, top + h)
}

val Rect.sizeF: Size get() = Size(width, height)

/** Cached thumbnail of a page. Shows a spinner while rendering. */
@Composable
fun PageThumb(page: PageEntity?, modifier: Modifier = Modifier) {
    val renderer = LocalContainer.current.renderer
    val bitmap by produceState<Bitmap?>(initialValue = null, page) {
        value = if (page == null) null else runCatching { renderer.thumbnail(page) }.getOrNull()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (page != null) {
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = "Loading preview" })
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Full-screen dimmed progress overlay used while images are processed. */
@Composable
fun BusyOverlay(visible: Boolean, label: String) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 320.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text(
                    label, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Converts an export result into a UI event. */
fun ExportResult.toShareEvent(): UiEvent = UiEvent.Share(files, mime)
