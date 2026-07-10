package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.AppStorage
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import java.util.Calendar

class SyncErrorManager(platformContext: PlatformContext) {
    private val storage: AppStorage = createAppStorage(platformContext)

    private companion object {
        const val PREF_DO_NOT_TELL_TODAY = "do_not_tell_sync_error_today"
        const val PREF_LAST_RESET_DATE = "last_reset_date"
    }

    fun shouldSuppressError(): Boolean {
        resetIfNewDay()
        return storage.getBoolean(PREF_DO_NOT_TELL_TODAY, false)
    }

    fun setSuppressErrorToday() {
        storage.putBoolean(PREF_DO_NOT_TELL_TODAY, true)
    }

    private fun resetIfNewDay() {
        val today = getCurrentDate()
        val lastResetDate = storage.getString(PREF_LAST_RESET_DATE, "")
        if (lastResetDate != today) {
            storage.putBoolean(PREF_DO_NOT_TELL_TODAY, false)
            storage.putString(PREF_LAST_RESET_DATE, today)
        }
    }

    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        return "$year-$month-$day"
    }

    fun reset() {
        storage.clear()
    }
}
