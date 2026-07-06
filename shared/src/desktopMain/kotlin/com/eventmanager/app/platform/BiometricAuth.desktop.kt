package com.eventmanager.app.platform

import com.eventmanager.app.platform.biometric.DesktopBiometricCapabilityProbe
import com.eventmanager.app.platform.biometric.DesktopBiometricResult
import com.eventmanager.app.platform.biometric.NativeBiometricEngine
import com.sun.jna.WString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createBiometricAuth(context: PlatformContext): BiometricAuth =
    DesktopBiometricAuth()

private class DesktopBiometricAuth : BiometricAuth {
    private val capability by lazy { DesktopBiometricCapabilityProbe.probe() }
    private val engine by lazy { NativeBiometricEngine.getOrNull() }

    override val isAvailable: Boolean
        get() = engine != null && capability.hardwareAvailable && !capability.noneEnrolled

    override val isNoneEnrolled: Boolean
        get() = engine != null && capability.noneEnrolled

    override suspend fun authenticate(title: String, subtitle: String): Boolean {
        val native = engine ?: return false
        val reason = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString("\n")
        return withContext(Dispatchers.Main) {
            runCatching {
                when (native.requestAuth(WString(reason))) {
                    DesktopBiometricResult.SUCCESS -> true
                    else -> false
                }
            }.getOrDefault(false)
        }
    }
}
