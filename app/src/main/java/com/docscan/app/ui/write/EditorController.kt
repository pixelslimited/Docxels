package com.docscan.app.ui.write

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.docscan.app.core.word.Align
import com.docscan.app.core.word.Block
import com.docscan.app.core.word.ListKind
import com.docscan.app.core.word.PageSetup
import com.docscan.app.core.word.ParaStyle
import com.docscan.app.core.word.StyleDefaults
import com.docscan.app.core.word.WordDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** EditText that reports cursor / selection moves so the toolbar can show what is active. */
/** How the editor is laid out on screen. */
enum class ViewMode { PAGE, TEXT, MOBILE }

/** What the page view draws around the text: header and footer text and the height of one page of text. */
class PageDecor(
    val headerText: String,
    val footerText: String,
    val pageNumbers: Boolean,
    val differentFirstPage: Boolean,
    val firstHeaderText: String,
    val firstFooterText: String,
    val firstPageNumbers: Boolean,
    val differentOddEven: Boolean,
    val evenHeaderText: String,
    val evenFooterText: String,
    val contentHeightPx: Float,
    val bottomMarginPx: Float,
    val smallTextPx: Float,
    /** Letterhead pictures for page 1 (or the first-page header) and the last page's footer, already decoded. */
    val headerPics: List<DecorPic> = emptyList(),
    val footerPics: List<DecorPic> = emptyList(),
    /** Header pictures for pages after the first (differs from [headerPics] when the first page has its own header). */
    val headerPicsRest: List<DecorPic> = emptyList(),
    val footerPicsFirst: List<DecorPic> = emptyList(),
    val sheetHeightTwips: Int = 0,
    val pxPerTwip: Float = 0f,
    val topMarginPx: Float = 0f,
    /** Height of the coloured strip drawn between one sheet and the next. */
    val deskGapPx: Float = 0f,
    val headerAlign: Int = 1,
    val footerAlign: Int = 1,
) {
    /** Extra vertical room the first line of every page after the first carries: bottom margin + strip + top margin. */
    val pageGapPx: Float get() = bottomMarginPx + deskGapPx + topMarginPx
}

/**
 * Where each page after the first begins and how much blank room (bottom margin + strip + top margin) its first line
 * carries, so pages are really apart on screen. It is only data kept in the text: [ParaSpan.chooseHeight] adds the
 * room, so one piece of code changes each line's height and can undo its own change when the layout hands a line the
 * metrics it already enlarged (two spans changing the same line cannot do that reliably).
 */
class PageGapSpan {
    /** Text offset of each page's first line -> extra blank px above its gap (fills the sheet before it after a hard page break). */
    var starts: Map<Int, Int> = emptyMap()
    var gapPx = 0
}

class DecorPic(val image: com.docscan.app.core.word.PageImage, val bitmap: android.graphics.Bitmap)

class RichEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null

    /** Read mode: the text can be scrolled, selected and copied but not changed. */
    var readOnly = false

    init {
        filters = arrayOf(android.text.InputFilter { _, _, _, dest, dstart, dend -> if (readOnly) dest.subSequence(dstart, dend) else null })
        // Letter widths that scale exactly with the text size, so lines break in the same places at every zoom and in the PDF.
        paintFlags = paintFlags or android.graphics.Paint.SUBPIXEL_TEXT_FLAG or android.graphics.Paint.LINEAR_TEXT_FLAG
    }

    /** Set in the page views; null in the mobile view. */
    var decor: PageDecor? = null
        set(v) {
            field = v
            // Without page view (mobile view) a table is one unbroken block again.
            if (v == null) (text as? android.text.Spanned)?.let { sp -> sp.getSpans(0, sp.length, BlockChipSpan::class.java).forEach { it.startY = -1f } }
            requestLayout()
            invalidate()
        }

    /** Tells whether the table / picture / page-break chip with this index is a hard page break. */
    var isPageBreak: (Int) -> Boolean = { false }
    private val small = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8F9C.toInt() }

    private val gapSpan = PageGapSpan()
    private var spacingFlip = false

    /** Colour of the desk behind the sheets; the strip between pages is drawn in it. */
    var deskColor: Int = 0xFFE4E6EC.toInt()
    private val bandPaint = android.graphics.Paint().apply { style = android.graphics.Paint.Style.FILL }

    /** Dark paper (see [NightPaper]): screen only. The desk strips between pages are drawn after the filter so they keep the desk colour. */
    var nightPaper = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val nightPaint by lazy { android.graphics.Paint().apply { colorFilter = NightPaper.androidFilter() } }

    override fun draw(canvas: android.graphics.Canvas) {
        if (!nightPaper) { super.draw(canvas); return }
        val n = canvas.saveLayer(null, nightPaint)
        super.draw(canvas)
        canvas.restoreToCount(n)
        drawDeskGaps(canvas)
    }

    private fun drawDeskGaps(canvas: android.graphics.Canvas) {
        val d = decor ?: return
        val ys = pageStarts()
        val topPad = totalPaddingTop.toFloat()
        bandPaint.color = deskColor
        for (k in 1 until ys.size) {
            val y = topPad + ys[k] + d.bottomMarginPx
            canvas.drawRect(0f, y, width.toFloat(), y + d.deskGapPx, bandPaint)
        }
    }

    private fun syncGap() {
        val d = decor
        val want = d?.pageGapPx?.toInt() ?: 0
        if (gapSpan.gapPx != want) { gapSpan.gapPx = want; gapSpan.starts = emptyMap() }
        if (d == null) return
        val e = text as? Editable ?: return
        if (e.getSpanStart(gapSpan) != 0 || e.getSpanEnd(gapSpan) != e.length) {
            e.setSpan(gapSpan, 0, e.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    /**
     * Works out where each page starts. Returns the layout Y of the first line of every page (the line that carries
     * the gap, except for page 1) and the text offsets of those lines. The same rule the PDF export uses: a page
     * ends when the next line would not fit in the text height, or after a hard page break.
     */
    private var chipsDirty = false

    private fun tableChipAt(ls: Int): BlockChipSpan? {
        val t = text
        if (ls >= t.length || t[ls] != CHIP_CHAR) return null
        return t.getSpans(ls, ls + 1, BlockChipSpan::class.java).firstOrNull { it.isTable && !it.floating }
    }

    private fun scanPages(): Pair<List<Float>, Map<Int, Int>> {
        val d = decor ?: return listOf(0f) to emptyMap()
        val l = layout ?: return listOf(0f) to emptyMap()
        val t = text
        val has = gapSpan.gapPx.toFloat()
        val full = d.pageGapPx
        val ys = arrayListOf(0f)
        val set = LinkedHashMap<Int, Int>()
        var pageStart = 0f
        var breakNext = false
        for (i in 0 until l.lineCount) {
            val ls = l.getLineStart(i)
            val top = l.getLineTop(i).toFloat()
            val bottom = l.getLineBottom(i).toFloat()
            val g = if (ls in gapSpan.starts) has + (gapSpan.starts[ls] ?: 0) else 0f
            // A table pages itself, row by row, like the PDF export; every other line moves whole.
            val table = tableChipAt(ls)
            val brk = i > 0 && (breakNext || (table == null && top > pageStart && (bottom - g) - pageStart > d.contentHeightPx + 0.5f))
            if (brk) {
                // After a hard page break the sheet is still a whole sheet: blank room fills it down to the bottom margin.
                val fill = if (breakNext) Math.round(maxOf(0f, pageStart + d.contentHeightPx - top)).toFloat() else 0f
                ys += top + fill
                set[ls] = fill.toInt()
                pageStart = top + fill + full
            }
            if (table != null) {
                val startY = if (brk) 0f else (top - pageStart).coerceAtLeast(0f)
                if (kotlin.math.abs(table.startY - startY) > 0.5f || table.pageH != d.contentHeightPx || table.pageGap != full) {
                    table.startY = startY
                    table.pageH = d.contentHeightPx
                    table.pageGap = full
                    chipsDirty = true
                }
                table.paging(paint)?.let { plan ->
                    val contentTop = l.getLineBaseline(i) - plan.height
                    for (b in plan.breaks) {
                        ys += contentTop + b.gapStart
                        pageStart = contentTop + b.gapStart + full
                    }
                }
            }
            // A page break that has just been pushed onto a new sheet by the text above it (that sheet was already full)
            // has done its job: honouring it again would leave a whole blank sheet.
            val flowedToNewSheet = brk && !breakNext
            breakNext = !flowedToNewSheet && ls < t.length && t[ls] == CHIP_CHAR && i < l.lineCount - 1 &&
                t.getSpans(ls, minOf(t.length, ls + 1), BlockChipSpan::class.java).any { isPageBreak(it.index) }
        }
        return ys to set
    }

    /**
     * Justified paragraphs: every wrapped line except the last of its paragraph gets a little extra width in each
     * space so it ends at the right margin (the last line stays left-aligned, as in Word). Returns true when it
     * changed anything, so the caller measures again. Safe to run repeatedly: it works out each line's natural width
     * by taking its own earlier extra off, so a settled layout gives no further changes.
     */
    private fun justifyLines(): Boolean {
        val l = layout ?: return false
        val t = text ?: return false
        val existing = t.getSpans(0, t.length, GapSpan::class.java)
        val used = HashSet<GapSpan>()
        var changed = false
        for (line in 0 until l.lineCount) {
            val s = l.getLineStart(line)
            val e = l.getLineEnd(line)
            if (e <= s) continue
            val para = t.getSpans(s, s + 1, ParaSpan::class.java).firstOrNull() ?: continue
            if (para.attrs.align != Align.JUSTIFY) continue
            if (e >= t.getSpanEnd(para) || t[e - 1] == '\n') continue
            var b = e
            while (b > s && t[b - 1] == ' ') b--
            if (b <= s) continue
            var gaps = 0
            var plain = true
            for (i in s until b) {
                val c = t[i]
                if (c == ' ') gaps++ else if (c == CHIP_CHAR || c == '\t') plain = false
            }
            if (gaps == 0 || !plain) continue
            val room = l.getParagraphRight(line) - l.getParagraphLeft(line)
            val mine = existing.firstOrNull { t.getSpanStart(it) == s && t.getSpanEnd(it) == b }
            val natural = l.getLineMax(line) - (mine?.extra ?: 0f) * gaps
            // 1.5 px short of the margin so rounding can never push the last word onto the next line.
            val want = ((room - 1.5f - natural) / gaps).coerceIn(0f, paint.textSize)
            if (mine != null) {
                used += mine
                if (kotlin.math.abs(mine.extra - want) > 0.3f) {
                    t.removeSpan(mine)
                    if (want > 0.3f) t.setSpan(GapSpan(want), s, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    changed = true
                }
            } else if (want > 0.3f) {
                t.setSpan(GapSpan(want), s, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                changed = true
            }
        }
        for (g in existing) if (g !in used) {
            if (t.getSpanStart(g) >= 0) { t.removeSpan(g); changed = true }
        }
        return changed
    }

    /** Y (in layout coordinates) where each page starts; see [scanPages]. */
    fun pageStarts(): List<Float> = scanPages().first

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        syncGap()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var fits = 0
        while (fits++ < 3 && layout != null && justifyLines()) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val d = decor ?: return
        if (layout == null) return
        var rounds = 0
        while (rounds++ < 8) {
            chipsDirty = false
            val set = scanPages().second
            if (set == gapSpan.starts && !chipsDirty) break
            gapSpan.starts = set
            spacingFlip = !spacingFlip
            setLineSpacing(if (spacingFlip) 0.01f else 0f, 1f) // forces the layout to be rebuilt with the new gaps
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        // Whole sheets only: the page always ends where a printed page would end.
        val ys = scanPages().first
        val lastTextTop = ys.last() + if (ys.size > 1) d.pageGapPx else 0f
        val need = (totalPaddingTop + lastTextTop + d.contentHeightPx + d.bottomMarginPx).toInt()
        if (need > measuredHeight) setMeasuredDimension(measuredWidth, need)
    }

    private val picPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)

    private fun drawPics(canvas: android.graphics.Canvas, pics: List<DecorPic>, behind: Boolean, d: PageDecor, sheetTop: Float, sheetBottom: Float, fromBottom: Boolean) {
        for (p in pics) {
            if (p.image.behind != behind) continue
            val x = p.image.xTwips * d.pxPerTwip
            val y = if (fromBottom) sheetBottom - (d.sheetHeightTwips - p.image.yTwips) * d.pxPerTwip else sheetTop + p.image.yTwips * d.pxPerTwip
            val dst = android.graphics.RectF(x, y, x + p.image.widthTwips * d.pxPerTwip, y + p.image.heightTwips * d.pxPerTwip)
            canvas.drawBitmap(p.bitmap, null, dst, picPaint)
        }
    }

    /** Where each printed sheet sits in this view: its top (px) and its height (px). Used to cut the page view into PDF pages. */
    fun sheetBands(): List<Pair<Float, Float>> {
        val d = decor ?: return emptyList()
        val ys = pageStarts()
        val topPad = totalPaddingTop.toFloat()
        val sheetH = d.sheetHeightTwips * d.pxPerTwip
        return List(ys.size) { k -> (if (k == 0) 0f else topPad + ys[k] + d.bottomMarginPx + d.deskGapPx) to sheetH }
    }

    /** Letterhead pictures on every sheet, each placed against its own sheet's top or bottom edge. */
    private fun drawAllPics(canvas: android.graphics.Canvas, behind: Boolean) {
        val d = decor ?: return
        if (d.headerPics.isEmpty() && d.footerPics.isEmpty() && d.headerPicsRest.isEmpty() && d.footerPicsFirst.isEmpty()) return
        val ys = pageStarts()
        val n = ys.size
        val topPad = totalPaddingTop.toFloat()
        for (k in 0 until n) {
            val sheetTop = if (k == 0) 0f else topPad + ys[k] + d.bottomMarginPx + d.deskGapPx
            val sheetBottom = if (k == n - 1) height.toFloat() else topPad + ys[k + 1] + d.bottomMarginPx
            drawPics(canvas, if (k == 0) d.headerPics else d.headerPicsRest, behind, d, sheetTop, sheetBottom, false)
            drawPics(canvas, if (k == 0) d.footerPicsFirst else d.footerPics, behind, d, sheetTop, sheetBottom, true)
        }
    }

    private fun drawFloating(canvas: android.graphics.Canvas, behind: Boolean) {
        for (h in floatingHits()) {
            if (h.span.behindText != behind) continue
            h.span.drawFloating(canvas, h.left, h.top, paint)
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        drawFloating(canvas, true)
        drawAllPics(canvas, true)
        super.onDraw(canvas)
        drawFloating(canvas, false)
        drawHandles(canvas)
        val d = decor ?: return
        drawAllPics(canvas, false)
        val ys = pageStarts()
        small.textSize = d.smallTextPx
        val topPad = totalPaddingTop.toFloat()
        val n = ys.size
        bandPaint.color = deskColor
        for (k in 0 until n) {
            if (k > 0 && !nightPaper) {
                val y = topPad + ys[k] + d.bottomMarginPx
                canvas.drawRect(0f, y, width.toFloat(), y + d.deskGapPx, bandPaint)
            }
            val sheetTop = if (k == 0) 0f else topPad + ys[k] + d.bottomMarginPx + d.deskGapPx
            val sheetBottom = if (k == n - 1) height.toFloat() else topPad + ys[k + 1] + d.bottomMarginPx
            val first = k == 0 && d.differentFirstPage
            val evenPage = !first && d.differentOddEven && (k + 1) % 2 == 0
            val header = if (first) d.firstHeaderText else if (evenPage) d.evenHeaderText else d.headerText
            val footer = if (first) d.firstFooterText else if (evenPage) d.evenFooterText else d.footerText
            val numbered = if (first) d.firstPageNumbers else d.pageNumbers
            val lineH = d.smallTextPx * 1.3f
            val headLines = com.docscan.app.core.word.HeaderFooter.lines(com.docscan.app.core.word.HeaderFooter.fill(header, k + 1, n))
            val footLines = com.docscan.app.core.word.HeaderFooter.lines(com.docscan.app.core.word.HeaderFooter.fill(footer, k + 1, n))
            val headY = sheetTop + d.topMarginPx / 2f + d.smallTextPx / 3f - (headLines.size - 1) * lineH / 2f
            headLines.forEachIndexed { i, line -> drawAligned(canvas, line, d.headerAlign, headY + i * lineH) }
            val footY = sheetBottom - d.bottomMarginPx / 2f + d.smallTextPx / 3f
            footLines.forEachIndexed { i, line -> drawAligned(canvas, line, d.footerAlign, footY - (footLines.size - 1 - i) * lineH) }
            if (numbered) {
                val label = (k + 1).toString()
                canvas.drawText(label, width - paddingRight - small.measureText(label), footY, small)
            }
        }
    }

    /** One line of header/footer text, left, centred or right between the margins. */
    private fun drawAligned(canvas: android.graphics.Canvas, line: String, align: Int, y: Float) {
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val w = small.measureText(line)
        val x = when (align) { 0 -> left; 2 -> right - w; else -> (left + right - w) / 2f }
        canvas.drawText(line, x, y, small)
    }

    /** Called with the block index when a table / image placeholder is tapped. */
    var onChipTap: ((Int) -> Unit)? = null

    /** Called with (block, row, column, cell rectangle in this view, cell padding) when a table cell is tapped. */
    var onCellTap: ((Int, Int, Int, android.graphics.RectF, Float) -> Unit)? = null

    private fun chipTapped(sp: BlockChipSpan, x: Float, y: Float) {
        val hit = if (sp.isTable) sp.cellAt(x - paddingLeft, y - totalPaddingTop, paint) else null
        val cb = onCellTap
        if (hit != null && cb != null) {
            val r = android.graphics.RectF(hit.rect)
            r.offset(paddingLeft.toFloat(), totalPaddingTop.toFloat())
            cb(sp.index, hit.row, hit.col, r, hit.pad)
        } else onChipTap?.invoke(sp.index)
    }

    /** Called with (start, end, address) when a hyperlink is tapped. */
    var onLinkTap: ((Int, Int, String) -> Unit)? = null
    /** Called with the fill-in field (check box, text, date or drop-down) that was tapped. */
    var onFormTap: ((com.docscan.app.core.word.FormToken) -> Unit)? = null
    /** Called with a phone number when one is double-tapped in the text (or in a table cell while reading). */
    var onDial: ((String) -> Unit)? = null

    private val taps = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
            val t = text
            val off = getOffsetForPosition(e.x, e.y)
            val chip = t.getSpans(maxOf(0, off - 1), minOf(t.length, off + 1), BlockChipSpan::class.java)
                .firstOrNull { t.getSpanStart(it) <= off && off <= t.getSpanEnd(it) }
            val number = if (chip != null && chip.isTable) {
                val hit = chip.cellAt(e.x - paddingLeft, e.y - totalPaddingTop, paint)
                val cell = (chip.block as? com.docscan.app.core.word.Block.Table)?.table?.rows?.getOrNull(hit?.row ?: -1)?.getOrNull(hit?.col ?: -1)
                cell?.let { com.docscan.app.core.PhoneNumbers.first(it.plain()) }
            } else if (chip == null) com.docscan.app.core.PhoneNumbers.at(t, off) else null
            if (number != null) onDial?.invoke(number)
            return false
        }

        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
            onUserTouch?.invoke()
            val t = text
            val off = getOffsetForPosition(e.x, e.y)
            val chip = t.getSpans(maxOf(0, off - 1), minOf(t.length, off + 1), BlockChipSpan::class.java)
                .firstOrNull { t.getSpanStart(it) <= off && off <= t.getSpanEnd(it) }
            if (chip != null) chipTapped(chip, e.x, e.y)
            else {
                val link = t.getSpans(maxOf(0, off - 1), minOf(t.length, off + 1), LinkSpan::class.java)
                    .firstOrNull { t.getSpanStart(it) <= off && off < t.getSpanEnd(it) }
                if (link != null) onLinkTap?.invoke(t.getSpanStart(link), t.getSpanEnd(link), link.url)
                else com.docscan.app.core.word.FormTokens.at(t, off)?.let { onFormTap?.invoke(it) }
            }
            return false
        }
    })

    // -- floating pictures and shapes: drawn over / under the text and dragged with a finger --

    /** Called with (block index, new block) while a floating object is dragged and when it is dropped (final = true). */
    var onFloatMoved: ((Int, com.docscan.app.core.word.Block, Boolean) -> Unit)? = null

    private class Hit(val span: BlockChipSpan, val left: Float, val top: Float, val w: Float, val h: Float)

    private fun floatingHits(): List<Hit> {
        val l = layout ?: return emptyList()
        val t = text
        val k = paint.density * ZOOM / 20f
        val out = ArrayList<Hit>()
        for (span in t.getSpans(0, t.length, BlockChipSpan::class.java)) {
            if (!span.floating) continue
            val (w, h) = span.floatSizePx(paint) ?: continue
            val (ox, oy) = span.offsetTwips()
            val line = l.getLineForOffset(t.getSpanStart(span))
            val pageImg = (span.block as? com.docscan.app.core.word.Block.Image)?.image?.takeIf { it.pageRelative }
            val dec = decor
            if (pageImg != null && dec != null) {
                // Pinned to the sheet the anchor falls on, measured from that sheet's top-left corner.
                val ys = pageStarts()
                val lineTop = l.getLineBottom(line).toFloat() - 1f
                val sheet = ys.indexOfLast { it <= lineTop }.coerceAtLeast(0)
                val sheetTop = if (sheet == 0) 0f else totalPaddingTop + ys[sheet] + dec.bottomMarginPx + dec.deskGapPx
                out += Hit(span, ox * k, sheetTop + oy * k, w.toFloat(), h.toFloat())
                continue
            }
            out += Hit(span, paddingLeft + ox * k, totalPaddingTop + l.getLineTop(line) + oy * k, w.toFloat(), h.toFloat())
        }
        return out
    }

    /** The picture / shape / signature whose handles are shown (the one open in its panel), or -1. */
    var selectedBlock: Int = -1
        set(v) { field = v; invalidate() }

    /** Called with (block index, resized block, final) while a handle is dragged. */
    var onResized: ((Int, com.docscan.app.core.word.Block, Boolean) -> Unit)? = null

    private fun selectedRect(): android.graphics.RectF? {
        if (selectedBlock < 0) return null
        val l = layout ?: return null
        val t = text
        val span = t.getSpans(0, t.length, BlockChipSpan::class.java).firstOrNull { it.index == selectedBlock } ?: return null
        val hit = floatingHits().firstOrNull { it.span === span }
        if (hit != null) return android.graphics.RectF(hit.left, hit.top, hit.left + hit.w, hit.top + hit.h)
        val (w, h) = span.floatSizePx(paint) ?: return null
        val start = span.let { t.getSpanStart(it) }
        val line = l.getLineForOffset(start)
        val left = paddingLeft + l.getPrimaryHorizontal(start)
        val bottom = totalPaddingTop + l.getLineBottom(line).toFloat()
        return android.graphics.RectF(left, bottom - h, left + w, bottom)
    }

    private val handlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private fun handleR() = 11f * resources.displayMetrics.density

    private enum class Grip { NONE, BOTH, WIDTH, HEIGHT }
    private var grip = Grip.NONE
    private var gripSpan: BlockChipSpan? = null
    private var gripW = 0
    private var gripH = 0
    private var gripX = 0f
    private var gripY = 0f

    private fun selectedSpan(): BlockChipSpan? =
        text.getSpans(0, text.length, BlockChipSpan::class.java).firstOrNull { it.index == selectedBlock }

    private fun gripAt(x: Float, y: Float): Grip {
        if (selectedSpan()?.sizeTwips() == null) return Grip.NONE
        val r = selectedRect() ?: return Grip.NONE
        val reach = handleR() * 2.2f
        fun near(px: Float, py: Float) = kotlin.math.abs(x - px) <= reach && kotlin.math.abs(y - py) <= reach
        return when {
            near(r.right, r.bottom) -> Grip.BOTH
            near(r.right, r.centerY()) -> Grip.WIDTH
            near(r.centerX(), r.bottom) -> Grip.HEIGHT
            else -> Grip.NONE
        }
    }

    private fun drawHandles(canvas: android.graphics.Canvas) {
        val r = selectedRect() ?: return
        handlePaint.style = android.graphics.Paint.Style.STROKE
        handlePaint.strokeWidth = 2f * resources.displayMetrics.density
        handlePaint.color = 0xFF4F46E5.toInt()
        canvas.drawRect(r, handlePaint)
        if (selectedSpan()?.sizeTwips() == null) return
        handlePaint.style = android.graphics.Paint.Style.FILL
        val rad = handleR()
        for ((px, py) in listOf(r.right to r.bottom, r.right to r.centerY(), r.centerX() to r.bottom)) {
            handlePaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(px, py, rad, handlePaint)
            handlePaint.color = 0xFF4F46E5.toInt()
            canvas.drawCircle(px, py, rad * 0.62f, handlePaint)
        }
    }

    /** Width of the text column in twips, for keeping a dragged table inside the page. */
    var contentWidthTwips: Int = 0

    /** Called with (block index, y of the drop) when a table is dropped well above or below where it was. */
    var onTableMoved: ((Int, Float) -> Unit)? = null

    private var tblSpan: BlockChipSpan? = null
    private var tblX = 0f
    private var tblY = 0f
    private var tblBase = 0
    private var tblDragging = false

    private var dragHit: Hit? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragBaseX = 0
    private var dragBaseY = 0
    private var dragMoved = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!readOnly) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    tblSpan = null
                    selectedSpan()?.takeIf { it.block is com.docscan.app.core.word.Block.Table }?.let { sp ->
                        val r = selectedRect()
                        if (r != null && r.contains(event.x, event.y)) {
                            tblSpan = sp; tblX = event.x; tblY = event.y
                            tblBase = (sp.block as com.docscan.app.core.word.Block.Table).table.indentTwips
                            tblDragging = false
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                    val g = gripAt(event.x, event.y)
                    if (g != Grip.NONE) {
                        val sp = text.getSpans(0, text.length, BlockChipSpan::class.java).firstOrNull { it.index == selectedBlock }
                        val sz = sp?.sizeTwips()
                        if (sp != null && sz != null) {
                            grip = g; gripSpan = sp; gripW = sz.first; gripH = sz.second; gripX = event.x; gripY = event.y
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                    val hit = floatingHits().lastOrNull { event.x >= it.left && event.x <= it.left + it.w && event.y >= it.top && event.y <= it.top + it.h }
                    if (hit != null) {
                        dragHit = hit
                        dragStartX = event.x
                        dragStartY = event.y
                        val (ox, oy) = hit.span.offsetTwips()
                        dragBaseX = ox
                        dragBaseY = oy
                        dragMoved = false
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> if (tblSpan != null) {
                    val sp = tblSpan!!
                    val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                    if (!tblDragging && kotlin.math.abs(event.x - tblX) < slop && kotlin.math.abs(event.y - tblY) < slop) return true
                    tblDragging = true
                    val tb = (sp.block as? com.docscan.app.core.word.Block.Table)?.table ?: return true
                    val k = paint.density * ZOOM / 20f
                    val room = (contentWidthTwips - tb.columnWidthsTwips.sum()).coerceAtLeast(0)
                    val ind = (tblBase + ((event.x - tblX) / k).toInt()).coerceIn(0, room)
                    if (ind != tb.indentTwips) onResized?.invoke(sp.index, com.docscan.app.core.word.Block.Table(tb.copy(indentTwips = ind)), false)
                    return true
                } else if (grip != Grip.NONE) {
                    val sp = gripSpan
                    if (sp != null) {
                        val k = paint.density * ZOOM / 20f
                        val dw = ((event.x - gripX) / k).toInt()
                        val dh = ((event.y - gripY) / k).toInt()
                        var w = gripW
                        var h = gripH
                        when (grip) {
                            Grip.BOTH -> {
                                w = (gripW + maxOf(dw, if (gripW > 0) (dh * gripW.toFloat() / gripH.coerceAtLeast(1)).toInt() else 0)).coerceAtLeast(240)
                                h = if (gripH > 0) (w.toLong() * gripH / gripW.coerceAtLeast(1)).toInt() else 0
                            }
                            Grip.WIDTH -> w = (gripW + dw).coerceAtLeast(240)
                            Grip.HEIGHT -> h = (gripH + dh).coerceAtLeast(120)
                            else -> {}
                        }
                        val nb = sp.withSize(w, h)
                        if (nb != null) onResized?.invoke(sp.index, nb, false)
                    }
                    return true
                } else dragHit?.let { h ->
                    val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                    // Keep the object inside the page.
                    val dx = (h.left + event.x - dragStartX).coerceIn(0f, maxOf(0f, width - h.w)) - h.left
                    val dy = (h.top + event.y - dragStartY).coerceIn(0f, maxOf(0f, height - h.h)) - h.top
                    if (!dragMoved && kotlin.math.abs(event.x - dragStartX) < slop && kotlin.math.abs(event.y - dragStartY) < slop) return true
                    dragMoved = true
                    val k = paint.density * ZOOM / 20f
                    val nb = h.span.withOffset(dragBaseX + (dx / k).toInt(), dragBaseY + (dy / k).toInt())
                    if (nb != null) {
                        h.span.block = nb
                        onFloatMoved?.invoke(h.span.index, nb, false)
                        invalidate()
                    }
                    return true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (tblSpan != null) {
                    val sp = tblSpan!!
                    tblSpan = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val moved = tblDragging
                    tblDragging = false
                    if (moved && event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                        val r = selectedRect()
                        if (r != null && (event.y < r.top - r.height() * 0.2f || event.y > r.bottom + 20f)) onTableMoved?.invoke(sp.index, event.y)
                    } else if (!moved && event.actionMasked == android.view.MotionEvent.ACTION_UP) chipTapped(sp, event.x, event.y)
                    return true
                } else if (grip != Grip.NONE) {
                    grip = Grip.NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    selectedRect()
                    return true
                } else dragHit?.let { h ->
                    dragHit = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val b = h.span.block
                    if (dragMoved && b != null) onFloatMoved?.invoke(h.span.index, b, true)
                    else if (event.actionMasked == android.view.MotionEvent.ACTION_UP) onChipTap?.invoke(h.span.index)
                    return true
                }
            }
        }
        taps.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /** Called when the person taps the page or selects text, so the quick tools can appear. */
    var onUserTouch: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart != selEnd) onUserTouch?.invoke()
        onSelection?.invoke()
    }
}

data class ToolbarState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val style: ParaStyle = ParaStyle.NORMAL,
    val align: Align = Align.LEFT,
    val list: ListKind = ListKind.NONE,
    val sizePt: Float = StyleDefaults.BODY_SIZE_PT,
    val font: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Whether the paragraph currently has any space before / after it (decides the toggle label). */
    val spaceBefore: Boolean = false,
    val spaceAfter: Boolean = false,
)

