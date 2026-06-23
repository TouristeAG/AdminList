package com.eventmanager.app.data.update

data class UpdateManifest(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minSupportedVersionCode: String? = null,
    val changelogShort: String? = null,
    val downloadUrl: String? = null,
    val storeUrl: String? = null
)

sealed class UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult()
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isRequired: Boolean = false
    ) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data class Downloaded(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
