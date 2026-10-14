package com.docscan.app.security

/** Decides whether the app must be locked when it returns to the foreground. Pure so it can be unit-tested. */
object LockPolicy {
    val TIMEOUTS = listOf(0L to "Immediately", 60_000L to "After 1 minute", 300_000L to "After 5 minutes")

    /**
     * @param backgroundedAt elapsed-realtime when the app left the screen, or null if it has not been in the
     * background since the last unlock (a cold start counts as "must lock").
     */
    fun shouldLock(enabled: Boolean, timeoutMs: Long, backgroundedAt: Long?, now: Long): Boolean {
        if (!enabled) return false
        if (backgroundedAt == null) return true
        val away = now - backgroundedAt
        return away < 0 || away >= timeoutMs
    }
}
