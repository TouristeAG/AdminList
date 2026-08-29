package com.eventmanager.app.data.remote

/**
 * Platform Firebase Auth (Google Sign-In). Lazy — no-op until Firebase mode is configured.
 */
interface FirebaseAuthService {
    suspend fun signInWithGoogle(): FirebaseAuthResult
    /** Restore Firebase Auth from persisted Google ID token / silent sign-in if possible. */
    suspend fun restoreSession(): FirebaseAuthResult?
    suspend fun signOut()
    fun currentUserEmail(): String?
    fun currentUserId(): String?
    fun isSignedIn(): Boolean
}

sealed class FirebaseAuthResult {
    data class Success(val uid: String, val email: String?) : FirebaseAuthResult()
    data class Error(val message: String) : FirebaseAuthResult()
}

class NoOpFirebaseAuthService : FirebaseAuthService {
    override suspend fun signInWithGoogle(): FirebaseAuthResult =
        FirebaseAuthResult.Error("Firebase Auth not configured on this platform build")

    override suspend fun restoreSession(): FirebaseAuthResult? = null
    override suspend fun signOut() {}
    override fun currentUserEmail(): String? = null
    override fun currentUserId(): String? = null
    override fun isSignedIn(): Boolean = false
}

expect fun createFirebaseAuthService(
    platformContext: com.eventmanager.app.platform.PlatformContext?,
): FirebaseAuthService
