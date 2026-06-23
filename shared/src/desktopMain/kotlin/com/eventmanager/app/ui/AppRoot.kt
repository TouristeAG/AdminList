package com.eventmanager.app.ui

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.AppRootContent
import com.eventmanager.app.ui.desktop.DesktopAppShell

@Composable
actual fun AppRoot(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit
) {
    DesktopAppShell(platformContext = platformContext, onThemeModeChanged = onThemeModeChanged) {
        AppRootContent(platformContext = platformContext, onThemeModeChanged = onThemeModeChanged)
    }
}
