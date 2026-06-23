package com.eventmanager.app

import android.app.Application
import android.util.Log
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.platform.createPlatformContext
import java.util.concurrent.Executors

class EventManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Executors.newSingleThreadExecutor().execute {
            try {
                createDatabase(createPlatformContext(applicationContext))
            } catch (e: Exception) {
                Log.e("EventManagerApplication", "Database warmup failed", e)
            }
        }
    }
}
