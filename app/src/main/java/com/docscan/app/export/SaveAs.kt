package com.docscan.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

/** "Save as…": the person picks the folder and the file name on the phone (Android's own file picker). */
object SaveAs {
    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> Mime.PDF
        "docx" -> Mime.DOCX
        "xlsx" -> Mime.XLSX
        "txt" -> "text/plain"
        "rtf" -> "application/rtf"
        "jpg", "jpeg" -> Mime.JPEG
        "png" -> "image/png"
        "zip" -> Mime.ZIP
        else -> "application/octet-stream"
    }

    /** Copies [file] into the document the person chose; returns true when it was written. */
    fun copyTo(context: Context, file: File, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    }.getOrDefault(false)

    /** Copies every file into the folder the person chose; returns how many were written. */
    fun copyAllTo(context: Context, files: List<File>, tree: Uri): Int {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        for (f in files) {
            val doc = runCatching { DocumentsContract.createDocument(context.contentResolver, parent, mimeOf(f), f.name) }.getOrNull() ?: continue
            if (copyTo(context, f, doc)) n++
        }
        return n
    }

    private class CreateAny : ActivityResultContract<File, Uri?>() {
        override fun createIntent(context: Context, input: File): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mimeOf(input)).putExtra(Intent.EXTRA_TITLE, input.name)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
    }

    /**
     * Returns a function that asks where to save the given files. One file opens the "save as" picker with its name filled in;
     * several files ask for a folder. [say] gets the outcome in words.
     */
    @Composable
    fun rememberSaveAs(say: (String) -> Unit): (List<File>) -> Unit {
        val context = LocalContext.current
        val pending = remember { arrayOfNulls<List<File>>(1) }
        val one = rememberLauncherForActivityResult(CreateAny()) { uri ->
            val f = pending[0]?.firstOrNull()
            if (uri != null && f != null) say(if (copyTo(context, f, uri)) "Saved." else "Could not save the file there.")
        }
        val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            val files = pending[0]
            if (tree != null && files != null) {
                val n = copyAllTo(context, files, tree)
                say(if (n == files.size) "Saved ${files.size} files." else "Saved $n of ${files.size} files.")
            }
        }
        return { files ->
            if (files.isNotEmpty()) {
                pending[0] = files
                if (files.size == 1) one.launch(files[0]) else many.launch(null)
            }
        }
    }
}
