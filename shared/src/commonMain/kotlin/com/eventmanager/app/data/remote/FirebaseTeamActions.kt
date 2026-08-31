package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import kotlin.coroutines.cancellation.CancellationException

object FirebaseTeamActions {
    suspend fun assignRole(
        platformContext: PlatformContext?,
        settings: SettingsManager,
        uid: String,
        email: String?,
        role: MemberRole,
    ): Result<Unit> = runCatchingExceptCancellation {
        val org = settings.resolveWritableFirebaseOrgId()
        val gateway = createFirestoreGateway(platformContext, settings)
        if (!gateway.isAvailable()) {
            error("NOT_READY")
        }
        MemberRoleAdmin.assignTeamMember(
            gateway = gateway,
            orgId = org,
            uid = uid,
            role = role,
            email = email,
            allowedEmailDomains = settings.getAllowedEmailDomains(),
        )
    }

    suspend fun loadMembers(
        platformContext: PlatformContext?,
        settings: SettingsManager,
    ): Result<List<FirebaseTeamMemberListing>> = runCatchingExceptCancellation {
        val org = settings.resolveWritableFirebaseOrgId()
        if (org.isBlank()) error("NO_ORG")
        val gateway = createFirestoreGateway(platformContext, settings)
        if (!gateway.isAvailable()) {
            error("NOT_READY")
        }
        gateway.listMembers(org)
    }
}

private suspend inline fun <T> runCatchingExceptCancellation(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
