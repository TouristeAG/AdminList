package com.eventmanager.app.platform

import com.eventmanager.app.data.sync.FileManager
import java.io.File

actual class PlatformFileManager actual constructor(private val context: PlatformContext) {
    private val delegate = FileManager(context.androidContext)
    private val serviceAccountFileName = "service_account_key.json"

    actual fun getServiceAccountFile(): File? {
        val path = delegate.getServiceAccountKeyPath() ?: return null
        return File(path)
    }

    actual fun saveServiceAccountJson(json: String): Boolean = runCatching {
        val assetsDir = File(context.androidContext.filesDir, "assets").also { it.mkdirs() }
        File(assetsDir, serviceAccountFileName).writeText(json)
        true
    }.getOrDefault(false)

    actual fun readServiceAccountJson(): String? = getServiceAccountFile()?.readText()

    actual fun getLogsDirectory(): File =
        File(context.androidContext.filesDir, "logs").also { it.mkdirs() }

    actual fun getCacheDirectory(): File = context.androidContext.cacheDir

    actual fun getUpdatesDirectory(): File =
        File(context.androidContext.cacheDir, "updates").also { it.mkdirs() }

    actual suspend fun pickServiceAccountJsonFile(): String? = null
}
