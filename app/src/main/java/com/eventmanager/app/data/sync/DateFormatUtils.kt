package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for date/time formatting that respects user settings
 * Optimized with caching to reduce object allocations
 */
object DateFormatUtils {
    // Cache SimpleDateFormat instances per format pattern (thread-safe for reading)
    // Using ThreadLocal to ensure thread safety without synchronization overhead
    private val formatterCache = ThreadLocal.withInitial {
        mutableMapOf<String, SimpleDateFormat>()
    }
    
    /**
     * Get or create a cached SimpleDateFormat instance for the given pattern
     * Uses ThreadLocal to avoid synchronization overhead
     */
    private fun getFormatter(pattern: String): SimpleDateFormat {
        val cache = formatterCache.get()
            ?: mutableMapOf<String, SimpleDateFormat>().also { formatterCache.set(it) }
        return cache.getOrPut(pattern) {
            SimpleDateFormat(pattern, Locale.getDefault())
        }
    }
    
    /**
     * Format a timestamp using the user's selected date format
     * Optimized to reuse cached formatters
     */
    fun formatDate(timestamp: Long, context: Context): String {
        val settingsManager = SettingsManager(context)
        val dateFormat = settingsManager.getDateFormat()
        val formatter = getFormatter(dateFormat)
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format a timestamp using the user's selected time format
     * Optimized to reuse cached formatters
     */
    fun formatTime(timestamp: Long, context: Context): String {
        val settingsManager = SettingsManager(context)
        val timeFormat = settingsManager.getTimeFormat()
        val formatter = getFormatter(timeFormat)
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format a timestamp using both date and time format with a separator
     */
    fun formatDateTime(timestamp: Long, context: Context): String {
        val date = formatDate(timestamp, context)
        val time = formatTime(timestamp, context)
        return "$date $time"
    }

    /**
     * Relative time since [syncTimeMillis]: "50 sec ago", "2min 30s ago", "1h 5m ago", "12d ago";
     * falls back to a short absolute date when older than ~30 days.
     */
    fun formatRelativeSinceSync(
        context: Context,
        syncTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (syncTimeMillis <= 0L) {
            return context.getString(R.string.last_update_never_brief)
        }
        val elapsed = (nowMillis - syncTimeMillis).coerceAtLeast(0L)
        val totalSec = elapsed / 1000L
        if (totalSec < 5L) {
            return context.getString(R.string.last_update_just_now)
        }
        if (totalSec < 60L) {
            return context.getString(R.string.last_update_seconds_ago, totalSec)
        }
        val totalMin = totalSec / 60L
        if (totalMin < 60L) {
            val sec = totalSec % 60L
            return context.getString(R.string.last_update_min_sec_ago, totalMin, sec)
        }
        val totalHr = totalMin / 60L
        if (totalHr < 24L) {
            val min = totalMin % 60L
            return context.getString(R.string.last_update_hour_min_ago, totalHr, min)
        }
        val totalDay = totalHr / 24L
        if (totalDay < 30L) {
            return context.getString(R.string.last_update_days_ago, totalDay)
        }
        return formatDateTime(syncTimeMillis, context)
    }

    /**
     * Compact relative time for the bottom-right sync pill: minutes, then hours+minutes,
     * then days+hours+minutes (no seconds). Omits zero sub-units where natural.
     */
    fun formatSyncPillTimeAgo(
        context: Context,
        syncTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (syncTimeMillis <= 0L) {
            return context.getString(R.string.last_update_never_brief)
        }
        val elapsed = (nowMillis - syncTimeMillis).coerceAtLeast(0L)
        if (elapsed < 60_000L) {
            return context.getString(R.string.last_update_just_now)
        }
        val totalMin = elapsed / 60_000L
        if (elapsed < 3_600_000L) {
            val mins = totalMin.coerceAtLeast(1L).toInt()
            return context.getString(R.string.sync_pill_minutes_ago, mins)
        }
        if (elapsed < 86_400_000L) {
            val h = (totalMin / 60L).toInt()
            val m = (totalMin % 60L).toInt()
            return if (m == 0) {
                context.getString(R.string.sync_pill_hours_ago, h)
            } else {
                context.getString(R.string.sync_pill_hours_minutes_ago, h, m)
            }
        }
        val d = (elapsed / 86_400_000L).toInt()
        val rem = elapsed % 86_400_000L
        val h = (rem / 3_600_000L).toInt()
        val m = ((rem % 3_600_000L) / 60_000L).toInt()
        return when {
            h == 0 && m == 0 -> context.getString(R.string.sync_pill_days_ago, d)
            h == 0 -> context.getString(R.string.sync_pill_days_minutes_ago, d, m)
            m == 0 -> context.getString(R.string.sync_pill_days_hours_ago, d, h)
            else -> context.getString(R.string.sync_pill_days_hours_minutes_ago, d, h, m)
        }
    }
}
