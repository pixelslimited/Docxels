package com.docscan.app.ui.write

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * Scrolling container for the editor. The text view sits on a "sheet" inside a coloured desk; in the page views the
 * sheet is as wide as the screen allows, so the whole page can be seen and worked on. The text view grows to its full
 * height and this container does the scrolling, so one set of scrollbars serves the whole page.
 */
class PageHost(context: Context) : ScrollView(context) {
    val edit = RichEditText(context)
    /**
     * A horizontal scroller measures its child with no width limit and ignores the child's requested width, so the
     * sheet forces its own exact width here; the text view inside then wraps at the sheet's edge.
     */
    private class Sheet(context: Context) : FrameLayout(context) {
        var forcedWidth = 0
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = if (forcedWidth > 0) MeasureSpec.makeMeasureSpec(forcedWidth, MeasureSpec.EXACTLY) else widthMeasureSpec
            super.onMeasure(w, heightMeasureSpec)
        }
    }
    private val sheet = Sheet(context)
    private val across = android.widget.HorizontalScrollView(context)
    private var zoom = 1f

    /** Called with the width available to the editor whenever it changes. */
    var onWidth: ((Int) -> Unit)? = null

    /** Called with the new zoom (1 to 3) while the person pinches the page. */
    var onPinch: ((Float) -> Unit)? = null
    var pinchEnabled = true
    private var pinchZoom = 1f
    private var pinching = false
    private val scaler = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean { pinchZoom = zoom; return pinchEnabled }
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            pinchZoom = (pinchZoom * detector.scaleFactor).coerceIn(1f, 3f)
            val q = Math.round(pinchZoom * 20f) / 20f // steps of 5% so the text is not re-laid out on every tiny move
            if (q != zoom) { setZoom(q); onPinch?.invoke(q) }
            return true
        }
    })

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        cellEditor?.let { ed ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                val loc = IntArray(2)
                ed.getLocationOnScreen(loc)
                val inside = ev.rawX >= loc[0] && ev.rawX <= loc[0] + ed.width && ev.rawY >= loc[1] && ev.rawY <= loc[1] + ed.height
                if (!inside) closeCellEditor()
            }
        }
        if (pinchEnabled) {
            scaler.onTouchEvent(ev)
            if (scaler.isInProgress && !pinching) {
                pinching = true
                val c = android.view.MotionEvent.obtain(ev)
                c.action = android.view.MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(c) // the editor was handling the first finger; let it go
                c.recycle()
            }
            if (pinching) {
                if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) pinching = false
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        across.isFillViewport = true
        across.overScrollMode = OVER_SCROLL_NEVER
        across.isHorizontalScrollBarEnabled = false
        addView(across, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        across.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(edit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private var cellEditor: android.widget.EditText? = null
    private var cellDone: ((String) -> Unit)? = null

    /** Types straight into a table cell: an edit box laid exactly over the cell, scrolling with the page. */
    fun showCellEditor(r: android.graphics.RectF, pad: Float, initial: String, sizePx: Float, bg: Int, onDone: (String) -> Unit) {
        closeCellEditor()
        val ed = android.widget.EditText(context).apply {
            setText(initial)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
            setTextColor(0xFF1B1B1F.toInt())
            setBackgroundColor(bg)
            includeFontPadding = false
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(pad.toInt(), pad.toInt(), pad.toInt(), pad.toInt())
            minHeight = r.height().toInt()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val lp = FrameLayout.LayoutParams(r.width().toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = edit.left + r.left.toInt()
        lp.topMargin = edit.top + r.top.toInt()
        sheet.addView(ed, lp)
        cellEditor = ed
        cellDone = onDone
        ed.requestFocus()
        ed.setSelection(ed.length())
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(ed, 0)
    }

    /** Ends cell typing and hands the text back. */
    fun closeCellEditor() {
        val ed = cellEditor ?: return
        val done = cellDone
        cellEditor = null
        cellDone = null
        val t = ed.text.toString()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(ed.windowToken, 0)
        sheet.removeView(ed)
        done?.invoke(t)
    }

    fun setDesk(color: Int, gutterPx: Int, verticalGutterPx: Int) {
        setBackgroundColor(color)
        if (edit.deskColor != color) { edit.deskColor = color; edit.invalidate() }
        sheet.setPadding(gutterPx, verticalGutterPx, gutterPx, verticalGutterPx)
    }

    /** Zoom above 1 makes the sheet wider than the screen; it then scrolls sideways. */
    fun setZoom(z: Float) {
        if (z == zoom) return
        zoom = z
        applyZoom()
    }

    private fun applyZoom() {
        val lp = sheet.layoutParams as FrameLayout.LayoutParams
        // Always an exact width: inside the sideways scroller a MATCH_PARENT child is measured unbounded, and the text
        // would then run on one line instead of wrapping.
        lp.width = if (width <= 0) ViewGroup.LayoutParams.MATCH_PARENT else (width * zoom).toInt()
        sheet.forcedWidth = if (width <= 0) 0 else (width * zoom).toInt()
        sheet.layoutParams = lp
        sheet.requestLayout()
    }

    /**
     * The sheet's width is set on every measure, not only when this view's size changes: a document opened after the
     * screen is already laid out would otherwise be measured with no width limit and never wrap.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        if (w > 0) {
            val target = (w * zoom).toInt()
            val lp = sheet.layoutParams as FrameLayout.LayoutParams
            if (lp.width != target) { lp.width = target; sheet.layoutParams = lp }
            sheet.forcedWidth = target
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) { applyZoom(); onWidth?.invoke(w) }
    }
}
