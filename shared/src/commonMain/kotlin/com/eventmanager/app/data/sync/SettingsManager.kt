package com.eventmanager.app.data.sync

import com.eventmanager.app.data.utils.AppIconStyles
import com.eventmanager.app.data.utils.NanoIdGenerator
import com.eventmanager.app.platform.AppBuildInfo
import com.eventmanager.app.platform.AppStorage
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.ui.components.BackgroundAnimationStyle

/**
 * Manages app settings persistence using cross-platform key-value storage.
 */
class SettingsManager(private val storage: AppStorage) {

    constructor(platformContext: PlatformContext) : this(createAppStorage(platformContext))
    
    companion object {
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_GUEST_LIST_SHEET = "guest_list_sheet"
        private const val KEY_VOLUNTEER_SHEET = "volunteer_sheet"
        private const val KEY_JOBS_SHEET = "jobs_sheet"
        private const val KEY_JOB_TYPES_SHEET = "job_types_sheet"
        private const val KEY_VOLUNTEER_GUEST_LIST_SHEET = "volunteer_guest_list_sheet"
        private const val KEY_VENUES_SHEET = "venues_sheet"
        private const val KEY_SALES_ITEMS_SHEET = "sales_items_sheet"
        private const val KEY_TRANSFERS_SHEET = "transfers_sheet"
        private const val KEY_TEMP_GUEST_LIST_SHEET = "temp_guest_list_sheet"
        private const val KEY_CURRENCY_CODE = "currency_code"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_DEBUG_MODE = "debug_mode"
        private const val KEY_ANIMATED_BACKGROUND = "animated_background"
        private const val KEY_BACKGROUND_ANIMATION_STYLE = "background_animation_style"
        private const val KEY_BACKGROUND_ANIMATION_OPACITY = "background_animation_opacity"
        private const val KEY_BILLETTERIE_BACKGROUND_ANIMATION_STYLE = "billeterie_background_animation_style"
        private const val KEY_BILLETTERIE_BACKGROUND_ANIMATION_OPACITY = "billeterie_background_animation_opacity"
        private const val KEY_POS_BACKGROUND_ANIMATION_STYLE = "pos_background_animation_style"
        private const val KEY_POS_BACKGROUND_ANIMATION_OPACITY = "pos_background_animation_opacity"
        private const val KEY_POS_SELECTED_CATEGORY = "pos_selected_category"
        private const val KEY_POS_SELECTED_VENUE = "pos_selected_venue"
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
        private const val KEY_DESKTOP_ADMIN_NAV_LAYOUT = "desktop_admin_nav_layout"
        private const val KEY_DESKTOP_ADMIN_NAV_RAIL_EXPANDED = "desktop_admin_nav_rail_expanded"
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
        private const val KEY_BIOMETRIC_ADMIN_PROFILE_LINK = "biometric_admin_profile_link"

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
        private const val KEY_WALLET_PASS_CERT_PASSWORD = "wallet_pass_cert_password"
        private const val KEY_WALLET_PASS_TYPE_IDENTIFIER = "wallet_pass_type_identifier"
        private const val KEY_WALLET_PASS_TEAM_IDENTIFIER = "wallet_pass_team_identifier"
        private const val KEY_EMAIL_ASSOCIATION_NAME = "email_association_name"
        private const val KEY_EMAIL_LOGO_URI = "email_logo_uri"
        private const val KEY_EMAIL_GMAIL_ACCOUNT = "email_gmail_account"
        private const val KEY_EMAIL_GMAIL_AUTH_TOKEN = "email_gmail_auth_token"
        private const val KEY_EMAIL_GMAIL_USE_SERVICE_ACCOUNT = "email_gmail_use_service_account"
        private const val KEY_EMAIL_GMAIL_SERVICE_ACCOUNT_SENDER = "email_gmail_service_account_sender"
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
        return storage.getString(KEY_SPREADSHEET_ID, GoogleSheetsConfig.SPREADSHEET_ID) ?: GoogleSheetsConfig.SPREADSHEET_ID
    }
    
    fun saveSpreadsheetId(id: String) {
        storage.putString(KEY_SPREADSHEET_ID, id)
    }
    
    fun getGuestListSheet(): String {
        return storage.getString(KEY_GUEST_LIST_SHEET, GoogleSheetsConfig.GUEST_LIST_SHEET) ?: GoogleSheetsConfig.GUEST_LIST_SHEET
    }
    
    fun saveGuestListSheet(sheet: String) {
        storage.putString(KEY_GUEST_LIST_SHEET, sheet)
    }
    
