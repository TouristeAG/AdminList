package com.eventmanager.app.data.remote

/**
 * Desktop JVM Firestore listener capability probe (PR 2 spike artifact).
 *
 * Result is stored so Settings / diagnostics can show whether realtime listeners are usable.
 * If [listenersAdequate] is false, [FirebaseRemoteBackend] may fall back to a 30s pull loop
 * (Firebase-only; Sheets path unchanged).
 */
data class DesktopFirebaseSpikeResult(
    val sdkPresent: Boolean,
    val listenersAdequate: Boolean,
    val notes: String,
)

object DesktopFirebaseSpike {
    fun probe(): DesktopFirebaseSpikeResult {
        val sdkPresent = runCatching {
            Class.forName("dev.gitlive.firebase.Firebase")
            true
        }.getOrDefault(false)
        val notes = if (sdkPresent) {
            "GitLive on classpath. Validate snapshot listeners on Desktop JVM with a Firebase project. " +
                "Fallback: 30s pull in Firebase mode only."
        } else {
            "GitLive Firebase SDK not on classpath yet. Enable gitlive firebase dependencies and " +
                "provision FirebaseOptions. Fallback: 30s pull in Firebase mode only."
        }
        return DesktopFirebaseSpikeResult(
            sdkPresent = sdkPresent,
            listenersAdequate = sdkPresent,
            notes = notes,
        )
    }
}
