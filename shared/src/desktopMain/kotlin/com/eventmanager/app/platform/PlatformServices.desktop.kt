package com.eventmanager.app.platform

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eventmanager.app.data.database.EventManagerDatabase
import java.awt.Desktop
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers

actual fun createDatabase(context: PlatformContext): EventManagerDatabase {
    val dbFile = java.io.File(context.appDataDir, "event_manager_database")
    return Room.databaseBuilder<EventManagerDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*EventManagerDatabase.ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun vibrateShort(context: PlatformContext) {
    // No haptics on desktop
}

actual fun getSystemLocaleTag(): String = Locale.getDefault().toLanguageTag()
