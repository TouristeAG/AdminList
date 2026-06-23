package com.eventmanager.app.platform

import java.io.File

/**
 * Local file operations for service account keys, logs, exports, and updates.
 */
expect class PlatformFileManager(context: PlatformContext) {
    fun getServiceAccountFile(): File?
    fun saveServiceAccountJson(json: String): Boolean
    fun readServiceAccountJson(): String?
    fun getLogsDirectory(): File
    fun getCacheDirectory(): File
    fun getUpdatesDirectory(): File
    suspend fun pickServiceAccountJsonFile(): String?
}
