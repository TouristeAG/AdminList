package com.eventmanager.app.ui.platform

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val LocalAppLocaleComposition = staticCompositionLocalOf {
        Locale.getDefault().toLanguageTag()
    }

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

/**
 * Live locale override for a Compose subtree (e.g. POS language) without Activity.recreate().
 * Mirrors desktop: set default locale, remount with [key], and also swap LocalContext /
 * LocalConfiguration so Android/CMP string resources resolve in the target language.
 */
@Composable
actual fun AppLocaleEnvironment(
    languageCode: String,
    refreshNonce: Int,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val locale = remember(languageCode) { localeFromLanguageCode(languageCode) }
    val localizedContext = remember(baseContext, languageCode) {
        val config = Configuration(baseContext.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        baseContext.createConfigurationContext(config)
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }

    applyPlatformLocale(languageCode)
    CompositionLocalProvider(
        LocalAppLocale provides languageCode,
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        key("$languageCode#$refreshNonce") {
            content()
        }
    }
}
