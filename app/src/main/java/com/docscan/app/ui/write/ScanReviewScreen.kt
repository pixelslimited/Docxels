package com.docscan.app.ui.write

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.docscan.app.AppContainer
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.Run
import com.docscan.app.core.word.WordDoc
import com.docscan.app.data.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stands in for a document id while a converted scan is being checked; the app opens the review screen for it. */
const val REVIEW_ID = "review"

/** A converted scan waiting for the person to check the recognised text against the pages. */
class ScanReview(val doc: WordDoc, val pages: List<PageEntity>)

/** Splits a converted document into one block list per scanned page (the converter puts a page break between pages). */
internal fun blocksByPage(blocks: List<Block>): List<List<Block>> {
    val out = ArrayList<MutableList<Block>>()
    var cur = ArrayList<Block>()
    for (b in blocks) {
        if (b is Block.PageBreak) { out += cur; cur = ArrayList() } else cur += b
    }
    out += cur
    return out
}

/** Applies the edited text: a paragraph that was changed keeps its first run's look; line breaks become paragraphs. */
internal fun applyReviewEdits(pages: List<List<Block>>, edits: Map<Pair<Int, Int>, String>): List<Block> {
    val result = ArrayList<Block>()
    pages.forEachIndexed { pi, blocks ->
        if (pi > 0) result += Block.PageBreak
        blocks.forEachIndexed { bi, b ->
            val edited = edits[pi to bi]
            if (b is Block.Para && edited != null && edited != b.paragraph.text) {
                val style = b.paragraph.runs.firstOrNull()?.style ?: com.docscan.app.core.word.RunStyle()
                edited.split("\n").forEach { line -> result += Block.Para(b.paragraph.copy(runs = listOf(Run(line, style)))) }
            } else result += b
        }
    }
    return result
}

@Composable
fun ScanReviewScreen(container: AppContainer, review: ScanReview, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val pages = remember(review) { blocksByPage(review.doc.blocks) }
    val edits = remember(review) { mutableStateMapOf<Pair<Int, Int>, String>() }
    var index by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(index) {
        image = null
        val entity = review.pages.getOrNull(index) ?: return@LaunchedEffect
        image = runCatching { container.renderer.render(entity, 1200).asImageBitmap() }.getOrNull()
    }

    fun save(withEdits: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            val doc = if (withEdits) review.doc.copy(blocks = applyReviewEdits(pages, edits)) else review.doc
            val id = withContext(Dispatchers.IO) { container.words.add(doc) }
            onDone(id)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Discard") }
                Column(Modifier.weight(1f)) {
                    Text("Check the text", style = MaterialTheme.typography.titleMedium)
                    Text("Page ${index + 1} of ${pages.size}", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.TextButton(onClick = { save(false) }, enabled = !saving) { Text("Skip") }
                IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                IconButton(onClick = { if (index < pages.size - 1) index++ }, enabled = index < pages.size - 1) { Icon(Icons.Default.ChevronRight, "Next page") }
            }
            Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                val img = image
                if (img != null) Image(img, "Scanned page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Loading page…", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Fix any words the scan read wrongly. Tables and pictures can be changed in the editor.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            val blocks = pages.getOrElse(index) { emptyList() }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(blocks) { bi, b ->
                    when (b) {
                        is Block.Para -> OutlinedTextField(
                            value = edits[index to bi] ?: b.paragraph.text,
                            onValueChange = { edits[index to bi] = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                        is Block.Table -> Text("Table (${b.table.rows.size} rows): edit it in the editor", style = MaterialTheme.typography.bodySmall)
                        is Block.Image -> Text("Picture", style = MaterialTheme.typography.bodySmall)
                        is Block.Shape -> Text("Shape", style = MaterialTheme.typography.bodySmall)
                        Block.PageBreak -> Unit
                    }
                }
            }
            Button(
                onClick = { save(true) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text(if (saving) "Saving…" else "Open in editor") }
        }
    }
}
