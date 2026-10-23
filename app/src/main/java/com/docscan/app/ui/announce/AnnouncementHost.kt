package com.docscan.app.ui.announce

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.docscan.app.AppContainer
import com.docscan.app.cloud.Announcement
import com.docscan.app.update.UpdateInfo
import com.docscan.app.update.Updater
import kotlinx.coroutines.delay

/** How long "Remind me later" waits before the new-version notice returns. */
private const val REMIND_LATER_MS = 4 * 60 * 60 * 1000L

private sealed interface Pending {
    data class Message(val a: Announcement, val image: Bitmap?) : Pending
    data class NewVersion(val info: UpdateInfo) : Pending
    data object Profile : Pending
}

/**
 * Shows one pop-up at a time on the Home screen: the welcome (once, after the first-run intro), messages sent from the
 * dashboard, and a notice when a newer build is published. Does nothing, and goes online for nothing, when the
 * "Announcements and update notices" switch in Settings is off.
 */
@Composable
fun AnnouncementHost(container: AppContainer, onOpenUpdates: () -> Unit, onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val manager = container.announcements
    val plan by container.cloud.planState.collectAsState()
    val session by container.cloud.session.collectAsState()
    var enabled by remember { mutableStateOf(container.ui.announcements) }
    var introDone by remember { mutableStateOf(container.ui.introSeen) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var refreshed by remember { mutableStateOf(0) }

    // The first-run intro sits above this screen; the welcome waits until it is closed.
    LaunchedEffect(introDone) { while (!introDone) { delay(1000); introDone = container.ui.introSeen } }
    LaunchedEffect(Unit) { while (true) { delay(2000); enabled = container.ui.announcements } }

    LaunchedEffect(enabled) {
        if (!enabled || !manager.due()) return@LaunchedEffect
        manager.checked()
        manager.refresh()
        refreshed++
    }

    // A new build is noticed as soon as the app is opened or comes back to the front, and every 10 minutes while it is open.
    var resumes by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(enabled, resumes) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            runCatching { Updater(context.applicationContext).check() }.onSuccess { update = it }
            refreshed++ // lets a "Remind me later" notice come back once its time has passed
            delay(10 * 60 * 1000L)
        }
    }

    // Android 13+ needs permission to show the "new version" notification; asked once, after the first-run intro.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled, introDone) {
        if (!enabled || !introDone || manager.askedNotifications || Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        manager.askedNotifications = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    LaunchedEffect(enabled, introDone, plan, refreshed, update?.versionCode, pending == null, session?.fullName) {
        if (!enabled || pending != null) return@LaunchedEffect
        val a = manager.next(plan?.pro == true, introDone)
        val today = System.currentTimeMillis() / 86_400_000L
        val needProfile = session != null && session?.fullName.isNullOrBlank() && introDone && manager.profileNudgeDay != today
        val u = update?.takeIf { it.versionCode != manager.noticedUpdate && System.currentTimeMillis() >= manager.updateRemindAt }
        pending = when {
            a != null -> Pending.Message(a, a.imagePath?.let { manager.image(it) })
            needProfile -> { manager.profileNudgeDay = today; Pending.Profile }
            u != null -> Pending.NewVersion(u)
            else -> null
        }
    }

    when (val p = pending) {
        is Pending.Message -> MessageDialog(
            title = p.a.title, body = p.a.body, image = p.image, button = p.a.buttonLabel,
            onButton = {
                p.a.buttonUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                manager.markSeen(p.a.id); pending = null
            },
            onClose = { manager.markSeen(p.a.id); pending = null },
        )
        is Pending.NewVersion -> MessageDialog(
            title = "A new version is ready",
            body = "Docxels ${p.info.versionName} is available. Tap Update now: Docxels downloads it, checks it and opens the installer. Your documents are kept.",
            image = null, button = "Update now",
            onButton = { manager.noticedUpdate = p.info.versionCode; update = null; pending = null; onOpenUpdates() },
            onClose = { manager.noticedUpdate = p.info.versionCode; update = null; pending = null },
            laterLabel = "Remind me later",
            onLater = { manager.updateRemindAt = System.currentTimeMillis() + REMIND_LATER_MS; pending = null },
        )
        Pending.Profile -> MessageDialog(
            title = "Complete your profile",
            body = "Add your first and last name in Settings, Profile. Your name then shows on Home and is written as the owner of the documents you create.",
            image = null, button = "Open Profile",
            onButton = { pending = null; onOpenProfile() },
            onClose = { pending = null },
        )
        null -> Unit
    }
}

@Composable
private fun MessageDialog(
    title: String, body: String, image: Bitmap?, button: String?, onButton: () -> Unit, onClose: () -> Unit,
    laterLabel: String? = null, onLater: () -> Unit = {},
) {
    // Only the X closes it: tapping outside or pressing back leaves the message up, so it is never missed by accident.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (image != null) {
                    Image(
                        image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(top = 4.dp))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (laterLabel != null) TextButton(onClick = onLater) { Text(laterLabel) }
                    if (button != null) Button(onClick = onButton) { Text(button) }
                }
            }
        }
    }
}
