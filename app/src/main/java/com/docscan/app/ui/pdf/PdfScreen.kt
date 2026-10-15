package com.docscan.app.ui.pdf

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.docscan.app.export.Mime
import com.docscan.app.pdf.PdfPreview
import com.docscan.app.pdf.PdfTools
import com.docscan.app.ui.kit.GradientButton
import com.docscan.app.ui.kit.SoftButton
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.theme.DocxelsTheme
import java.io.File

enum class PdfMode(val route: String, val title: String, val hint: String, val many: Boolean) {
    VIEW("view", "PDF viewer", "Open a PDF to read it on this phone.", false),
    MERGE("merge", "Merge PDFs", "Choose two or more PDFs, put them in order, and join them into one.", true),
    SPLIT("split", "Split PDF", "Cut one PDF into several, or pull out just the pages you want.", false),
    ROTATE("rotate", "Rotate pages", "Turn all pages, or only the ones you list.", false),
    ORGANIZE("organize", "Reorder & delete pages", "Move pages up or down, or remove the ones you do not want.", false),
    SIGN("sign", "Sign & fill PDF", "Put your saved signature, text, the date or ticks on any page, then save a copy.", false),
    COMPRESS("compress", "Compress PDF", "Make a PDF with big pictures smaller. Text stays sharp; pictures are re-saved at lower quality.", false),
    PROTECT("protect", "Password protect", "Lock a PDF with a password (AES-256). Keep the password safe: it cannot be recovered.", false);

