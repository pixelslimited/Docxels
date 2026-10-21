package com.docscan.app.ui.write

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * "Dark paper": the page is shown on screen with its lightness flipped and its hues kept (invert, then rotate the hue
 * half a turn), so white paper becomes near-black and black text becomes light grey while colours stay recognisable.
 * Only what is drawn on screen changes; the document, the saved file, PDFs, prints and shares are untouched.
 */
object NightPaper {
    // White -> about #121212, black -> about #E6E6E6.
    private const val K = 0.831f
    private const val OFFSET = 230f

    private val values = floatArrayOf(
        0.574f * K, -1.430f * K, -0.144f * K, 0f, OFFSET,
        -0.426f * K, -0.430f * K, -0.144f * K, 0f, OFFSET,
        -0.426f * K, -1.430f * K, 0.856f * K, 0f, OFFSET,
        0f, 0f, 0f, 1f, 0f,
    )

    fun androidFilter() = ColorMatrixColorFilter(ColorMatrix(values))
    fun composeFilter() = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(values.copyOf()))

    private const val PREFS = "docxels_editor"
    private const val KEY = "dark_paper"
    fun load(context: Context): Boolean = runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)
    fun save(context: Context, dark: Boolean) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, dark).apply() }
    }
}
