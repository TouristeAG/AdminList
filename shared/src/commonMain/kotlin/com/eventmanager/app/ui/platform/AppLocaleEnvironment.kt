package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): androidx.compose.runtime.ProvidedValue<*>
}

@Composable
expect fun AppLocaleEnvironment(
    languageCode: String,
    refreshNonce: Int,
    content: @Composable () -> Unit
)
