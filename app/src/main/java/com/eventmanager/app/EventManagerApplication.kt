package com.eventmanager.app

import android.app.Application
import android.util.Log
import com.eventmanager.app.data.database.EventManagerDatabase
import java.util.concurrent.Executors

class EventManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Open SQLite on a background thread so the first Activity frame is not blocked by
        // Room.openHelper / migrations (common cause of startup ANRs).
        Executors.newSingleThreadExecutor().execute {
            try {
                EventManagerDatabase.getDatabase(applicationContext)
            } catch (e: Exception) {
                Log.e("EventManagerApplication", "Database warmup failed", e)
            }
        }
    }
}

