package com.docscan.app.core

/** Finds phone numbers in text, so a double tap on one can open the dialer. */
object PhoneNumbers {
    class Found(val start: Int, val end: Int, val number: String)

    private val group = Regex("""\+?\d+""")
    private const val MAX_DIGITS = 13   // +234 803 123 4567; two neighbouring numbers never merge into one
    private const val MIN_DIGITS = 7

    fun all(text: CharSequence): List<Found> {
        val groups = group.findAll(text).toList()
        val out = ArrayList<Found>()
        var i = 0
        while (i < groups.size) {
            var j = i
            var digits = groups[i].value.count { it.isDigit() }
            while (j + 1 < groups.size) {
                val gap = text.subSequence(groups[j].range.last + 1, groups[j + 1].range.first).toString()
                val add = groups[j + 1].value.count { it.isDigit() }
                val joinable = gap.length in 1..2 && gap.all { it in " -.()" } && !groups[j + 1].value.startsWith("+")
                if (!joinable || digits + add > MAX_DIGITS) break
                digits += add
                j++
            }
            if (digits in MIN_DIGITS..15) {
                val s = groups[i].range.first
                val e = groups[j].range.last + 1
                val plus = if (text[s] == '+') "+" else ""
                out += Found(s, e, plus + text.subSequence(s, e).filter { it.isDigit() })
            }
            i = j + 1
        }
        return out
    }

    /** The number under [offset] (the character index that was tapped), or null. */
    fun at(text: CharSequence, offset: Int): String? =
        all(text).firstOrNull { offset >= it.start && offset <= it.end }?.number

    fun first(text: CharSequence): String? = all(text).firstOrNull()?.number
}
