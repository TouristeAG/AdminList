package com.eventmanager.app.platform

import java.awt.Desktop
import java.net.URI

/**
 * Opens HTTPS/HTTP URLs in the user's default browser.
 *
 * Packaged desktop apps (jpackage DMG / MSI / EXE / Deb / AppImage) often fail silently with
 * [Desktop.browse] while `./gradlew :desktopApp:run` works — prefer the OS launcher on all OSes.
 */
internal fun openExternalBrowser(url: String) {
    val trimmed = url.trim()
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "Refusing to open non-http(s) URL"
    }
    if (isPackagedDesktopRuntime()) {
        if (tryOsLauncher(trimmed)) return
        if (tryDesktopBrowse(trimmed)) return
    } else {
        if (tryDesktopBrowse(trimmed)) return
        if (tryOsLauncher(trimmed)) return
    }
    error("Could not open the system browser for Google Sign-In.")
}

private fun tryDesktopBrowse(url: String): Boolean =
    runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(URI(url))
        true
    }.getOrDefault(false)

private fun tryOsLauncher(url: String): Boolean {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val commands: List<Array<String>> = when {
        "mac" in os || "darwin" in os -> listOf(arrayOf("open", url))
        "win" in os -> listOf(arrayOf("cmd", "/c", "start", "", url))
        else -> listOf(
            arrayOf("xdg-open", url),
            arrayOf("gio", "open", url),
            arrayOf("sensible-browser", url),
        )
    }
    for (command in commands) {
        if (runDetached(command)) return true
    }
    return false
}

private fun runDetached(command: Array<String>): Boolean =
    runCatching {
        ProcessBuilder(*command).start()
        true
    }.getOrDefault(false)
