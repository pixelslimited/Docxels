package com.docscan.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docscan.app.data.DocKind
import com.docscan.app.data.LibraryItem
import com.docscan.app.ui.ConfirmDialog
import com.docscan.app.ui.TextInputDialog
import com.docscan.app.ui.kit.DocBadge
import com.docscan.app.ui.kit.FilterPill
import com.docscan.app.ui.kit.GradientButton
import com.docscan.app.ui.kit.SoftButton
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.shell.Actions
import com.docscan.app.ui.shell.LibraryViewModel
import com.docscan.app.ui.theme.Brand
import com.docscan.app.ui.theme.DocxelsTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Filter { ALL, WORD, SCANS, PDFS, FAVOURITES, TRASH }
private enum class Sort(val label: String) { DATE("Date modified"), NAME("Name") }

@Composable
fun DocumentsScreen(items: List<LibraryItem>, trash: List<LibraryItem>, actions: Actions, vm: LibraryViewModel, onOpen: (LibraryItem) -> Unit) {
    val context = LocalContext.current
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { vm.say(it) }
    val t = DocxelsTheme.tints
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf(Sort.DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }
    var foreverOf by remember { mutableStateOf<LibraryItem?>(null) }
    var emptying by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<LibraryItem?>(null) }
    var tagging by remember { mutableStateOf<LibraryItem?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var grid by remember { mutableStateOf(false) }
    val folders = remember(items) { items.map { it.meta.folder }.filter { it.isNotEmpty() }.distinct().sorted() }
    if (folder != null && folder !in folders) folder = null

    val inside by vm.searchText.collectAsState()
    LaunchedEffect(Unit) { vm.indexContent() }
    val shown = remember(items, trash, query, filter, sort, folder, inside) {
        (if (filter == Filter.TRASH) trash else items)
            .filter {
                when (filter) {
                    Filter.ALL, Filter.TRASH -> true
                    Filter.WORD -> it.kind == DocKind.DOCX
                    Filter.PDFS -> it.kind == DocKind.PDF
                    Filter.SCANS -> it.kind == DocKind.SCAN
                    Filter.FAVOURITES -> it.meta.favourite
                }
            }
            .filter { folder == null || filter == Filter.TRASH || it.meta.folder == folder }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.meta.tags.any { t -> t.contains(query.trim(), ignoreCase = true) } ||
                (inside[com.docscan.app.data.LibraryMeta.key(it.kind, it.id)]?.text?.contains(query.trim(), ignoreCase = true) == true) }
            .let { list -> if (sort == Sort.NAME) list.sortedBy { it.title.lowercase() } else list }
    }

    if (items.isEmpty() && trash.isEmpty()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Documents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)).background(DocxelsTheme.brand.gradient), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                    Text("No documents yet", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 28.dp))
                    Text(
                        "Scan a paper document, start writing, or import a file you already have.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
                    )
                    GradientButton("Scan a document", actions.scan, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton("New document", t.blue, actions.newDoc, Modifier.weight(1f))
                        SoftButton("Import", t.orange, actions.importMenu, Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Documents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search names, tags and words inside") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill("All", filter == Filter.ALL, { filter = Filter.ALL })
                FilterPill("Word", filter == Filter.WORD, { filter = Filter.WORD })
                FilterPill("Scans", filter == Filter.SCANS, { filter = Filter.SCANS })
                FilterPill("PDF", filter == Filter.PDFS, { filter = Filter.PDFS })
                FilterPill("Favourites", filter == Filter.FAVOURITES, { filter = Filter.FAVOURITES })
                folders.forEach { f -> FilterPill("\uD83D\uDCC1 $f", folder == f, { folder = if (folder == f) null else f }) }
                FilterPill("Trash${if (trash.isEmpty()) "" else " (${trash.size})"}", filter == Filter.TRASH, { filter = Filter.TRASH })
            }
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.weight(1f)) {
                Text(
                    "Sorted by ${sort.label.lowercase()}  ▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).clickable { sortMenu = true }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false }) }
                }
              }
              if (filter == Filter.TRASH && trash.isNotEmpty()) {
                  androidx.compose.material3.TextButton(onClick = { emptying = true }) { Text("Empty Trash") }
              }
              IconButton(onClick = { grid = !grid }) {
                  Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, contentDescription = if (grid) "Show as list" else "Show as grid")
              }
            }
            if (filter == Filter.TRASH) {
                Text("Items stay here for 30 days, then they are deleted for good.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (shown.isEmpty()) {
                Text(
                    "Nothing matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            val menuFor: (LibraryItem) -> RowMenu = { item ->
                if (filter == Filter.TRASH) RowMenu(
                    onRename = {}, onDelete = { foreverOf = item }, onRestore = { vm.restore(item) }, trashed = true,
                ) else RowMenu(
                    onRename = { renaming = item },
                    onDelete = { deleting = item },
                    onShare = if (item.kind == DocKind.DOCX) ({ vm.shareDocx(context, item) }) else null,
                    onSharePdf = if (item.kind != DocKind.SCAN) ({ vm.sharePdf(context, item) }) else null,
                    onSaveAs = ({ vm.withFile(context, item, asPdf = item.kind != DocKind.DOCX) { saveAs(listOf(it)) } }),
                    onSaveAsPdf = if (item.kind != DocKind.PDF) ({ vm.withFile(context, item, asPdf = true) { saveAs(listOf(it)) } }) else null,
                    onDuplicate = if (item.kind == DocKind.DOCX) ({ vm.duplicate(item) }) else null,
                    onToWord = if (item.kind == DocKind.SCAN) ({ vm.scanToWord(item.id) { id -> onOpenWordFromLibrary(actions, id) } }) else null,
                    onFavourite = { vm.setFavourite(item, !item.meta.favourite) },
                    onMove = { moving = item },
                    onTags = { tagging = item },
                )
            }
            if (grid) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown.size, key = { shown[it].kind.name + shown[it].id }) { idx ->
                        val item = shown[idx]
                        LibraryTile(item, onClick = { if (filter != Filter.TRASH) onOpen(item) }, menu = menuFor(item))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.kind.name + it.id }) { item ->
                        LibraryRow(item, onClick = { if (filter != Filter.TRASH) onOpen(item) }, menu = menuFor(item))
                    }
                }
            }
        }
    }

    renaming?.let { item ->
        TextInputDialog("Rename document", item.title, "Rename", onConfirm = { vm.rename(item, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Move to Trash?",
            message = "\"${item.title}\" goes to Trash. You can restore it for 30 days.",
            confirmLabel = "Move to Trash",
            onConfirm = { vm.delete(item); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    foreverOf?.let { item ->
        ConfirmDialog(
            title = "Delete for good?",
            message = "\"${item.title}\" will be permanently deleted from this phone. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteForever(item); foreverOf = null },
            onDismiss = { foreverOf = null },
        )
    }
    if (emptying) {
        ConfirmDialog(
            title = "Empty Trash?",
            message = "Everything in Trash will be permanently deleted from this phone.",
            confirmLabel = "Empty Trash",
            onConfirm = { vm.emptyTrash(); emptying = false },
            onDismiss = { emptying = false },
        )
    }
    moving?.let { item ->
        FolderDialog(item, folders, onPick = { vm.moveToFolder(item, it); moving = null }, onDismiss = { moving = null })
    }
    tagging?.let { item ->
        TextInputDialog("Tags (separate with commas)", item.meta.tags.joinToString(", "), "Save", onConfirm = { vm.setTags(item, it); tagging = null }, onDismiss = { tagging = null })
    }
}

// Opening a converted document needs the navigation callback, which Actions does not carry; the shell wires it below.
private fun onOpenWordFromLibrary(actions: Actions, id: String) = actions.openWord(id)

class RowMenu(
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
    val onRestore: (() -> Unit)? = null,
    val trashed: Boolean = false,
    val onFavourite: (() -> Unit)? = null,
    val onMove: (() -> Unit)? = null,
    val onTags: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val onSharePdf: (() -> Unit)? = null,
    val onToWord: (() -> Unit)? = null,
    val onSaveAs: (() -> Unit)? = null,
    val onSaveAsPdf: (() -> Unit)? = null,
    val onDuplicate: (() -> Unit)? = null,
)

@Composable
fun LibraryRow(item: LibraryItem, onClick: () -> Unit, menu: RowMenu?) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().docCard().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocBadge(item.kind)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (item.meta.favourite) Icon(Icons.Default.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
            Text(
                "${friendlyDate(item.modified)} · ${item.detail}${if (item.meta.folder.isNotEmpty()) " · " + item.meta.folder else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (menu != null) {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ItemMenuItems(item, menu) { open = false }
                }
            }
        }
    }
}

@Composable
private fun ItemMenuItems(item: LibraryItem, menu: RowMenu, close: () -> Unit) {
    if (menu.trashed) {
        menu.onRestore?.let { f -> DropdownMenuItem(text = { Text("Restore") }, onClick = { close(); f() }) }
        DropdownMenuItem(text = { Text("Delete for good") }, onClick = { close(); menu.onDelete() })
        return
    }
    menu.onFavourite?.let { f -> DropdownMenuItem(text = { Text(if (item.meta.favourite) "Remove from favourites" else "Add to favourites") }, onClick = { close(); f() }) }
    menu.onToWord?.let { f -> DropdownMenuItem(text = { Text("Convert to Word") }, onClick = { close(); f() }) }
    DropdownMenuItem(text = { Text("Rename") }, onClick = { close(); menu.onRename() })
    menu.onMove?.let { f -> DropdownMenuItem(text = { Text("Move to folder…") }, onClick = { close(); f() }) }
    menu.onTags?.let { f -> DropdownMenuItem(text = { Text("Tags…") }, onClick = { close(); f() }) }
    menu.onDuplicate?.let { f -> DropdownMenuItem(text = { Text("Make a copy") }, onClick = { close(); f() }) }
    menu.onSaveAs?.let { f -> DropdownMenuItem(text = { Text(if (item.kind == DocKind.DOCX) "Save as .docx…" else "Save as…") }, onClick = { close(); f() }) }
    menu.onSaveAsPdf?.let { f -> DropdownMenuItem(text = { Text("Save as PDF…") }, onClick = { close(); f() }) }
    menu.onShare?.let { f -> DropdownMenuItem(text = { Text("Share as .docx") }, onClick = { close(); f() }) }
    menu.onSharePdf?.let { f -> DropdownMenuItem(text = { Text("Share as PDF") }, onClick = { close(); f() }) }
    DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { close(); menu.onDelete() })
}

@Composable
fun LibraryTile(item: LibraryItem, onClick: () -> Unit, menu: RowMenu?) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().docCard().clickable(onClick = onClick).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocBadge(item.kind)
            Box(Modifier.weight(1f))
            if (item.meta.favourite) Icon(Icons.Default.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            if (menu != null) {
                Box {
                    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { ItemMenuItems(item, menu) { open = false } }
                }
            }
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        Text(friendlyDate(item.modified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (item.meta.folder.isNotEmpty()) Text("\uD83D\uDCC1 ${item.meta.folder}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FolderDialog(item: LibraryItem, folders: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.meta.folder.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { onPick("") }) { Text("Take out of \"${item.meta.folder}\"") }
                folders.filter { it != item.meta.folder }.forEach { f -> androidx.compose.material3.TextButton(onClick = { onPick(f) }) { Text("\uD83D\uDCC1 $f") } }
                androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("New folder name") })
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (name.isNotBlank()) onPick(name) }, enabled = name.isNotBlank()) { Text("Create and move") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun friendlyDate(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        yesterday -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}
