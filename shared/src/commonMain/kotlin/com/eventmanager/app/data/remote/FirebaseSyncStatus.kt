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
    /** Snapshot listeners are active and the last snapshot was confirmed by the server. */
    LIVE,
    /** Periodic pull loop (no listeners or Desktop safety net). */
    PULL,
    /** Missing org / SDK, or listeners are only serving the local cache (no network). */
    OFFLINE,
}

internal fun firebaseSyncTransport(
    orgConfigured: Boolean,
    sdkAvailable: Boolean,
    listenersActive: Boolean,
    serverReachable: Boolean,
    pullJobActive: Boolean,
): FirebaseSyncTransport {
    if (!orgConfigured || !sdkAvailable) return FirebaseSyncTransport.OFFLINE
    if (listenersActive && serverReachable) return FirebaseSyncTransport.LIVE
    if (listenersActive) return FirebaseSyncTransport.OFFLINE
    if (pullJobActive) return FirebaseSyncTransport.PULL
    return FirebaseSyncTransport.OFFLINE
}
