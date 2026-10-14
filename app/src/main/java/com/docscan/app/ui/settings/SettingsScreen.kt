package com.docscan.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.docscan.app.AppContainer
import com.docscan.app.security.Authenticator
import com.docscan.app.security.LockPolicy
import com.docscan.app.ui.ConfirmDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val security = container.security
    val protect by security.protect.collectAsState()
    var lockOn by remember { mutableStateOf(security.lockEnabled) }
    var timeout by remember { mutableStateOf(security.lockTimeoutMs) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) { usage = withContext(Dispatchers.IO) { storageSummary(context) } }

    fun say(text: String) = scope.launch { snackbar.showSnackbar(text) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }

            Section("Privacy and security")
            ToggleRow(
                title = "App lock",
                body = "Ask for your fingerprint, face or screen lock before showing documents.",
                checked = lockOn,
                onChange = { want ->
                    if (activity == null) return@ToggleRow
                    if (!want) {
                        // Turning the lock off needs the same proof as using the app.
                        Authenticator.prompt(activity, "Turn off app lock", "Confirm it's you", onSuccess = {
                            security.setLockEnabled(false)
                            lockOn = false
                        })
                    } else if (!Authenticator.available(activity)) {
                        say("Set a screen lock or fingerprint in your phone's settings first.")
                    } else {
                        Authenticator.prompt(activity, "Turn on app lock", "Confirm it's you", onSuccess = {
                            security.setLockEnabled(true)
                            lockOn = true
                        })
                    }
                },
            )
            if (lockOn) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Lock again", style = MaterialTheme.typography.bodyLarge)
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        TextButton(onClick = { menu = true }) {
                            Text(LockPolicy.TIMEOUTS.firstOrNull { it.first == timeout }?.second ?: "Immediately")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            LockPolicy.TIMEOUTS.forEach { (ms, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    security.lockTimeoutMs = ms
                                    timeout = ms
                                    menu = false
                                })
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            ToggleRow(
                title = "Hide screen contents",
                body = "Blocks screenshots and hides the app in the recent-apps list.",
                checked = protect,
                onChange = { security.protectScreen = it },
            )
            HorizontalDivider()
            InfoRow(
                "How your documents are protected",
                "Pages are encrypted on this phone with a key held in the Android Keystore. Docxels has no internet " +
                    "permission, so nothing is uploaded. Backups of app data are switched off. This keeps casual access " +
                    "and stolen-storage attacks out; it can't protect a phone that is already unlocked, rooted or infected.",
            )

            Section("Storage")
            InfoRow("Used by Docxels", usage ?: "Calculating…")
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Clear temporary files")
            }

            Section("About")
            InfoRow("Docxels", "Version ${versionName(context)} · by Pixels Limited")
            InfoRow(
                "Privacy",
                "Scanning, text recognition and exports all run on this phone. Files you export or share leave the app only where you send them.",
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear temporary files?",
            message = "Removes exported copies kept for sharing. Your documents are not touched.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                scope.launch {
                    withContext(Dispatchers.IO) { container.exporter.clearCache() }
                    usage = withContext(Dispatchers.IO) { storageSummary(context) }
                    say("Temporary files cleared")
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

private fun storageSummary(context: Context): String {
    fun size(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0L
    val docs = size(File(context.filesDir, "docs"))
    val cache = size(context.cacheDir)
    fun fmt(b: Long) = when {
        b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576.0)
        b >= 1024 -> String.format("%d KB", b / 1024)
        else -> "$b B"
    }
    return "Documents ${fmt(docs)} · temporary files ${fmt(cache)}"
}
