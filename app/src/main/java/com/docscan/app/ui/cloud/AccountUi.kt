package com.docscan.app.ui.cloud

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.cloud.Cloud
import com.docscan.app.cloud.Plan
import kotlinx.coroutines.launch

/** Sign in or create an account; once signed in, shows the plan and the Pro upgrade. Used from Settings and the editor's Assistant. */
@Composable
fun AccountDialog(cloud: Cloud, onDismiss: () -> Unit) {
    val session by cloud.session.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }

    LaunchedEffect(session?.email) {
        plan = null
        if (session != null) runCatching { cloud.plan() }.onSuccess { plan = it; cloud.refreshPlan() }
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true; note = null
            runCatching { block() }.onFailure { note = it.message ?: "Something went wrong." }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "Your Docxels account" else "Account") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val s = session
                if (s == null) {
                    Text("An account lets you use the writing assistant. Your documents stay on this phone; text is sent only when you tap an assistant action.", style = MaterialTheme.typography.bodySmall)
                    if (creating) {
                        OutlinedTextField(first, { first = it.take(30) }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(last, { last = it.take(30) }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    com.docscan.app.ui.PasswordField(password, { password = it }, "Password", Modifier.fillMaxWidth())
                    if (creating) com.docscan.app.ui.PasswordStrength(password)
                    if (!creating) TextButton(enabled = !busy && email.contains("@"), onClick = {
                        run { cloud.requestPasswordReset(email); note = "If that email has an account, a reset link is on its way. Open it on any device, choose a new password, then sign in here." }
                    }) { Text("Forgot password?") }
                } else {
                    Text(s.email, style = MaterialTheme.typography.titleMedium)
                    val p = plan
                    Text(
                        when { p == null -> "Checking your plan…"; p.pro -> "Pro" + (p.proUntil?.let { " until ${it.take(10)}" } ?: ""); else -> "Free plan: a few assistant actions a day" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (plan?.pro == false) Button(enabled = !busy, onClick = {
                        run { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cloud.checkoutUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Upgrade to Pro") }
                    if (plan?.pro == false) Text("After paying, come back here; Pro turns on within a minute.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        cloud.signOut()
                        android.widget.Toast.makeText(context, "Logged out. See you back soon!", android.widget.Toast.LENGTH_LONG).show()
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                }
                note?.let { Text(it, color = if (it.startsWith("If that email")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (session == null && creating) Button(enabled = !busy && email.contains("@") && first.isNotBlank() && last.isNotBlank() && com.docscan.app.ui.PasswordPolicy.ok(password), onClick = {
                run {
                    if (cloud.signUp(email, password, first, last)) onDismiss()
                    else note = "Check your email and tap the link to confirm, then sign in."
                }
            }) { Text("Create account") }
            else if (session == null) Button(enabled = !busy && email.contains("@") && password.length >= 8, onClick = {
                run { cloud.signIn(email, password); onDismiss() }
            }) { Text("Sign in") }
            else TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (session == null) TextButton(enabled = !busy, onClick = { creating = !creating; note = null }) { Text(if (creating) "I have an account" else "Create account") }
        },
    )
}
