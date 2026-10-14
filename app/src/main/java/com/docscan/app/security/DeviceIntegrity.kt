package com.docscan.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File

/**
 * Best-effort signs that the phone is rooted, modified or being debugged. These checks can be bypassed by a
 * determined attacker, so they only inform the user; they never claim to prove safety.
 */
object DeviceIntegrity {
    private val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/vendor/bin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/su/bin/su", "/system/app/Superuser.apk",
        "/data/adb/magisk", "/sbin/.magisk",
    )

    /** Pure so it can be unit-tested. */
    fun assess(buildTags: String?, suFound: Boolean, debuggable: Boolean, debuggerAttached: Boolean): List<String> {
        val out = ArrayList<String>()
        if (suFound) out += "Root tools were found on this phone."
        if (buildTags?.contains("test-keys") == true) out += "This phone runs a test or custom Android build."
        if (debuggable) out += "This copy of Docxels is a debug build."
        if (debuggerAttached) out += "A debugger is attached to Docxels."
        return out
    }

    fun check(context: Context): List<String> = assess(
        buildTags = Build.TAGS,
        suFound = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) },
        debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        debuggerAttached = android.os.Debug.isDebuggerConnected(),
    )
}
