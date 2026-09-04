package com.eventmanager.app.data.sync

import com.eventmanager.app.data.remote.FirebaseAuthBridge
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.platform.PlatformFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears local DB tables, preference storage, and credential/cache files so the app
 * returns to a first-launch (setup wizard) state. Does not uninstall the binary.
 */
object FactoryReset {
    suspend fun perform(
        repository: EventManagerRepository,
        settingsManager: SettingsManager,
        fileManager: PlatformFileManager,
    ) {
        withContext(Dispatchers.IO) {
            // The Firebase SDK persists its session outside our preference storage, so without
            // this the app restarts signed in to an org it no longer has any settings for.
            runCatching { FirebaseAuthBridge.signOut() }
            runCatching { repository.clearAllData() }
            runCatching { fileManager.getServiceAccountFile()?.delete() }
            runCatching { fileManager.getGmailOAuthClientFile()?.delete() }
            runCatching { fileManager.clearEmailLogoFile() }
            runCatching { fileManager.getWalletPassCertificateFile()?.delete() }
            runCatching {
                fileManager.getUpdatesDirectory().listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
            runCatching {
                fileManager.getCacheDirectory().listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
            runCatching {
                fileManager.getLogsDirectory().listFiles()?.forEach { child ->
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                }
            }
            settingsManager.clearAllSettings()
        }
    }
}
