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
    private val sheet = FrameLayout(context)
    private val across = android.widget.HorizontalScrollView(context)
    private var zoom = 1f

    /** Called with the width available to the editor whenever it changes. */
    var onWidth: ((Int) -> Unit)? = null

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
        sheet.layoutParams = lp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) { applyZoom(); onWidth?.invoke(w) }
    }
}
