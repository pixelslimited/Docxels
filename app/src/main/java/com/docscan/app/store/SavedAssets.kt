package com.docscan.app.store

import android.content.Context
import com.docscan.app.security.SecureFiles
import java.io.File

/**
 * Letterheads and signatures the person keeps on the phone to insert at will. Pictures are stored encrypted at rest
 * (through [SecureFiles]) in the app's private folder and never leave the device.
 */
class SavedAssets(context: Context, kind: String) {
    /**
     * [name] is the company for a letterhead and the person for a signature. A signature also has the [company] and
     * [position] of that person; the writing assistant uses these to know who a letter is from and who signs it.
     * Items kept by older versions have no details until the person adds them.
     */
    class Item(val id: String, val name: String, val file: File, val company: String = "", val position: String = "")

    private val dir = File(context.applicationContext.filesDir, kind).apply { mkdirs() }

    fun list(): List<Item> = (dir.listFiles().orEmpty())
        .filter { it.name.endsWith(".img") }
        .sortedByDescending { it.name }
        .map { f ->
            val base = f.name.removeSuffix(".img")
            val (company, position) = readDetails(base.substringBefore('_'))
            Item(base, base.substringAfter('_', "Untitled").ifBlank { "Untitled" }, f, company, position)
        }

    fun add(name: String, bytes: ByteArray, company: String = "", position: String = ""): Item {
        val id = System.currentTimeMillis().toString()
        val clean = cleanName(name)
        val file = File(dir, "${id}_$clean.img")
        SecureFiles.write(file, bytes)
        writeDetails(id, company, position)
        return Item("${id}_$clean", clean, file, company.trim(), position.trim())
    }

    /** Changes the name, company and position of a kept item in one go. */
    fun update(item: Item, name: String, company: String, position: String): Item {
        val renamed = rename(item, name)
        writeDetails(renamed.id.substringBefore('_'), company, position)
        return Item(renamed.id, renamed.name, renamed.file, company.trim(), position.trim())
    }

    private fun metaFile(stamp: String) = File(dir, "$stamp.meta")

    private fun readDetails(stamp: String): Pair<String, String> = runCatching {
        val j = org.json.JSONObject(String(SecureFiles.read(metaFile(stamp)) ?: error("no details"), Charsets.UTF_8))
        j.optString("company") to j.optString("position")
    }.getOrDefault("" to "")

    private fun writeDetails(stamp: String, company: String, position: String) {
        val j = org.json.JSONObject().put("company", cleanText(company)).put("position", cleanText(position))
        SecureFiles.write(metaFile(stamp), j.toString().toByteArray(Charsets.UTF_8))
    }

    /** Gives a kept item a new name (the picture and its place in the list stay the same). */
    fun rename(item: Item, name: String): Item {
        val clean = cleanName(name)
        val stamp = item.id.substringBefore('_')
        val target = File(dir, "${stamp}_$clean.img")
        if (target != item.file && !target.exists()) item.file.renameTo(target)
        return Item("${stamp}_$clean", clean, if (target.exists()) target else item.file)
    }

    fun bytes(item: Item): ByteArray? = runCatching { SecureFiles.read(item.file) }.getOrNull()

    fun delete(item: Item) {
        SecureFiles.wipe(item.file)
        metaFile(item.id.substringBefore('_')).takeIf { it.exists() }?.let { SecureFiles.wipe(it) }
    }

    companion object {
        fun cleanText(text: String): String = text.replace(Regex("[\\r\\n]+"), " ").trim().take(60)

        fun cleanName(name: String): String = name.replace(Regex("[^\\p{L}\\p{N} .-]"), "").trim().take(40).ifBlank { "Untitled" }
    }
}
