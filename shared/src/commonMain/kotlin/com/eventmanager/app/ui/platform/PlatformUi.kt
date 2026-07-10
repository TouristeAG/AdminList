package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.platform.PlatformContext

expect fun showToast(platformContext: PlatformContext, message: String)

expect fun openEmailClient(platformContext: PlatformContext, to: String, subject: String, body: String)

expect fun openAppSettings(platformContext: PlatformContext)

@Composable
expect fun PlatformWebView(url: String, modifier: Modifier)
