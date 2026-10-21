package com.docscan.app.ui.write

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docscan.app.core.word.PageSetup
import com.docscan.app.core.word.PaperSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun FindDialog(c: EditorController, onClose: () -> Unit) {
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
internal fun PageSetupDialog(page: PageSetup, onApply: (PageSetup) -> Unit, onDismiss: () -> Unit) {
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
internal fun tw(text: String, fallback: Int): Int {
    val v = text.replace(',', '.').toFloatOrNull() ?: return fallback
    return (v.coerceIn(0f, 10f) * 567f).toInt()
}

@Composable
internal fun Modifier.horizontalScrollFreeColumn(): Modifier = this.then(Modifier.fillMaxWidth())


@Composable
internal fun CommentEditDialog(title: String, initial: String, action: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
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
internal fun CommentsDialog(controller: EditorController, onDismiss: () -> Unit) {
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


/** Save the formatting at the cursor under a name, and apply saved formatting to the selection or paragraph. */
@Composable
internal fun MyStylesDialog(c: EditorController, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var styles by remember { mutableStateOf(MyStyles.load(context)) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    fun store(list: List<MyStyles.Style>) { styles = list; MyStyles.save(context, list) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("My styles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Format some text the way you like, put the cursor in it, then save that look under a name. Apply it to selected text or the paragraph the cursor is in.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (styles.isEmpty()) Text("Nothing saved yet.", style = MaterialTheme.typography.bodyMedium)
                styles.forEach { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { c.applySaved(st.attrs); onClose() }, modifier = Modifier.weight(1f)) {
                            Text(st.name, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { store(styles - st) }) { Text("Delete") }
                    }
                }
                if (naming) {
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name for this look") })
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val look = c.captureFormat()
                            store(styles.filter { it.name != name.trim() } + MyStyles.Style(name.trim(), look))
                            naming = false
                            name = ""
                        },
                    ) { Text("Save") }
                } else TextButton(onClick = { naming = true }) { Text("Save the formatting at the cursor…") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
