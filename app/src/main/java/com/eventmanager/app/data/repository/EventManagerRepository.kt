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

    // Batch guest operations for optimized sync
    suspend fun insertGuestsAll(guests: List<Guest>): List<Long> = guestDao.insertGuestsAll(guests)
    suspend fun updateGuestsAll(guests: List<Guest>) = guestDao.updateGuestsAll(guests)
    suspend fun deleteGuestsAll(guests: List<Guest>) = guestDao.deleteGuestsAll(guests)

    // Volunteer-benefit guest helpers
    suspend fun getVolunteerBenefitGuests(): List<Guest> = guestDao.getVolunteerBenefitGuests()

    // Volunteer operations
    fun getAllActiveVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllActiveVolunteers()
    fun getAllVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllVolunteers()
    suspend fun getVolunteerById(id: Long): Volunteer? = volunteerDao.getVolunteerById(id)
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
    suspend fun updateVolunteerStatus(id: Long, isActive: Boolean) = volunteerDao.updateVolunteerStatus(id, isActive)

    // Batch volunteer operations for optimized sync
    suspend fun insertVolunteersAll(volunteers: List<Volunteer>): List<Long> = volunteerDao.insertVolunteersAll(volunteers)
    suspend fun updateVolunteersAll(volunteers: List<Volunteer>) = volunteerDao.updateVolunteersAll(volunteers)
    suspend fun deleteVolunteersAll(volunteers: List<Volunteer>) = volunteerDao.deleteVolunteersAll(volunteers)

    // Job operations
    fun getAllJobs(): Flow<List<Job>> = jobDao.getAllJobs()
    suspend fun insertJob(job: Job): Long = jobDao.insertJob(job)
    suspend fun updateJob(job: Job) = jobDao.updateJob(job)
    suspend fun deleteJob(job: Job) = jobDao.deleteJob(job)

    // Batch job operations for optimized sync
    suspend fun insertJobsAll(jobs: List<Job>): List<Long> = jobDao.insertJobsAll(jobs)
    suspend fun updateJobsAll(jobs: List<Job>) = jobDao.updateJobsAll(jobs)
    suspend fun deleteJobsAll(jobs: List<Job>) = jobDao.deleteJobsAll(jobs)

    // Job Type Config operations
    fun getAllJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllJobTypeConfigs()
    fun getAllActiveJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllActiveJobTypeConfigs()
    suspend fun insertJobTypeConfig(config: JobTypeConfig): Long = jobTypeConfigDao.insertJobTypeConfig(config)
    suspend fun updateJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.updateJobTypeConfig(config)
    suspend fun deleteJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.deleteJobTypeConfig(config)

    // Batch job type config operations for optimized sync
    suspend fun insertJobTypeConfigsAll(configs: List<JobTypeConfig>): List<Long> = jobTypeConfigDao.insertJobTypeConfigsAll(configs)
    suspend fun updateJobTypeConfigsAll(configs: List<JobTypeConfig>) = jobTypeConfigDao.updateJobTypeConfigsAll(configs)
    suspend fun deleteJobTypeConfigsAll(configs: List<JobTypeConfig>) = jobTypeConfigDao.deleteJobTypeConfigsAll(configs)

    // Venue operations
    fun getAllVenues(): Flow<List<VenueEntity>> = venueDao.getAllVenues()
    suspend fun insertVenue(venue: VenueEntity): Long = venueDao.insertVenue(venue)
    suspend fun updateVenue(venue: VenueEntity) = venueDao.updateVenue(venue)
    suspend fun deleteVenue(venue: VenueEntity) = venueDao.deleteVenue(venue)
    suspend fun updateVenueStatus(id: Long, isActive: Boolean) = venueDao.updateVenueStatus(id, isActive)
    suspend fun clearAllVenues() = venueDao.deleteAllVenues()

    // Batch venue operations for optimized sync
    suspend fun insertVenuesAll(venues: List<VenueEntity>): List<Long> = venueDao.insertVenuesAll(venues)
    suspend fun updateVenuesAll(venues: List<VenueEntity>) = venueDao.updateVenuesAll(venues)
    suspend fun deleteVenuesAll(venues: List<VenueEntity>) = venueDao.deleteVenuesAll(venues)

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
        
        // OPTIMIZED: Use pre-computed calculation context to avoid repeated filtering
        // This creates lookups and date ranges once, then reuses them for all volunteers
        val ctx = BenefitCalculator.CalculationContext(jobTypeConfigs)
        
        // OPTIMIZED: group jobs once to avoid O(volunteers * jobs) filtering
        val jobsByVolunteerId = jobs.groupBy { it.volunteerId }
        
        return volunteers.map { volunteer ->
            val volunteerJobs = jobsByVolunteerId[volunteer.id] ?: emptyList()
            BenefitCalculator.calculateWithContext(
                volunteer = volunteer,
                volunteerJobs = volunteerJobs,
                ctx = ctx
            )
        }
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
    
    suspend fun resetCounter() {
        val counter = CounterData(
            id = 1,
            count = 0,
            lastModified = System.currentTimeMillis()
        )
        counterDao.insertOrUpdateCounter(counter)
    }
}

