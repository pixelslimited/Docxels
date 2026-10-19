package com.docscan.app.core.word

/**
 * Where a table breaks across pages. The same rule as the PDF export (`WordPdf`): a row that does not fit in what is
 * left of the page starts a new page, the header row repeats at the top of each new page, and a row taller than a page
 * is cut to one page. All numbers are in one unit (pixels in the editor); offsets are measured from the table's top.
 */
object TablePaging {
    /** A page break before row [row]: the page's content ends at [gapStart]; the next page's content starts at gapStart + gap. */
    class Break(val row: Int, val gapStart: Float, val headerAt: Float?)

    class Plan(val breaks: List<Break>, val rowTops: List<Float>, val rowHeights: List<Float>, val height: Float)

    /**
     * @param heights natural height of each row
     * @param startY how far down the page the table's top is (0 = top of a page)
     * @param pageH height of the text area of one page
     * @param gap blank space between one page's text area and the next page's
     */
    fun plan(heights: List<Float>, header: Boolean, startY: Float, pageH: Float, gap: Float): Plan {
        val breaks = ArrayList<Break>()
        val tops = ArrayList<Float>()
        val used = ArrayList<Float>()
        var y = startY.coerceAtLeast(0f)
        var off = 0f
        for ((r, natural) in heights.withIndex()) {
            val h = minOf(natural, pageH)
            if (y + h > pageH && y > 0f) {
                val gapStart = off + (pageH - y)
                off = gapStart + gap
                y = 0f
                var headerAt: Float? = null
                if (header && r > 0) {
                    headerAt = off
                    val hh = minOf(heights[0], pageH)
                    off += hh
                    y += hh
                }
                breaks += Break(r, gapStart, headerAt)
            }
            tops += off
            used += h
            off += h
            y += h
        }
        return Plan(breaks, tops, used, off)
    }
}
