package com.docscan.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity
import com.docscan.app.security.Authenticator
import com.docscan.app.ui.AppNav
import com.docscan.app.ui.LockScreen
import com.docscan.app.ui.theme.DocScanTheme

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MainActivity : FragmentActivity() {
    private lateinit var container: AppContainer

    /** A file another app asked Docxels to open or received through Share; consumed by the navigation. */
    private val incoming = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)

    private fun handleIntent(i: android.content.Intent?) {
        val uri: android.net.Uri? = when (i?.action) {
            android.content.Intent.ACTION_VIEW -> i.data
            android.content.Intent.ACTION_SEND ->
                if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) incoming.value = uri
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as DocScanApp).container
        handleIntent(intent)
        // Ignore touches while another window is drawn over the app (tap-jacking protection).
        window.decorView.filterTouchesWhenObscured = true
        setContent {
            val locked by container.security.locked.collectAsState()
            val protect by container.security.protect.collectAsState()
            val mode by container.ui.theme.collectAsState()
            val accent by container.ui.accent.collectAsState()
            val dark = when (mode) {
                com.docscan.app.security.ThemeMode.LIGHT -> false
                com.docscan.app.security.ThemeMode.DARK -> true
                com.docscan.app.security.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(protect) { applyScreenProtection(protect) }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            CompositionLocalProvider(LocalContainer provides container) {
                DocScanTheme(darkTheme = dark, accent = accent) {
                    Box {
                        AppNav(container, incoming) { incoming.value = null }
                        if (locked) LockScreen(onUnlockRequest = ::requestUnlock)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.security.onForegrounded()
    }

    override fun onStop() {
        container.security.onBackgrounded()
        super.onStop()
    }

    private fun requestUnlock() {
        Authenticator.prompt(
            this,
            title = "Unlock Docxels",
            subtitle = "Confirm it's you to open your documents",
            onSuccess = { container.security.unlock() },
        )
    }

    private fun applyScreenProtection(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
