package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import kotlin.coroutines.cancellation.CancellationException

sealed class FirebaseOrgRepairResult {
    data class Ok(val orgId: String) : FirebaseOrgRepairResult()
    data class Recovered(val orgId: String, val previousOrgId: String) : FirebaseOrgRepairResult()
    data object NotSignedIn : FirebaseOrgRepairResult()
    data object NoOrgsConfigured : FirebaseOrgRepairResult()

    /** Org is configured but the server refused membership — [message] is user-facing. */
    data class Blocked(val orgId: String, val message: String) : FirebaseOrgRepairResult()
}

/**
 * What a caller is allowed to do when the signed-in user is not a member yet.
 */
enum class OrgBootstrapIntent {
    /**
     * Startup, org switch, multi-org pull. Joins with the stored invitation code at most —
     * never creates an organization, so a stale local state cannot rewrite an existing org.
     */
    ENSURE_MEMBERSHIP,

    /**
     * Explicit admin action (setup wizard "create", org ID field commit). May create the org
     * and become its first admin, which Firestore rules only permit when no config exists.
     */
    CREATE_IF_MISSING,
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

    private fun isOrgIdAlphanumeric(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9'

    private const val ACCENTED = "àáâãäåÀÁÂÃÄÅèéêëÈÉÊËìíîïÌÍÎÏòóôõöÒÓÔÕÖùúûüÙÚÛÜçÇñÑÿŸ"
    private const val UNACCENTED = "aaaaaaAAAAAAeeeeEEEEiiiiIIIIoooooOOOOOuuuuUUUUcCnNyY"

    /**
     * Coerces typed input into a legal org ID: accents are folded to ASCII, whitespace runs become
     * `-`, characters a Firestore path segment cannot carry are dropped, and leading separators
     * are trimmed.
     *
     * Input fields must apply this as the user types. An org name like "Collectif Nocturne" is
     * otherwise stored verbatim, fails [isValidOrgId] forever, and blocks the setup wizard with
     * no visible reason. Folding rather than dropping accents matters too — deleting them outright
     * would turn "Société" into "Socit".
     */
    fun sanitizeOrgId(raw: String): String =
        raw.map { c -> ACCENTED.indexOf(c).let { if (it >= 0) UNACCENTED[it] else c } }
            .joinToString("")
            .replace(Regex("\\s+"), "-")
            .filter { isOrgIdAlphanumeric(it) || it == '-' || it == '_' }
            .dropWhile { !isOrgIdAlphanumeric(it) }
            .take(64)

    suspend fun isMember(gateway: FirestoreGateway, orgId: String, uid: String): Boolean =
        gateway.readMemberRole(orgId.trim(), uid.trim()) != null

    /**
     * Makes sure the signed-in user has a `members/{uid}` document in [orgId].
     *
     * Membership is probed against the server first, because "no member doc", "read refused" and
     * "cannot reach Firestore" need opposite reactions. Re-joining an org the account already
     * belongs to is a plain re-write of its own member doc; attempting an admin bootstrap there
     * is what produced the permanent `PERMISSION_DENIED` after a factory reset.
     *
     * [signedInUid] / [signedInEmail] let a sign-in callback pass the identity it just obtained,
     * before the platform auth cache has caught up.
     */
    suspend fun ensureOrgBootstrappedIfNeeded(
        gateway: FirestoreGateway,
        settings: SettingsManager,
        orgId: String,
        intent: OrgBootstrapIntent = OrgBootstrapIntent.ENSURE_MEMBERSHIP,
        signedInUid: String? = null,
        signedInEmail: String? = null,
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
        val uid = signedInUid?.trim()?.takeIf { it.isNotBlank() }
            ?: FirebaseAuthBridge.currentUserId()?.trim().orEmpty()
        if (uid.isBlank()) {
            throw IllegalStateException(
                "Sign in with Google before using organization \"$trimmed\"",
            )
        }

        val probe = gateway.probeMembership(trimmed, uid)
        if (probe is MembershipProbe.Member) {
            // Already a member: nothing to write, and the invitation code is no longer needed.
            forgetInvitationCodeAfterJoin(settings, trimmed)
            return
        }
        if (probe is MembershipProbe.Unavailable) {
            // Offline or timed out. Writing here would guess at the remote state.
            println("Firebase org $trimmed: membership unknown (offline) — skipping bootstrap")
            return
        }
        if (probe is MembershipProbe.Denied) {
            // The server refused our read of members/{uid}. The rule allows every signed-in
            // user to read their own member doc, so Denied almost always means a transient
            // auth-token propagation delay on a brand-new device.
            //
            // NEVER attempt joinOrgAsMember here: that call writes role=MEMBER, and if
            // Firestore evaluates isOrgAdmin() as still-true for this user (because the
            // existing doc has role=admin), the merge-write is allowed — permanently
            // demoting the admin. Treating Denied like Unavailable is the safe default.
            println("Firebase org $trimmed: membership probe denied — skipping bootstrap to avoid accidental self-demotion")
            return
        }

        val email = signedInEmail?.trim()?.takeIf { it.isNotBlank() }
            ?: FirebaseAuthBridge.currentUserEmail()
            ?: settings.getFirebaseAuthEmail().takeIf { it.isNotBlank() }
        val invitationCode = settings.getFirebaseBootstrapCode().trim()

        // Nothing to try: without an invitation code only an explicit "create" may write.
        if (invitationCode.isBlank() && intent != OrgBootstrapIntent.CREATE_IF_MISSING) {
            throw IllegalStateException(
                firebaseMemberWriteDenialMessage(trimmed, probe, hasInvitationCode = false),
            )
        }

        try {
            if (invitationCode.isNotBlank()) {
                MemberRoleAdmin.joinOrgAsMember(
                    gateway = gateway,
                    orgId = trimmed,
                    uid = uid,
                    email = email,
                    bootstrapCode = invitationCode,
                    allowedEmailDomains = settings.getAllowedEmailDomains(),
                )
            } else {
                val code = MemberRoleAdmin.bootstrapOrgAdmin(
                    gateway = gateway,
                    orgId = trimmed,
                    uid = uid,
                    email = email,
                    allowedEmailDomains = settings.getAllowedEmailDomains(),
                )
                if (settings.getFirebaseOrgId().trim() == trimmed) {
                    settings.setFirebaseBootstrapCode(code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isFirestorePermissionDenied(e)) {
                throw IllegalStateException(
                    firebaseMemberWriteDenialMessage(
                        trimmed,
                        probe,
                        hasInvitationCode = invitationCode.isNotBlank(),
                    ),
                    e,
                )
            }
            val detail = e.message?.takeIf { it.isNotBlank() } ?: "unknown error"
            throw IllegalStateException(
                "Cannot join organization \"$trimmed\": $detail",
                e,
            )
        }

        if (gateway.probeMembership(trimmed, uid) is MembershipProbe.Member) {
            forgetInvitationCodeAfterJoin(settings, trimmed)
        }
    }

    /**
     * Drops the plaintext org invitation code once membership is confirmed by the server.
     * A joined member device has no use for it, and it is the secret that lets a device join.
     */
    private fun forgetInvitationCodeAfterJoin(settings: SettingsManager, orgId: String) {
        if (settings.getFirebaseBootstrapCode().isBlank()) return
        // Admin devices keep it: they need it to render the join QR for the rest of the team.
        if (settings.isFirebaseJoinImported() && settings.getFirebaseOrgId().trim() == orgId) {
            settings.setFirebaseBootstrapCode("")
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
        ensureOrgBootstrappedIfNeeded(
            gateway,
            settings,
            orgId,
            intent = OrgBootstrapIntent.CREATE_IF_MISSING,
        )
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
            val failure = runCatching {
                ensureOrgBootstrappedIfNeeded(gateway, settings, active)
            }.exceptionOrNull()
            if (failure is CancellationException) throw failure
            return when {
                failure != null -> FirebaseOrgRepairResult.Blocked(
                    active,
                    failure.message?.takeIf { it.isNotBlank() }
                        ?: "Organization \"$active\" is not accessible with this account.",
                )
                else -> FirebaseOrgRepairResult.Ok(active)
            }
        }

        if (configured.isEmpty()) {
            return FirebaseOrgRepairResult.NoOrgsConfigured
        }

        for (orgId in configured) {
            runCatching { ensureOrgBootstrappedIfNeeded(gateway, settings, orgId) }
            if (gateway.probeMembership(orgId, uid) is MembershipProbe.Member) {
                settings.setFirebaseOrgId(orgId)
                return FirebaseOrgRepairResult.Recovered(orgId, "")
            }
        }
        settings.setFirebaseOrgId(configured.first())
        return FirebaseOrgRepairResult.Recovered(configured.first(), "")
    }
}
