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
    // While the fingers are down the sheet is only scaled visually (cheap, follows the fingers every frame); the text is
    // laid out once at the new size when they lift. Re-laying out the whole document on every step is what made it jerky.
    private var anchorX = 0f // the point of the sheet under the fingers when the pinch began (sheet coordinates)
    private var anchorY = 0f
    private var startFocusX = 0f
    private var startFocusY = 0f
    private var focusX = 0f
    private var focusY = 0f
    private val scaler = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
            pinchZoom = zoom
            startFocusX = detector.focusX; startFocusY = detector.focusY
            focusX = startFocusX; focusY = startFocusY
            anchorX = across.scrollX + startFocusX
            anchorY = scrollY + startFocusY
            sheet.pivotX = anchorX
            sheet.pivotY = anchorY
            return pinchEnabled
        }
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            pinchZoom = (pinchZoom * detector.scaleFactor).coerceIn(1f, 3f)
            focusX = detector.focusX; focusY = detector.focusY
            val s = pinchZoom / zoom
            sheet.scaleX = s
            sheet.scaleY = s
            sheet.translationX = focusX - startFocusX
            sheet.translationY = focusY - startFocusY
            return true
        }
        override fun onScaleEnd(detector: android.view.ScaleGestureDetector) { commitPinch() }
    })

    private fun commitPinch() {
        sheet.scaleX = 1f; sheet.scaleY = 1f
        sheet.translationX = 0f; sheet.translationY = 0f
        val q = Math.round(pinchZoom * 20f) / 20f
        if (q == zoom) return
        val ratio = q / zoom
        // Keep the spot that was under the fingers under them once the page has been laid out at the new size.
        val wantX = (anchorX * ratio - focusX).toInt().coerceAtLeast(0)
        val wantY = (anchorY * ratio - focusY).toInt().coerceAtLeast(0)
        setZoom(q)
        onPinch?.invoke(q)
        viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(this)
                across.scrollTo(wantX, 0)
                scrollTo(0, wantY)
            }
        })
    }

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

    /** Called with a phone number when it is double-tapped (the screen opens the dialer). */
    var onDial: ((String) -> Unit)? = null
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
        // The first tap of a double tap opened this box, so the second one lands here: count the box opening as the first tap.
        var lastTap = android.os.SystemClock.uptimeMillis()
        ed.setOnTouchListener { v, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                val now = ev.eventTime
                val double = now - lastTap <= android.view.ViewConfiguration.getDoubleTapTimeout()
                lastTap = now
                val number = if (double) {
                    val box = v as android.widget.EditText
                    val off = box.getOffsetForPosition(ev.x, ev.y)
                    com.docscan.app.core.PhoneNumbers.at(box.text, off) ?: com.docscan.app.core.PhoneNumbers.first(box.text)
                } else null
                if (number != null) {
                    discardCellEditor()
                    onDial?.invoke(number)
                    true
                } else false
            } else false
        }
        ed.requestFocus()
        ed.setSelection(ed.length())
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(ed, 0)
    }

    /** Removes the cell box without handing anything back. */
    fun discardCellEditor() { cellDone = null; closeCellEditor() }

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

    /** Called with how far the page just scrolled (positive = moving down the document). */
    var onScrollDelta: ((Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) onScrollDelta?.invoke(t - oldt)
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
