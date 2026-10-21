package com.docscan.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import com.docscan.app.R
import com.docscan.app.ui.TextInputDialog
import com.docscan.app.ui.library.friendlyDate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.app.data.LibraryItem
import com.docscan.app.ui.kit.ToolTile
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.library.LibraryRow
import com.docscan.app.ui.shell.Actions
import com.docscan.app.ui.theme.DocxelsTheme

/** Home: one place to start scanning, writing or opening a file, with the tools and latest documents below. */
@Composable
fun HomeScreen(
    items: List<LibraryItem>,
    actions: Actions,
    onOpen: (LibraryItem) -> Unit,
    onSearch: () -> Unit,
    onAllTools: () -> Unit,
    onAllDocs: () -> Unit,
    name: String,
    onName: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val t = DocxelsTheme.tints
    val container = com.docscan.app.LocalContainer.current
    val session by container.cloud.session.collectAsState()
    val plan by container.cloud.planState.collectAsState()
    val exporting by container.exportStatus.state.collectAsState()
    var accountOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(session?.email, accountOpen) { container.cloud.refreshPlan() }
    if (accountOpen) com.docscan.app.ui.cloud.AccountDialog(container.cloud) { accountOpen = false }
    var editingName by remember { mutableStateOf(false) }
    if (editingName) {
        TextInputDialog("Your name", name, "Save", { onName(it); editingName = false }, { editingName = false }, allowBlank = true, maxLength = 30)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.docxels_logo), contentDescription = "Docxels",
                modifier = Modifier.size(56.dp).shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = DocxelsTheme.brand.a, spotColor = DocxelsTheme.brand.a).clip(RoundedCornerShape(18.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(greeting(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    name.ifBlank { "Welcome" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Change your name") { editingName = true },
                )
            }
            Row(
                Modifier.height(48.dp).clip(CircleShape).background(Color(0xFFFFF0C2))
                    .clickable(role = Role.Button, onClickLabel = "Account and plan") { accountOpen = true }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (plan?.pro == true) Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF6B4E00), modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text(if (plan?.pro == true) "Pro" else "Free", fontWeight = FontWeight.Bold, color = Color(0xFF6B4E00))
            }
            Box(
                Modifier.padding(start = 10.dp).size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable(role = Role.Button, onClickLabel = "Open settings", onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Tune, contentDescription = "Settings") }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.Button, onClickLabel = "Search documents", onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Search names and scanned text", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }

        // The latest document, big, with one tap to carry on.
        items.firstOrNull()?.let { last ->
            ContinueCard(last, exporting?.takeIf { it.key == com.docscan.app.data.LibraryMeta.key(last.kind, last.id) }, onClick = { onOpen(last) })
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Default.NoteAdd, "New doc", Modifier.weight(1f), actions.newDoc)
                QuickAction(Icons.Default.ReceiptLong, "Invoice", Modifier.weight(1f), actions.newInvoice)
                QuickAction(Icons.Default.Article, "Templates", Modifier.weight(1f), actions.templates)
                QuickAction(Icons.Default.Download, "Import", Modifier.weight(1f), actions.importMenu)
            }
        }

        // Hero: start a document. Touching the card turns the sheets: drag, flick or tap.
        val sheets = rememberSheetsState()
        if (items.isEmpty()) Column(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DocxelsTheme.brand.gradient).sheetsTouch(sheets).padding(18.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(end = 96.dp).align(Alignment.CenterStart)) {
                    Text("Your office, in your pocket", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Write, edit and convert documents.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
                FloatingSheets(sheets, Modifier.align(Alignment.CenterEnd))
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroAction(Icons.Default.NoteAdd, "New doc", primary = true, modifier = Modifier.weight(1f), onClick = actions.newDoc)
                HeroAction(Icons.Default.ReceiptLong, "Invoice", modifier = Modifier.weight(1f), onClick = actions.newInvoice)
                HeroAction(Icons.Default.Article, "Templates", modifier = Modifier.weight(1f), onClick = actions.templates)
                HeroAction(Icons.Default.Download, "Import", modifier = Modifier.weight(1f), onClick = actions.importMenu)
            }
        }

        SectionTitle("Convert", "All tools", onAllTools)
        // Tiles are a fixed width so the next one always peeks in from the edge, telling the eye that the row scrolls.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolTile(Icons.Default.PictureAsPdf, "PDF to Word", t.pink, actions.pdfToWord, Modifier.width(92.dp))
            ToolTile(Icons.Default.Description, "Word to PDF", t.blue, actions.wordToPdf, Modifier.width(92.dp))
            ToolTile(Icons.Default.PhotoLibrary, "Photos to PDF", t.green, actions.importPhotos, Modifier.width(92.dp))
            ToolTile(Icons.Default.CallMerge, "Merge PDFs", t.teal, { actions.pdfTool("merge") }, Modifier.width(92.dp))
            ToolTile(Icons.Default.TextSnippet, "Scan to Word", t.orange, actions.scanToWord, Modifier.width(92.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Continue where you left off", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onAllDocs).padding(8.dp),
                )
            }
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().docCard().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start a document or open a file. Everything you make shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(5).forEach { item -> LibraryRow(item, onClick = { onOpen(item) }) }
            }
        }
    }
}

