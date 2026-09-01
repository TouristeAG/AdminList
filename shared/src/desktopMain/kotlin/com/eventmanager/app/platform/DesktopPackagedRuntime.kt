package com.eventmanager.app.platform

import java.io.File

/**
 * True when running inside a jpackage-produced installer (DMG / MSI / EXE / Deb / AppImage),
 * as opposed to `./gradlew :desktopApp:run` with a full JDK.
 *
 * Packaged desktop runtimes are jlink-trimmed: Jetty OAuth, gRPC Firestore, and [Desktop.browse]
 * fail or hang on macOS, Windows, and Linux alike — not only on macOS.
 */
internal fun isPackagedDesktopRuntime(): Boolean {
    val javaHome = System.getProperty("java.home").orEmpty().replace('\\', '/')
    if (javaHome.contains("/runtime", ignoreCase = true)) return true
    // jlink custom runtime images omit the JDK source archive.
    val srcZip = File(System.getProperty("java.home"), "lib/src.zip")
    if (!srcZip.exists()) {
        val classPath = System.getProperty("java.class.path", "")
        val looksLikeDevRun = classPath.contains("gradle", ignoreCase = true) ||
            classPath.contains("build/classes", ignoreCase = true) ||
            classPath.contains("build/compose", ignoreCase = true)
        if (!looksLikeDevRun) return true
    }
    return false
}
