package com.docscan.app.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint / face / screen-lock check using the system prompt. Nothing is stored by the app. */
object Authenticator {
    private val allowed: Int get() = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** True when the phone has a screen lock or biometric the prompt can use. */
    fun available(activity: FragmentActivity): Boolean {
        if (Build.VERSION.SDK_INT < 30) {
            // The combined authenticator type is only supported from Android 11; older phones use the legacy flag.
            val km = activity.getSystemService(android.app.KeyguardManager::class.java)
            return km?.isDeviceSecure == true
        }
        return BiometricManager.from(activity).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (cancelled: Boolean) -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED,
                    )
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).apply {
            if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(allowed)
            else @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
        }.build()
        prompt.authenticate(info)
    }
}
