package com.eventmanager.app.ui.platform

import java.util.Locale
import com.eventmanager.app.platform.PlatformContext

internal actual fun applyPlatformLocale(languageCode: String) {
    Locale.setDefault(localeFromLanguageCode(languageCode))
}

internal actual fun afterThemeAppearanceChange(platformContext: PlatformContext) {
    // Desktop refreshes theme via AppAppearanceState observers; no activity recreate.
}
