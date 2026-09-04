package com.eventmanager.app.ui.platform

import com.eventmanager.app.platform.PlatformContext
import java.util.Locale

internal actual fun applyPlatformLocale(languageCode: String) {
    Locale.setDefault(localeFromLanguageCode(languageCode))
}

internal actual fun afterThemeAppearanceChange(platformContext: PlatformContext) {
    com.eventmanager.app.platform.recreateActivity(platformContext)
}
