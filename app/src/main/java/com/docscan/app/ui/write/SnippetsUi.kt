package com.docscan.app.ui.write

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docscan.app.core.Snippet
import com.docscan.app.core.Snippets

/** The saved snippets, kept on this phone only (the app's private preferences). */
class SnippetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("docxels_snippets", Context.MODE_PRIVATE)
    fun load(): List<Snippet> = Snippets.decode(prefs.getString("list", ""))
    fun save(list: List<Snippet>) { prefs.edit().putString("list", Snippets.encode(list)).apply() }
}

/** "My text": tap a saved snippet to type it at the cursor; save new ones (the selected words are offered first). */
@Composable
fun SnippetsDialog(store: SnippetStore, selectedText: String, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf(store.load()) }
    var adding by remember { mutableStateOf(selectedText.isNotBlank() || list.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(selectedText.trim().take(Snippets.MAX_TEXT)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My text") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (list.isEmpty() && !adding) Text("Nothing saved yet. Save an address, bank details or a closing line once, then insert it anywhere.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { onInsert(s.text) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text(s.text.replace('\n', ' '), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { list = list.filter { it != s }; store.save(list) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${s.name}") }
                    }
                }
                if (adding) {
                    OutlinedTextField(name, { name = it.take(Snippets.MAX_NAME) }, label = { Text("Name (for example Bank details)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it.take(Snippets.MAX_TEXT) }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
                    OutlinedButton(
                        enabled = text.isNotBlank() && list.size < Snippets.MAX,
                        onClick = {
                            Snippets.clean(name, text)?.let { s -> list = Snippets.add(list, s); store.save(list); name = ""; text = ""; adding = false }
                        },
                    ) { Text("Save") }
                } else if (list.size < Snippets.MAX) TextButton(onClick = { adding = true }) { Text("Save new text") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
