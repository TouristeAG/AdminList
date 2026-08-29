package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

internal actual suspend fun platformSignInWithGoogleTokens(
    idToken: String?,
    accessToken: String?,
    platformContext: PlatformContext?,
): FirebaseAuthResult {
    val settings = platformContext?.let { SettingsManager(it) }
    val key = settings?.getFirebaseApiKey().orEmpty()
    if (key.isBlank()) {
        return FirebaseAuthResult.Error(
            "Firebase API key missing — paste it in the migration form before Sign-In",
        )
    }
    val googleId = idToken?.takeIf { it.isNotBlank() }
        ?: return FirebaseAuthResult.Error("Missing Google id_token for Desktop Firebase Sign-In")

    // JVM GitLive stubs throw NotImplementedError for GoogleAuthProvider / signInWithCredential.
    val result = DesktopFirebaseGoogleRestSignIn.signInWithGoogleIdToken(
        apiKey = key,
        googleIdToken = googleId,
        projectId = settings?.getFirebaseProjectId().orEmpty(),
    )
    if (result is FirebaseAuthResult.Success) {
        DesktopFirebaseSession.uid = result.uid
        DesktopFirebaseSession.email = result.email
    }
    return result
}

internal actual suspend fun platformSignOut() {
    DesktopFirebaseSession.clear()
    DesktopFirebaseGoogleRestSignIn.clearStoredUser()
}

internal actual fun platformCachedUserId(): String? = DesktopFirebaseSession.uid

internal actual fun platformCachedUserEmail(): String? = DesktopFirebaseSession.email

/** In-process Auth session for Desktop after REST sign-in (GitLive JVM may not hydrate currentUser). */
internal object DesktopFirebaseSession {
    @Volatile
    var uid: String? = null

    @Volatile
    var email: String? = null

    fun clear() {
        uid = null
        email = null
    }
}
