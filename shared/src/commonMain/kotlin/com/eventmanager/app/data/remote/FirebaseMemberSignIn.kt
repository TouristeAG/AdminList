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
                val code = settings.getFirebaseBootstrapCode()
                if (code.isBlank()) return
                runCatching {
                    MemberRoleAdmin.joinOrgAsMember(
                        gateway = gateway,
                        orgId = orgId,
                        uid = uid,
                        email = email,
                        bootstrapCode = code,
                        allowedEmailDomains = domains,
                    )
                }
            }
        }
    }
}
