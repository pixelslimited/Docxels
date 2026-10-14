package com.docscan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.update.UpdateInfo
import com.docscan.app.update.Updater
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val apk: java.io.File) : UpdateState
    data class Failed(val text: String) : UpdateState
}

@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    val updater = remember { Updater(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            state = runCatching { updater.check() }.fold(
                onSuccess = { if (it == null) UpdateState.UpToDate else UpdateState.Available(it) },
                onFailure = { UpdateState.Failed("Could not check. Make sure you are online and try again.") },
            )
        }
    }

    fun download(info: UpdateInfo) {
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            state = runCatching { updater.download(info) { p -> state = UpdateState.Downloading(info, p) } }.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed(it.message?.takeIf { m -> m.startsWith("The download") } ?: "The download failed. Try again.") },
            )
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val s = state
        Text(
            "Updates come from Pixels Limited's own release page. Only a small version file is read; nothing about you or your documents is sent. Every download is checked against a fingerprint, and Android only installs it if it is signed by the same key as this app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (s) {
            UpdateState.Idle -> OutlinedButton(onClick = ::check) { Text("Check for updates") }
            UpdateState.Checking -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking…") }
            UpdateState.UpToDate -> {
                Text("You have the latest version.")
                OutlinedButton(onClick = ::check) { Text("Check again") }
            }
            is UpdateState.Available -> {
                Text("Version ${s.info.versionName} is available (${s.info.size / 1_048_576} MB).", style = MaterialTheme.typography.titleSmall)
                if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { download(s.info) }) { Text("Download and install") }
            }
            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(s.progress * 100).toInt()}%")
            }
            is UpdateState.Ready -> {
                Text("Downloaded and verified.")
                Button(onClick = { if (updater.ensureInstallPermission()) updater.install(s.apk) }) { Text("Install now") }
                Text(
                    "If Android asks, allow Docxels to install apps, then come back and tap Install now. Your documents are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is UpdateState.Failed -> {
                Text(s.text, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = ::check) { Text("Try again") }
            }
        }
    }
}
