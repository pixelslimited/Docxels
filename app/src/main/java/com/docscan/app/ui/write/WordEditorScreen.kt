package com.docscan.app.ui.write

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.ListKind
import com.docscan.app.core.word.PageSetup
import com.docscan.app.core.word.PaperSize
import com.docscan.app.core.word.ParaStyle
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.ImageBlock
import com.docscan.app.core.word.WordDoc
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.docscan.app.data.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.docscan.app.export.Mime
import com.docscan.app.export.ShareHelper
import com.docscan.app.ui.TextInputDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TEXT_COLORS = listOf(0x000000, 0x595959, 0xC00000, 0xED7D31, 0x00B050, 0x0070C0, 0x7030A0, 0xFFFFFF)
private val HIGHLIGHTS = listOf(0xFFFF00, 0x92D050, 0x00FFFF, 0xFF99CC, 0xFFC000, 0xD9D9D9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(vm: WordViewModel, onBack: () -> Unit) {
    val doc = vm.doc
    when {
        vm.failed -> {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.Center) {
                    Text("This document could not be opened.", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onBack) { Text("Back") }
                }
            }
        }
        doc == null -> Surface(Modifier.fillMaxSize()) {}
        else -> Editor(vm, doc, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(vm: WordViewModel, initial: WordDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val controller = remember { EditorController(density, initial.blocks) }
    val state by controller.state.collectAsState()
    var title by remember { mutableStateOf(initial.title) }
    var page by remember { mutableStateOf(initial.page) }
    var tick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var pageOpen by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tableOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val block = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = ImageIO.decodeUri(context, uri, 1600) ?: error("unreadable")
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    val w = page.contentWidthTwips
                    val h = (w.toLong() * bmp.height / bmp.width.coerceAtLeast(1)).toInt()
                    bmp.recycle()
                    ImageBlock(out.toByteArray(), "jpg", w, h)
                }.getOrNull()
            }
            if (block == null) snackbar.showSnackbar("Could not read that picture.") else controller.insertBlock(Block.Image(block))
        }
    }
    val snackbar = remember { SnackbarHostState() }

    fun saveNow() = vm.save(controller.toDoc(title, page))

    controller.onChanged = { tick++ }
    LaunchedEffect(tick, title, page) {
        if (tick > 0 || title != initial.title || page != initial.page) {
            delay(1500)
            saveNow()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { saveNow() }
    DisposableEffect(Unit) { onDispose { controller.detach() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { renaming = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        saveNow()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = controller::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = controller::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Find and replace") }, onClick = { menu = false; findOpen = true })
                            DropdownMenuItem(text = { Text("Insert table") }, onClick = { menu = false; tableOpen = true })
                            DropdownMenuItem(text = { Text("Insert picture") }, onClick = {
                                menu = false
                                photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            DropdownMenuItem(text = { Text("Insert page break") }, onClick = { menu = false; controller.insertPageBreak() })
                            DropdownMenuItem(text = { Text("Page setup") }, onClick = { menu = false; pageOpen = true })
                            DropdownMenuItem(text = { Text("Word count") }, onClick = { menu = false; counts = controller.counts() })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Share as .docx") },
                                onClick = {
                                    menu = false
                                    val d = controller.toDoc(title, page)
                                    scope.launch {
                                        runCatching { ShareHelper.share(context, listOf(vm.docxFile(d, context)), Mime.DOCX) }
                                            .onFailure { snackbar.showSnackbar("Could not share the document.") }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx -> RichEditText(ctx).also { controller.attach(it); controller.setChipTapListener { i -> editing = i } } },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            FormatBar(controller, state)
        }
    }

    if (renaming) {
        TextInputDialog("Rename document", title, "Rename", onConfirm = {
            title = it.ifBlank { "Untitled" }
            renaming = false
        }, onDismiss = { renaming = false })
    }
    if (tableOpen) InsertTableDialog(
        onInsert = { r, c, h ->
            tableOpen = false
            controller.insertBlock(Block.Table(newTable(r, c, h, page.contentWidthTwips)))
        },
        onDismiss = { tableOpen = false },
    )
    editing?.let { idx ->
        when (val b = controller.blockAt(idx)) {
            is Block.Table -> TableEditorDialog(
                initial = b.table,
                onSave = { controller.replaceBlock(idx, Block.Table(it)); editing = null },
                onRemove = { controller.removeBlock(idx); editing = null },
                onDismiss = { editing = null },
            )
            is Block.Image -> ImageEditorDialog(
                initial = b.image,
                contentWidthTwips = page.contentWidthTwips,
                onSave = { controller.replaceBlock(idx, Block.Image(it)); editing = null },
                onRemove = { controller.removeBlock(idx); editing = null },
                onDismiss = { editing = null },
            )
            else -> editing = null
        }
    }
    if (findOpen) FindDialog(controller, onClose = { findOpen = false })
    if (pageOpen) PageSetupDialog(page, onApply = { page = it; pageOpen = false }, onDismiss = { pageOpen = false })
    counts?.let { (words, chars) ->
        AlertDialog(
            onDismissRequest = { counts = null },
            title = { Text("Word count") },
            text = { Text("$words words\n$chars characters (without spaces)") },
            confirmButton = { TextButton(onClick = { counts = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun FormatBar(c: EditorController, s: ToolbarState) {
    var styleMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var fillMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { styleMenu = true }) { Text(s.style.label) }
                DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                    ParaStyle.entries.forEach { st ->
                        DropdownMenuItem(text = { Text(st.label) }, onClick = { styleMenu = false; c.setStyle(st) })
                    }
                }
            }
            TextButton(onClick = { c.sizeStep(-1) }) { Text("A−") }
            Text(if (s.sizePt % 1f == 0f) s.sizePt.toInt().toString() else s.sizePt.toString(), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { c.sizeStep(1) }) { Text("A+") }
            Toggle(Icons.Default.FormatBold, "Bold", s.bold) { c.toggle(CharKind.BOLD, CharAttr.Bold) }
            Toggle(Icons.Default.FormatItalic, "Italic", s.italic) { c.toggle(CharKind.ITALIC, CharAttr.Italic) }
            Toggle(Icons.Default.FormatUnderlined, "Underline", s.underline) { c.toggle(CharKind.UNDERLINE, CharAttr.Underline) }
            Toggle(Icons.Default.StrikethroughS, "Strikethrough", s.strike) { c.toggle(CharKind.STRIKE, CharAttr.Strike) }
            Toggle(Icons.Default.Superscript, "Superscript", s.sup) { c.toggleVert(true) }
            Toggle(Icons.Default.Subscript, "Subscript", s.sub) { c.toggleVert(false) }
            Box {
                Toggle(Icons.Default.FormatColorText, "Text colour", false) { colorMenu = true }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    Swatches(TEXT_COLORS, "Automatic", { colorMenu = false; c.remove(CharKind.COLOR) }) {
                        colorMenu = false
                        c.apply(CharAttr.Color(it))
                    }
                }
            }
            Box {
                Toggle(Icons.Default.FormatColorFill, "Highlight", false) { fillMenu = true }
                DropdownMenu(expanded = fillMenu, onDismissRequest = { fillMenu = false }) {
                    Swatches(HIGHLIGHTS, "No highlight", { fillMenu = false; c.remove(CharKind.HIGHLIGHT) }) {
                        fillMenu = false
                        c.apply(CharAttr.Highlight(it))
                    }
                }
            }
            Toggle(Icons.Default.FormatAlignLeft, "Align left", s.align == Align.LEFT) { c.setAlign(Align.LEFT) }
            Toggle(Icons.Default.FormatAlignCenter, "Align centre", s.align == Align.CENTER) { c.setAlign(Align.CENTER) }
            Toggle(Icons.Default.FormatAlignRight, "Align right", s.align == Align.RIGHT) { c.setAlign(Align.RIGHT) }
            Toggle(Icons.Default.FormatAlignJustify, "Justify", s.align == Align.JUSTIFY) { c.setAlign(Align.JUSTIFY) }
            Toggle(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet list", s.list == ListKind.BULLET) { c.toggleList(ListKind.BULLET) }
            Toggle(Icons.Default.FormatListNumbered, "Numbered list", s.list == ListKind.NUMBER) { c.toggleList(ListKind.NUMBER) }
            Toggle(Icons.Default.FormatIndentDecrease, "Decrease indent", false) { c.indent(-1) }
            Toggle(Icons.Default.FormatIndentIncrease, "Increase indent", false) { c.indent(1) }
            Toggle(Icons.Default.FormatClear, "Clear formatting", false) { c.clearFormatting() }
        }
    }
}

@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
    ) { Icon(icon, contentDescription = label) }
}

@Composable
private fun Swatches(colors: List<Int>, noneLabel: String, onNone: () -> Unit, onPick: (Int) -> Unit) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { rgb ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Color(0xFF000000.toInt() or rgb))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .clickable { onPick(rgb) },
                )
            }
        }
        TextButton(onClick = onNone) { Text(noneLabel) }
    }
}

