package com.eventmanager.app.data.update

import com.google.gson.Gson

object UpdateManifestEvaluator {
    private val gson = Gson()

    fun parseManifest(json: String): UpdateManifest =
        gson.fromJson(json, UpdateManifest::class.java)

    fun evaluate(
        manifest: UpdateManifest,
        currentVersionCode: Int,
        preferDesktopArtifact: Boolean = false,
    ): UpdateCheckResult {
        if (manifest.latestVersionCode <= currentVersionCode) {
            return UpdateCheckResult.NoUpdate
        }

        val isRequired = manifest.minSupportedVersionCode?.let { minString ->
            minString.toDoubleOrNull()?.let { min ->
                currentVersionCode.toDouble() < min
            } ?: false
        } ?: false

        val resolvedManifest = if (preferDesktopArtifact) {
            val desktopUrl = manifest.resolveDownloadUrl(preferDesktopArtifact = true)
            if (desktopUrl != null) {
                manifest.copy(downloadUrl = desktopUrl)
            } else {
                manifest
            }
        } else {
            manifest
        }

        return UpdateCheckResult.UpdateAvailable(resolvedManifest, isRequired)
    }
}
