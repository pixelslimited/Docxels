package com.docscan.app.core

/** Recognises a standalone page number such as "3", "Page 3", "3 of 10" or "- 3 -". */
object PageNumberParser {
    private val regex = Regex(
        """^\s*[-–—]?\s*(?:page|pg\.?|p\.?)?\s*(\d{1,3})(?:\s*(?:of|/)\s*\d{1,3})?\s*[-–—]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(line: String): Int? = regex.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
}

/** Decides a page order from detected page numbers. */
object ArrangeLogic {
    /**
     * Sorts pages by [numbers] (page id -> printed page number).
     * Pages without a number keep their slot; numbered pages are sorted into the remaining slots.
     * Returns the input unchanged when fewer than half of the pages have a number or numbers repeat,
     * because a guess based on thin evidence would be worse than the capture order.
     */
    fun order(ids: List<String>, numbers: Map<String, Int>): List<String> {
        val numbered = ids.filter { it in numbers }
        if (numbered.size < 2 || numbered.size < (ids.size + 1) / 2) return ids
        if (numbered.map { numbers.getValue(it) }.toSet().size != numbered.size) return ids
        val sorted = numbered.sortedBy { numbers.getValue(it) }.iterator()
        return ids.map { if (it in numbers) sorted.next() else it }
    }
}
