package com.docscan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.docscan.app.ui.AppNav
import com.docscan.app.ui.theme.DocScanTheme

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DocScanApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                DocScanTheme { AppNav(container) }
            }
        }
    }
}
