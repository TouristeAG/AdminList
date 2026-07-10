package com.eventmanager.app.platform

enum class DesktopInstallerFormat {
    Dmg,
    Msi,
    Exe,
    Deb,
    AppImage,
}

/** Returns the installer type for the current desktop OS, or null on mobile. */
expect fun currentDesktopInstallerFormat(): DesktopInstallerFormat?
