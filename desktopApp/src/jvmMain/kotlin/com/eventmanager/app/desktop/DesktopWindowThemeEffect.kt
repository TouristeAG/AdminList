package com.eventmanager.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.WindowScope
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.desktop.DesktopWindowAppearance
import com.eventmanager.app.ui.platform.AppAppearanceState
import com.eventmanager.app.ui.theme.ThemeMode

@Composable
fun WindowScope.DesktopWindowThemeEffect(platformContext: PlatformContext) {
    val settingsManager = settingsManagerFor(platformContext)
    val appearanceTheme by AppAppearanceState::themeMode
    val themeRefreshNonce by AppAppearanceState::themeRefreshNonce
    val systemDark = isSystemInDarkTheme()

    val themeMode = ThemeMode.fromString(appearanceTheme ?: settingsManager.getThemeMode())
    val preferDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DEFAULT -> systemDark
    }

    LaunchedEffect(preferDark, themeRefreshNonce) {
        DesktopWindowAppearance.applyToWindow(window, preferDark)
    }
}
