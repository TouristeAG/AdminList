package com.eventmanager.app.ui

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext

@Composable
expect fun AppRootContent(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit = {}
)
