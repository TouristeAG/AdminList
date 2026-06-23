package com.eventmanager.app.data.update

import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.flow.Flow

expect class UpdateChecker(platformContext: PlatformContext) {
    suspend fun checkForUpdates(): UpdateCheckResult
}

expect class UpdateDownloader(platformContext: PlatformContext) {
    suspend fun downloadUpdate(downloadUrl: String): Flow<DownloadState>
    fun installUpdate(filePath: String)
}
