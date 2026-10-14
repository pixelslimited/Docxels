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
class RichEditText(context: Context) : EditText(context) {
    var onSelection: (() -> Unit)? = null
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
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

private class Snap(val blocks: List<Block>, val selStart: Int, val selEnd: Int)

/**
 * Owns the editable text and every formatting action. The screen only draws the toolbar from [state] and calls
 * these methods. Undo/redo is snapshot based: a snapshot of the document is taken once typing pauses.
 */
class EditorController(private val density: Float, initial: List<Block>) {
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
        val (e, o) = EditorModel.toEditable(initial, density)
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

    private fun restore(s: Snap) {
        val v = view ?: return
        applying = true
        val (e, o) = EditorModel.toEditable(s.blocks, density)
        originals = o
        v.setText(e, android.widget.TextView.BufferType.EDITABLE)
        val len = v.text.length
        v.setSelection(s.selStart.coerceIn(0, len), s.selEnd.coerceIn(0, len))
        applying = false
        onChanged()
        refresh()
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

    fun insertPageBreak() {
        val v = view ?: return
        val t = v.text
        val pr = RichOps.paragraphAt(t, selection.first)
        val insert = if (pr.hasNewline) "\n$CHIP_CHAR" else "\n$CHIP_CHAR\n"
        val index = originals.size
        originals = originals + Block.PageBreak
        applying = true
        t.insert(pr.end, insert)
        t.setSpan(BlockChipSpan(index, EditorModel.label(Block.PageBreak)), pr.end + 1, pr.end + 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        RichOps.normalize(t, density)
        val chipPara = RichOps.paragraphAt(t, pr.end + 1)
        RichOps.updateParagraphs(t, chipPara.start, chipPara.start, density) { ParaAttrs() }
        applying = false
        v.setSelection(minOf(t.length, pr.end + 3))
        changed()
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

    fun counts(): Pair<Int, Int> {
        val s = text.toString().replace(CHIP_CHAR.toString(), "")
        val words = s.split(Regex("\\s+")).count { it.isNotEmpty() }
        return words to s.count { !it.isWhitespace() }
    }
}
