package com.eventmanager.app.data.update

import com.eventmanager.app.data.update.UpdateManifestEvaluator
import com.eventmanager.app.platform.installedVersionCode
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.data.sync.settingsManagerFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

actual class UpdateChecker actual constructor(private val platformContext: PlatformContext) {
    actual suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val settingsManager = settingsManagerFor(platformContext)
            val manifestUrl = settingsManager.getUpdateManifestUrl()
            val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
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

                val manifest = UpdateManifestEvaluator.parseManifest(response)
                UpdateManifestEvaluator.evaluate(
                    manifest = manifest,
                    currentVersionCode = platformContext.installedVersionCode(),
                    preferDesktopArtifact = false,
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error while checking for updates")
        }
    }
}
