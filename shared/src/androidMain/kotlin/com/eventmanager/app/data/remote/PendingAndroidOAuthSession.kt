package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext

/**
 * Keeps the localhost OAuth callback server alive while [FirebaseGoogleSignInActivity]
 * is stopped or recreated (Custom Tabs / memory pressure). Cleared when sign-in finishes.
 */
internal object PendingAndroidOAuthSession {
    @Volatile
    var receiver: AndroidOAuthLoopbackReceiver? = null

    @Volatile
    var webClientId: String = ""

    @Volatile
    var webClientSecret: String = ""

    @Volatile
    var platformContext: PlatformContext? = null

    fun clear() {
        receiver?.cancel()
        receiver = null
        webClientId = ""
        webClientSecret = ""
        platformContext = null
    }

    fun isActive(): Boolean = receiver != null
}
