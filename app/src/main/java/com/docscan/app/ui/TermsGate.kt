package com.docscan.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The terms and privacy policy a new person must accept before the app can be used. People who already had the app
 * before this screen existed are not asked: they are marked as having accepted the version that was in force.
 */
object TermsGate {
    /** Change this when the terms or privacy policy change in a way people must agree to again. */
    const val VERSION = "2026-10"
    const val SITE = "https://docxels.pixelslimited.ng"

    /** True when the screen has to be shown: nothing accepted yet, on a fresh install. */
    fun required(context: Context, accepted: String): Boolean {
        if (accepted.isNotEmpty()) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return true
        return info.firstInstallTime == info.lastUpdateTime
    }
}

@Composable
fun TermsGateScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    var agreed by remember { mutableStateOf(false) }
    fun open(path: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TermsGate.SITE + path)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Before you start", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Docxels is made by Pixels Limited. To use the app, please read and accept our Terms of use and Privacy policy.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(onClick = { open("/terms.html") }, modifier = Modifier.fillMaxWidth()) { Text("Read the Terms of use") }
            OutlinedButton(onClick = { open("/privacy.html") }, modifier = Modifier.fillMaxWidth()) { Text("Read the Privacy policy") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text("I have read and accept the Terms of use and the Privacy policy.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            OutlinedButton(onClick = { (context as? Activity)?.finishAffinity() }, modifier = Modifier.fillMaxWidth()) { Text("Exit") }
            Text("Docxels cannot be used until these are accepted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
