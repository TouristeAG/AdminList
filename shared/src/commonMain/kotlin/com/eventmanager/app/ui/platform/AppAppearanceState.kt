package com.eventmanager.app.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Drives live theme/locale refresh on desktop (Android recreates the activity instead).
 */
object AppAppearanceState {
    var localeCode by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf<String?>(null)
    /** Bumped when language changes so observers can refresh translated strings. */
    var refreshNonce by mutableIntStateOf(0)
    /** Theme / color palette changes — refreshes MaterialTheme only. */
    var themeRefreshNonce by mutableIntStateOf(0)

    fun notifyLocaleChanged(languageCode: String) {
        localeCode = languageCode
        refreshNonce++
        applyPlatformLocale(languageCode)
    }

    fun notifyThemeAppearanceChanged(themeModeValue: String? = null) {
        themeModeValue?.let { themeMode = it }
        themeRefreshNonce++
    }

    @Deprecated("Use notifyLocaleChanged or notifyThemeAppearanceChanged", ReplaceWith("notifyLocaleChanged(languageCode)"))
    fun notifyLocaleAndThemeChanged(languageCode: String, themeModeValue: String) {
        notifyLocaleChanged(languageCode)
        notifyThemeAppearanceChanged(themeModeValue)
    }

    @Deprecated("Use notifyThemeAppearanceChanged", ReplaceWith("notifyThemeAppearanceChanged(themeModeValue)"))
    fun notifyThemeChanged(themeModeValue: String) {
        notifyThemeAppearanceChanged(themeModeValue)
    }
}

fun localeFromLanguageCode(languageCode: String): Locale = when {
    languageCode.equals("en", ignoreCase = true) -> Locale("en", "GB")
    languageCode.contains("-") -> languageCode.split("-", limit = 2).let { Locale(it[0], it[1]) }
    languageCode.contains("_") -> languageCode.split("_", limit = 2).let { Locale(it[0], it[1]) }
    else -> Locale(languageCode)
}

internal expect fun applyPlatformLocale(languageCode: String)

fun bootstrapAppLocale(languageCode: String) {
    AppAppearanceState.localeCode = languageCode
    applyPlatformLocale(languageCode)
}
