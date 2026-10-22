package com.docscan.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.docscan.app.update.Note
import com.docscan.app.update.NoteTier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.update.UpdateInfo
import com.docscan.app.update.Updater
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
        val installed = updater.installedCode()
        Text("Installed: build $installed (version 0.2.$installed)", style = MaterialTheme.typography.titleSmall)
        // What this build brought, and what the one before it brought, from the list that ships inside the app.
        val mine = remember { Note.parse(runCatching { context.assets.open("whatsnew.txt").bufferedReader().use { it.readText() } }.getOrDefault("")) }
        val builds = mine.map { it.code }.distinct().sortedDescending()
        val updating = s is UpdateState.Available || s is UpdateState.Downloading || s is UpdateState.Ready
        if (!updating) builds.getOrNull(0)?.let { c -> NotesCard("New in this update · build $c", mine.filter { it.code == c }) }
        if (!updating) builds.getOrNull(1)?.let { c -> NotesCard("Before that · build $c", mine.filter { it.code == c }) }
        when (s) {
            UpdateState.Idle -> OutlinedButton(onClick = ::check) { Text("Check for updates") }
            UpdateState.Checking -> { com.docscan.app.ui.SkeletonBox(Modifier.fillMaxWidth().height(18.dp)); com.docscan.app.ui.SkeletonBox(Modifier.fillMaxWidth(0.6f).height(18.dp)); Text("Checking…") }
            UpdateState.UpToDate -> {
                Text("You have the latest version.")
                OutlinedButton(onClick = ::check) { Text("Check again") }
            }
            is UpdateState.Available -> {
                val small = s.info.patchFor(updater.installedCode())
                val sizeText = if (small != null) "download about ${fmtSize(small.size)}, only the changes" else "download ${fmtSize(s.info.size)}"
                Text("Build ${s.info.versionCode} (version ${s.info.versionName}) is available ($sizeText).", style = MaterialTheme.typography.titleSmall)
                s.info.whatsNew(updater.installedCode()).let { if (it.isNotEmpty()) NotesCard("Coming in this update", it) }
                Button(onClick = { download(s.info) }) { Text("Download and install") }
            }
            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(s.progress * 100).toInt()}%")
            }
            is UpdateState.Ready -> {
                s.info.whatsNew(updater.installedCode()).let { if (it.isNotEmpty()) NotesCard("Coming in this update", it) }
                Text(if (updater.lastUsedPatch) "Downloaded only the changes and verified." else "Downloaded and verified.")
                if (!updater.lastUsedPatch && updater.lastPatchNote.isNotBlank()) {
                    Text(updater.lastPatchNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

private fun fmtSize(bytes: Long): String =
    if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${(bytes / 1024).coerceAtLeast(1)} KB"

/** A card of features grouped by who gets them: everyone (Free), members (Pro) and fixes. */
@Composable
private fun NotesCard(heading: String, notes: List<Note>) {
    if (notes.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        NoteTier.entries.forEach { tier ->
            val rows = notes.filter { it.tier == tier }
            if (rows.isEmpty()) return@forEach
            Text(
                tier.label.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = if (tier == NoteTier.PRO) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 6.dp).clip(CircleShape)
                    .background(if (tier == NoteTier.PRO) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            rows.forEach { n ->
                Column(Modifier.padding(top = 2.dp)) {
                    Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (n.detail.isNotEmpty()) Text(n.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
