package com.eventmanager.app.platform

actual fun createBiometricAuth(context: PlatformContext): BiometricAuth =
    DesktopBiometricAuth()

private class DesktopBiometricAuth : BiometricAuth {
    override val isAvailable: Boolean = false
    override suspend fun authenticate(title: String, subtitle: String): Boolean = false
}
