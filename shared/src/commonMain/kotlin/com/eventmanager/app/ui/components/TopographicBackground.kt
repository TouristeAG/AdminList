package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.sync.SettingsManager

/**
 * Topographic line art via GPU shaders (desktop / Android 13+).
 * Falls back to a lightweight CPU path on older Android devices.
 *
 * @see <a href="https://blog.scottlogic.com/2021/09/09/topographic-line-art-with-webgl.html">Topographic Line Art with WebGL</a>
 */
@Composable
fun TopographicBackground(
    settingsManager: SettingsManager,
    enabled: Boolean = true,
    isDesktop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animationMultiplier = remember { settingsManager.getAnimationIntensityMultiplier() }
    val shouldAnimate = enabled && animationMultiplier > 0f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (!shouldAnimate) return@BoxWithConstraints

        val colorScheme = MaterialTheme.colorScheme
        val lineColors = remember(isDesktop, colorScheme) {
            backgroundTopographicLineColors(isDesktop, colorScheme)
        }
        val config = remember(isDesktop) { topographicConfig(isDesktop) }

        TopographicBackgroundPlatform(
            lineColors = lineColors,
            config = config,
            animationMultiplier = animationMultiplier,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
