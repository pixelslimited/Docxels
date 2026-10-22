package com.docscan.app.core.word

/**
 * Who a new document belongs to. Set from the signed-in account's email (see [fromEmail]); "Docxels" when nobody is
 * signed in. Used as the default author written into Word and RTF files, and for the greeting on Home.
 */
object DocOwner {
    @Volatile var name: String = ""
        private set

    /** The author text for new documents: "Mukhtar Sabiu (Docxels)", or just "Docxels" when signed out. */
    val label: String get() = name.takeIf { it.isNotBlank() }?.let { "$it (Docxels)" } ?: "Docxels"

    /** The default the app used before names came from accounts; such files are treated as having no chosen author. */
    const val LEGACY_AUTHOR = "Kelvin Codes"

    fun set(email: String?) { name = fromEmail(email) }

    /** "mukhtar.sabiu@gmail.com" -> "Mukhtar Sabiu". Words are split at dots, underscores, hyphens, plus signs and digits. */
    fun fromEmail(email: String?): String =
        email.orEmpty().substringBefore('@').split(Regex("[._+\\-\\d]+")).filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercaseChar() } }.take(40)
}
