package com.docscan.app.ui.edit

import com.docscan.app.ui.SwitchRow
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.docscan.app.core.Pt
import com.docscan.app.core.Geometry
import com.docscan.app.data.PageEntity
import com.docscan.app.data.cornerList
import com.docscan.app.data.settings
import com.docscan.app.export.ShareHelper
import kotlinx.coroutines.launch
import com.docscan.app.imaging.DocMode
import com.docscan.app.ui.BusyOverlay
import com.docscan.app.ui.UiEvent
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.docscan.app.ui.swipeTabs

// A page opens on Enhance. Crop and the table extractor are reached from buttons there and come back to Enhance.
private const val CROP = 0
private const val ENHANCE = 1
private const val TABLE = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: EditViewModel, onBack: () -> Unit, onGoToPage: (String) -> Unit = {}, onRescan: (String) -> Unit = {}) {
    val context = LocalContext.current
    val page by vm.page.collectAsState()
    val pageIds by vm.pageIds.collectAsState()
    val pageIndex = pageIds.indexOf(page?.id)
    val source by vm.source.collectAsState()
    val preview by vm.preview.collectAsState()
    val table by vm.table.collectAsState()
    val busyLabel by vm.busyLabel.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(ENHANCE) }
    androidx.activity.compose.BackHandler(enabled = tab != ENHANCE) { tab = ENHANCE }
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
                title = { Text(if (pageIndex >= 0 && pageIds.size > 1) "Page ${pageIndex + 1} of ${pageIds.size}" else "Edit page") },
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
                // On Enhance, sliding sideways moves to the next or previous page (zooming or painting use their own drags first).
                Column(Modifier.fillMaxSize().swipeTabs(pageIndex.coerceAtLeast(0), pageIds.size, enabled = tab == ENHANCE && pageIndex >= 0) { onGoToPage(pageIds[it]) }) {
                    if (tab == ENHANCE) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        ) {
                            androidx.compose.material3.OutlinedButton(onClick = { tab = CROP }, modifier = Modifier.weight(1f)) { Text("Crop") }
                            androidx.compose.material3.OutlinedButton(onClick = { tab = TABLE }, modifier = Modifier.weight(1f)) { Text("Extract table") }
                        }
                    } else {
                        androidx.compose.material3.TextButton(onClick = { tab = ENHANCE }, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text("‹ Back to Enhance")
                        }
                    }
                    when (tab) {
                        CROP -> CropTab(vm, current, source, onRescan = { onRescan(current.id) })
                        ENHANCE -> EnhanceTab(vm, current, preview)
                        else -> TableTab(vm, preview, table)
                    }
                }
            }
            BusyOverlay(visible = busyLabel != null, label = busyLabel ?: "")
        }
    }
}

/** The scanned page. Pinch to zoom, drag to move around when zoomed in, double-tap to zoom in or back out. */
@Composable
private fun PreviewImage(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (zoom > 1.01f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // One finger only moves the page once it is zoomed in; otherwise the touch is left alone.
                        // The event where the last finger lifts has no centroid (it is NaN); using it would push the page off screen.
                        if (fingers >= 1 && (fingers >= 2 || zoom > 1.01f)) {
                            val newZoom = (zoom * (if (fingers >= 2) event.calculateZoom() else 1f)).coerceIn(1f, 4f)
                            val rel = event.calculateCentroid() - Offset(size.width / 2f, size.height / 2f)
                            val moved = rel - (rel - pan) * (newZoom / zoom) + event.calculatePan()
                            val maxX = size.width * (newZoom - 1f) / 2f
                            val maxY = size.height * (newZoom - 1f) / 2f
                            zoom = newZoom
                            pan = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                },
            )
        }
    }
}

private fun turnedCopy(source: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return source
    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

@Composable
private fun CropTab(vm: EditViewModel, page: PageEntity, source: Bitmap?, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            if (source == null) {
                CircularProgressIndicator()
            } else {
                // Shown the way the page is turned, so the rotate button visibly turns it here too. The corners are
                // stored on the un-rotated photo, so they are turned for display and turned back when committed.
                val turn = page.rotation
                val shown = remember(source, turn) { turnedCopy(source, turn) }
                val shownCorners = remember(page.corners, turn) { page.cornerList().map { Geometry.rotate(it, turn) } }
                CropEditor(
                    bitmap = shown,
                    corners = shownCorners,
                    onCommit = { moved -> vm.setCorners(moved.map { Geometry.unrotate(it, turn) }) },
                )
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
        OutlinedButton(
            onClick = onRescan,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) { Text("Rescan this page") }
    }
}

@Composable
private fun EnhanceTab(vm: EditViewModel, page: PageEntity, preview: Bitmap?) {
    val settings = page.settings()
    var brightness by remember(settings.brightness) { mutableFloatStateOf(settings.brightness.toFloat()) }
    var contrast by remember(settings.contrast) { mutableFloatStateOf(settings.contrast) }
    var sharpen by remember(settings.sharpen) { mutableFloatStateOf(settings.sharpen.toFloat()) }
    var fine by remember { mutableStateOf(false) }
    var painting by remember { mutableStateOf(false) }
    var strokes by remember { mutableStateOf<List<List<Pt>>>(emptyList()) }
    var brush by remember { mutableFloatStateOf(0.03f) }

    Column(Modifier.fillMaxSize()) {
        if (painting && preview != null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                BrushCanvas(bitmap = preview, strokes = strokes, brushFraction = brush, onStroke = { strokes = strokes + listOf(it) })
            }
        } else {
            PreviewImage(preview, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
        }
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
            Text(
                "Shadows, uneven light, glare, paper colour, straightening and curve flattening are done for you automatically.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TextButton(onClick = { fine = !fine }) { Text(if (fine) "Hide fine-tune" else "Fine-tune (optional)") }
            if (fine) {
                Text("Fingers, objects and folded corners are removed automatically. If something is left or was removed by mistake:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(onClick = vm::autoRemoveHand, modifier = Modifier.weight(1f)) { Text("Clean again") }
                    OutlinedButton(onClick = vm::restoreOriginalPage, enabled = page.cleanedPath != null, modifier = Modifier.weight(1f)) { Text("Undo removal") }
                }
                OutlinedButton(
                    onClick = { painting = !painting; strokes = emptyList() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (painting) "Stop painting" else "Paint over something to remove") }
                if (painting) {
                    Text("Paint over it with your finger, then tap Remove painted area.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text("Brush size", style = MaterialTheme.typography.labelMedium)
                    Slider(value = brush, onValueChange = { brush = it }, valueRange = 0.01f..0.08f)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { vm.removePainted(strokes, brush); strokes = emptyList() },
                            enabled = strokes.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Remove painted area") }
                        OutlinedButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear painting") }
                    }
                }
                SwitchRow("Reduce noise (grain from dim light)", settings.denoise) { vm.setSettings(settings.copy(denoise = it)) }
                SwitchRow("Mirror (flip left to right)", settings.mirror) { vm.setSettings(settings.copy(mirror = it)) }
                Text("Sharpen (reduces mild blur)  ${sharpen.roundToInt()}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = sharpen,
                    onValueChange = { sharpen = it },
                    onValueChangeFinished = { vm.setSettings(settings.copy(sharpen = sharpen.roundToInt())) },
                    valueRange = 0f..100f,
                )
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.setSettings(settings.copy(brightness = 0, contrast = 1f, sharpen = 0)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset sliders") }
                Button(onClick = { vm.applyLookToAll(settings) }, modifier = Modifier.weight(1f)) { Text("Apply to all pages") }
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
