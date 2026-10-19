package com.docscan.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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

/** "Delete all my data": wipes everything Docxels keeps on this phone, after the person types DELETE. */
object DeleteAllData {
    const val WORD = "DELETE"

    /** True when what was typed is the confirmation word (any case, spaces around it ignored). */
    fun confirmed(typed: String): Boolean = typed.trim().equals(WORD, ignoreCase = true)

    /**
     * Android's own "clear app data": removes documents, letterheads, signatures, settings, the encryption key and
     * the search index, then closes the app. Files the person exported to Pictures, Downloads or elsewhere are not
     * touched. Returns false if Android refused, in which case nothing was deleted.
     */
    fun run(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
}

@Composable
fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all my data?") },
        text = {
            Column {
                Text(
                    "This permanently erases every document, letterhead, signature and setting inside Docxels on this phone, " +
                        "then closes the app. It cannot be undone. Files you already exported or shared stay where you saved them.",
                )
                Text(
                    "Type ${DeleteAllData.WORD} to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = DeleteAllData.confirmed(typed)) {
                Text("Delete everything", color = if (DeleteAllData.confirmed(typed)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
