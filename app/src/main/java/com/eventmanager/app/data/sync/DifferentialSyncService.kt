package com.eventmanager.app.data.sync

import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Differential Sync Service
 * 
 * This service implements efficient UI updates by comparing TEMP_DB (remote data)
 * with MAIN_DB (local data) and identifying only the changes.
 * 
 * Instead of refreshing the entire UI, we identify:
 * 1. New items in TEMP_DB
 * 2. Modified items (present in both with changes)
 * 3. Deleted items (in MAIN_DB but not in TEMP_DB)
 * 
 * This allows targeted UI updates instead of full-page reloads.
 */
class DifferentialSyncService(
    private val repository: EventManagerRepository
) {
    
    /**
     * Data class to hold sync changes for a specific entity type
     */
    data class SyncChanges<T>(
        val new: List<T> = emptyList(),
        val modified: List<T> = emptyList(),
        val deleted: List<T> = emptyList(),
        val unchanged: List<T> = emptyList()
    ) {
        val totalChanges: Int get() = new.size + modified.size + deleted.size
        val hasChanges: Boolean get() = totalChanges > 0
    }
    
    /**
     * Unified sync result for all data types
     */
    data class DifferentialSyncResult(
        val guests: SyncChanges<Guest> = SyncChanges(),
        val volunteers: SyncChanges<Volunteer> = SyncChanges(),
        val jobs: SyncChanges<Job> = SyncChanges(),
        val jobTypeConfigs: SyncChanges<JobTypeConfig> = SyncChanges(),
        val venues: SyncChanges<VenueEntity> = SyncChanges(),
        val syncTime: Long = System.currentTimeMillis()
    ) {
        fun hasAnyChanges(): Boolean =
            guests.hasChanges || volunteers.hasChanges || jobs.hasChanges || 
            jobTypeConfigs.hasChanges || venues.hasChanges
        
        fun summary(): String = buildString {
            append("Guests: ${guests.new.size} new, ${guests.modified.size} modified, ${guests.deleted.size} deleted")
            append(" | Volunteers: ${volunteers.new.size} new, ${volunteers.modified.size} modified, ${volunteers.deleted.size} deleted")
            append(" | Jobs: ${jobs.new.size} new, ${jobs.modified.size} modified, ${jobs.deleted.size} deleted")
            append(" | JobTypes: ${jobTypeConfigs.new.size} new, ${jobTypeConfigs.modified.size} modified, ${jobTypeConfigs.deleted.size} deleted")
            append(" | Venues: ${venues.new.size} new, ${venues.modified.size} modified, ${venues.deleted.size} deleted")
        }
    }
    
    // ========== GUEST COMPARISON ==========
    
    /**
     * Compare TEMP_DB guests with MAIN_DB guests
     */
    suspend fun compareGuests(tempGuests: List<Guest>, mainGuests: List<Guest>): SyncChanges<Guest> =
        withContext(Dispatchers.Default) {
            val mainMap = mainGuests.associateBy { it.sheetsId ?: "${it.name}_${it.venueName}_${it.invitations}" }
            val tempMap = tempGuests.associateBy { it.sheetsId ?: "${it.name}_${it.venueName}_${it.invitations}" }
            
            val new = mutableListOf<Guest>()
            val modified = mutableListOf<Guest>()
            val unchanged = mutableListOf<Guest>()
            
            // Find new and modified items in TEMP_DB
            for ((key, tempGuest) in tempMap) {
                val mainGuest = mainMap[key]
                if (mainGuest == null) {
                    new.add(tempGuest)
                } else if (hasGuestChanged(mainGuest, tempGuest)) {
                    modified.add(tempGuest)
                } else {
                    unchanged.add(tempGuest)
                }
            }
            
            // Find deleted items (in MAIN_DB but not in TEMP_DB)
            val deleted = mainGuests.filter { mainGuest ->
                val key = mainGuest.sheetsId ?: "${mainGuest.name}_${mainGuest.venueName}_${mainGuest.invitations}"
                !tempMap.containsKey(key)
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasGuestChanged(old: Guest, new: Guest): Boolean =
        old.name != new.name ||
        old.invitations != new.invitations ||
        old.venueName != new.venueName ||
        old.notes != new.notes ||
        old.isVolunteerBenefit != new.isVolunteerBenefit
    
    // ========== VOLUNTEER COMPARISON ==========
    
    /**
     * Compare TEMP_DB volunteers with MAIN_DB volunteers
     */
    suspend fun compareVolunteers(tempVolunteers: List<Volunteer>, mainVolunteers: List<Volunteer>): SyncChanges<Volunteer> =
        withContext(Dispatchers.Default) {
            val mainMap = mainVolunteers.associateBy { it.sheetsId ?: "${it.name}_${it.email}_${it.phoneNumber}" }
            val tempMap = tempVolunteers.associateBy { it.sheetsId ?: "${it.name}_${it.email}_${it.phoneNumber}" }
            
            val new = mutableListOf<Volunteer>()
            val modified = mutableListOf<Volunteer>()
            val unchanged = mutableListOf<Volunteer>()
            
            // Find new and modified items in TEMP_DB
            for ((key, tempVolunteer) in tempMap) {
                val mainVolunteer = mainMap[key]
                if (mainVolunteer == null) {
                    new.add(tempVolunteer)
                } else if (hasVolunteerChanged(mainVolunteer, tempVolunteer)) {
                    modified.add(tempVolunteer)
                } else {
                    unchanged.add(tempVolunteer)
                }
            }
            
            // Find deleted items (in MAIN_DB but not in TEMP_DB)
            val deleted = mainVolunteers.filter { mainVolunteer ->
                val key = mainVolunteer.sheetsId ?: "${mainVolunteer.name}_${mainVolunteer.email}_${mainVolunteer.phoneNumber}"
                !tempMap.containsKey(key)
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasVolunteerChanged(old: Volunteer, new: Volunteer): Boolean =
        old.name != new.name ||
        old.email != new.email ||
        old.phoneNumber != new.phoneNumber ||
        old.lastNameAbbreviation != new.lastNameAbbreviation ||
        old.dateOfBirth != new.dateOfBirth ||
        old.gender != new.gender ||
        old.currentRank != new.currentRank ||
        old.isActive != new.isActive ||
        old.lastShiftDate != new.lastShiftDate
    
    // ========== JOB COMPARISON ==========
    
    /**
     * Compare TEMP_DB jobs with MAIN_DB jobs
     */
    suspend fun compareJobs(tempJobs: List<Job>, mainJobs: List<Job>): SyncChanges<Job> =
        withContext(Dispatchers.Default) {
            val mainMap = mainJobs.associateBy { it.sheetsId ?: "${it.volunteerId}_${it.jobTypeName}_${it.date}_${it.venueName}_${it.shiftTime}" }
            val tempMap = tempJobs.associateBy { it.sheetsId ?: "${it.volunteerId}_${it.jobTypeName}_${it.date}_${it.venueName}_${it.shiftTime}" }
            
            val new = mutableListOf<Job>()
            val modified = mutableListOf<Job>()
            val unchanged = mutableListOf<Job>()
            
            // Find new and modified items in TEMP_DB
            for ((key, tempJob) in tempMap) {
                val mainJob = mainMap[key]
                if (mainJob == null) {
                    new.add(tempJob)
                } else if (hasJobChanged(mainJob, tempJob)) {
                    modified.add(tempJob)
                } else {
                    unchanged.add(tempJob)
                }
            }
            
            // Find deleted items (in MAIN_DB but not in TEMP_DB)
            val deleted = mainJobs.filter { mainJob ->
                val key = mainJob.sheetsId ?: "${mainJob.volunteerId}_${mainJob.jobTypeName}_${mainJob.date}_${mainJob.venueName}_${mainJob.shiftTime}"
                !tempMap.containsKey(key)
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasJobChanged(old: Job, new: Job): Boolean =
        old.volunteerId != new.volunteerId ||
        old.jobType != new.jobType ||
        old.jobTypeName != new.jobTypeName ||
        old.venueName != new.venueName ||
        old.date != new.date ||
        old.shiftTime != new.shiftTime ||
        old.notes != new.notes
    
    // ========== JOB TYPE CONFIG COMPARISON ==========
    
    /**
     * Compare TEMP_DB job type configs with MAIN_DB configs
     */
    suspend fun compareJobTypeConfigs(tempConfigs: List<JobTypeConfig>, mainConfigs: List<JobTypeConfig>): SyncChanges<JobTypeConfig> =
        withContext(Dispatchers.Default) {
            val mainMap = mainConfigs.associateBy { it.sheetsId ?: it.name }
            val tempMap = tempConfigs.associateBy { it.sheetsId ?: it.name }
            
            val new = mutableListOf<JobTypeConfig>()
            val modified = mutableListOf<JobTypeConfig>()
            val unchanged = mutableListOf<JobTypeConfig>()
            
            // Find new and modified items in TEMP_DB
            for ((key, tempConfig) in tempMap) {
                val mainConfig = mainMap[key]
                if (mainConfig == null) {
                    new.add(tempConfig)
                } else if (hasJobTypeConfigChanged(mainConfig, tempConfig)) {
                    modified.add(tempConfig)
                } else {
                    unchanged.add(tempConfig)
                }
            }
            
            // Find deleted items (in MAIN_DB but not in TEMP_DB)
            val deleted = mainConfigs.filter { mainConfig ->
                val key = mainConfig.sheetsId ?: mainConfig.name
                !tempMap.containsKey(key)
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasJobTypeConfigChanged(old: JobTypeConfig, new: JobTypeConfig): Boolean =
        old.name != new.name ||
        old.isActive != new.isActive ||
        old.isShiftJob != new.isShiftJob ||
        old.isOrionJob != new.isOrionJob ||
        old.requiresShiftTime != new.requiresShiftTime ||
        old.benefitSystemType != new.benefitSystemType ||
        old.manualRewards != new.manualRewards ||
        old.description != new.description
    
    // ========== VENUE COMPARISON ==========
    
    /**
     * Compare TEMP_DB venues with MAIN_DB venues
     */
    suspend fun compareVenues(tempVenues: List<VenueEntity>, mainVenues: List<VenueEntity>): SyncChanges<VenueEntity> =
        withContext(Dispatchers.Default) {
            val mainMap = mainVenues.associateBy { it.sheetsId ?: it.name }
            val tempMap = tempVenues.associateBy { it.sheetsId ?: it.name }
            
            val new = mutableListOf<VenueEntity>()
            val modified = mutableListOf<VenueEntity>()
            val unchanged = mutableListOf<VenueEntity>()
            
            // Find new and modified items in TEMP_DB
            for ((key, tempVenue) in tempMap) {
                val mainVenue = mainMap[key]
                if (mainVenue == null) {
                    new.add(tempVenue)
                } else if (hasVenueChanged(mainVenue, tempVenue)) {
                    modified.add(tempVenue)
                } else {
                    unchanged.add(tempVenue)
                }
            }
            
            // Find deleted items (in MAIN_DB but not in TEMP_DB)
            val deleted = mainVenues.filter { mainVenue ->
                val key = mainVenue.sheetsId ?: mainVenue.name
                !tempMap.containsKey(key)
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasVenueChanged(old: VenueEntity, new: VenueEntity): Boolean =
        old.name != new.name ||
        old.description != new.description ||
        old.isActive != new.isActive
    
    // ========== APPLY CHANGES ==========
    
    /**
     * Apply all changes to the database (merge TEMP_DB → MAIN_DB)
     */
    suspend fun applyChanges(result: DifferentialSyncResult) = withContext(Dispatchers.IO) {
        // Apply guest changes
        result.guests.new.forEach { repository.insertGuest(it) }
        result.guests.modified.forEach { repository.updateGuest(it) }
        result.guests.deleted.forEach { repository.deleteGuest(it) }
        
        // Apply volunteer changes
        result.volunteers.new.forEach { repository.insertVolunteer(it) }
        result.volunteers.modified.forEach { repository.updateVolunteer(it) }
        result.volunteers.deleted.forEach { repository.deleteVolunteer(it) }
        
        // Apply job changes
        result.jobs.new.forEach { repository.insertJob(it) }
        result.jobs.modified.forEach { repository.updateJob(it) }
        result.jobs.deleted.forEach { repository.deleteJob(it) }
        
        // Apply job type config changes
        result.jobTypeConfigs.new.forEach { repository.insertJobTypeConfig(it) }
        result.jobTypeConfigs.modified.forEach { repository.updateJobTypeConfig(it) }
        result.jobTypeConfigs.deleted.forEach { repository.deleteJobTypeConfig(it) }
        
        // Apply venue changes
        result.venues.new.forEach { repository.insertVenue(it) }
        result.venues.modified.forEach { repository.updateVenue(it) }
        result.venues.deleted.forEach { repository.deleteVenue(it) }
    }
}

