package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val LocalAppLocaleComposition = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = LocalAppLocaleComposition.current

    @Composable
    actual infix fun provides(value: String?): androidx.compose.runtime.ProvidedValue<*> {
        if (default == null) {
            default = Locale.getDefault()
        }
        val locale = when (value) {
            null -> default!!
            else -> localeFromLanguageCode(value)
        }
        Locale.setDefault(locale)
        return LocalAppLocaleComposition.provides(locale.toLanguageTag())
    }
}

@Composable
actual fun AppLocaleEnvironment(
    languageCode: String,
    refreshNonce: Int,
    content: @Composable () -> Unit
) {
    applyPlatformLocale(languageCode)
    CompositionLocalProvider(LocalAppLocale provides languageCode) {
        key("$languageCode#$refreshNonce") {
            content()
        }
    }
}
