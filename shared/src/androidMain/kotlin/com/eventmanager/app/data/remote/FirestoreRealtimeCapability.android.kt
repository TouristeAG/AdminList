package com.eventmanager.app.data.remote

actual object FirestoreRealtimeCapability {
    actual fun preferSnapshotListeners(): Boolean = true
    actual fun alsoRunPullFallback(): Boolean = false
}
