package com.docscan.app.core.word

enum class FormKind { CHECK, TEXT, DATE, CHOICE }

/** A fill-in field found in the text: where it starts and ends (end is exclusive), what it is, and its choices. */
class FormToken(val start: Int, val end: Int, val kind: FormKind, val options: List<String> = emptyList(), val checked: Boolean = false)

/**
 * Fill-in fields that are plain text, so they print, save in .docx and open in Word exactly as they look:
 *  - ☐ / ☑ is a check box (tap to tick or untick);
 *  - "[ Type here ]" is a text field (tap to select it, then type over it);
 *  - "[ Pick a date ]" is a date field (tap to choose a date, which replaces it);
 *  - "[ Choose one: Yes | No | Maybe ]" is a drop-down (tap to choose; the choice replaces it).
 */
object FormTokens {
    const val BOX = "☐"
    const val TICKED = "☑"
    const val TEXT = "[ Type here ]"
    const val DATE = "[ Pick a date ]"
    private const val CHOOSE = "Choose one:"
    private val bracket = Regex("""\[ [^\[\]\n]{1,100} \]""")

    fun choice(options: List<String>): String = "[ $CHOOSE ${options.joinToString(" | ")} ]"

    /** Splits what a person typed ("Yes, No, Maybe") into clean options. */
    fun parseOptions(typed: String): List<String> =
        typed.split(',', ';', '\n', '|').map { it.replace(Regex("[\\[\\]]"), "").trim() }.filter { it.isNotEmpty() }.distinct().take(20)

    /** The field at (or just before) [off], or null. */
    fun at(text: CharSequence, off: Int): FormToken? {
        if (text.isEmpty()) return null
        for (i in intArrayOf(off, off - 1)) {
            if (i in text.indices) {
                val c = text[i].toString()
                if (c == BOX || c == TICKED) return FormToken(i, i + 1, FormKind.CHECK, checked = c == TICKED)
            }
        }
        // Brackets never span a line, so only the line around the tap needs looking at.
        var ls = off.coerceIn(0, text.length)
        while (ls > 0 && text[ls - 1] != '\n') ls--
        var le = off.coerceIn(0, text.length)
        while (le < text.length && text[le] != '\n') le++
        val line = text.subSequence(ls, le).toString()
        for (m in bracket.findAll(line)) {
            val s = ls + m.range.first
            val e = ls + m.range.last + 1
            if (off in s..e) {
                val inner = m.value.substring(2, m.value.length - 2)
                return when {
                    inner == DATE.substring(2, DATE.length - 2) -> FormToken(s, e, FormKind.DATE)
                    inner.startsWith(CHOOSE) -> {
                        val opts = parseOptions(inner.removePrefix(CHOOSE))
                        if (opts.isEmpty()) null else FormToken(s, e, FormKind.CHOICE, opts)
                    }
                    else -> FormToken(s, e, FormKind.TEXT)
                }
            }
        }
        return null
    }
}
