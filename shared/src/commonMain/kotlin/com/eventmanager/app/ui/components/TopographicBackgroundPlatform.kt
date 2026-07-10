package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun TopographicBackgroundPlatform(
    lineColors: BackgroundLineColors,
    config: TopographicConfig,
    animationMultiplier: Float,
    modifier: Modifier = Modifier,
)