@Composable
private fun HeroAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    soon: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (primary) Color.White else Color.White.copy(alpha = 0.18f)
    val fg = if (primary) MaterialTheme.colorScheme.primary else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
            .alpha(if (soon) 0.7f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(8.dp),
        )
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
}

/** The most recent document as a large card: a little page, its title and details, and a button to carry on. */
@Composable
private fun ContinueCard(item: LibraryItem, export: com.docscan.app.export.ExportStatus.State?, onClick: () -> Unit) {
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(DocxelsTheme.brand.gradient).padding(20.dp),
    ) {
        Text("CONTINUE WHERE YOU STOPPED", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CardThumb(item)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(item.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(
                    "Edited ${friendlyDate(item.modified).replaceFirstChar { it.lowercase() }} · " +
                        (if (item.meta.lastPage > 0 && item.meta.pageTotal > 0) "Page ${item.meta.lastPage.coerceAtMost(item.meta.pageTotal)} of ${item.meta.pageTotal}" else item.detail),
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(top = 4.dp),
                )
                if (item.meta.lastPage > 0 && item.meta.pageTotal > 0) {
                    // How far through the document the person got (page reached out of all pages).
                    val frac = (item.meta.lastPage.toFloat() / item.meta.pageTotal).coerceIn(0f, 1f)
                    val shown by androidx.compose.animation.core.animateFloatAsState(frac, androidx.compose.animation.core.tween(900), label = "readPct")
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { shown },
                            modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                            color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 0.dp, drawStopIndicator = {},
                        )
                        Text("${(frac * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (export != null) Row(
                    Modifier.padding(top = 10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (export.fraction == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else androidx.compose.material3.CircularProgressIndicator(progress = { export.fraction }, modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(
                        export.label + (export.fraction?.let { " · ${(it * 100).toInt()}%" } ?: "…"),
                        color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 18.dp).fillMaxWidth().height(56.dp).clip(CircleShape).background(Color.White)
                .clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Continue editing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 10.dp).size(22.dp))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** First pages already drawn, so Home does not redraw them every time it is shown. */
private object CardThumbs {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(6) {}
    fun get(key: String): android.graphics.Bitmap? = cache.get(key)
    fun put(key: String, b: android.graphics.Bitmap) { cache.put(key, b) }
}

/** A real picture of the document's first page: scans, PDFs and Word documents alike. */
@Composable
private fun CardThumb(item: LibraryItem) {
    val container = com.docscan.app.LocalContainer.current
    val key = "${item.kind}:${item.id}:${item.modified}"
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(CardThumbs.get(key), key) {
        if (value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                when (item.kind) {
                    com.docscan.app.data.DocKind.SCAN -> item.cover?.let { container.renderer.thumbnail(it) }
                    com.docscan.app.data.DocKind.PDF -> {
                        val f = container.pdfs.open(item.id)
                        if (f == null) null else try { com.docscan.app.pdf.PdfPreview(f).use { it.render(0, 360) } } finally { f.delete() }
                    }
                    com.docscan.app.data.DocKind.DOCX -> {
                        val doc = container.words.load(item.id)
                        if (doc == null) null else {
                            // Only the start of the document is needed for page one.
                            val tmp = java.io.File.createTempFile("thumb", ".pdf", container.thumbDir())
                            try {
                                tmp.outputStream().use { com.docscan.app.export.WordPdf.write(doc.copy(blocks = doc.blocks.take(40)), it) }
                                com.docscan.app.pdf.PdfPreview(tmp).use { it.render(0, 360) }
                            } finally { tmp.delete() }
                        }
                    }
                }
            }.getOrNull()
        }?.also { CardThumbs.put(key, it) }
    }
    val b = bmp
    LivePage(b, "First page of ${item.title}")
}

/**
 * The first page, alive: it fans in on top of two pages behind it, then floats and sways gently. Touch it and it
 * leans toward your finger in 3D, then springs back when you let go. It never consumes touches, so scrolling and
 * tapping the card still work. Stays still when the phone has animations switched off.
 */
@Composable
private fun LivePage(b: android.graphics.Bitmap?, description: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val still = remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tx = remember { androidx.compose.animation.core.Animatable(0f) }
    val ty = remember { androidx.compose.animation.core.Animatable(0f) }
    val fan = remember { androidx.compose.animation.core.Animatable(if (still) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        fan.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
    }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "livePage")
    val sway by t.animateFloat(
        -7f, 7f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.EaseInOutSine), androidx.compose.animation.core.RepeatMode.Reverse), label = "sway",
    )
    val bob by t.animateFloat(
        -3f, 3f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2500, easing = androidx.compose.animation.core.EaseInOutSine), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob",
    )
    Box(
        Modifier
            .size(width = 100.dp, height = 124.dp)
            .pointerInput(Unit) {
                if (still) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (c.pressed) {
                            val nx = (c.position.x / size.width - 0.5f).coerceIn(-0.8f, 0.8f)
                            val ny = (c.position.y / size.height - 0.5f).coerceIn(-0.8f, 0.8f)
                            scope.launch { ty.snapTo(nx * 70f); tx.snapTo(-ny * 70f) }
                        }
                    } while (ev.changes.any { it.pressed })
                    scope.launch { ty.animateTo(0f, androidx.compose.animation.core.spring(0.35f, androidx.compose.animation.core.Spring.StiffnessLow)) }
                    scope.launch { tx.animateTo(0f, androidx.compose.animation.core.spring(0.35f, androidx.compose.animation.core.Spring.StiffnessLow)) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Two pages behind, fanned out to either side.
        for (i in 0 until 2) {
            val dir = if (i == 0) -1f else 1f
            Box(
                Modifier
                    .size(width = 78.dp, height = 104.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationZ = dir * 9f * fan.value
                        translationX = dir * 10.dp.toPx() * fan.value
                        rotationY = ty.value * 0.6f + (if (still) 0f else sway * 0.5f * -dir)
                        rotationX = tx.value * 0.6f
                        alpha = 0.55f + 0.1f * i
                    }
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
        // The real first page.
        Box(
            Modifier
                .size(width = 84.dp, height = 112.dp)
                .graphicsLayer {
                    cameraDistance = 14f * density
                    rotationY = ty.value + (if (still) 0f else sway)
                    rotationX = tx.value
                    translationY = if (still) 0f else bob.dp.toPx()
                    val pop = 0.6f + 0.4f * fan.value
                    scaleX = pop; scaleY = pop
                    shadowElevation = 10.dp.toPx()
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
                .background(Color.White),
        ) {
            if (b != null) Image(
                b.asImageBitmap(), contentDescription = description, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
