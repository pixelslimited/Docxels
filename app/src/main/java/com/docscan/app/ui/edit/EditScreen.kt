package com.docscan.app.ui.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.docscan.app.core.Geometry
import com.docscan.app.core.Pt
import com.docscan.app.data.PageEntity
import com.docscan.app.data.cornerList
import com.docscan.app.data.settings
import com.docscan.app.export.ShareHelper
import kotlinx.coroutines.launch
import com.docscan.app.imaging.DocMode
import com.docscan.app.imaging.EnhanceSettings
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.UiEvent
import kotlin.math.roundToInt

private val TABS = listOf("Crop", "Enhance", "Clean", "Table")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: EditViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val page by vm.page.collectAsState()
    val source by vm.source.collectAsState()
    val preview by vm.preview.collectAsState()
    val table by vm.table.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    val saveScope = androidx.compose.runtime.rememberCoroutineScope()
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
                title = { Text("Edit page") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = vm::rotate) { Icon(Icons.Default.RotateRight, contentDescription = "Rotate page") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val current = page
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        TABS.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
                    }
                    when (tab) {
                        0 -> CropTab(vm, current, source)
                        1 -> EnhanceTab(vm, current, preview)
                        2 -> CleanTab(vm, current, preview)
                        else -> TableTab(vm, preview, table)
                    }
                }
            }
            BusyOverlay(visible = busyLabel != null, label = busyLabel ?: "")
        }
    }
}

@Composable
private fun PreviewImage(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CropTab(vm: EditViewModel, page: PageEntity, source: Bitmap?) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            if (source == null) {
                CircularProgressIndicator()
            } else {
                CropEditor(bitmap = source, corners = page.cornerList(), onCommit = vm::setCorners)
            }
        }
        Text(
            "Drag the round handles to match the page. The small handles on each side move that whole edge.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = vm::autoDetectEdges, modifier = Modifier.weight(1f), enabled = source != null) { Text("Auto-detect") }
            OutlinedButton(onClick = vm::useFullImage, modifier = Modifier.weight(1f)) { Text("Whole photo") }
        }
    }
}

@Composable
private fun EnhanceTab(vm: EditViewModel, page: PageEntity, preview: Bitmap?) {
    val settings = page.settings()
    var brightness by remember(settings.brightness) { mutableFloatStateOf(settings.brightness.toFloat()) }
    var contrast by remember(settings.contrast) { mutableFloatStateOf(settings.contrast) }

    Column(Modifier.fillMaxSize()) {
        PreviewImage(preview, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { vm.setSettings(settings.copy(mode = mode)) },
                        label = { Text(mode.label) },
                    )
                }
            }
            SwitchRow("Remove shadows and uneven light", settings.removeShadows) { vm.setSettings(settings.copy(removeShadows = it)) }
            SwitchRow("Auto brightness and contrast", settings.autoLevels) { vm.setSettings(settings.copy(autoLevels = it)) }
            Text("Brightness  ${brightness.roundToInt()}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                onValueChangeFinished = { vm.setSettings(settings.copy(brightness = brightness.roundToInt())) },
                valueRange = -100f..100f,
            )
            Text("Contrast  ${"%.2f".format(contrast)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = contrast,
                onValueChange = { contrast = it },
                onValueChangeFinished = { vm.setSettings(settings.copy(contrast = contrast)) },
                valueRange = 0.5f..2f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.setSettings(settings.copy(brightness = 0, contrast = 1f)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset sliders") }
                Button(onClick = { vm.applyLookToAll(settings) }, modifier = Modifier.weight(1f)) { Text("Apply to all pages") }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CleanTab(vm: EditViewModel, page: PageEntity, preview: Bitmap?) {
    var strokes by remember { mutableStateOf<List<List<Pt>>>(emptyList()) }
    var brush by remember { mutableFloatStateOf(0.03f) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            if (preview == null) {
                CircularProgressIndicator()
            } else {
                BrushCanvas(
                    bitmap = preview,
                    strokes = strokes,
                    brushFraction = brush,
                    onStroke = { strokes = strokes + listOf(it) },
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Auto-clean removes hands and fingers, pens and other objects, staple pins and folded corners at the page edge. If it misses something, paint over it with your finger, then tap Remove painted area.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Brush size", style = MaterialTheme.typography.labelMedium)
            Slider(value = brush, onValueChange = { brush = it }, valueRange = 0.01f..0.08f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = vm::autoRemoveHand, modifier = Modifier.weight(1f)) { Text("Auto-clean") }
                Button(
                    onClick = {
                        vm.removePainted(strokes, brush)
                        strokes = emptyList()
                    },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Remove painted area") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Clear painting")
                }
                OutlinedButton(onClick = vm::restoreOriginalPage, enabled = page.cleanedPath != null, modifier = Modifier.weight(1f)) {
                    Text("Undo removal")
                }
            }
        }
    }
}

@Composable
private fun TableTab(vm: EditViewModel, preview: Bitmap?, table: TableState?) {
    var export by remember { mutableStateOf(TableExport.EXCEL) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            when {
                table != null -> TableOverlay(table.bitmap, table.cells)
                preview != null -> PreviewImage(preview, Modifier.fillMaxSize())
                else -> CircularProgressIndicator()
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (table == null) {
                Text(
                    "Finds tables that have visible grid lines. Straighten and crop the page first for the best result.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "${table.cells.size} cells found. Tap Read text, check the cells below, then export.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(onClick = vm::detectTable, modifier = Modifier.weight(1f)) { Text("Find table") }
                Button(onClick = vm::readTableText, enabled = table != null, modifier = Modifier.weight(1f)) { Text("Read text") }
            }
            if (table != null && table.rows.isNotEmpty()) {
                CellEditor(table.rows, vm::editCell)
            }
            if (table != null) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TableExport.entries.forEach { kind ->
                        FilterChip(selected = export == kind, onClick = { export = kind }, label = { Text(kind.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = { vm.exportTable(export, save = false) }, modifier = Modifier.weight(1f)) { Text("Share") }
                    OutlinedButton(onClick = { vm.exportTable(export, save = true) }, modifier = Modifier.weight(1f)) {
                        Text("Save to phone")
                    }
                }
                OutlinedButton(onClick = { vm.exportTable(export, save = false, saveAs = true) }, modifier = Modifier.fillMaxWidth()) { Text("Save as… (choose folder and name)") }
            }
        }
    }
}

/** Recognised text laid out as a grid so mistakes can be fixed before exporting. */
@Composable
private fun CellEditor(rows: List<List<String>>, onEdit: (Int, Int, String) -> Unit) {
    // One horizontal scroll for the whole grid so columns stay aligned; rows scroll vertically.
    Box(Modifier.fillMaxWidth().heightIn(max = 160.dp).padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
        LazyColumn {
            itemsIndexed(rows) { r, row ->
                Row {
                    row.forEachIndexed { c, text ->
                        BasicTextField(
                            value = text,
                            onValueChange = { onEdit(r, c, it) },
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.width(96.dp).padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
