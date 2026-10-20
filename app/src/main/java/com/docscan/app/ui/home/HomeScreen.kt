package com.docscan.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import com.docscan.app.R
import com.docscan.app.data.DocKind
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
            Box(Modifier.height(48.dp).clip(CircleShape).background(Color(0xFFFFF0C2)).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text("Free", fontWeight = FontWeight.Bold, color = Color(0xFF6B4E00))
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
            ContinueCard(last, onClick = { onOpen(last) })
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
private fun ContinueCard(item: LibraryItem, onClick: () -> Unit) {
    val sheets = rememberSheetsState()
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(DocxelsTheme.brand.gradient).sheetsTouch(sheets).padding(20.dp),
    ) {
        Text("CONTINUE WHERE YOU STOPPED", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            FloatingSheets(sheets)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(item.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(
                    "Edited ${friendlyDate(item.modified).replaceFirstChar { it.lowercase() }} · ${item.detail}",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    when (item.kind) { DocKind.DOCX -> "Word document"; DocKind.PDF -> "PDF"; else -> "Scan" },
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 14.dp, vertical = 7.dp),
                )
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
