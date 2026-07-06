package com.eventmanager.app.platform

import java.io.File

/**
 * Local file operations for service account keys, logs, exports, and updates.
 */
expect class PlatformFileManager(context: PlatformContext) {
    fun getServiceAccountFile(): File?
    fun saveServiceAccountJson(json: String): Boolean
    fun readServiceAccountJson(): String?
    fun getGmailOAuthClientFile(): File?
    fun saveGmailOAuthClientJson(json: String): Boolean
    fun readGmailOAuthClientJson(): String?
    fun getLogsDirectory(): File
    fun getCacheDirectory(): File
    fun getUpdatesDirectory(): File
    suspend fun pickServiceAccountJsonFile(): String?
    suspend fun pickGmailOAuthClientJsonFile(): String?
    suspend fun pickEmailLogoImageFile(): String?
    fun getEmailLogoFile(): File?
    fun clearEmailLogoFile(): Boolean
    fun getWalletPassCertificateFile(): File?
    fun saveWalletPassCertificate(bytes: ByteArray): Boolean
    suspend fun pickWalletPassCertificateFile(): ByteArray?
}
