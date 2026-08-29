package com.eventmanager.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Institution Firebase Sign-In via Chrome Custom Tabs + localhost OAuth callback.
 * Uses the same Web OAuth client and redirect URIs as Desktop (no Android SHA-1 per institution).
 */
class FirebaseGoogleSignInActivity : ComponentActivity() {

    private var loopbackReceiver: AndroidOAuthLoopbackReceiver? = null
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        launchWebOAuth()
    }

    override fun onDestroy() {
        loopbackReceiver?.cancel()
        loopbackReceiver = null
        super.onDestroy()
    }

    private fun launchWebOAuth() {
        val platformContext = createPlatformContext(this)
        val settings = SettingsManager(createAppStorage(platformContext))
        val webClientId = settings.getFirebaseWebClientId().trim()
        val webClientSecret = settings.getFirebaseWebClientSecret().trim()
        if (webClientId.isBlank()) {
            finishWithError(
                "Web client ID is required. Copy it from Firebase → Authentication → Google " +
                    "(institution project, not the developer Gmail JSON).",
            )
            return
        }
        if (webClientSecret.isBlank()) {
            finishWithError(
                "Web client secret is required on Android. Copy it from Cloud Console → " +
                    "APIs & Services → Credentials → your Web OAuth client (same as Desktop). " +
                    "Authorized redirect URIs must include the localhost Callback URLs from the ? guide.",
            )
            return
        }
        val options = FirebaseOptionsReader.fromSettings(settings)
        if (options == null) {
            finishWithError("Firebase project options incomplete. Fill Project ID, Application ID and API key first.")
            return
        }
        lifecycleScope.launch {
            val initialized = withContext(Dispatchers.IO) {
                FirebaseBootstrap.ensureInitialized(platformContext, options)
            }
            if (!initialized) {
                finishWithError("Firebase failed to initialize. Check project settings.")
                return@launch
            }
            val receiver = AndroidOAuthLoopbackReceiver()
            loopbackReceiver = receiver
            val redirectUri = try {
                withContext(Dispatchers.IO) { receiver.start() }
            } catch (e: Exception) {
                finishWithError(e.message ?: "Could not start OAuth callback server")
                return@launch
            }
            val authUrl = InstitutionGoogleWebOAuth.buildAuthorizationUrl(
                webClientId = webClientId,
                redirectUri = redirectUri,
            )
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this@FirebaseGoogleSignInActivity, Uri.parse(authUrl))

            when (val loopback = receiver.awaitResult()) {
                is LoopbackOAuthResult.Success -> lifecycle.whenResumed {
                    // Token exchange needs foreground network — loopback may complete while Chrome is still open.
                    completeOAuthCodeExchange(
                        code = loopback.code,
                        redirectUri = loopback.redirectUri,
                        webClientId = webClientId,
                        webClientSecret = webClientSecret,
                        platformContext = platformContext,
                    )
                }
                is LoopbackOAuthResult.OAuthError -> {
                    val detail = loopback.description?.takeIf { it.isNotBlank() } ?: loopback.error
                    finishWithError("Google Sign-In failed: $detail")
                }
                LoopbackOAuthResult.TimedOut -> finishWithError(
                    "Google Sign-In timed out. Complete sign-in in the browser tab, or try again.",
                )
                LoopbackOAuthResult.Cancelled -> finishWithError("Google Sign-In cancelled.")
            }
        }
    }

    private suspend fun completeOAuthCodeExchange(
        code: String,
        redirectUri: String,
        webClientId: String,
        webClientSecret: String,
        platformContext: com.eventmanager.app.platform.PlatformContext,
    ) {
        val tokenResponse = withContext(Dispatchers.IO) {
            AndroidFirebaseWebOAuth.exchangeAuthorizationCode(
                context = this@FirebaseGoogleSignInActivity,
                code = code,
                webClientId = webClientId,
                webClientSecret = webClientSecret,
                redirectUri = redirectUri,
            )
        }
        if (!tokenResponse.error.isNullOrBlank()) {
            val message = tokenResponse.errorDescription ?: tokenResponse.error
            val hint = when {
                message.contains("redirect_uri", ignoreCase = true) ->
                    " Add localhost Callback redirect URIs from the Firebase ? guide " +
                        "(http://localhost:8889/Callback, etc.)."
                tokenResponse.error == "network_error" ->
                    " Return to NoctuList and ensure Wi‑Fi or mobile data is active, then try again."
                else -> ""
            }
            finishWithError("Google token exchange failed: $message$hint")
            return
        }
        val idToken = tokenResponse.idToken
        if (idToken.isNullOrBlank()) {
            finishWithError(
                "Google returned no id_token. Use the institution Web OAuth client with openid scope.",
            )
            return
        }
        val authService = AndroidFirebaseAuthService(platformContext)
        when (
            val result = authService.finishGoogleIdTokenSignIn(
                idToken = idToken,
                email = null,
                accessToken = tokenResponse.accessToken,
            )
        ) {
            is FirebaseAuthResult.Success -> {
                handled = true
                setResult(RESULT_OK)
                finish()
            }
            is FirebaseAuthResult.Error -> finishWithError(result.message)
        }
    }

    private fun finishWithError(message: String) {
        if (isFinishing || handled) return
        handled = true
        loopbackReceiver?.cancel()
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR_MESSAGE, message),
        )
        finish()
    }

    companion object {
        const val EXTRA_ERROR_MESSAGE = "firebase_google_sign_in_error"

        fun createLaunchIntent(context: Context): Intent =
            Intent(context, FirebaseGoogleSignInActivity::class.java)
    }
}
