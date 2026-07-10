package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
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
    fun formatDate(timestamp: Long, platformContext: PlatformContext): String {
        val settingsManager = SettingsManager(platformContext)
        val dateFormat = settingsManager.getDateFormat()
        val formatter = getFormatter(dateFormat)
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format a timestamp using the user's selected time format
     * Optimized to reuse cached formatters
     */
    fun formatTime(timestamp: Long, platformContext: PlatformContext): String {
        val settingsManager = SettingsManager(platformContext)
        val timeFormat = settingsManager.getTimeFormat()
        val formatter = getFormatter(timeFormat)
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format a timestamp using both date and time format with a separator
     */
    fun formatDateTime(timestamp: Long, platformContext: PlatformContext): String {
        val date = formatDate(timestamp, platformContext)
        val time = formatTime(timestamp, platformContext)
        return "$date $time"
    }

    /**
     * Relative time since [syncTimeMillis]: "50 sec ago", "2min 30s ago", "1h 5m ago", "12d ago";
     * falls back to a short absolute date when older than ~30 days.
     */
    fun formatRelativeSinceSync(
        context: PlatformContext,
        syncTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (syncTimeMillis <= 0L) return "Never"
        val elapsed = (nowMillis - syncTimeMillis).coerceAtLeast(0L)
        val totalSec = elapsed / 1000L
        if (totalSec < 5L) return "Just now"
        if (totalSec < 60L) return "${totalSec}s ago"
        val totalMin = totalSec / 60L
        if (totalMin < 60L) return "${totalMin}m ${totalSec % 60L}s ago"
        val totalHr = totalMin / 60L
        if (totalHr < 24L) return "${totalHr}h ${totalMin % 60L}m ago"
        val totalDay = totalHr / 24L
        if (totalDay < 30L) return "${totalDay}d ago"
        return formatDateTime(syncTimeMillis, context)
    }

    fun formatSyncPillTimeAgo(
        context: PlatformContext,
        syncTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (syncTimeMillis <= 0L) return "Never"
        val elapsed = (nowMillis - syncTimeMillis).coerceAtLeast(0L)
        if (elapsed < 60_000L) return "Just now"
        val totalMin = elapsed / 60_000L
        if (elapsed < 3_600_000L) return "${totalMin.coerceAtLeast(1L)}m ago"
        if (elapsed < 86_400_000L) {
            val h = (totalMin / 60L).toInt()
            val m = (totalMin % 60L).toInt()
            return if (m == 0) "${h}h ago" else "${h}h ${m}m ago"
        }
        val d = (elapsed / 86_400_000L).toInt()
        val rem = elapsed % 86_400_000L
        val h = (rem / 3_600_000L).toInt()
        val m = ((rem % 3_600_000L) / 60_000L).toInt()
        return when {
            h == 0 && m == 0 -> "${d}d ago"
            h == 0 -> "${d}d ${m}m ago"
            m == 0 -> "${d}d ${h}h ago"
            else -> "${d}d ${h}h ${m}m ago"
        }
    }
}
