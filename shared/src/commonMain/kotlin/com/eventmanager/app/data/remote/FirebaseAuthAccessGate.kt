package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager

/**
 * After a successful Google → Firebase Auth exchange, reject (and sign out) emails
 * outside the institution allowlist.
 */
object FirebaseAuthAccessGate {
    suspend fun enforceEmailDomain(
        result: FirebaseAuthResult,
        settings: SettingsManager,
        signOut: suspend () -> Unit,
    ): FirebaseAuthResult {
        if (result !is FirebaseAuthResult.Success) return result
        val allowed = settings.getAllowedEmailDomains()
        if (FirebaseEmailDomainPolicy.isEmailAllowed(result.email, allowed)) {
            return result
        }
        runCatching { signOut() }
        settings.setFirebaseAuthEmail("")
        return FirebaseAuthResult.Error(
            FirebaseEmailDomainPolicy.denialMessage(result.email, allowed),
        )
    }
}
