package com.eventmanager.app.data.update

import android.content.Context
import com.eventmanager.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateManifest(
    val latestVersionCode: Int,
    val latestVersionName: String,
    // Allow decimals/strings like "0.9" instead of requiring an int
    val minSupportedVersionCode: String? = null,
    val changelogShort: String? = null,
    val downloadUrl: String? = null,
    val storeUrl: String? = null
)

sealed class UpdateCheckResult {
    object NoUpdate : UpdateCheckResult()
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isRequired: Boolean
    ) : UpdateCheckResult()

    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateChecker(
    private val context: Context
) {

    private val gson = Gson()

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(BuildConfig.UPDATE_MANIFEST_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }

            return@withContext connection.inputStream.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val response = buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        append(line)
                    }
                }

                val manifest = gson.fromJson(response, UpdateManifest::class.java)
                val currentVersionCode = BuildConfig.VERSION_CODE

                if (manifest.latestVersionCode <= currentVersionCode) {
                    UpdateCheckResult.NoUpdate
                } else {
                    val isRequired = manifest.minSupportedVersionCode?.let { minString ->
                        // Try to interpret the minSupportedVersionCode as a number (supports decimals)
                        val min = minString.toDoubleOrNull()
                        min?.let {
                            currentVersionCode.toDouble() < it
                        } ?: false
                    } ?: false
                    UpdateCheckResult.UpdateAvailable(manifest, isRequired)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error while checking for updates")
        }
    }
}

