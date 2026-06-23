package com.eventmanager.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import com.eventmanager.app.ui.AdminSessionHost
import java.awt.Desktop
import java.net.URI

actual fun recreateActivity(platformContext: PlatformContext) { /* desktop applies settings live */ }

actual fun getAdminSessionHost(platformContext: PlatformContext): AdminSessionHost? = null

actual fun finishApplication(platformContext: PlatformContext) {
    kotlin.system.exitProcess(0)
}

actual fun openDateSettings(platformContext: PlatformContext) {
    openUrl("x-apple.systempreferences:com.apple.preference.datetime")
}

actual fun openExternalUrl(platformContext: PlatformContext, url: String) {
    openUrl(url)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (!enabled) return
}

actual fun elapsedRealtimeMs(): Long = System.currentTimeMillis()
