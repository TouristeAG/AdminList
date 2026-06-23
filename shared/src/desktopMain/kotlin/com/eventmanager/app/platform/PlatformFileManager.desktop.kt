package com.eventmanager.app.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformFileManager actual constructor(private val context: PlatformContext) {
    private fun serviceAccountPath(): File = File(context.appDataDir, "service_account.json")

    actual fun getServiceAccountFile(): File? =
        serviceAccountPath().takeIf { it.exists() }

    actual fun saveServiceAccountJson(json: String): Boolean = runCatching {
        serviceAccountPath().writeText(json)
        true
    }.getOrDefault(false)

    actual fun readServiceAccountJson(): String? =
        serviceAccountPath().takeIf { it.exists() }?.readText()

    actual fun getLogsDirectory(): File =
        File(context.appDataDir, "logs").also { it.mkdirs() }

    actual fun getCacheDirectory(): File =
        File(context.appDataDir, "cache").also { it.mkdirs() }

    actual fun getUpdatesDirectory(): File =
        File(context.appDataDir, "updates").also { it.mkdirs() }

    actual suspend fun pickServiceAccountJsonFile(): String? = suspendCoroutine { cont ->
        val chooser = JFileChooser().apply {
            dialogTitle = "Select Google Service Account JSON"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            cont.resume(runCatching { file.readText() }.getOrNull())
        } else {
            cont.resume(null)
        }
    }
}
