package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable

actual object LocalAppLocale {
    actual val current: String
        @Composable get() = ""

    @Composable
    actual infix fun provides(value: String?): androidx.compose.runtime.ProvidedValue<*> {
        error("LocalAppLocale is not used on Android — the activity is recreated on locale change")
    }
}

@Composable
actual fun AppLocaleEnvironment(
    languageCode: String,
    refreshNonce: Int,
    content: @Composable () -> Unit
) {
    content()
}
