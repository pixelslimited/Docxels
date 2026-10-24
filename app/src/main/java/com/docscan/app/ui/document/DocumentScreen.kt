package com.docscan.app.ui.document

import com.docscan.app.ui.SwitchRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.data.PageEntity
import com.docscan.app.export.DocFormat
import com.docscan.app.export.ExportOptions
import com.docscan.app.export.ShareHelper
import kotlinx.coroutines.launch
import com.docscan.app.imaging.DocMode
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.ConfirmDialog
import com.docscan.app.ui.PageThumb
import com.docscan.app.ui.TextInputDialog
import com.docscan.app.ui.UiEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    vm: DocumentViewModel,
    onBack: () -> Unit,
    onAddPages: () -> Unit,
    onEditPage: (String) -> Unit,
    onOpenWord: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val document by vm.document.collectAsState()
    val pages by vm.pages.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PageEntity?>(null) }
    var showExport by remember { mutableStateOf(false) }
    // Pages ticked by long-pressing one: Share/Export, Rotate and Delete then work on just those pages.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deletingSelected by remember { mutableStateOf(false) }
    val saveScope = androidx.compose.runtime.rememberCoroutineScope()

    // Dragging pages into place. While a drag runs (and until the saved order comes back) [order] is the order shown.
    val gridState = rememberLazyGridState()
    var order by remember { mutableStateOf<List<String>?>(null) }
    var dragging by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val latestPages by rememberUpdatedState(pages)
    val latestSelected by rememberUpdatedState(selected)
    val shown = remember(pages, order) {
        order?.let { o ->
            val byId = pages.associateBy { it.id }
            if (o.size == pages.size && o.all { it in byId }) o.map { byId.getValue(it) } else pages
        } ?: pages
    }
    LaunchedEffect(pages) {
        // The saved order has arrived (or pages were added/deleted meanwhile): stop overriding it.
        val o = order
        if (o != null && dragging.isEmpty() && (o.size != pages.size || o == pages.map { it.id })) order = null
    }
    // Near the top or bottom edge a drag scrolls the grid, so a page can be carried further than the screen shows.
    LaunchedEffect(dragging.isNotEmpty()) {
        while (dragging.isNotEmpty()) {
            val h = gridState.layoutInfo.viewportSize.height
            val edge = h * 0.12f
            when {
                dragY < edge -> gridState.scrollBy(-18f)
                dragY > h - edge -> gridState.scrollBy(18f)
            }
            kotlinx.coroutines.delay(16)
        }
    }
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { msg -> saveScope.launch { snackbar.showSnackbar(msg) } }

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                is UiEvent.Share -> ShareHelper.share(context, event.files, event.mime)
                is UiEvent.SaveAs -> saveAs(event.files)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selected.isNotEmpty()) Text("${selected.size} selected", maxLines = 1)
                    else Text(
                        document?.name ?: "Document",
                        maxLines = 1,
                        modifier = Modifier.clickable { renaming = true },
                    )
                },
                navigationIcon = {
                    if (selected.isNotEmpty()) IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
                    else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                  if (selected.isNotEmpty()) {
                    IconButton(onClick = { selected = pages.map { it.id }.toSet() }) { Icon(Icons.Default.SelectAll, contentDescription = "Select all pages") }
                    IconButton(onClick = { vm.rotatePages(selected) }) { Icon(Icons.Default.RotateRight, contentDescription = "Rotate selected pages") }
                    IconButton(onClick = { showExport = true }) { Icon(Icons.Default.Share, contentDescription = "Export or share selected pages") }
                    IconButton(onClick = { deletingSelected = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete selected pages", tint = MaterialTheme.colorScheme.error) }
                  } else {
                    IconButton(onClick = vm::autoArrange, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto-arrange pages")
                    }
                    IconButton(onClick = vm::rotateAll, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate all pages")
                    }
                    IconButton(onClick = { vm.convertToWord(onOpenWord) }, enabled = pages.isNotEmpty() && busyLabel == null) {
                        Icon(Icons.Default.Description, contentDescription = "Convert to an editable Word document")
                    }
                    IconButton(onClick = { showExport = true }, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Default.Share, contentDescription = "Export or share")
                    }
                  }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPages,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add pages") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (pages.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Apply to all:", style = MaterialTheme.typography.labelLarge)
                        DocMode.entries.forEach { mode ->
                            FilterChip(selected = false, onClick = { vm.applyModeToAll(mode) }, label = { Text(mode.label) })
                        }
                    }
                }
                if (pages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This document has no pages yet.")
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        userScrollEnabled = dragging.isEmpty(),
                        modifier = Modifier.pointerInput(Unit) {
                            var startId: String? = null
                            var wasSelected = false
                            var hadSelection = false
                            var moved = false
                            fun idAt(pos: Offset): String? = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                pos.x >= it.offset.x && pos.x < it.offset.x + it.size.width &&
                                    pos.y >= it.offset.y && pos.y < it.offset.y + it.size.height
                            }?.key as? String
                            fun finish(commit: Boolean) {
                                val id = startId
                                if (id != null) {
                                    if (commit && moved) {
                                        order?.let { vm.setOrder(it) }
                                        if (!hadSelection) selected = emptySet()
                                    } else {
                                        order = null
                                        if (wasSelected && !moved) selected = selected - id
                                    }
                                }
                                startId = null
                                dragging = emptySet()
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val id = idAt(pos) ?: return@detectDragGesturesAfterLongPress
                                    startId = id
                                    moved = false
                                    hadSelection = latestSelected.isNotEmpty()
                                    wasSelected = id in latestSelected
                                    if (!wasSelected) selected = latestSelected + id
                                    dragY = pos.y
                                    order = latestPages.map { it.id }
                                    dragging = if (wasSelected) latestSelected else setOf(id)
                                },
                                onDragEnd = { finish(commit = true) },
                                onDragCancel = { finish(commit = false) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragY = change.position.y
                                    val target = idAt(change.position) ?: return@detectDragGesturesAfterLongPress
                                    val current = order ?: return@detectDragGesturesAfterLongPress
                                    if (target in dragging) return@detectDragGesturesAfterLongPress
                                    val group = current.filter { it in dragging }
                                    val rest = current - group.toSet()
                                    val forward = current.indexOf(target) > current.indexOf(group.first())
                                    val at = rest.indexOf(target) + if (forward) 1 else 0
                                    order = rest.take(at) + group + rest.drop(at)
                                    moved = true
                                },
                            )
                        },
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(shown, key = { _, page -> page.id }) { index, page ->
                            PageCard(
                                modifier = Modifier.animateItem().alpha(if (page.id in dragging) 0.55f else 1f),
                                index = index,
                                count = pages.size,
                                page = page,
                                selected = page.id in selected,
                                onOpen = { if (selected.isNotEmpty()) selected = if (page.id in selected) selected - page.id else selected + page.id else onEditPage(page.id) },
                                onEarlier = { vm.move(page.id, -1) },
                                onLater = { vm.move(page.id, 1) },
                                onRotate = { vm.rotate(page) },
                                onDuplicate = { vm.duplicate(page) },
                                onDelete = { deleting = page },
                            )
                        }
                    }
                }
            }
            BusyOverlay(visible = busyLabel != null, label = busyLabel ?: "")
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename document",
            initial = document?.name.orEmpty(),
            confirmLabel = "Rename",
            onConfirm = {
                vm.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    deleting?.let { page ->
        ConfirmDialog(
            title = "Delete page?",
            message = "This page and its edits will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(page)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    if (deletingSelected) {
        ConfirmDialog(
            title = "Delete ${selected.size} page(s)?",
            message = "These pages and their edits will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = { vm.deletePages(selected); selected = emptySet(); deletingSelected = false },
            onDismiss = { deletingSelected = false },
        )
    }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onExport = { options, save, saveAs ->
                showExport = false
                com.docscan.app.cloud.Analytics.track("export:" + options.format.name.lowercase())
                vm.export(options, save, saveAs, only = selected.takeIf { it.isNotEmpty() })
                selected = emptySet()
            },
        )
    }
}

