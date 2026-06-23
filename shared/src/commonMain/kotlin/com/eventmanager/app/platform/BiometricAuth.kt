package com.eventmanager.app.platform

/**
 * Biometric authentication for admin login (fingerprint / Face ID / Windows Hello).
 */
interface BiometricAuth {
    val isAvailable: Boolean
    suspend fun authenticate(title: String, subtitle: String): Boolean
}

expect fun createBiometricAuth(context: PlatformContext): BiometricAuth