    companion object {
        fun from(route: String?) = entries.firstOrNull { it.route == route } ?: VIEW
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(vm: PdfViewModel, mode: PdfMode, onBack: () -> Unit, autoPick: Boolean = true, storedId: String? = null) {
    val sources by vm.sources.collectAsState()
    val results by vm.results.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val locked by vm.locked.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var viewing by remember { mutableStateOf<PdfSource?>(null) }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.clearResults(); vm.addUris(listOf(uri), replace = true) }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.clearResults(); vm.addUris(uris, replace = false)
    }
    val pick = { if (mode.many) pickMany.launch(arrayOf(Mime.PDF)) else pickOne.launch(arrayOf(Mime.PDF)) }

    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoPick && !asked && sources.isEmpty()) { asked = true; pick() }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(mode.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (mode == PdfMode.VIEW && sources.isNotEmpty()) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = { com.docscan.app.export.PrintHelper.printPdf(ctx, sources[0].file, sources[0].name) }) { Icon(Icons.Default.Print, "Print") }
                        if (storedId == null) {
                            IconButton(onClick = { vm.saveToLibrary(sources[0].file, sources[0].name) }) { Icon(Icons.Default.Save, "Save in Docxels") }
                        }
                        val viewSaveAs = com.docscan.app.export.SaveAs.rememberSaveAs { vm.say(it) }
                        IconButton(onClick = { viewSaveAs(listOf(sources[0].file)) }) { Icon(Icons.Default.SaveAlt, "Save as") }
                        IconButton(onClick = { vm.share(listOf(sources[0].file)) }) { Icon(Icons.Default.Share, "Share") }
                    }
                    IconButton(onClick = pick) { Icon(Icons.Default.FolderOpen, "Choose PDF") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (mode == PdfMode.VIEW) {
                val src = sources.firstOrNull()
                if (src == null) EmptyPick(mode, pick) else PdfPages(src.file)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(mode.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    if (sources.isEmpty()) {
                        EmptyPick(mode, pick)
                    } else if (results.isNotEmpty()) {
                        ResultCard(results, vm, preview = mode != PdfMode.PROTECT)
                    } else {
                        when (mode) {
                            PdfMode.MERGE -> MergeBody(vm, sources, pick) { viewing = it }
                            PdfMode.SPLIT -> SplitBody(vm, sources[0]) { viewing = it }
                            PdfMode.ROTATE -> RotateBody(vm, sources[0]) { viewing = it }
                            PdfMode.ORGANIZE -> OrganizeBody(vm, sources[0])
                            PdfMode.SIGN -> SignBody(vm, sources[0])
                            PdfMode.COMPRESS -> CompressBody(vm, sources[0]) { viewing = it }
                            PdfMode.PROTECT -> ProtectBody(vm, sources[0]) { viewing = it }
                            PdfMode.VIEW -> Unit
                        }
                    }
                }
            }
            if (busy != null) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(busy ?: "")
                    }
                }
            }
        }
    }

    viewing?.let { v ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewing = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close preview") }
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${v.pages} pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(Modifier.weight(1f)) { PdfPages(v.file) }
                }
            }
        }
    }

    locked?.let { l ->
        var pw by remember(l) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = vm::cancelUnlock,
            title = { Text("Password needed") },
            text = {
                Column {
                    Text("“${l.name}” is protected. Docxels opens an unlocked copy for this session only.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(onClick = { vm.unlock(pw) }, enabled = pw.isNotEmpty()) { Text("Unlock") } },
            dismissButton = { TextButton(onClick = vm::cancelUnlock) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyPick(mode: PdfMode, pick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(if (mode.many) "Choose PDFs" else "Choose a PDF", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        GradientButton("Browse files", onClick = pick)
    }
}

@Composable
private fun ResultCard(files: List<File>, vm: PdfViewModel, preview: Boolean) {
    var selected by remember(files) { mutableStateOf(0) }
    val current = files.getOrElse(selected) { files.first() }
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { vm.say(it) }
    Column(Modifier.fillMaxSize()) {
        Text(
            if (files.size == 1) "Preview: check it before you save or share" else "Preview: ${files.size} files made. Tap one to look at it.",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (files.size > 1) {
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                files.forEachIndexed { i, f ->
                    androidx.compose.material3.FilterChip(selected == i, { selected = i }, label = { Text("${i + 1}. ${f.nameWithoutExtension}", maxLines = 1) })
                }
            }
        }
        Text(
            "${current.name} · ${current.length() / 1024} KB",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (preview) key(current.path) { PdfPages(current) }
            else Text("A password-protected PDF can't be previewed here. Open it in any PDF app with the password.", modifier = Modifier.padding(24.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton(if (files.size > 1) "Share all" else "Share", onClick = { vm.share(files) }, modifier = Modifier.weight(1f))
                SoftButton(if (files.size > 1) "Save all" else "Save to phone", DocxelsTheme.tints.blue, onClick = { vm.saveToPhone(files) }, modifier = Modifier.weight(1f))
            }
            SoftButton("Save in Docxels", DocxelsTheme.tints.violet, onClick = { files.forEach { vm.saveToLibrary(it, it.nameWithoutExtension) } }, modifier = Modifier.fillMaxWidth())
            SoftButton("Save as… (choose folder and name)", DocxelsTheme.tints.blue, onClick = { saveAs(files) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = vm::clearResults, modifier = Modifier.fillMaxWidth()) { Text("Back to editing") }
        }
    }
}

@Composable
private fun MergeBody(vm: PdfViewModel, sources: List<com.docscan.app.ui.pdf.PdfSource>, add: () -> Unit, onView: (PdfSource) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sources) { i, s ->
                Row(Modifier.fillMaxWidth().docCard().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onView(s) }) {
                        Text("${i + 1}. ${s.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${s.pages} pages · tap to view", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.moveSource(i, -1) }, enabled = i > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                    IconButton(onClick = { vm.moveSource(i, 1) }, enabled = i < sources.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                    IconButton(onClick = { vm.removeSource(i) }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Add more PDFs", DocxelsTheme.tints.blue, onClick = add, modifier = Modifier.fillMaxWidth())
            GradientButton(
                if (sources.size < 2) "Add one more PDF to merge" else "Merge ${sources.size} PDFs (${sources.sumOf { it.pages }} pages)",
                onClick = vm::merge,
                enabled = sources.size >= 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FileHeader(s: PdfSource, onView: (PdfSource) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).docCard().clickable { onView(s) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("${s.pages} pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { onView(s) }) { Text("View") }
    }
}

@Composable
private fun SplitBody(vm: PdfViewModel, s: PdfSource, onView: (PdfSource) -> Unit) {
    var range by rememberSaveable(s.file.path) { mutableStateOf("") }
    var every by rememberSaveable(s.file.path) { mutableStateOf("1") }
    Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FileHeader(s, onView)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Extract pages", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(range, { range = it }, singleLine = true, label = { Text("Pages, e.g. 1-3, 5") }, modifier = Modifier.fillMaxWidth())
            GradientButton("Extract into a new PDF", onClick = { vm.extract(range) }, modifier = Modifier.fillMaxWidth())
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Split into equal parts", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                every, { every = it.filter(Char::isDigit).take(4) }, singleLine = true,
                label = { Text("Pages per file") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SoftButton("Split", DocxelsTheme.tints.blue, onClick = { vm.splitEvery(every.toIntOrNull() ?: 0) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RotateBody(vm: PdfViewModel, s: PdfSource, onView: (PdfSource) -> Unit) {
    var range by rememberSaveable(s.file.path) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FileHeader(s, onView)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(range, { range = it }, singleLine = true, label = { Text("Pages (leave empty for all)") }, modifier = Modifier.fillMaxWidth())
            GradientButton("Rotate right 90°", onClick = { vm.rotate(range, 90) }, modifier = Modifier.fillMaxWidth())
            SoftButton("Rotate left 90°", DocxelsTheme.tints.blue, onClick = { vm.rotate(range, -90) }, modifier = Modifier.fillMaxWidth())
            SoftButton("Rotate 180°", DocxelsTheme.tints.blue, onClick = { vm.rotate(range, 180) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompressBody(vm: PdfViewModel, s: PdfSource, onView: (PdfSource) -> Unit) {
    var level by rememberSaveable { mutableStateOf(PdfTools.Compression.MEDIUM.name) }
    Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FileHeader(s, onView)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Current size: ${s.file.length() / 1024} KB", style = MaterialTheme.typography.bodyMedium)
            PdfTools.Compression.entries.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { level = c.name }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = level == c.name, onClick = { level = c.name })
                    Text(c.label)
                }
            }
            GradientButton("Compress PDF", onClick = { vm.compress(PdfTools.Compression.valueOf(level)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProtectBody(vm: PdfViewModel, s: PdfSource, onView: (PdfSource) -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FileHeader(s, onView)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Password (4+ characters)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(again, { again = it }, singleLine = true, label = { Text("Repeat password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            val ok = pw.length >= 4 && pw == again
            GradientButton("Protect PDF", onClick = { vm.protect(pw) }, enabled = ok, modifier = Modifier.fillMaxWidth())
            if (pw.isNotEmpty() && again.isNotEmpty() && pw != again) {
                Text("The passwords do not match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OrganizeBody(vm: PdfViewModel, s: PdfSource) {
    val order = remember(s.file.path) { mutableStateListOf<Int>().also { l -> repeat(s.pages) { l.add(it) } } }
    val preview = remember(s.file.path) { PdfPreview(s.file) }
    DisposableEffect(preview) { onDispose { preview.close() } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(order, key = { _, p -> p }) { pos, page ->
                Row(Modifier.fillMaxWidth().docCard().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageThumbnail(preview, page, Modifier.width(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Page ${page + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { if (pos > 0) { val x = order.removeAt(pos); order.add(pos - 1, x) } }, enabled = pos > 0) { Icon(Icons.Default.ArrowUpward, "Move up") }
                    IconButton(onClick = { if (pos < order.lastIndex) { val x = order.removeAt(pos); order.add(pos + 1, x) } }, enabled = pos < order.lastIndex) { Icon(Icons.Default.ArrowDownward, "Move down") }
                    IconButton(onClick = { order.removeAt(pos) }, enabled = order.size > 1) { Icon(Icons.Default.Delete, "Delete page") }
                }
            }
        }
        Column(Modifier.padding(20.dp)) {
            GradientButton("Save ${order.size} pages", onClick = { vm.organize(order.toList()) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PageThumbnail(preview: PdfPreview, index: Int, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, preview, index) { value = runCatching { preview.render(index, 160) }.getOrNull() }
    Box(modifier.aspectRatio(0.72f).background(Color.White).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/** Scrollable reader: pages are drawn as the user reaches them and dropped when far away. */
@Composable
private fun PdfPages(file: File) {
    val preview = remember(file.path) { runCatching { PdfPreview(file) }.getOrNull() }
    DisposableEffect(preview) { onDispose { preview?.close() } }
    if (preview == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This PDF could not be displayed.") }
        return
    }
    val state = rememberLazyListState()
    val count = preview.pageCount
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
            LazyColumn(state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count) { i ->
                    val bmp by produceState<Bitmap?>(null, preview, i) { value = runCatching { preview.render(i, widthPx * 2) }.getOrNull() }
                    val ratio by produceState(0.707f, preview, i) { value = runCatching { preview.pageSize(i).let { it.first.toFloat() / it.second } }.getOrDefault(0.707f) }
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White), contentAlignment = Alignment.Center) {
                        bmp?.let { Image(it.asImageBitmap(), "Page ${i + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        Text(
            "${(state.firstVisibleItemIndex + 1).coerceAtMost(count)} / $count",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(RoundedCornerShape(50)).background(Color(0xAA000000)).padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
