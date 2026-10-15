package com.docscan.app.core.word

/** Turns the internal font name a PDF carries ("ABCDEF+BookmanOldStyle-BoldItalic") into the family Word would use. */
object FontNames {
    private val styleWords = Regex("(?i)(bold|italic|oblique|regular|roman|normal|semibold|demibold|medium|light|black|heavy|condensed|narrow|book|ital|bd|it)$")

    /** Null when the name is only a code ("F1", "TT3", "Font12") so the caller can choose by the font's flags instead. */
    fun family(raw: String?): String? {
        var n = (raw ?: return null).substringAfter('+').trim()
        n = n.substringBefore(',')
        n = n.substringBefore('-')
        n = n.replace(Regex("(PSMT|PS|MT)$"), "")
        n = n.replace('_', ' ')
        // BookmanOldStyle -> Bookman Old Style, but keep short all-caps names (e.g. "ITC") together.
        n = n.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim()
        while (true) {
            val m = styleWords.find(n)?.takeIf { it.range.first > 0 && n[it.range.first - 1] == ' ' } ?: break
            n = n.substring(0, m.range.first).trim()
        }
        if (n.length < 3 || n.none { it.isLetter() }) return null
        if (Regex("(?i)^(f|tt|font|ttf|cid|type)\\s*\\d+$").matches(n)) return null
        return n
    }
}
