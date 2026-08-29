package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext

actual fun createFirestoreGateway(
    platformContext: PlatformContext?,
    settingsManager: SettingsManager?,
): FirestoreGateway {
    // Always return GitLive — isAvailable() is evaluated lazily per call so configuring
    // Firebase options mid-session (Settings) does not leave the coordinator stuck on NoOp.
    return GitLiveFirestoreGateway(platformContext, settingsManager)
}
