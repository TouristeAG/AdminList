package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext

expect fun isAppIconChangeSupported(): Boolean

@Composable
expect fun rememberServiceAccountJsonPicker(
    platformContext: PlatformContext,
    onPicked: (Result<String>) -> Unit
): () -> Unit

@Composable
expect fun SettingsWebView(url: String, modifier: androidx.compose.ui.Modifier)

expect fun showPlatformToast(platformContext: PlatformContext, message: String)
