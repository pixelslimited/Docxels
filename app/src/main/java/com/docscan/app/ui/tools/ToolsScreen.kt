package com.docscan.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.docscan.app.ui.kit.SectionLabel
import com.docscan.app.ui.kit.TintIcon
import com.docscan.app.ui.kit.docCard
import com.docscan.app.ui.shell.Actions
import com.docscan.app.ui.theme.DocxelsTheme
import com.docscan.app.ui.theme.Tint

private class ToolDef(val icon: ImageVector, val label: String, val tint: Tint, val soon: Boolean, val run: () -> Unit)

@Composable
fun ToolsScreen(actions: Actions) {
    val t = DocxelsTheme.tints
    fun planned(name: String) = { actions.soon(name) }

    val scan = listOf(
        ToolDef(Icons.Default.DocumentScanner, "Document scan", t.violet, false, actions.scan),
        ToolDef(Icons.Default.Badge, "ID card", t.violet, true, planned("ID card scan")),
        ToolDef(Icons.Default.PhotoLibrary, "Import images", t.violet, false, actions.importPhotos),
        ToolDef(Icons.Default.ViewModule, "Batch scan", t.violet, false, actions.scan),
    )
    val pdf = listOf(
        ToolDef(Icons.Default.FolderOpen, "Open PDF", t.blue, false, { actions.pdfTool("view") }),
        ToolDef(Icons.Default.CallMerge, "Merge PDFs", t.blue, false, { actions.pdfTool("merge") }),
        ToolDef(Icons.Default.ContentCut, "Split PDF", t.blue, false, { actions.pdfTool("split") }),
        ToolDef(Icons.Default.Compress, "Compress", t.blue, false, { actions.pdfTool("compress") }),
        ToolDef(Icons.Default.SwapVert, "Reorder pages", t.blue, false, { actions.pdfTool("organize") }),
        ToolDef(Icons.Default.RotateRight, "Rotate pages", t.blue, false, { actions.pdfTool("rotate") }),
        ToolDef(Icons.Default.Lock, "Password protect", t.blue, false, { actions.pdfTool("protect") }),
    )
    val convert = listOf(
        ToolDef(Icons.Default.TextSnippet, "PDF to Word", t.green, false, actions.pdfToWord),
        ToolDef(Icons.Default.TextSnippet, "Scan to Word", t.green, false, actions.scanToWord),
        ToolDef(Icons.Default.PictureAsPdf, "Word to PDF", t.green, false, actions.wordToPdf),
        ToolDef(Icons.Default.Image, "Photos to PDF", t.green, false, actions.importPhotos),
        
        ToolDef(Icons.Default.TableChart, "Scan to Excel", t.green, true, planned("Scan to Excel")),
    )
    val write = listOf(
        ToolDef(Icons.Default.Edit, "New document", t.orange, false, actions.newDoc),
        ToolDef(Icons.Default.Draw, "Sign a PDF", t.orange, false, { actions.pdfTool("sign") }),
        ToolDef(Icons.Default.AutoFixHigh, "Fill form", t.orange, false, { actions.pdfTool("sign") }),
        ToolDef(Icons.Default.NoteAdd, "Templates", t.orange, false, actions.templates),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Everything runs on this phone. Tools marked Soon are planned but not built yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Group("Scan", t.violet.strong, scan)
        Group("Write", t.orange.strong, write)
        Group("Convert", t.green.strong, convert)
        Group("PDF", t.blue.strong, pdf)
    }
}

@Composable
private fun Group(title: String, color: androidx.compose.ui.graphics.Color, tools: List<ToolDef>) {
    SectionLabel(title, color)
    tools.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { tool -> ToolCard(tool, Modifier.weight(1f)) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolCard(tool: ToolDef, modifier: Modifier) {
    Row(
        modifier.docCard().clickable(role = Role.Button, onClick = tool.run).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintIcon(tool.icon, tool.tint, box = 42.dp, iconSize = 22.dp, modifier = Modifier.alpha(if (tool.soon) 0.55f else 1f))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(tool.label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            if (tool.soon) Text("Soon", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
