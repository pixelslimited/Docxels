package com.docscan.app.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** The app's accent colour: two shades that make the brand gradient (ARGB). */
enum class Accent(val label: String, val a: Long, val b: Long) {
    DOCXELS("Docxels", 0xFF4F46E5, 0xFF7C3AED),
    WORD_BLUE("Word blue", 0xFF1E4DA1, 0xFF2B7CD3),
    GREY("Grey", 0xFF475569, 0xFF6B7A90),
    BLACK("Black", 0xFF0F172A, 0xFF334155),
    GREEN("Green", 0xFF15803D, 0xFF0F766E),
    TEAL("Teal", 0xFF0E7490, 0xFF0891B2),
    ORANGE("Orange", 0xFFEA580C, 0xFFDB2777),
    ROSE("Rose", 0xFFBE185D, 0xFF9D174D),
}

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

    private val _accent = MutableStateFlow(
        runCatching { Accent.valueOf(prefs.getString("accent", "DOCXELS")!!) }.getOrDefault(Accent.DOCXELS),
    )
    val accent: StateFlow<Accent> = _accent

    fun setAccent(a: Accent) {
        prefs.edit().putString("accent", a.name).apply()
        _accent.value = a
    }

    /** PAGE, TEXT or MOBILE; remembered between documents. */
    var editorView: String
        get() = prefs.getString("editor_view", "PAGE") ?: "PAGE"
        set(v) = prefs.edit().putString("editor_view", v).apply()

    /** Read the words in scans and documents so the library search can find them (off until the person turns it on). */
    var searchInside: Boolean
        get() = prefs.getBoolean("search_inside", false)
        set(v) = prefs.edit().putBoolean("search_inside", v).apply()

    /** Format and page fit that the scan export sheet starts with (see Settings > Exporting). */
    var exportFormat: String
        get() = prefs.getString("export_format", "PDF") ?: "PDF"
        set(v) = prefs.edit().putString("export_format", v).apply()

    var exportA4: Boolean
        get() = prefs.getBoolean("export_a4", true)
        set(v) = prefs.edit().putBoolean("export_a4", v).apply()

    /** Lets the app check for messages from Docxels (daily) and for a newer version (every few minutes while open, about every 15 minutes in the background), and show them as pop-ups and a notification. On by default. */
    var announcements: Boolean
        get() = prefs.getBoolean("announcements", true)
        set(v) = prefs.edit().putBoolean("announcements", v).apply()

    /** The short welcome shown on a fresh install has been seen (or was skipped for people who already use the app). */
    var introSeen: Boolean
        get() = prefs.getBoolean("intro_seen", false)
        set(v) = prefs.edit().putBoolean("intro_seen", v).apply()

    /** How the Documents list is sorted and shown; remembered between visits (names of the Sort and ViewMode choices). */
    var librarySort: String
        get() = prefs.getString("library_sort", "NEWEST") ?: "NEWEST"
        set(v) = prefs.edit().putString("library_sort", v).apply()

    var libraryView: String
        get() = prefs.getString("library_view", "LIST") ?: "LIST"
        set(v) = prefs.edit().putString("library_view", v).apply()

    /** Folder that every new scan is put into (empty = none; the scan stays loose in the library). */
    var newScanFolder: String
        get() = prefs.getString("new_scan_folder", "") ?: ""
        set(v) = prefs.edit().putString("new_scan_folder", v).apply()

    /** Last known Pro status (kept so exporting works offline). */
    var knownPro: Boolean
        get() = prefs.getBoolean("known_pro", false)
        set(v) = prefs.edit().putBoolean("known_pro", v).apply()

    /** Pro only: leave the "Scanned with Docxels by Pixels" credit off exported pages. */
    var watermarkOff: Boolean
        get() = prefs.getBoolean("watermark_off", false)
        set(v) = prefs.edit().putBoolean("watermark_off", v).apply()
}
