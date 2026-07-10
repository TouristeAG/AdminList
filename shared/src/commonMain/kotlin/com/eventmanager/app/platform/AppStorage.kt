package com.eventmanager.app.platform

/**
 * Key-value settings storage (replaces Android SharedPreferences).
 */
interface AppStorage {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, default: Float = 0f): Float
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
    fun contains(key: String): Boolean
    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String>
    fun putStringSet(key: String, value: Set<String>)
    fun clear()
}

expect fun createAppStorage(context: PlatformContext): AppStorage
