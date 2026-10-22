package com.docscan.app.core.word

/**
 * Who a new document belongs to. Set from the signed-in person's profile (first and last name); "Docxels" when there is no name. Used as the default author written into Word and RTF files, and for the greeting on Home.
 */
object DocOwner {
    @Volatile var name: String = ""
        private set

    /** The author text for new documents: "Mukhtar Sabiu (Docxels)", or just "Docxels" when signed out. */
    val label: String get() = name.takeIf { it.isNotBlank() }?.let { "$it (Docxels)" } ?: "Docxels"

    /** The default the app used before names came from accounts; such files are treated as having no chosen author. */
    const val LEGACY_AUTHOR = "Kelvin Codes"

    /** [fullName] is the first and last name from the signed-in person's profile; blank when signed out or not filled in. */
    fun set(fullName: String?) { name = fullName.orEmpty().trim() }
}
