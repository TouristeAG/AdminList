package com.eventmanager.app.platform

import com.eventmanager.app.platform.elapsedRealtimeMs

import androidx.compose.runtime.Composable
import com.eventmanager.app.ui.AdminSessionHost

expect fun recreateActivity(platformContext: PlatformContext)

expect fun getAdminSessionHost(platformContext: PlatformContext): AdminSessionHost?

expect fun finishApplication(platformContext: PlatformContext)

expect fun openDateSettings(platformContext: PlatformContext)

expect fun openExternalUrl(platformContext: PlatformContext, url: String)

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

expect fun elapsedRealtimeMs(): Long
