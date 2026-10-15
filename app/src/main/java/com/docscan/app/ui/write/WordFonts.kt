package com.docscan.app.ui.write

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface

/**
 * Fonts that ship inside Docxels so the font chosen for a document really changes the text, on every phone.
 * Several are metric-compatible stand-ins for Microsoft fonts (Carlito for Calibri, Caladea for Cambria, Liberation
 * for Arial / Times New Roman / Courier New), so a Word document keeps its line breaks and page breaks.
 */
object WordFonts {
    class Family(val display: String, val base: String, val aliases: List<String>, val generic: String, val styles: Set<Int> = setOf(0, 1, 2, 3)) {
        val lowered = aliases.map { it.lowercase() }
    }

    /** What is built in, in the order shown in the picker. */
    val families = listOf(
        Family("Carlito", "Carlito", listOf("Calibri", "Aptos", "Segoe UI", "Candara"), "sans-serif"),
        Family("Caladea", "Caladea", listOf("Cambria"), "serif"),
        Family("Liberation Sans", "LiberationSans", listOf("Arial", "Helvetica", "Verdana", "Tahoma", "Trebuchet MS", "Century Gothic", "Arial Narrow", "Calibri Light"), "sans-serif"),
        Family("Liberation Serif", "LiberationSerif", listOf("Times New Roman", "Times", "Georgia", "Garamond", "Palatino Linotype", "Book Antiqua", "Baskerville", "Bookman Old Style"), "serif"),
        Family("Liberation Mono", "LiberationMono", listOf("Courier New", "Courier", "Consolas", "Lucida Console"), "monospace"),
        Family("Lora", "Lora", emptyList(), "serif"),
        Family("Poppins", "Poppins", emptyList(), "sans-serif"),
        Family("DejaVu Sans", "DejaVuSans", emptyList(), "sans-serif"),
        Family("DejaVu Serif", "DejaVuSerif", emptyList(), "serif", setOf(0, 1)),
        Family("DejaVu Sans Mono", "DejaVuSansMono", emptyList(), "monospace"),
    )

    /** Names shown in the "Built into Docxels" part of the picker. */
    val builtInNames: List<String> get() = families.map { it.display }

    private var assets: AssetManager? = null
    private val cache = HashMap<String, Typeface?>()

    private var customDir: java.io.File? = null

    fun init(context: Context) {
        assets = context.applicationContext.assets
        customDir = java.io.File(context.applicationContext.filesDir, "docfonts").apply { mkdirs() }
    }

    private fun customFile(name: String, style: Int) =
        customDir?.let { java.io.File(it, name.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + style + ".ttf") }

    /** Keeps a font that came inside a Word file so text in that font shows in it, now and the next time the document opens. */
    fun addCustom(name: String, style: Int, bytes: ByteArray) {
        val f = customFile(name, style and 3) ?: return
        runCatching {
            if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes)
            synchronized(cache) { cache.remove("custom:" + f.name) }
        }
    }

    private fun custom(name: String, style: Int): Typeface? {
        val st = style and 3
        for (s in listOf(st, 0)) {
            val f = customFile(name, s)?.takeIf { it.exists() } ?: continue
            val tf = synchronized(cache) { cache.getOrPut("custom:" + f.name) { runCatching { Typeface.createFromFile(f) }.getOrNull() } } ?: continue
            return if (s == st) tf else Typeface.create(tf, st)
        }
        return null
    }

    fun find(name: String): Family? {
        val n = name.trim().lowercase()
        return families.firstOrNull { it.display.lowercase() == n || n in it.lowered }
    }

    private val suffix = arrayOf("Regular", "Bold", "Italic", "BoldItalic")

    /** The typeface for a font name and a [Typeface] style (0 normal, 1 bold, 2 italic, 3 both), or null if not built in. */
    fun typeface(name: String, style: Int): Typeface? {
        custom(name, style)?.let { return it }
        val fam = find(name) ?: return null
        val am = assets ?: return null
        val st = style and 3
        val real = if (st in fam.styles) st else 0
        val key = fam.base + real
        val tf = synchronized(cache) {
            cache.getOrPut(key) { runCatching { Typeface.createFromAsset(am, "fonts/${fam.base}-${suffix[real]}.ttf") }.getOrNull() }
        } ?: return null
        return if (real == st) tf else Typeface.create(tf, st)
    }
}
