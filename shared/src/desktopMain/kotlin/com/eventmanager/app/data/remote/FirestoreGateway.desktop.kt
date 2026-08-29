package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

/**
 * Desktop: always [GitLiveFirestoreGateway] (lazy availability).
 * [DesktopFirebaseSpike] / [FirestoreRealtimeCapability] still control pull fallback.
 */
actual fun createFirestoreGateway(
    platformContext: PlatformContext?,
    settingsManager: SettingsManager?,
): FirestoreGateway {
    if (platformContext != null && settingsManager != null) {
        FirebaseBootstrap.ensureInitialized(
            platformContext,
            FirebaseOptionsReader.fromSettings(settingsManager),
        )
    }
    return GitLiveFirestoreGateway(platformContext, settingsManager)
}
