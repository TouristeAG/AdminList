package com.eventmanager.app.data.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual class UpdateDownloader actual constructor(private val platformContext: PlatformContext) {
  private val context = platformContext.androidContext

    actual suspend fun downloadUpdate(downloadUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)
        try {
            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                requestMethod = "GET"
                doInput = true
            }
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@flow
            }
            val fileLength = connection.contentLength
            val outputFile = File(context.cacheDir, "update.apk")
            if (outputFile.exists()) outputFile.delete()
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileLength > 0) {
                            emit(DownloadState.Downloading(((totalBytesRead * 100) / fileLength).toInt()))
                        }
                    }
                    output.flush()
                }
            }
            emit(DownloadState.Downloaded(outputFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)

    actual fun installUpdate(filePath: String) {
        val apkFile = File(filePath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                setDataAndType(apkUri, "application/vnd.android.package-archive")
            } else {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
        }
        context.startActivity(intent)
    }
}
