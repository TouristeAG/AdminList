package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.ui.platform.AppAppearanceState

@Composable
fun backgroundAwareContainerColor(animationStyle: String): Color {
    return if (BackgroundAnimationStyle.isEnabled(animationStyle)) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surface
    }
}

@Composable
fun rememberBilleterieBackgroundAnimationStyle(settingsManager: SettingsManager): String {
    val refreshNonce by AppAppearanceState::refreshNonce
    return remember(refreshNonce) { settingsManager.getBilleterieBackgroundAnimationStyle() }
}

@Composable
fun rememberPosBackgroundAnimationStyle(settingsManager: SettingsManager): String {
    val refreshNonce by AppAppearanceState::refreshNonce
    return remember(refreshNonce) { settingsManager.getPosBackgroundAnimationStyle() }
}

@Composable
fun billeterieBackgroundAwareContainerColor(settingsManager: SettingsManager): Color {
    return backgroundAwareContainerColor(rememberBilleterieBackgroundAnimationStyle(settingsManager))
}

@Composable
fun posBackgroundAwareContainerColor(settingsManager: SettingsManager): Color {
    return backgroundAwareContainerColor(rememberPosBackgroundAnimationStyle(settingsManager))
}

@Composable
fun billeterieBackgroundAwareTopAppBarColors(): TopAppBarColors {
    val colorScheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = colorScheme.onSurface,
        titleContentColor = colorScheme.onSurface,
        actionIconContentColor = colorScheme.onSurface,
    )
}

@Composable
fun posBackgroundAwareTopAppBarColors(): TopAppBarColors = billeterieBackgroundAwareTopAppBarColors()

@Composable
fun AppBackgroundAnimation(
    style: String,
    opacity: Float,
    settingsManager: SettingsManager,
    isDesktop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shouldRender = BackgroundAnimationStyle.isEnabled(style) && opacity > 0f

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (shouldRender) opacity else 0f),
        ) {
            if (!shouldRender) return@BoxWithConstraints

            when (BackgroundAnimationStyle.fromStored(style)) {
                BackgroundAnimationStyle.NONE -> Unit
                BackgroundAnimationStyle.ARCHES -> AnimatedBackground(
                    settingsManager = settingsManager,
                    enabled = true,
                    isDesktop = isDesktop,
                    modifier = Modifier.fillMaxSize(),
                )
                BackgroundAnimationStyle.TOPOGRAPHIC -> TopographicBackground(
                    settingsManager = settingsManager,
                    enabled = true,
                    isDesktop = isDesktop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
