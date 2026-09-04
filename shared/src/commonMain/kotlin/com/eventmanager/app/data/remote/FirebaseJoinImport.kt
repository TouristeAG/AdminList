package com.eventmanager.app.data.remote

import com.eventmanager.app.data.security.firebaseOAuthCredentialsReady
import com.eventmanager.app.data.sync.SettingsManager

/** What a scanned / pasted join code was still missing once applied. */
enum class FirebaseJoinImportProblem {
    /** Legacy v2 QR, or a v1 QR rendered before the admin filled the Web client secret. */
    OAUTH_SECRET_MISSING,

    /** The admin device had no invitation code when it rendered the QR. */
    INVITATION_MISSING,
}

sealed class FirebaseJoinImportResult {
    /** The device has everything it needs to sign in and join [orgId]. */
    data class Complete(val orgId: String) : FirebaseJoinImportResult()

    /** Project options were stored, but the device still cannot join. */
    data class Incomplete(
        val orgId: String,
        val problem: FirebaseJoinImportProblem,
    ) : FirebaseJoinImportResult()

    data class Undecodable(val message: String?) : FirebaseJoinImportResult()
}

/**
 * Applies a scanned or pasted join code and reports what is still missing.
 *
 * Both the setup wizard scanner and the join card go through here. Applying a payload without
 * re-checking the OAuth secret and the invitation code marks the device as "configuration
 * received" while every later step stays blocked, with nothing on screen explaining why —
 * exactly what a legacy v2 QR produces.
 */
object FirebaseJoinImport {
    fun apply(
        settings: SettingsManager,
        raw: String,
        fallbackInvitationCode: String = "",
    ): FirebaseJoinImportResult {
        val payload = FirebaseJoinCodec.decode(raw).getOrElse { e ->
            return FirebaseJoinImportResult.Undecodable(e.message)
        }
        settings.applyFirebaseJoinPayload(payload)
        // Prefer the invitation from the code itself; only fall back to a manually typed one.
        val invitation = payload.bootstrapCode.trim().ifBlank { fallbackInvitationCode.trim() }
        if (invitation.isNotBlank()) settings.setFirebaseBootstrapCode(invitation)

        val orgId = payload.orgId.trim()
        return when {
            !firebaseOAuthCredentialsReady(
                settings.getFirebaseWebClientId(),
                settings.getFirebaseWebClientSecret(),
            ) -> FirebaseJoinImportResult.Incomplete(
                orgId,
                FirebaseJoinImportProblem.OAUTH_SECRET_MISSING,
            )

            settings.getFirebaseBootstrapCode().isBlank() -> FirebaseJoinImportResult.Incomplete(
                orgId,
                FirebaseJoinImportProblem.INVITATION_MISSING,
            )

            else -> FirebaseJoinImportResult.Complete(orgId)
        }
    }
}