private class Snap(val blocks: List<Block>, val selStart: Int, val selEnd: Int)

/**
 * Owns the editable text and every formatting action. The screen only draws the toolbar from [state] and calls
 * these methods. Undo/redo is snapshot based: a snapshot of the document is taken once typing pauses.
 */
class EditorController(private val baseDensity: Float, initial: List<Block>, initialComments: List<com.docscan.app.core.word.DocComment> = emptyList()) {
    private var allComments: List<com.docscan.app.core.word.DocComment> = initialComments
    /** The density the text is currently laid out at; the page views rescale it to fit the sheet to the screen. */
    private var density: Float = baseDensity
    private var mode = ViewMode.MOBILE
    private var pageSetup = com.docscan.app.core.word.PageSetup()
    private var availWidth = 0
    private var originals: List<Block>
    private val initialText: Editable
    private var view: RichEditText? = null
    private val handler = Handler(Looper.getMainLooper())
    private val undoStack = ArrayList<Snap>()
    private val redoStack = ArrayList<Snap>()
    private var current: Snap? = null
    private var applying = false
    private val commit = Runnable { commitSnapshot() }

    private val _state = MutableStateFlow(ToolbarState())
    val state: StateFlow<ToolbarState> = _state

    /** Called after every change to the document (for autosave). */
    var onChanged: () -> Unit = {}

