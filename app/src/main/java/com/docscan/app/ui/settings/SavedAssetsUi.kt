package com.docscan.app.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SavedAssetsPanel(signature: Boolean, say: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(signature) { SavedAssets(context, if (signature) "signatures" else "letterheads") }
    var items by remember(signature) { mutableStateOf(store.list()) }
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { say(it) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    val word = if (signature) "signature" else "letterhead"

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var renaming by remember { mutableStateOf<SavedAssets.Item?>(null) }
    var acting by remember { mutableStateOf<SavedAssets.Item?>(null) }

    fun save(bytes: ByteArray) { pendingBytes = bytes } // asks for a name first

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
            // The actions stay hidden until the row is tapped (then they appear in a sheet, as on the Documents list).
            Row(
                Modifier.fillMaxWidth().clickable { acting = item }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Thumb(store, item, signature, Modifier.width(110.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    val sub = if (signature) listOf(item.position, item.company).filter { it.isNotBlank() }.joinToString(", ") else ""
                    if (signature && sub.isBlank()) Text("Tap to add position and company", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = { picker.launch(PageSources.MIMES) }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text("  Add a $word")
        }
    }
    pendingBytes?.let { bytes ->
        AssetDetailsDialog(signature, "Add this $word", null, "Save",
            onDone = { n, c, p ->
                store.add(n, bytes, c, p); items = store.list(); pendingBytes = null
                say("Saved as ${SavedAssets.cleanName(n)}. Insert it from the editor's ${if (signature) "Signature" else "Letterhead"} menu.")
            },
            onDismiss = { pendingBytes = null })
    }
    acting?.let { item ->
        com.docscan.app.ui.CenteredSheet({ acting = null }) {
            Column(Modifier.padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                @Composable
                fun Act(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, run: () -> Unit) {
                    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    androidx.compose.material3.ListItem(
                        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
                        headlineContent = { Text(label, color = tint) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { acting = null; run() },
                    )
                }
                Act("Edit details", Icons.Default.Edit) { renaming = item }
                Act("Save to the phone", Icons.Default.SaveAlt) {
                    val bytes = store.bytes(item)
                    if (bytes == null) say("Could not read it.") else {
                        val ext = if (bytes.size > 3 && bytes[0] == 0x89.toByte()) "png" else "jpg"
                        val f = java.io.File(java.io.File(context.cacheDir, "exports").apply { mkdirs() }, "${item.name.ifBlank { word }}.$ext")
                        f.writeBytes(bytes)
                        saveAs(listOf(f))
                    }
                }
                Act("Delete", Icons.Default.Delete, danger = true) { store.delete(item); items = store.list() }
            }
        }
    }
    renaming?.let { item ->
        AssetDetailsDialog(signature, "Edit details", item, "Save",
            onDone = { n, c, p -> store.update(item, n, c, p); items = store.list(); renaming = null },
            onDismiss = { renaming = null })
    }
    source?.let { src ->
        SignatureImportDialog(src, onInsert = { bytes, _, _ -> source = null; save(bytes) }, onDismiss = { source = null })
    }
}

/**
 * Asks for what the writing assistant needs to know about a kept letterhead or signature. A letterhead needs its company
 * name; a signature needs the person's name, their company and their position. Nothing is saved until these are filled in.
 */
@Composable
fun AssetDetailsDialog(
    signature: Boolean, title: String, initial: SavedAssets.Item?, action: String,
    onDone: (name: String, company: String, position: String) -> Unit, onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name?.takeIf { it != "Untitled" && !it.matches(Regex("(Letterhead|Signature) \\d+")) } ?: "") }
    var company by remember { mutableStateOf(initial?.company ?: "") }
    var position by remember { mutableStateOf(initial?.position ?: "") }
    val ok = name.isNotBlank() && (!signature || (company.isNotBlank() && position.isNotBlank()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(40) }, singleLine = true,
                    label = { Text(if (signature) "Name of the person (required)" else "Company name (required)") })
                if (signature) {
                    androidx.compose.material3.OutlinedTextField(company, { company = it.take(60) }, singleLine = true, label = { Text("Company (required)") })
                    androidx.compose.material3.OutlinedTextField(position, { position = it.take(60) }, singleLine = true, label = { Text("Position, e.g. Managing Director (required)") })
                }
                Text(
                    "The writing assistant uses ${if (signature) "these" else "the company name"} to know which company a letter is from${if (signature) " and who signs it" else ""}.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onDone(name, company, position) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Editor: choose a saved letterhead (and where it goes) or a saved signature. */
@Composable
fun SavedAssetsDialog(signature: Boolean, onPick: (bytes: ByteArray, mode: String) -> Unit, onDismiss: () -> Unit, signLabel: String = "Place on the signing line") {
    val context = LocalContext.current
    val store = remember(signature) { SavedAssets(context, if (signature) "signatures" else "letterheads") }
    var items by remember(signature) { mutableStateOf(store.list()) }
    var renaming by remember { mutableStateOf<SavedAssets.Item?>(null) }
    val scope = rememberCoroutineScope()
    val word = if (signature) "signature" else "letterhead"
    var message by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var drawing by remember { mutableStateOf(false) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Upload from the phone (picture, PDF or Word file) right here, without going to Settings.
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = if (signature) PageSources.signatureBitmap(context, uri, 1600) else PageSources.letterheadBitmap(context, uri, 2000)
            if (bmp == null) { message = "Could not read a picture from that file."; return@launch }
            if (signature) source = bmp
            else {
                pendingBytes = withContext(Dispatchers.Default) {
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
                bmp.recycle()
            }
        }
    }
    source?.let { src -> com.docscan.app.ui.write.SignatureImportDialog(src, onInsert = { bytes, _, _ -> source = null; pendingBytes = bytes }, onDismiss = { source = null }) }
    if (drawing) com.docscan.app.ui.write.SignaturePadDialog(onDone = { bytes, _, _ -> drawing = false; pendingBytes = bytes }, onDismiss = { drawing = false })
    pendingBytes?.let { bytes ->
        AssetDetailsDialog(signature, "Add this $word", null, "Save",
            onDone = { n, c, p -> store.add(n, bytes, c, p); items = store.list(); pendingBytes = null; message = "Saved. Choose it below to use it." },
            onDismiss = { pendingBytes = null })
    }
    renaming?.let { item ->
        AssetDetailsDialog(signature, "Edit details", item, "Save",
            onDone = { n, c, p -> store.update(item, n, c, p); items = store.list(); renaming = null },
            onDismiss = { renaming = null })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (signature) "My signatures" else "My letterheads") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (items.isEmpty()) {
                    Text("Nothing saved yet. Tap Upload below to add ${if (signature) "a signature" else "a letterhead"}.", style = MaterialTheme.typography.bodyMedium)
                }
                items.forEach { item ->
                    val bytes = remember(item.id) { store.bytes(item) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.labelLarge)
                                val sub = if (signature) listOf(item.position, item.company).filter { it.isNotBlank() }.joinToString(", ") else ""
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { renaming = item }) { Icon(Icons.Default.Edit, contentDescription = "Edit details of ${item.name}") }
                        }
                        Thumb(store, item, signature, Modifier.fillMaxWidth())
                        if (bytes != null) {
                            if (signature) {
                                TextButton(onClick = { onPick(bytes, "sign") }) { Text(signLabel) }
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
        dismissButton = {
            Row {
                TextButton(onClick = { upload.launch(PageSources.MIMES) }) { Text("Upload") }
                if (signature) TextButton(onClick = { drawing = true }) { Text("Draw") }
            }
        },
    )
}
