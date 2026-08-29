package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth

internal actual suspend fun platformSignInWithGoogleTokens(
    idToken: String?,
    accessToken: String?,
    platformContext: PlatformContext?,
): FirebaseAuthResult {
    return try {
        val credential = GoogleAuthProvider.credential(idToken, accessToken)
        val result = Firebase.auth.signInWithCredential(credential)
        val user = result.user
            ?: return FirebaseAuthResult.Error("Firebase Auth returned no user")
        FirebaseAuthResult.Success(uid = user.uid, email = user.email)
    } catch (e: Exception) {
        FirebaseAuthResult.Error(e.message ?: "Firebase Auth sign-in failed")
    }
}

internal actual suspend fun platformSignOut() = Unit

internal actual fun platformCachedUserId(): String? = null

internal actual fun platformCachedUserEmail(): String? = null
