package com.eventmanager.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class GoogleOAuthTokenResponse(
    val idToken: String?,
    val accessToken: String?,
    val error: String?,
    val errorDescription: String?,
)

internal object AndroidFirebaseWebOAuth {
    private val json = Json { ignoreUnknownKeys = true }
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val MAX_ATTEMPTS = 4

    fun exchangeAuthorizationCode(
        context: Context,
        code: String,
        webClientId: String,
        webClientSecret: String,
        redirectUri: String,
    ): GoogleOAuthTokenResponse {
        val body = linkedMapOf(
            "code" to code.trim(),
            "client_id" to webClientId.trim(),
            "client_secret" to webClientSecret.trim(),
            "redirect_uri" to redirectUri,
            "grant_type" to "authorization_code",
        )
        val encoded = body.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        var lastError: GoogleOAuthTokenResponse? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = postTokenRequest(context, encoded)
            if (result.error != "network_error") {
                return result
            }
            lastError = result
            if (attempt < MAX_ATTEMPTS - 1) {
                Thread.sleep(400L * (attempt + 1))
            }
        }
        return lastError ?: GoogleOAuthTokenResponse(
            idToken = null,
            accessToken = null,
            error = "network_error",
            errorDescription = "Token exchange failed",
        )
    }

    private fun postTokenRequest(context: Context, encodedBody: String): GoogleOAuthTokenResponse {
        return try {
            val connection = openHttpConnection(context, URL(TOKEN_URL)).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            connection.outputStream.use { it.write(encodedBody.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            parseTokenResponse(payload)
        } catch (e: Exception) {
            GoogleOAuthTokenResponse(
                idToken = null,
                accessToken = null,
                error = "network_error",
                errorDescription = e.message ?: "Token exchange failed",
            )
        }
    }

    private fun openHttpConnection(context: Context, url: URL): HttpURLConnection {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        @Suppress("DEPRECATION")
        val connection = if (network != null) {
            network.openConnection(url)
        } else {
            url.openConnection()
        } as HttpURLConnection
        return connection
    }

    private fun parseTokenResponse(payload: String): GoogleOAuthTokenResponse {
        if (payload.isBlank()) {
            return GoogleOAuthTokenResponse(null, null, "empty_response", "Google returned an empty token response")
        }
        val root = json.parseToJsonElement(payload).jsonObject
        val error = root["error"]?.jsonPrimitive?.contentOrNull
        val errorDescription = root["error_description"]?.jsonPrimitive?.contentOrNull
        return GoogleOAuthTokenResponse(
            idToken = root["id_token"]?.jsonPrimitive?.contentOrNull,
            accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull,
            error = error,
            errorDescription = errorDescription,
        )
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
