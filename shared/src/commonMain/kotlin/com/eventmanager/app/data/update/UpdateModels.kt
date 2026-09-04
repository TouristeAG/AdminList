package com.eventmanager.app.data.update

import com.eventmanager.app.platform.DesktopInstallerFormat
import com.eventmanager.app.platform.currentDesktopInstallerFormat

data class UpdateManifest(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minSupportedVersionCode: String? = null,
    val changelogShort: String? = null,
    val downloadUrl: String? = null,
    val desktopDownloadUrl: String? = null,
    /** Arch-specific macOS DMGs (preferred when present). */
    val desktopDownloadUrlDmgArm64: String? = null,
    val desktopDownloadUrlDmgX86: String? = null,
    /** Legacy / fallback DMG (used when the arch-specific ones are absent). */
    val desktopDownloadUrlDmg: String? = null,
    val desktopDownloadUrlMsi: String? = null,
    val desktopDownloadUrlExe: String? = null,
    val desktopDownloadUrlDeb: String? = null,
    val desktopDownloadUrlAppImage: String? = null,
    val storeUrl: String? = null,
) {
    fun resolveDownloadUrl(preferDesktopArtifact: Boolean = false): String? {
        if (!preferDesktopArtifact) {
            return downloadUrl?.takeIf { it.isNotBlank() }
        }
        return resolveDesktopDownloadUrl()
    }

    private fun resolveDesktopDownloadUrl(): String? {
        when (currentDesktopInstallerFormat()) {
            DesktopInstallerFormat.Dmg -> {
                // Pick the arch-specific DMG first so Apple Silicon users get a native build.
                val archUrl = if (isAppleSilicon()) desktopDownloadUrlDmgArm64 else desktopDownloadUrlDmgX86
                archUrl?.takeIf { it.isNotBlank() }?.let { return it }
                // Fall back to the legacy single-arch field for older manifests.
                desktopDownloadUrlDmg?.takeIf { it.isNotBlank() }?.let { return it }
            }
            DesktopInstallerFormat.Msi -> {
                desktopDownloadUrlMsi?.takeIf { it.isNotBlank() }?.let { return it }
                desktopDownloadUrlExe?.takeIf { it.isNotBlank() }?.let { return it }
            }
            DesktopInstallerFormat.Exe ->
                desktopDownloadUrlExe?.takeIf { it.isNotBlank() }?.let { return it }
            DesktopInstallerFormat.Deb -> {
                desktopDownloadUrlDeb?.takeIf { it.isNotBlank() }?.let { return it }
                desktopDownloadUrlAppImage?.takeIf { it.isNotBlank() }?.let { return it }
            }
            DesktopInstallerFormat.AppImage ->
                desktopDownloadUrlAppImage?.takeIf { it.isNotBlank() }?.let { return it }
            null -> Unit
        }
        return desktopDownloadUrl?.takeIf { it.isNotBlank() }
    }
}

/**
 * Returns true when running on Apple Silicon (arm64 / aarch64).
 * `os.arch` is "aarch64" on native ARM JVM; "x86_64" under Rosetta.
 */
private fun isAppleSilicon(): Boolean =
    System.getProperty("os.arch").orEmpty().equals("aarch64", ignoreCase = true)

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
