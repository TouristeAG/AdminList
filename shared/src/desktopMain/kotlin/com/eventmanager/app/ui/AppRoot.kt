package com.eventmanager.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.desktop.DesktopAppShell
import com.eventmanager.app.ui.desktop.LocalDesktopNavigation
import com.eventmanager.app.ui.desktop.rememberDesktopNavigationHolder
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createAppStorage

@Composable
actual fun AppRoot(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit
) {
    val settingsManager = androidx.compose.runtime.remember(platformContext) {
        SettingsManager(createAppStorage(platformContext))
    }
    val navigation = rememberDesktopNavigationHolder(settingsManager)
    CompositionLocalProvider(LocalDesktopNavigation provides navigation) {
        DesktopAppShell(platformContext = platformContext, onThemeModeChanged = onThemeModeChanged) {
            AppRootContent(platformContext = platformContext, onThemeModeChanged = onThemeModeChanged)
        }
    }
}
