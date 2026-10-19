package com.docscan.app.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.docscan.app.AppContainer
import com.docscan.app.data.DocKind
import com.docscan.app.export.Mime
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.home.HomeScreen
import com.docscan.app.ui.library.DocumentsScreen
import com.docscan.app.ui.settings.SettingsScreen
import com.docscan.app.ui.theme.DocxelsTheme
import com.docscan.app.ui.tools.ToolsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class Tab { HOME, DOCS, TOOLS, SETTINGS }

/** Everything the three main screens can ask the app to do. */
class Actions(
    val newDoc: () -> Unit,
    val templates: () -> Unit,
    val newFromTemplate: (String) -> Unit,
    val newInvoice: () -> Unit,
    val scan: () -> Unit,
    val importMenu: () -> Unit,
    val importPhotos: () -> Unit,
    val importWord: () -> Unit,
    val scanToWord: () -> Unit,
    val scanToExcel: () -> Unit,
    val idCard: () -> Unit,
    val wordToPdf: () -> Unit,
    val soon: (String) -> Unit,
    val openWord: (String) -> Unit,
    val pdfTool: (String) -> Unit,
    val pdfToWord: () -> Unit,
)

private enum class Pick { SCAN_TO_WORD, WORD_TO_PDF, SCAN_TO_EXCEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    container: AppContainer,
    onScanDoc: (String) -> Unit,
    onOpenScan: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onPdfTool: (String) -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(container) } })
    val items by vm.items.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var importSheet by remember { mutableStateOf(false) }
    var templateSheet by remember { mutableStateOf(false) }
    var invoiceMaker by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    val snackbar = remember { SnackbarHostState() }
    // Back from Documents, Tools or Settings goes to Home first; only Home leaves the app.
    androidx.activity.compose.BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    LaunchedEffect(Unit) { vm.cleanUpEmptyDocuments(); vm.emptyOldTrash(); vm.indexContent() }
    val trash by vm.trash.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        vm.importImages(context, uris, onOpenScan)
    }
    val word = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWord(context, uri, onOpenWord)
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pdfToWord(context, uri, onOpenWord)
    }

    val actions = remember(vm) {
        Actions(
            newDoc = { vm.newWord(onOpenWord) },
            templates = { templateSheet = true },
            newFromTemplate = { id -> if (id == "invoice") invoiceMaker = true else vm.newFromTemplate(id, onOpenWord) },
            newInvoice = { invoiceMaker = true },
            scan = { vm.startScan(onScanDoc) },
            importMenu = { importSheet = true },
            importPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            importWord = { word.launch(arrayOf(Mime.DOCX, "application/octet-stream")) },
            scanToWord = { pick = Pick.SCAN_TO_WORD },
            scanToExcel = { pick = Pick.SCAN_TO_EXCEL },
            idCard = { vm.say("Scan the front, then the back. When you export, switch on “ID card layout” to get both sides on one page."); vm.startScan(onScanDoc) },
            wordToPdf = { pick = Pick.WORD_TO_PDF },
            soon = { name -> vm.say("$name is coming in a later update.") },
            openWord = onOpenWord,
            pdfTool = onPdfTool,
            pdfToWord = { pdfPicker.launch(arrayOf("application/pdf")) },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomBar(tab, onSelect = { tab = it }) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    items = items,
                    actions = actions,
                    onOpen = { when (it.kind) { DocKind.SCAN -> onOpenScan(it.id); DocKind.PDF -> onOpenPdf(it.id); else -> onOpenWord(it.id) } },
                    onSearch = { tab = Tab.DOCS },
                    onAllTools = { tab = Tab.TOOLS },
                    onAllDocs = { tab = Tab.DOCS },
                )
                Tab.DOCS -> DocumentsScreen(
                    items = items,
                    trash = trash,
                    actions = actions,
                    vm = vm,
                    onOpen = { when (it.kind) { DocKind.SCAN -> onOpenScan(it.id); DocKind.PDF -> onOpenPdf(it.id); else -> onOpenWord(it.id) } },
                )
                Tab.TOOLS -> ToolsScreen(actions)
                Tab.SETTINGS -> SettingsScreen(container = container)
            }
        }
    }

    BusyOverlay(visible = busy != null, label = busy ?: "")
    }

    if (templateSheet) {
        ModalBottomSheet(onDismissRequest = { templateSheet = false }) {
            Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text("Templates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                com.docscan.app.core.word.Templates.all.forEach { t ->
                    SheetRow(Icons.Default.NoteAdd, t.label, t.blurb) {
                        templateSheet = false
                        actions.newFromTemplate(t.id)
                    }
                }
            }
        }
    }

    if (invoiceMaker) {
        com.docscan.app.ui.shell.InvoiceMaker(
            onCreate = { data, style -> invoiceMaker = false; vm.createInvoice(data, style, onOpenWord) },
            onDismiss = { invoiceMaker = false },
        )
    }

    if (importSheet) {
        ModalBottomSheet(onDismissRequest = { importSheet = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Import", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                SheetRow(Icons.Default.PhotoLibrary, "Photos from gallery", "Becomes a scan you can clean up and export") {
                    importSheet = false
                    actions.importPhotos()
                }
                SheetRow(Icons.Default.FileOpen, "Word file (.docx)", "Opens in the editor") {
                    importSheet = false
                    actions.importWord()
                }
            }
        }
    }

    pick?.let { mode ->
        val wantKind = if (mode == Pick.SCAN_TO_WORD || mode == Pick.SCAN_TO_EXCEL) DocKind.SCAN else DocKind.DOCX
        val choices = items.filter { it.kind == wantKind }
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text(when (mode) { Pick.SCAN_TO_WORD -> "Scan to Word"; Pick.SCAN_TO_EXCEL -> "Scan to Excel"; else -> "Word to PDF" }) },
            text = {
                if (choices.isEmpty()) {
                    Text(if (mode != Pick.WORD_TO_PDF) "Scan a document first, then convert it here." else "Write or import a Word document first.")
                } else {
                    Column {
                        Text("Choose a document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        choices.take(8).forEach { item ->
                            ListItem(
                                headlineContent = { Text(item.title, maxLines = 1) },
                                supportingContent = { Text(item.detail) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    pick = null
                                    when (mode) { Pick.SCAN_TO_WORD -> vm.scanToWord(item.id, onOpenWord); Pick.SCAN_TO_EXCEL -> vm.scanToExcel(context, item.id); else -> vm.sharePdf(context, item) }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Floating dock with four tabs. A coloured "bead" rides over the active tab: tap a tab, or drag the bead along the bar.
 */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    class Slot(val tab: Tab, val label: String, val filled: ImageVector, val outlined: ImageVector)
    val slots = listOf(
        Slot(Tab.HOME, "Home", Icons.Default.Home, Icons.Outlined.Home),
        Slot(Tab.DOCS, "Documents", Icons.Default.Folder, Icons.Outlined.Folder),
        Slot(Tab.TOOLS, "Tools", Icons.Default.GridView, Icons.Outlined.GridView),
        Slot(Tab.SETTINGS, "Settings", Icons.Default.Settings, Icons.Outlined.Settings),
    )
    val selIndex = slots.indexOfFirst { it.tab == selected }.coerceAtLeast(0)
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 8.dp).height(92.dp),
    ) {
        val slotW = maxWidth / slots.size
        val beadSize = 54.dp
        var dragX by remember { mutableStateOf<Float?>(null) }
        val slotPx = with(density) { slotW.toPx() }
        val restX = (selIndex + 0.5f) * slotPx
        val beadX by androidx.compose.animation.core.animateFloatAsState(
            dragX ?: restX, androidx.compose.animation.core.spring(stiffness = 500f), label = "bead",
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(66.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                slots.forEachIndexed { i, slot ->
                    val active = i == selIndex
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab, onClickLabel = slot.label) { onSelect(slot.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // The active icon lives in the bead; keep its space so nothing jumps.
                        Icon(slot.outlined, contentDescription = null, modifier = Modifier.size(24.dp).alpha(if (active) 0f else 1f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(slot.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
                    }
                }
            }
        }
        // The bead sits half above the bar; its rim is the page colour so it looks cut into the dock.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { androidx.compose.ui.unit.IntOffset((beadX - beadSize.toPx() / 2).toInt(), 0) }
                .size(beadSize + 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(4.dp)
                .clip(CircleShape)
                .background(DocxelsTheme.brand.gradient)
                .pointerInput(slotPx, selIndex) {
                    var x = restX
                    detectHorizontalDragGestures(
                        onDragStart = { x = restX; dragX = x },
                        onDragEnd = {
                            onSelect(slots[(x / slotPx).toInt().coerceIn(0, slots.lastIndex)].tab)
                            dragX = null
                        },
                        onDragCancel = { dragX = null },
                    ) { _, delta -> x = (x + delta).coerceIn(slotPx * 0.5f, slotPx * (slots.size - 0.5f)); dragX = x }
                },
            contentAlignment = Alignment.Center,
        ) {
            val sl = slots[selIndex]
            Icon(sl.filled, contentDescription = sl.label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
