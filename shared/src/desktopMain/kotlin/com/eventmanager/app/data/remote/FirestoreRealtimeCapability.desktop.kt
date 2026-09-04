package com.eventmanager.app.data.remote

actual object FirestoreRealtimeCapability {
    // GitLive snapshot listeners; when LIVE, periodic safety pull uses Settings sync interval (default 5 min).
    // When listeners are degraded/offline, keep the 30s REST safety net until LIVE is confirmed.
    actual fun preferSnapshotListeners(): Boolean = true
    actual fun alsoRunPullFallback(): Boolean = true
    actual fun periodicPullIntervalMs(): Long? = null
    actual fun listenerPollIntervalMs(): Long? = null
    actual fun pullFallbackIntervalMs(): Long? = null
    actual fun deferStartupPullToBackgroundJob(): Boolean = false
}
