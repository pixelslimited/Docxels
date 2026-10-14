package com.docscan.app.core.word

enum class CaseMode(val label: String) {
    SENTENCE("Sentence case"),
    LOWER("lowercase"),
    UPPER("UPPERCASE"),
    CAPITALIZE("Capitalize Each Word"),
    TOGGLE("tOGGLE cASE"),
}

/** Word's "Change Case". Never changes the length of the text, so formatting can stay where it is. */
object TextCase {
    fun apply(s: String, mode: CaseMode): String = when (mode) {
        CaseMode.LOWER -> s.lowercase()
        CaseMode.UPPER -> s.uppercase()
        CaseMode.TOGGLE -> buildString { s.forEach { append(if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar()) } }
        CaseMode.CAPITALIZE -> {
            val out = StringBuilder()
            var startOfWord = true
            for (ch in s) {
                out.append(if (startOfWord && ch.isLetter()) ch.uppercaseChar() else if (ch.isLetter()) ch.lowercaseChar() else ch)
                startOfWord = !(ch.isLetterOrDigit() || ch == '\'' || ch == '’')
            }
            out.toString()
        }
        CaseMode.SENTENCE -> {
            val out = StringBuilder()
            var capNext = true
            for (ch in s) {
                if (ch.isLetter()) {
                    out.append(if (capNext) ch.uppercaseChar() else ch.lowercaseChar())
                    capNext = false
                } else {
                    out.append(ch)
                    if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') capNext = true
                }
            }
            out.toString()
        }
    }.let { r -> if (r.length == s.length) r else s } // e.g. German ß: keep the length so spans stay aligned
}

/** Everything the Word Count dialog and the status bar show. */
data class WordStats(val words: Int, val charsNoSpaces: Int, val charsWithSpaces: Int, val paragraphs: Int, val lines: Int) {
    companion object {
        /** [text] is the editor text: paragraphs separated by '\n', object placeholders already removed. */
        fun of(text: String, lines: Int): WordStats {
            val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
            val paras = text.split('\n').count { it.isNotBlank() }
            return WordStats(words, text.count { !it.isWhitespace() }, text.count { it != '\n' }, paras, lines)
        }
    }
}

/** A heading in the document, for the Navigation pane. [offset] is the character position of its paragraph. */
data class OutlineItem(val level: Int, val title: String, val offset: Int)

object Outline {
    /** [paragraphs] are the editor paragraphs in order (one per '\n'-separated line) with their styles. */
    fun build(texts: List<String>, styles: List<ParaStyle>): List<OutlineItem> {
        val out = ArrayList<OutlineItem>()
        var offset = 0
        for (i in texts.indices) {
            val level = when (styles.getOrNull(i)) {
                ParaStyle.TITLE -> 0
                ParaStyle.H1 -> 1
                ParaStyle.H2 -> 2
                ParaStyle.H3 -> 3
                else -> -1
            }
            val t = texts[i].trim()
            if (level >= 0 && t.isNotEmpty()) out += OutlineItem(level, t.take(80), offset)
            offset += texts[i].length + 1
        }
        return out
    }
}

object FormatDate {
    val PATTERNS = listOf("dd/MM/yyyy", "d MMMM yyyy", "EEEE, d MMMM yyyy", "yyyy-MM-dd", "MMMM d, yyyy", "HH:mm", "d MMMM yyyy HH:mm")
}

object Symbols {
    val COMMON = listOf(
        "©", "®", "™", "§", "¶", "†", "‡", "•", "…", "–", "—", "‘", "’", "“", "”", "«", "»",
        "°", "±", "×", "÷", "≈", "≠", "≤", "≥", "∞", "√", "π", "µ", "Ω", "½", "¼", "¾", "⅓", "²", "³",
        "₦", "€", "£", "¥", "¢", "$", "✓", "✗", "→", "←", "↑", "↓", "↔", "★", "☆", "♥", "☎", "✉",
    )
    const val NON_BREAKING_SPACE = " "
    const val NON_BREAKING_HYPHEN = "‑"
}
