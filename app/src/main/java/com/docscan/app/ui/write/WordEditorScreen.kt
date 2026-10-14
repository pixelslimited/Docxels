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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.runtime.mutableStateListOf
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
    var fontOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }
    var paraOpen by remember { mutableStateOf(false) }
    var symbolOpen by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var navOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf<Triple<String, String, IntArray?>?>(null) }
    var linkActions by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    val recentFonts = remember { mutableStateListOf<String>() }
    var caseMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(state) {
        delay(300)
        val st = controller.stats()
        val pg = controller.pageInfo()
        status = (if (pg != null) "Page ${pg.first} of ${pg.second}  ·  " else "") + "%,d words".format(st.words)
    }
    var title by remember { mutableStateOf(initial.title) }
    var page by remember { mutableStateOf(initial.page) }
    var tick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var pageOpen by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val container = com.docscan.app.LocalContainer.current
    var viewMode by remember { mutableStateOf(runCatching { ViewMode.valueOf(container.ui.editorView) }.getOrDefault(ViewMode.PAGE)) }
    var ribbonTab by remember { mutableIntStateOf(0) }
    val deskColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val snackbar = remember { SnackbarHostState() }
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
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text("Share as PDF") },
                                onClick = {
                                    menu = false
                                    val d = controller.toDoc(title, page)
                                    scope.launch {
                                        runCatching { ShareHelper.share(context, listOf(vm.pdfFile(d, context)), Mime.PDF) }
                                            .onFailure { snackbar.showSnackbar("Could not create the PDF.") }
                                    }
                                },
                            )
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
                factory = { ctx ->
                    PageHost(ctx).also { host ->
                        controller.attach(host.edit)
                        controller.setChipTapListener { i -> editing = i }
                        controller.setLinkTapListener { a, b, u -> linkActions = Triple(a, b, u) }
                        host.onWidth = { w -> controller.setAvailableWidth(w) }
                    }
                },
                update = { host ->
                    val d = host.resources.displayMetrics.density
                    val gutter = when (viewMode) { ViewMode.PAGE -> 12f; ViewMode.TEXT -> 8f; ViewMode.MOBILE -> 0f }
                    host.setDesk(deskColor, (gutter * d).toInt(), (gutter * d).toInt())
                    controller.setViewConfig(viewMode, page)
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { statsOpen = true }.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Ribbon(
                c = controller,
                s = state,
                tab = ribbonTab,
                onTab = { ribbonTab = it },
                viewMode = viewMode,
                onViewMode = { viewMode = it; container.ui.editorView = it.name },
                onTable = { tableOpen = true },
                onPicture = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPageBreak = { controller.insertPageBreak() },
                onPageSetup = { pageOpen = true },
                onFind = { findOpen = true },
                onCount = { statsOpen = true },
                onFont = { fontOpen = true },
                onSize = { sizeOpen = true },
                onParagraph = { paraOpen = true },
                onSymbol = { symbolOpen = true },
                onDate = { dateOpen = true },
                onLink = { linkOpen = Triple(controller.selectedText(), "", null) },
                onNav = { navOpen = true },
                caseMenu = caseMenu,
                onCaseMenu = { caseMenu = it },
            )
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
    if (fontOpen) FontPickerDialog(
        current = state.font, recent = recentFonts.toList(),
        onPick = { f ->
            fontOpen = false
            controller.setFont(f)
            if (f != null) { recentFonts.remove(f); recentFonts.add(0, f); while (recentFonts.size > 5) recentFonts.removeAt(5) }
        },
        onDismiss = { fontOpen = false },
    )
    if (sizeOpen) SizeDialog(state.sizePt, onPick = { sizeOpen = false; controller.setSize(it) }, onDismiss = { sizeOpen = false })
    if (paraOpen) ParagraphDialog(
        initial = remember { controller.currentParagraph() },
        onApply = { b, a, l, left, first -> paraOpen = false; controller.setParagraphLayout(b, a, l, left, first) },
        onDismiss = { paraOpen = false },
    )
    linkOpen?.let { (txt, url, range) ->
        LinkDialog(
            initialText = txt, initialUrl = url, editing = range != null,
            onApply = { t, u ->
                linkOpen = null
                if (range != null) controller.applyLink(t, u, range[0], range[1]) else controller.applyLink(t, u)
            },
            onDismiss = { linkOpen = null },
        )
    }
    linkActions?.let { (a, b, url) ->
        LinkActionsDialog(
            url = url,
            onOpen = {
                linkActions = null
                val safe = com.docscan.app.core.word.LinkSafety.normalize(url)
                if (safe != null) runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(safe)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onEdit = { linkActions = null; linkOpen = Triple(controller.textBetween(a, b), url, intArrayOf(a, b)) },
            onCopy = {
                linkActions = null
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("link", url))
            },
            onRemove = { linkActions = null; controller.removeLink(a, b) },
            onDismiss = { linkActions = null },
        )
    }
    if (symbolOpen) SymbolDialog(onInsert = { symbolOpen = false; controller.insertText(it) }, onDismiss = { symbolOpen = false })
    if (dateOpen) DateTimeDialog(onInsert = { dateOpen = false; controller.insertText(it) }, onDismiss = { dateOpen = false })
    if (navOpen) NavigationDialog(remember { controller.outline() }, onGo = { navOpen = false; controller.goTo(it) }, onDismiss = { navOpen = false })
    if (statsOpen) WordCountDialog(remember { controller.stats() }, controller.pageInfo()?.second, onDismiss = { statsOpen = false })
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
private fun Ribbon(
    c: EditorController,
    s: ToolbarState,
    tab: Int,
    onTab: (Int) -> Unit,
    viewMode: ViewMode,
    onViewMode: (ViewMode) -> Unit,
    onTable: () -> Unit,
    onPicture: () -> Unit,
    onPageBreak: () -> Unit,
    onPageSetup: () -> Unit,
    onFind: () -> Unit,
    onCount: () -> Unit,
    onFont: () -> Unit,
    onSize: () -> Unit,
    onParagraph: () -> Unit,
    onSymbol: () -> Unit,
    onDate: () -> Unit,
    onLink: () -> Unit,
    onNav: () -> Unit,
    caseMenu: Boolean,
    onCaseMenu: (Boolean) -> Unit,
) {
    var styleMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var fillMenu by remember { mutableStateOf(false) }
    var spacingMenu by remember { mutableStateOf(false) }
    val tabs = listOf("Home", "Insert", "Layout", "Review", "View")
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(label, maxLines = 1) })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (tab) {
                    0 -> {
            Box {
                TextButton(onClick = { styleMenu = true }) { Text(s.style.label) }
                DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                    ParaStyle.entries.forEach { st ->
                        DropdownMenuItem(text = { Text(st.label) }, onClick = { styleMenu = false; c.setStyle(st) })
                    }
                }
            }
            TextButton(onClick = onFont) { Text(s.font ?: "Font", maxLines = 1, modifier = Modifier.widthIn(max = 110.dp)) }
            TextButton(onClick = { c.sizeStep(-1) }) { Text("A−") }
            TextButton(onClick = onSize) { Text(if (s.sizePt % 1f == 0f) s.sizePt.toInt().toString() else s.sizePt.toString()) }
            TextButton(onClick = { c.sizeStep(1) }) { Text("A+") }
            Box {
                TextButton(onClick = { onCaseMenu(true) }) { Text("Aa") }
                DropdownMenu(expanded = caseMenu, onDismissRequest = { onCaseMenu(false) }) {
                    com.docscan.app.core.word.CaseMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { onCaseMenu(false); c.changeCase(m) })
                    }
                }
            }
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
                    1 -> {
                        RibbonButton(Icons.Default.TableChart, "Table", onTable)
                        RibbonButton(Icons.Default.Image, "Picture", onPicture)
                        RibbonButton(Icons.Default.InsertPageBreak, "Page break", onPageBreak)
                        RibbonButton(Icons.Default.Link, "Link", onLink)
                        RibbonButton(Icons.Default.Event, "Date & time", onDate)
                        RibbonButton(Icons.Default.Functions, "Symbol", onSymbol)
                    }
                    2 -> {
                        RibbonButton(Icons.Default.Description, "Page setup", onPageSetup)
                        RibbonButton(Icons.Default.FormatAlignLeft, "Paragraph", onParagraph)
                        Box {
                            RibbonButton(Icons.Default.FormatLineSpacing, "Line spacing") { spacingMenu = true }
                            DropdownMenu(expanded = spacingMenu, onDismissRequest = { spacingMenu = false }) {
                                listOf(null to "Default", 1.0f to "Single", 1.15f to "1.15", 1.5f to "1.5", 2.0f to "Double").forEach { (m, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { spacingMenu = false; c.setLineSpacing(m) })
                                }
                            }
                        }
                        RibbonButton(Icons.Default.FormatIndentDecrease, "Outdent") { c.indent(-1) }
                        RibbonButton(Icons.Default.FormatIndentIncrease, "Indent") { c.indent(1) }
                    }
                    3 -> {
                        RibbonButton(Icons.Default.Search, "Find and replace", onFind)
                        RibbonButton(Icons.Default.Numbers, "Word count", onCount)
                    }
                    else -> {
                        RibbonButton(Icons.Default.Menu, "Navigation", onNav)
                        com.docscan.app.ui.kit.FilterPill("Print layout", viewMode == ViewMode.PAGE, { onViewMode(ViewMode.PAGE) }, Modifier.padding(horizontal = 4.dp))
                        com.docscan.app.ui.kit.FilterPill("Text width", viewMode == ViewMode.TEXT, { onViewMode(ViewMode.TEXT) }, Modifier.padding(horizontal = 4.dp))
                        com.docscan.app.ui.kit.FilterPill("Mobile", viewMode == ViewMode.MOBILE, { onViewMode(ViewMode.MOBILE) }, Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RibbonButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
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
