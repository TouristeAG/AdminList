package com.eventmanager.app.data.remote

/**
 * Whether Firestore snapshot listeners should be started for this platform.
 * Desktop uses a REST change poller instead of gRPC GitLive listeners in packaged installers.
 */
expect object FirestoreRealtimeCapability {
    fun preferSnapshotListeners(): Boolean
    /** Safety-net full pull while snapshot listeners are active. */
    fun alsoRunPullFallback(): Boolean
    /** Interval between full pulls when listeners are off; null = listeners/poller handle updates. */
    fun periodicPullIntervalMs(): Long?
    /** Interval between REST listener polls on desktop (ms); null = native GitLive listeners. */
    fun listenerPollIntervalMs(): Long?
    /** Full-pull safety net interval while listeners are active; null = default 30s. */
    fun pullFallbackIntervalMs(): Long?
    /** Whether [EventManagerViewModel] should call performStartupSync in the background job. */
    fun deferStartupPullToBackgroundJob(): Boolean
}
