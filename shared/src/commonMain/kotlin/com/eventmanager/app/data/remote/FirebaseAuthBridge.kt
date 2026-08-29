package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

/**
 * Bridges Google ID tokens into Firebase Auth.
 * Android: GitLive [signInWithCredential].
 * Desktop JVM: Identity Toolkit REST (GitLive stubs throw [NotImplementedError] for Google credentials).
 */
object FirebaseAuthBridge {
    suspend fun signInWithGoogleTokens(
        idToken: String?,
        accessToken: String? = null,
        platformContext: PlatformContext? = null,
    ): FirebaseAuthResult {
        if (idToken.isNullOrBlank() && accessToken.isNullOrBlank()) {
            return FirebaseAuthResult.Error("Missing Google ID/access token for Firebase Auth")
        }
        if (!FirebaseBootstrap.isInitialized()) {
            return FirebaseAuthResult.Error("Initialize Firebase (project options) before signing in")
        }
        return platformSignInWithGoogleTokens(idToken, accessToken, platformContext)
    }

    suspend fun signOut() {
        runCatching { platformSignOut() }
        runCatching { Firebase.auth.signOut() }
    }

    fun currentUserId(): String? =
        platformCachedUserId()
            ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    fun currentUserEmail(): String? =
        platformCachedUserEmail()
            ?: runCatching { Firebase.auth.currentUser?.email }.getOrNull()

    fun isSignedIn(): Boolean = currentUserId() != null
}

internal expect suspend fun platformSignInWithGoogleTokens(
    idToken: String?,
    accessToken: String?,
    platformContext: PlatformContext?,
): FirebaseAuthResult

internal expect suspend fun platformSignOut()

internal expect fun platformCachedUserId(): String?

internal expect fun platformCachedUserEmail(): String?
