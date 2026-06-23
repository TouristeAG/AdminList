package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.Volunteer
import java.util.Calendar

object VolunteerActivityManager {
    private const val ACTIVE_THRESHOLD_YEARS = 1

    fun isVolunteerActive(volunteer: Volunteer): Boolean {
        val lastShiftDate = volunteer.lastShiftDate ?: return false
        val calendar = Calendar.getInstance()
        val oneYearAgo = calendar.apply {
            add(Calendar.YEAR, -ACTIVE_THRESHOLD_YEARS)
        }.timeInMillis
        return lastShiftDate >= oneYearAgo
    }

    fun getDaysSinceLastShift(volunteer: Volunteer): Long? {
        val lastShiftDate = volunteer.lastShiftDate ?: return null
        val currentTime = Calendar.getInstance().timeInMillis
        return (currentTime - lastShiftDate) / (1000 * 60 * 60 * 24)
    }

    fun getDaysSinceLastActivity(volunteer: Volunteer): Long? {
        val daysSinceLastShift = getDaysSinceLastShift(volunteer)
        if (daysSinceLastShift != null) return daysSinceLastShift
        val lastModified = volunteer.lastModified
        if (lastModified > 0) {
            val currentTime = Calendar.getInstance().timeInMillis
            return (currentTime - lastModified) / (1000 * 60 * 60 * 24)
        }
        return null
    }

    fun calculateActivityFromJobs(volunteer: Volunteer, allJobs: List<Job>): Volunteer {
        val volunteerJobs = allJobs.filter { it.volunteerId == volunteer.id }
        if (volunteerJobs.isEmpty()) {
            return volunteer.copy(lastShiftDate = null, isActive = false)
        }
        val mostRecentJobDate = volunteerJobs.maxOfOrNull { it.date } ?: 0L
        val isActive = isVolunteerActive(volunteer.copy(lastShiftDate = mostRecentJobDate))
        return volunteer.copy(lastShiftDate = mostRecentJobDate, isActive = isActive)
    }

    fun calculateActivityFromJobsMap(volunteer: Volunteer, jobsByVolunteerId: Map<String, List<Job>>): Volunteer {
        val volunteerJobs = jobsByVolunteerId[volunteer.id] ?: emptyList()
        if (volunteerJobs.isEmpty()) {
            return volunteer.copy(lastShiftDate = null, isActive = false)
        }
        val mostRecentJobDate = volunteerJobs.maxOfOrNull { it.date } ?: 0L
        val isActive = isVolunteerActive(volunteer.copy(lastShiftDate = mostRecentJobDate))
        return volunteer.copy(lastShiftDate = mostRecentJobDate, isActive = isActive)
    }

    fun groupJobsByVolunteerId(allJobs: List<Job>): Map<String, List<Job>> {
        return allJobs.groupBy { it.volunteerId }
    }

    fun updateVolunteerActivityFromJobs(volunteers: List<Volunteer>, allJobs: List<Job>): List<Volunteer> {
        val jobsByVolunteerId = groupJobsByVolunteerId(allJobs)
        return volunteers.map { volunteer ->
            calculateActivityFromJobsMap(volunteer, jobsByVolunteerId)
        }
    }
}
