package com.eventmanager.app.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.eventmanager.app.ui.AdminSessionHost

actual fun recreateActivity(platformContext: PlatformContext) {
    (platformContext.androidContext as? Activity)?.recreate()
}

actual fun getAdminSessionHost(platformContext: PlatformContext): AdminSessionHost? =
    platformContext.androidContext as? AdminSessionHost

actual fun finishApplication(platformContext: PlatformContext) {
    (platformContext.androidContext as? Activity)?.finish()
}

actual fun openDateSettings(platformContext: PlatformContext) {
    val ctx = platformContext.androidContext
    try {
        ctx.startActivity(Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}

actual fun openExternalUrl(platformContext: PlatformContext, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    platformContext.androidContext.startActivity(intent)
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

actual fun elapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()
