package com.eventmanager.app.data.remote

actual object FirestoreRealtimeCapability {
    actual fun preferSnapshotListeners(): Boolean = true
    actual fun alsoRunPullFallback(): Boolean = false
    actual fun periodicPullIntervalMs(): Long? = null
    actual fun listenerPollIntervalMs(): Long? = null
    actual fun pullFallbackIntervalMs(): Long? = null
    actual fun deferStartupPullToBackgroundJob(): Boolean = true
}
