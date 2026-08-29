package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.SyncResult
import com.eventmanager.app.data.sync.TwoWaySyncService
import kotlinx.coroutines.launch

/**
 * One-way Room → Google Sheets export while Firebase is the active sync backend.
 * Does not participate in bidirectional sync.
 *
 * Uses [SettingsManager.withSpreadsheetIdOverride] so a crash mid-export cannot leave the
 * primary spreadsheet ID pointing at the mirror target.
 */
class SheetsMirrorExporter(
    private val settingsManager: SettingsManager,
    private val twoWaySyncService: TwoWaySyncService,
) {
    suspend fun exportNow(): SyncResult {
        if (settingsManager.getBackendType() != BackendType.FIREBASE) {
            return SyncResult.Error("Sheets mirror is only available in Firebase mode")
        }
        if (!settingsManager.isSheetsMirrorEnabled()) {
            return SyncResult.Error("Sheets mirror is disabled in settings")
        }
        val mirrorId = settingsManager.getSheetsMirrorSpreadsheetId()
            .ifBlank { settingsManager.getSpreadsheetId() }
        if (mirrorId.isBlank() || mirrorId == "YOUR_SPREADSHEET_ID_HERE") {
            return SyncResult.Error("Configure a Sheets mirror spreadsheet ID first")
        }
        return try {
            settingsManager.withSpreadsheetIdOverride(mirrorId) {
                twoWaySyncService.backupToGoogleSheets()
            }
            settingsManager.setSheetsMirrorLastExportAt(System.currentTimeMillis())
            SyncResult.Success("Sheets mirror export completed")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Mirror export failed")
        }
    }

    /**
     * Optional scheduled one-way export while Firebase is active.
     * Interval 0 (default) = manual only.
     */
    fun startScheduled(scope: kotlinx.coroutines.CoroutineScope): kotlinx.coroutines.Job? {
        val minutes = settingsManager.getSheetsMirrorIntervalMinutes()
        if (!settingsManager.isSheetsMirrorEnabled() || minutes <= 0) return null
        return scope.launch {
            while (true) {
                kotlinx.coroutines.delay(minutes.coerceAtLeast(5) * 60_000L)
                runCatching { exportNow() }
            }
        }
    }
}