@Composable
private fun FindDialog(c: EditorController, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Find and replace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it; info = "" }, label = { Text("Find") }, singleLine = true)
                OutlinedTextField(with, { with = it }, label = { Text("Replace with") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(matchCase, { matchCase = it })
                    Text("Match case")
                }
                if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { info = if (c.find(query, matchCase)) "" else "Not found." }) { Text("Find next") }
                    TextButton(onClick = { info = if (c.replaceCurrent(query, with, matchCase)) "" else "Not found." }) { Text("Replace") }
                    TextButton(onClick = {
                        val n = c.replaceAll(query, with, matchCase)
                        info = if (n == 0) "Not found." else "Replaced $n."
                    }) { Text("All") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSetupDialog(page: PageSetup, onApply: (PageSetup) -> Unit, onDismiss: () -> Unit) {
    var paper by remember { mutableStateOf(PaperSize.entries.firstOrNull { it.widthTwips == page.widthTwips && it.heightTwips == page.heightTwips } ?: PaperSize.A4) }
    var landscape by remember { mutableStateOf(page.landscape) }
    var margin by remember { mutableIntStateOf(page.marginLeftTwips) }
    var header by remember { mutableStateOf(page.headerText) }
    var footer by remember { mutableStateOf(page.footerText) }
    var numbers by remember { mutableStateOf(page.pageNumbers) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page setup") },
        text = {
            Column(
                Modifier.horizontalScrollFreeColumn(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Paper", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PaperSize.entries.forEach { p -> FilterChip(paper == p, { paper = p }, label = { Text(p.label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(landscape, { landscape = it })
                    Text("  Landscape")
                }
                Text("Margins", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Narrow 1.27 cm" to 720, "Normal 2.54 cm" to 1440, "Wide 3.81 cm" to 2160).forEach { (label, v) ->
                        FilterChip(margin == v, { margin = v }, label = { Text(label) })
                    }
                }
                OutlinedTextField(header, { header = it }, label = { Text("Header text") }, singleLine = true)
                OutlinedTextField(footer, { footer = it }, label = { Text("Footer text") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(numbers, { numbers = it })
                    Text("  Page numbers")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    page.copy(
                        widthTwips = paper.widthTwips, heightTwips = paper.heightTwips, landscape = landscape,
                        marginLeftTwips = margin, marginRightTwips = margin, marginTopTwips = margin, marginBottomTwips = margin,
                        headerText = header.trim(), footerText = footer.trim(), pageNumbers = numbers,
                    ),
                )
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Modifier.horizontalScrollFreeColumn(): Modifier = this.then(Modifier.fillMaxWidth())
