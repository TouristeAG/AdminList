package com.eventmanager.app.data.remote

import com.eventmanager.app.data.security.firebaseRoleIsOrgAdmin
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import kotlin.coroutines.cancellation.CancellationException

object FirebaseOrgAdminAccess {
    suspend fun currentUserIsOrgAdmin(
        platformContext: PlatformContext?,
        settings: SettingsManager,
    ): Boolean {
        val org = settings.resolveWritableFirebaseOrgId()
        if (org.isBlank()) return false
        val auth = createFirebaseAuthService(platformContext)
        val uid = auth.currentUserId()?.trim().orEmpty()
        if (uid.isBlank() || !auth.isSignedIn()) return false
        return try {
            val gateway = createFirestoreGateway(platformContext, settings)
            if (!gateway.isAvailable()) return false
            firebaseRoleIsOrgAdmin(gateway.readMemberRoleFromServer(org, uid))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
