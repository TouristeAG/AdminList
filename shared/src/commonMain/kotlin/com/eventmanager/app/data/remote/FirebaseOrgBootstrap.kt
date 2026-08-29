package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager

sealed class FirebaseOrgRepairResult {
    data class Ok(val orgId: String) : FirebaseOrgRepairResult()
    data class Recovered(val orgId: String, val previousOrgId: String) : FirebaseOrgRepairResult()
    data object NotSignedIn : FirebaseOrgRepairResult()
    data object NoOrgsConfigured : FirebaseOrgRepairResult()
}

/**
 * Firestore org bootstrap and multi-org access helpers.
 *
 * Each configured org ID is a separate tree under `orgs/{orgId}/`. Adding an ID in settings
 * only stores it locally until [ensureOrgBootstrappedIfNeeded] runs (on field commit or org switch).
 */
object FirebaseOrgBootstrap {
    /** Min 4 chars to avoid accidental partial IDs while typing. */
    private val orgIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{3,63}$")

    fun isValidOrgId(orgId: String): Boolean = orgIdPattern.matches(orgId.trim())

    suspend fun isMember(gateway: FirestoreGateway, orgId: String, uid: String): Boolean =
        gateway.readMemberRole(orgId.trim(), uid.trim()) != null

    /**
     * Creates `members/{uid}` + `metadata/config` when the org does not exist yet.
     */
    suspend fun ensureOrgBootstrappedIfNeeded(
        gateway: FirestoreGateway,
        settings: SettingsManager,
        orgId: String,
    ) {
        val trimmed = orgId.trim()
        if (!isValidOrgId(trimmed)) {
            throw IllegalStateException("Invalid organization ID \"$trimmed\"")
        }
        if (!gateway.isAvailable()) {
            throw IllegalStateException(
                "Firestore is unavailable — check Firebase project options",
            )
        }
        val uid = FirebaseAuthBridge.currentUserId()?.trim().orEmpty()
        if (uid.isBlank()) {
            throw IllegalStateException(
                "Sign in with Google before using organization \"$trimmed\"",
            )
        }
        if (isMember(gateway, trimmed, uid)) return

        val email = FirebaseAuthBridge.currentUserEmail()
            ?: settings.getFirebaseAuthEmail().takeIf { it.isNotBlank() }
        try {
            val code = MemberRoleAdmin.bootstrapOrgAdmin(
                gateway = gateway,
                orgId = trimmed,
                uid = uid,
                email = email,
                allowedEmailDomains = emptyList(),
            )
            if (settings.getFirebaseOrgId().trim() == trimmed) {
                settings.setFirebaseBootstrapCode(code)
            }
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() }
                ?: "Missing or insufficient permissions"
            throw IllegalStateException(
                "Cannot create or access organization \"$trimmed\": $detail",
                e,
            )
        }

        if (!isMember(gateway, trimmed, uid)) {
            throw IllegalStateException(
                "Organization \"$trimmed\" is still not accessible after bootstrap. " +
                    "Publish firestore.rules and sign in with the correct Google account.",
            )
        }
    }

    /**
     * Provisions a single org in Firestore (called when the user finishes editing an org ID field).
     */
    suspend fun provisionOrgInFirestore(
        gateway: FirestoreGateway,
        settings: SettingsManager,
        orgId: String,
    ) {
        ensureOrgBootstrappedIfNeeded(gateway, settings, orgId)
    }

    /**
     * On cold start: keep the user's active org. Only pick a default when none is set.
     * Never auto-switch away from the active org (that broke the org switcher).
     */
    suspend fun repairActiveOrgIfNeeded(
        gateway: FirestoreGateway,
        settings: SettingsManager,
    ): FirebaseOrgRepairResult {
        if (!gateway.isAvailable()) {
            return FirebaseOrgRepairResult.Ok(settings.getFirebaseOrgId().trim())
        }
        val uid = FirebaseAuthBridge.currentUserId()?.trim().orEmpty()
        if (uid.isBlank()) return FirebaseOrgRepairResult.NotSignedIn

        val configured = settings.getFirebaseConfiguredOrgs()
            .map { it.orgId.trim() }
            .filter { isValidOrgId(it) }
            .distinct()

        val active = settings.getFirebaseOrgId().trim()
        if (active.isNotBlank()) {
            runCatching { ensureOrgBootstrappedIfNeeded(gateway, settings, active) }
            return FirebaseOrgRepairResult.Ok(active)
        }

        if (configured.isEmpty()) {
            return FirebaseOrgRepairResult.NoOrgsConfigured
        }

        for (orgId in configured) {
            runCatching { ensureOrgBootstrappedIfNeeded(gateway, settings, orgId) }
            if (isMember(gateway, orgId, uid)) {
                settings.setFirebaseOrgId(orgId)
                return FirebaseOrgRepairResult.Recovered(orgId, "")
            }
        }
        settings.setFirebaseOrgId(configured.first())
        return FirebaseOrgRepairResult.Recovered(configured.first(), "")
    }
}
