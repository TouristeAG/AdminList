package com.eventmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.isDesktop
import java.util.Calendar

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    primaryContainer = Color(0xFF2D1B69),
    onPrimaryContainer = Color(0xFFE1E1E1),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFE1E1E1)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private fun ColorTheme.toColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme {
    val colorScheme = if (isDark) this.darkColors else this.lightColors
    return lightColorScheme(
        primary = colorScheme.primary,
        onPrimary = colorScheme.onPrimary,
        primaryContainer = colorScheme.primaryContainer,
        onPrimaryContainer = colorScheme.onPrimaryContainer,
        secondary = colorScheme.secondary,
        onSecondary = colorScheme.onSecondary,
        secondaryContainer = colorScheme.secondaryContainer,
        onSecondaryContainer = colorScheme.onSecondaryContainer,
        tertiary = colorScheme.tertiary,
        onTertiary = colorScheme.onTertiary,
        tertiaryContainer = colorScheme.tertiaryContainer,
        onTertiaryContainer = colorScheme.onTertiaryContainer,
        error = colorScheme.error,
        onError = colorScheme.onError,
        errorContainer = colorScheme.errorContainer,
        onErrorContainer = colorScheme.onErrorContainer,
        background = colorScheme.background,
        onBackground = colorScheme.onBackground,
        surface = colorScheme.surface,
        onSurface = colorScheme.onSurface,
        surfaceVariant = colorScheme.surfaceVariant,
        onSurfaceVariant = colorScheme.onSurfaceVariant,
        outline = colorScheme.outline,
        outlineVariant = colorScheme.outlineVariant,
        scrim = colorScheme.scrim,
        inverseSurface = colorScheme.inverseSurface,
        inverseOnSurface = colorScheme.inverseOnSurface,
        inversePrimary = colorScheme.inversePrimary,
        surfaceDim = colorScheme.surfaceDim,
        surfaceBright = colorScheme.surfaceBright,
        surfaceContainerLowest = colorScheme.surfaceContainerLowest,
        surfaceContainerLow = colorScheme.surfaceContainerLow,
        surfaceContainer = colorScheme.surfaceContainer,
        surfaceContainerHigh = colorScheme.surfaceContainerHigh,
        surfaceContainerHighest = colorScheme.surfaceContainerHighest
    )
}

private fun loadCustomThemeColorScheme(
    settingsManager: SettingsManager,
    isDark: Boolean
): androidx.compose.material3.ColorScheme {
    val base = if (isDark) ColorThemes.SUNSET_MIST.darkColors else ColorThemes.SUNSET_MIST.lightColors
    fun c(role: String, default: Color): Color =
        Color(settingsManager.getCustomThemeColor(isDark, role, default.toArgb()))

    return lightColorScheme(
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        primaryContainer = c("primaryContainer", base.primaryContainer),
        onPrimaryContainer = c("onPrimaryContainer", base.onPrimaryContainer),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        secondaryContainer = c("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = c("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("onTertiary", base.onTertiary),
        tertiaryContainer = c("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = c("onTertiaryContainer", base.onTertiaryContainer),
        error = c("error", base.error),
        onError = c("onError", base.onError),
        errorContainer = c("errorContainer", base.errorContainer),
        onErrorContainer = c("onErrorContainer", base.onErrorContainer),
        background = c("background", base.background),
        onBackground = c("onBackground", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("onSurface", base.onSurface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        outline = c("outline", base.outline),
        outlineVariant = c("outlineVariant", base.outlineVariant),
        scrim = c("scrim", base.scrim),
        inverseSurface = c("inverseSurface", base.inverseSurface),
        inverseOnSurface = c("inverseOnSurface", base.inverseOnSurface),
        inversePrimary = c("inversePrimary", base.inversePrimary),
        surfaceDim = c("surfaceDim", base.surfaceDim),
        surfaceBright = c("surfaceBright", base.surfaceBright),
        surfaceContainerLowest = c("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = c("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = c("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = c("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = c("surfaceContainerHighest", base.surfaceContainerHighest)
    )
}

@Composable
fun EventManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    EventManagerTheme(themeMode = ThemeMode.DEFAULT, content = content)
}

@Composable
fun EventManagerTheme(
    themeMode: ThemeMode,
    platformContext: PlatformContext? = null,
    settingsManager: SettingsManager? = null,
    themeRefreshNonce: Int = 0,
    content: @Composable () -> Unit
) {
    val sm = settingsManager ?: remember(platformContext) {
        platformContext?.let { SettingsManager(createAppStorage(it)) }
    }
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DEFAULT -> systemInDarkTheme
    }

    val colorScheme = if (sm != null) {
        val calendar = Calendar.getInstance()
        val isWomensDay = calendar.get(Calendar.MONTH) == Calendar.MARCH && calendar.get(Calendar.DAY_OF_MONTH) == 8
        val colorThemeName = remember(themeRefreshNonce, sm, platformContext) {
            val raw = if (isWomensDay && sm.isSeasonalFunEnabled()) "feminist_violet" else sm.getColorTheme()
            if (platformContext?.isDesktop == true && raw == "custom") "system" else raw
        }
        when {
            colorThemeName == "custom" -> loadCustomThemeColorScheme(sm, darkTheme)
            colorThemeName != "system" -> ColorThemes.getThemeByName(colorThemeName).toColorScheme(darkTheme)
            else -> platformSystemColorScheme(darkTheme)
                ?: if (darkTheme) DarkColorScheme else LightColorScheme
        }
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    PlatformThemeSideEffects(darkTheme = darkTheme, colorScheme = colorScheme)

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
