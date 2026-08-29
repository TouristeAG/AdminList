package com.eventmanager.app.data.remote

/**
 * Institution-level announcement that all devices of an org must use the same backend.
 * Synced via Sheets Settings tab and Firestore institutionSettings / metadata.
 *
 * When switching to Firebase, optional project options are included so peers can apply
 * them locally without re-typing (UI must not display apiKey / appId to operators).
 */
data class InstitutionBackendAnnouncement(
    val backendType: BackendType,
    val migrationId: String,
    val migratedAt: Long,
    val migratedBy: String = "",
    val firebaseOrgId: String? = null,
    val sheetsSpreadsheetIdHint: String? = null,
    val firebaseProjectId: String? = null,
    val firebaseApplicationId: String? = null,
    val firebaseApiKey: String? = null,
    val firebaseWebClientId: String? = null,
    /** Institution Web client secret for Desktop OAuth code exchange (not developer Gmail JSON). */
    val firebaseWebClientSecret: String? = null,
) {
    fun hasFirebaseProjectOptions(): Boolean =
        !firebaseProjectId.isNullOrBlank() &&
            !firebaseApplicationId.isNullOrBlank() &&
            !firebaseApiKey.isNullOrBlank()
}
