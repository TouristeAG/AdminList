package com.eventmanager.app.data.remote

import kotlin.random.Random

/**
 * Admin-only helper to invite / assign org member roles in Firestore.
 * Also bootstraps org [metadata/config] so security rules allow the first admin.
 */
object MemberRoleAdmin {
    suspend fun upsertMember(
        gateway: FirestoreGateway,
        orgId: String,
        uid: String,
        role: MemberRole,
        email: String?,
        allowedEmailDomains: List<String> = emptyList(),
        bootstrapCode: String? = null,
    ) {
        if (orgId.isBlank() || uid.isBlank()) return
        if (!FirebaseEmailDomainPolicy.isEmailAllowed(email, allowedEmailDomains)) {
            throw IllegalArgumentException(
                FirebaseEmailDomainPolicy.denialMessage(email, allowedEmailDomains),
            )
        }
        val memberData = mutableMapOf<String, Any?>(
            "role" to role.storageValue(),
            "email" to email,
            "updatedAt" to System.currentTimeMillis(),
        )
        bootstrapCode?.takeIf { it.isNotBlank() }?.let { memberData["bootstrapCode"] = it }
        gateway.upsertDocument(orgId, "members", uid, memberData)
        if (role == MemberRole.ADMIN) {
            val code = bootstrapCode?.takeIf { it.isNotBlank() } ?: generateBootstrapCode()
            gateway.upsertDocument(
                orgId,
                "metadata",
                "config",
                buildMetadataConfig(orgId, allowedEmailDomains, code),
            )
        }
    }

    /** First org setup (wizard / migration): admin + metadata with join bootstrap code. */
    suspend fun bootstrapOrgAdmin(
        gateway: FirestoreGateway,
        orgId: String,
        uid: String,
        email: String?,
        allowedEmailDomains: List<String> = emptyList(),
    ): String {
        val code = generateBootstrapCode()
        upsertMember(
            gateway = gateway,
            orgId = orgId,
            uid = uid,
            role = MemberRole.ADMIN,
            email = email,
            allowedEmailDomains = allowedEmailDomains,
            bootstrapCode = code,
        )
        return code
    }

    /**
     * Joining device after Google sign-in: create member doc only (never promotes to admin).
     * [bootstrapCode] must match `metadata/config.bootstrapCode` (Firestore rules).
     */
    suspend fun joinOrgAsMember(
        gateway: FirestoreGateway,
        orgId: String,
        uid: String,
        email: String?,
        bootstrapCode: String,
        allowedEmailDomains: List<String> = emptyList(),
    ) {
        if (bootstrapCode.isBlank()) {
            throw IllegalArgumentException("Bootstrap code required to join this organization")
        }
        upsertMember(
            gateway = gateway,
            orgId = orgId,
            uid = uid,
            role = MemberRole.MEMBER,
            email = email,
            allowedEmailDomains = allowedEmailDomains,
            bootstrapCode = bootstrapCode.trim(),
        )
    }

    /** Publish allowed email domains into `metadata/config` for Firestore rules. */
    suspend fun publishAllowedEmailDomains(
        gateway: FirestoreGateway,
        orgId: String,
        domains: List<String>,
    ) {
        if (orgId.isBlank()) return
        gateway.upsertDocument(
            orgId,
            "metadata",
            "config",
            buildMetadataConfig(orgId, domains, bootstrapCode = null),
        )
    }

    fun generateBootstrapCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun buildMetadataConfig(
        orgId: String,
        allowedEmailDomains: List<String>,
        bootstrapCode: String?,
    ): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "backendType" to BackendType.FIREBASE.name,
            "schemaVersion" to 1,
            "firebaseOrgId" to orgId,
            "allowedEmailDomains" to FirebaseEmailDomainPolicy.domainsToFirestoreMap(allowedEmailDomains),
            "allowedEmailDomainsUpdatedAt" to System.currentTimeMillis(),
        )
        if (bootstrapCode != null) {
            map["createdAt"] = System.currentTimeMillis()
            map["bootstrapCode"] = bootstrapCode
        }
        return map
    }
}
