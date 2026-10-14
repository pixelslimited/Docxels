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

    /** Called with the width available to the editor whenever it changes. */
    var onWidth: ((Int) -> Unit)? = null

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        sheet.addView(edit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setDesk(color: Int, gutterPx: Int, verticalGutterPx: Int) {
        setBackgroundColor(color)
        sheet.setPadding(gutterPx, verticalGutterPx, gutterPx, verticalGutterPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) onWidth?.invoke(w)
    }
}
