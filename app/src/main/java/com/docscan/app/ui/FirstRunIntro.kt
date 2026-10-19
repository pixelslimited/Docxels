package com.docscan.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The three-step welcome shown once, only on a fresh install (people updating from an older build never see it). */
object FirstRunIntro {
    val STEPS: List<Pair<String, String>> = listOf(
        "Scan paper into documents" to "Point the camera at a page. Docxels straightens it, cleans it up and reads the words, all on this phone.",
        "Turn files into Word, PDF or images" to "Open a PDF or Word file, or share a scan, and save it in the format you need. Word files keep their fonts, headers and pictures.",
        "Your documents stay private" to "Everything is encrypted on this phone and nothing is uploaded. You can turn on an app lock, or erase everything, in Settings.",
    )

    /** True on a fresh install that hasn't seen the intro; false once seen or when the app was installed before this feature. */
    fun shouldShow(context: Context, seen: Boolean): Boolean {
        if (seen) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
        return info.firstInstallTime == info.lastUpdateTime
    }
}

@Composable
fun FirstRunIntroDialog(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val (title, body) = FirstRunIntro.STEPS[step]
    val last = step == FirstRunIntro.STEPS.lastIndex
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                Text(
                    "${step + 1} of ${FirstRunIntro.STEPS.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (last) onDone() else step++ }) { Text(if (last) "Get started" else "Next") } },
        dismissButton = { if (!last) TextButton(onClick = onDone) { Text("Skip") } },
    )
}
