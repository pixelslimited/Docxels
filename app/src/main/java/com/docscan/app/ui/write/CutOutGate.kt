package com.docscan.app.ui.write

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.update.CutOutModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Remove background works best with a small helper model that is downloaded once (4.6 MB) instead of shipping inside the
 * app. [run] does the action straight away when the model is already here, or when the person said "Use basic" earlier in
 * this session; otherwise it asks first and then does the action.
 */
class CutOutGate internal constructor(private val context: Context, private val scope: CoroutineScope) {
    private var pending by mutableStateOf<(() -> Unit)?>(null)
    private var progress by mutableFloatStateOf(-1f)
    private var error by mutableStateOf<String?>(null)

    fun run(action: () -> Unit) {
        if (CutOutModel.ready(context) || declined) action() else { error = null; progress = -1f; pending = action }
    }

    @Composable
    fun Dialog() {
        val action = pending ?: return
        val downloading = progress >= 0f
        AlertDialog(
            onDismissRequest = { if (!downloading) pending = null },
            title = { Text("Better cut-outs") },
            text = {
                Column {
                    Text(
                        "Remove background cuts best with a small helper (4.6 MB) that is downloaded once from the Docxels " +
                            "site and kept on this phone. Your pictures never leave the phone. Without it, plain backgrounds " +
                            "still work but busy pictures may be cut wrongly.",
                    )
                    if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = {
                    progress = 0f
                    error = null
                    scope.launch {
                        val problem = CutOutModel.download(context) { progress = it }
                        progress = -1f
                        if (problem == null) { pending = null; action() } else error = problem
                    }
                }) { Text(if (error != null) "Try again" else "Download") }
            },
            dismissButton = {
                TextButton(enabled = !downloading, onClick = { declined = true; pending = null; action() }) { Text("Use basic") }
            },
        )
    }

    private companion object { var declined = false }
}

@Composable
fun rememberCutOutGate(): CutOutGate {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { CutOutGate(context, scope) }
}
