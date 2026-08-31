package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstitutionGoogleWebOAuthTest {
    @Test
    fun loopbackRedirectUri_matchesDesktopPorts() {
        assertEquals(
            "http://localhost:8889/Callback",
            InstitutionGoogleWebOAuth.loopbackRedirectUri(8889),
        )
        assertEquals(
            InstitutionGoogleWebOAuth.LOOPBACK_PORTS.size,
            InstitutionGoogleWebOAuth.LOOPBACK_REDIRECT_URIS.size,
        )
    }

    @Test
    fun buildAuthorizationUrl_includesClientIdAndRedirect() {
        val redirect = InstitutionGoogleWebOAuth.loopbackRedirectUri(8889)
        val url = InstitutionGoogleWebOAuth.buildAuthorizationUrl(
            webClientId = "client-id.apps.googleusercontent.com",
            redirectUri = redirect,
            promptConsent = false,
        )
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(url.contains("client_id=client-id.apps.googleusercontent.com"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8889%2FCallback"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=openid"))
    }

    @Test
    fun buildAuthorizationUrl_includesAccountPickerWhenRequested() {
        val redirect = InstitutionGoogleWebOAuth.loopbackRedirectUri(8889)
        val url = InstitutionGoogleWebOAuth.buildAuthorizationUrl(
            webClientId = "client-id.apps.googleusercontent.com",
            redirectUri = redirect,
            forceAccountPicker = true,
        )
        assertTrue(
            url.contains("prompt=select_account+consent") ||
                url.contains("prompt=select_account%20consent"),
        )
    }
}
