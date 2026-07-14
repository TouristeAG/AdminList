package com.eventmanager.app.ui.platform

import java.util.Locale

internal actual fun applyPlatformLocale(languageCode: String) {
    Locale.setDefault(localeFromLanguageCode(languageCode))
}
