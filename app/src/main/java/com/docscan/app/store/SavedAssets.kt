package com.docscan.app.store

import android.content.Context
import com.docscan.app.security.SecureFiles
import java.io.File

/**
 * Letterheads and signatures the person keeps on the phone to insert at will. Pictures are stored encrypted at rest
 * (through [SecureFiles]) in the app's private folder and never leave the device.
 */
class SavedAssets(context: Context, kind: String) {
    class Item(val id: String, val name: String, val file: File)

    private val dir = File(context.applicationContext.filesDir, kind).apply { mkdirs() }

    fun list(): List<Item> = (dir.listFiles().orEmpty())
        .filter { it.name.endsWith(".img") }
        .sortedByDescending { it.name }
        .map { f ->
            val base = f.name.removeSuffix(".img")
            Item(base, base.substringAfter('_', "Untitled").ifBlank { "Untitled" }, f)
        }

    fun add(name: String, bytes: ByteArray): Item {
        val id = System.currentTimeMillis().toString()
        val clean = cleanName(name)
        val file = File(dir, "${id}_$clean.img")
        SecureFiles.write(file, bytes)
        return Item("${id}_$clean", clean, file)
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

    fun delete(item: Item) = SecureFiles.wipe(item.file)

    companion object {
        fun cleanName(name: String): String = name.replace(Regex("[^\\p{L}\\p{N} .-]"), "").trim().take(40).ifBlank { "Untitled" }
    }
}
