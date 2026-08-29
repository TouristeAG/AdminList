package com.eventmanager.app.data.remote

import java.net.URLEncoder

/**
 * Institution Firebase Sign-In via the **Web** OAuth client (browser + loopback redirect).
 * Desktop and Android share the same localhost callback URIs — no Android OAuth client / SHA-1
 * per institution.
 */
object InstitutionGoogleWebOAuth {
    const val LOOPBACK_CALLBACK_PATH = "/Callback"

    val LOOPBACK_PORTS: List<Int> = listOf(8889, 8888, 8765, 9090)

    /** Registered in each institution Web OAuth client (Desktop + Android). */
    val LOOPBACK_REDIRECT_URIS: List<String> = LOOPBACK_PORTS.map { port ->
        loopbackRedirectUri(port)
    }

    /** @deprecated Use [LOOPBACK_REDIRECT_URIS] — kept as alias for Desktop docs. */
    val DESKTOP_REDIRECT_URIS: List<String> = LOOPBACK_REDIRECT_URIS

    fun loopbackRedirectUri(port: Int): String =
        "http://localhost:$port$LOOPBACK_CALLBACK_PATH"

    val OAUTH_SCOPES: List<String> = listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
    )

    fun buildAuthorizationUrl(
        webClientId: String,
        redirectUri: String,
        promptConsent: Boolean = true,
    ): String {
        val scope = OAUTH_SCOPES.joinToString(" ")
        val params = linkedMapOf(
            "client_id" to webClientId.trim(),
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to scope,
            "access_type" to "offline",
        )
        if (promptConsent) {
            params["prompt"] = "consent"
        }
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "https://accounts.google.com/o/oauth2/v2/auth?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
