package com.eventmanager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Material You dynamic colors on Android 12+ when the color theme is "system".
 * Returns null on desktop and on older Android versions (caller uses static fallback).
 */
@Composable
expect fun platformSystemColorScheme(darkTheme: Boolean): ColorScheme?

/** Platform-specific chrome (status bar, etc.) after the color scheme is resolved. */
@Composable
expect fun PlatformThemeSideEffects(darkTheme: Boolean, colorScheme: ColorScheme)