    init {
        val (e, o) = EditorModel.toEditable(initial, baseDensity)
        initialText = e
        originals = o
    }

    fun attach(v: RichEditText) {
        view = v
        v.onFloatMoved = { i, b, fin -> floatMoved(i, b, fin) }
        v.onResized = { i, b, _ -> resizeBlock(i, b) }
        v.onTableMoved = { i, y -> moveBlockToY(i, y) }
        v.setText(initialText, android.widget.TextView.BufferType.EDITABLE)
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, StyleDefaults.BODY_SIZE_PT * ZOOM * density)
        v.setTextColor(0xFF1B1B1F.toInt())
        v.setBackgroundColor(0xFFFFFFFF.toInt())
        v.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        val pad = (20 * density).toInt()
        v.setPadding(pad, pad, pad, pad * 6)
        v.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        v.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        v.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        v.typeface = com.docscan.app.ui.write.WordFonts.body()
        v.setLineSpacing(0f, 1f)
        v.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (applying) return
                applying = true
                RichOps.normalize(s, density)
                applying = false
                changed()
            }
        })
        v.onSelection = { refresh() }
        v.isPageBreak = { idx -> originals.getOrNull(idx) == Block.PageBreak }
        current = snapshot()
        refresh()
    }

    fun detach() {
        handler.removeCallbacks(commit)
        view?.onSelection = null
        view = null
    }

    private val text: Editable get() = view!!.text
    private val selection: Pair<Int, Int>
        get() {
            val v = view!!
            val a = v.selectionStart.coerceAtLeast(0)
            val b = v.selectionEnd.coerceAtLeast(0)
            return minOf(a, b) to maxOf(a, b)
        }

    private fun snapshot() = Snap(EditorModel.toBlocks(text, originals), selection.first, selection.second)

    /** File properties carried through every save (set by the screen). */
    var author = "Kelvin Codes"
    var subject = ""
    var keywords = ""

    fun toDoc(title: String, page: PageSetup): WordDoc {
        val blocks = EditorModel.toBlocks(text, originals)
        val live = liveCommentIds()
        return WordDoc(title, blocks, page, comments = allComments.filter { it.id in live }, author = author, subject = subject, keywords = keywords)
    }

    private fun liveCommentIds(): Set<Int> =
        view?.text?.let { t -> t.getSpans(0, t.length, CommentSpan::class.java).filter { t.getSpanStart(it) < t.getSpanEnd(it) }.map { it.id }.toSet() } ?: emptySet()

    /** The comments whose text is still in the document, in the order they appear. */
    fun comments(): List<com.docscan.app.core.word.DocComment> {
        val t = view?.text ?: return emptyList()
        val live = t.getSpans(0, t.length, CommentSpan::class.java).filter { t.getSpanStart(it) < t.getSpanEnd(it) }.sortedBy { t.getSpanStart(it) }.map { it.id }
        return live.distinct().mapNotNull { id -> allComments.firstOrNull { it.id == id } }
    }

    /** Attaches a comment to the selected text. Returns false when nothing is selected. */
    fun addComment(author: String, body: String): Boolean {
        val v = view ?: return false
        val (a, b) = selection
        if (a >= b || body.isBlank()) return false
        val id = (allComments.maxOfOrNull { it.id } ?: -1) + 1
        allComments = allComments + com.docscan.app.core.word.DocComment(id, author, body.trim())
        v.text.setSpan(CommentSpan(id), a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        changed()
        return true
    }

    fun editComment(id: Int, body: String) {
        allComments = allComments.map { if (it.id == id) it.copy(text = body.trim()) else it }
        changed()
    }

    fun deleteComment(id: Int) {
        val t = view?.text ?: return
        t.getSpans(0, t.length, CommentSpan::class.java).filter { it.id == id }.forEach { t.removeSpan(it) }
        changed()
    }

    fun showComment(id: Int) {
        val v = view ?: return
        val t = v.text
        val sp = t.getSpans(0, t.length, CommentSpan::class.java).firstOrNull { it.id == id } ?: return
        v.requestFocus()
        v.setSelection(t.getSpanStart(sp).coerceAtLeast(0), t.getSpanEnd(sp).coerceAtMost(t.length))
    }

    // -- history --

    private fun changed() {
        handler.removeCallbacks(commit)
        handler.postDelayed(commit, 700)
        onChanged()
        refresh()
    }

    private fun commitSnapshot() {
        val now = snapshot()
        val cur = current
        if (cur == null || cur.blocks != now.blocks) {
            if (cur != null) {
                undoStack += cur
                if (undoStack.size > 100) undoStack.removeAt(0)
            }
            current = now
            redoStack.clear()
        }
        refresh()
    }

    fun undo() {
        handler.removeCallbacks(commit)
        commitSnapshot()
        if (undoStack.isEmpty()) return
        redoStack += current!!
        current = undoStack.removeAt(undoStack.size - 1)
        restore(current!!)
    }

    fun redo() {
        handler.removeCallbacks(commit)
        commitSnapshot()
        if (redoStack.isEmpty()) return
        undoStack += current!!
        current = redoStack.removeAt(redoStack.size - 1)
        restore(current!!)
    }

    private fun restore(s: Snap, notify: Boolean = true) {
        val v = view ?: return
        applying = true
        val (e, o) = EditorModel.toEditable(s.blocks, density)
        originals = o
        v.setText(e, android.widget.TextView.BufferType.EDITABLE)
        val len = v.text.length
        v.setSelection(s.selStart.coerceIn(0, len), s.selEnd.coerceIn(0, len))
        applying = false
        if (notify) onChanged()
        refresh()
    }

    // -- view layout --

    fun setNightPaper(on: Boolean) { view?.nightPaper = on }

    fun setViewConfig(mode: ViewMode, page: com.docscan.app.core.word.PageSetup) {
        if (mode == this.mode && page == pageSetup) return
        this.mode = mode
        pageSetup = page
        configure()
    }

    private var zoomFactor = 1f

    /** 1 = the sheet fits the screen; larger values magnify the page view (the host scrolls sideways). */
    fun setZoom(z: Float) {
        val f = z.coerceIn(1f, 3f)
        if (f == zoomFactor) return
        zoomFactor = f
        configure()
    }

    fun setAvailableWidth(px: Int) {
        if (px == availWidth) return
        availWidth = px
        configure()
    }

    /**
     * Lays the editor out for the current mode. In the page views the text is rescaled so the sheet (or just its
     * text column) fills the screen width, and margins, header and footer are drawn where they would print.
     */
    private fun configure() {
        val v = view ?: return
        if (availWidth <= 0) return
        val d0 = baseDensity
        val gutter = when (mode) { ViewMode.PAGE -> 12f * d0; ViewMode.TEXT -> 8f * d0; ViewMode.MOBILE -> 0f }
        val zoomK = if (mode == ViewMode.MOBILE) 1f else zoomFactor
        val usable = (availWidth * zoomK - 2 * gutter).coerceAtLeast(1f)
        val sheetPt = pageSetup.sheetWidthTwips / 20f
        val contentPt = pageSetup.contentWidthTwips / 20f
        val eff = when (mode) {
            ViewMode.MOBILE -> d0
            ViewMode.PAGE -> usable / (sheetPt * ZOOM)
            ViewMode.TEXT -> usable / (contentPt * ZOOM)
        }
        if (kotlin.math.abs(eff - density) > 0.0005f) {
            val snap = snapshot()
            density = eff
            v.paint.density = eff
            restore(snap, notify = false)
        }
        v.paint.density = eff
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, StyleDefaults.BODY_SIZE_PT * ZOOM * eff)
        fun px(twips: Int) = (twips / 20f * ZOOM * eff).toInt()
        if (mode == ViewMode.MOBILE) {
            val pad = (20 * d0).toInt()
            v.setPadding(pad, pad, pad, pad * 6)
            v.decor = null
            ChipLimits.maxWidthPx = (availWidth - 2 * pad).toFloat()
        } else {
            val side = if (mode == ViewMode.PAGE) px(pageSetup.marginLeftTwips) else 0
            val sideR = if (mode == ViewMode.PAGE) px(pageSetup.marginRightTwips) else 0
            v.setPadding(side, px(pageSetup.marginTopTwips), sideR, px(pageSetup.marginBottomTwips))
            ChipLimits.maxWidthPx = Float.MAX_VALUE
            v.contentWidthTwips = pageSetup.contentWidthTwips
            v.decor = PageDecor(
                headerText = pageSetup.headerText,
                footerText = pageSetup.footerText,
                pageNumbers = pageSetup.pageNumbers,
                differentFirstPage = pageSetup.differentFirstPage,
                firstHeaderText = pageSetup.firstHeaderText,
                firstFooterText = pageSetup.firstFooterText,
                firstPageNumbers = pageSetup.firstPageNumbers,
                differentOddEven = pageSetup.differentOddEven,
                evenHeaderText = pageSetup.evenHeaderText,
                evenFooterText = pageSetup.evenFooterText,
                contentHeightPx = pageSetup.contentHeightTwips / 20f * ZOOM * eff,
                bottomMarginPx = pageSetup.marginBottomTwips / 20f * ZOOM * eff,
                smallTextPx = 9f * ZOOM * eff,
                headerPics = if (mode == ViewMode.PAGE) pics(if (pageSetup.differentFirstPage) pageSetup.firstHeaderImages else pageSetup.headerImages) else emptyList(),
                footerPics = if (mode == ViewMode.PAGE) pics(pageSetup.footerImages) else emptyList(),
                headerPicsRest = if (mode == ViewMode.PAGE) pics(pageSetup.headerImages) else emptyList(),
                footerPicsFirst = if (mode == ViewMode.PAGE) pics(if (pageSetup.differentFirstPage) pageSetup.firstFooterImages else pageSetup.footerImages) else emptyList(),
                sheetHeightTwips = pageSetup.sheetHeightTwips,
                pxPerTwip = ZOOM * eff / 20f,
                topMarginPx = pageSetup.marginTopTwips / 20f * ZOOM * eff,
                deskGapPx = 14f * d0,
                headerAlign = pageSetup.headerAlign,
                footerAlign = pageSetup.footerAlign,
            )
        }
        v.requestLayout()
        v.invalidate()
    }

    private val picCache = HashMap<com.docscan.app.core.word.PageImage, android.graphics.Bitmap?>()

    private fun pics(list: List<com.docscan.app.core.word.PageImage>): List<DecorPic> = list.mapNotNull { img ->
        val bmp = picCache.getOrPut(img) { com.docscan.app.export.PageImageBitmaps.decode(img, 1400) } ?: return@mapNotNull null
        DecorPic(img, bmp)
    }

    // -- toolbar state --

    fun refresh() {
        val v = view ?: return
        val t = v.text
        val (a, b) = selection
        val attrs = RichOps.attrsOf(t, RichOps.paragraphAt(t, a))
        val size = (RichOps.valueAt(t, a, CharKind.SIZE) as? Float) ?: StyleDefaults.sizePt(attrs.style)
        val vert = RichOps.valueAt(t, a, CharKind.VERT) as? Boolean
        val before = (attrs.spaceBeforePt ?: StyleDefaults.spaceBeforePt(attrs.style)) > 0f
        val after = (attrs.spaceAfterPt ?: if (attrs.list != ListKind.NONE) 0f else StyleDefaults.spaceAfterPt(attrs.style)) > 0f
        _state.value = ToolbarState(
            spaceBefore = before,
            spaceAfter = after,
            bold = RichOps.covers(t, a, b, CharKind.BOLD),
            italic = RichOps.covers(t, a, b, CharKind.ITALIC),
            underline = RichOps.covers(t, a, b, CharKind.UNDERLINE),
            strike = RichOps.covers(t, a, b, CharKind.STRIKE),
            sup = vert == true,
            sub = vert == false,
            style = attrs.style,
            align = attrs.align,
            list = attrs.list,
            sizePt = size,
            font = (RichOps.valueAt(t, a, CharKind.FONT) as? String)?.takeIf { it.isNotEmpty() },
            canUndo = undoStack.isNotEmpty() || (current?.let { it.blocks != EditorModel.toBlocks(t, originals) } ?: false),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    // -- character formatting --

    fun toggle(kind: CharKind, attr: CharAttr) {
        if (view == null) return
        val (a, b) = selection
        if (RichOps.covers(text, a, b, kind)) RichOps.clear(text, a, b, kind) else RichOps.set(text, a, b, attr)
        changed()
    }

    fun toggleVert(up: Boolean) {
        val (a, b) = selection
        val cur = RichOps.valueAt(text, a, CharKind.VERT) as? Boolean
        if (cur == up && RichOps.covers(text, a, b, CharKind.VERT)) RichOps.clear(text, a, b, CharKind.VERT)
        else RichOps.set(text, a, b, CharAttr.Vert(up))
        changed()
    }

    fun apply(attr: CharAttr) {
        val (a, b) = selection
        RichOps.set(text, a, b, attr)
        changed()
    }

    fun remove(kind: CharKind) {
        val (a, b) = selection
        RichOps.clear(text, a, b, kind)
        changed()
    }

    fun sizeStep(delta: Int) {
        val steps = floatArrayOf(8f, 9f, 10f, 10.5f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 36f, 48f, 72f)
        val cur = _state.value.sizePt
        var i = steps.indexOfFirst { it >= cur }.let { if (it < 0) steps.size - 1 else it }
        if (steps[i] != cur) i = if (delta > 0) i - 1 else i
        i = (i + delta).coerceIn(0, steps.size - 1)
        apply(CharAttr.Size(steps[i]))
    }

    fun clearFormatting() {
        val (a, b) = selection
        RichOps.clearAll(text, a, b)
        RichOps.updateParagraphs(text, a, b, density) { ParaAttrs(list = it.list, level = it.level) }
        changed()
    }

    // -- paragraph formatting --

    private fun paragraphs(change: (ParaAttrs) -> ParaAttrs) {
        val (a, b) = selection
        RichOps.updateParagraphs(text, a, b, density, change)
        changed()
    }

    fun setStyle(style: ParaStyle) = paragraphs { it.copy(style = style) }
    fun setAlign(align: Align) = paragraphs { it.copy(align = align) }
    fun setRtl(on: Boolean) = paragraphs { it.copy(rtl = on) }
    fun setShading(rgb: Int?) = paragraphs { it.copy(shading = rgb) }
    fun toggleBorder() {
        val on = currentParagraph().borderColor == null
        paragraphs { it.copy(borderColor = if (on) 0x404040 else null) }
    }

    fun toggleList(kind: ListKind) {
        val t = text
        val (a, b) = selection
        val all = RichOps.paragraphRanges(t).filter {
            if (a == b) it.start <= a && a <= it.end else it.start < b && it.full > a
        }.all { RichOps.attrsOf(t, it).list == kind }
        paragraphs { it.copy(list = if (all) ListKind.NONE else kind) }
    }

    fun indent(delta: Int) = paragraphs {
        if (it.list != ListKind.NONE) it.copy(level = (it.level + delta).coerceIn(0, 8))
        else it.copy(indentLeftTwips = (it.indentLeftTwips + delta * 720).coerceIn(0, 7200))
    }

    /** Toggles the gap above the selected paragraphs: 12 pt when there is none, otherwise none. */
    fun toggleSpaceBefore() {
        val add = !_state.value.spaceBefore
        paragraphs { it.copy(spaceBeforePt = if (add) 12f else 0f) }
    }

    fun toggleSpaceAfter() {
        val add = !_state.value.spaceAfter
        paragraphs { it.copy(spaceAfterPt = if (add) 12f else 0f) }
    }

    fun setLineSpacing(mult: Float?) = paragraphs { it.copy(lineSpacing = mult) }

    // -- inserting --

    fun insertPageBreak() = insertBlock(Block.PageBreak)

    /** Inserts a table / image / page break after the paragraph the cursor is in. */
    fun insertBlock(block: Block) {
        val v = view ?: return
        val t = v.text
        val pr = RichOps.paragraphAt(t, selection.first)
        val insert = if (pr.hasNewline) "\n$CHIP_CHAR" else "\n$CHIP_CHAR\n"
        val index = originals.size
        originals = originals + block
        applying = true
        t.insert(pr.end, insert)
        t.setSpan(BlockChipSpan(index, EditorModel.label(block), block), pr.end + 1, pr.end + 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        RichOps.normalize(t, density)
        val chipPara = RichOps.paragraphAt(t, pr.end + 1)
        RichOps.updateParagraphs(t, chipPara.start, chipPara.start, density) { ParaAttrs() }
        applying = false
        v.setSelection(minOf(t.length, pr.end + 3))
        changed()
    }

    /**
     * Inserts a signature sized for a signature and sets it on the signing line: the first dotted / underscore line
     * (or a "Signature", "Director"… line) at or after the cursor that has no signature yet. Returns true if it found
     * a line; otherwise the signature goes in at the cursor, still sized, to be dragged into place.
     */
    fun insertSignature(sig: com.docscan.app.core.word.ImageBlock, contentWidthTwips: Int): Boolean {
        val v = view ?: return false
        val t = v.text
        val l = v.layout
        val aspect = sig.heightTwips.toFloat() / sig.widthTwips.coerceAtLeast(1)
        var w = minOf(2880, (contentWidthTwips * 0.4f).toInt()).coerceAtLeast(900)
        var h = (w * aspect).toInt()
        if (h > 1100) { h = 1100; w = (h / aspect).toInt().coerceAtLeast(600) }
        val sized = sig.copy(widthTwips = w, heightTwips = h.coerceAtLeast(200), wrap = com.docscan.app.core.word.ImageWrap.IN_FRONT)

        val ranges = RichOps.paragraphRanges(t)
        val cur = ranges.indexOfLast { it.start <= selection.first }.coerceAtLeast(0)
        val strong = Regex("[.\\u2026_]{6,}")
        val weak = Regex("(?i)(signature|signed|sign here|authori[sz]ed|director|secretary|witness|on behalf of)")
        fun hasSignature(i: Int): Boolean {
            if (i <= 0) return false
            val prev = ranges[i - 1]
            return t.getSpans(prev.start, prev.end.coerceAtMost(t.length), BlockChipSpan::class.java).any {
                val b = originals.getOrNull(it.index)
                b is Block.Image && b.image.wrap.floating
            }
        }
        fun find(rx: Regex): Int? {
            val order = (cur until ranges.size) + (0 until cur)
            return order.firstOrNull { i -> i > 0 && rx.containsMatchIn(t.subSequence(ranges[i].start, ranges[i].end)) && !hasSignature(i) }
        }
        val target = find(strong) ?: find(weak)
        if (target == null || l == null) {
            insertBlock(Block.Image(sized))
            return false
        }
        val r = ranges[target]
        val k = v.paint.density * ZOOM / 20f
        val line = l.getLineForOffset(r.start.coerceAtMost(t.length))
        val lineTwips = ((l.getLineBottom(line) - l.getLineTop(line)) / k).toInt()
        val dots = strong.find(t.subSequence(r.start, r.end))
        val startOffset = r.start + (dots?.range?.first ?: 0)
        val xTwips = (l.getPrimaryHorizontal(startOffset.coerceAtMost(t.length)) / k).toInt() + 60
        val placed = sized.copy(offsetXTwips = xTwips.coerceAtLeast(0), offsetYTwips = (lineTwips * 0.75f).toInt() - sized.heightTwips)
        v.setSelection(ranges[target - 1].start.coerceIn(0, t.length))
        insertBlock(Block.Image(placed))
        return true
    }

    fun blockAt(index: Int): Block? = originals.getOrNull(index)

    /** Index of the block inserted last (for opening it right after inserting). */
    fun lastBlockIndex(): Int? = originals.indices.lastOrNull()

    /** A picture or shape was resized by its handles. */
    /** Moves a table (or any block's paragraph) to the paragraph under screen height [y]. */
    fun moveBlockToY(index: Int, y: Float) {
        val v = view ?: return
        val t = v.text as? Editable ?: return
        val chip = t.getSpans(0, t.length, BlockChipSpan::class.java).firstOrNull { it.index == index } ?: return
        val l = v.layout ?: return
        val off = l.getOffsetForHorizontal(l.getLineForVertical((y - v.totalPaddingTop).toInt().coerceAtLeast(0)), 0f)
        val line = l.getLineForOffset(off)
        val lineMid = v.totalPaddingTop + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
        val r = RichOps.paragraphAt(t, t.getSpanStart(chip))
        val tgt = RichOps.paragraphAt(t, off)
        if (tgt.start == r.start) return
        val below = y > lineMid
        val pos = if (below) tgt.full else tgt.start
        if (pos == r.start || pos == r.full) return
        applying = true
        try {
            val piece = android.text.SpannableStringBuilder(t.subSequence(r.start, r.end))
            val needLead = below && !tgt.hasNewline
            val insert = android.text.SpannableStringBuilder().also {
                if (needLead) it.append('\n')
                it.append(piece)
                if (!needLead) it.append('\n')
            }
            t.insert(pos, insert)
            val shift = if (pos <= r.start) insert.length else 0
            val from = r.start + shift
            val to = r.end + shift
            if (r.hasNewline) t.delete(from, to + 1) else t.delete(maxOf(0, from - 1), to)
        } finally { applying = false }
        changed()
        view?.invalidate()
    }

    fun resizeBlock(index: Int, block: Block) {
        val floating = (block as? Block.Image)?.image?.wrap?.floating ?: (block as? Block.Shape)?.shape?.wrap?.floating ?: false
        if (floating) {
            val t = text
            t.getSpans(0, t.length, BlockChipSpan::class.java).firstOrNull { it.index == index }?.block = block
            floatMoved(index, block, true)
            view?.invalidate()
        } else replaceBlock(index, block)
    }

    fun select(index: Int) { view?.selectedBlock = index }

    /** A floating object was dragged: remember its new position (and, once dropped, save). */
    fun floatMoved(index: Int, block: Block, final: Boolean) {
        if (index !in originals.indices) return
        originals = originals.toMutableList().also { it[index] = block }
        if (final) changed()
    }

    /** Replaces an existing table / image (edited in its own dialog). */
    fun replaceBlock(index: Int, block: Block) {
        if (index !in originals.indices) return
        originals = originals.toMutableList().also { it[index] = block }
        val t = text
        for (chip in t.getSpans(0, t.length, BlockChipSpan::class.java)) {
            if (chip.index != index) continue
            val a = t.getSpanStart(chip)
            val e = t.getSpanEnd(chip)
            t.removeSpan(chip)
            t.setSpan(BlockChipSpan(index, EditorModel.label(block), block), a, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        changed()
    }

    /** Removes the block's placeholder paragraph. */
    fun removeBlock(index: Int) {
        val t = text
        val chip = t.getSpans(0, t.length, BlockChipSpan::class.java).firstOrNull { it.index == index } ?: return
        val r = RichOps.paragraphAt(t, t.getSpanStart(chip))
        val from = if (r.hasNewline) r.start else maxOf(0, r.start - 1)
        t.delete(from, r.full)
        changed()
    }

    fun setLinkTapListener(l: (Int, Int, String) -> Unit) {
        view?.onLinkTap = l
    }

    fun setFormTapListener(l: (com.docscan.app.core.word.FormToken) -> Unit) {
        view?.onFormTap = l
    }

    /** Selects the words between [start] and [end], so typing replaces them (a text field's hint). */
    fun selectRange(start: Int, end: Int) {
        val v = view ?: return
        if (start in 0..end && end <= v.text.length) v.setSelection(start, end)
    }

    /** What the assistant works on: the selection if there is one, otherwise the whole document. Pictures become spaces so offsets still line up. */
    class Scope(val start: Int, val end: Int, val text: String, val isSelection: Boolean)
    fun assistantScope(): Scope {
        val (a, b) = selection
        val whole = a >= b
        val s = if (whole) 0 else a
        val e = if (whole) text.length else b
        return Scope(s, e, text.subSequence(s, e).toString().replace(CHIP_CHAR, ' '), !whole)
    }

    /** Replaces [a, b) with [s] as one edit (so Undo brings the old text back). */
    fun replaceRange(a: Int, b: Int, s: String) {
        val t = text
        val x = a.coerceIn(0, t.length); val y = b.coerceIn(x, t.length)
        t.replace(x, y, s)
        changed()
    }

    /** Underlines writing problems. Tapping one shows the suggested fix, like the keyboard's spelling suggestions. */
    fun markIssues(base: Int, issues: List<com.docscan.app.cloud.WritingIssue>) {
        val v = view ?: return
        val t = v.text
        clearIssues()
        issues.forEach { i ->
            val a = (base + i.start).coerceIn(0, t.length); val b = (base + i.end).coerceIn(a, t.length)
            if (b > a) t.setSpan(
                android.text.style.SuggestionSpan(v.context, arrayOf(i.replacement), android.text.style.SuggestionSpan.FLAG_MISSPELLED),
                a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /** Underlines misspelled words; tapping one shows the checker's suggestions. [base] is where the checked text starts in the document. */
    fun markSpelling(base: Int, hits: List<SpellHit>) {
        val v = view ?: return
        val t = v.text
        clearIssues()
        hits.forEach { h ->
            val a = (base + h.start).coerceIn(0, t.length); val b = (base + h.end).coerceIn(a, t.length)
            if (b > a) t.setSpan(
                android.text.style.SuggestionSpan(v.context, h.suggestions.toTypedArray(), android.text.style.SuggestionSpan.FLAG_MISSPELLED),
                a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /** Cut, copy, paste, paste as plain text or select all, with the phone's own clipboard handling. [id] is an android.R.id value. */
    fun clipboard(id: Int) {
        view?.onTextContextMenuItem(id)
    }

    fun clearIssues() {
        val t = view?.text ?: return
        t.getSpans(0, t.length, android.text.style.SuggestionSpan::class.java).forEach { t.removeSpan(it) }
    }

    fun textBetween(a: Int, b: Int): String = text.subSequence(a.coerceIn(0, text.length), b.coerceIn(0, text.length)).toString()

    /** The text currently selected (empty if none). */
    fun selectedText(): String {
        val (a, b) = selection
        return if (a < b) text.subSequence(a, b).toString().replace(CHIP_CHAR.toString(), "") else ""
    }

    /** Makes [display] a link to [url]. Replaces the selection, or the link at [editStart, editEnd) when editing one. */
    fun applyLink(display: String, url: String, editStart: Int = -1, editEnd: Int = -1) {
        val v = view ?: return
        val t = v.text
        var (a, b) = if (editStart >= 0) editStart to editEnd else selection
        a = a.coerceIn(0, t.length); b = b.coerceIn(a, t.length)
        val shown = display.ifBlank { url }
        val existing = t.subSequence(a, b).toString()
        if (existing != shown) {
            t.replace(a, b, shown)
            b = a + shown.length
        }
        RichOps.clear(t, a, b, CharKind.LINK)
        RichOps.set(t, a, b, CharAttr.Link(url))
        v.setSelection(b)
        changed()
    }

    fun removeLink(start: Int, end: Int) {
        RichOps.clear(text, start, end, CharKind.LINK)
        changed()
    }

    fun setChipTapListener(l: (Int) -> Unit) {
        view?.onChipTap = l
    }

    // -- find / replace --

    fun find(query: String, matchCase: Boolean): Boolean {
        val v = view ?: return false
        if (query.isEmpty()) return false
        val t = v.text
        var i = t.toString().indexOf(query, selection.second, ignoreCase = !matchCase)
        if (i < 0) i = t.toString().indexOf(query, 0, ignoreCase = !matchCase)
        if (i < 0) return false
        v.requestFocus()
        v.setSelection(i, i + query.length)
        return true
    }

    fun replaceCurrent(query: String, with: String, matchCase: Boolean): Boolean {
        val (a, b) = selection
        if (a != b && text.subSequence(a, b).toString().equals(query, ignoreCase = !matchCase)) {
            text.replace(a, b, with)
        }
        return find(query, matchCase)
    }

    fun replaceAll(query: String, with: String, matchCase: Boolean): Int {
        if (query.isEmpty()) return 0
        val t = text
        var count = 0
        var i = 0
        while (true) {
            val idx = t.toString().indexOf(query, i, ignoreCase = !matchCase)
            if (idx < 0) break
            t.replace(idx, idx + query.length, with)
            i = idx + with.length
            count++
        }
        return count
    }

    // -- format painter --

    /** Reads the character formatting at the selection (or cursor) so it can be applied elsewhere. */
    fun captureFormat(): List<CharAttr> {
        val t = text
        val (a, b) = selection
        val out = ArrayList<CharAttr>()
        if (RichOps.covers(t, a, b, CharKind.BOLD)) out += CharAttr.Bold
        if (RichOps.covers(t, a, b, CharKind.ITALIC)) out += CharAttr.Italic
        if (RichOps.covers(t, a, b, CharKind.UNDERLINE)) out += CharAttr.Underline
        if (RichOps.covers(t, a, b, CharKind.STRIKE)) out += CharAttr.Strike
        (RichOps.valueAt(t, a, CharKind.COLOR) as? Int)?.let { out += CharAttr.Color(it) }
        (RichOps.valueAt(t, a, CharKind.HIGHLIGHT) as? Int)?.let { out += CharAttr.Highlight(it) }
        (RichOps.valueAt(t, a, CharKind.SIZE) as? Float)?.let { out += CharAttr.Size(it) }
        (RichOps.valueAt(t, a, CharKind.FONT) as? String)?.takeIf { it.isNotEmpty() }?.let { out += CharAttr.Font(it) }
        (RichOps.valueAt(t, a, CharKind.VERT) as? Boolean)?.let { out += CharAttr.Vert(it) }
        return out
    }

    /** Applies captured formatting to the selection, or to the word at the cursor. Replaces the old character formatting. */
    fun applyFormat(f: List<CharAttr>) {
        if (view == null) return
        val t = text
        var (a, b) = selection
        if (a == b) {
            while (a > 0 && t[a - 1].isLetterOrDigit()) a--
            while (b < t.length && t[b].isLetterOrDigit()) b++
        }
        if (a >= b) return
        RichOps.clearAll(t, a, b)
        f.forEach { RichOps.set(t, a, b, it) }
        changed()
    }

    /** Applies saved formatting to the selection, or to the whole paragraph when nothing is selected. */
    fun applySaved(f: List<CharAttr>) {
        if (view == null) return
        val t = text
        var (a, b) = selection
        if (a == b) {
            val r = RichOps.paragraphAt(t, a)
            a = r.start
            b = r.end
        }
        if (a >= b) return
        RichOps.clearAll(t, a, b)
        f.forEach { RichOps.set(t, a, b, it) }
        changed()
    }

    // -- Phase 1 additions: font, size, case, paragraph dialog, insert text, navigation, statistics --

    fun setFont(name: String?) {
        if (name == null) remove(CharKind.FONT) else apply(CharAttr.Font(name))
    }

    fun setSize(pt: Float) = apply(CharAttr.Size(pt.coerceIn(1f, 400f)))

    /** Changes case of the selection, or of the word at the cursor when nothing is selected. Formatting is kept. */
    fun changeCase(mode: com.docscan.app.core.word.CaseMode) {
        if (view == null) return
        var (a, b) = selection
        val t = text
        if (a == b) {
            while (a > 0 && t[a - 1].isLetterOrDigit()) a--
            while (b < t.length && t[b].isLetterOrDigit()) b++
        }
        if (a >= b) return
        val old = t.subSequence(a, b).toString()
        val new = com.docscan.app.core.word.TextCase.apply(old, mode)
        if (new == old) return
        // Replacing text can drop spans that cover exactly the replaced range, so put every span back afterwards.
        val spans = t.getSpans(a, b, Any::class.java).map { Triple(it, t.getSpanStart(it) to t.getSpanEnd(it), t.getSpanFlags(it)) }
        val sel = selection
        t.replace(a, b, new)
        for ((sp, range, flags) in spans) {
            if (sp is android.text.style.ParagraphStyle || sp is android.text.style.CharacterStyle) {
                if (t.getSpanStart(sp) != range.first || t.getSpanEnd(sp) != range.second) t.setSpan(sp, range.first, range.second, flags)
            }
        }
        view?.setSelection(sel.first.coerceIn(0, t.length), sel.second.coerceIn(0, t.length))
        changed()
    }

    fun isReadOnly(): Boolean = view?.readOnly == true

    fun setReadOnly(on: Boolean) {
        val v = view ?: return
        if (v.readOnly == on) return
        v.readOnly = on
        v.showSoftInputOnFocus = !on
        v.isCursorVisible = !on
        if (on) {
            (v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    fun currentParagraph(): ParaAttrs {
        val t = text
        return RichOps.attrsOf(t, RichOps.paragraphAt(t, selection.first))
    }

    /** Paragraph dialog: any argument left null keeps the current value; [clearSpacing] resets before/after to the style default. */
    fun setParagraphLayout(
        before: Float?, after: Float?, lineSpacing: Float?, indentLeftTwips: Int, indentFirstTwips: Int,
    ) = paragraphs {
        it.copy(
            spaceBeforePt = before, spaceAfterPt = after, lineSpacing = lineSpacing,
            indentLeftTwips = indentLeftTwips, indentFirstTwips = indentFirstTwips,
        )
    }

    /** Types [s] at the cursor, replacing any selection. */
    fun insertText(s: String) {
        val v = view ?: return
        val (a, b) = selection
        v.text.replace(a, b, s)
        v.setSelection(a + s.length)
        changed()
    }

    fun outline(): List<com.docscan.app.core.word.OutlineItem> {
        val t = text
        val lines = t.toString().split('\n')
        var off = 0
        val styles = lines.map { line ->
            val st = RichOps.attrsOf(t, RichOps.paragraphAt(t, off)).style
            off += line.length + 1
            st
        }
        return com.docscan.app.core.word.Outline.build(lines.map { it.replace(CHIP_CHAR.toString(), "") }, styles)
    }

    /** Caret position, to come back to later. */
    fun caretOffset(): Int = view?.selectionStart?.coerceAtLeast(0) ?: 0

    fun goTo(offset: Int) {
        val v = view ?: return
        val o = offset.coerceIn(0, v.text.length)
        v.requestFocus()
        v.setSelection(o)
        val l = v.layout ?: return
        val line = l.getLineForOffset(o)
        val r = android.graphics.Rect(0, l.getLineTop(line) + v.paddingTop, v.width, l.getLineBottom(line) + v.paddingTop)
        v.requestRectangleOnScreen(r, true)
    }

    /** (current page, total pages) in the page view; null where pages are not laid out. */
    fun pageInfo(): Pair<Int, Int>? {
        val v = view ?: return null
        if (v.decor == null) return null
        val l = v.layout ?: return null
        val starts = v.pageStarts()
        val y = l.getLineBottom(l.getLineForOffset(selection.first.coerceIn(0, v.text.length))).toFloat() - 1f
        val page = starts.indexOfLast { it <= y }.coerceAtLeast(0) + 1
        return page to starts.size
    }

    fun stats(): com.docscan.app.core.word.WordStats {
        val v = view
        val s = text.toString().replace(CHIP_CHAR.toString(), "")
        return com.docscan.app.core.word.WordStats.of(s, v?.layout?.lineCount ?: 0)
    }

    fun counts(): Pair<Int, Int> {
        val s = text.toString().replace(CHIP_CHAR.toString(), "")
        val words = s.split(Regex("\\s+")).count { it.isNotEmpty() }
        return words to s.count { !it.isWhitespace() }
    }
}
