package com.docscan.app.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.store.SavedAssets
import com.docscan.app.ui.write.PageSources
import com.docscan.app.ui.write.SignatureImportDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun thumb(bytes: ByteArray?): Bitmap? = bytes?.let {
    runCatching {
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(it, 0, it.size, o)
    }.getOrNull()
}

@Composable
private fun Thumb(store: SavedAssets, item: SavedAssets.Item, signature: Boolean, modifier: Modifier = Modifier) {
    val bmp = remember(item.id) { thumb(store.bytes(item)) }
    Box(modifier.background(if (signature) Color(0xFFD9DCE3) else Color.White), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(if (signature) 48.dp else 72.dp))
    }
}

/** Settings: keep letterheads or signatures on the phone (picture, PDF or Word file), ready to insert in any document. */
@Composable
fun SavedAssetsPanel(signature: Boolean, say: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(signature) { SavedAssets(context, if (signature) "signatures" else "letterheads") }
    var items by remember(signature) { mutableStateOf(store.list()) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    val word = if (signature) "signature" else "letterhead"

    fun save(bytes: ByteArray) {
        store.add("${word.replaceFirstChar { it.uppercase() }} ${items.size + 1}", bytes)
        items = store.list()
        say("Saved. You can insert it from the editor's ${if (signature) "Signature" else "Letterhead"} menu.")
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = if (signature) PageSources.signatureBitmap(context, uri, 1600) else PageSources.letterheadBitmap(context, uri, 2000)
            if (bmp == null) { say("Could not read a picture from that file."); return@launch }
            if (signature) source = bmp
            else {
                val bytes = withContext(Dispatchers.Default) {
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
                bmp.recycle()
                save(bytes)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (signature) "Keep your signatures here and place them in any document. Add one from a picture, a PDF or a Word file: the signature is found in the document and its background is removed."
            else "Keep your letterheads here and add them to any document. Add one from a picture, a PDF or a Word file: the letterhead is picked out of the document, the letter text is left out, and it is fitted to the page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumb(store, item, signature, Modifier.width(110.dp))
                Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { store.delete(item); items = store.list() }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${item.name}") }
            }
        }
        OutlinedButton(onClick = { picker.launch(PageSources.MIMES) }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text("  Add a $word")
        }
    }
    source?.let { src ->
        SignatureImportDialog(src, onInsert = { bytes, _, _ -> source = null; save(bytes) }, onDismiss = { source = null })
    }
}

/** Editor: choose a saved letterhead (and where it goes) or a saved signature. */
@Composable
fun SavedAssetsDialog(signature: Boolean, onPick: (bytes: ByteArray, mode: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(signature) { SavedAssets(context, if (signature) "signatures" else "letterheads") }
    val items = remember(signature) { store.list() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (signature) "My signatures" else "My letterheads") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (items.isEmpty()) {
                    Text("Nothing saved yet. Add ${if (signature) "signatures" else "letterheads"} in Settings.", style = MaterialTheme.typography.bodyMedium)
                }
                items.forEach { item ->
                    val bytes = remember(item.id) { store.bytes(item) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.labelLarge)
                        Thumb(store, item, signature, Modifier.fillMaxWidth())
                        if (bytes != null) {
                            if (signature) {
                                TextButton(onClick = { onPick(bytes, "sign") }) { Text("Place on the signing line") }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { onPick(bytes, "first") }) { Text("First page") }
                                    TextButton(onClick = { onPick(bytes, "rest") }) { Text("Continuous") }
                                    TextButton(onClick = { onPick(bytes, "all") }) { Text("Every page") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
