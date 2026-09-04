package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.PlatformContext

expect fun supportsResolutionScaleStep(): Boolean

fun applyLocaleOrThemeChange(platformContext: PlatformContext) {
    applyLocaleChange(platformContext)
}

fun applyLocaleChange(platformContext: PlatformContext) {
    val settingsManager = settingsManagerFor(platformContext)
    AppAppearanceState.notifyLocaleChanged(settingsManager.getLanguage())
}

fun applyThemeAppearanceChange(platformContext: PlatformContext) {
    val settingsManager = settingsManagerFor(platformContext)
    AppAppearanceState.notifyThemeAppearanceChanged(settingsManager.getThemeMode())
    afterThemeAppearanceChange(platformContext)
}

internal expect fun afterThemeAppearanceChange(platformContext: PlatformContext)

@Composable
expect fun ServiceAccountKeyUploadButton(
    platformContext: PlatformContext,
    onStatusUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
expect fun SetupLayoutScalePage(
    resolutionScale: Float,
    onSave: (Float) -> Unit,
    onUseRecommended: () -> Unit,
    modifier: Modifier = Modifier
)
