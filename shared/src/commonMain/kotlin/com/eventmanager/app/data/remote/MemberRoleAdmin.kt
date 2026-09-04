package com.eventmanager.app.data.remote

import com.eventmanager.app.data.security.crypto.BootstrapCodeHash
import kotlin.coroutines.cancellation.CancellationException
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
            "updatedAt" to System.currentTimeMillis(),
        )
        // A null email would erase the stored one on a merge write.
        email?.trim()?.takeIf { it.isNotBlank() }?.let { memberData["email"] = it }
        bootstrapCode?.takeIf { it.isNotBlank() }?.let {
            memberData["bootstrapCode"] = BootstrapCodeHash.hash(it)
        }
        gateway.upsertDocument(orgId, "members", uid, memberData)
        if (role == MemberRole.ADMIN && bootstrapCode != null) {
            gateway.upsertDocument(
                orgId,
                "metadata",
                "config",
                buildMetadataConfig(orgId, allowedEmailDomains, bootstrapCode),
            )
        }
    }

    /**
     * Promote or invite a colleague. Does not rewrite org metadata / join codes.
     */
    suspend fun assignTeamMember(
        gateway: FirestoreGateway,
        orgId: String,
        uid: String,
        role: MemberRole,
        email: String?,
        allowedEmailDomains: List<String> = emptyList(),
    ) {
        val org = orgId.trim()
        val memberUid = uid.trim()
        if (org.isBlank() || isFirebaseOrgAllSentinel(org)) {
            error("NO_ORG")
        }
        if (memberUid.isBlank()) {
            error("BLANK_UID")
        }
        val mail = email?.trim()?.ifBlank { null }
        if (allowedEmailDomains.isNotEmpty() && mail == null) {
            error("EMAIL_REQUIRED")
        }
        if (!FirebaseEmailDomainPolicy.isEmailAllowed(mail, allowedEmailDomains)) {
            throw IllegalArgumentException(
                FirebaseEmailDomainPolicy.denialMessage(mail, allowedEmailDomains),
            )
        }
        val memberData = mutableMapOf<String, Any?>(
            "role" to role.storageValue(),
            "updatedAt" to System.currentTimeMillis(),
        )
        mail?.let { memberData["email"] = it }
        println("Firebase team: assigning ${role.storageValue()} to $memberUid in org $org")
        try {
            gateway.upsertDocument(org, "members", memberUid, memberData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Firebase team: assign failed: ${e.message}")
            if (isFirestorePermissionDenied(e)) {
                error("PERMISSION")
            }
            throw e
        }
        println("Firebase team: assign succeeded for $memberUid")
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
            bootstrapCode = BootstrapCodeHash.hash(code),
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
            bootstrapCode = BootstrapCodeHash.hash(bootstrapCode.trim()),
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
            buildMetadataConfig(
                orgId,
                domains,
                bootstrapCode = null,
                // Explicit admin edit: an empty list must really clear the allowlist.
                publishAllowedEmailDomains = true,
            ),
        )
    }

    /**
     * Create or rotate the org invitation code and publish its hash to `metadata/config`.
     * Returns the new plaintext code (store locally / embed in join QR).
     * Uses the same hashing path as [bootstrapOrgAdmin] so joins keep working.
     */
    suspend fun rotateBootstrapCode(
        gateway: FirestoreGateway,
        orgId: String,
        allowedEmailDomains: List<String> = emptyList(),
    ): String {
        if (orgId.isBlank()) error("NO_ORG")
        val code = generateBootstrapCode()
        gateway.upsertDocument(
            orgId,
            "metadata",
            "config",
            buildMetadataConfig(
                orgId,
                allowedEmailDomains,
                // Same double-hash convention as bootstrapOrgAdmin → upsertMember.
                bootstrapCode = BootstrapCodeHash.hash(code),
            ),
        )
        return code
    }

    fun generateBootstrapCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    /**
     * @param publishAllowedEmailDomains when false the key is omitted entirely, so the merge write
     * leaves the org's existing allowlist alone. Only an explicit admin edit may clear it —
     * a bootstrap or a code rotation must never silently open the org to every domain.
     */
    private fun buildMetadataConfig(
        orgId: String,
        allowedEmailDomains: List<String>,
        bootstrapCode: String?,
        publishAllowedEmailDomains: Boolean = allowedEmailDomains.isNotEmpty(),
    ): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "backendType" to BackendType.FIREBASE.name,
            "schemaVersion" to 1,
            "firebaseOrgId" to orgId,
        )
        if (publishAllowedEmailDomains) {
            map["allowedEmailDomains"] =
                FirebaseEmailDomainPolicy.domainsToFirestoreMap(allowedEmailDomains)
            map["allowedEmailDomainsUpdatedAt"] = System.currentTimeMillis()
        }
        if (bootstrapCode != null) {
            map["createdAt"] = System.currentTimeMillis()
            map["bootstrapCodeHash"] = BootstrapCodeHash.hash(bootstrapCode)
        }
        return map
    }
}
