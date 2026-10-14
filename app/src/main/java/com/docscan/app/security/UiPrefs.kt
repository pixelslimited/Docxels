package com.docscan.app.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Look-and-feel choices (none are secrets). */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("docxels_ui", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
    )
    val theme: StateFlow<ThemeMode> = _theme

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    /** PAGE, TEXT or MOBILE; remembered between documents. */
    var editorView: String
        get() = prefs.getString("editor_view", "PAGE") ?: "PAGE"
        set(v) = prefs.edit().putString("editor_view", v).apply()
}
