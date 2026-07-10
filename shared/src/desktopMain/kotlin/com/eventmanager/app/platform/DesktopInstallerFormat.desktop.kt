package com.eventmanager.app.platform

actual fun currentDesktopInstallerFormat(): DesktopInstallerFormat? {
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.startsWith("Mac", ignoreCase = true) -> DesktopInstallerFormat.Dmg
        osName.startsWith("Windows", ignoreCase = true) -> DesktopInstallerFormat.Msi
        osName.contains("linux", ignoreCase = true) -> DesktopInstallerFormat.Deb
        else -> null
    }
}
