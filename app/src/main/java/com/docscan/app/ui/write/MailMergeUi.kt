package com.docscan.app.ui.write

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.core.RecipientFiles
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.MailMerge
import com.docscan.app.core.word.WordDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mail merge, step by step in one dialog: choose the list (a table in this document, or an Excel or CSV file),
 * insert fields into the letter, look at each recipient, and make a document with one page per recipient.
 */
@Composable
fun MailMergeDialog(
    document: () -> WordDoc,
    onInsertField: (String) -> Unit,
    onCreate: (WordDoc, skipBlock: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rec by remember { mutableStateOf<MailMerge.Recipients?>(null) }
    var source by remember { mutableStateOf("") }
    var skipBlock by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var tableMenu by remember { mutableStateOf(false) }

    var tick by remember { mutableIntStateOf(0) }
    val doc = remember(tick) { document() }
    val tables = doc.blocks.withIndex().filter { (_, b) -> b is Block.Table && (b as Block.Table).table.rows.size >= 2 }

    fun load(r: MailMerge.Recipients?, label: String, skip: Int) {
        if (r == null) { message = "That list has no rows to merge. The first row should name the columns."; return }
        rec = r; source = label; skipBlock = skip; index = 0; message = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val grid = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes().takeIf { b -> b.size <= 25 * 1024 * 1024 } } ?: return@runCatching null
                    if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) RecipientFiles.parseXlsx(bytes)
                    else RecipientFiles.parseCsv(String(bytes, Charsets.UTF_8))
                }.getOrNull()
            }
            if (grid == null) message = "Could not read that file. Use an Excel (.xlsx) or CSV file."
            else load(MailMerge.fromGrid(grid), name, -1)
        }
    }

    val list = rec
    val missing = list?.let { MailMerge.missingColumns(doc, it) }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mail merge") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Type fields such as {{Name}} in your letter, choose your list, then create one page for each person.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("1. Your list", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(enabled = tables.isNotEmpty(), onClick = {
                            if (tables.size == 1) tables[0].let { (i, b) -> load(MailMerge.fromTable((b as Block.Table).table), "Table in this document", i) }
                            else tableMenu = true
                        }) { Text("Table here") }
                        DropdownMenu(expanded = tableMenu, onDismissRequest = { tableMenu = false }) {
                            tables.forEachIndexed { n, (i, b) ->
                                val t = (b as Block.Table).table
                                DropdownMenuItem(text = { Text("Table ${n + 1} (${t.rows.size} rows)") }, onClick = {
                                    tableMenu = false
                                    load(MailMerge.fromTable(t), "Table ${n + 1} in this document", i)
                                })
                            }
                        }
                    }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Excel or CSV file") }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (list != null) {
                    Text("$source: ${list.rows.size} recipients", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Put a field in your letter", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.columns.forEach { c -> FilterChip(false, { onInsertField("{{$c}}"); tick++ }, label = { Text(c) }) }
                    }
                    if (missing.isNotEmpty()) {
                        Text(
                            "Your letter uses fields the list does not have: ${missing.joinToString { "{{$it}}" }}. They will stay as typed.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text("3. Check a recipient", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(enabled = index > 0, onClick = { index-- }) { Text("Back") }
                        Text("${index + 1} of ${list.rows.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(enabled = index < list.rows.size - 1, onClick = { index++ }) { Text("Next") }
                    }
                    val row = list.rows[index]
                    list.columns.forEach { c -> Text("$c: ${row[c.lowercase()].orEmpty()}", style = MaterialTheme.typography.bodyMedium) }
                    val first = doc.blocks.firstNotNullOfOrNull { (it as? Block.Para)?.paragraph?.text?.takeIf { t -> t.isNotBlank() } }
                    if (first != null) Text("Your first line becomes: " + MailMerge.fillText(first, row), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = list != null && list.rows.isNotEmpty(), onClick = {
                val r = list ?: return@TextButton
                val doc = document()
                onCreate(MailMerge.merge(doc, r, "${doc.title} - merged (${r.rows.size})", skipBlock), skipBlock)
            }) { Text("Create letters") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