    fun getVolunteerSheet(): String {
        return storage.getString(KEY_VOLUNTEER_SHEET, GoogleSheetsConfig.VOLUNTEER_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_SHEET
    }
    
    fun saveVolunteerSheet(sheet: String) {
        storage.putString(KEY_VOLUNTEER_SHEET, sheet)
    }
    
    fun getJobsSheet(): String {
        return storage.getString(KEY_JOBS_SHEET, GoogleSheetsConfig.JOBS_SHEET) ?: GoogleSheetsConfig.JOBS_SHEET
    }
    
    fun saveJobsSheet(sheet: String) {
        storage.putString(KEY_JOBS_SHEET, sheet)
    }
    
    fun getVolunteersSheet(): String {
        return storage.getString(KEY_VOLUNTEER_SHEET, GoogleSheetsConfig.VOLUNTEER_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_SHEET
    }
    
    fun getJobTypesSheet(): String {
        val stored = storage.getString(KEY_JOB_TYPES_SHEET, GoogleSheetsConfig.JOB_TYPES_SHEET)
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
            storage.putString(KEY_JOB_TYPES_SHEET, canonical)
            return canonical
        }
        val isDeprecatedEnglishTabName =
            trimmed.equals("JobTypes", ignoreCase = true) ||
                trimmed.equals("Job Types", ignoreCase = true)
        if (isDeprecatedEnglishTabName && !trimmed.equals(canonical, ignoreCase = true)) {
            storage.putString(KEY_JOB_TYPES_SHEET, canonical)
            return canonical
        }
        return trimmed
    }
    
    fun saveJobTypesSheet(sheet: String) {
        storage.putString(KEY_JOB_TYPES_SHEET, sheet)
    }

    fun getVolunteerGuestListSheet(): String {
        return storage.getString(KEY_VOLUNTEER_GUEST_LIST_SHEET, GoogleSheetsConfig.VOLUNTEER_GUEST_LIST_SHEET) ?: GoogleSheetsConfig.VOLUNTEER_GUEST_LIST_SHEET
    }

    fun saveVolunteerGuestListSheet(sheet: String) {
        storage.putString(KEY_VOLUNTEER_GUEST_LIST_SHEET, sheet)
    }

    fun getVenuesSheet(): String {
        return storage.getString(KEY_VENUES_SHEET, GoogleSheetsConfig.VENUES_SHEET) ?: GoogleSheetsConfig.VENUES_SHEET
    }

    fun saveVenuesSheet(sheet: String) {
        storage.putString(KEY_VENUES_SHEET, sheet)
    }

    fun getSalesItemsSheet(): String {
        return storage.getString(KEY_SALES_ITEMS_SHEET, GoogleSheetsConfig.SALES_ITEMS_SHEET)
            ?: GoogleSheetsConfig.SALES_ITEMS_SHEET
    }

    fun saveSalesItemsSheet(sheet: String) {
        storage.putString(KEY_SALES_ITEMS_SHEET, sheet)
    }

    fun getTransfersSheet(): String {
        return storage.getString(KEY_TRANSFERS_SHEET, GoogleSheetsConfig.TRANSFERS_SHEET)
            ?: GoogleSheetsConfig.TRANSFERS_SHEET
    }

    fun saveTransfersSheet(sheet: String) {
        storage.putString(KEY_TRANSFERS_SHEET, sheet)
    }

    fun getCurrencyCode(): String {
        return storage.getString(KEY_CURRENCY_CODE, "CHF") ?: "CHF"
    }

    fun saveCurrencyCode(code: String) {
        storage.putString(KEY_CURRENCY_CODE, code.trim().uppercase())
    }

    fun getTempGuestListSheet(): String {
        return storage.getString(KEY_TEMP_GUEST_LIST_SHEET, GoogleSheetsConfig.TEMP_GUEST_LIST_SHEET)
            ?: GoogleSheetsConfig.TEMP_GUEST_LIST_SHEET
    }

    fun saveTempGuestListSheet(sheet: String) {
        storage.putString(KEY_TEMP_GUEST_LIST_SHEET, sheet)
    }
    
    // Sync Configuration
    fun isSyncEnabled(): Boolean {
        return storage.getBoolean(KEY_SYNC_ENABLED, true)
    }
    
    fun setSyncEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_SYNC_ENABLED, enabled)
    }
    
    fun isAutoSyncEnabled(): Boolean {
        return storage.getBoolean(KEY_AUTO_SYNC, true)
    }
    
    fun setAutoSyncEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_AUTO_SYNC, enabled)
    }
    
    fun getSyncInterval(): Int {
        return storage.getInt(KEY_SYNC_INTERVAL, 5) // 5 minutes default
    }
    
    fun saveSyncInterval(intervalMinutes: Int) {
        storage.putInt(KEY_SYNC_INTERVAL, intervalMinutes)
    }
    
    fun getDebugMode(): Boolean {
        return storage.getBoolean(KEY_DEBUG_MODE, false)
    }
    
    fun saveDebugMode(enabled: Boolean) {
        storage.putBoolean(KEY_DEBUG_MODE, enabled)
    }
    
    fun getBackgroundAnimationStyle(): String {
        val stored = storage.getString(KEY_BACKGROUND_ANIMATION_STYLE, "")
        if (stored.isNotEmpty()) {
            return normalizeBackgroundAnimationStyle(stored, default = "topographic")
        }
        if (storage.contains(KEY_ANIMATED_BACKGROUND)) {
            return if (storage.getBoolean(KEY_ANIMATED_BACKGROUND, true)) {
                "arches"
            } else {
                "none"
            }
        }
        return "topographic"
    }

    fun setBackgroundAnimationStyle(style: String) {
        val normalized = normalizeBackgroundAnimationStyle(style, default = "topographic")
        storage.putString(KEY_BACKGROUND_ANIMATION_STYLE, normalized)
        storage.putBoolean(KEY_ANIMATED_BACKGROUND, normalized != "none")
    }

    fun getBackgroundAnimationOpacity(): Float {
        val default = BackgroundAnimationStyle.defaultOpacity(getBackgroundAnimationStyle())
        if (!storage.contains(KEY_BACKGROUND_ANIMATION_OPACITY)) {
            return default
        }
        return storage.getFloat(KEY_BACKGROUND_ANIMATION_OPACITY, default).coerceIn(0.05f, 1.0f)
    }

    fun setBackgroundAnimationOpacity(opacity: Float) {
        storage.putFloat(
            KEY_BACKGROUND_ANIMATION_OPACITY,
            opacity.coerceIn(0.05f, 1.0f),
        )
    }

    fun getBilleterieBackgroundAnimationStyle(): String {
        val stored = storage.getString(KEY_BILLETTERIE_BACKGROUND_ANIMATION_STYLE, "")
        if (stored.isNotEmpty()) {
            return normalizeBackgroundAnimationStyle(stored, default = "none")
        }
        return "none"
    }

    fun setBilleterieBackgroundAnimationStyle(style: String) {
        val normalized = normalizeBackgroundAnimationStyle(style, default = "none")
        storage.putString(KEY_BILLETTERIE_BACKGROUND_ANIMATION_STYLE, normalized)
    }

    fun getBilleterieBackgroundAnimationOpacity(): Float {
        val default = BackgroundAnimationStyle.defaultOpacity(getBilleterieBackgroundAnimationStyle())
        if (!storage.contains(KEY_BILLETTERIE_BACKGROUND_ANIMATION_OPACITY)) {
            return default
        }
        return storage.getFloat(KEY_BILLETTERIE_BACKGROUND_ANIMATION_OPACITY, default).coerceIn(0.05f, 1.0f)
    }

    fun setBilleterieBackgroundAnimationOpacity(opacity: Float) {
        storage.putFloat(
            KEY_BILLETTERIE_BACKGROUND_ANIMATION_OPACITY,
            opacity.coerceIn(0.05f, 1.0f),
        )
    }

    fun getPosBackgroundAnimationStyle(): String {
        val stored = storage.getString(KEY_POS_BACKGROUND_ANIMATION_STYLE, "")
        if (stored.isNotEmpty()) {
            return normalizeBackgroundAnimationStyle(stored, default = "none")
        }
        return "none"
    }

    fun setPosBackgroundAnimationStyle(style: String) {
        val normalized = normalizeBackgroundAnimationStyle(style, default = "none")
        storage.putString(KEY_POS_BACKGROUND_ANIMATION_STYLE, normalized)
    }

    fun getPosBackgroundAnimationOpacity(): Float {
        val default = BackgroundAnimationStyle.defaultOpacity(getPosBackgroundAnimationStyle())
        if (!storage.contains(KEY_POS_BACKGROUND_ANIMATION_OPACITY)) {
            return default
        }
        return storage.getFloat(KEY_POS_BACKGROUND_ANIMATION_OPACITY, default).coerceIn(0.05f, 1.0f)
    }

    fun setPosBackgroundAnimationOpacity(opacity: Float) {
        storage.putFloat(
            KEY_POS_BACKGROUND_ANIMATION_OPACITY,
            opacity.coerceIn(0.05f, 1.0f),
        )
    }

    fun getPosSelectedCategoryName(): String? {
        val stored = storage.getString(KEY_POS_SELECTED_CATEGORY, "").orEmpty()
        return stored.ifEmpty { null }
    }

    fun setPosSelectedCategoryName(name: String?) {
        storage.putString(KEY_POS_SELECTED_CATEGORY, name.orEmpty())
    }

    fun getPosSelectedVenue(): String {
        val stored = storage.getString(KEY_POS_SELECTED_VENUE, "").orEmpty()
        return stored.ifEmpty { com.eventmanager.app.data.models.PosVenueScope.GLOBAL }
    }

    fun setPosSelectedVenue(venue: String) {
        storage.putString(KEY_POS_SELECTED_VENUE, venue)
    }

    private fun normalizeBackgroundAnimationStyle(style: String, default: String): String {
        return when (style) {
            "none", "arches", "topographic" -> style
            else -> default
        }
    }

    fun isAnimatedBackgroundEnabled(): Boolean = getBackgroundAnimationStyle() != "none"

    fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        setBackgroundAnimationStyle(if (enabled) "arches" else "none")
    }
    
    // UI Page Animations Configuration
    fun isPageAnimationsEnabled(): Boolean {
        return storage.getBoolean(KEY_PAGE_ANIMATIONS, true) // Enabled by default
    }
    
    fun setPageAnimationsEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_PAGE_ANIMATIONS, enabled)
    }
    
    /**
     * Millis of the last time the app successfully **pulled** data from Google Sheets
     * (full sync, differential sync, tab-targeted pull, temp guest sheet, etc.).
     * Upload-only / backup-only operations must not advance this value.
     */
    fun getLastSheetsPullAt(): Long {
        val pull = storage.getLong(KEY_LAST_SHEETS_PULL_AT, 0L)
        if (pull > 0L) return pull
        return storage.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun recordSheetsPullAt(timestamp: Long = System.currentTimeMillis()) {
        storage.putLong(KEY_LAST_SHEETS_PULL_AT, timestamp)
    }

    /** @see getLastSheetsPullAt */
    fun getLastSyncTime(): Long = getLastSheetsPullAt()

    /** Call [recordSheetsPullAt] after a successful Sheets download; kept for older call sites. */
    fun saveLastSyncTime(timestamp: Long) = recordSheetsPullAt(timestamp)
    
    // Language Configuration
    fun getLanguage(): String {
        return storage.getString(KEY_LANGUAGE, "en") ?: "en" // Default to English
    }
    
    fun saveLanguage(language: String) {
        storage.putString(KEY_LANGUAGE, language)
    }
    
    // Theme Configuration
    fun getThemeMode(): String {
        return storage.getString(KEY_THEME_MODE, "default") ?: "default" // Default to system theme
    }
    
    fun saveThemeMode(themeMode: String) {
        storage.putString(KEY_THEME_MODE, themeMode)
    }
    
    // Color Theme Configuration
    fun getColorTheme(): String {
        return storage.getString(KEY_COLOR_THEME, "system") ?: "system" // Default to system colors
    }
    
    fun saveColorTheme(colorTheme: String) {
        storage.putString(KEY_COLOR_THEME, colorTheme)
    }

    fun getCustomThemeColor(isDark: Boolean, role: String, defaultArgb: Int): Int {
        val mode = if (isDark) "dark" else "light"
        val key = "${KEY_CUSTOM_THEME_COLOR_PREFIX}_${mode}_${role}"
        return storage.getInt(key, defaultArgb)
    }

    fun saveCustomThemeColor(isDark: Boolean, role: String, argb: Int) {
        val mode = if (isDark) "dark" else "light"
        val key = "${KEY_CUSTOM_THEME_COLOR_PREFIX}_${mode}_${role}"
        storage.putInt(key, argb)
    }

    /**
     * Mark that the next app recreation is visual-only (e.g. theme switch),
     * so startup sync should be skipped once to avoid unnecessary API calls.
     */
    fun markSkipNextStartupSync() {
        storage.putBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, true)
    }

    /**
     * Consume and clear the one-shot "skip startup sync" marker.
     */
    fun consumeSkipNextStartupSync(): Boolean {
        val shouldSkip = storage.getBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, false)
        if (shouldSkip) {
            storage.putBoolean(KEY_SKIP_NEXT_STARTUP_SYNC, false)
        }
        return shouldSkip
    }
    
    // Resolution Scale Configuration
    fun getResolutionScale(): Float {
        return storage.getFloat(KEY_RESOLUTION_SCALE, 1.0f) // Default to 100% (normal size)
    }
    
    fun saveResolutionScale(scale: Float) {
        storage.putFloat(KEY_RESOLUTION_SCALE, scale)
    }
    
    // Date Format Configuration
    fun getDateFormat(): String {
        return storage.getString(KEY_DATE_FORMAT, "MM/dd/yyyy") ?: "MM/dd/yyyy" // Default to MM/dd/yyyy
    }

    fun saveDateFormat(dateFormat: String) {
        storage.putString(KEY_DATE_FORMAT, dateFormat)
    }

    // Time Format Configuration
    fun getTimeFormat(): String {
        return storage.getString(KEY_TIME_FORMAT, "HH:mm") ?: "HH:mm" // Default to HH:mm
    }

    fun saveTimeFormat(timeFormat: String) {
        storage.putString(KEY_TIME_FORMAT, timeFormat)
    }
    
    // Date Change Offset Configuration
    fun getDateChangeOffsetHours(): Int {
        return storage.getInt(KEY_DATE_CHANGE_OFFSET_HOURS, 0) // Default to 0 (midnight)
    }
    
    fun saveDateChangeOffsetHours(hours: Int) {
        storage.putInt(KEY_DATE_CHANGE_OFFSET_HOURS, hours)
    }
    
    // Seasonal Fun Configuration
    fun isSeasonalFunEnabled(): Boolean {
        return storage.getBoolean(KEY_SEASONAL_FUN, true) // Enabled by default
    }
    
    fun setSeasonalFunEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_SEASONAL_FUN, enabled)
    }
    
    // Graph Time Period Configuration
    fun getSelectedGraphTimePeriod(): String {
        return storage.getString(KEY_SELECTED_GRAPH_TIME_PERIOD, "ONE_MONTH") ?: "ONE_MONTH" // Default to 1 Month
    }
    
    fun saveSelectedGraphTimePeriod(timePeriod: String) {
        storage.putString(KEY_SELECTED_GRAPH_TIME_PERIOD, timePeriod)
    }
    
    // App Icon Configuration
    fun getAppIconStyle(): String {
        val raw = storage.getString(KEY_APP_ICON_STYLE, "white") ?: "white"
        val migrated = migrateLegacyAppIconStyle(raw)
        if (migrated != raw) {
            storage.putString(KEY_APP_ICON_STYLE, migrated)
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
        return if (mapped in AppIconStyles.ALL_ICON_STYLES) mapped else "white"
    }
    
    fun saveAppIconStyle(iconStyle: String) {
        storage.putString(KEY_APP_ICON_STYLE, iconStyle)
    }
    
    fun isAppIconAutoAdapt(): Boolean {
        return storage.getBoolean(KEY_APP_ICON_AUTO_ADAPT, false) // Disabled by default
    }
    
    fun setAppIconAutoAdapt(enabled: Boolean) {
        storage.putBoolean(KEY_APP_ICON_AUTO_ADAPT, enabled)
    }
    
    // People Counter Visibility Configuration
    fun isPeopleCounterVisible(): Boolean {
        return storage.getBoolean(KEY_PEOPLE_COUNTER_VISIBLE, false) // Hidden by default
    }
    
    fun setPeopleCounterVisible(visible: Boolean) {
        storage.putBoolean(KEY_PEOPLE_COUNTER_VISIBLE, visible)
    }

    /** Stable per-installation ID used for people-counter writer arbitration on Google Sheets. */
    fun getOrCreatePersistentDeviceId(): String {
        val existing = storage.getString(KEY_APP_DEVICE_NANOID, "").trim()
        if (existing.isNotEmpty()) return existing
        val created = NanoIdGenerator.generateGuestId()
        storage.putString(KEY_APP_DEVICE_NANOID, created)
        return created
    }

    fun getPeopleCounterSelectedVenueId(): Long {
        return storage.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
    }

    fun setPeopleCounterSelectedVenueId(id: Long) {
        storage.putLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, id)
    }

    private fun ensurePeopleCounterPriorityVenuesMigrated() {
        if (storage.contains(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS)) {
            storage.remove(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY)
            storage.remove(KEY_PEOPLE_COUNTER_PRIORITY)
            return
        }
        var venueIds: Set<String> = emptySet()
        when {
            storage.contains(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY) -> {
                val legacyReadOnly = storage.getBoolean(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY, true)
                if (!legacyReadOnly) {
                    val sel = storage.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
                    if (sel > 0L) venueIds = setOf(sel.toString())
                }
            }
            storage.contains(KEY_PEOPLE_COUNTER_PRIORITY) -> {
                if (storage.getBoolean(KEY_PEOPLE_COUNTER_PRIORITY, false)) {
                    val sel = storage.getLong(KEY_PEOPLE_COUNTER_SELECTED_VENUE_ID, 0L)
                    if (sel > 0L) venueIds = setOf(sel.toString())
                }
            }
        }
        storage.remove(KEY_PEOPLE_COUNTER_USER_READ_ONLY_LEGACY)
        storage.remove(KEY_PEOPLE_COUNTER_PRIORITY)
        storage.putStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, HashSet(venueIds))
    }

    /**
     * When true, this device requests people-counter upload priority for [venueId] only.
     * Each venue is independent; default is off until the user enables priority for that venue.
     */
    fun isPeopleCounterPriority(venueId: Long): Boolean {
        if (venueId <= 0L) return false
        ensurePeopleCounterPriorityVenuesMigrated()
        val set = storage.getStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, emptySet()) ?: emptySet()
        return set.contains(venueId.toString())
    }

    fun setPeopleCounterPriority(venueId: Long, enabled: Boolean) {
        if (venueId <= 0L) return
        ensurePeopleCounterPriorityVenuesMigrated()
        val raw = storage.getStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, emptySet()) ?: emptySet()
        val next = HashSet(raw)
        if (enabled) next.add(venueId.toString()) else next.remove(venueId.toString())
        storage.putStringSet(KEY_PEOPLE_COUNTER_PRIORITY_VENUE_IDS, next)
    }
    
    // Statistics Visibility Configuration
    fun isStatisticsVisible(): Boolean {
        return storage.getBoolean(KEY_STATISTICS_VISIBLE, true) // Shown by default
    }
    
    fun setStatisticsVisible(visible: Boolean) {
        storage.putBoolean(KEY_STATISTICS_VISIBLE, visible)
    }

    // Announcements Configuration

    fun isAnnouncementsReceptionEnabled(): Boolean {
        return storage.getBoolean(KEY_ANNOUNCEMENTS_RECEPTION_ENABLED, true)
    }

    fun setAnnouncementsReceptionEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_ANNOUNCEMENTS_RECEPTION_ENABLED, enabled)
    }

    fun getAnnouncementsTrackedVenueIds(): Set<String> {
        return storage.getStringSet(KEY_ANNOUNCEMENTS_TRACKED_VENUE_IDS, emptySet()) ?: emptySet()
    }

    fun setAnnouncementsTrackedVenueIds(venueIds: Set<String>) {
        storage.putStringSet(KEY_ANNOUNCEMENTS_TRACKED_VENUE_IDS, venueIds)
    }

    fun getAnnouncementsValidityMinutes(): Int {
        return storage.getInt(KEY_ANNOUNCEMENTS_VALIDITY_MINUTES, 60)
    }

    fun setAnnouncementsValidityMinutes(minutes: Int) {
        storage.putInt(KEY_ANNOUNCEMENTS_VALIDITY_MINUTES, minutes)
    }

    fun isAnnouncementsNonAdminSendEnabled(): Boolean {
        return storage.getBoolean(KEY_ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED, false)
    }

    fun setAnnouncementsNonAdminSendEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_ANNOUNCEMENTS_NON_ADMIN_SEND_ENABLED, enabled)
    }

    fun isCategoryAnnouncementsExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_ANNOUNCEMENTS_EXPANDED, false)
    }

    fun setCategoryAnnouncementsExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_ANNOUNCEMENTS_EXPANDED, expanded)
    }

    fun getAnnouncementsLastSeenTimestamps(): Map<String, Long> {
        val set = storage.getStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, emptySet()) ?: emptySet()
        return set.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0] to (parts[1].toLongOrNull() ?: 0L)
            } else null
        }.toMap()
    }

    fun setAnnouncementLastSeenTimestamp(venueKey: String, timestamp: Long) {
        val set = storage.getStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, emptySet()) ?: emptySet()
        val mutable = HashSet(set)
        mutable.removeAll { it.startsWith("$venueKey:") }
        mutable.add("$venueKey:$timestamp")
        storage.putStringSet(KEY_ANNOUNCEMENTS_LAST_SEEN_TIMESTAMPS, mutable)
    }

    // Page Scroll Behavior Configuration
    // HEADER_PINNED   -> Only the list scrolls, header stays fixed
    // FULL_SCROLL     -> Header scrolls together with the list
    // STICKY_FILTERS  -> Header scrolls but filters become sticky at the top (default)
    fun getScrollBehavior(): String {
        // Check if new string setting exists
        val storedString = storage.getString("${KEY_PAGE_SCROLL_BEHAVIOR}_mode", "").ifEmpty { null }
        if (storedString != null) {
            return storedString
        }
        
        // Migrate old boolean setting if it was explicitly set
        // Note: storage.contains checks if the key was ever set by the user
        if (storage.contains(KEY_PAGE_SCROLL_BEHAVIOR)) {
            val oldValue = storage.getBoolean(KEY_PAGE_SCROLL_BEHAVIOR, true)
            return if (oldValue) HEADER_PINNED else FULL_SCROLL
        }
        
        // Default to STICKY_FILTERS for new users
        return STICKY_FILTERS
    }
    
    fun setScrollBehavior(behavior: String) {
        storage.putString("${KEY_PAGE_SCROLL_BEHAVIOR}_mode", behavior)
    }

    /** Desktop only: admin navigation placement (bottom, left, or right). */
    fun getDesktopAdminNavLayout(): String =
        storage.getString(KEY_DESKTOP_ADMIN_NAV_LAYOUT, "left") ?: "left"

    fun setDesktopAdminNavLayout(layout: String) {
        storage.putString(KEY_DESKTOP_ADMIN_NAV_LAYOUT, layout)
    }

    /** Desktop only: whether the side navigation rail shows labels (expanded) or icons only. */
    fun isDesktopAdminNavRailExpanded(): Boolean =
        storage.getBoolean(KEY_DESKTOP_ADMIN_NAV_RAIL_EXPANDED, true)

    fun setDesktopAdminNavRailExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_DESKTOP_ADMIN_NAV_RAIL_EXPANDED, expanded)
    }
    
    // Update Manifest URL Configuration
    fun getUpdateManifestUrl(): String {
        return storage.getString(KEY_UPDATE_MANIFEST_URL, AppBuildInfo.UPDATE_MANIFEST_URL)
            ?: AppBuildInfo.UPDATE_MANIFEST_URL
    }

    fun saveUpdateManifestUrl(url: String) {
        storage.putString(KEY_UPDATE_MANIFEST_URL, url)
    }

    // Update Store URL Configuration
    fun getUpdateStoreUrl(): String {
        return storage.getString(KEY_UPDATE_STORE_URL, AppBuildInfo.UPDATE_FALLBACK_STORE_URL)
            ?: AppBuildInfo.UPDATE_FALLBACK_STORE_URL
    }

    fun saveUpdateStoreUrl(url: String) {
        storage.putString(KEY_UPDATE_STORE_URL, url)
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
        return storage.getString(KEY_EMAIL_SUBJECT, "") ?: ""
    }
    
    fun saveEmailSubject(subject: String) {
        storage.putString(KEY_EMAIL_SUBJECT, subject)
    }
    
    fun getEmailContentBefore(): String {
        return storage.getString(KEY_EMAIL_CONTENT_BEFORE, "") ?: ""
    }
    
    fun saveEmailContentBefore(content: String) {
        storage.putString(KEY_EMAIL_CONTENT_BEFORE, content)
    }
    
    fun isEmailIncludeQrEnabled(): Boolean {
        return storage.getBoolean(KEY_EMAIL_INCLUDE_QR, true) // Include QR by default
    }
    
    fun setEmailIncludeQrEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_EMAIL_INCLUDE_QR, enabled)
    }
    
    fun getEmailContentAfter(): String {
        return storage.getString(KEY_EMAIL_CONTENT_AFTER, "") ?: ""
    }
    
    fun saveEmailContentAfter(content: String) {
        storage.putString(KEY_EMAIL_CONTENT_AFTER, content)
    }
    
    fun getEmailSignature(): String {
        return storage.getString(KEY_EMAIL_SIGNATURE, "") ?: ""
    }
    
    fun saveEmailSignature(signature: String) {
        storage.putString(KEY_EMAIL_SIGNATURE, signature)
    }
    
    fun isEmailIncludeLogoEnabled(): Boolean {
        return storage.getBoolean(KEY_EMAIL_INCLUDE_LOGO, false) // Logo disabled by default
    }
    
    fun setEmailIncludeLogoEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_EMAIL_INCLUDE_LOGO, enabled)
    }

    fun isEmailIncludeDigitalWalletPassEnabled(): Boolean {
        return storage.getBoolean(KEY_EMAIL_INCLUDE_DIGITAL_WALLET_PASS, true)
    }

    fun setEmailIncludeDigitalWalletPassEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_EMAIL_INCLUDE_DIGITAL_WALLET_PASS, enabled)
    }

    fun getWalletPassCertificatePassword(): String {
        return storage.getString(KEY_WALLET_PASS_CERT_PASSWORD, "") ?: ""
    }

    fun saveWalletPassCertificatePassword(password: String) {
        storage.putString(KEY_WALLET_PASS_CERT_PASSWORD, password)
    }

    fun getWalletPassTypeIdentifier(): String {
        return storage.getString(KEY_WALLET_PASS_TYPE_IDENTIFIER, "") ?: ""
    }

    fun saveWalletPassTypeIdentifier(identifier: String) {
        storage.putString(KEY_WALLET_PASS_TYPE_IDENTIFIER, identifier)
    }

    fun getWalletPassTeamIdentifier(): String {
        return storage.getString(KEY_WALLET_PASS_TEAM_IDENTIFIER, "") ?: ""
    }

    fun saveWalletPassTeamIdentifier(teamId: String) {
        storage.putString(KEY_WALLET_PASS_TEAM_IDENTIFIER, teamId)
    }

    fun getEmailAssociationName(): String {
        return storage.getString(KEY_EMAIL_ASSOCIATION_NAME, "Collectif Nocturne") ?: "Collectif Nocturne"
    }

    fun saveEmailAssociationName(name: String) {
        storage.putString(KEY_EMAIL_ASSOCIATION_NAME, name)
    }
    
    fun getEmailLogoUri(): String {
        return storage.getString(KEY_EMAIL_LOGO_URI, "") ?: ""
    }
    
    fun saveEmailLogoUri(uri: String) {
        storage.putString(KEY_EMAIL_LOGO_URI, uri)
    }
    
    fun getGmailAccount(): String {
        return storage.getString(KEY_EMAIL_GMAIL_ACCOUNT, "") ?: ""
    }
    
    fun saveGmailAccount(account: String) {
        storage.putString(KEY_EMAIL_GMAIL_ACCOUNT, account)
    }
    
    fun getGmailAuthToken(): String {
        return storage.getString(KEY_EMAIL_GMAIL_AUTH_TOKEN, "") ?: ""
    }
    
    fun saveGmailAuthToken(token: String) {
        storage.putString(KEY_EMAIL_GMAIL_AUTH_TOKEN, token)
    }
    
    fun clearGmailAuth() {
        storage.remove(KEY_EMAIL_GMAIL_ACCOUNT)
        storage.remove(KEY_EMAIL_GMAIL_AUTH_TOKEN)
    }

    fun isGmailUseServiceAccount(): Boolean {
        return storage.getBoolean(KEY_EMAIL_GMAIL_USE_SERVICE_ACCOUNT, false)
    }

    fun setGmailUseServiceAccount(enabled: Boolean) {
        storage.putBoolean(KEY_EMAIL_GMAIL_USE_SERVICE_ACCOUNT, enabled)
    }

    fun getGmailServiceAccountSenderEmail(): String {
        return storage.getString(KEY_EMAIL_GMAIL_SERVICE_ACCOUNT_SENDER, "") ?: ""
    }

    fun saveGmailServiceAccountSenderEmail(email: String) {
        storage.putString(KEY_EMAIL_GMAIL_SERVICE_ACCOUNT_SENDER, email.trim())
    }
    
    fun isCategoryEmailExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_EMAIL_EXPANDED, false)
    }
    
    fun setCategoryEmailExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_EMAIL_EXPANDED, expanded)
    }
    
    // Guest Email Settings Configuration
    fun getGuestEmailSubject(): String {
        return storage.getString(KEY_GUEST_EMAIL_SUBJECT, "") ?: ""
    }
    
    fun saveGuestEmailSubject(subject: String) {
        storage.putString(KEY_GUEST_EMAIL_SUBJECT, subject)
    }
    
    fun getGuestEmailContentBefore(): String {
        return storage.getString(KEY_GUEST_EMAIL_CONTENT_BEFORE, "") ?: ""
    }
    
    fun saveGuestEmailContentBefore(content: String) {
        storage.putString(KEY_GUEST_EMAIL_CONTENT_BEFORE, content)
    }
    
    fun isGuestEmailIncludeQrEnabled(): Boolean {
        return storage.getBoolean(KEY_GUEST_EMAIL_INCLUDE_QR, true) // Include QR by default
    }
    
    fun setGuestEmailIncludeQrEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_GUEST_EMAIL_INCLUDE_QR, enabled)
    }
    
    fun getGuestEmailContentAfter(): String {
        return storage.getString(KEY_GUEST_EMAIL_CONTENT_AFTER, "") ?: ""
    }
    
    fun saveGuestEmailContentAfter(content: String) {
        storage.putString(KEY_GUEST_EMAIL_CONTENT_AFTER, content)
    }
    
    fun getGuestEmailSignature(): String {
        return storage.getString(KEY_GUEST_EMAIL_SIGNATURE, "") ?: ""
    }
    
    fun saveGuestEmailSignature(signature: String) {
        storage.putString(KEY_GUEST_EMAIL_SIGNATURE, signature)
    }
    
    // Biometric Admin Login Configuration
    fun isBiometricAdminLoginEnabled(): Boolean {
        if (!storage.getBoolean(KEY_BIOMETRIC_ADMIN_LOGIN, false)) return false
        return getBiometricAdminProfileLink() != null
    }

    fun getBiometricAdminProfileLink(): BiometricAdminProfileLink? {
        val raw = storage.getString(KEY_BIOMETRIC_ADMIN_PROFILE_LINK, "")
        return BiometricAdminProfileLink.decode(raw.takeIf { it.isNotBlank() })
    }

    fun setBiometricAdminLoginEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_BIOMETRIC_ADMIN_LOGIN, enabled)
        if (!enabled) {
            storage.remove(KEY_BIOMETRIC_ADMIN_PROFILE_LINK)
        }
    }

    fun setBiometricAdminProfileLink(link: BiometricAdminProfileLink) {
        storage.putString(KEY_BIOMETRIC_ADMIN_PROFILE_LINK, link.encode())
        storage.putBoolean(KEY_BIOMETRIC_ADMIN_LOGIN, true)
    }
    
    // Clear all settings
    fun clearAllSettings() {
        storage.clear()
    }
    
    // Check if settings are configured
    fun isConfigured(): Boolean {
        val spreadsheetId = getSpreadsheetId()
        return spreadsheetId.isNotEmpty() && spreadsheetId != GoogleSheetsConfig.SPREADSHEET_ID
    }
    
    // Settings Category Expansion State
    fun isCategorySyncExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_SYNC_EXPANDED, false)
    }
    
    fun setCategorySyncExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_SYNC_EXPANDED, expanded)
    }
    
    fun isCategoryAppearanceExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_APPEARANCE_EXPANDED, false)
    }
    
    fun setCategoryAppearanceExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_APPEARANCE_EXPANDED, expanded)
    }
    
    fun isCategoryLocalizationExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_LOCALIZATION_EXPANDED, false)
    }
    
    fun setCategoryLocalizationExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_LOCALIZATION_EXPANDED, expanded)
    }
    
    fun isCategoryAnimationExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_ANIMATION_EXPANDED, false)
    }
    
    fun setCategoryAnimationExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_ANIMATION_EXPANDED, expanded)
    }
    
    fun isCategoryDeveloperExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_DEVELOPER_EXPANDED, false)
    }
    
    fun setCategoryDeveloperExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_DEVELOPER_EXPANDED, expanded)
    }
    
    fun isCategoryMaintenanceExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_MAINTENANCE_EXPANDED, false)
    }
    
    fun setCategoryMaintenanceExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_MAINTENANCE_EXPANDED, expanded)
    }

    fun isCategoryExternalReaderExpanded(): Boolean {
        return storage.getBoolean(KEY_CATEGORY_EXTERNAL_READER_EXPANDED, false)
    }

    fun setCategoryExternalReaderExpanded(expanded: Boolean) {
        storage.putBoolean(KEY_CATEGORY_EXTERNAL_READER_EXPANDED, expanded)
    }

    /**
     * MAC address of the BLE NFC reader (ACR1255U-J1) the user selected from the in-app scanner.
     * Empty string means "no reader chosen yet" — fall back to bonded-device lookup.
     */
    fun getExternalBleReaderMac(): String {
        return storage.getString(KEY_EXTERNAL_BLE_READER_MAC, "") ?: ""
    }

    fun saveExternalBleReaderMac(mac: String) {
        storage.putString(KEY_EXTERNAL_BLE_READER_MAC, mac)
    }

    fun getExternalBleReaderName(): String {
        return storage.getString(KEY_EXTERNAL_BLE_READER_NAME, "") ?: ""
    }

    fun saveExternalBleReaderName(name: String) {
        storage.putString(KEY_EXTERNAL_BLE_READER_NAME, name)
    }

    fun clearExternalBleReader() {
        storage.remove(KEY_EXTERNAL_BLE_READER_MAC)
        storage.remove(KEY_EXTERNAL_BLE_READER_NAME)
    }
    
    fun isSetupWizardCompleted(): Boolean {
        return storage.getBoolean(KEY_SETUP_WIZARD_COMPLETED, false)
    }
    
    fun setSetupWizardCompleted(completed: Boolean) {
        storage.putBoolean(KEY_SETUP_WIZARD_COMPLETED, completed)
    }
    
    fun shouldShowSetupWizard(): Boolean {
        return !isSetupWizardCompleted() && !isConfigured()
    }
}
