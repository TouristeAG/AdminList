package com.eventmanager.app.ui.scaling

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun ResolutionScaledContent(
    platformContext: PlatformContext,
    settingsManager: SettingsManager,
    content: @Composable () -> Unit
) {
    content()
}
