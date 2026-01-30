package com.eventmanager.app.data.sync

import android.content.Context
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
}
