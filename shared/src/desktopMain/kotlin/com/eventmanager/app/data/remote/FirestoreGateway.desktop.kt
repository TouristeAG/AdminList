package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

/**
 * Desktop: GitLive Firestore (full JRE in installers via includeAllModules).
 * REST helpers remain for Auth / Storage; Firestore sync matches ./gradlew :desktopApp:run.
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
