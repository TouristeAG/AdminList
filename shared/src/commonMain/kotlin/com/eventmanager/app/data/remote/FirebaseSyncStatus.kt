package com.eventmanager.app.data.remote

/**
 * Snapshot for the sync status pill when [BackendType.FIREBASE] is active.
 */
data class FirebaseSyncStatus(
    val mode: FirebaseSyncTransport,
    val lastActivityAt: Long = 0L,
    val pendingWriteCount: Int = 0,
    /** Pending rows that failed at least once (permission denied or transient error). */
    val failedPendingWriteCount: Int = 0,
    val firestoreAvailable: Boolean = false,
    val orgConfigured: Boolean = false,
) {
    companion object {
        fun offline(): FirebaseSyncStatus = FirebaseSyncStatus(mode = FirebaseSyncTransport.OFFLINE)
    }
}

enum class FirebaseSyncTransport {
    /** Firestore snapshot listeners are active. */
    LIVE,
    /** Periodic pull loop (no listeners or Desktop safety net). */
    PULL,
    /** Missing org / SDK / sign-in prerequisites. */
    OFFLINE,
}
