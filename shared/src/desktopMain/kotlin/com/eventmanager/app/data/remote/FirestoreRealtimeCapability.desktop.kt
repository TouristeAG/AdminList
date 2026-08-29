package com.eventmanager.app.data.remote

actual object FirestoreRealtimeCapability {
    // Start listeners when SDK is up; keep a 30s pull as Desktop safety net (PR2 spike).
    actual fun preferSnapshotListeners(): Boolean = true
    actual fun alsoRunPullFallback(): Boolean = true
}
