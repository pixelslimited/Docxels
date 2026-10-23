package com.docscan.app.ui.write

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
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

private class Proposal(val title: String, val text: String, val start: Int, val end: Int, val original: String, val replaces: Boolean, val insert: Boolean = false)

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
    var writeOpen by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("Let AI decide") }
    // Saved letterheads give the company name; saved signatures give the signer, company and position. Only items with
    // their details filled in are used. "Auto" lets the AI choose from what the user writes.
    val letterheads = remember {
        com.docscan.app.store.SavedAssets(context, "letterheads").list()
            .filter { it.name != "Untitled" && !it.name.matches(Regex("Letterhead \\d+")) }.map { it.name }.take(10)
    }
    val signers = remember {
        com.docscan.app.store.SavedAssets(context, "signatures").list()
            .filter { it.name != "Untitled" && it.company.isNotBlank() && it.position.isNotBlank() }.take(10)
    }
    var company by remember { mutableStateOf(AUTO) }
    var signer by remember { mutableStateOf(AUTO) }
    var brief by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    fun work(label: String, replaces: Boolean, task: String, instruction: String = "", q: String = "") {
        val sc = controller.assistantScope()
        if (sc.text.isBlank()) { say("Write or select some text first."); return }
        com.docscan.app.cloud.Analytics.track("assistant:$task")
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

    fun writeWithAi() {
        if (brief.isBlank()) return
        com.docscan.app.cloud.Analytics.track("assistant:write")
        val instruction = buildString {
            append("Kind of document: ").append(kind).append('.')
            when {
                company == AUTO && letterheads.isNotEmpty() ->
                    append(" Saved companies (pick the one the description is about; if unclear use the first): ").append(letterheads.joinToString(" | ")).append('.')
                company != AUTO && company != NONE -> append(" Company (from the saved letterhead): ").append(company).append('.')
            }
            when {
                signer == AUTO && signers.isNotEmpty() ->
                    append(" Saved signers (name; position; company; pick the one the description is about, or a signer of the chosen company): ")
                        .append(signers.joinToString(" | ") { "${it.name}; ${it.position}; ${it.company}" }).append('.')
                signer != AUTO && signer != NONE -> signers.firstOrNull { it.name == signer }?.let {
                    append(" Signed by (from the saved signature): ").append(it.name).append("; ").append(it.position).append("; ").append(it.company).append('.')
                }
            }
            if ((company == NONE || letterheads.isEmpty()) && from.isNotBlank()) append(" From: ").append(from.trim().take(300)).append('.')
            if (to.isNotBlank()) append(" To: ").append(to.trim().take(300)).append('.')
        }
        scope.launch {
            busy = true
            try {
                val out = cloud.ai("write", brief.trim(), instruction).trim()
                proposal = Proposal("Your draft", out, 0, 0, "", replaces = false, insert = true)
            } catch (e: CloudException) {
                say(e.message ?: "The assistant could not write it.")
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
        Act("Write with AI (Pro)", "Describe it and get a finished letter, email, application or report") { writeOpen = true },
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

    com.docscan.app.ui.CenteredSheet(onDismiss) {
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

    if (writeOpen) AlertDialog(
        onDismissRequest = { writeOpen = false },
        title = { Text("Write with AI") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Kind of document", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf("Let AI decide", "Formal letter", "Email", "Application", "Complaint", "Request", "Resignation", "Recommendation", "Report", "Proposal", "Speech").forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k) })
                    }
                }
                if (letterheads.isNotEmpty()) {
                    Text("Company (Auto picks it from what you write)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = company == AUTO, onClick = { company = AUTO }, label = { Text("Auto") })
                        letterheads.forEach { n -> FilterChip(selected = company == n, onClick = { company = n }, label = { Text(n) }) }
                        FilterChip(selected = company == NONE, onClick = { company = NONE }, label = { Text("None") })
                    }
                }
                if (signers.isNotEmpty()) {
                    Text("Signed by (Auto picks it from what you write)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = signer == AUTO, onClick = { signer = AUTO }, label = { Text("Auto") })
                        signers.forEach { g -> FilterChip(selected = signer == g.name, onClick = { signer = g.name }, label = { Text(g.name) }) }
                        FilterChip(selected = signer == NONE, onClick = { signer = NONE }, label = { Text("None") })
                    }
                }
                OutlinedTextField(brief, { brief = it }, label = { Text("What should it say?") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (company == NONE || letterheads.isEmpty()) OutlinedTextField(from, { from = it }, label = { Text("From: your name and address (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(to, { to = it }, label = { Text("To: recipient and address (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("The tone is chosen for you. Check names, dates and amounts before you send. Missing details appear in [square brackets]. Add your signature picture on the blank lines above your name.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = brief.isNotBlank() && !busy, onClick = { writeOpen = false; writeWithAi() }) { Text("Write it") } },
        dismissButton = { TextButton(onClick = { writeOpen = false }) { Text("Cancel") } },
    )

    if (account) AccountDialog(cloud) { account = false }

    proposal?.let { p ->
        AlertDialog(
            onDismissRequest = { proposal = null },
            title = { Text(p.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(p.text) } },
            confirmButton = {
                if (p.insert) TextButton(onClick = { controller.insertText(p.text); proposal = null; onDismiss() }) { Text("Insert into document") }
                else if (p.replaces) TextButton(onClick = {
                    // Only replace if the text underneath has not changed since it was sent.
                    if (controller.textBetween(p.start, p.end).replace(CHIP_CHAR, ' ') == p.original) { controller.replaceRange(p.start, p.end, p.text); proposal = null; onDismiss() }
                    else { say("The text changed while the assistant was working. Run it again."); proposal = null }
                }) { Text("Replace") }
                else TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Docxels", p.text))
                    say("Copied."); proposal = null
                }) { Text("Copy") }
            },
            dismissButton = {
                if (p.insert) Row {
                    TextButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Docxels", p.text))
                        say("Copied."); proposal = null
                    }) { Text("Copy") }
                    TextButton(onClick = { proposal = null }) { Text("Discard") }
                } else TextButton(onClick = { proposal = null }) { Text(if (p.replaces) "Keep mine" else "Close") }
            },
        )
    }
}

private const val AUTO = "\u0000auto"
private const val NONE = "\u0000none"
