package com.eventmanager.app.platform

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings

actual fun createAppStorage(context: PlatformContext): AppStorage {
    val prefs = context.androidContext.getSharedPreferences("event_manager_settings", Context.MODE_PRIVATE)
    return SettingsAppStorage(prefs, SharedPreferencesSettings(prefs))
}

private class SettingsAppStorage(
    private val prefs: android.content.SharedPreferences,
    private val settings: Settings
) : AppStorage {
    override fun getString(key: String, default: String) = settings.getStringOrNull(key) ?: default
    override fun putString(key: String, value: String) = settings.putString(key, value)
    override fun getBoolean(key: String, default: Boolean) = settings.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = settings.putBoolean(key, value)
    override fun getInt(key: String, default: Int) = settings.getInt(key, default)
    override fun putInt(key: String, value: Int) = settings.putInt(key, value)
    override fun getLong(key: String, default: Long) = settings.getLong(key, default)
    override fun putLong(key: String, value: Long) = settings.putLong(key, value)
    override fun getFloat(key: String, default: Float) = settings.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = settings.putFloat(key, value)
    override fun remove(key: String) = settings.remove(key)
    override fun contains(key: String) = settings.hasKey(key)
    override fun getStringSet(key: String, default: Set<String>): Set<String> =
        prefs.getStringSet(key, default) ?: default
    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
    override fun clear() = settings.clear()
}
