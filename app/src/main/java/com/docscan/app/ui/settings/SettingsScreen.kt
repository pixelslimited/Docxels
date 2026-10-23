package com.docscan.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Where a pop-up button wants Settings to open. */
enum class SettingsGoal { UPDATE, PROFILE }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: (() -> Unit)? = null, goal: SettingsGoal? = null, onGoalHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val security = container.security
    val protect by security.protect.collectAsState()
    var lockOn by remember { mutableStateOf(security.lockEnabled) }
    var timeout by remember { mutableStateOf(security.lockTimeoutMs) }
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
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
            Modifier.fillMaxSize().padding(inner).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = if (onBack == null) 12.dp else 0.dp))
            }

            var tab by remember { mutableIntStateOf(0) }
            // A pop-up button ("Update now", "Open Profile") lands here: open App settings and scroll to the right place.
            androidx.compose.runtime.LaunchedEffect(goal) { if (goal != null) tab = 1 }
            androidx.compose.material3.TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.padding(top = 8.dp)) {
                listOf("Resources", "App settings", "About").forEachIndexed { i, label ->
                    androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
                }
            }

            if (tab == 0) {
                Text(
                    "Things you keep and reuse in your documents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            Section("Letterheads")
            SavedAssetsPanel(signature = false, say = { say(it) })
            Section("Signatures")
            SavedAssetsPanel(signature = true, say = { say(it) })

            }

            if (tab == 1) {
            Section("Appearance")
            val mode by container.ui.theme.collectAsState()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.docscan.app.security.ThemeMode.entries.forEach { m ->
                    com.docscan.app.ui.kit.FilterPill(
                        m.name.lowercase().replaceFirstChar { it.uppercase() },
                        mode == m,
                        { container.ui.setTheme(m) },
                    )
                }
            }

            Text("Theme colour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            val accent by container.ui.accent.collectAsState()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.docscan.app.security.Accent.entries.forEach { ac ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { container.ui.setAccent(ac) }) {
                        Box(
                            Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(ac.a), androidx.compose.ui.graphics.Color(ac.b))))
                                .then(if (accent == ac) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, androidx.compose.foundation.shape.CircleShape) else Modifier),
                        )
                        Text(ac.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Section("Exporting scans")
            Text("The export sheet starts with this format.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var fmt by remember { mutableStateOf(container.ui.exportFormat) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.docscan.app.export.DocFormat.entries.forEach { f ->
                    com.docscan.app.ui.kit.FilterPill(f.label, fmt == f.name, { fmt = f.name; container.ui.exportFormat = f.name })
                }
            }
            var a4 by remember { mutableStateOf(container.ui.exportA4) }
            ToggleRow(title = "Fit PDF pages to A4", body = "Off keeps each scan's own proportions.", checked = a4, onChange = { a4 = it; container.ui.exportA4 = it })

            var inside by remember { mutableStateOf(container.ui.searchInside) }
            ToggleRow(
                title = "Search inside documents",
                body = "Reads the words in your scans (on this phone) and Word documents so the Documents search can find them. Uses some battery the first time.",
                checked = inside,
                onChange = { inside = it; container.ui.searchInside = it; if (!it) container.searchIndex.clear() },
            )

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
                            security.changeLock(false)
                            lockOn = false
                        })
                    } else if (!Authenticator.available(activity)) {
                        say("Set a screen lock or fingerprint in your phone's settings first.")
                    } else {
                        Authenticator.prompt(activity, "Turn on app lock", "Confirm it's you", onSuccess = {
                            security.changeLock(true)
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
                body = "Off by default, so you can take screenshots. Turn on to block screenshots and hide the app in the recent-apps list.",
                checked = protect,
                onChange = { security.protectScreen = it },
            )
            HorizontalDivider()
            InfoRow(
                "How your documents are protected",
                "Pages are encrypted on this phone with a key held in the Android Keystore. Docxels never uploads your documents in the background. " +
                    "It goes online only for the update check you start, and, if you sign in, for the assistant action you tap (the text you chose is sent for that one request) and for payments. Backups of app data are switched off. This keeps casual access " +
                    "and stolen-storage attacks out; it can't protect a phone that is already unlocked, rooted or infected.",
            )

            Section("Device check")
            val findings = remember { com.docscan.app.security.DeviceIntegrity.check(context) }
            if (findings.isEmpty()) {
                InfoRow("No warning signs", "No root tools or debugging were detected. This check is a hint, not a guarantee.")
            } else {
                findings.forEach { InfoRow("Warning", it) }
                Text(
                    "On a rooted or modified phone, other apps may be able to read Docxels data. Avoid keeping sensitive documents here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Section("Storage")
            InfoRow("Used by Docxels", usage ?: "Calculating…")
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Clear temporary files")
            }
            OutlinedButton(onClick = {
                // With the app lock on, prove it's you before offering to erase everything.
                if (lockOn && activity != null) {
                    Authenticator.prompt(activity, "Delete all my data", "Confirm it's you", onSuccess = { confirmDeleteAll = true })
                } else {
                    confirmDeleteAll = true
                }
            }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Delete all my data", color = MaterialTheme.colorScheme.error)
            }

            Section("Profile")
            val cloudSession by container.cloud.session.collectAsState()
            val planNow by container.cloud.planState.collectAsState()
            androidx.compose.runtime.LaunchedEffect(cloudSession?.email) { container.cloud.refreshPlan() }
            val profileAt = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            androidx.compose.runtime.LaunchedEffect(goal, tab) {
                if (goal == SettingsGoal.PROFILE && tab == 1) { kotlinx.coroutines.delay(350); profileAt.bringIntoView(); onGoalHandled() }
            }
            Box(Modifier.bringIntoViewRequester(profileAt)) { ProfileEditor(container.cloud, cloudSession, planNow) }

            Section("Account")
            var accountOpen by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { accountOpen = true }) { Text(cloudSession?.email ?: "Sign in for the writing assistant") }
            if (accountOpen) com.docscan.app.ui.cloud.AccountDialog(container.cloud) { accountOpen = false }

            Section("App updates")
            val updateAt = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            androidx.compose.runtime.LaunchedEffect(goal, tab) {
                if (goal == SettingsGoal.UPDATE && tab == 1) { kotlinx.coroutines.delay(350); updateAt.bringIntoView(); onGoalHandled() }
            }
            Box(Modifier.bringIntoViewRequester(updateAt)) { UpdatePanel(autoStart = goal == SettingsGoal.UPDATE) }
            var announce by remember { mutableStateOf(container.ui.announcements) }
            ToggleRow(
                "Announcements and update notices",
                "Docxels checks for news once a day, and for new versions when you open it and about every 15 minutes in the background, then shows a pop-up or a notification. It also sends anonymous statistics: a random install code, build number, Android version, phone model, which tools were used and error codes. No name, email, documents or file names. Turn off to stay fully offline and send nothing.",
                announce,
            ) { announce = it; container.ui.announcements = it }

            }

            if (tab == 2) AboutPanel(context)
        }
    }

    if (confirmDeleteAll) {
        DeleteAllDialog(
            onConfirm = {
                confirmDeleteAll = false
                if (!DeleteAllData.run(context)) say("Android would not delete the data. Try Settings > Apps > Docxels > Storage > Clear data.")
            },
            onDismiss = { confirmDeleteAll = false },
        )
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
internal fun InfoRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun versionName(context: Context): String =
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
