package com.eventmanager.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun AppRoot(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit
) {
    CompositionLocalProvider(LocalPlatformContext provides platformContext) {
        AppRootContent(platformContext = platformContext, onThemeModeChanged = onThemeModeChanged)
    }
}
