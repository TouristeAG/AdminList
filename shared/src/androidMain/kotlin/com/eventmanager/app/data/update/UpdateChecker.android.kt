package com.eventmanager.app.data.update

import com.eventmanager.app.platform.AppBuildInfo
import com.eventmanager.app.platform.PlatformContext
import com.google.gson.Gson
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.settingsManagerFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

actual class UpdateChecker actual constructor(private val platformContext: PlatformContext) {
    private val gson = Gson()

    actual suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val settingsManager = settingsManagerFor(platformContext)
            val manifestUrl = settingsManager.getUpdateManifestUrl()
            val url = URL(manifestUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }

            connection.inputStream.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        append(line)
                    }
                }

                val manifest = gson.fromJson(response, UpdateManifest::class.java)
                val currentVersionCode = AppBuildInfo.VERSION_CODE

                if (manifest.latestVersionCode <= currentVersionCode) {
                    UpdateCheckResult.NoUpdate
                } else {
                    val isRequired = manifest.minSupportedVersionCode?.let { minString ->
                        val min = minString.toDoubleOrNull()
                        min?.let { currentVersionCode.toDouble() < it } ?: false
                    } ?: false
                    UpdateCheckResult.UpdateAvailable(manifest, isRequired)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error while checking for updates")
        }
    }
}
