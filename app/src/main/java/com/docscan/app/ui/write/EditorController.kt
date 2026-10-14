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
    val contentHeightPx: Float,
    val bottomMarginPx: Float,
    val smallTextPx: Float,
)

class RichEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null

    /** Set in the page views; null in the mobile view. */
    var decor: PageDecor? = null
        set(v) {
            field = v
            requestLayout()
            invalidate()
        }

    /** Tells whether the table / picture / page-break chip with this index is a hard page break. */
    var isPageBreak: (Int) -> Boolean = { false }

    private val guide = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = 0xFF9AA0AE.toInt()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val small = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8F9C.toInt() }

    /** Y (in layout coordinates) where each page's text starts, mirroring how the PDF export breaks pages by line. */
    fun pageStarts(): List<Float> {
        val d = decor ?: return listOf(0f)
        val l = layout ?: return listOf(0f)
        val starts = arrayListOf(0f)
        var pageStart = 0f
        val t = text
        for (i in 0 until l.lineCount) {
            val top = l.getLineTop(i).toFloat()
            val bottom = l.getLineBottom(i).toFloat()
            if (i > 0 && top > pageStart && bottom - pageStart > d.contentHeightPx + 0.5f) {
                starts += top
                pageStart = top
            }
            val ls = l.getLineStart(i)
            if (ls < t.length && t[ls] == CHIP_CHAR && i < l.lineCount - 1 &&
                t.getSpans(ls, minOf(t.length, ls + 1), BlockChipSpan::class.java).any { isPageBreak(it.index) }
            ) {
                starts += bottom
                pageStart = bottom
            }
        }
        return starts
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val d = decor ?: return
        if (layout == null) return
        // Whole sheets only: the page always ends where a printed page would end.
        val need = (totalPaddingTop + pageStarts().last() + d.contentHeightPx + d.bottomMarginPx).toInt()
        if (need > measuredHeight) setMeasuredDimension(measuredWidth, need)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val d = decor ?: return
        val starts = pageStarts()
        small.textSize = d.smallTextPx
        guide.strokeWidth = maxOf(1f, d.smallTextPx / 8f)
        val topPad = totalPaddingTop.toFloat()
        if (d.headerText.isNotEmpty()) {
            canvas.drawText(d.headerText, paddingLeft.toFloat(), topPad / 2f + d.smallTextPx / 3f, small)
        }
        for (k in 1 until starts.size) {
            val y = topPad + starts[k] - 2f
            canvas.drawLine(0f, y, width.toFloat(), y, guide)
            val label = "Page ${k + 1}"
            canvas.drawText(label, width - paddingRight - small.measureText(label), y - d.smallTextPx / 3f, small)
        }
        val footY = height - d.bottomMarginPx / 2f + d.smallTextPx / 3f
        if (d.footerText.isNotEmpty()) canvas.drawText(d.footerText, paddingLeft.toFloat(), footY, small)
        if (d.pageNumbers) {
            val label = starts.size.toString()
            canvas.drawText(label, width - paddingRight - small.measureText(label), footY, small)
        }
    }

    /** Called with the block index when a table / image placeholder is tapped. */
    var onChipTap: ((Int) -> Unit)? = null

    /** Called with (start, end, address) when a hyperlink is tapped. */
    var onLinkTap: ((Int, Int, String) -> Unit)? = null
    private val taps = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
            val t = text
            val off = getOffsetForPosition(e.x, e.y)
            val chip = t.getSpans(maxOf(0, off - 1), minOf(t.length, off + 1), BlockChipSpan::class.java)
                .firstOrNull { t.getSpanStart(it) <= off && off <= t.getSpanEnd(it) }
            if (chip != null) onChipTap?.invoke(chip.index)
            else {
                val link = t.getSpans(maxOf(0, off - 1), minOf(t.length, off + 1), LinkSpan::class.java)
                    .firstOrNull { t.getSpanStart(it) <= off && off < t.getSpanEnd(it) }
                if (link != null) onLinkTap?.invoke(t.getSpanStart(link), t.getSpanEnd(link), link.url)
            }
            return false
        }
    })

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        taps.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
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
)

private class Snap(val blocks: List<Block>, val selStart: Int, val selEnd: Int)

/**
 * Owns the editable text and every formatting action. The screen only draws the toolbar from [state] and calls
 * these methods. Undo/redo is snapshot based: a snapshot of the document is taken once typing pauses.
 */
class EditorController(private val baseDensity: Float, initial: List<Block>) {
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
        v.typeface = android.graphics.Typeface.SANS_SERIF
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

    fun toDoc(title: String, page: PageSetup): WordDoc = WordDoc(title, EditorModel.toBlocks(text, originals), page)

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

    fun setViewConfig(mode: ViewMode, page: com.docscan.app.core.word.PageSetup) {
        if (mode == this.mode && page == pageSetup) return
        this.mode = mode
        pageSetup = page
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
        val usable = (availWidth - 2 * gutter).coerceAtLeast(1f)
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
            v.decor = PageDecor(
                headerText = pageSetup.headerText,
                footerText = pageSetup.footerText,
                pageNumbers = pageSetup.pageNumbers,
                contentHeightPx = pageSetup.contentHeightTwips / 20f * ZOOM * eff,
                bottomMarginPx = pageSetup.marginBottomTwips / 20f * ZOOM * eff,
                smallTextPx = 9f * ZOOM * eff,
            )
        }
        v.requestLayout()
        v.invalidate()
    }

    // -- toolbar state --

    fun refresh() {
        val v = view ?: return
        val t = v.text
        val (a, b) = selection
        val attrs = RichOps.attrsOf(t, RichOps.paragraphAt(t, a))
        val size = (RichOps.valueAt(t, a, CharKind.SIZE) as? Float) ?: StyleDefaults.sizePt(attrs.style)
        val vert = RichOps.valueAt(t, a, CharKind.VERT) as? Boolean
        _state.value = ToolbarState(
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

    fun blockAt(index: Int): Block? = originals.getOrNull(index)

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
        val y = l.getLineTop(l.getLineForOffset(selection.first.coerceIn(0, v.text.length))).toFloat()
        val page = starts.indexOfLast { it <= y + 0.5f }.coerceAtLeast(0) + 1
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
