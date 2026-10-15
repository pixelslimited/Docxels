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
import androidx.compose.material.icons.filled.MoreVert
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

private enum class Filter { ALL, WORD, SCANS }
private enum class Sort(val label: String) { DATE("Date modified"), NAME("Name") }

@Composable
fun DocumentsScreen(items: List<LibraryItem>, actions: Actions, vm: LibraryViewModel, onOpen: (LibraryItem) -> Unit) {
    val context = LocalContext.current
    val t = DocxelsTheme.tints
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf(Sort.DATE) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }

    val shown = remember(items, query, filter, sort) {
        items
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.WORD -> it.kind == DocKind.DOCX
                    Filter.SCANS -> it.kind == DocKind.SCAN
                }
            }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            .let { list -> if (sort == Sort.NAME) list.sortedBy { it.title.lowercase() } else list }
    }

    if (items.isEmpty()) {
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
                placeholder = { Text("Search names") },
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
            }
            Box(Modifier.padding(horizontal = 20.dp)) {
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
            if (shown.isEmpty()) {
                Text(
                    "Nothing matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.kind.name + it.id }) { item ->
                    LibraryRow(
                        item,
                        onClick = { onOpen(item) },
                        menu = RowMenu(
                            onRename = { renaming = item },
                            onDelete = { deleting = item },
                            onShare = if (item.kind == DocKind.DOCX) ({ vm.shareDocx(context, item) }) else null,
                            onSharePdf = if (item.kind == DocKind.DOCX) ({ vm.sharePdf(context, item) }) else null,
                            onToWord = if (item.kind == DocKind.SCAN) ({ vm.scanToWord(item.id) { id -> onOpenWordFromLibrary(actions, id) } }) else null,
                        ),
                    )
                }
            }
        }
    }

    renaming?.let { item ->
        TextInputDialog("Rename document", item.title, "Rename", onConfirm = { vm.rename(item, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${item.title}\" will be permanently deleted from this phone.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(item); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

// Opening a converted document needs the navigation callback, which Actions does not carry; the shell wires it below.
private fun onOpenWordFromLibrary(actions: Actions, id: String) = actions.openWord(id)

class RowMenu(
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
    val onShare: (() -> Unit)? = null,
    val onSharePdf: (() -> Unit)? = null,
    val onToWord: (() -> Unit)? = null,
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
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                "${friendlyDate(item.modified)} · ${item.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (menu != null) {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.onToWord?.let { f -> DropdownMenuItem(text = { Text("Convert to Word") }, onClick = { open = false; f() }) }
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; menu.onRename() })
                    menu.onShare?.let { f -> DropdownMenuItem(text = { Text("Share as .docx") }, onClick = { open = false; f() }) }
                    menu.onSharePdf?.let { f -> DropdownMenuItem(text = { Text("Share as PDF") }, onClick = { open = false; f() }) }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; menu.onDelete() })
                }
            }
        }
    }
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
