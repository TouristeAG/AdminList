package com.eventmanager.app.data.remote

/**
 * Whether Firestore snapshot listeners should be started for this platform.
 * Desktop may prefer a pull fallback until JVM listeners are validated in production.
 */
expect object FirestoreRealtimeCapability {
    fun preferSnapshotListeners(): Boolean
    fun alsoRunPullFallback(): Boolean
}
