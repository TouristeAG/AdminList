package com.eventmanager.app.data.database

import android.content.Context
import androidx.room.Room

object EventManagerDatabaseAndroid {
    @Volatile
    private var INSTANCE: EventManagerDatabase? = null

    fun getDatabase(context: Context): EventManagerDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                EventManagerDatabase::class.java,
                "event_manager_database"
            )
                .addMigrations(*EventManagerDatabase.ALL_MIGRATIONS)
                .fallbackToDestructiveMigration()
                .build()
            INSTANCE = instance
            instance
        }
    }

    fun clearDatabase(context: Context) {
        synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
            val dbFile = context.getDatabasePath("event_manager_database")
            if (dbFile.exists()) {
                dbFile.delete()
                println("Deleted existing database file to force recreation")
            }
        }
    }
}
