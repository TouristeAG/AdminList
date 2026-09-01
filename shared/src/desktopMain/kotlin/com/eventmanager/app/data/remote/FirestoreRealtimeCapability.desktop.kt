package com.eventmanager.app.data.remote

actual object FirestoreRealtimeCapability {
    // Same as before REST workarounds — GitLive snapshot listeners with 30s pull safety net.
    actual fun preferSnapshotListeners(): Boolean = true
    actual fun alsoRunPullFallback(): Boolean = true
    actual fun periodicPullIntervalMs(): Long? = null
    actual fun listenerPollIntervalMs(): Long? = null
    actual fun pullFallbackIntervalMs(): Long? = null
    actual fun deferStartupPullToBackgroundJob(): Boolean = false
}
