package com.eventmanager.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.eventmanager.app.BuildConfig
import com.eventmanager.app.data.utils.AppIconManager
import com.eventmanager.app.data.utils.NanoIdGenerator

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
        private const val KEY_SALES_ITEMS_SHEET = "sales_items_sheet"
        private const val KEY_TEMP_GUEST_LIST_SHEET = "temp_guest_list_sheet"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val KEY_ANIMATED_BACKGROUND = "animated_background"
        private const val KEY_PAGE_ANIMATIONS = "page_animations"
        /** Legacy: was also updated on upload-only paths; still read for migration. */
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        /** Last successful read of Google Sheets data into the app (any tab / scope). */
        private const val KEY_LAST_SHEETS_PULL_AT = "last_sheets_pull_at_ms"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_CUSTOM_THEME_COLOR_PREFIX = "custom_theme_color"
        private const val KEY_SKIP_NEXT_STARTUP_SYNC = "skip_next_startup_sync"
        private const val KEY_RESOLUTION_SCALE = "resolution_scale"
        private const val KEY_DATE_FORMAT = "date_format"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_DATE_CHANGE_OFFSET_HOURS = "date_change_offset_hours"
        private const val KEY_SEASONAL_FUN = "seasonal_fun"
        private const val KEY_SELECTED_GRAPH_TIME_PERIOD = "selected_graph_time_period"
        private const val KEY_APP_ICON_STYLE = "app_icon_style"
        private const val KEY_APP_ICON_AUTO_ADAPT = "app_icon_auto_adapt"
        private const val KEY_PEOPLE_COUNTER_VISIBLE = "people_counter_visible"
        private const val KEY_APP_DEVICE_NANOID = "app_device_nano_id"
        private const val KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID = "people_counter_selected_venue_id"
        /** @deprecated Migrated into [KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS]. */
        private const val KEY_PEOPLE_COUNTER_PRIORITY = "people_counter_priority"
        /** Venue ids (as strings) where this device wants people-counter upload priority. */
        private const val KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS = "people_counter_priority_venue_ids"
        /** Legacy key — migrated once to [KEY_PEOPLE_COUNTER_PRIORITY] (inverted meaning). */
        private const val KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY = "people_counter_user_read_only"
        private const val KEY_STATISTICS_VISIBLE = "statistics_visible"
        private const val KEY_PAGE_SCROLL_BEHAVIOR = "page_scroll_behavior"
        private const val KEY_UPDATE_MANIFEST_URL = "update_manifest_url"
        private const val KEY_UPDATE_STORE_URL = "update_store_url"
        
        // Settings Category Expansion State Keys
        private const val KEY_CATEGORY_SYNC_EXPANDED = "category_sync_expanded"
        private const val KEY_CATEGORY_APPEARANCE_EXPANDED = "category_appearance_expanded"
        private const val KEY_CATEGORY_LOCALIZATION_EXPANDED = "category_localization_expanded"
        private const val KEY_CATEGORY_ANIMATION_EXPANDED = "category_animation_expanded"
        private const val KEY_CATEGORY_DEVELOPER_EXPANDED = "category_developer_expanded"
        private const val KEY_CATEGORY_MAINTENANCE_EXPANDED = "category_maintenance_expanded"
        private const val KEY_SETUP_WIZARD_COMPLETED = "setup_wizard_completed"
        private const val KEY_BIOMETRIC_ADMIN_LOGIN = "biometric_admin_login"

        // External Bluetooth NFC reader (ACR1255U-J1) pairing
        private const val KEY_EXTERNAL_BLE_READER_MAC = "external_ble_reader_mac"
        private const val KEY_EXTERNAL_BLE_READER_NAME = "external_ble_reader_name"
        private const val KEY_CATEGORY_EXTERNAL_READER_EXPANDED = "category_external_reader_expanded"
        
        // Announcements Settings Keys
        private const val KEY_ANNOUNCEMENTS_RECEPTION_ENABLED = "announcements_reception_enabled"
        private const val KEY_ANNOUNCEMENTS_TRACKED_VENUE_IDS = "announcements_tracked_venue_ids"
        private const val KEY_ANNOUNCEMENTS_VALIDITY_MINUTES = "announcements_validity_minutes"
        private const val KEY_ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED = "announcements_non_admin_send_enabled"
        private const val KEY_CATEGORY_ANNOUNCEMENTS_EXPANDED = "category_announcements_expanded"
        private const val KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS = "announcements_last_seen_timestamps"

        // Email Settings Keys
        private const val KEY_EMAIL_SUBJECT = "email_qr_subject"
        private const val KEY_EMAIL_CONTENT_BEFORE = "email_qr_content_before"
        private const val KEY_EMAIL_INCLUDE_QR = "email_include_qr"
        private const val KEY_EMAIL_CONTENT_AFTER = "email_qr_content_after"
        private const val KEY_EMAIL_SIGNATURE = "email_signature"
        private const val KEY_EMAIL_INCLUDE_LOGO = "email_include_logo"
        private const val KEY_EMAIL_INCLUDE_DIGITAL_WALLET_PASS = "email_include_digital_wallet_pass"
        private const val KEY_EMAIL_ASSOCIATION_NAME = "email_association_name"
        private const val KEY_EMAIL_LOGO_URI = "email_logo_uri"
        private const val KEY_EMAIL_GMAIL_ACCOUNT = "email_gmail_account"
        private const val KEY_EMAIL_GMAIL_AUTH_TOKEN = "email_gmail_auth_token"
        private const val KEY_CATEGORY_EMAIL_EXPANDED = "category_email_expanded"
        
        // Guest Email Settings Keys
        private const val KEY_GUEST_EMAIL_SUBJECT = "guest_email_subject"
        private const val KEY_GUEST_EMAIL_CONTENT_BEFORE = "guest_email_content_before"
        private const val KEY_GUEST_EMAIL_INCLUDE_QR = "guest_email_include_qr"
        private const val KEY_GUEST_EMAIL_CONTENT_AFTER = "guest_email_content_after"
        private const val KEY_GUEST_EMAIL_SIGNATURE = "guest_email_signature"
        
        // Page Scroll Behavior Configuration Constants
        const val HEADER_PINNED = "header_pinned"
        const val FULL_SCROLL = "full_scroll"
        const val STICKY_FILTERS = "sticky_filters"
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
        val stored = prefs.getString(KEY_JOB_TYPES_SHEET, GoogleSheetsConfig.JOB_TYPES_SHEET)
            ?: GoogleSheetsConfig.JOB_TYPES_SHEET
        return migrateLegacyJobTypesSheetNameToCanonical(stored)
    }

    /**
     * Older builds / docs used "JobTypes" or "Job Types" while the spreadsheet (and current default)
     * uses "Shift Types". Keeping the legacy string in prefs made structure validation create a new
     * empty "Job Types" tab while the app read/wrote "Shift Types" from [GoogleSheetsConfig].
     */
    private fun migrateLegacyJobTypesSheetNameToCanonical(stored: String): String {
        val trimmed = stored.trim()
        val canonical = GoogleSheetsConfig.JOB_TYPES_SHEET.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().putString(KEY_JOB_TYPES_SHEET, canonical).apply()
            return canonical
        }
        val isDeprecatedEnglishTabName =
            trimmed.equals("JobTypes", ignoreCase = true) ||
                trimmed.equals("Job Types", ignoreCase = true)
        if (isDeprecatedEnglishTabName && !trimmed.equals(canonical, ignoreCase = true)) {
            prefs.edit().putString(KEY_JOB_TYPES_SHEET, canonical).apply()
            return canonical
        }
        return trimmed
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

    fun getSalesItemsSheet(): String {
        return prefs.getString(KEY_SALES_ITEMS_SHEET, GoogleSheetsConfig.SALES_ITEMS_SHEET)
            ?: GoogleSheetsConfig.SALES_ITEMS_SHEET
    }

    fun saveSalesItemsSheet(sheet: String) {
        prefs.edit().putString(KEY_SALES_ITEMS_SHEET, sheet).apply()
    }

    fun getTempGuestListSheet(): String {
        return prefs.getString(KEY_TEMP_GUEST_LIST_SHEET, GoogleSheetsConfig.TEMP_GUEST_LIST_SHEET)
            ?: GoogleSheetsConfig.TEMP_GUEST_LIST_SHEET
    }

    fun saveTempGuestListSheet(sheet: String) {
        prefs.edit().putString(KEY_TEMP_GUEST_LIST_SHEET, sheet).apply()
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
    
    fun getDebugMode(): Boolean {
        return prefs.getBoolean(KEY_DEBUG_MODE, false)
    }
    
    fun saveDebugMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
    }
    
    fun isAnimatedBackgroundEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANIMATED_BACKGROUND, true) // On by default; users can turn off in settings
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
    
    /**
     * Millis of the last time the app successfully **pulled** data from Google Sheets
     * (full sync, differential sync, tab-targeted pull, temp guest sheet, etc.).
     * Upload-only / backup-only operations must not advance this value.
     */
    fun getLastSheetsPullAt(): Long {
        val pull = prefs.getLong(KEY_LAST_SHEETS_PULL_AT, 0L)
        if (pull > 0L) return pull
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun recordSheetsPullAt(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SHEETS_PULL_AT, timestamp).apply()
    }

    /** @see getLastSheetsPullAt */
    fun getLastSyncTime(): Long = getLastSheetsPullAt()

    /** Call [recordSheetsPullAt] after a successful Sheets download; kept for older call sites. */
    fun saveLastSyncTime(timestamp: Long) = recordSheetsPullAt(timestamp)
    
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

    fun getCustomThemeColor(isDark: Boolean, role: String, defaultArgb: Int): Int {
        val mode = if (isDark) "dark" else "light"
        val key = "${KEY_CUSTOM_THEME_COLOR_PREFIX}_${mode}_${role}"
        return prefs.getInt(key, defaultArgb)
    }

    fun saveCustomThemeColor(isDark: Boolean, role: String, argb: Int) {
        val mode = if (isDark) "dark" else "light"
        val key = "${KEY_CUSTOM_THEME_COLOR_PREFIX}_${mode}_${role}"
        prefs.edit().putInt(key, argb).apply()
    }

    /**
     * Mark that the next app recreation is visual-only (e.g. theme switch),
     * so startup sync should be skipped once to avoid unnecessary API calls.
     */
    fun markSkipNextStartupSync() {
        prefs.edit().putBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, true).apply()
    }

    /**
     * Consume and clear the one-shot "skip startup sync" marker.
     */
    fun consumeSkipNextStartupSync(): Boolean {
        val shouldSkip = prefs.getBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, false)
        if (shouldSkip) {
            prefs.edit().putBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, false).apply()
        }
        return shouldSkip
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
        val raw = prefs.getString(KEY_APP_ICON_STYLE, "white") ?: "white"
        val migrated = migrateLegacyAppIconStyle(raw)
        if (migrated != raw) {
            prefs.edit().putString(KEY_APP_ICON_STYLE, migrated).apply()
        }
        return migrated
    }

    private fun migrateLegacyAppIconStyle(raw: String): String {
        val mapped = when (raw) {
            "light" -> "white"
            "dark" -> "black"
            "deep_blue" -> "dark_blue"
            "blue_ocean" -> "dark_turquoise"
            "braun" -> "brown"
            "purple" -> "dark_violet"
            "violet" -> "light_violet"
            else -> raw
        }
        return if (mapped in AppIconManager.ALL_ICON_STYLES) mapped else "white"
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

    /** Stable per-installation ID used for people-counter writer arbitration on Google Sheets. */
    fun getOrCreatePersistentDeviceId(): String {
        val existing = prefs.getString(KEY_APP_DEVICE_NANOID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val created = NanoIdGenerator.generateGuestId()
        prefs.edit().putString(KEY_APP_DEVICE_NANOID, created).apply()
        return created
    }

    fun getPeopleCounterSelectedVenueId(): Long {
        return prefs.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
    }

    fun setPeopleCounterSelectedVenueId(id: Long) {
        prefs.edit().putLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, id).apply()
    }

    private fun ensurePeopleCounterPriorityVenuesMigrated() {
        if (prefs.contains(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS)) {
            prefs.edit()
                .remove(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY)
                .remove(KEY_PEOPLE_COUNTER_PRIORITY)
                .apply()
            return
        }
        var venueIds: Set<String> = emptySet()
        when {
            prefs.contains(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY) -> {
                val legacyReadOnly = prefs.getBoolean(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY, true)
                if (!legacyReadOnly) {
                    val sel = prefs.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
                    if (sel > 0L) venueIds = setOf(sel.toString())
                }
            }
            prefs.contains(KEY_PEOPLE_COUNTER_PRIORITY) -> {
                if (prefs.getBoolean(KEY_PEOPLE_COUNTER_PRIORITY, false)) {
                    val sel = prefs.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
                    if (sel > 0L) venueIds = setOf(sel.toString())
                }
            }
        }
        prefs.edit()
            .remove(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY)
            .remove(KEY_PEOPLE_COUNTER_PRIORITY)
            .putStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, HashSet(venueIds))
            .apply()
    }

    /**
     * When true, this device requests people-counter upload priority for [venueId] only.
     * Each venue is independent; default is off until the user enables priority for that venue.
     */
    fun isPeopleCounterPriority(venueId: Long): Boolean {
        if (venueId <= 0L) return false
        ensurePeopleCounterPriorityVenuesMigrated()
        val set = prefs.getStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, emptySet()) ?: emptySet()
        return set.contains(venueId.toString())
    }

    fun setPeopleCounterPriority(venueId: Long, enabled: Boolean) {
        if (venueId <= 0L) return
        ensurePeopleCounterPriorityVenuesMigrated()
        val raw = prefs.getStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, emptySet()) ?: emptySet()
        val next = HashSet(raw)
        if (enabled) next.add(venueId.toString()) else next.remove(venueId.toString())
        prefs.edit().putStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, next).apply()
    }
    
    // Statistics Visibility Configuration
    fun isStatisticsVisible(): Boolean {
        return prefs.getBoolean(KEY_STATISTICS_VISIBLE, true) // Shown by default
    }
    
    fun setStatisticsVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_STATISTICS_VISIBLE, visible).apply()
    }

    // Announcements Configuration

    fun isAnnouncementsReceptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANNOUNCEMENTS_RECEPTION_ENABLED, true)
    }

    fun setAnnouncementsReceptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCEMENTS_RECEPTION_ENABLED, enabled).apply()
    }

    fun getAnnouncementsTrackedVenueIds(): Set<String> {
        return prefs.getStringSet(KEY_ANNOUNCEMENTS_TRACKED_VENUE_IDS, emptySet()) ?: emptySet()
    }

    fun setAnnouncementsTrackedVenueIds(venueIds: Set<String>) {
        prefs.edit().putStringSet(KEY_ANNOUNCEMENTS_TRACKED_VENUE_IDS, venueIds).apply()
    }

    fun getAnnouncementsValidityMinutes(): Int {
        return prefs.getInt(KEY_ANNOUNCEMENTS_VALIDITY_MINUTES, 60)
    }

    fun setAnnouncementsValidityMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_ANNOUNCEMENTS_VALIDITY_MINUTES, minutes).apply()
    }

    fun isAnnouncementsNonAdminSendEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED, false)
    }

    fun setAnnouncementsNonAdminSendEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED, enabled).apply()
    }

    fun isCategoryAnnouncementsExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_ANNOUNCEMENTS_EXPANDED, false)
    }

    fun setCategoryAnnouncementsExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_ANNOUNCEMENTS_EXPANDED, expanded).apply()
    }

    fun getAnnouncementsLastSeenTimestamps(): Map<String, Long> {
        val set = prefs.getStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, emptySet()) ?: emptySet()
        return set.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0] to (parts[1].toLongOrNull() ?: 0L)
            } else null
        }.toMap()
    }

    fun setAnnouncementLastSeenTimestamp(venueKey: String, timestamp: Long) {
        val set = prefs.getStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, emptySet()) ?: emptySet()
        val mutable = HashSet(set)
        mutable.removeAll { it.startsWith("$venueKey:") }
        mutable.add("$venueKey:$timestamp")
        prefs.edit().putStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, mutable).apply()
    }

    // Page Scroll Behavior Configuration
    // HEADER_PINNED   -> Only the list scrolls, header stays fixed
    // FULL_SCROLL     -> Header scrolls together with the list
    // STICKY_FILTERS  -> Header scrolls but filters become sticky at the top (default)
    fun getScrollBehavior(): String {
        // Check if new string setting exists
        val storedString = prefs.getString("${KEY_PAGE_SCROLL_BEHAVIOR}_mode", null)
        if (storedString != null) {
            return storedString
        }
        
        // Migrate old boolean setting if it was explicitly set
        // Note: prefs.contains checks if the key was ever set by the user
        if (prefs.contains(KEY_PAGE_SCROLL_BEHAVIOR)) {
            val oldValue = prefs.getBoolean(KEY_PAGE_SCROLL_BEHAVIOR, true)
            return if (oldValue) HEADER_PINNED else FULL_SCROLL
        }
        
        // Default to STICKY_FILTERS for new users
        return STICKY_FILTERS
    }
    
    fun setScrollBehavior(behavior: String) {
        prefs.edit().putString("${KEY_PAGE_SCROLL_BEHAVIOR}_mode", behavior).apply()
    }
    
    // Update Manifest URL Configuration
    fun getUpdateManifestUrl(): String {
        return prefs.getString(KEY_UPDATE_MANIFEST_URL, BuildConfig.UPDATE_MANIFEST_URL)
            ?: BuildConfig.UPDATE_MANIFEST_URL
    }

    fun saveUpdateManifestUrl(url: String) {
        prefs.edit().putString(KEY_UPDATE_MANIFEST_URL, url).apply()
    }

    // Update Store URL Configuration
    fun getUpdateStoreUrl(): String {
        return prefs.getString(KEY_UPDATE_STORE_URL, BuildConfig.UPDATE_FALLBACK_STORE_URL)
            ?: BuildConfig.UPDATE_FALLBACK_STORE_URL
    }

    fun saveUpdateStoreUrl(url: String) {
        prefs.edit().putString(KEY_UPDATE_STORE_URL, url).apply()
    }
    
    /**
     * Returns animation intensity multiplier.
     * Always returns 1.0f - users can disable specific animations via their individual toggles.
     */
    fun getAnimationIntensityMultiplier(): Float {
        return 1.0f
    }
    
    // Email Settings Configuration
    // Note: Default values use resource IDs that should be resolved at runtime with context
    fun getEmailSubject(): String {
        return prefs.getString(KEY_EMAIL_SUBJECT, "") ?: ""
    }
    
    fun saveEmailSubject(subject: String) {
        prefs.edit().putString(KEY_EMAIL_SUBJECT, subject).apply()
    }
    
    fun getEmailContentBefore(): String {
        return prefs.getString(KEY_EMAIL_CONTENT_BEFORE, "") ?: ""
    }
    
    fun saveEmailContentBefore(content: String) {
        prefs.edit().putString(KEY_EMAIL_CONTENT_BEFORE, content).apply()
    }
    
    fun isEmailIncludeQrEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_INCLUDE_QR, true) // Include QR by default
    }
    
    fun setEmailIncludeQrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_INCLUDE_QR, enabled).apply()
    }
    
    fun getEmailContentAfter(): String {
        return prefs.getString(KEY_EMAIL_CONTENT_AFTER, "") ?: ""
    }
    
    fun saveEmailContentAfter(content: String) {
        prefs.edit().putString(KEY_EMAIL_CONTENT_AFTER, content).apply()
    }
    
    fun getEmailSignature(): String {
        return prefs.getString(KEY_EMAIL_SIGNATURE, "") ?: ""
    }
    
    fun saveEmailSignature(signature: String) {
        prefs.edit().putString(KEY_EMAIL_SIGNATURE, signature).apply()
    }
    
    fun isEmailIncludeLogoEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_INCLUDE_LOGO, false) // Logo disabled by default
    }
    
    fun setEmailIncludeLogoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_INCLUDE_LOGO, enabled).apply()
    }

    fun isEmailIncludeDigitalWalletPassEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_INCLUDE_DIGITAL_WALLET_PASS, true)
    }

    fun setEmailIncludeDigitalWalletPassEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_INCLUDE_DIGITAL_WALLET_PASS, enabled).apply()
    }

    fun getEmailAssociationName(): String {
        return prefs.getString(KEY_EMAIL_ASSOCIATION_NAME, "Collectif Nocturne") ?: "Collectif Nocturne"
    }

    fun saveEmailAssociationName(name: String) {
        prefs.edit().putString(KEY_EMAIL_ASSOCIATION_NAME, name).apply()
    }
    
    fun getEmailLogoUri(): String {
        return prefs.getString(KEY_EMAIL_LOGO_URI, "") ?: ""
    }
    
    fun saveEmailLogoUri(uri: String) {
        prefs.edit().putString(KEY_EMAIL_LOGO_URI, uri).apply()
    }
    
    fun getGmailAccount(): String {
        return prefs.getString(KEY_EMAIL_GMAIL_ACCOUNT, "") ?: ""
    }
    
    fun saveGmailAccount(account: String) {
        prefs.edit().putString(KEY_EMAIL_GMAIL_ACCOUNT, account).apply()
    }
    
    fun getGmailAuthToken(): String {
        return prefs.getString(KEY_EMAIL_GMAIL_AUTH_TOKEN, "") ?: ""
    }
    
    fun saveGmailAuthToken(token: String) {
        prefs.edit().putString(KEY_EMAIL_GMAIL_AUTH_TOKEN, token).apply()
    }
    
    fun clearGmailAuth() {
        prefs.edit()
            .remove(KEY_EMAIL_GMAIL_ACCOUNT)
            .remove(KEY_EMAIL_GMAIL_AUTH_TOKEN)
            .apply()
    }
    
    fun isCategoryEmailExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_EMAIL_EXPANDED, false)
    }
    
    fun setCategoryEmailExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_EMAIL_EXPANDED, expanded).apply()
    }
    
    // Guest Email Settings Configuration
    fun getGuestEmailSubject(): String {
        return prefs.getString(KEY_GUEST_EMAIL_SUBJECT, "") ?: ""
    }
    
    fun saveGuestEmailSubject(subject: String) {
        prefs.edit().putString(KEY_GUEST_EMAIL_SUBJECT, subject).apply()
    }
    
    fun getGuestEmailContentBefore(): String {
        return prefs.getString(KEY_GUEST_EMAIL_CONTENT_BEFORE, "") ?: ""
    }
    
    fun saveGuestEmailContentBefore(content: String) {
        prefs.edit().putString(KEY_GUEST_EMAIL_CONTENT_BEFORE, content).apply()
    }
    
    fun isGuestEmailIncludeQrEnabled(): Boolean {
        return prefs.getBoolean(KEY_GUEST_EMAIL_INCLUDE_QR, true) // Include QR by default
    }
    
    fun setGuestEmailIncludeQrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GUEST_EMAIL_INCLUDE_QR, enabled).apply()
    }
    
    fun getGuestEmailContentAfter(): String {
        return prefs.getString(KEY_GUEST_EMAIL_CONTENT_AFTER, "") ?: ""
    }
    
    fun saveGuestEmailContentAfter(content: String) {
        prefs.edit().putString(KEY_GUEST_EMAIL_CONTENT_AFTER, content).apply()
    }
    
    fun getGuestEmailSignature(): String {
        return prefs.getString(KEY_GUEST_EMAIL_SIGNATURE, "") ?: ""
    }
    
    fun saveGuestEmailSignature(signature: String) {
        prefs.edit().putString(KEY_GUEST_EMAIL_SIGNATURE, signature).apply()
    }
    
    // Biometric Admin Login Configuration
    fun isBiometricAdminLoginEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ADMIN_LOGIN, false)
    }
    
    fun setBiometricAdminLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ADMIN_LOGIN, enabled).apply()
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
    
    // Settings Category Expansion State
    fun isCategorySyncExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_SYNC_EXPANDED, false)
    }
    
    fun setCategorySyncExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_SYNC_EXPANDED, expanded).apply()
    }
    
    fun isCategoryAppearanceExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_APPEARANCE_EXPANDED, false)
    }
    
    fun setCategoryAppearanceExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_APPEARANCE_EXPANDED, expanded).apply()
    }
    
    fun isCategoryLocalizationExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_LOCALIZATION_EXPANDED, false)
    }
    
    fun setCategoryLocalizationExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_LOCALIZATION_EXPANDED, expanded).apply()
    }
    
    fun isCategoryAnimationExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_ANIMATION_EXPANDED, false)
    }
    
    fun setCategoryAnimationExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_ANIMATION_EXPANDED, expanded).apply()
    }
    
    fun isCategoryDeveloperExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_DEVELOPER_EXPANDED, false)
    }
    
    fun setCategoryDeveloperExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_DEVELOPER_EXPANDED, expanded).apply()
    }
    
    fun isCategoryMaintenanceExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_MAINTENANCE_EXPANDED, false)
    }
    
    fun setCategoryMaintenanceExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_MAINTENANCE_EXPANDED, expanded).apply()
    }

    fun isCategoryExternalReaderExpanded(): Boolean {
        return prefs.getBoolean(KEY_CATEGORY_EXTERNAL_READER_EXPANDED, false)
    }

    fun setCategoryExternalReaderExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_CATEGORY_EXTERNAL_READER_EXPANDED, expanded).apply()
    }

    /**
     * MAC address of the BLE NFC reader (ACR1255U-J1) the user selected from the in-app scanner.
     * Empty string means "no reader chosen yet" — fall back to bonded-device lookup.
     */
    fun getExternalBleReaderMac(): String {
        return prefs.getString(KEY_EXTERNAL_BLE_READER_MAC, "") ?: ""
    }

    fun saveExternalBleReaderMac(mac: String) {
        prefs.edit().putString(KEY_EXTERNAL_BLE_READER_MAC, mac).apply()
    }

    fun getExternalBleReaderName(): String {
        return prefs.getString(KEY_EXTERNAL_BLE_READER_NAME, "") ?: ""
    }

    fun saveExternalBleReaderName(name: String) {
        prefs.edit().putString(KEY_EXTERNAL_BLE_READER_NAME, name).apply()
    }

    fun clearExternalBleReader() {
        prefs.edit()
            .remove(KEY_EXTERNAL_BLE_READER_MAC)
            .remove(KEY_EXTERNAL_BLE_READER_NAME)
            .apply()
    }
    
    fun isSetupWizardCompleted(): Boolean {
        return prefs.getBoolean(KEY_SETUP_WIZARD_COMPLETED, false)
    }
    
    fun setSetupWizardCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_WIZARD_COMPLETED, completed).apply()
    }
    
    fun shouldShowSetupWizard(): Boolean {
        return !isSetupWizardCompleted() && !isConfigured()
    }
}
