package com.docscan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docscan.app.cloud.Cloud
import com.docscan.app.cloud.Plan
import com.docscan.app.cloud.Session
import kotlinx.coroutines.launch

/** Settings, Profile: first and last name (saved on the account, shown on Home, written as the owner of new documents) and the plan. */
@Composable
fun ProfileEditor(cloud: Cloud, session: Session?, plan: Plan?) {
    if (session == null) {
        Text("Sign in under Account to set your name and see your plan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val scope = rememberCoroutineScope()
    var first by remember(session.email) { mutableStateOf(session.first) }
    var last by remember(session.email) { mutableStateOf(session.last) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when { plan == null -> "Plan: checking…"; plan.pro -> "Plan: Pro" + (plan.proUntil?.let { " until ${it.take(10)}" } ?: ""); else -> "Plan: Free" },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(session.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(first, { first = it.take(30); note = null }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(last, { last = it.take(30); note = null }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && first.isNotBlank() && last.isNotBlank() && (first.trim() != session.first || last.trim() != session.last), onClick = {
            scope.launch {
                busy = true; note = null
                runCatching { cloud.saveProfile(first, last) }
                    .onSuccess { failed = false; note = "Saved." }
                    .onFailure { failed = true; note = it.message ?: "Could not save. Try again." }
                busy = false
            }
        }) { Text("Save") }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        Text("Your name shows on Home and is written as the owner of new documents (for example Mukhtar Sabiu (Docxels)). Signing out clears it from this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
