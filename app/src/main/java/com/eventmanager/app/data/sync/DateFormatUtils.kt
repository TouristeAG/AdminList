package com.eventmanager.app.data.sync

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for date/time formatting that respects user settings
 */
object DateFormatUtils {
    
    /**
     * Format a timestamp using the user's selected date format
     */
    fun formatDate(timestamp: Long, context: Context): String {
        val settingsManager = SettingsManager(context)
        val dateFormat = settingsManager.getDateFormat()
        val formatter = SimpleDateFormat(dateFormat, Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
    
    /**
     * Format a timestamp using the user's selected time format
     */
    fun formatTime(timestamp: Long, context: Context): String {
        val settingsManager = SettingsManager(context)
        val timeFormat = settingsManager.getTimeFormat()
        val formatter = SimpleDateFormat(timeFormat, Locale.getDefault())
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
     * Get all available date formats
     */
    fun getAvailableDateFormats(): List<Pair<String, String>> {
        return listOf(
            "MM/dd/yyyy" to "12/25/2025",
            "dd/MM/yyyy" to "25/12/2025",
            "yyyy-MM-dd" to "2025-12-25",
            "MMM dd, yyyy" to "Dec 25, 2025",
            "dd MMM yyyy" to "25 Dec 2025",
            "MMMM dd, yyyy" to "December 25, 2025"
        )
    }
    
    /**
     * Get all available time formats
     */
    fun getAvailableTimeFormats(): List<Pair<String, String>> {
        return listOf(
            "HH:mm" to "14:30",
            "hh:mm a" to "02:30 PM",
            "HH:mm:ss" to "14:30:45"
        )
    }
}
