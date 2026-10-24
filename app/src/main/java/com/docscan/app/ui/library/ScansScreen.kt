package com.docscan.app.ui.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docscan.app.data.DocKind
import com.docscan.app.data.LibraryItem
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.PageThumb
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.shell.LibraryViewModel

private enum class ScanSort(val label: String) {
    NEWEST("Newest first"), OLDEST("Oldest first"), NAME("Name A to Z"), PAGES("Most pages")
}

private fun pageCountOf(item: LibraryItem): Int = item.detail.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

/**
 * Every scan with a thumbnail, as a list or a grid, sorted the way the person likes. Each one can be opened, renamed,
 * exported as a PDF or moved to Trash. Reached from Home and from the camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScansScreen(vm: LibraryViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onNewScan: (String) -> Unit) {
    val context = LocalContext.current
    val all by vm.items.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var grid by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(ScanSort.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    val scans = remember(all, sort) {
        val list = all.filter { it.kind == DocKind.SCAN }
        when (sort) {
            ScanSort.NEWEST -> list.sortedByDescending { it.modified }
            ScanSort.OLDEST -> list.sortedBy { it.modified }
            ScanSort.NAME -> list.sortedBy { it.title.lowercase() }
            ScanSort.PAGES -> list.sortedByDescending { pageCountOf(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scans.isEmpty()) "My scans" else "My scans (${scans.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ScanSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (option == sort) "✓ ${option.label}" else option.label) },
                                    onClick = { sort = option; sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { grid = !grid }) {
                        if (grid) Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Show as list")
                        else Icon(Icons.Default.GridView, contentDescription = "Show as grid")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startScan(onNewScan) },
                icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                text = { Text("New scan") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val actions = ScanActions(
                open = { onOpen(it.id) },
                rename = { renaming = it },
                export = { vm.sharePdf(context, it) },
                delete = { deleting = it },
            )
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                scans.isEmpty() -> Text(
                    "No scans yet. Tap New scan to make your first.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { items(scans, key = { it.id }) { ScanCard(it, actions) } }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { items(scans, key = { it.id }) { ScanRow(it, actions) } }
            }
            BusyOverlay(visible = busy != null, label = busy ?: "")
        }
    }

    renaming?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename scan") },
            text = { OutlinedTextField(name, { name = it.take(80) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.rename(item, name.trim()); renaming = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this scan?") },
            text = { Text("“${item.title}” moves to Trash. You can restore it from Documents, Trash, for 30 days.") },
            confirmButton = { TextButton(onClick = { vm.delete(item); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private class ScanActions(
    val open: (LibraryItem) -> Unit,
    val rename: (LibraryItem) -> Unit,
    val export: (LibraryItem) -> Unit,
    val delete: (LibraryItem) -> Unit,
)

@Composable
private fun ScanMenu(item: LibraryItem, actions: ScanActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More for ${item.title}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { open = false; actions.open(item) })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; actions.rename(item) })
            DropdownMenuItem(text = { Text("Export as PDF") }, onClick = { open = false; actions.export(item) })
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { open = false; actions.delete(item) })
        }
    }
}

@Composable
private fun ScanRow(item: LibraryItem, actions: ScanActions) {
    Row(
        Modifier.fillMaxWidth().docCard().clickable { actions.open(item) }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageThumb(item.cover, Modifier.size(width = 52.dp, height = 68.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${friendlyDate(item.modified)} · ${item.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        ScanMenu(item, actions)
    }
}

@Composable
private fun ScanCard(item: LibraryItem, actions: ScanActions) {
    Column(Modifier.fillMaxWidth().docCard().clickable { actions.open(item) }.padding(10.dp)) {
        PageThumb(item.cover, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${friendlyDate(item.modified)} · ${item.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            ScanMenu(item, actions)
        }
    }
}
