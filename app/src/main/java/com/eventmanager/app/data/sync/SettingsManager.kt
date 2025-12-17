package com.eventmanager.app.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app settings persistence using SharedPreferences
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("event_manager_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_GUEST_LIST_SHEET = "guest_list_sheet"
        private const val KEY_VOLUNTEER_SHEET = "volunteer_sheet"
        private const val KEY_JOBS_SHEET = "jobs_sheet"
        private const val KEY_JOB_TYPES_SHEET = "job_types_sheet"
        private const val KEY_VOLUNTEER_GUEST_LIST_SHEET = "volunteer_guest_list_sheet"
        private const val KEY_VENUES_SHEET = "venues_sheet"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val KEY_ANIMATED_BACKGROUND = "animated_background"
        private const val KEY_PAGE_ANIMATIONS = "page_animations"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_RESOLUTION_SCALE = "resolution_scale"
        private const val KEY_DATE_FORMAT = "date_format"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_DATE_CHANGE_OFFSET_HOURS = "date_change_offset_hours"
        private const val KEY_SEASONAL_FUN = "seasonal_fun"
        private const val KEY_SELECTED_GRAPH_TIME_PERIOD = "selected_graph_time_period"
        private const val KEY_APP_ICON_STYLE = "app_icon_style"
        private const val KEY_APP_ICON_AUTO_ADAPT = "app_icon_auto_adapt"
        private const val KEY_PEOPLE_COUNTER_VISIBLE = "people_counter_visible"
        private const val KEY_STATISTICS_VISIBLE = "statistics_visible"
        private const val KEY_PAGE_SCROLL_BEHAVIOR = "page_scroll_behavior"
    }
    
    // Google Sheets Configuration
    fun getSpreadsheetId(): String {
        return prefs.getString(KEY_SPREADSHEET_ID, GoogleSheetsConfig.SPREADSHEET_ID) ?: GoogleSheetsConfig.SPREADSHEET_ID
    }
    
    fun saveSpreadsheetId(id: String) {
        prefs.edit().putString(KEY_SPREADSHEET_ID, id).apply()
    }
    
    fun getGuestListSheet(): String {
        return prefs.getString(KEY_GUEST_LIST_SHEET, GoogleSheetsConfig.GUEST_LIST_SHEET) ?: GoogleSheetsConfig.GUEST_LIST_SHEET
    }
    
    fun saveGuestListSheet(sheet: String) {
        prefs.edit().putString(KEY_GUEST_LIST_SHEET, sheet).apply()
    }
    
    fun getVolunteerSheet(): String {
        return prefs.getString(KEY_VOLUNTEER_SHEET, GoogleSheetsConfig.VOLUNTEER_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_SHEET
    }
    
    fun saveVolunteerSheet(sheet: String) {
        prefs.edit().putString(KEY_VOLUNTEER_SHEET, sheet).apply()
    }
    
    fun getJobsSheet(): String {
        return prefs.getString(KEY_JOBS_SHEET, GoogleSheetsConfig.JOBS_SHEET) ?: GoogleSheetsConfig.JOBS_SHEET
    }
    
    fun saveJobsSheet(sheet: String) {
        prefs.edit().putString(KEY_JOBS_SHEET, sheet).apply()
    }
    
    fun getVolunteersSheet(): String {
        return prefs.getString(KEY_VOLUNTEER_SHEET, GoogleSheetsConfig.VOLUNTEER_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_SHEET
    }
    
    fun getJobTypesSheet(): String {
        return prefs.getString(KEY_JOB_TYPES_SHEET, GoogleSheetsConfig.JOB_TYPES_SHEET) ?: GoogleSheetsConfig.JOB_TYPES_SHEET
    }
    
    fun saveJobTypesSheet(sheet: String) {
        prefs.edit().putString(KEY_JOB_TYPES_SHEET, sheet).apply()
    }

    fun getVolunteerGuestListSheet(): String {
        return prefs.getString(KEY_VOLUNTEER_GUEST_LIST_SHEET, GoogleSheetsConfig.VOLUNTEER_GUEST_LIST_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_GUEST_LIST_SHEET
    }

    fun saveVolunteerGuestListSheet(sheet: String) {
        prefs.edit().putString(KEY_VOLUNTEER_GUEST_LIST_SHEET, sheet).apply()
    }

    fun getVenuesSheet(): String {
        return prefs.getString(KEY_VENUES_SHEET, GoogleSheetsConfig.VENUES_SHEET) ?: GoogleSheetsConfig.VENUES_SHEET
    }

    fun saveVenuesSheet(sheet: String) {
        prefs.edit().putString(KEY_VENUES_SHEET, sheet).apply()
    }
    
    // Sync Configuration
    fun isSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_SYNC_ENABLED, true)
    }
    
    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }
    
    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC, true)
    }
    
    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }
    
    fun getSyncInterval(): Int {
        return prefs.getInt(KEY_SYNC_INTERVAL, 5) // 5 minutes default
    }
    
    fun saveSyncInterval(intervalMinutes: Int) {
        prefs.edit().putInt(KEY_SYNC_INTERVAL, intervalMinutes).apply()
    }
    
    fun getAutoSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SYNC, true)
    }
    
    fun saveAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }
    
    fun getDebugMode(): Boolean {
        return prefs.getBoolean(KEY_DEBUG_MODE, false)
    }
    
    fun saveDebugMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }
    
    fun isAnimatedBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANIMATED_BACKGROUND, false) // Disabled by default for better performance
    }
    
    fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATED_BACKGROUND, enabled).apply()
    }
    
    // UI Page Animations Configuration
    fun isPageAnimationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_PAGE_ANIMATIONS, true) // Enabled by default
    }
    
    fun setPageAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAGE_ANIMATIONS, enabled).apply()
    }
    
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    fun saveLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
    }
    
    // Language Configuration
    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en" // Default to English
    }
    
    fun saveLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }
    
    // Theme Configuration
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, "default") ?: "default" // Default to system theme
    }
    
    fun saveThemeMode(themeMode: String) {
        prefs.edit().putString(KEY_THEME_MODE, themeMode).apply()
    }
    
    // Color Theme Configuration
    fun getColorTheme(): String {
        return prefs.getString(KEY_COLOR_THEME, "system") ?: "system" // Default to system colors
    }
    
    fun saveColorTheme(colorTheme: String) {
        prefs.edit().putString(KEY_COLOR_THEME, colorTheme).apply()
    }
    
    // Resolution Scale Configuration
    fun getResolutionScale(): Float {
        return prefs.getFloat(KEY_RESOLUTION_SCALE, 1.0f) // Default to 100% (normal size)
    }
    
    fun saveResolutionScale(scale: Float) {
        prefs.edit().putFloat(KEY_RESOLUTION_SCALE, scale).apply()
    }
    
    // Date Format Configuration
    fun getDateFormat(): String {
        return prefs.getString(KEY_DATE_FORMAT, "MM/dd/yyyy") ?: "MM/dd/yyyy" // Default to MM/dd/yyyy
    }

    fun saveDateFormat(dateFormat: String) {
        prefs.edit().putString(KEY_DATE_FORMAT, dateFormat).apply()
    }

    // Time Format Configuration
    fun getTimeFormat(): String {
        return prefs.getString(KEY_TIME_FORMAT, "HH:mm") ?: "HH:mm" // Default to HH:mm
    }

    fun saveTimeFormat(timeFormat: String) {
        prefs.edit().putString(KEY_TIME_FORMAT, timeFormat).apply()
    }
    
    // Date Change Offset Configuration
    fun getDateChangeOffsetHours(): Int {
        return prefs.getInt(KEY_DATE_CHANGE_OFFSET_HOURS, 0) // Default to 0 (midnight)
    }
    
    fun saveDateChangeOffsetHours(hours: Int) {
        prefs.edit().putInt(KEY_DATE_CHANGE_OFFSET_HOURS, hours).apply()
    }
    
    // Seasonal Fun Configuration
    fun isSeasonalFunEnabled(): Boolean {
        return prefs.getBoolean(KEY_SEASONAL_FUN, true) // Enabled by default
    }
    
    fun setSeasonalFunEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEASONAL_FUN, enabled).apply()
    }
    
    // Graph Time Period Configuration
    fun getSelectedGraphTimePeriod(): String {
        return prefs.getString(KEY_SELECTED_GRAPH_TIME_PERIOD, "ONE_MONTH") ?: "ONE_MONTH" // Default to 1 Month
    }
    
    fun saveSelectedGraphTimePeriod(timePeriod: String) {
        prefs.edit().putString(KEY_SELECTED_GRAPH_TIME_PERIOD, timePeriod).apply()
    }
    
    // App Icon Configuration
    fun getAppIconStyle(): String {
        return prefs.getString(KEY_APP_ICON_STYLE, "light") ?: "light" // Default to light icon
    }
    
    fun saveAppIconStyle(iconStyle: String) {
        prefs.edit().putString(KEY_APP_ICON_STYLE, iconStyle).apply()
    }
    
    fun isAppIconAutoAdapt(): Boolean {
        return prefs.getBoolean(KEY_APP_ICON_AUTO_ADAPT, false) // Disabled by default
    }
    
    fun setAppIconAutoAdapt(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_ICON_AUTO_ADAPT, enabled).apply()
    }
    
    // People Counter Visibility Configuration
    fun isPeopleCounterVisible(): Boolean {
        return prefs.getBoolean(KEY_PEOPLE_COUNTER_VISIBLE, false) // Hidden by default
    }
    
    fun setPeopleCounterVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_PEOPLE_COUNTER_VISIBLE, visible).apply()
    }
    
    // Statistics Visibility Configuration
    fun isStatisticsVisible(): Boolean {
        return prefs.getBoolean(KEY_STATISTICS_VISIBLE, true) // Shown by default
    }
    
    fun setStatisticsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_STATISTICS_VISIBLE, visible).apply()
    }

    // Page Scroll Behavior Configuration
    // true  -> Only the list scrolls, header stays fixed (current default)
    // false -> Header scrolls together with the list
    fun isHeaderPinned(): Boolean {
        return prefs.getBoolean(KEY_PAGE_SCROLL_BEHAVIOR, true)
    }
    
    fun setHeaderPinned(pinned: Boolean) {
        prefs.edit().putBoolean(KEY_PAGE_SCROLL_BEHAVIOR, pinned).apply()
    }
    
    // Clear all settings
    fun clearAllSettings() {
        prefs.edit().clear().apply()
    }
    
    // Check if settings are configured
    fun isConfigured(): Boolean {
        val spreadsheetId = getSpreadsheetId()
        return spreadsheetId.isNotEmpty() && spreadsheetId != GoogleSheetsConfig.SPREADSHEET_ID
    }
}
