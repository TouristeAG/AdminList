package com.eventmanager.app.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual fun createBiometricAuth(context: PlatformContext): BiometricAuth =
    AndroidBiometricAuth(context)

private class AndroidBiometricAuth(private val context: PlatformContext) : BiometricAuth {
    override val isAvailable: Boolean
        get() {
            val manager = BiometricManager.from(context.androidContext)
            return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    override suspend fun authenticate(title: String, subtitle: String): Boolean {
        val activity = context.androidContext as? FragmentActivity ?: return false
        return suspendCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        cont.resume(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        cont.resume(false)
                    }
                    override fun onAuthenticationFailed() {
                        cont.resume(false)
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Cancel")
                    .build()
            )
        }
    }
}
