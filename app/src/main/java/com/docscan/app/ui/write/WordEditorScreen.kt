package com.docscan.app.ui.write

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FormatPaint
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
import androidx.compose.material.icons.filled.CropSquare
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
private val SHADES = listOf(0xF2F2F2, 0xFFF2CC, 0xDDEBF7, 0xE2EFDA, 0xFCE4D6, 0xEADCF4)
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
    val controller = remember { EditorController(density, initial.blocks, initial.comments) }
    val state by controller.state.collectAsState()
    var fontOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }
    var paraOpen by remember { mutableStateOf(false) }
    var symbolOpen by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var commentNew by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var navOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf<Triple<String, String, IntArray?>?>(null) }
    var linkActions by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    val recentFonts = remember { mutableStateListOf<String>() }
    var caseMenu by remember { mutableStateOf(false) }
    var painter by remember { mutableStateOf<List<com.docscan.app.ui.write.CharAttr>?>(null) }
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
    var zoom by remember { mutableStateOf(1f) }
    var viewMode by remember { mutableStateOf(runCatching { ViewMode.valueOf(container.ui.editorView) }.getOrDefault(ViewMode.PAGE)) }
    var reading by remember { mutableStateOf(false) }
    var previewDoc by remember { mutableStateOf<WordDoc?>(null) }
    var ribbonTab by remember { mutableIntStateOf(0) }
    val deskColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val snackbar = remember { SnackbarHostState() }
    var tableOpen by remember { mutableStateOf(false) }
    var tableCustom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val block = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = ImageIO.decodeUri(context, uri, 1600) ?: error("unreadable")
                    val out = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    val w = (page.contentWidthTwips * 0.5f).toInt()
                    val h = (w.toLong() * bmp.height / bmp.width.coerceAtLeast(1)).toInt()
                    bmp.recycle()
                    ImageBlock(out.toByteArray(), "jpg", w, h, wrap = com.docscan.app.core.word.ImageWrap.IN_FRONT)
                }.getOrNull()
            }
            if (block == null) snackbar.showSnackbar("Could not read that picture.") else controller.insertBlock(Block.Image(block))
        }
    }

    // Letterhead: one picture, stretched to the paper, repeated behind the text of every page (or just the first).
    var letterheadMode by remember { mutableStateOf("all") }
    fun applyLetterhead(lh: PageSources.Letterhead, mode: String) {
        scope.launch {
            val p0 = page
            // Switching to separate first / later pages: keep what the first page already shows.
            val split = if (p0.differentFirstPage) p0 else p0.copy(
                differentFirstPage = true,
                firstHeaderText = p0.headerText, firstFooterText = p0.footerText, firstPageNumbers = p0.pageNumbers,
                firstHeaderImages = p0.headerImages, firstFooterImages = p0.footerImages,
            )
            val hasFooter = lh.footer.isNotEmpty()
            page = when (mode) {
                "first" -> split.copy(
                    firstHeaderImages = lh.header,
                    headerImages = if (p0.differentFirstPage) split.headerImages else emptyList(),
                    firstFooterImages = if (hasFooter) lh.footer else split.firstFooterImages,
                )
                "rest" -> split.copy(headerImages = lh.header, footerImages = if (hasFooter) lh.footer else split.footerImages)
                else -> p0.copy(
                    headerImages = lh.header,
                    footerImages = if (hasFooter) lh.footer else p0.footerImages,
                    firstHeaderImages = if (p0.differentFirstPage) lh.header else p0.firstHeaderImages,
                    firstFooterImages = if (p0.differentFirstPage && hasFooter) lh.footer else p0.firstFooterImages,
                )
            }
            snackbar.showSnackbar(
                when (mode) {
                    "first" -> "Letterhead added to the first page only."
                    "rest" -> "Continuous letterhead added to the pages after the first."
                    else -> "Letterhead added to every page."
                },
            )
        }
    }
    val letterheadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mode = letterheadMode
        val setup = page
        scope.launch {
            val lh = PageSources.letterhead(context, uri, setup)
            if (lh == null) { snackbar.showSnackbar("Could not find a letterhead in that file."); return@launch }
            applyLetterhead(lh, mode)
            // Keep it in Settings > Letterheads too, so it can be used again in any document.
            runCatching {
                val bmp = PageSources.letterheadBitmap(context, uri, 2000)
                if (bmp != null) {
                    val bytes = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(if (bmp.hasAlpha()) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        out.toByteArray()
                    }
                    bmp.recycle()
                    val store = com.docscan.app.store.SavedAssets(context, "letterheads")
                    store.add("Letterhead ${store.list().size + 1}", bytes)
                    snackbar.showSnackbar("Also kept in My letterheads.")
                }
            }
        }
    }
    var savedPicker by remember { mutableStateOf<Boolean?>(null) }
    /** A new signature (drawn or from a picture) is also kept in Settings > Signatures so it can be used again anywhere. */
    fun keepSignature(bytes: ByteArray) {
        runCatching {
            val store = com.docscan.app.store.SavedAssets(context, "signatures")
            store.add("Signature ${store.list().size + 1}", bytes)
        }
    }
    fun placeSignature(block: ImageBlock, keep: Boolean = false) {
        val found = controller.insertSignature(block, page.contentWidthTwips)
        if (keep) keepSignature(block.bytes)
        scope.launch {
            val base = if (found) "Signature placed on the signing line." else "No signing line found. Drag the signature where you want it."
            snackbar.showSnackbar(if (keep) "$base Also kept in My signatures." else base)
        }
    }
    var signSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = PageSources.signatureBitmap(context, uri, 1600)
            if (bmp == null) snackbar.showSnackbar("Could not read a picture from that file.") else signSource = bmp
        }
    }
    var signPad by remember { mutableStateOf(false) }

    fun saveNow() = vm.save(controller.toDoc(title, page))
    val saveAs = com.docscan.app.export.SaveAs.rememberSaveAs { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    fun saveAsFile(make: suspend (WordDoc) -> java.io.File) {
        val d = controller.toDoc(title, page)
        scope.launch {
            runCatching { make(d) }.onSuccess { saveAs(listOf(it)) }.onFailure { snackbar.showSnackbar("Could not create the file.") }
        }
    }

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
                            DropdownMenuItem(text = { Text("Page preview") }, onClick = { menu = false; previewDoc = controller.toDoc(title, page) })
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
                            DropdownMenuItem(text = { Text("Save") }, onClick = { menu = false; saveNow(); scope.launch { snackbar.showSnackbar("Saved.") } })
                            DropdownMenuItem(text = { Text("Save a copy in Docxels") }, onClick = {
                                menu = false
                                val d = controller.toDoc(title, page)
                                scope.launch { runCatching { vm.saveCopy(d) }.onSuccess { snackbar.showSnackbar("Copy saved as \"${d.title} copy\".") }.onFailure { snackbar.showSnackbar("Could not save a copy.") } }
                            })
                            DropdownMenuItem(text = { Text("Save PDF in Docxels") }, onClick = {
                                menu = false
                                val d = controller.toDoc(title, page)
                                scope.launch { snackbar.showSnackbar(if (vm.savePdfToLibrary(d, context)) "PDF saved in Documents." else "Could not save the PDF.") }
                            })
                            DropdownMenuItem(text = { Text("Save as .docx…") }, onClick = { menu = false; saveAsFile { vm.docxFile(it, context) } })
                            DropdownMenuItem(text = { Text("Save as PDF…") }, onClick = { menu = false; saveAsFile { vm.pdfFile(it, context) } })
                            DropdownMenuItem(text = { Text("Save as text…") }, onClick = { menu = false; saveAsFile { vm.txtFile(it, context) } })
                            DropdownMenuItem(text = { Text("Save as RTF…") }, onClick = { menu = false; saveAsFile { vm.rtfFile(it, context) } })
                            DropdownMenuItem(
                                text = { Text("Print") },
                                onClick = {
                                    menu = false
                                    val d = controller.toDoc(title, page)
                                    scope.launch {
                                        runCatching { com.docscan.app.export.PrintHelper.printPdf(context, vm.pdfFile(d, context), title) }
                                            .onFailure { snackbar.showSnackbar("Could not start printing.") }
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
                            DropdownMenuItem(
                                text = { Text("Share as text (.txt)") },
                                onClick = {
                                    menu = false
                                    val d = controller.toDoc(title, page)
                                    scope.launch {
                                        runCatching { ShareHelper.share(context, listOf(vm.txtFile(d, context)), Mime.TXT) }
                                            .onFailure { snackbar.showSnackbar("Could not share the document.") }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share as RTF (no pictures)") },
                                onClick = {
                                    menu = false
                                    val d = controller.toDoc(title, page)
                                    scope.launch {
                                        runCatching { ShareHelper.share(context, listOf(vm.rtfFile(d, context)), Mime.RTF) }
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
                        controller.setChipTapListener { i -> if (!controller.isReadOnly()) editing = i }
                        controller.setLinkTapListener { a, b, u -> linkActions = Triple(a, b, u) }
                        host.onWidth = { w -> controller.setAvailableWidth(w) }
                        host.onPinch = { z -> zoom = z }
                    }
                },
                update = { host ->
                    val d = host.resources.displayMetrics.density
                    val gutter = when (viewMode) { ViewMode.PAGE -> 12f; ViewMode.TEXT -> 8f; ViewMode.MOBILE -> 0f }
                    host.setDesk(deskColor, (gutter * d).toInt(), (gutter * d).toInt())
                    val z = if (viewMode == ViewMode.MOBILE) 1f else zoom
                    host.pinchEnabled = viewMode != ViewMode.MOBILE
                    host.setZoom(z)
                    controller.setZoom(z)
                    controller.setViewConfig(viewMode, page)
                    controller.setReadOnly(reading)
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            if (reading) Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { statsOpen = true }.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (reading) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Read mode", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.FilledTonalButton(onClick = { reading = false }) { Text("Edit") }
                }
            } else Ribbon(
                c = controller,
                s = state,
                status = status,
                onStatus = { statsOpen = true },
                tab = ribbonTab,
                onTab = { ribbonTab = it },
                viewMode = viewMode,
                onViewMode = { viewMode = it; container.ui.editorView = it.name },
                zoom = zoom,
                onZoom = { zoom = when { zoom < 1.25f -> 1.5f; zoom < 1.75f -> 2f; zoom < 2.5f -> 3f; else -> 1f } },
                onRead = { reading = true },
                onPreview = { previewDoc = controller.toDoc(title, page) },
                onLetterhead = { mode ->
                    if (mode == "saved") savedPicker = false
                    else if (mode.startsWith("remove")) {
                        page = when (mode) {
                            "remove_first" -> page.copy(firstHeaderImages = emptyList())
                            "remove_rest" -> page.copy(headerImages = emptyList())
                            else -> page.copy(headerImages = emptyList(), firstHeaderImages = emptyList())
                        }
                    } else {
                        letterheadMode = mode
                        letterheadPicker.launch(PageSources.MIMES)
                    }
                },
                onSignature = { mode ->
                    if (mode == "draw") signPad = true
                    else if (mode == "saved") savedPicker = true
                    else signaturePicker.launch(PageSources.MIMES)
                },
                onTable = { tableOpen = true },
                onPicture = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onShape = { preset ->
                    val w = (page.contentWidthTwips * 0.45f).toInt()
                    controller.insertBlock(Block.Shape(com.docscan.app.core.word.ShapeBlock(preset = preset, widthTwips = w, heightTwips = if (preset == "line") 0 else w / 2, wrap = com.docscan.app.core.word.ImageWrap.IN_FRONT)))
                },
                onPageBreak = { controller.insertPageBreak() },
                onPageSetup = { pageOpen = true },
                onFind = { findOpen = true },
                onCount = { statsOpen = true },
                onFont = { fontOpen = true },
                onSize = { sizeOpen = true },
                onParagraph = { paraOpen = true },
                onSymbol = { symbolOpen = true },
                onDate = { dateOpen = true },
                onNewComment = {
                    if (controller.selectedText().isBlank()) scope.launch { snackbar.showSnackbar("Select the words you want to comment on first.") } else commentNew = true
                },
                onComments = { commentsOpen = true },
                onToc = {
                    val t = com.docscan.app.core.word.TableOfContents.text(controller.outline())
                    if (t.isEmpty()) scope.launch { snackbar.showSnackbar("Give some lines a Heading style first, then insert the contents.") }
                    else controller.insertText("Contents\n$t\n")
                },
                onLink = { linkOpen = Triple(controller.selectedText(), "", null) },
                onNav = { navOpen = true },
                caseMenu = caseMenu,
                onCaseMenu = { caseMenu = it },
                painting = painter != null,
                onPainter = {
                    val held = painter
                    if (held == null) painter = controller.captureFormat() else { controller.applyFormat(held); painter = null }
                },
            )
        }
    }

    previewDoc?.let { d -> PagePreview(vm, d) { previewDoc = null } }
    savedPicker?.let { isSignature ->
        com.docscan.app.ui.settings.SavedAssetsDialog(
            signature = isSignature,
            onPick = { bytes, mode ->
                savedPicker = null
                scope.launch {
                    val bmp = withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    if (bmp == null) { snackbar.showSnackbar("Could not open that saved picture."); return@launch }
                    if (isSignature) {
                        val tw = 2800
                        val out = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        placeSignature(ImageBlock(out.toByteArray(), "png", tw, (tw.toLong() * bmp.height / bmp.width.coerceAtLeast(1)).toInt().coerceAtLeast(200), Align.LEFT, com.docscan.app.core.word.ImageWrap.IN_FRONT))
                    } else {
                        applyLetterhead(PageSources.Letterhead(listOf(PageSources.fullPage(bmp, page)), emptyList()), mode)
                    }
                }
            },
            onDismiss = { savedPicker = null },
        )
    }
    signSource?.let { src ->
        SignatureImportDialog(
            source = src,
            onInsert = { bytes, w, h ->
                signSource = null
                val tw = 2800
                placeSignature(ImageBlock(bytes, "png", tw, (tw.toLong() * h / w.coerceAtLeast(1)).toInt().coerceAtLeast(200), Align.LEFT, com.docscan.app.core.word.ImageWrap.IN_FRONT), keep = true)
            },
            onDismiss = { signSource = null },
        )
    }
    if (signPad) {
        SignaturePadDialog(
            onDone = { bytes, w, h ->
                signPad = false
                val tw = 2800
                placeSignature(ImageBlock(bytes, "png", tw, (tw.toLong() * h / w.coerceAtLeast(1)).toInt().coerceAtLeast(200), Align.LEFT, com.docscan.app.core.word.ImageWrap.IN_FRONT), keep = true)
            },
            onDismiss = { signPad = false },
        )
    }

    if (renaming) {
        TextInputDialog("Rename document", title, "Rename", onConfirm = {
            title = it.ifBlank { "Untitled" }
            renaming = false
        }, onDismiss = { renaming = false })
    }
    fun insertTable(r: Int, c: Int, h: Boolean) {
        tableOpen = false
        tableCustom = false
        controller.insertBlock(Block.Table(newTable(r, c, h, page.contentWidthTwips)))
        // Open it straight away so the cells can be filled in.
        controller.lastBlockIndex()?.let { editing = it }
    }
    if (tableOpen && !tableCustom) TablePickerDialog(
        onPick = { r, c, h -> insertTable(r, c, h) },
        onCustom = { tableCustom = true },
        onDismiss = { tableOpen = false },
    )
    if (tableOpen && tableCustom) InsertTableDialog(
        onInsert = { r, c, h -> insertTable(r, c, h) },
        onDismiss = { tableOpen = false; tableCustom = false },
    )
    androidx.compose.runtime.LaunchedEffect(editing) { controller.select(editing ?: -1) }
    editing?.let { idx ->
        // The block as it was when the editor opened, so live changes don't reset the editor's own state.
        when (val b = remember(idx) { controller.blockAt(idx) }) {
            is Block.Table -> TableEditorDialog(
                initial = b.table,
                contentWidthTwips = page.contentWidthTwips,
                current = (controller.blockAt(idx) as? Block.Table)?.table ?: b.table,
                onSave = { controller.replaceBlock(idx, Block.Table(it)); editing = null },
                onLive = { controller.replaceBlock(idx, Block.Table(it)) },
                onRemove = { controller.removeBlock(idx); editing = null },
                onDismiss = { editing = null },
            )
            is Block.Image -> ImageEditorDialog(
                initial = b.image,
                current = (controller.blockAt(idx) as? Block.Image)?.image ?: b.image,
                contentWidthTwips = page.contentWidthTwips,
                onSave = { controller.replaceBlock(idx, Block.Image(it)); editing = null },
                onLive = { controller.replaceBlock(idx, Block.Image(it)) },
                onRemove = { controller.removeBlock(idx); editing = null },
                onDismiss = { editing = null },
            )
            is Block.Shape -> ShapeEditorDialog(
                initial = b.shape,
                current = (controller.blockAt(idx) as? Block.Shape)?.shape ?: b.shape,
                contentWidthTwips = page.contentWidthTwips,
                onSave = { controller.replaceBlock(idx, Block.Shape(it)); editing = null },
                onLive = { controller.replaceBlock(idx, Block.Shape(it)) },
                onRemove = { controller.removeBlock(idx); editing = null },
                onDismiss = { editing = null },
            )
            else -> editing = null
        }
    }
    if (vm.compatNotes.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.compatNotes = emptyList() },
            title = { Text("Some content can't be kept yet") },
            text = {
                Text(
                    "This file contains " + vm.compatNotes.joinToString(", ") + ". Docxels can't edit or keep these yet, " +
                        "so they will be missing if you save this copy. Your original file is not changed.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.compatNotes = emptyList() }) { Text("OK") } },
        )
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
    if (commentNew) CommentEditDialog(
        title = "New comment", initial = "", action = "Add",
        onDone = { body -> commentNew = false; controller.addComment("Me", body) },
        onDismiss = { commentNew = false },
    )
    if (commentsOpen) CommentsDialog(controller, onDismiss = { commentsOpen = false })
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Ribbon(
    c: EditorController,
    s: ToolbarState,
    status: String,
    onStatus: () -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    viewMode: ViewMode,
    onViewMode: (ViewMode) -> Unit,
    zoom: Float,
    onZoom: () -> Unit,
    onRead: () -> Unit,
    onPreview: () -> Unit,
    onLetterhead: (String) -> Unit,
    onSignature: (String) -> Unit,
    onTable: () -> Unit,
    onPicture: () -> Unit,
    onShape: (String) -> Unit,
    onPageBreak: () -> Unit,
    onPageSetup: () -> Unit,
    onFind: () -> Unit,
    onCount: () -> Unit,
    onFont: () -> Unit,
    onSize: () -> Unit,
    onParagraph: () -> Unit,
    onSymbol: () -> Unit,
    onDate: () -> Unit,
    onToc: () -> Unit,
    onNewComment: () -> Unit,
    onComments: () -> Unit,
    onLink: () -> Unit,
    onNav: () -> Unit,
    caseMenu: Boolean,
    onCaseMenu: (Boolean) -> Unit,
    painting: Boolean,
    onPainter: () -> Unit,
) {
    var customFor by remember { mutableStateOf(0) }
    var styleMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var fillMenu by remember { mutableStateOf(false) }
    var spacingMenu by remember { mutableStateOf(false) }
    var shadeMenu by remember { mutableStateOf(false) }
    var ulMenu by remember { mutableStateOf(false) }
    var shapeMenu by remember { mutableStateOf(false) }
    var letterMenu by remember { mutableStateOf(false) }
    var signMenu by remember { mutableStateOf(false) }
    val tabs = listOf("Home", "Insert", "Layout", "Review", "View")
    CustomColorDialogHost(customFor, { customFor = 0 }, c)
    var open by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(true) }
    val wide = tab != 0
    // Tool groups: a row of group icons; the picked group's tools show below as icon tiles (no dropdowns).
    var groupPick by remember { mutableStateOf(mapOf<Int, String>()) }
    fun groupsOf(t: Int): List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = when (t) {
        0 -> listOf("Font" to Icons.Default.TextFields, "Paragraph" to Icons.Default.FormatAlignLeft, "Style" to Icons.Default.Title, "Clear" to Icons.Default.FormatClear)
        1 -> listOf("Illustrations" to Icons.Default.Image, "Letterhead and signature" to Icons.Default.Draw, "Text and page" to Icons.Default.Link)
        2 -> listOf("Page" to Icons.Default.Description)
        3 -> listOf("Find" to Icons.Default.Search, "Proofing" to Icons.Default.Numbers, "Comments" to Icons.Default.Menu)
        else -> listOf("Views" to Icons.Default.Description, "Zoom" to Icons.Default.Search, "Reading" to Icons.Default.Menu)
    }
    val pickedGroup = groupPick[tab]?.takeIf { n -> groupsOf(tab).any { it.first == n } } ?: groupsOf(tab).first().first
    var groupShown = true
    // One item, drawn as a small icon in the compact bar or as a labelled tile in the grid.
    @Composable
    fun Btn(
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        glyph: String? = null,
        on: Boolean = false,
        main: Boolean = true,
        close: Boolean = false,
        onClick: () -> Unit,
    ) {
        if (!grid && !main) return
        if (grid && !groupShown) return
        val click = { onClick(); if (close) open = false }
        if (grid) {
            RibbonTile(label, icon, glyph, on, click)
        } else if (glyph != null) {
            TextButton(onClick = click) { Text(glyph, maxLines = 1, modifier = Modifier.widthIn(max = 110.dp)) }
        } else if (wide && icon != null) {
            RibbonButton(icon, label, click)
        } else if (icon != null) {
            Toggle(icon, label, on, click)
        }
    }
    // Marks the start of a group's tools; in the grid only the picked group's tools are drawn.
    @Composable
    fun Group(title: String) {
        groupShown = !grid || title == pickedGroup
    }
    // Closed: no bar at all, just the Docxels mark floating at the corner. Open: the tool panel on its own surface.
    Surface(
        tonalElevation = if (open) 3.dp else 0.dp,
        color = if (open) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = if (open) 14.dp else 4.dp),
    ) {
        Column {
            if (!open) {
                // The page and word count share one centre line with the mark, so they read as a single bottom strip.
                Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).clickable(onClick = onStatus),
                    )
                    Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { open = true; grid = true }, contentAlignment = Alignment.Center) {
                        com.docscan.app.ui.kit.AnimatedMark(44.dp, description = "Show tools")
                    }
                }
            } else {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onStatus).padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, modifier = Modifier.weight(1f)) {
                        tabs.forEachIndexed { i, label ->
                            Tab(selected = tab == i, onClick = { if (tab == i && grid) grid = false else { onTab(i); grid = true } }, text = { Text(label, maxLines = 1) })
                        }
                    }
                    IconButton(onClick = { open = false }, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Close, contentDescription = "Hide tools", modifier = Modifier.size(30.dp)) }
                }
            }
            val items: @Composable () -> Unit = {
                when (tab) {
                    0 -> {
                        Group("Font")
                        Btn("Bold", Icons.Default.FormatBold, on = s.bold) { c.toggle(CharKind.BOLD, CharAttr.Bold) }
                        Btn("Italic", Icons.Default.FormatItalic, on = s.italic) { c.toggle(CharKind.ITALIC, CharAttr.Italic) }
                        Btn("Underline", Icons.Default.FormatUnderlined, on = s.underline) { c.toggle(CharKind.UNDERLINE, CharAttr.Underline) }
                        Btn("Smaller", glyph = "A−") { c.sizeStep(-1) }
                        Btn("Size", glyph = if (s.sizePt % 1f == 0f) s.sizePt.toInt().toString() else s.sizePt.toString(), close = true) { onSize() }
                        Btn("Larger", glyph = "A+") { c.sizeStep(1) }
                        Btn("Font", glyph = s.font ?: "Font", main = false, close = true) { onFont() }
                        Btn("Format painter", Icons.Default.FormatPaint, on = painting, main = false) { onPainter() }
                        Box {
                            Btn("Change case", glyph = "Aa", main = false) { onCaseMenu(true) }
                            DropdownMenu(expanded = caseMenu, onDismissRequest = { onCaseMenu(false) }) {
                                com.docscan.app.core.word.CaseMode.entries.forEach { m ->
                                    DropdownMenuItem(text = { Text(m.label) }, onClick = { onCaseMenu(false); c.changeCase(m) })
                                }
                            }
                        }
                        Box {
                            Btn("Underline style", glyph = "U▾", main = false) { ulMenu = true }
                            DropdownMenu(expanded = ulMenu, onDismissRequest = { ulMenu = false }) {
                                listOf(
                                    "single" to "Single", "double" to "Double", "thick" to "Thick", "dotted" to "Dotted",
                                    "dash" to "Dashed", "wave" to "Wavy", "words" to "Words only",
                                ).forEach { (w, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        ulMenu = false
                                        if (w == "single") c.apply(CharAttr.Underline) else c.apply(CharAttr.UnderlineAs(w))
                                    })
                                }
                                DropdownMenuItem(text = { Text("No underline") }, onClick = { ulMenu = false; c.remove(CharKind.UNDERLINE) })
                            }
                        }
                        Btn("Strikethrough", Icons.Default.StrikethroughS, on = s.strike, main = false) { c.toggle(CharKind.STRIKE, CharAttr.Strike) }
                        Btn("Superscript", Icons.Default.Superscript, on = s.sup, main = false) { c.toggleVert(true) }
                        Btn("Subscript", Icons.Default.Subscript, on = s.sub, main = false) { c.toggleVert(false) }
                        Box {
                            Btn("Text colour", Icons.Default.FormatColorText, main = false) { colorMenu = true }
                            DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                                Swatches(TEXT_COLORS, "Automatic", { colorMenu = false; c.remove(CharKind.COLOR) }) {
                                    colorMenu = false
                                    c.apply(CharAttr.Color(it))
                                }
                                TextButton(onClick = { colorMenu = false; customFor = 1 }) { Text("More colours…") }
                            }
                        }
                        Box {
                            Btn("Highlight", Icons.Default.FormatColorFill, main = false) { fillMenu = true }
                            DropdownMenu(expanded = fillMenu, onDismissRequest = { fillMenu = false }) {
                                Swatches(HIGHLIGHTS, "No highlight", { fillMenu = false; c.remove(CharKind.HIGHLIGHT) }) {
                                    fillMenu = false
                                    c.apply(CharAttr.Highlight(it))
                                }
                                TextButton(onClick = { fillMenu = false; customFor = 2 }) { Text("More colours…") }
                            }
                        }
                        Group("Paragraph")
                        Btn("Bullets", Icons.AutoMirrored.Filled.FormatListBulleted, on = s.list == ListKind.BULLET) { c.toggleList(ListKind.BULLET) }
                        Btn("Numbering", Icons.Default.FormatListNumbered, on = s.list == ListKind.NUMBER) { c.toggleList(ListKind.NUMBER) }
                        Btn("Align left", Icons.Default.FormatAlignLeft, on = s.align == Align.LEFT, main = false) { c.setAlign(Align.LEFT) }
                        Btn("Align centre", Icons.Default.FormatAlignCenter, on = s.align == Align.CENTER, main = false) { c.setAlign(Align.CENTER) }
                        Btn("Align right", Icons.Default.FormatAlignRight, on = s.align == Align.RIGHT, main = false) { c.setAlign(Align.RIGHT) }
                        Btn("Justify", Icons.Default.FormatAlignJustify, on = s.align == Align.JUSTIFY, main = false) { c.setAlign(Align.JUSTIFY) }
                        Btn("Outdent", Icons.Default.FormatIndentDecrease, main = false) { c.indent(-1) }
                        Btn("Indent", Icons.Default.FormatIndentIncrease, main = false) { c.indent(1) }
                        Btn("Space before", Icons.Default.FormatLineSpacing, on = s.spaceBefore, main = false) { c.toggleSpaceBefore() }
                        Btn("Space after", Icons.Default.FormatLineSpacing, on = s.spaceAfter, main = false) { c.toggleSpaceAfter() }
                        Box {
                            Btn("Line spacing", Icons.Default.FormatLineSpacing, main = false) { spacingMenu = true }
                            DropdownMenu(expanded = spacingMenu, onDismissRequest = { spacingMenu = false }) {
                                listOf(null to "Default", 1.0f to "Single", 1.15f to "1.15", 1.5f to "1.5", 2.0f to "Double").forEach { (m, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { spacingMenu = false; c.setLineSpacing(m) })
                                }
                            }
                        }
                        Box {
                            Btn("Shading", Icons.Default.FormatColorFill, main = false) { shadeMenu = true }
                            DropdownMenu(expanded = shadeMenu, onDismissRequest = { shadeMenu = false }) {
                                DropdownMenuItem(text = { Text("No shading") }, onClick = { shadeMenu = false; c.setShading(null) })
                                androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    SHADES.forEach { rgb ->
                                        Box(
                                            Modifier.padding(3.dp).size(28.dp)
                                                .background(androidx.compose.ui.graphics.Color(0xFF000000.toInt() or rgb), androidx.compose.foundation.shape.CircleShape)
                                                .clickable { shadeMenu = false; c.setShading(rgb) },
                                        )
                                    }
                                }
                            }
                        }
                        Btn("Border", Icons.Default.CropSquare, main = false) { c.toggleBorder() }
                        Btn("Paragraph settings", Icons.Default.Menu, main = false, close = true) { onParagraph() }
                        Group("Style")
                        if (!grid) Box {
                            Btn("Style", glyph = s.style.label) { styleMenu = true }
                            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                                ParaStyle.entries.forEach { st ->
                                    DropdownMenuItem(text = { Text(st.label) }, onClick = { styleMenu = false; c.setStyle(st) })
                                }
                            }
                        }
                        ParaStyle.entries.forEach { st -> Btn(st.label, glyph = "Aa", on = s.style == st, main = false) { c.setStyle(st) } }
                        Group("Clear")
                        Btn("Clear formatting", Icons.Default.FormatClear, main = false) { c.clearFormatting() }
                        Btn("Clear colour", Icons.Default.FormatColorText, main = false) { c.remove(CharKind.COLOR) }
                        Btn("Clear highlight", Icons.Default.FormatColorFill, main = false) { c.remove(CharKind.HIGHLIGHT) }
                        Btn("No underline", Icons.Default.FormatUnderlined, main = false) { c.remove(CharKind.UNDERLINE) }
                    }
                    1 -> {
                        Group("Illustrations")
                        Btn("Table", Icons.Default.TableChart, close = true) { onTable() }
                        Btn("Picture", Icons.Default.Image, close = true) { onPicture() }
                        Box {
                            Btn("Shape", Icons.Default.CropSquare) { shapeMenu = true }
                            DropdownMenu(expanded = shapeMenu, onDismissRequest = { shapeMenu = false }) {
                                com.docscan.app.core.word.ShapePresets.INSERTABLE.forEach { (preset, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { shapeMenu = false; onShape(preset) })
                                }
                            }
                        }
                        Group("Letterhead and signature")
                        Box {
                            Btn("Letterhead", Icons.Default.Description) { letterMenu = true }
                            DropdownMenu(expanded = letterMenu, onDismissRequest = { letterMenu = false }) {
                                DropdownMenuItem(text = { Text("My saved letterheads…") }, onClick = { letterMenu = false; open = false; onLetterhead("saved") })
                                DropdownMenuItem(text = { Text("First page only") }, onClick = { letterMenu = false; open = false; onLetterhead("first") })
                                DropdownMenuItem(text = { Text("Continuous letterhead (pages after the first)") }, onClick = { letterMenu = false; open = false; onLetterhead("rest") })
                                DropdownMenuItem(text = { Text("Same on every page") }, onClick = { letterMenu = false; open = false; onLetterhead("all") })
                                DropdownMenuItem(text = { Text("Remove first-page letterhead") }, onClick = { letterMenu = false; onLetterhead("remove_first") })
                                DropdownMenuItem(text = { Text("Remove continuous letterhead") }, onClick = { letterMenu = false; onLetterhead("remove_rest") })
                                DropdownMenuItem(text = { Text("Remove all letterhead") }, onClick = { letterMenu = false; onLetterhead("remove") })
                            }
                        }
                        Box {
                            Btn("Signature", Icons.Default.Draw) { signMenu = true }
                            DropdownMenu(expanded = signMenu, onDismissRequest = { signMenu = false }) {
                                DropdownMenuItem(text = { Text("My saved signatures…") }, onClick = { signMenu = false; open = false; onSignature("saved") })
                                DropdownMenuItem(text = { Text("Draw with finger") }, onClick = { signMenu = false; open = false; onSignature("draw") })
                                DropdownMenuItem(text = { Text("From a picture") }, onClick = { signMenu = false; open = false; onSignature("picture") })
                            }
                        }
                        Group("Text and page")
                        Btn("Page break", Icons.Default.InsertPageBreak, close = true) { onPageBreak() }
                        Btn("Link", Icons.Default.Link, close = true) { onLink() }
                        Btn("Date & time", Icons.Default.Event, close = true) { onDate() }
                        Btn("Contents", Icons.Default.Description, close = true) { onToc() }
                        Btn("Symbol", Icons.Default.Functions, close = true) { onSymbol() }
                    }
                    2 -> {
                        Group("Page")
                        Btn("Page setup", Icons.Default.Description, close = true) { onPageSetup() }
                    }
                    3 -> {
                        Group("Find")
                        Btn("Find and replace", Icons.Default.Search, close = true) { onFind() }
                        Group("Proofing")
                        Btn("Word count", Icons.Default.Numbers, close = true) { onCount() }
                        Group("Comments")
                        Btn("New comment", Icons.Default.Add, close = true) { onNewComment() }
                        Btn("Comments", Icons.Default.Menu, close = true) { onComments() }
                    }
                    else -> {
                        Group("Views")
                        Btn("Page preview", Icons.Default.Description, close = true) { onPreview() }
                        Btn("Navigation", Icons.Default.Menu, close = true) { onNav() }
                        Group("Zoom")
                        if (viewMode != ViewMode.MOBILE) Btn("Zoom ${(zoom * 100).toInt()}%", Icons.Default.Search) { onZoom() }
                        Group("Reading")
                        if (groupShown || !grid) com.docscan.app.ui.kit.FilterPill("Read mode", false, onRead, Modifier.padding(horizontal = 4.dp))
                        if (groupShown || !grid) com.docscan.app.ui.kit.FilterPill("Print layout", viewMode == ViewMode.PAGE, { onViewMode(ViewMode.PAGE) }, Modifier.padding(horizontal = 4.dp))
                        if (groupShown || !grid) com.docscan.app.ui.kit.FilterPill("Text width", viewMode == ViewMode.TEXT, { onViewMode(ViewMode.TEXT) }, Modifier.padding(horizontal = 4.dp))
                        if (groupShown || !grid) com.docscan.app.ui.kit.FilterPill("Mobile", viewMode == ViewMode.MOBILE, { onViewMode(ViewMode.MOBILE) }, Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            if (open && grid) {
                // Group names are plain underlined text, so they read as headings and not as more tool buttons.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    groupsOf(tab).forEach { (name, _) ->
                        val on = name == pickedGroup
                        Column(
                            Modifier.clickable { groupPick = groupPick + (tab to name) }.padding(top = 8.dp, bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                name.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                Modifier.padding(top = 4.dp).height(2.dp).width(if (on) 28.dp else 0.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { items() }
            }
        }
    }
}

@Composable
private fun RibbonTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, glyph: String?, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(76.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            else Text(glyph.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun CustomColorDialogHost(customFor: Int, onDone: () -> Unit, c: EditorController) {
    if (customFor == 0) return
    ColorHexDialog(
        title = if (customFor == 1) "Text colour" else "Highlight colour",
        onPick = { rgb ->
            onDone()
            if (customFor == 1) c.apply(CharAttr.Color(rgb)) else c.apply(CharAttr.Highlight(rgb))
        },
        onDismiss = onDone,
    )
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
    fun cm(twips: Int) = String.format(java.util.Locale.US, "%.2f", twips / 567f)
    var mTop by remember { mutableStateOf(cm(page.marginTopTwips)) }
    var mBottom by remember { mutableStateOf(cm(page.marginBottomTwips)) }
    var mLeft by remember { mutableStateOf(cm(page.marginLeftTwips)) }
    var mRight by remember { mutableStateOf(cm(page.marginRightTwips)) }
    var dropPics by remember { mutableStateOf(false) }
    val picCount = page.headerImages.size + page.footerImages.size + page.firstHeaderImages.size + page.firstFooterImages.size
    var header by remember { mutableStateOf(page.headerText) }
    var footer by remember { mutableStateOf(page.footerText) }
    var numbers by remember { mutableStateOf(page.pageNumbers) }
    var diffFirst by remember { mutableStateOf(page.differentFirstPage) }
    var header1 by remember { mutableStateOf(page.firstHeaderText) }
    var oddEven by remember { mutableStateOf(page.differentOddEven) }
    var headerEven by remember { mutableStateOf(page.evenHeaderText) }
    var footerEven by remember { mutableStateOf(page.evenFooterText) }
    var footer1 by remember { mutableStateOf(page.firstFooterText) }
    var numbers1 by remember { mutableStateOf(page.firstPageNumbers) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page setup") },
        text = {
            Column(
                Modifier.horizontalScrollFreeColumn().verticalScroll(rememberScrollState()),
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
                Text("Margins (cm)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        "Normal" to listOf(1440, 1440, 1440, 1440), "Narrow" to listOf(720, 720, 720, 720),
                        "Moderate" to listOf(1440, 1440, 1080, 1080), "Wide" to listOf(1440, 1440, 2880, 2880),
                    ).forEach { (label, v) ->
                        FilterChip(false, { mTop = cm(v[0]); mBottom = cm(v[1]); mLeft = cm(v[2]); mRight = cm(v[3]) }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mTop, { mTop = it.take(5) }, singleLine = true, label = { Text("Top") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(mBottom, { mBottom = it.take(5) }, singleLine = true, label = { Text("Bottom") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mLeft, { mLeft = it.take(5) }, singleLine = true, label = { Text("Left") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(mRight, { mRight = it.take(5) }, singleLine = true, label = { Text("Right") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(header, { header = it }, label = { Text("Header text") }, singleLine = true)
                OutlinedTextField(footer, { footer = it }, label = { Text("Footer text") }, singleLine = true)
                if (picCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (dropPics) "Letterhead pictures will be removed" else "Letterhead: $picCount picture(s) in the header/footer, kept on every page",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { dropPics = !dropPics }) { Text(if (dropPics) "Keep" else "Remove") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(numbers, { numbers = it })
                    Text("  Page numbers")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(diffFirst, { diffFirst = it })
                    Text("  Different first page")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(oddEven, { oddEven = it })
                    Text("  Different odd and even pages")
                }
                if (oddEven) {
                    Text("The header and footer above are for odd pages (1, 3, 5…).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(headerEven, { headerEven = it }, label = { Text("Even page header") }, singleLine = true)
                    OutlinedTextField(footerEven, { footerEven = it }, label = { Text("Even page footer") }, singleLine = true)
                }
                if (diffFirst) {
                    OutlinedTextField(header1, { header1 = it }, label = { Text("First page header") }, singleLine = true)
                    OutlinedTextField(footer1, { footer1 = it }, label = { Text("First page footer") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(numbers1, { numbers1 = it })
                        Text("  Page number on first page")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    page.copy(
                        widthTwips = paper.widthTwips, heightTwips = paper.heightTwips, landscape = landscape,
                        marginLeftTwips = tw(mLeft, page.marginLeftTwips), marginRightTwips = tw(mRight, page.marginRightTwips),
                        marginTopTwips = tw(mTop, page.marginTopTwips), marginBottomTwips = tw(mBottom, page.marginBottomTwips),
                        headerText = header.trim(), footerText = footer.trim(), pageNumbers = numbers,
                        differentFirstPage = diffFirst, firstHeaderText = header1.trim(), firstFooterText = footer1.trim(), firstPageNumbers = numbers1,
                        differentOddEven = oddEven, evenHeaderText = headerEven.trim(), evenFooterText = footerEven.trim(),
                    ).let { if (dropPics) it.copy(headerImages = emptyList(), footerImages = emptyList(), firstHeaderImages = emptyList(), firstFooterImages = emptyList()) else it },
                )
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Centimetres typed by the user to twips; keeps the old value if the text is not a sensible number. Margins stay between 0 and 10 cm. */
private fun tw(text: String, fallback: Int): Int {
    val v = text.replace(',', '.').toFloatOrNull() ?: return fallback
    return (v.coerceIn(0f, 10f) * 567f).toInt()
}

@Composable
private fun Modifier.horizontalScrollFreeColumn(): Modifier = this.then(Modifier.fillMaxWidth())


@Composable
private fun CommentEditDialog(title: String, initial: String, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(text, { text = it.take(2000) }, label = { Text("Comment") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Every comment in the document: tap Go to to jump to the text, edit it, or delete it. */
@Composable
private fun CommentsDialog(controller: EditorController, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(controller.comments()) }
    var editing by remember { mutableStateOf<com.docscan.app.core.word.DocComment?>(null) }
    editing?.let { c ->
        CommentEditDialog("Edit comment", c.text, "Save",
            onDone = { controller.editComment(c.id, it); list = controller.comments(); editing = null },
            onDismiss = { editing = null })
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments (${list.size})") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (list.isEmpty()) Text("No comments yet. Select some words, then choose New comment.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { c ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(c.author, style = MaterialTheme.typography.labelLarge)
                        Text(c.text, style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { controller.showComment(c.id); onDismiss() }) { Text("Go to") }
                            TextButton(onClick = { editing = c }) { Text("Edit") }
                            TextButton(onClick = { controller.deleteComment(c.id); list = controller.comments() }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
