package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.PlatformContext

internal fun sanitizeProfilePhotoExportFileName(fileName: String): String {
    val trimmed = fileName.trim().ifBlank { "profile.jpg" }
    val withExt = if (trimmed.endsWith(".jpg", ignoreCase = true) || trimmed.endsWith(".jpeg", ignoreCase = true)) {
        trimmed
    } else {
        "$trimmed.jpg"
    }
    val sanitized = withExt.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "profile.jpg" }
    return sanitized.take(80)
}

internal expect object ProfilePhotoExport {
    suspend fun share(platformContext: PlatformContext, url: String, fileName: String, storagePath: String = "")
    suspend fun download(platformContext: PlatformContext, url: String, fileName: String, storagePath: String = "")
}
