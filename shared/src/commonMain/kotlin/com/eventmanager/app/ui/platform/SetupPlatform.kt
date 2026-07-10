package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.platform.PlatformContext

expect fun supportsResolutionScaleStep(): Boolean

expect fun applyLocaleOrThemeChange(platformContext: PlatformContext)

/** Updates UI language without resetting navigation (desktop). */
expect fun applyLocaleChange(platformContext: PlatformContext)

/** Updates light/dark mode or color palette without resetting navigation (desktop). */
expect fun applyThemeAppearanceChange(platformContext: PlatformContext)

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
