package com.eventmanager.app

import android.app.Application
import android.util.Log
import com.eventmanager.app.data.remote.FirebaseBootstrap
import com.eventmanager.app.data.remote.FirebaseOptionsReader
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.platform.createPlatformContext
import java.util.concurrent.Executors

class EventManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val platformContext = createPlatformContext(applicationContext)
        runCatching {
            val settings = SettingsManager(platformContext)
            FirebaseBootstrap.ensureInitialized(
                platformContext,
                FirebaseOptionsReader.fromSettings(settings),
            )
        }.onFailure {
            Log.w("EventManagerApplication", "Firebase bootstrap skipped: ${it.message}")
        }
        Executors.newSingleThreadExecutor().execute {
            try {
                createDatabase(platformContext)
            } catch (e: Exception) {
                Log.e("EventManagerApplication", "Database warmup failed", e)
            }
        }
    }
}
