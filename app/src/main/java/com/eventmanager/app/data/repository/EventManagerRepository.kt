package com.eventmanager.app.data.repository

import com.eventmanager.app.data.dao.GuestDao
import com.eventmanager.app.data.dao.JobDao
import com.eventmanager.app.data.dao.JobTypeConfigDao
import com.eventmanager.app.data.dao.VenueDao
import com.eventmanager.app.data.dao.VolunteerDao
import com.eventmanager.app.data.dao.CounterDao
import com.eventmanager.app.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
    }
}

class EventManagerRepository(
    private val guestDao: GuestDao,
    private val volunteerDao: VolunteerDao,
    private val jobDao: JobDao,
    private val jobTypeConfigDao: JobTypeConfigDao,
    private val venueDao: VenueDao,
    private val counterDao: CounterDao
) {
    // Guest operations
    fun getAllGuests(): Flow<List<Guest>> = guestDao.getAllGuests()
    fun getGuestsByVenue(venueName: String): Flow<List<Guest>> = guestDao.getGuestsByVenue(venueName)
    suspend fun getGuestById(id: Long): Guest? = guestDao.getGuestById(id)
    suspend fun getGuestBySheetsId(sheetsId: String): Guest? = guestDao.getGuestBySheetsId(sheetsId)
    suspend fun insertGuest(guest: Guest): Long {
        // Check for duplicate names
        val existingGuest = guestDao.getGuestByName(guest.name)
        if (existingGuest != null) {
            throw IllegalArgumentException("A guest with the name '${guest.name}' already exists")
        }
        return guestDao.insertGuest(guest)
    }
    
    suspend fun updateGuest(guest: Guest) {
        // Check for duplicate names (excluding current guest)
        val existingGuest = guestDao.getGuestByName(guest.name)
        if (existingGuest != null && existingGuest.id != guest.id) {
            throw IllegalArgumentException("A guest with the name '${guest.name}' already exists")
        }
        guestDao.updateGuest(guest)
    }
    
    suspend fun deleteGuest(guest: Guest) = guestDao.deleteGuest(guest)
    suspend fun deleteGuestById(id: Long) = guestDao.deleteGuestById(id)

    // Volunteer-benefit guest helpers
    suspend fun getVolunteerBenefitGuests(): List<Guest> = guestDao.getVolunteerBenefitGuests()

    // Volunteer operations
    fun getAllActiveVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllActiveVolunteers()
    fun getAllVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllVolunteers()
    fun getInactiveVolunteers(): Flow<List<Volunteer>> = volunteerDao.getInactiveVolunteers()
    suspend fun getVolunteerById(id: Long): Volunteer? = volunteerDao.getVolunteerById(id)
    suspend fun getVolunteerBySheetsId(sheetsId: String): Volunteer? = volunteerDao.getVolunteerBySheetsId(sheetsId)
    fun getVolunteersByRank(rank: VolunteerRank): Flow<List<Volunteer>> = volunteerDao.getVolunteersByRank(rank)
    suspend fun insertVolunteer(volunteer: Volunteer): Long {
        // During sync, if a volunteer with the same name exists, update them instead of error
        val existingVolunteer = volunteerDao.getVolunteerByName(volunteer.name)
        return if (existingVolunteer != null) {
            // Update existing volunteer with new data while preserving ID
            val updated = volunteer.copy(
                id = existingVolunteer.id,
                sheetsId = volunteer.sheetsId ?: existingVolunteer.sheetsId
            )
            volunteerDao.updateVolunteer(updated)
            existingVolunteer.id
        } else {
            volunteerDao.insertVolunteer(volunteer)
        }
    }
    
    suspend fun updateVolunteer(volunteer: Volunteer) {
        // Check for duplicate names (excluding current volunteer)
        val existingVolunteer = volunteerDao.getVolunteerByName(volunteer.name)
        if (existingVolunteer != null && existingVolunteer.id != volunteer.id) {
            throw IllegalArgumentException("A volunteer with the name '${volunteer.name}' already exists")
        }
        volunteerDao.updateVolunteer(volunteer)
    }
    
    suspend fun deleteVolunteer(volunteer: Volunteer) = volunteerDao.deleteVolunteer(volunteer)
    suspend fun deleteVolunteerById(id: Long) = volunteerDao.deleteVolunteerById(id)
    suspend fun updateVolunteerStatus(id: Long, isActive: Boolean) = volunteerDao.updateVolunteerStatus(id, isActive)

    // Job operations
    fun getAllJobs(): Flow<List<Job>> = jobDao.getAllJobs()
    fun getJobsByVolunteer(volunteerId: Long): Flow<List<Job>> = jobDao.getJobsByVolunteer(volunteerId)
    fun getJobsByVenue(venueName: String): Flow<List<Job>> = jobDao.getJobsByVenue(venueName)
    suspend fun getJobById(id: Long): Job? = jobDao.getJobById(id)
    suspend fun getJobBySheetsId(sheetsId: String): Job? = jobDao.getJobBySheetsId(sheetsId)
    fun getJobsByDateRange(startDate: Long, endDate: Long): Flow<List<Job>> = jobDao.getJobsByDateRange(startDate, endDate)
    suspend fun insertJob(job: Job): Long = jobDao.insertJob(job)
    suspend fun updateJob(job: Job) = jobDao.updateJob(job)
    suspend fun deleteJob(job: Job) = jobDao.deleteJob(job)
    suspend fun deleteJobById(id: Long) = jobDao.deleteJobById(id)

    // Job Type Config operations
    fun getAllJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllJobTypeConfigs()
    fun getAllActiveJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllActiveJobTypeConfigs()
    suspend fun getJobTypeConfigById(id: Long): JobTypeConfig? = jobTypeConfigDao.getJobTypeConfigById(id)
    suspend fun getJobTypeConfigByName(name: String): JobTypeConfig? = jobTypeConfigDao.getJobTypeConfigByName(name)
    suspend fun insertJobTypeConfig(config: JobTypeConfig): Long = jobTypeConfigDao.insertJobTypeConfig(config)
    suspend fun updateJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.updateJobTypeConfig(config)
    suspend fun deleteJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.deleteJobTypeConfig(config)
    suspend fun deleteJobTypeConfigById(id: Long) = jobTypeConfigDao.deleteJobTypeConfigById(id)
    suspend fun updateJobTypeConfigStatus(id: Long, isActive: Boolean) = jobTypeConfigDao.updateJobTypeConfigStatus(id, isActive)
    fun getShiftJobTypes(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getShiftJobTypes()
    fun getOrionJobTypes(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getOrionJobTypes()

    // Venue operations
    fun getAllVenues(): Flow<List<VenueEntity>> = venueDao.getAllVenues()
    fun getAllActiveVenues(): Flow<List<VenueEntity>> = venueDao.getAllActiveVenues()
    suspend fun getVenueById(id: Long): VenueEntity? = venueDao.getVenueById(id)
    suspend fun getVenueByName(name: String): VenueEntity? = venueDao.getVenueByName(name)
    suspend fun insertVenue(venue: VenueEntity): Long = venueDao.insertVenue(venue)
    suspend fun updateVenue(venue: VenueEntity) = venueDao.updateVenue(venue)
    suspend fun deleteVenue(venue: VenueEntity) = venueDao.deleteVenue(venue)
    suspend fun deleteVenueById(id: Long) = venueDao.deleteVenueById(id)
    suspend fun updateVenueStatus(id: Long, isActive: Boolean) = venueDao.updateVenueStatus(id, isActive)
    suspend fun clearAllVenues() = venueDao.deleteAllVenues()

    // Sync operations
    suspend fun getGuestsModifiedAfter(timestamp: Long): List<Guest> = guestDao.getGuestsModifiedAfter(timestamp)
    suspend fun getVolunteersModifiedAfter(timestamp: Long): List<Volunteer> = volunteerDao.getVolunteersModifiedAfter(timestamp)
    suspend fun getJobsModifiedAfter(timestamp: Long): List<Job> = jobDao.getJobsModifiedAfter(timestamp)

    // Get volunteer benefit status with time-based calculations
    suspend fun getVolunteerBenefitStatus(volunteerId: Long): VolunteerBenefitStatus? {
        val volunteer = getVolunteerById(volunteerId) ?: return null
        val jobs = getAllJobs().first()
        val jobTypeConfigs = getAllActiveJobTypeConfigs().first()
        return BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs)
    }
    
    // Get all volunteers with their current benefit status
    suspend fun getAllVolunteerBenefitStatuses(): List<VolunteerBenefitStatus> {
        val volunteers = getAllVolunteers().first() // Include both active and inactive volunteers
        val jobs = getAllJobs().first()
        val jobTypeConfigs = getAllActiveJobTypeConfigs().first()
        return volunteers.map { volunteer ->
            BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs)
        }
    }
    
    // Legacy method for backward compatibility
    suspend fun calculateVolunteerRank(volunteerId: Long): VolunteerRank {
        val status = getVolunteerBenefitStatus(volunteerId)
        return status?.rank ?: VolunteerRank.NOVA
    }

    // Update volunteer rank based on job history
    suspend fun updateVolunteerRank(volunteerId: Long) {
        val newRank = calculateVolunteerRank(volunteerId)
        val volunteer = getVolunteerById(volunteerId)
        volunteer?.let {
            updateVolunteer(it.copy(currentRank = newRank))
        }
    }

    // Get volunteers with guest list access (VETERAN rank)
    suspend fun getVolunteersWithGuestListAccess(): List<Volunteer> {
        return volunteerDao.getVolunteersByRank(VolunteerRank.VETERAN).first()
    }

    // Clear all data (for clean sync)
    suspend fun clearAllData() {
        guestDao.deleteAllGuests()
        volunteerDao.deleteAllVolunteers()
        jobDao.deleteAllJobs()
        jobTypeConfigDao.deleteAllJobTypeConfigs()
        venueDao.deleteAllVenues()
    }
    
    suspend fun clearAllGuests() {
        guestDao.deleteAllGuests()
    }
    
    suspend fun clearAllVolunteers() {
        volunteerDao.deleteAllVolunteers()
    }
    
    suspend fun clearAllJobs() {
        jobDao.deleteAllJobs()
    }
    
    suspend fun clearAllJobTypeConfigs() {
        jobTypeConfigDao.deleteAllJobTypeConfigs()
    }
    
    // Update volunteer activity status based on last job date
    suspend fun updateVolunteerActivityStatus() {
        val currentTime = System.currentTimeMillis()
        val oneYearAgo = currentTime - (365L * 24 * 60 * 60 * 1000) // 1 year in milliseconds
        
        val allVolunteers = getAllVolunteers().first()
        val allJobs = getAllJobs().first()
        
        for (volunteer in allVolunteers) {
            val volunteerJobs = allJobs.filter { it.volunteerId == volunteer.id }
            val lastJobDate = volunteerJobs.maxOfOrNull { it.date } ?: 0L
            
            val shouldBeActive = lastJobDate > oneYearAgo
            
            // Only update if status needs to change
            if (volunteer.isActive != shouldBeActive) {
                updateVolunteerStatus(volunteer.id, shouldBeActive)
                println("Volunteer '${volunteer.name}' status changed to ${if (shouldBeActive) "active" else "inactive"} (last job: ${if (lastJobDate > 0) java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(lastJobDate)) else "never"})")
            }
        }
    }
    
    // Get volunteers who haven't been active for a specified period
    suspend fun getVolunteersInactiveSince(daysAgo: Int): List<Volunteer> {
        val currentTime = System.currentTimeMillis()
        val thresholdTime = currentTime - (daysAgo * 24L * 60 * 60 * 1000)
        
        val allVolunteers = getAllVolunteers().first()
        val allJobs = getAllJobs().first()
        
        return allVolunteers.filter { volunteer ->
            val volunteerJobs = allJobs.filter { it.volunteerId == volunteer.id }
            val lastJobDate = volunteerJobs.maxOfOrNull { it.date } ?: 0L
            lastJobDate < thresholdTime
        }
    }
    
    // Sync volunteer benefits to guest list with expiry handling
    suspend fun syncVolunteerBenefitsToGuestList() {
        val volunteerStatuses = getAllVolunteerBenefitStatuses()
        val currentTime = System.currentTimeMillis()
        
        // Remove expired volunteer benefits from guest list
        val expiredVolunteerGuests = guestDao.getVolunteerBenefitGuests()
        for (guest in expiredVolunteerGuests) {
            if (guest.volunteerId != null) {
                val volunteerStatus = volunteerStatuses.find { it.volunteerId == guest.volunteerId }
                if (volunteerStatus == null || !volunteerStatus.benefits.isActive || 
                    !volunteerStatus.benefits.guestListAccess ||
                    (volunteerStatus.benefits.validUntil != null && currentTime >= volunteerStatus.benefits.validUntil)) {
                    // Remove expired benefit guest
                    deleteGuest(guest)
                    println("Removed expired volunteer guest: ${guest.name}")
                }
            }
        }
        
        // Add active volunteer benefits to guest list per-rank invitation logic
        // All volunteers with guestListAccess get added to guest list
        for (status in volunteerStatuses) {
            if (status.benefits.isActive && 
                status.benefits.guestListAccess &&
                (status.benefits.validUntil == null || currentTime < status.benefits.validUntil)) {
                
                val volunteer = getVolunteerById(status.volunteerId)
                if (volunteer != null) {
                    // Use actual benefit information instead of hardcoded values
                    val invitations = status.benefits.inviteCount
                    // Check if guest already exists
                    val existingGuest = guestDao.getVolunteerBenefitGuest(volunteer.id)
                    if (existingGuest == null) {
                        // Add new volunteer benefit guest
                        val guest = Guest(
                            name = volunteer.name,
                            lastNameAbbreviation = volunteer.lastNameAbbreviation,
                            invitations = invitations,
                            venueName = "BOTH", // Volunteers can access both venues
                            notes = "Volunteer benefit - ${getRankDisplayName(status.rank)}",
                            isVolunteerBenefit = true,
                            volunteerId = volunteer.id
                        )
                        insertGuest(guest)
                        println("Added volunteer to guest list: ${volunteer.name} (${getRankDisplayName(status.rank)})")
                    } else {
                        // Update existing guest if rank changed
                        if (existingGuest.notes != "Volunteer benefit - ${getRankDisplayName(status.rank)}" || existingGuest.invitations != invitations) {
                            val updatedGuest = existingGuest.copy(
                                notes = "Volunteer benefit - ${getRankDisplayName(status.rank)}",
                                invitations = invitations,
                                lastModified = currentTime
                            )
                            updateGuest(updatedGuest)
                            println("Updated volunteer guest list entry: ${volunteer.name} (${getRankDisplayName(status.rank)})")
                        }
                    }
                }
            }
        }
    }
    
    // Counter operations
    fun getCounter(): Flow<CounterData?> = counterDao.getCounter()
    
    suspend fun getCounterOnce(): CounterData? = counterDao.getCounterOnce()
    
    suspend fun updateCounter(count: Int) {
        val counter = CounterData(
            id = 1,
            count = count,
            lastModified = System.currentTimeMillis()
        )
        counterDao.insertOrUpdateCounter(counter)
    }
    
    suspend fun incrementCounter(): Int {
        val currentCounter = counterDao.getCounterOnce() ?: CounterData(id = 1, count = 0)
        val newCount = currentCounter.count + 1
        val newCounter = currentCounter.copy(
            count = newCount,
            lastModified = System.currentTimeMillis()
        )
        counterDao.insertOrUpdateCounter(newCounter)
        return newCount
    }
    
    suspend fun decrementCounter(): Int {
        val currentCounter = counterDao.getCounterOnce() ?: CounterData(id = 1, count = 0)
        val newCount = maxOf(0, currentCounter.count - 1)
        val newCounter = currentCounter.copy(
            count = newCount,
            lastModified = System.currentTimeMillis()
        )
        counterDao.insertOrUpdateCounter(newCounter)
        return newCount
    }
    
    suspend fun resetCounter() {
        val counter = CounterData(
            id = 1,
            count = 0,
            lastModified = System.currentTimeMillis()
        )
        counterDao.insertOrUpdateCounter(counter)
    }
}

