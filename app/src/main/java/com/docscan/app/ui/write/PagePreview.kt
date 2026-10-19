package com.docscan.app.ui.write

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docscan.app.core.word.WordDoc
import com.docscan.app.pdf.PdfPreview
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Shows the document exactly as it prints: every sheet with its margins, header, footer and letterhead repeated on each
 * page. It renders through the same PDF engine as "Share as PDF", so what you see here is what the PDF will contain.
 */
@Composable
fun PagePreview(vm: WordViewModel, doc: WordDoc, onClose: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val file by produceState<File?>(null, doc) {
        value = runCatching { vm.pdfFile(doc, context) }.getOrElse { failed = true; null }
    }
    val preview = remember(file) { file?.let { f -> runCatching { PdfPreview(f) }.getOrNull() } }
    DisposableEffect(preview) { onDispose { preview?.close() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close preview") }
                Text("Page preview", style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    failed || (file != null && preview == null) -> Text("Could not build the preview.", modifier = Modifier.padding(24.dp))
                    preview == null -> CircularProgressIndicator()
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        items((0 until preview.pageCount).toList()) { i ->
                            val bmp by produceState<android.graphics.Bitmap?>(null, i) { value = runCatching { preview.render(i, 1100) }.getOrNull() }
                            val b = bmp
                            if (b == null) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else {
                                Image(b.asImageBitmap(), contentDescription = "Page ${i + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                            }
                        }
                    }
                }
            }
        }
    }
}
