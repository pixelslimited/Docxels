package com.docscan.app.ui.write

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docscan.app.cloud.Cloud
import com.docscan.app.cloud.CloudException
import com.docscan.app.ui.cloud.AccountDialog
import kotlinx.coroutines.launch

private class Proposal(val title: String, val text: String, val start: Int, val end: Int, val original: String, val replaces: Boolean)

/** The writing assistant: rewrite, fix, shorten, translate, clean scanned text, check writing, summarise and ask. Runs only on tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(controller: EditorController, cloud: Cloud, onDismiss: () -> Unit, say: (String) -> Unit) {
    val session by cloud.session.collectAsState()
    if (session == null) { AccountDialog(cloud, onDismiss); return }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<Proposal?>(null) }
    var languagePick by remember { mutableStateOf(false) }
    var askOpen by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(false) }

    fun work(label: String, replaces: Boolean, task: String, instruction: String = "", q: String = "") {
        val sc = controller.assistantScope()
        if (sc.text.isBlank()) { say("Write or select some text first."); return }
        scope.launch {
            busy = true
            try {
                val out = cloud.ai(task, sc.text, instruction, q).trim()
                proposal = Proposal(label, out, sc.start, sc.end, sc.text, replaces)
            } catch (e: CloudException) {
                say(e.message ?: "The assistant could not answer.")
                if (e.limitReached) account = true
            } catch (e: Exception) {
                say("Could not reach the assistant. Check your connection.")
            }
            busy = false
        }
    }

    fun checkWriting() {
        val sc = controller.assistantScope()
        if (sc.text.isBlank()) { say("Write or select some text first."); return }
        scope.launch {
            busy = true
            try {
                val issues = cloud.checkWriting(sc.text)
                controller.markIssues(sc.start, issues)
                say(if (issues.isEmpty()) "No problems found." else "${issues.size} suggestion${if (issues.size == 1) "" else "s"} underlined. Tap one to fix it.")
                onDismiss()
            } catch (e: CloudException) {
                say(e.message ?: "The assistant could not check the text.")
                if (e.limitReached) account = true
            } catch (e: Exception) {
                say("Could not reach the assistant. Check your connection.")
            }
            busy = false
        }
    }

    class Act(val label: String, val hint: String, val run: () -> Unit)
    val acts = listOf(
        Act("Check writing", "Underline spelling, grammar and clarity problems; tap one to fix it") { checkWriting() },
        Act("Fix spelling and grammar", "Correct mistakes only, keep your wording") { work("Corrected text", true, "fix") },
        Act("Improve writing", "Clearer and smoother, same meaning") { work("Improved text", true, "rewrite", "Improve clarity, flow and tone. Keep the meaning.") },
        Act("Make it shorter", "Same facts, fewer words") { work("Shorter text", true, "shorten") },
        Act("Make it longer", "A little more detail") { work("Longer text", true, "expand") },
        Act("Translate…", "English, Hausa, Yoruba, Igbo, French, Arabic") { languagePick = true },
        Act("Clean scanned text", "Fix recognition mistakes in text from a scan") { work("Cleaned text", true, "clean_ocr") },
        Act("Summarise", "Short overview of the document or selection") { work("Summary", false, "summarise") },
        Act("Ask about this document", "Answers only from the text") { askOpen = true },
        Act("Account and plan", "Sign out or upgrade") { account = true },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Black.copy(alpha = 0.7f)) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Assistant", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text(
                if (controller.assistantScope().isSelection) "Works on your selected text." else "Works on the whole document. Select text to work on just that part.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
            acts.forEach { a ->
                ListItem(
                    headlineContent = { Text(a.label) },
                    supportingContent = { Text(a.hint) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = !busy) { a.run() },
                )
            }
        }
    }

    if (languagePick) AlertDialog(
        onDismissRequest = { languagePick = false },
        title = { Text("Translate into") },
        text = {
            Column {
                listOf("English", "Hausa", "Yoruba", "Igbo", "French", "Arabic").forEach { l ->
                    Text(l, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { languagePick = false; work("Translation ($l)", true, "translate", l) }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { languagePick = false }) { Text("Cancel") } },
    )

    if (askOpen) AlertDialog(
        onDismissRequest = { askOpen = false },
        title = { Text("Ask about this document") },
        text = { OutlinedTextField(question, { question = it }, label = { Text("Your question") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = question.isNotBlank(), onClick = { askOpen = false; work("Answer", false, "ask", q = question) }) { Text("Ask") } },
        dismissButton = { TextButton(onClick = { askOpen = false }) { Text("Cancel") } },
    )

    if (account) AccountDialog(cloud) { account = false }

    proposal?.let { p ->
        AlertDialog(
            onDismissRequest = { proposal = null },
            title = { Text(p.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(p.text) } },
            confirmButton = {
                if (p.replaces) TextButton(onClick = {
                    // Only replace if the text underneath has not changed since it was sent.
                    if (controller.textBetween(p.start, p.end).replace(CHIP_CHAR, ' ') == p.original) { controller.replaceRange(p.start, p.end, p.text); proposal = null; onDismiss() }
                    else { say("The text changed while the assistant was working. Run it again."); proposal = null }
                }) { Text("Replace") }
                else TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Docxels", p.text))
                    say("Copied."); proposal = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { proposal = null }) { Text(if (p.replaces) "Keep mine" else "Close") } },
        )
    }
}
