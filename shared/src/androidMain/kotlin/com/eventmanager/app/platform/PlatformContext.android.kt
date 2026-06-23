package com.eventmanager.app.platform

import android.content.Context
import java.io.File

actual class PlatformContext(val androidContext: Context)

actual fun createPlatformContext(): PlatformContext {
    throw IllegalStateException("Use createPlatformContext(context) on Android")
}

fun createPlatformContext(context: Context): PlatformContext = PlatformContext(context.applicationContext)

actual val PlatformContext.appDataDir: File
    get() = androidContext.filesDir

actual val PlatformContext.isDesktop: Boolean
    get() = false
