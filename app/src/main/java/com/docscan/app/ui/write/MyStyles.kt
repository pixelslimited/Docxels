package com.docscan.app.ui.write

import android.content.Context

/** Formatting the person saved under a name (font, size, weight, colours) to apply again to any text. Kept on the phone. */
object MyStyles {
    class Style(val name: String, val attrs: List<CharAttr>)

    private const val FILE = "my_styles"
    private const val KEY = "styles"

    /** One style per line: name, then each attribute as a short code, separated by tabs. Tabs and newlines are removed from names. */
    fun encode(styles: List<Style>): String = styles.joinToString("\n") { st ->
        (listOf(st.name.replace(Regex("[\\t\\n\\r]"), " ")) + st.attrs.mapNotNull(::code)).joinToString("\t")
    }

    fun decode(text: String): List<Style> = text.split("\n").filter { it.isNotBlank() }.map { line ->
        val parts = line.split("\t")
        Style(parts[0], parts.drop(1).mapNotNull(::attr))
    }

    private fun code(a: CharAttr): String? = when (a) {
        CharAttr.Bold -> "B"
        CharAttr.Italic -> "I"
        CharAttr.Underline -> "U"
        CharAttr.Strike -> "S"
        is CharAttr.Color -> "C:" + a.rgb
        is CharAttr.Highlight -> "H:" + a.rgb
        is CharAttr.Size -> "Z:" + a.pt
        is CharAttr.Font -> "F:" + a.name.replace(Regex("[\\t\\n\\r]"), " ")
        else -> null
    }

    private fun attr(c: String): CharAttr? = when {
        c == "B" -> CharAttr.Bold
        c == "I" -> CharAttr.Italic
        c == "U" -> CharAttr.Underline
        c == "S" -> CharAttr.Strike
        c.startsWith("C:") -> c.substring(2).toIntOrNull()?.let { CharAttr.Color(it) }
        c.startsWith("H:") -> c.substring(2).toIntOrNull()?.let { CharAttr.Highlight(it) }
        c.startsWith("Z:") -> c.substring(2).toFloatOrNull()?.let { CharAttr.Size(it) }
        c.startsWith("F:") -> c.substring(2).takeIf { it.isNotBlank() }?.let { CharAttr.Font(it) }
        else -> null
    }

    fun load(context: Context): List<Style> =
        decode(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: "")

    fun save(context: Context, styles: List<Style>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, encode(styles)).apply()
    }
}
