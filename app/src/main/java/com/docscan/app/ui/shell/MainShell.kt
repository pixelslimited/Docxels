package com.docscan.app.ui.shell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.docscan.app.ui.theme.Brand
import com.docscan.app.ui.theme.DocxelsTheme
import com.docscan.app.ui.tools.ToolsScreen

enum class Tab { HOME, DOCS, TOOLS, SETTINGS }

/** Everything the three main screens can ask the app to do. */
class Actions(
    val newDoc: () -> Unit,
    val scan: () -> Unit,
    val importMenu: () -> Unit,
    val importPhotos: () -> Unit,
    val importWord: () -> Unit,
    val scanToWord: () -> Unit,
    val wordToPdf: () -> Unit,
    val soon: (String) -> Unit,
    val openWord: (String) -> Unit,
    val pdfTool: (String) -> Unit,
    val pdfToWord: () -> Unit,
)

private enum class Pick { SCAN_TO_WORD, WORD_TO_PDF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    container: AppContainer,
    onScanDoc: (String) -> Unit,
    onOpenScan: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onPdfTool: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(container) } })
    val items by vm.items.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var importSheet by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.cleanUpEmptyDocuments() }
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
            scan = { vm.startScan(onScanDoc) },
            importMenu = { importSheet = true },
            importPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            importWord = { word.launch(arrayOf(Mime.DOCX, "application/octet-stream")) },
            scanToWord = { pick = Pick.SCAN_TO_WORD },
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
        bottomBar = { BottomBar(tab, onSelect = { tab = it }, onScan = actions.scan) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    items = items,
                    actions = actions,
                    onOpen = { if (it.kind == DocKind.SCAN) onOpenScan(it.id) else onOpenWord(it.id) },
                    onSearch = { tab = Tab.DOCS },
                    onAllTools = { tab = Tab.TOOLS },
                    onAllDocs = { tab = Tab.DOCS },
                )
                Tab.DOCS -> DocumentsScreen(
                    items = items,
                    actions = actions,
                    vm = vm,
                    onOpen = { if (it.kind == DocKind.SCAN) onOpenScan(it.id) else onOpenWord(it.id) },
                )
                Tab.TOOLS -> ToolsScreen(actions)
                Tab.SETTINGS -> SettingsScreen(container = container)
            }
        }
    }

    BusyOverlay(visible = busy != null, label = busy ?: "")
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
        val wantKind = if (mode == Pick.SCAN_TO_WORD) DocKind.SCAN else DocKind.DOCX
        val choices = items.filter { it.kind == wantKind }
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text(if (mode == Pick.SCAN_TO_WORD) "Scan to Word" else "Word to PDF") },
            text = {
                if (choices.isEmpty()) {
                    Text(if (mode == Pick.SCAN_TO_WORD) "Scan a document first, then convert it here." else "Write or import a Word document first.")
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
                                    if (mode == Pick.SCAN_TO_WORD) vm.scanToWord(item.id, onOpenWord) else vm.sharePdf(context, item)
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

/** Four tabs with the scan button raised in the middle. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, onScan: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(100.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 12.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem("Home", Icons.Default.Home, Icons.Outlined.Home, selected == Tab.HOME, Modifier.weight(1f)) { onSelect(Tab.HOME) }
                NavItem("Documents", Icons.Default.Folder, Icons.Outlined.Folder, selected == Tab.DOCS, Modifier.weight(1f)) { onSelect(Tab.DOCS) }
                Box(Modifier.weight(1f))
                NavItem("Tools", Icons.Default.GridView, Icons.Outlined.GridView, selected == Tab.TOOLS, Modifier.weight(1f)) { onSelect(Tab.TOOLS) }
                NavItem("Settings", Icons.Default.Settings, Icons.Outlined.Settings, selected == Tab.SETTINGS, Modifier.weight(1f)) { onSelect(Tab.SETTINGS) }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 0.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp)
                .clip(CircleShape)
                .background(DocxelsTheme.brand.gradient)
                .clickable(onClickLabel = "Scan a document", role = Role.Button, onClick = onScan),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.DocumentScanner, contentDescription = "Scan", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun NavItem(label: String, filled: ImageVector, outlined: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(if (selected) filled else outlined, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
