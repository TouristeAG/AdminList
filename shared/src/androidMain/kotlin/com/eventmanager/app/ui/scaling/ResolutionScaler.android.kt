package com.eventmanager.app.ui.scaling

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun ResolutionScaledContent(
    platformContext: PlatformContext,
    settingsManager: SettingsManager,
    content: @Composable () -> Unit
) {
    val context = platformContext.androidContext
    val scale = remember(settingsManager) { settingsManager.getResolutionScale() }
    val scaledContext = remember(context, scale) {
        applyResolutionScaling(context, scale)
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, scale) {
        Density(baseDensity.density / scale, baseDensity.fontScale / scale)
    }
    CompositionLocalProvider(
        LocalContext provides scaledContext,
        LocalDensity provides scaledDensity
    ) {
        content()
    }
}

@Suppress("DEPRECATION")
private fun applyResolutionScaling(context: Context, scale: Float): Context {
    val originalMetrics = context.resources.displayMetrics
    val newMetrics = android.util.DisplayMetrics().apply {
        setTo(originalMetrics)
        density = originalMetrics.density / scale
        scaledDensity = originalMetrics.scaledDensity / scale
    }
    val newConfig = android.content.res.Configuration(context.resources.configuration).apply {
        densityDpi = (newMetrics.density * 160).toInt()
    }
    return context.createConfigurationContext(newConfig)
}
