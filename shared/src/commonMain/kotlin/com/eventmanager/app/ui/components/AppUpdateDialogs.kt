package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.update.DownloadState
import com.eventmanager.app.data.update.UpdateCheckResult
import com.eventmanager.app.platform.AppBuildInfo
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.openUrl
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun resolveUpdateFallbackUrl(
    storeUrl: String?,
    settingsStoreUrl: String,
    forDesktop: Boolean,
): String {
    storeUrl?.takeIf { it.isNotBlank() }?.let { return it }
    if (forDesktop && settingsStoreUrl.contains("play.google.com", ignoreCase = true)) {
        return AppBuildInfo.DESKTOP_UPDATE_FALLBACK_URL
    }
    return settingsStoreUrl.ifBlank {
        if (forDesktop) AppBuildInfo.DESKTOP_UPDATE_FALLBACK_URL else AppBuildInfo.UPDATE_FALLBACK_STORE_URL
    }
}

/**
 * Mirrors the Android startup / settings update dialog flow (download progress, install, fallback URL).
 */
@Composable
fun AppUpdateFlowDialog(
    visible: Boolean,
    updateResult: UpdateCheckResult?,
    downloadState: DownloadState,
    fallbackStoreUrl: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onInstall: (String) -> Unit,
) {
    if (!visible) return

    val forDesktop = LocalPlatformContext.current.isDesktop

    when (updateResult) {
        is UpdateCheckResult.UpdateAvailable -> {
            val manifest = updateResult.manifest
            val isRequired = updateResult.isRequired

            when (downloadState) {
                is DownloadState.Downloading -> {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(Res.string.downloading_update)) },
                        text = {
                            Column {
                                LinearProgressIndicator(
                                    progress = { downloadState.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(Res.string.download_progress, downloadState.progress),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                        confirmButton = {},
                    )
                }
                is DownloadState.Downloaded -> {
                    if (forDesktop) {
                        LaunchedEffect(downloadState.filePath) {
                            onInstall(downloadState.filePath)
                        }
                    }
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else onDismiss,
                        properties = if (isRequired) {
                            DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            )
                        } else {
                            DialogProperties()
                        },
                        title = { Text(stringResource(Res.string.download_complete)) },
                        text = {
                            Text(
                                if (forDesktop) {
                                    stringResource(Res.string.download_complete_opening_installer)
                                } else {
                                    stringResource(Res.string.update_available_message)
                                },
                            )
                        },
                        confirmButton = {
                            if (forDesktop) {
                                TextButton(onClick = onDismiss) {
                                    Text(stringResource(Res.string.ok))
                                }
                            } else {
                                TextButton(onClick = {
                                    onInstall(downloadState.filePath)
                                    onDismiss()
                                }) {
                                    Text(stringResource(Res.string.install_update))
                                }
                            }
                        },
                        dismissButton = if (!isRequired && !forDesktop) {
                            {
                                TextButton(onClick = onDismiss) {
                                    Text(stringResource(Res.string.later))
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                is DownloadState.Error -> {
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else onDismiss,
                        title = { Text(stringResource(Res.string.download_error_title)) },
                        text = {
                            Text(stringResource(Res.string.download_error_message, downloadState.message))
                        },
                        confirmButton = {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(Res.string.ok))
                            }
                        },
                    )
                }
                else -> {
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else onDismiss,
                        properties = if (isRequired) {
                            DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            )
                        } else {
                            DialogProperties()
                        },
                        title = {
                            Text(stringResource(Res.string.update_available_title, manifest.latestVersionName))
                        },
                        text = {
                            Text(
                                manifest.changelogShort
                                    ?: if (isRequired) {
                                        stringResource(Res.string.update_required_message)
                                    } else {
                                        stringResource(Res.string.update_available_message)
                                    },
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val downloadUrl = manifest.resolveDownloadUrl(forDesktop)
                                if (downloadUrl != null) {
                                    onDownload(downloadUrl)
                                } else {
                                    openUrl(
                                        resolveUpdateFallbackUrl(
                                            storeUrl = manifest.storeUrl,
                                            settingsStoreUrl = fallbackStoreUrl,
                                            forDesktop = forDesktop,
                                        ),
                                    )
                                    onDismiss()
                                }
                            }) {
                                Text(stringResource(Res.string.update_now))
                            }
                        },
                        dismissButton = if (!isRequired) {
                            {
                                TextButton(onClick = onDismiss) {
                                    Text(stringResource(Res.string.later))
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        is UpdateCheckResult.NoUpdate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.up_to_date_title)) },
                text = { Text(stringResource(Res.string.up_to_date_message)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
            )
        }
        is UpdateCheckResult.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.update_error_title)) },
                text = { Text(stringResource(Res.string.update_error_message, updateResult.message)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
            )
        }
        null -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.checking_for_updates_title)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.checking_for_updates_message))
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
fun UpdateSourcesDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
) {
    val forDesktop = LocalPlatformContext.current.isDesktop
    val defaultManifestUrl = AppBuildInfo.UPDATE_MANIFEST_URL
    val defaultStoreUrl = if (forDesktop) {
        AppBuildInfo.DESKTOP_UPDATE_FALLBACK_URL
    } else {
        AppBuildInfo.UPDATE_FALLBACK_STORE_URL
    }

    var githubUrl by remember { mutableStateOf(settingsManager.getUpdateManifestUrl()) }
    var storeUrl by remember { mutableStateOf(settingsManager.getUpdateStoreUrl()) }

    val isGithubUrlChanged = githubUrl != defaultManifestUrl
    val isStoreUrlChanged = storeUrl != defaultStoreUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.update_sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.update_sources_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                Column {
                    Text(
                        text = stringResource(Res.string.update_sources_github_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(4.dp))
                    OutlinedTextField(
                        value = githubUrl,
                        onValueChange = { githubUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(defaultManifestUrl, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (isGithubUrlChanged) {
                                IconButton(onClick = { githubUrl = defaultManifestUrl }) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = stringResource(Res.string.update_sources_reset),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }

                Column {
                    Text(
                        text = stringResource(Res.string.update_sources_store_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(4.dp))
                    OutlinedTextField(
                        value = storeUrl,
                        onValueChange = { storeUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(defaultStoreUrl, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (isStoreUrlChanged) {
                                IconButton(onClick = { storeUrl = defaultStoreUrl }) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = stringResource(Res.string.update_sources_reset),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                settingsManager.saveUpdateManifestUrl(githubUrl)
                settingsManager.saveUpdateStoreUrl(storeUrl)
                onDismiss()
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
