package com.docscan.app.security

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Security settings (none of these are secrets) and the live locked / unlocked state of the app. */
class SecurityPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("docxels_security", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock", false)
        set(v) = prefs.edit().putBoolean("lock", v).apply()

    var lockTimeoutMs: Long
        get() = prefs.getLong("lock_timeout", 0L)
        set(v) = prefs.edit().putLong("lock_timeout", v).apply()

    /** Blocks screenshots and hides the app preview in the recent-apps list. */
    var protectScreen: Boolean
        get() = prefs.getBoolean("protect_screen", true)
        set(v) {
            prefs.edit().putBoolean("protect_screen", v).apply()
            _protect.value = v
        }

    private val _locked = MutableStateFlow(lockEnabled)
    val locked: StateFlow<Boolean> = _locked

    private val _protect = MutableStateFlow(protectScreen)
    val protect: StateFlow<Boolean> = _protect

    private var backgroundedAt: Long? = null

    fun onBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForegrounded() {
        if (LockPolicy.shouldLock(lockEnabled, lockTimeoutMs, backgroundedAt, SystemClock.elapsedRealtime())) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
        // After a successful unlock, the next return from background is measured from that moment.
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun lockNow() {
        if (lockEnabled) _locked.value = true
    }

    fun changeLock(enabled: Boolean) {
        lockEnabled = enabled
        if (!enabled) _locked.value = false
    }
}
