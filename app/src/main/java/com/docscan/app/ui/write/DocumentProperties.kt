package com.docscan.app.ui.write

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Document properties: what is written into the file's own details (title, author, subject, keywords). "Remove
 * personal data" blanks the author so a shared copy does not carry a name.
 */
@Composable
fun DocumentPropertiesDialog(
    title: String,
    author: String,
    subject: String,
    keywords: String,
    onSave: (title: String, author: String, subject: String, keywords: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var t by remember { mutableStateOf(title) }
    var a by remember { mutableStateOf(author) }
    var s by remember { mutableStateOf(subject) }
    var k by remember { mutableStateOf(keywords) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document properties") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(a, { a = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(s, { s = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(k, { k = it }, label = { Text("Keywords") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { a = ""; s = ""; k = "" }) { Text("Remove personal data") }
                Text(
                    "The author is saved inside the .docx file. Remove it before sharing a copy if you do not want your name in it.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t, a, s, k) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
