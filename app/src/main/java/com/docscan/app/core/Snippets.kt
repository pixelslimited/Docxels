package com.docscan.app.core

/** A piece of text saved to type again quickly: an address, bank details, a closing line. */
data class Snippet(val name: String, val text: String)

/**
 * Keeps the saved text as one plain string (records and fields split by control characters nobody types), so the list
 * can sit in the app's private preferences and be tested without Android. Anything malformed is dropped.
 */
object Snippets {
    const val MAX = 50
    const val MAX_TEXT = 2000
    const val MAX_NAME = 40
    private const val REC = '\u001E'
    private const val FLD = '\u001F'

    fun clean(name: String, text: String): Snippet? {
        val t = text.replace(REC, ' ').replace(FLD, ' ').trim().take(MAX_TEXT)
        val n = name.replace(REC, ' ').replace(FLD, ' ').replace('\n', ' ').trim().take(MAX_NAME).ifEmpty { t.lineSequence().first().take(MAX_NAME) }
        return if (t.isEmpty() || n.isEmpty()) null else Snippet(n, t)
    }

    fun encode(list: List<Snippet>): String = list.joinToString(REC.toString()) { "${it.name}$FLD${it.text}" }

    fun decode(raw: String?): List<Snippet> =
        raw.orEmpty().split(REC).mapNotNull { rec ->
            val p = rec.split(FLD)
            if (p.size == 2) clean(p[0], p[1]) else null
        }.take(MAX)

    /** Adds [s] on top; a snippet with the same name is replaced. */
    fun add(list: List<Snippet>, s: Snippet): List<Snippet> = (listOf(s) + list.filter { !it.name.equals(s.name, ignoreCase = true) }).take(MAX)
}