@Composable
private fun PageCard(
    modifier: Modifier = Modifier,
    index: Int,
    count: Int,
    page: PageEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier.then(if (selected) Modifier.fillMaxWidth().border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier.fillMaxWidth())) {
        // Long-press (select, or drag to move) is handled by the grid, which needs it to tell a drag from a scroll.
        PageThumb(page, Modifier.fillMaxWidth().aspectRatio(0.72f).clickable(onClickLabel = "Edit page", onClick = onOpen))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
            Text("Page ${index + 1}", style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onEarlier, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move page earlier")
            }
            IconButton(onClick = onRotate) { Icon(Icons.Default.RotateRight, contentDescription = "Rotate page") }
            IconButton(onClick = onDuplicate) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate page") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete page") }
            IconButton(onClick = onLater, enabled = index < count - 1) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move page later")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(onDismiss: () -> Unit, onExport: (ExportOptions, Boolean, Boolean) -> Unit) {
    val ui = com.docscan.app.LocalContainer.current.ui
    var options by remember {
        mutableStateOf(ExportOptions(format = runCatching { DocFormat.valueOf(ui.exportFormat) }.getOrDefault(DocFormat.PDF), a4 = ui.exportA4))
    }
    com.docscan.app.ui.CenteredSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Export", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocFormat.entries.forEach { format ->
                    FilterChip(
                        selected = options.format == format,
                        onClick = { options = options.copy(format = format) },
                        label = { Text(format.label) },
                    )
                }
            }
            if (options.format == DocFormat.PDF || options.format == DocFormat.JPEG || options.format == DocFormat.PNG || options.format == DocFormat.ZIP) {
                Text("Picture size", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.docscan.app.export.ImageSize.entries.forEach { size ->
                        FilterChip(selected = options.size == size, onClick = { options = options.copy(size = size) }, label = { Text(size.label) })
                    }
                }
            }
            when (options.format) {
                DocFormat.PDF -> {
                    SwitchRow("Fit pages to A4", options.a4) { options = options.copy(a4 = it) }
                    SwitchRow("Searchable text (slower)", options.searchable) { options = options.copy(searchable = it) }
                    SwitchRow("ID card layout: front and back on one page", options.idCard) { options = options.copy(idCard = it) }
                }
                DocFormat.WORD -> Text(
                    "Text is read from each page (OCR) so you can edit it in Word.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DocFormat.TXT -> Text(
                    "All text from the pages (OCR) in one plain .txt file.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DocFormat.ZIP -> Text("All pages as pictures in one .zip file.", style = MaterialTheme.typography.bodySmall)
                else -> Text("One image file per page.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onExport(options, false, false) }, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = { onExport(options, true, false) }, modifier = Modifier.weight(1f)) { Text("Save to phone") }
            }
            OutlinedButton(onClick = { onExport(options, false, true) }, modifier = Modifier.fillMaxWidth()) { Text("Save as… (choose folder and name)") }
        }
    }
}

