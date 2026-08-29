package com.eventmanager.app.data.remote

import android.app.Application
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.app
import dev.gitlive.firebase.initialize
import java.io.File

actual object FirebaseBootstrap {
    @Volatile
    private var platformReady = false

    @Volatile
    private var platformInstance: FirebasePlatform? = null

    @Volatile
    private var lastFailure: String? = null

    /** Exposed so Desktop Google Sign-In can inject the Auth session into platform KV. */
    fun platformOrNull(): FirebasePlatform? = platformInstance

    /** Last bootstrap failure (platform or [Firebase.initialize]); cleared on success. */
    fun lastFailureMessage(): String? = lastFailure

    actual fun ensureInitialized(
        platformContext: PlatformContext,
        options: FirebaseProjectOptions?,
    ): Boolean {
        if (!ensurePlatform(platformContext)) return false
        if (isInitialized()) {
            lastFailure = null
            return true
        }
        val opts = options
        if (opts == null || !opts.isComplete()) {
            lastFailure =
                "Missing Firebase project options (Project ID, Application ID, API key)."
            return false
        }
        return runCatching {
            // GitLive JVM requires a non-null android.content.Context; firebase-java-sdk
            // provides a stub Application for Desktop (null throws and was swallowed before).
            Firebase.initialize(
                context = Application(),
                options = FirebaseOptions(
                    applicationId = opts.applicationId,
                    apiKey = opts.apiKey,
                    projectId = opts.projectId,
                    gcmSenderId = opts.gcmSenderId.ifBlank { null },
                    storageBucket = opts.storageBucket.ifBlank { null },
                ),
            )
            lastFailure = null
            true
        }.getOrElse { e ->
            lastFailure = e.message?.takeIf { it.isNotBlank() }
                ?: e::class.simpleName
                ?: "Firebase.initialize failed"
            false
        }
    }

    actual fun isInitialized(): Boolean = runCatching {
        Firebase.app
        true
    }.getOrDefault(false)

    private fun ensurePlatform(platformContext: PlatformContext): Boolean {
        if (platformReady && platformInstance != null) return true
        synchronized(this) {
            if (platformReady && platformInstance != null) return true
            val storageDir = File(platformContext.appDataDir, "firebase_platform_kv").also { it.mkdirs() }
            return try {
                val platform = object : FirebasePlatform() {
                    override fun store(key: String, value: String) {
                        File(storageDir, sanitize(key)).writeText(value)
                    }

                    override fun retrieve(key: String): String? {
                        val f = File(storageDir, sanitize(key))
                        return if (f.exists()) f.readText() else null
                    }

                    override fun clear(key: String) {
                        File(storageDir, sanitize(key)).delete()
                    }

                    override fun log(msg: String) {
                        val safe = msg
                            .replace(
                                Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"""),
                                "[jwt-redacted]",
                            )
                            .replace(
                                Regex("""(?i)(id[_-]?token|access[_-]?token)\s*[:=]\s*\S+"""),
                                "$1=[redacted]",
                            )
                        println("Firebase: $safe")
                    }

                    private fun sanitize(key: String): String =
                        key.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                }
                FirebasePlatform.initializeFirebasePlatform(platform)
                platformInstance = platform
                platformReady = true
                lastFailure = null
                true
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                // Second call in the same process is OK — platform is already usable.
                if (msg.contains("already", ignoreCase = true)) {
                    platformReady = true
                    lastFailure = null
                    true
                } else {
                    lastFailure = msg.ifBlank { "FirebasePlatform.initializeFirebasePlatform failed" }
                    false
                }
            }
        }
    }
}
