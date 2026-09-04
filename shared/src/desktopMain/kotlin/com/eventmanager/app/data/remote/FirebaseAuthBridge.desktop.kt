package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app

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
    // signInWithGoogleIdToken() stores the session in FirebasePlatform KV via platform.store().
    val result = DesktopFirebaseGoogleRestSignIn.signInWithGoogleIdToken(
        apiKey = key,
        googleIdToken = googleId,
        projectId = settings?.getFirebaseProjectId().orEmpty(),
    )
    if (result is FirebaseAuthResult.Success) {
        DesktopFirebaseSession.uid = result.uid
        DesktopFirebaseSession.email = result.email

        // ── Auth-propagation fix ───────────────────────────────────────────────
        // Firebase was initialised at app startup BEFORE the user existed, so
        // Firebase.auth.currentUser is null in the SDK's in-memory state even
        // though the session is now stored in the platform KV file.  As a result,
        // every Firestore read/write immediately after sign-in is unauthenticated
        // → PERMISSION_DENIED.  Closing and reopening the app works because the
        // SDK reads the KV at init time.
        //
        // Fix: delete the existing Firebase app and re-initialise it immediately.
        // The re-init reads FIREBASE_USER from the KV that signInWithGoogleIdToken()
        // just wrote, so Firebase.auth.currentUser becomes non-null and all
        // subsequent Firestore requests carry the auth token.
        if (platformContext != null && settings != null) {
            runCatching { Firebase.app.delete() }
            FirebaseBootstrap.ensureInitialized(
                platformContext,
                FirebaseOptionsReader.fromSettings(settings),
            )
            println("Firebase: re-initialised after REST sign-in to propagate auth state to Firestore")
        }
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
