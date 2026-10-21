package com.docscan.app.ui.write

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/** One tool the person can find by typing what they want to do. [where] says which tab it lives on; [words] are other names for it. */
class Command(val label: String, val where: String, val words: String = "", val run: () -> Unit)

/** Typing narrows the list; every word typed must appear in the name, place or other names. */
fun matchCommands(all: List<Command>, query: String, limit: Int = 8): List<Command> {
    val terms = query.lowercase().split(' ', ',').filter { it.isNotBlank() }
    if (terms.isEmpty()) return all.take(limit)
    return all.filter { c ->
        val hay = (c.label + " " + c.where + " " + c.words).lowercase()
        terms.all { it in hay }
    }.sortedBy { c -> if (terms.all { it in c.label.lowercase() }) 0 else 1 }.take(limit)
}

@Composable
fun CommandSearchDialog(commands: List<Command>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shown = matchCommands(commands, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What do you want to do?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Try: table, spelling, page number, bold") },
                )
                if (shown.isEmpty()) Text("Nothing matches. Try another word.", style = MaterialTheme.typography.bodyMedium)
                shown.forEach { c ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onDismiss(); c.run() }.padding(vertical = 8.dp),
                    ) {
                        Text(c.label, style = MaterialTheme.typography.titleSmall)
                        Text(c.where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
