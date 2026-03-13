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
        old.email != new.email ||
        old.phoneNumber != new.phoneNumber ||
        old.invitations != new.invitations ||
        old.venueName != new.venueName ||
        old.notes != new.notes ||
        old.isVolunteerBenefit != new.isVolunteerBenefit
    
    // ========== VOLUNTEER COMPARISON ==========
    
    /**
     * Compare TEMP_DB volunteers with MAIN_DB volunteers
     * Uses multiple matching strategies to handle volunteers with or without sheetsId
     */
    suspend fun compareVolunteers(tempVolunteers: List<Volunteer>, mainVolunteers: List<Volunteer>): SyncChanges<Volunteer> =
        withContext(Dispatchers.Default) {
            // Create multiple lookup maps for flexible matching
            val mainBySheetsId = mainVolunteers.filter { it.sheetsId != null }.associateBy { it.sheetsId!! }
            // Use name + abbreviation as key to allow multiple volunteers with same first name
            val mainByFullName = mainVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}" }
            val mainByCompositeKey = mainVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}_${it.email}_${it.phoneNumber}" }
            
            val new = mutableListOf<Volunteer>()
            val modified = mutableListOf<Volunteer>()
            val unchanged = mutableListOf<Volunteer>()
            val matchedMainIds = mutableSetOf<String>() // Track which main volunteers were matched
            
            // Find new and modified items in TEMP_DB (remote volunteers)
            for (tempVolunteer in tempVolunteers) {
                // Try to find matching main (local) volunteer using multiple strategies:
                // 1. Match by sheetsId first (most reliable)
                // 2. Then try matching by name+abbreviation (handles case where local has no sheetsId yet)
                // 3. Finally try composite key as fallback
                val mainVolunteer = tempVolunteer.sheetsId?.let { mainBySheetsId[it] }
                    ?: mainByFullName["${tempVolunteer.name}_${tempVolunteer.lastNameAbbreviation}"]
                    ?: mainByCompositeKey["${tempVolunteer.name}_${tempVolunteer.lastNameAbbreviation}_${tempVolunteer.email}_${tempVolunteer.phoneNumber}"]
                
                if (mainVolunteer == null) {
                    // No matching local volunteer found - this is new from sheets
                    new.add(tempVolunteer)
                } else {
                    // Mark this local volunteer as matched
                    matchedMainIds.add(mainVolunteer.id)
                    
                    if (hasVolunteerChanged(mainVolunteer, tempVolunteer)) {
                        // Volunteer exists locally but has changes (including NanoID changes)
                        modified.add(tempVolunteer)
                    } else {
                        unchanged.add(tempVolunteer)
                    }
                }
            }
            
            // Find deleted items (in MAIN_DB but not matched by any TEMP_DB volunteer)
            // Check by name+abbreviation to avoid incorrectly marking as deleted if sheetsId changed
            val tempFullNames = tempVolunteers.map { "${it.name}_${it.lastNameAbbreviation}" }.toSet()
            val deleted = mainVolunteers.filter { mainVolunteer ->
                !matchedMainIds.contains(mainVolunteer.id) && 
                !tempFullNames.contains("${mainVolunteer.name}_${mainVolunteer.lastNameAbbreviation}")
            }
            
            SyncChanges(new, modified, deleted, unchanged)
        }
    
    private fun hasVolunteerChanged(old: Volunteer, new: Volunteer): Boolean =
        old.id != new.id || // NanoID change - Google Sheets is source of truth
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
        old.benefitUsed != new.benefitUsed ||
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
     * Apply all changes to the database using batch operations
     * This is significantly faster for large datasets as it reduces database transaction overhead
     */
    suspend fun applyChangesBatched(result: DifferentialSyncResult) = withContext(Dispatchers.IO) {
        // Apply guest changes (batch operations)
        if (result.guests.new.isNotEmpty()) {
            repository.insertGuestsAll(result.guests.new)
        }
        if (result.guests.modified.isNotEmpty()) {
            repository.updateGuestsAll(result.guests.modified)
        }
        if (result.guests.deleted.isNotEmpty()) {
            repository.deleteGuestsAll(result.guests.deleted)
        }
        
        // Apply volunteer changes (batch operations)
        if (result.volunteers.new.isNotEmpty()) {
            repository.insertVolunteersAll(result.volunteers.new)
        }
        if (result.volunteers.modified.isNotEmpty()) {
            repository.updateVolunteersAll(result.volunteers.modified)
        }
        if (result.volunteers.deleted.isNotEmpty()) {
            repository.deleteVolunteersAll(result.volunteers.deleted)
        }
        
        // Apply job changes (batch operations)
        if (result.jobs.new.isNotEmpty()) {
            repository.insertJobsAll(result.jobs.new)
        }
        if (result.jobs.modified.isNotEmpty()) {
            repository.updateJobsAll(result.jobs.modified)
        }
        if (result.jobs.deleted.isNotEmpty()) {
            repository.deleteJobsAll(result.jobs.deleted)
        }
        
        // Apply job type config changes (batch operations)
        if (result.jobTypeConfigs.new.isNotEmpty()) {
            repository.insertJobTypeConfigsAll(result.jobTypeConfigs.new)
        }
        if (result.jobTypeConfigs.modified.isNotEmpty()) {
            repository.updateJobTypeConfigsAll(result.jobTypeConfigs.modified)
        }
        if (result.jobTypeConfigs.deleted.isNotEmpty()) {
            repository.deleteJobTypeConfigsAll(result.jobTypeConfigs.deleted)
        }
        
        // Apply venue changes (batch operations)
        if (result.venues.new.isNotEmpty()) {
            repository.insertVenuesAll(result.venues.new)
        }
        if (result.venues.modified.isNotEmpty()) {
            repository.updateVenuesAll(result.venues.modified)
        }
        if (result.venues.deleted.isNotEmpty()) {
            repository.deleteVenuesAll(result.venues.deleted)
        }
    }
}

