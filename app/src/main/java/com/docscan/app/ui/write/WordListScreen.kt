package com.docscan.app.ui.write

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.AppContainer
import com.docscan.app.data.WordItem
import com.docscan.app.export.Mime
import com.docscan.app.export.ShareHelper
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.ConfirmDialog
import com.docscan.app.ui.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by container.words.items.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<WordItem?>(null) }
    var deleting by remember { mutableStateOf<WordItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var name = "Imported"
                    var size = -1L
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                            c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                        }
                    }
                    require(size < 0 || size <= MAX_IMPORT_BYTES) { "That file is too large to import." }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_IMPORT_BYTES.toInt() + 1) }
                        ?: error("Could not read that file.")
                    require(bytes.size <= MAX_IMPORT_BYTES) { "That file is too large to import." }
                    container.words.importDocx(bytes, name.removeSuffix(".docx").removeSuffix(".DOCX"))
                }
            }
            busy = false
            result.onSuccess { onOpen(it) }
                .onFailure { snackbar.showSnackbar(it.message?.takeIf { m -> m.length < 120 } ?: "That file could not be opened as a Word document.") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    picker.launch(arrayOf(Mime.DOCX, "application/octet-stream"))
                }) { Icon(Icons.Default.FileOpen, contentDescription = "Import a .docx file") }
                ExtendedFloatingActionButton(
                    onClick = { scope.launch { onOpen(withContext(Dispatchers.IO) { container.words.create() }) } },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New document") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (items.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No documents yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Write a new one, or import a Word (.docx) file to edit it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                    items(items, key = { it.id }) { item ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.modified))) },
                            leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = item })
                                        DropdownMenuItem(text = { Text("Share as .docx") }, onClick = {
                                            menu = false
                                            scope.launch {
                                                runCatching {
                                                    val file = withContext(Dispatchers.IO) {
                                                        val bytes = container.words.docxBytes(item.id) ?: error("missing")
                                                        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                                                        val name = item.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "Document" }
                                                        java.io.File(dir, "$name.docx").also { it.writeBytes(bytes) }
                                                    }
                                                    ShareHelper.share(context, listOf(file), Mime.DOCX)
                                                }.onFailure { snackbar.showSnackbar("Could not share the document.") }
                                            }
                                        })
                                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = item })
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpen(item.id) },
                        )
                    }
                }
            }
        }
        BusyOverlay(visible = busy, label = "Opening…")
    }

    renaming?.let { item ->
        TextInputDialog("Rename document", item.title, "Rename", onConfirm = {
            scope.launch(Dispatchers.IO) { container.words.rename(item.id, it) }
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${item.title}\" will be permanently deleted from this phone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch(Dispatchers.IO) { container.words.delete(item.id) }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
