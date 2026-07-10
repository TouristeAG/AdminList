package com.eventmanager.app.platform

import java.io.File

private var desktopContext: PlatformContext? = null

actual class PlatformContext internal constructor(
    val dataDir: File
)

actual fun createPlatformContext(): PlatformContext {
    return desktopContext ?: run {
        val dir = File(System.getProperty("user.home"), ".noctulist").also { it.mkdirs() }
        PlatformContext(dir).also { desktopContext = it }
    }
}

actual val PlatformContext.appDataDir: File
    get() = dataDir

actual val PlatformContext.isDesktop: Boolean
    get() = true
