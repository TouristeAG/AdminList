package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager

/**
 * Firebase Google sign-in side effects for org membership (not local Admin NFC).
 */
object FirebaseMemberSignIn {
    /**
     * @param isOrgBootstrap true on first org setup (wizard / migration admin).
     * @param joinWithBootstrapCode when true, uses [SettingsManager.getFirebaseBootstrapCode] to join as member.
     */
    suspend fun afterGoogleSignIn(
        gateway: FirestoreGateway,
        settings: SettingsManager,
        uid: String,
        email: String?,
        isOrgBootstrap: Boolean = false,
        joinWithBootstrapCode: Boolean = false,
    ) {
        val orgId = settings.getFirebaseOrgId()
        if (orgId.isBlank() || uid.isBlank()) return
        val domains = settings.getAllowedEmailDomains()
        when {
            isOrgBootstrap -> {
                val code = MemberRoleAdmin.bootstrapOrgAdmin(
                    gateway = gateway,
                    orgId = orgId,
                    uid = uid,
                    email = email,
                    allowedEmailDomains = domains,
                )
                settings.setFirebaseBootstrapCode(code)
            }
            joinWithBootstrapCode -> {
                // Goes through the shared path so an account that is already a member is a
                // no-op instead of a refused re-write, and refusals get an actionable message.
                FirebaseOrgBootstrap.ensureOrgBootstrappedIfNeeded(
                    gateway = gateway,
                    settings = settings,
                    orgId = orgId,
                    intent = OrgBootstrapIntent.ENSURE_MEMBERSHIP,
                    signedInUid = uid,
                    signedInEmail = email,
                )
            }
        }
    }
}
