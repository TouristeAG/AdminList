package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Firebase Sign-In via institution Web OAuth (Custom Tabs browser flow).
 * Requires Web client ID + secret and localhost redirect URIs (same as Desktop).
 */
class AndroidFirebaseAuthService(
    private val platformContext: PlatformContext,
) : FirebaseAuthService {
    private val context get() = platformContext.androidContext
    private val settings by lazy { SettingsManager(platformContext) }

    fun getSignInIntent(): android.content.Intent =
        FirebaseGoogleSignInActivity.createLaunchIntent(context)

    suspend fun completeSignInFromIntent(data: android.content.Intent?): FirebaseAuthResult =
        withContext(Dispatchers.IO) {
            if (FirebaseAuthBridge.isSignedIn()) {
                return@withContext FirebaseAuthResult.Success(
                    uid = FirebaseAuthBridge.currentUserId().orEmpty(),
                    email = FirebaseAuthBridge.currentUserEmail(),
                )
            }
            val error = data?.getStringExtra(FirebaseGoogleSignInActivity.EXTRA_ERROR_MESSAGE)
            if (!error.isNullOrBlank()) {
                return@withContext FirebaseAuthResult.Error(error)
            }
            FirebaseAuthResult.Error("Google Sign-In cancelled or failed.")
        }

    override suspend fun signInWithGoogle(): FirebaseAuthResult = withContext(Dispatchers.IO) {
        val clientId = settings.getFirebaseWebClientId().trim()
        val clientSecret = settings.getFirebaseWebClientSecret().trim()
        if (clientId.isBlank()) {
            return@withContext FirebaseAuthResult.Error(
                "Web client ID is required. Copy it from Firebase → Authentication → Google " +
                    "(institution project).",
            )
        }
        if (clientSecret.isBlank()) {
            return@withContext FirebaseAuthResult.Error(
                "Web client secret missing. Rescan or paste the full join code from your admin " +
                    "(the QR includes OAuth credentials). During setup you do not open Settings.",
            )
        }
        FirebaseAuthResult.Error(
            "Tap Sign in with Google to continue (institution Web client ID + secret).",
        )
    }

    suspend fun finishGoogleIdTokenSignIn(
        idToken: String,
        email: String?,
        accessToken: String? = null,
    ): FirebaseAuthResult = withContext(Dispatchers.IO) {
        FirebaseBootstrap.ensureInitialized(
            platformContext,
            FirebaseOptionsReader.fromSettings(settings),
        )
        val firebaseResult = FirebaseAuthBridge.signInWithGoogleTokens(
            idToken = idToken,
            accessToken = accessToken,
        )
        when (firebaseResult) {
            is FirebaseAuthResult.Success -> {
                val gated = FirebaseAuthAccessGate.enforceEmailDomain(
                    result = firebaseResult,
                    settings = settings,
                    signOut = { signOutLocal() },
                )
                if (gated is FirebaseAuthResult.Success) {
                    settings.setFirebaseAuthEmail(gated.email ?: email.orEmpty())
                }
                gated
            }
            is FirebaseAuthResult.Error -> firebaseResult
        }
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            signOutLocal()
        }
    }

    private suspend fun signOutLocal() {
        FirebaseAuthBridge.signOut()
        settings.setFirebaseAuthEmail("")
    }

    override fun currentUserEmail(): String? =
        FirebaseAuthBridge.currentUserEmail()
            ?: settings.getFirebaseAuthEmail().takeIf { it.isNotBlank() }

    override fun currentUserId(): String? = FirebaseAuthBridge.currentUserId()

    override suspend fun restoreSession(): FirebaseAuthResult? = withContext(Dispatchers.IO) {
        FirebaseBootstrap.ensureInitialized(
            platformContext,
            FirebaseOptionsReader.fromSettings(settings),
        )
        if (!FirebaseAuthBridge.isSignedIn()) return@withContext null
        val candidate = FirebaseAuthResult.Success(
            uid = FirebaseAuthBridge.currentUserId().orEmpty(),
            email = FirebaseAuthBridge.currentUserEmail(),
        )
        return@withContext when (
            val gated = FirebaseAuthAccessGate.enforceEmailDomain(
                result = candidate,
                settings = settings,
                signOut = { signOutLocal() },
            )
        ) {
            is FirebaseAuthResult.Success -> gated
            is FirebaseAuthResult.Error -> null
        }
    }

    override fun isSignedIn(): Boolean = FirebaseAuthBridge.isSignedIn()
}

actual fun createFirebaseAuthService(platformContext: PlatformContext?): FirebaseAuthService {
    val ctx = platformContext ?: return NoOpFirebaseAuthService()
    return AndroidFirebaseAuthService(ctx)
}
