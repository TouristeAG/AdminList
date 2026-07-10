package com.eventmanager.app.platform.biometric

internal data class DesktopBiometricCapability(
    val hardwareAvailable: Boolean,
    val noneEnrolled: Boolean
)

internal object DesktopBiometricCapabilityProbe {
    fun probe(): DesktopBiometricCapability {
        if (NativeBiometricEngine.getOrNull() == null) {
            return DesktopBiometricCapability(hardwareAvailable = false, noneEnrolled = false)
        }
        val os = System.getProperty("os.name").orEmpty()
        return when {
            os.startsWith("Mac OS") -> probeMacOs()
            os.startsWith("Windows") -> probeWindows()
            os.contains("linux", ignoreCase = true) -> probeLinux()
            else -> DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
        }
    }

    private fun probeMacOs(): DesktopBiometricCapability {
        return runCatching {
            val process = ProcessBuilder("bioutil", "-r")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val lower = output.lowercase()
            when {
                lower.contains("not enrolled") ->
                    DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = true)
                lower.contains("not available") ->
                    DesktopBiometricCapability(hardwareAvailable = false, noneEnrolled = false)
                lower.contains("touch id") || lower.contains("face id") || lower.contains("biometric") ->
                    DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
                else -> DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
            }
        }.getOrElse {
            DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
        }
    }

    private fun probeWindows(): DesktopBiometricCapability {
        val script = "Add-Type -AssemblyName System.Runtime.WindowsRuntime; " +
            "[Windows.Security.Credentials.UI.UserConsentVerifier,Windows.Security.Credentials.UI,ContentType=WindowsRuntime] | Out-Null; " +
            "[Windows.Security.Credentials.UI.UserConsentVerifier]::CheckAvailabilityAsync().GetAwaiter().GetResult().ToString()"
        return runCatching {
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            when {
                output.contains("Available", ignoreCase = true) ->
                    DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
                output.contains("NotConfiguredForUser", ignoreCase = true) ->
                    DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = true)
                output.contains("DeviceNotPresent", ignoreCase = true) ->
                    DesktopBiometricCapability(hardwareAvailable = false, noneEnrolled = false)
                output.contains("DisabledByPolicy", ignoreCase = true) ->
                    DesktopBiometricCapability(hardwareAvailable = false, noneEnrolled = false)
                else -> DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
            }
        }.getOrElse {
            DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
        }
    }

    private fun probeLinux(): DesktopBiometricCapability {
        val user = System.getenv("USER")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.name")
        return runCatching {
            val process = ProcessBuilder("fprintd-list", user)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val lower = output.lowercase()
            when {
                lower.contains("no devices") || lower.contains("not found") ->
                    DesktopBiometricCapability(hardwareAvailable = false, noneEnrolled = false)
                lower.contains("no fingerprints") || lower.contains("no prints") ->
                    DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = true)
                else -> DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
            }
        }.getOrElse {
            DesktopBiometricCapability(hardwareAvailable = true, noneEnrolled = false)
        }
    }
}
