package com.docscan.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docscan.app.data.HomeItem
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.ConfirmDialog
import com.docscan.app.ui.PageThumb
import com.docscan.app.ui.TextInputDialog
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: HomeViewModel, onScan: (String) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<HomeItem?>(null) }
    var deleting by remember { mutableStateOf<HomeItem?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.cleanUpEmptyDocuments() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.importImages(context, uris, onOpen)
    }
    val shown = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.document.name.contains(query.trim(), ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (items.isEmpty()) {
            EmptyState(Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Header(count = items.size, query = query, onQuery = { query = it })
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Nothing called \"${query.trim()}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(shown, key = { it.document.id }) { item ->
                    DocumentCard(
                        item = item,
                        onOpen = { onOpen(item.document.id) },
                        onRename = { renaming = item },
                        onDelete = { deleting = item },
                    )
                }
            }
        }

        IconButton(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 20.dp, end = 8.dp),
        ) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        Dock(
            modifier = Modifier.align(Alignment.BottomCenter),
            onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScan = { vm.startScan(onScan) },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
        BusyOverlay(visible = busy, label = "Importing photos…")
    }

    renaming?.let { item ->
        TextInputDialog(
            title = "Rename document",
            initial = item.document.name,
            confirmLabel = "Rename",
            onConfirm = {
                vm.rename(item.document.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${item.document.name}\" and all of its pages will be permanently deleted from this phone.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(item.document.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun Header(count: Int, query: String, onQuery: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 16.dp)) {
        Text("Your scans", style = MaterialTheme.typography.displaySmall)
        Text(
            "$count document${if (count == 1) "" else "s"}, all on this phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Search by name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Floating pill: import on the left, the one big action on the right. */
@Composable
private fun Dock(modifier: Modifier, onImport: () -> Unit, onScan: () -> Unit) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 20.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 10.dp,
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).clickable(onClickLabel = "Import photos", role = Role.Button, onClick = onImport),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
            Row(
                Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable(onClickLabel = "Scan", role = Role.Button, onClick = onScan)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                Text("Scan", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A drawn sheet with a folded corner and dashed page corners: the same shape the camera snaps to. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.secondary
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    Column(modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 120.dp, height = 156.dp)) {
            val fold = 34.dp.toPx()
            val sheet = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width - fold, 0f)
                lineTo(size.width, fold)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(sheet, paper)
            drawPath(sheet, ink, style = Stroke(width = 2.dp.toPx()))
            val corner = Path().apply {
                moveTo(size.width - fold, 0f)
                lineTo(size.width - fold, fold)
                lineTo(size.width, fold)
            }
            drawPath(corner, ink, style = Stroke(width = 2.dp.toPx()))
            // Text lines.
            for (i in 0 until 5) {
                val y = fold + 22.dp.toPx() + i * 16.dp.toPx()
                drawLine(ink.copy(alpha = 0.25f), Offset(18.dp.toPx(), y), Offset(size.width - 18.dp.toPx() - if (i == 4) 36.dp.toPx() else 0f, y), strokeWidth = 3.dp.toPx())
            }
            // The page-finder frame.
            val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
            drawRect(accent, topLeft = Offset(-12.dp.toPx(), -12.dp.toPx()), size = Size(size.width + 24.dp.toPx(), size.height + 24.dp.toPx()), style = Stroke(width = 2.dp.toPx(), pathEffect = dash))
        }
        Text("Nothing scanned yet.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 36.dp))
        Text(
            "Tap Scan and the camera finds the page by itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(item: HomeItem, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        // Extra sheets peeking out behind the cover show that this is a stack of pages.
        Box(Modifier.padding(end = 8.dp, bottom = 8.dp)) {
            if (item.pageCount > 2) {
                Box(Modifier.matchParentSize().offset(8.dp, 8.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, line, shape))
            }
            if (item.pageCount > 1) {
                Box(Modifier.matchParentSize().offset(4.dp, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, line, shape))
            }
            PageThumb(item.cover, Modifier.fillMaxWidth().aspectRatio(0.72f).clip(shape).border(1.dp, line, shape))
        }
        Text(item.document.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            "${item.pageCount} page${if (item.pageCount == 1) "" else "s"} · " +
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.document.updatedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontWeight = FontWeight.Normal,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}
