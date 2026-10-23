package com.docscan.app.cloud

import android.content.Context
import android.content.SharedPreferences

/**
 * Anonymous usage counts kept on the phone until the daily check sends them (see Announcements). Only names of features
 * ("scan", "pdf_tool:merge") and short error codes are kept, never document names, text or anything about the person.
 * Nothing is counted while Settings > Announcements and update notices is off. This class does no networking.
 */
object Analytics {
    private var stats: SharedPreferences? = null
    private var ui: SharedPreferences? = null

    fun init(context: Context) {
        stats = context.getSharedPreferences("docxels_stats", Context.MODE_PRIVATE)
        ui = context.getSharedPreferences("docxels_ui", Context.MODE_PRIVATE)
    }

    private fun enabled() = ui?.getBoolean("announcements", true) == true

    /** Counts one use of a feature. */
    fun track(feature: String) = bump("f_" + feature.lowercase().replace(Regex("[^a-z0-9_:.]"), "_").take(40))

    /** Counts one error. [code] must describe the kind of failure only, never contain user data. */
    fun error(code: String) = bump("e_" + code.replace(Regex("[^A-Za-z0-9_:.@$<>-]"), "_").take(100), now = true)

    /** The code for a crash: the exception type and the first line of Docxels' own code in its stack trace. */
    fun crashCode(t: Throwable): String {
        val frame = t.stackTrace.firstOrNull { it.className.startsWith("com.docscan.app") }
        return "crash:" + t.javaClass.simpleName + (frame?.let { "@" + it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber } ?: "")
    }

    @Synchronized
    private fun bump(key: String, now: Boolean = false) {
        val p = stats ?: return
        if (!enabled()) return
        val e = p.edit().putInt(key, (p.getInt(key, 0) + 1).coerceAtMost(100_000))
        if (now) e.commit() else e.apply()
    }

    /** What is waiting to be sent: feature counts and error counts. */
    @Synchronized
    fun snapshot(): Pair<Map<String, Int>, Map<String, Int>> {
        val all = stats?.all.orEmpty().filterValues { it is Int }.mapValues { it.value as Int }
        return all.filterKeys { it.startsWith("f_") }.mapKeys { it.key.drop(2) } to
            all.filterKeys { it.startsWith("e_") }.mapKeys { it.key.drop(2) }
    }

    /** Takes away what was sent, keeping anything counted while the request was in flight. */
    @Synchronized
    fun sent(features: Map<String, Int>, errors: Map<String, Int>) {
        val p = stats ?: return
        val e = p.edit()
        for ((k, n) in features) { val left = p.getInt("f_$k", 0) - n; if (left > 0) e.putInt("f_$k", left) else e.remove("f_$k") }
        for ((k, n) in errors) { val left = p.getInt("e_$k", 0) - n; if (left > 0) e.putInt("e_$k", left) else e.remove("e_$k") }
        e.apply()
    }
}
