package com.docscan.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.docscan.app.ui.theme.DocxelsTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class Filter { ALL, WORD, SCANS, PDFS, FAVOURITES, TRASH }
private enum class Sort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME_AZ("Name A to Z"), NAME_ZA("Name Z to A"), TYPE("Type"),
}

private enum class ViewMode(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tile: Int) {
    LIST("List", Icons.AutoMirrored.Filled.ViewList, 0),
    GRID("Grid", Icons.Default.GridView, 150),
    SMALL("Small grid", Icons.Default.GridView, 100),
}

@Composable
fun DocumentsScreen(
    items: List<LibraryItem>, trash: List<LibraryItem>, actions: Actions, vm: LibraryViewModel, onOpen: (LibraryItem) -> Unit,
    onImportDevice: (java.io.File) -> Unit = {}, focusSearch: Boolean = false, onSearchFocused: () -> Unit = {},
) {
    val context = LocalContext.current
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { vm.say(it) }
    val t = DocxelsTheme.tints
    var query by remember { mutableStateOf("") }
    // Documents anywhere on the phone, listed while typing (needs the optional "All files access" switch).
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) { runCatching { searchFocus.requestFocus() }; onSearchFocused() } }
    var deviceAccess by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) deviceAccess = android.os.Environment.isExternalStorageManager()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    var deviceIndex by remember { mutableStateOf<List<com.docscan.app.core.DeviceFile>?>(null) }
    val deviceActive = query.trim().length >= 2
    LaunchedEffect(deviceAccess, deviceActive) {
        if (deviceAccess && deviceActive && deviceIndex == null) {
            deviceIndex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.docscan.app.core.DeviceFiles.scan(android.os.Environment.getExternalStorageDirectory())
            }
        }
    }
    val deviceHits = remember(deviceIndex, query) { deviceIndex?.let { com.docscan.app.core.DeviceFiles.filter(it, query) } ?: emptyList() }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf(runCatching { Sort.valueOf(vm.prefs.librarySort) }.getOrDefault(Sort.NEWEST)) }
    var sortMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    // Tick items by long-pressing one; then Share, Favourite, Move and Trash work on all ticked items.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var movingMany by remember { mutableStateOf(false) }
    var deletingMany by remember { mutableStateOf(false) }
    var foreverMany by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }
    var foreverOf by remember { mutableStateOf<LibraryItem?>(null) }
    var emptying by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<LibraryItem?>(null) }
    var moving by remember { mutableStateOf<LibraryItem?>(null) }
    var tagging by remember { mutableStateOf<LibraryItem?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(runCatching { ViewMode.valueOf(vm.prefs.libraryView) }.getOrDefault(ViewMode.LIST)) }
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
            .let { list ->
                when (sort) {
                    Sort.NEWEST -> list.sortedByDescending { it.modified }
                    Sort.OLDEST -> list.sortedBy { it.modified }
                    Sort.NAME_AZ -> list.sortedBy { it.title.lowercase() }
                    Sort.NAME_ZA -> list.sortedByDescending { it.title.lowercase() }
                    Sort.TYPE -> list.sortedWith(compareBy<LibraryItem> { it.kind.ordinal }.thenByDescending { it.modified })
                }
            }
    }
    fun keyOf(item: LibraryItem) = item.kind.name + item.id
    // Only items still on screen stay ticked (a filter change or a deletion must not leave hidden ticks behind).
    val picked = shown.filter { keyOf(it) in selected }
    val selecting = picked.isNotEmpty()
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }
    fun toggle(item: LibraryItem) { selected = if (keyOf(item) in selected) selected - keyOf(item) else selected + keyOf(item) }

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
            if (selecting) {
                SelectionBar(
                    count = picked.size,
                    trash = filter == Filter.TRASH,
                    allFavourite = picked.all { it.meta.favourite },
                    onClose = { selected = emptySet() },
                    onSelectAll = { selected = shown.map { keyOf(it) }.toSet() },
                    onShare = { vm.sharePdfs(context, picked); selected = emptySet() },
                    onFavourite = { vm.favouriteMany(picked, !picked.all { it.meta.favourite }); selected = emptySet() },
                    onMove = { movingMany = true },
                    onDelete = { if (filter == Filter.TRASH) foreverMany = true else deletingMany = true },
                    onRestore = { vm.restoreMany(picked); selected = emptySet() },
                )
            } else {
                Text("Documents", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search your documents and this phone") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().focusRequester(searchFocus),
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
                    "${sort.label}  ▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).clickable { sortMenu = true }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Sort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; vm.prefs.librarySort = s.name; sortMenu = false }) }
                }
              }
              if (filter == Filter.TRASH && trash.isNotEmpty()) {
                  androidx.compose.material3.TextButton(onClick = { emptying = true }) { Text("Empty Trash") }
              }
              Box {
                  IconButton(onClick = { viewMenu = true }) { Icon(view.icon, contentDescription = "View: ${view.label}") }
                  DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                      ViewMode.entries.forEach { m ->
                          DropdownMenuItem(
                              text = { Text(if (m == view) "${m.label}  ✓" else m.label) },
                              leadingIcon = { Icon(m.icon, contentDescription = null) },
                              onClick = { view = m; vm.prefs.libraryView = m.name; viewMenu = false },
                          )
                      }
                  }
              }
            }
            if (filter == Filter.TRASH) {
                Text("Items stay here for 30 days, then they are deleted for good.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (shown.isEmpty() && !(deviceActive && filter != Filter.TRASH)) {
                Text(
                    "Nothing matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            val showDevice = deviceActive && filter != Filter.TRASH
            if (view != ViewMode.LIST && !showDevice) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(view.tile.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown.size, key = { shown[it].kind.name + shown[it].id }) { idx ->
                        val item = shown[idx]
                        LibraryTile(item, selected = keyOf(item) in selected, small = view == ViewMode.SMALL, onClick = { if (selecting) toggle(item) else actionsFor = item }, onLongClick = { toggle(item) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.kind.name + it.id }) { item ->
                        LibraryRow(item, selected = keyOf(item) in selected, onClick = { if (selecting) toggle(item) else actionsFor = item }, onLongClick = { toggle(item) })
                    }
                    if (showDevice) {
                        item(key = "device-head") {
                            Text("ON THIS PHONE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                        }
                        when {
                            !deviceAccess -> item(key = "device-allow") {
                                DeviceNote("Allow access to all files to search the documents on your phone as you type. Tap here to switch it on.") {
                                    val pkg = android.net.Uri.parse("package:" + context.packageName)
                                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)) }
                                        .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
                                }
                            }
                            deviceIndex == null -> item(key = "device-wait") { DeviceNote("Looking through your phone…", null) }
                            deviceHits.isEmpty() -> item(key = "device-none") { DeviceNote("No PDF, Word or text file on the phone matches.", null) }
                            else -> items(deviceHits.size, key = { "dev:" + deviceHits[it].file.path }) { idx ->
                                val f = deviceHits[idx]
                                DeviceRow(f) { onImportDevice(f.file) }
                            }
                        }
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
            onConfirm = { foreverOf = null; confirmWithLock(context, "Delete \"${item.title}\" for good") { vm.deleteForever(item) } },
            onDismiss = { foreverOf = null },
        )
    }
    if (emptying) {
        ConfirmDialog(
            title = "Empty Trash?",
            message = "Everything in Trash will be permanently deleted from this phone.",
            confirmLabel = "Empty Trash",
            onConfirm = { emptying = false; confirmWithLock(context, "Empty Trash") { vm.emptyTrash() } },
            onDismiss = { emptying = false },
        )
    }
    actionsFor?.let { item ->
        val menu = menuFor(item)
        ItemActionsSheet(
            item = item,
            menu = menu,
            onOpen = if (filter == Filter.TRASH) null else ({ onOpen(item) }),
            onDismiss = { actionsFor = null },
        )
    }
    moving?.let { item ->
        FolderDialog(item.meta.folder, folders, onPick = { vm.moveToFolder(item, it); moving = null }, onDismiss = { moving = null })
    }
    if (movingMany) {
        FolderDialog("", folders, onPick = { vm.moveMany(picked, it); movingMany = false; selected = emptySet() }, onDismiss = { movingMany = false })
    }
    if (deletingMany) {
        ConfirmDialog(
            title = "Move ${picked.size} to Trash?",
            message = "They go to Trash. You can restore them for 30 days.",
            confirmLabel = "Move to Trash",
            onConfirm = { vm.deleteMany(picked); deletingMany = false; selected = emptySet() },
            onDismiss = { deletingMany = false },
        )
    }
    if (foreverMany) {
        ConfirmDialog(
            title = "Delete ${picked.size} for good?",
            message = "They will be permanently deleted from this phone. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { val doomed = picked; foreverMany = false; selected = emptySet(); confirmWithLock(context, "Delete ${doomed.size} items for good") { vm.deleteForeverMany(doomed) } },
            onDismiss = { foreverMany = false },
        )
    }
    tagging?.let { item ->
        TextInputDialog("Tags (separate with commas)", item.meta.tags.joinToString(", "), "Save", onConfirm = { vm.setTags(item, it); tagging = null }, onDismiss = { tagging = null }, allowBlank = true, maxLength = 200)
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun LibraryRow(item: LibraryItem, onClick: () -> Unit, selected: Boolean = false, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().docCard().selectedOutline(selected).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
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
    }
}

/** What can be done with a document. Shown only after the document is tapped. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ItemActionsSheet(item: LibraryItem, menu: RowMenu, onOpen: (() -> Unit)?, onDismiss: () -> Unit) {
    class Act(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val danger: Boolean = false, val run: () -> Unit)
    val acts = ArrayList<Act>()
    if (menu.trashed) {
        menu.onRestore?.let { acts += Act("Restore", Icons.Default.Restore, run = it) }
        acts += Act("Delete for good", Icons.Default.DeleteForever, danger = true, run = menu.onDelete)
    } else {
        onOpen?.let { acts += Act("Open", Icons.Default.OpenInNew, run = it) }
        menu.onFavourite?.let { acts += Act(if (item.meta.favourite) "Remove from favourites" else "Add to favourites", Icons.Default.Star, run = it) }
        menu.onToWord?.let { acts += Act("Convert to Word", Icons.Default.Description, run = it) }
        acts += Act("Rename", Icons.Default.Edit, run = menu.onRename)
        menu.onMove?.let { acts += Act("Move to folder…", Icons.Default.Folder, run = it) }
        menu.onTags?.let { acts += Act("Tags…", Icons.Default.Label, run = it) }
        menu.onDuplicate?.let { acts += Act("Make a copy", Icons.Default.ContentCopy, run = it) }
        menu.onSaveAs?.let { acts += Act(if (item.kind == DocKind.DOCX) "Save as .docx…" else "Save as…", Icons.Default.Save, run = it) }
        menu.onSaveAsPdf?.let { acts += Act("Save as PDF…", Icons.Default.PictureAsPdf, run = it) }
        menu.onShare?.let { acts += Act("Share as .docx", Icons.Default.Share, run = it) }
        menu.onSharePdf?.let { acts += Act("Share as PDF", Icons.Default.Share, run = it) }
        acts += Act("Move to Trash", Icons.Default.Delete, danger = true, run = menu.onDelete)
    }
    com.docscan.app.ui.CenteredSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text("${friendlyDate(item.modified)} · ${item.detail}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            acts.forEach { a ->
                val tint = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                androidx.compose.material3.ListItem(
                    leadingContent = { Icon(a.icon, contentDescription = null, tint = tint) },
                    headlineContent = { Text(a.label, color = tint) },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onDismiss(); a.run() },
                )
            }
        }
    }
}

/** Permanent deletes ask for fingerprint, face or screen lock when the phone has one; otherwise the confirmation dialog is the check. */
private fun confirmWithLock(context: android.content.Context, why: String, run: () -> Unit) {
    val activity = context as? androidx.fragment.app.FragmentActivity
    if (activity != null && com.docscan.app.security.Authenticator.available(activity)) {
        com.docscan.app.security.Authenticator.prompt(activity, "Confirm it is you", why, onSuccess = run)
    } else {
        run()
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun LibraryTile(item: LibraryItem, onClick: () -> Unit, selected: Boolean = false, small: Boolean = false, onLongClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().docCard().selectedOutline(selected).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(if (small) 8.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocBadge(item.kind)
            Box(Modifier.weight(1f))
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            if (item.meta.favourite) Icon(Icons.Default.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        Text(friendlyDate(item.modified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (item.meta.folder.isNotEmpty()) Text("\uD83D\uDCC1 ${item.meta.folder}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun FolderDialog(current: String, folders: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (current.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { onPick("") }) { Text("Take out of \"$current\"") }
                folders.filter { it != current }.forEach { f -> androidx.compose.material3.TextButton(onClick = { onPick(f) }) { Text("\uD83D\uDCC1 $f") } }
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

@Composable
private fun DeviceNote(text: String, onClick: (() -> Unit)?) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
    )
}

/** A document found on the phone. Tapping it imports a copy into Docxels and opens it. */
@Composable
private fun DeviceRow(f: com.docscan.app.core.DeviceFile, onClick: () -> Unit) {
    val pdf = f.name.endsWith(".pdf", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Import and open", onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (pdf) Icons.Default.PictureAsPdf else Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                "${f.folder} · ${android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current, f.size)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

/** A coloured outline around a ticked card. */
@Composable
private fun Modifier.selectedOutline(selected: Boolean): Modifier =
    if (selected) this.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else this

/** Replaces the page title while items are ticked: how many, select all, and what can be done with them. */
@Composable
private fun SelectionBar(
    count: Int, trash: Boolean, allFavourite: Boolean,
    onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit, onFavourite: () -> Unit,
    onMove: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = "Select all") }
        if (trash) {
            IconButton(onClick = onRestore) { Icon(Icons.Default.Restore, contentDescription = "Restore") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteForever, contentDescription = "Delete for good", tint = MaterialTheme.colorScheme.error) }
        } else {
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share as PDF") }
            IconButton(onClick = onFavourite) { Icon(Icons.Default.Star, contentDescription = if (allFavourite) "Remove from favourites" else "Add to favourites") }
            IconButton(onClick = onMove) { Icon(Icons.Default.Folder, contentDescription = "Move to folder") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Move to Trash", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
