package com.eventmanager.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import com.eventmanager.app.data.sync.SettingsManager
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
        when {
            savedInstanceState != null && PendingAndroidOAuthSession.isActive() -> {
                resumePendingOAuth()
            }
            savedInstanceState != null -> {
                // Recreated without an active session — do not start a second OAuth tab.
                return
            }
            else -> launchWebOAuth()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            PendingAndroidOAuthSession.clear()
            loopbackReceiver?.cancel()
            loopbackReceiver = null
        }
        super.onDestroy()
    }

    private fun resumePendingOAuth() {
        loopbackReceiver = PendingAndroidOAuthSession.receiver
        val platformContext = PendingAndroidOAuthSession.platformContext ?: createPlatformContext(this)
        lifecycleScope.launch {
            awaitLoopbackAndComplete(
                receiver = loopbackReceiver ?: return@launch,
                webClientId = PendingAndroidOAuthSession.webClientId,
                webClientSecret = PendingAndroidOAuthSession.webClientSecret,
                platformContext = platformContext,
            )
        }
    }

    private fun launchWebOAuth() {
        val platformContext = createPlatformContext(this)
        val settings = SettingsManager(platformContext)
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
                "Web client secret missing. Rescan or paste the full join code from your admin " +
                    "(the QR includes OAuth credentials). During setup you do not open Settings.",
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
            withContext(Dispatchers.IO) {
                FirebaseAuthBridge.signOut()
            }
            val receiver = AndroidOAuthLoopbackReceiver()
            loopbackReceiver = receiver
            PendingAndroidOAuthSession.receiver = receiver
            PendingAndroidOAuthSession.webClientId = webClientId
            PendingAndroidOAuthSession.webClientSecret = webClientSecret
            PendingAndroidOAuthSession.platformContext = platformContext
            val redirectUri = try {
                withContext(Dispatchers.IO) { receiver.start() }
            } catch (e: Exception) {
                PendingAndroidOAuthSession.clear()
                finishWithError(e.message ?: "Could not start OAuth callback server")
                return@launch
            }
            val authUrl = InstitutionGoogleWebOAuth.buildAuthorizationUrl(
                webClientId = webClientId,
                redirectUri = redirectUri,
                forceAccountPicker = true,
            )
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this@FirebaseGoogleSignInActivity, Uri.parse(authUrl))

            awaitLoopbackAndComplete(
                receiver = receiver,
                webClientId = webClientId,
                webClientSecret = webClientSecret,
                platformContext = platformContext,
            )
        }
    }

    private suspend fun awaitLoopbackAndComplete(
        receiver: AndroidOAuthLoopbackReceiver,
        webClientId: String,
        webClientSecret: String,
        platformContext: com.eventmanager.app.platform.PlatformContext,
    ) {
        when (val loopback = receiver.awaitResult()) {
            is LoopbackOAuthResult.Success -> lifecycle.whenResumed {
                lifecycleScope.launch {
                    completeOAuthCodeExchange(
                        code = loopback.code,
                        redirectUri = loopback.redirectUri,
                        webClientId = webClientId,
                        webClientSecret = webClientSecret,
                        platformContext = platformContext,
                    )
                }
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
                PendingAndroidOAuthSession.clear()
                setResult(RESULT_OK)
                finish()
            }
            is FirebaseAuthResult.Error -> finishWithError(result.message)
        }
    }

    private fun finishWithError(message: String) {
        if (isFinishing || handled) return
        handled = true
        Log.w(TAG, message)
        PendingAndroidOAuthSession.clear()
        loopbackReceiver?.cancel()
        loopbackReceiver = null
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR_MESSAGE, message),
        )
        finish()
    }

    companion object {
        private const val TAG = "FirebaseGoogleSignIn"
        const val EXTRA_ERROR_MESSAGE = "firebase_google_sign_in_error"

        fun createLaunchIntent(context: Context): Intent =
            Intent(context, FirebaseGoogleSignInActivity::class.java)
    }
}
