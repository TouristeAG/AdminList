package com.eventmanager.app.data.update

import com.eventmanager.app.platform.AppBuildInfo
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual class UpdateChecker actual constructor(private val platformContext: PlatformContext) {
    actual suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(AppBuildInfo.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            val body = conn.inputStream.bufferedReader().readText()
            val versionMatch = Regex(""""latestVersionName"\s*:\s*"([^"]+)"""").find(body)
            val urlMatch = Regex(""""downloadUrl"\s*:\s*"([^"]+)"""").find(body)
            val codeMatch = Regex(""""latestVersionCode"\s*:\s*(\d+)"""").find(body)
            val latestName = versionMatch?.groupValues?.get(1) ?: AppBuildInfo.VERSION_NAME
            val latestCode = codeMatch?.groupValues?.get(1)?.toIntOrNull() ?: AppBuildInfo.VERSION_CODE
            val url = urlMatch?.groupValues?.get(1)
            if (latestCode <= AppBuildInfo.VERSION_CODE || url.isNullOrBlank()) {
                UpdateCheckResult.NoUpdate
            } else {
                UpdateCheckResult.UpdateAvailable(
                    manifest = UpdateManifest(
                        latestVersionCode = latestCode,
                        latestVersionName = latestName,
                        downloadUrl = url
                    )
                )
            }
        }.getOrElse { UpdateCheckResult.Error(it.message ?: "Update check failed") }
    }
}

actual class UpdateDownloader actual constructor(private val platformContext: PlatformContext) {
    actual suspend fun downloadUpdate(downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)
        try {
            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            val fileLength = connection.contentLength
            val outputFile = File(System.getProperty("java.io.tmpdir"), "noctulist-update.bin")
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        if (fileLength > 0) emit(DownloadState.Downloading(((total * 100) / fileLength).toInt()))
                    }
                }
            }
            emit(DownloadState.Downloaded(outputFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    actual fun installUpdate(filePath: String) {
        openUrl("file://$filePath")
    }
}
