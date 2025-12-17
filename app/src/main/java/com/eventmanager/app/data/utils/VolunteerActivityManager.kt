package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.Job
import java.util.Calendar

object VolunteerActivityManager {
    
    // Constants for activity tracking
    private const val ACTIVE_THRESHOLD_YEARS = 1
    private const val CLEANUP_THRESHOLD_YEARS = 4
    
    /**
     * Determines if a volunteer is considered active based on their last shift date
     * A volunteer is active if they have done a shift within the last year
     */
    fun isVolunteerActive(volunteer: Volunteer): Boolean {
        val lastShiftDate = volunteer.lastShiftDate ?: return false
        
        val calendar = Calendar.getInstance()
        val oneYearAgo = calendar.apply {
            add(Calendar.YEAR, -ACTIVE_THRESHOLD_YEARS)
        }.timeInMillis
        
        return lastShiftDate >= oneYearAgo
    }
    
    /**
     * Determines if a volunteer should be cleaned up (deleted)
     * A volunteer should be cleaned up if they haven't done a shift in 4+ years
     */
    fun shouldCleanupVolunteer(volunteer: Volunteer): Boolean {
        val lastShiftDate = volunteer.lastShiftDate ?: return false
        
        val calendar = Calendar.getInstance()
        val fourYearsAgo = calendar.apply {
            add(Calendar.YEAR, -CLEANUP_THRESHOLD_YEARS)
        }.timeInMillis
        
        return lastShiftDate < fourYearsAgo
    }
    
    /**
     * Updates a volunteer's last shift date to the current time
     */
    fun updateLastShiftDate(volunteer: Volunteer): Volunteer {
        return volunteer.copy(lastShiftDate = System.currentTimeMillis())
    }
    
    /**
     * Gets the number of days since the volunteer's last shift
     * Returns null if they have never done a shift
     */
    fun getDaysSinceLastShift(volunteer: Volunteer): Long? {
        val lastShiftDate = volunteer.lastShiftDate ?: return null
        
        val calendar = Calendar.getInstance()
        val currentTime = calendar.timeInMillis
        
        return (currentTime - lastShiftDate) / (1000 * 60 * 60 * 24) // Convert to days
    }
    
    /**
     * Gets a human-readable string describing the volunteer's activity status
     */
    fun getActivityStatusText(volunteer: Volunteer): String {
        val daysSince = getDaysSinceLastShift(volunteer) ?: return "Never worked"
        
        return when {
            daysSince == 0L -> "Active (today)"
            daysSince < 7 -> "Active (${daysSince} days ago)"
            daysSince < 30 -> "Active (${daysSince} days ago)"
            daysSince < 365 -> "Active (${daysSince / 30} months ago)"
            else -> "Inactive (${daysSince / 365} years ago)"
        }
    }
    
    /**
     * Calculates volunteer activity based on actual job assignments
     * This is more accurate than relying on lastShiftDate field
     */
    fun calculateActivityFromJobs(volunteer: Volunteer, allJobs: List<Job>): Volunteer {
        val volunteerJobs = allJobs.filter { it.volunteerId == volunteer.id }
        
        if (volunteerJobs.isEmpty()) {
            return volunteer.copy(lastShiftDate = null, isActive = false)
        }
        
        // Find the most recent job date
        val mostRecentJobDate = volunteerJobs.maxOfOrNull { it.date } ?: 0L
        val isActive = isVolunteerActive(volunteer.copy(lastShiftDate = mostRecentJobDate))
        
        return volunteer.copy(lastShiftDate = mostRecentJobDate, isActive = isActive)
    }
    
    /**
     * Updates all volunteers' activity status based on job assignments
     */
    fun updateVolunteerActivityFromJobs(volunteers: List<Volunteer>, allJobs: List<Job>): List<Volunteer> {
        return volunteers.map { volunteer ->
            calculateActivityFromJobs(volunteer, allJobs)
        }
    }
}
