package com.docscan.app.ui.write

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** A word the phone's spell checker does not know, where it is in the checked text, and what it suggests instead. */
class SpellHit(val start: Int, val end: Int, val suggestions: List<String>)

/**
 * Spelling check with the spell checker that is already on the phone (the keyboard's), so nothing leaves the device.
 * Returns null when the phone has no spell checker turned on.
 */
object Spelling {
    /** Words worth skipping: numbers, codes, web and e-mail addresses, and ALL-CAPS abbreviations. */
    fun worthChecking(word: String): Boolean {
        if (word.length < 3) return false
        if (word.any { it.isDigit() } || word.contains('@') || word.contains("://") || word.startsWith("www.")) return false
        if (word.all { !it.isLowerCase() }) return false
        return true
    }

    suspend fun check(context: Context, text: String): List<SpellHit>? {
        val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager ?: return null
        var session: SpellCheckerSession? = null
        return try {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<List<SpellHit>?> { cont ->
                    val hits = ArrayList<SpellHit>()
                    val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}
                        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                            for (r in results.orEmpty()) {
                                for (j in 0 until r.suggestionsCount) {
                                    val info = r.getSuggestionsInfoAt(j)
                                    if (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO == 0) continue
                                    val start = r.getOffsetAt(j)
                                    val end = start + r.getLengthAt(j)
                                    if (start < 0 || end > text.length || end <= start) continue
                                    if (!worthChecking(text.substring(start, end))) continue
                                    val sug = (0 until info.suggestionsCount).mapNotNull { info.getSuggestionAt(it)?.takeIf { s -> s.isNotBlank() } }
                                    hits += SpellHit(start, end, sug)
                                }
                            }
                            if (cont.isActive) cont.resume(hits.sortedBy { it.start })
                        }
                    }
                    session = tsm.newSpellCheckerSession(null, Locale.getDefault(), listener, true)
                    val s = session
                    if (s == null) { cont.resume(null); return@suspendCancellableCoroutine }
                    // One request for the whole text; the checker walks it sentence by sentence.
                    s.getSentenceSuggestions(arrayOf(TextInfo(text)), 5)
                }
            }
        } finally {
            session?.close()
        }
    }
}
