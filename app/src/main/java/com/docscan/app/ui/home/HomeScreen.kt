package com.docscan.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
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
import com.docscan.app.ui.kit.DocBadge
import com.docscan.app.ui.kit.TintIcon
import com.docscan.app.ui.kit.ToolTile
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.library.LibraryRow
import com.docscan.app.ui.shell.Actions
import com.docscan.app.ui.theme.Brand
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
) {
    val t = DocxelsTheme.tints
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brand.Gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Text("Docxels", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
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
            Text("Search your documents", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        }

        // Hero: the four ways to begin. Touching the card turns the sheets: drag, flick or tap.
        val sheets = rememberSheetsState()
        Column(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.Gradient).sheetsTouch(sheets).padding(18.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(end = 96.dp).align(Alignment.CenterStart)) {
                    Text("What are we working on?", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Scan, write and convert in one place.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                }
                FloatingSheets(sheets, Modifier.align(Alignment.CenterEnd))
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroAction(Icons.Default.NoteAdd, "New doc", primary = true, modifier = Modifier.weight(1f), onClick = actions.newDoc)
                HeroAction(Icons.Default.DocumentScanner, "Scan", modifier = Modifier.weight(1f), onClick = actions.scan)
                HeroAction(Icons.Default.FolderOpen, "Open PDF", modifier = Modifier.weight(1f)) { actions.pdfTool("view") }
                HeroAction(Icons.Default.Download, "Import", modifier = Modifier.weight(1f), onClick = actions.importMenu)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Convert and scan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "All tools",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAllTools).padding(8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ToolTile(Icons.Default.PictureAsPdf, "PDF to Word", t.pink, actions.pdfToWord, Modifier.weight(1f))
            ToolTile(Icons.Default.TextSnippet, "Scan to Word", t.teal, actions.scanToWord, Modifier.weight(1f))
            ToolTile(Icons.Default.Description, "Word to PDF", t.blue, actions.wordToPdf, Modifier.weight(1f))
            ToolTile(Icons.Default.PhotoLibrary, "Photos to PDF", t.green, actions.importPhotos, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                    "Scan a paper document or start writing. Everything you make shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(5).forEach { item -> LibraryRow(item, onClick = { onOpen(item) }, menu = null) }
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
