package com.docscan.app.ui.write

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docscan.app.core.word.FormTokens
import com.docscan.app.ui.TextInputDialog

/** Asks for the choices of a new drop-down ("Yes, No, Maybe") and hands back the text to put in the document. */
@Composable
fun FormChoiceBuilder(onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        title = "Drop-down choices (separate with commas)",
        initial = "Yes, No",
        confirmLabel = "Insert",
        onConfirm = { typed ->
            val options = FormTokens.parseOptions(typed)
            if (options.size >= 2) onInsert(FormTokens.choice(options)) else onDismiss()
        },
        onDismiss = onDismiss,
        maxLength = 300,
    )
}

/** The choices of a tapped drop-down; the one picked replaces the field. */
@Composable
fun FormChoicePicker(options: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose one") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Text(o, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 14.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A calendar; the date picked replaces the field, written like "21 October 2026". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDatePicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { ms ->
                        // The calendar hands back midnight UTC, so format in UTC or the day can shift.
                        val f = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
                        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        onPick(f.format(java.util.Date(ms)))
                    }
                },
            ) { Text("Use this date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
