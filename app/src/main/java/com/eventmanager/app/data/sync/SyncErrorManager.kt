package com.eventmanager.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Manages sync error popups and tracks "do not tell me again today" state.
 * Resets at midnight (00:00) each day.
 */
class SyncErrorManager(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "sync_error_manager",
        Context.MODE_PRIVATE
    )
    
    private companion object {
        const val PREF_DO_NOT_TELL_TODAY = "do_not_tell_sync_error_today"
        const val PREF_LAST_RESET_DATE = "last_reset_date"
    }
    
    /**
     * Check if the user has enabled "do not tell me again today"
     */
    fun shouldSuppressError(): Boolean {
        resetIfNewDay()
        return preferences.getBoolean(PREF_DO_NOT_TELL_TODAY, false)
    }
    
    /**
     * Set "do not tell me again today" to true
     */
    fun setSuppressErrorToday() {
        preferences.edit().apply {
            putBoolean(PREF_DO_NOT_TELL_TODAY, true)
            apply()
        }
    }
    
    /**
     * Reset the "do not tell me again today" state at midnight
     */
    private fun resetIfNewDay() {
        val today = getCurrentDate()
        val lastResetDate = preferences.getString(PREF_LAST_RESET_DATE, "")
        
        if (lastResetDate != today) {
            // New day, reset the suppression
            preferences.edit().apply {
                putBoolean(PREF_DO_NOT_TELL_TODAY, false)
                putString(PREF_LAST_RESET_DATE, today)
                apply()
            }
        }
    }
    
    /**
     * Get today's date as a string (YYYY-MM-DD)
     */
    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        return "$year-$month-$day"
    }
    
    /**
     * Reset all stored values (for debugging or manual reset)
     */
    fun reset() {
        preferences.edit().clear().apply()
    }
}
