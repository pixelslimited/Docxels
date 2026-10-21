package com.docscan.app.core.word

/**
 * Header and footer text may have several lines and the placeholders {page} (this page's number) and {pages}
 * (how many pages there are), which Word stores as PAGE and NUMPAGES fields.
 */
object HeaderFooter {
    const val PAGE = "{page}"
    const val PAGES = "{pages}"

    fun fill(text: String, pageNo: Int, total: Int): String = text.replace(PAGE, pageNo.toString()).replace(PAGES, total.toString())

    fun lines(text: String): List<String> = text.split('\n').map { it.trimEnd() }.filter { it.isNotEmpty() }

    /** Splits a line into plain text and placeholder pieces, keeping the order: "Page {page} of {pages}" -> Page , {page},  of , {pages}. */
    fun pieces(line: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        var start = 0
        while (i < line.length) {
            val tok = when {
                line.startsWith(PAGES, i) -> PAGES
                line.startsWith(PAGE, i) -> PAGE
                else -> null
            }
            if (tok != null) {
                if (i > start) out += line.substring(start, i)
                out += tok
                i += tok.length
                start = i
            } else i++
        }
        if (start < line.length) out += line.substring(start)
        return out
    }

    fun usesTotal(page: PageSetup): Boolean =
        listOf(page.headerText, page.footerText, page.firstHeaderText, page.firstFooterText, page.evenHeaderText, page.evenFooterText).any { it.contains(PAGES) }
}
