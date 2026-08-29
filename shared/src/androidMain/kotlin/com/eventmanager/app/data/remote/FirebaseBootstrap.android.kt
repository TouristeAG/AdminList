package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.app
import dev.gitlive.firebase.initialize

actual object FirebaseBootstrap {
    actual fun ensureInitialized(
        platformContext: PlatformContext,
        options: FirebaseProjectOptions?,
    ): Boolean {
        if (isInitialized()) return true
        // Prefer google-services.json auto-init when present
        if (runCatching { Firebase.app; true }.getOrDefault(false)) return true
        val opts = options ?: return false
        return runCatching {
            Firebase.initialize(
                context = platformContext.androidContext,
                options = FirebaseOptions(
                    applicationId = opts.applicationId,
                    apiKey = opts.apiKey,
                    projectId = opts.projectId,
                    gcmSenderId = opts.gcmSenderId.ifBlank { null },
                    storageBucket = opts.storageBucket.ifBlank { null },
                ),
            )
            true
        }.getOrDefault(false)
    }

    actual fun isInitialized(): Boolean = runCatching {
        Firebase.app
        true
    }.getOrDefault(false)
}
