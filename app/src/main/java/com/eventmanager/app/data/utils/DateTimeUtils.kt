package com.eventmanager.app.data.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

object DateTimeUtils {
    
    // Geneva timezone
    private val GENEVA_TIMEZONE = TimeZone.getTimeZone("Europe/Zurich")
    
    /**
     * Gets the current date and time in Geneva timezone
     */
    fun getCurrentGenevaTime(): Calendar {
        return Calendar.getInstance(GENEVA_TIMEZONE)
    }
    
    /**
     * Gets the current timestamp in Geneva timezone
     */
    fun getCurrentGenevaTimestamp(): Long {
        return getCurrentGenevaTime().timeInMillis
    }
    
    /**
     * Formats a timestamp to a readable date string in Geneva timezone
     */
    fun formatGenevaDate(timestamp: Long): String {
        val calendar = Calendar.getInstance(GENEVA_TIMEZONE).apply {
            timeInMillis = timestamp
        }
        val formatter = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).apply {
            timeZone = GENEVA_TIMEZONE
        }
        return formatter.format(calendar.time)
    }
    
    /**
     * Formats a timestamp to a date string (date only) in Geneva timezone
     */
    fun formatGenevaDateOnly(timestamp: Long): String {
        val calendar = Calendar.getInstance(GENEVA_TIMEZONE).apply {
            timeInMillis = timestamp
        }
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
            timeZone = GENEVA_TIMEZONE
        }
        return formatter.format(calendar.time)
    }
    
    /**
     * Formats a timestamp to a time string (time only) in Geneva timezone
     */
    fun formatGenevaTimeOnly(timestamp: Long): String {
        val calendar = Calendar.getInstance(GENEVA_TIMEZONE).apply {
            timeInMillis = timestamp
        }
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = GENEVA_TIMEZONE
        }
        return formatter.format(calendar.time)
    }
    
    /**
     * Creates a Calendar instance for a specific date and time in Geneva timezone
     */
    fun createGenevaCalendar(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int): Calendar {
        val calendar = Calendar.getInstance(GENEVA_TIMEZONE)
        calendar.set(year, month, dayOfMonth, hourOfDay, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }
    
    /**
     * Gets the start of day (00:00:00) for a given date in Geneva timezone
     */
    fun getStartOfDayGeneva(year: Int, month: Int, dayOfMonth: Int): Calendar {
        return createGenevaCalendar(year, month, dayOfMonth, 0, 0)
    }
    
    /**
     * Gets the end of day (23:59:59) for a given date in Geneva timezone
     */
    fun getEndOfDayGeneva(year: Int, month: Int, dayOfMonth: Int): Calendar {
        return createGenevaCalendar(year, month, dayOfMonth, 23, 59)
    }
    
    /**
     * Checks if a timestamp is in the past (before current Geneva time)
     */
    fun isPast(timestamp: Long): Boolean {
        return timestamp < getCurrentGenevaTimestamp()
    }
    
    /**
     * Checks if a timestamp is in the future (after current Geneva time)
     */
    fun isFuture(timestamp: Long): Boolean {
        return timestamp > getCurrentGenevaTimestamp()
    }
    
    /**
     * Gets a human-readable relative time string (e.g., "2 hours ago", "in 3 days")
     */
    fun getRelativeTimeString(timestamp: Long): String {
        val now = getCurrentGenevaTimestamp()
        val diff = timestamp - now
        val absDiff = kotlin.math.abs(diff)
        
        val minutes = absDiff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> {
                val prefix = if (diff > 0) "in" else "ago"
                "$prefix $days day${if (days > 1) "s" else ""}"
            }
            hours > 0 -> {
                val prefix = if (diff > 0) "in" else "ago"
                "$prefix $hours hour${if (hours > 1) "s" else ""}"
            }
            minutes > 0 -> {
                val prefix = if (diff > 0) "in" else "ago"
                "$prefix $minutes minute${if (minutes > 1) "s" else ""}"
            }
            else -> if (diff > 0) "in a moment" else "just now"
        }
    }
    
    /**
     * Gets the start of day with offset applied for a given timestamp.
     * If offset is +3 hours, start of day for Jan 1 would be Jan 1 3:00 AM.
     * 
     * @param timestamp The timestamp to get the start of day for
     * @param offsetHours Hours to add before the date changes (e.g., 3 means 3:00 AM)
     * @return Calendar instance set to the start of day with offset applied
     */
    fun getStartOfDayWithOffset(timestamp: Long, offsetHours: Int): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Subtract offset hours to get the actual start of day
        // If offset is +3, we want Jan 1 3:00 AM, so we set to Jan 1 00:00 and add 3 hours
        calendar.add(Calendar.HOUR_OF_DAY, offsetHours)
        
        return calendar
    }
    
    /**
     * Gets the end of day with offset applied for a given timestamp.
     * If offset is +3 hours, end of day for Jan 1 would be Jan 2 3:00 AM (start of next day).
     * 
     * @param timestamp The timestamp to get the end of day for
     * @param offsetHours Hours to add before the date changes (e.g., 3 means 3:00 AM)
     * @return Calendar instance set to the end of day with offset applied
     */
    fun getEndOfDayWithOffset(timestamp: Long, offsetHours: Int): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Add one day, then add offset hours
        // If offset is +3, we want Jan 2 3:00 AM, so we set to Jan 2 00:00 and add 3 hours
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.HOUR_OF_DAY, offsetHours)
        // Subtract 1 millisecond to get the last moment of the day
        calendar.add(Calendar.MILLISECOND, -1)
        
        return calendar
    }
    
    /**
     * Gets the start of month with offset applied for a given timestamp.
     * 
     * @param timestamp The timestamp to get the start of month for
     * @param offsetHours Hours to add before the date changes
     * @return Timestamp of the start of month with offset applied
     */
    fun getStartOfMonthWithOffset(timestamp: Long, offsetHours: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.HOUR_OF_DAY, offsetHours)
        return calendar.timeInMillis
    }
    
    /**
     * Gets the end of month with offset applied for a given timestamp.
     * 
     * @param timestamp The timestamp to get the end of month for
     * @param offsetHours Hours to add before the date changes
     * @return Timestamp of the end of month with offset applied
     */
    fun getEndOfMonthWithOffset(timestamp: Long, offsetHours: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.HOUR_OF_DAY, offsetHours)
        calendar.add(Calendar.MILLISECOND, -1)
        return calendar.timeInMillis
    }
}
