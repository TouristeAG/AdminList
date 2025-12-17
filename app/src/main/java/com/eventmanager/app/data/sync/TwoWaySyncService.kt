package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Two-way sync service implementing the new sync rules:
 * 1. Backup Mode: Local Changes → Google Sheets (overwrite entire dataset)
 * 2. Sync Mode: Google Sheets → App (download and replace local data)
 * 3. Page Change Sync: Download current + new page only
 * 4. Manual/Scheduled Sync: Download entire dataset
 * 5. No merge logic - simple overwrite behavior
 * 6. Differential Sync: Efficient UI updates via data comparison
 */
class TwoWaySyncService(
    private val context: Context,
    private val repository: EventManagerRepository,
    private val googleSheetsService: GoogleSheetsService
) {
    
    private val settingsManager = SettingsManager(context)
    private val differentialSyncService = DifferentialSyncService(repository)
    
    // Simple synchronization to prevent concurrent backup operations
    @Volatile
    private var isBackingUp = false
    
    // Global mutex to serialize Google Sheets operations across pages/features
    private val sheetsOpMutex = Mutex()
    
    /**
     * BACKUP MODE: Upload entire local dataset to Google Sheets
     * This overwrites the corresponding Google Sheet tab completely
     */
    suspend fun backupToGoogleSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            // Get all local data
            val guests = repository.getAllGuests().first()
            val volunteers = repository.getAllVolunteers().first() // Get ALL volunteers (active and inactive)
            val jobs = repository.getAllJobs().first()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val venues = repository.getAllVenues().first()
            
            println("Starting backup to Google Sheets...")
            println("Backing up: ${guests.size} guests, ${volunteers.size} volunteers, ${jobs.size} jobs, ${jobTypeConfigs.size} job types, ${venues.size} venues")
            
            // Upload each dataset completely (overwrites entire sheet)
            googleSheetsService.syncJobTypeConfigsToSheets(jobTypeConfigs)
            googleSheetsService.syncGuestsToSheets(guests, venues)
            googleSheetsService.syncVolunteersToSheets(volunteers)
            googleSheetsService.syncJobsToSheets(jobs, venues)
            googleSheetsService.syncVenuesToSheets(venues)
            
            // Update last sync time
            updateLastSyncTime()
            
            println("Backup to Google Sheets completed successfully")
            
        } catch (e: Exception) {
            println("Backup to Google Sheets failed: ${e.message}")
            throw IOException("Backup failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * SYNC MODE: Download entire dataset from Google Sheets and replace local data
     * This is used for manual sync and scheduled sync
     */
    suspend fun syncFromGoogleSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            println("Starting sync from Google Sheets...")
            
            // Download all data from sheets
            val remoteJobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
            val remoteGuests = googleSheetsService.syncGuestsFromSheets()
            val remoteVolunteers = googleSheetsService.syncVolunteersFromSheets()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            val remoteVenues = googleSheetsService.syncVenuesFromSheets()
            
            println("Downloaded from sheets: ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues")
            
            // Safety check: Only replace local data if we have remote data to prevent data loss
            val hasRemoteData = remoteGuests.isNotEmpty() || remoteVolunteers.isNotEmpty() || 
                               remoteJobs.isNotEmpty() || remoteJobTypeConfigs.isNotEmpty() || remoteVenues.isNotEmpty()
            
            if (!hasRemoteData) {
                println("⚠️ No data found in Google Sheets - keeping existing local data")
                println("This might be a first-time setup or the sheets are empty.")
                return@withContext
            }
            
            println("📥 Remote data found - merging with local data...")
            
            // Merge job type configs
            repository.clearAllJobTypeConfigs()
            for (config in remoteJobTypeConfigs) {
                repository.insertJobTypeConfig(config)
            }
            
            // Merge venues
            repository.clearAllVenues()
            for (venue in remoteVenues) {
                repository.insertVenue(venue)
            }
            
            // Merge guests
            repository.clearAllGuests()
            for (guest in remoteGuests) {
                repository.insertGuest(guest)
            }
            
            // Merge volunteers (preserve local volunteers not in remote data)
            val localVolunteers = repository.getAllVolunteers().first()
            val remoteVolunteersMap = remoteVolunteers.associateBy { it.sheetsId }
            
            // Update or insert remote volunteers
            for (volunteer in remoteVolunteers) {
                val existingVolunteer = localVolunteers.find { it.sheetsId == volunteer.sheetsId }
                if (existingVolunteer != null) {
                    repository.updateVolunteer(volunteer)
                } else {
                    repository.insertVolunteer(volunteer)
                }
            }
            
            // Keep local volunteers that don't exist in remote data
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                localVolunteer.sheetsId == null || remoteVolunteersMap[localVolunteer.sheetsId] == null
            }
            
            // Re-insert local volunteers that weren't in remote data
            for (volunteer in localVolunteersToKeep) {
                try {
                    repository.insertVolunteer(volunteer)
                    println("Preserved local volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                } catch (e: Exception) {
                    println("Failed to preserve local volunteer ${volunteer.name}: ${e.message}")
                }
            }
            
            println("Preserved ${localVolunteersToKeep.size} local volunteers not found in remote data")
            
            // Merge jobs
            repository.clearAllJobs()
            for (job in remoteJobs) {
                repository.insertJob(job)
            }
            
            println("✅ Successfully replaced local data with ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types from Google Sheets")
            
            // Update last sync time
            updateLastSyncTime()
            
            println("Sync from Google Sheets completed successfully")
            
        } catch (e: Exception) {
            println("Sync from Google Sheets failed: ${e.message}")
            throw IOException("Sync failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * DIFFERENTIAL SYNC MODE: Download from Google Sheets and update only what changed
     * This is the new efficient sync that avoids full-page UI reloads
     * 
     * Steps:
     * 1. Download all remote data from Google Sheets (TEMP_DB)
     * 2. Get current local data (MAIN_DB)
     * 3. Compare TEMP_DB vs MAIN_DB to identify changes
     * 4. Return sync result with detailed change information
     * 5. UI will apply only the targeted updates
     * 
     * @return DifferentialSyncResult containing new, modified, and deleted items
     */
    suspend fun syncFromGoogleSheetsWithDifferentialUpdate(): DifferentialSyncService.DifferentialSyncResult = 
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            println("🔄 Starting differential sync from Google Sheets...")
            
            // STEP 1: Download all data from sheets (TEMP_DB)
            val remoteJobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
            val remoteGuests = googleSheetsService.syncGuestsFromSheets()
            val remoteVolunteers = googleSheetsService.syncVolunteersFromSheets()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            val remoteVenues = googleSheetsService.syncVenuesFromSheets()
            
            println("📥 Downloaded from sheets: ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues")
            
            // Safety check: Only proceed if we have remote data to prevent data loss
            val hasRemoteData = remoteGuests.isNotEmpty() || remoteVolunteers.isNotEmpty() || 
                               remoteJobs.isNotEmpty() || remoteJobTypeConfigs.isNotEmpty() || remoteVenues.isNotEmpty()
            
            if (!hasRemoteData) {
                println("⚠️ No data found in Google Sheets - returning empty differential result")
                return@withContext DifferentialSyncService.DifferentialSyncResult()
            }
            
            // STEP 2: Get current local data (MAIN_DB)
            val mainGuests = repository.getAllGuests().first()
            val mainVolunteers = repository.getAllVolunteers().first()
            val mainJobs = repository.getAllJobs().first()
            val mainJobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val mainVenues = repository.getAllVenues().first()
            
            println("📊 Current local data: ${mainGuests.size} guests, ${mainVolunteers.size} volunteers, ${mainJobs.size} jobs, ${mainJobTypeConfigs.size} job types, ${mainVenues.size} venues")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val guestChanges = differentialSyncService.compareGuests(remoteGuests, mainGuests)
            val volunteerChanges = differentialSyncService.compareVolunteers(remoteVolunteers, mainVolunteers)
            val jobChanges = differentialSyncService.compareJobs(remoteJobs, mainJobs)
            val jobTypeChanges = differentialSyncService.compareJobTypeConfigs(remoteJobTypeConfigs, mainJobTypeConfigs)
            val venueChanges = differentialSyncService.compareVenues(remoteVenues, mainVenues)
            
            // STEP 4: Build result with detailed change information
            val result = DifferentialSyncService.DifferentialSyncResult(
                guests = guestChanges,
                volunteers = volunteerChanges,
                jobs = jobChanges,
                jobTypeConfigs = jobTypeChanges,
                venues = venueChanges,
                syncTime = System.currentTimeMillis()
            )
            
            println("📋 Changes detected: ${result.summary()}")
            
            // STEP 5: Apply changes to database (merge TEMP_DB → MAIN_DB)
            if (result.hasAnyChanges()) {
                differentialSyncService.applyChanges(result)
                println("✅ Applied ${result.guests.totalChanges + result.volunteers.totalChanges + result.jobs.totalChanges + result.jobTypeConfigs.totalChanges + result.venues.totalChanges} changes to local database")
            } else {
                println("ℹ️ No changes detected - data is already in sync")
            }
            
            // Update last sync time
            updateLastSyncTime()
            
            println("✅ Differential sync completed successfully")
            
            // Return result for UI to use for targeted updates
            result
            
        } catch (e: Exception) {
            println("❌ Differential sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential sync failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * PAGE CHANGE SYNC: Download only current page and new page data
     * This is used when user changes pages in the app
     */
    suspend fun syncPageChange(currentPage: String, newPage: String) = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            println("Starting page change sync: $currentPage → $newPage")
            
            // Determine which datasets to sync based on page
            val pagesToSync = setOf(currentPage, newPage)
            
            if (pagesToSync.contains("guests") || pagesToSync.contains("guest_list")) {
                syncGuestsOnly()
            }
            
            if (pagesToSync.contains("volunteers") || pagesToSync.contains("volunteer_list")) {
                syncVolunteersOnly()
            }
            
            if (pagesToSync.contains("jobs") || pagesToSync.contains("job_list")) {
                syncJobsOnly()
            }
            
            if (pagesToSync.contains("job_types") || pagesToSync.contains("job_type_configs")) {
                syncJobTypesOnly()
            }
            
            println("Page change sync completed successfully")
            
        } catch (e: Exception) {
            println("Page change sync failed: ${e.message}")
            throw IOException("Page change sync failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * SYNC SPECIFIC DATASET: Download and replace specific dataset only
     */
    suspend fun syncGuestsOnly() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            val remoteGuests = googleSheetsService.syncGuestsFromSheets()
            repository.clearAllGuests()
            for (guest in remoteGuests) {
                repository.insertGuest(guest)
            }
            println("Synced ${remoteGuests.size} guests")
        } catch (e: Exception) {
            println("Failed to sync guests: ${e.message}")
            throw e
        }
        }
    }
    
    /**
     * DIFFERENTIAL GUEST SYNC: Download guests and update only what changed
     * This is efficient UI update for the guest page - only changed guests are updated
     */
    suspend fun syncGuestsWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<Guest> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential guest sync from Google Sheets...")
            
            // STEP 1: Download guests from sheets (TEMP_DB)
            val remoteGuests = googleSheetsService.syncGuestsFromSheets()
            println("📥 Downloaded ${remoteGuests.size} guests from sheets")
            
            // STEP 2: Get current local guests (MAIN_DB)
            val mainGuests = repository.getAllGuests().first()
            println("📊 Current local data: ${mainGuests.size} guests")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val guestChanges = differentialSyncService.compareGuests(remoteGuests, mainGuests)
            println("📋 Changes detected: ${guestChanges.new.size} new, ${guestChanges.modified.size} modified, ${guestChanges.deleted.size} deleted")
            
            // STEP 4: Apply changes to database
            if (guestChanges.hasChanges) {
                guestChanges.new.forEach { repository.insertGuest(it) }
                guestChanges.modified.forEach { repository.updateGuest(it) }
                guestChanges.deleted.forEach { repository.deleteGuest(it) }
                println("✅ Applied ${guestChanges.totalChanges} guest changes to database")
            } else {
                println("ℹ️ No guest changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential guest sync completed successfully")
            
            // Return changes for UI to apply targeted updates
            guestChanges
            
        } catch (e: Exception) {
            println("❌ Differential guest sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential guest sync failed: ${e.message}", e)
        }
        }
    }
    
    suspend fun syncVolunteersOnly() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("Starting volunteer sync from Google Sheets...")
            val remoteVolunteers = googleSheetsService.syncVolunteersFromSheets()
            println("Downloaded ${remoteVolunteers.size} volunteers from Google Sheets")
            
            // Get existing local volunteers to preserve any that aren't in remote data
            val localVolunteers = repository.getAllVolunteers().first()
            println("Found ${localVolunteers.size} local volunteers")
            
            // Create a map of remote volunteers by sheetsId for quick lookup
            val remoteVolunteersMap = remoteVolunteers.associateBy { it.sheetsId }
            
            // Update or insert remote volunteers
            for (volunteer in remoteVolunteers) {
                try {
                    val existingVolunteer = localVolunteers.find { it.sheetsId == volunteer.sheetsId }
                    if (existingVolunteer != null) {
                        // Update existing volunteer
                        repository.updateVolunteer(volunteer)
                        println("Updated volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    } else {
                        // Insert new volunteer
                        repository.insertVolunteer(volunteer)
                        println("Inserted new volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    }
                } catch (e: Exception) {
                    println("Failed to sync volunteer ${volunteer.name}: ${e.message}")
                    // Continue with other volunteers even if one fails
                }
            }
            
            // Keep local volunteers that don't exist in remote data (preserve inactive volunteers)
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                localVolunteer.sheetsId == null || remoteVolunteersMap[localVolunteer.sheetsId] == null
            }
            
            // Re-insert local volunteers that weren't in remote data
            for (volunteer in localVolunteersToKeep) {
                try {
                    repository.insertVolunteer(volunteer)
                    println("Preserved local volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                } catch (e: Exception) {
                    println("Failed to preserve local volunteer ${volunteer.name}: ${e.message}")
                }
            }
            
            println("Preserved ${localVolunteersToKeep.size} local volunteers not found in remote data")
            
            println("Successfully synced volunteers from Google Sheets (${remoteVolunteers.size} remote, ${localVolunteersToKeep.size} preserved local)")
        } catch (e: Exception) {
            println("Failed to sync volunteers: ${e.message}")
            e.printStackTrace()
            throw e
        }
        }
    }
    
    /**
     * DIFFERENTIAL VOLUNTEER SYNC: Download volunteers and update only what changed
     * This is efficient UI update for the volunteer page - only changed volunteers are updated
     */
    suspend fun syncVolunteersWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<Volunteer> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential volunteer sync from Google Sheets...")
            
            // STEP 1: Download volunteers from sheets (TEMP_DB)
            val remoteVolunteers = googleSheetsService.syncVolunteersFromSheets()
            println("📥 Downloaded ${remoteVolunteers.size} volunteers from sheets")
            
            // STEP 2: Get current local volunteers (MAIN_DB)
            val mainVolunteers = repository.getAllVolunteers().first()
            println("📊 Current local data: ${mainVolunteers.size} volunteers")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val volunteerChanges = differentialSyncService.compareVolunteers(remoteVolunteers, mainVolunteers)
            println("📋 Changes detected: ${volunteerChanges.new.size} new, ${volunteerChanges.modified.size} modified, ${volunteerChanges.deleted.size} deleted")
            
            // STEP 4: Apply changes to database
            if (volunteerChanges.hasChanges) {
                volunteerChanges.new.forEach { repository.insertVolunteer(it) }
                volunteerChanges.modified.forEach { repository.updateVolunteer(it) }
                volunteerChanges.deleted.forEach { repository.deleteVolunteer(it) }
                println("✅ Applied ${volunteerChanges.totalChanges} volunteer changes to database")
            } else {
                println("ℹ️ No volunteer changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential volunteer sync completed successfully")
            
            // Return changes for UI to apply targeted updates
            volunteerChanges
            
        } catch (e: Exception) {
            println("❌ Differential volunteer sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential volunteer sync failed: ${e.message}", e)
        }
        }
    }
    
    suspend fun syncJobsOnly() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            val remoteJobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            repository.clearAllJobs()
            for (job in remoteJobs) {
                repository.insertJob(job)
            }
            println("Synced ${remoteJobs.size} jobs")
        } catch (e: Exception) {
            println("Failed to sync jobs: ${e.message}")
            throw e
        }
        }
    }
    
    /**
     * DIFFERENTIAL JOB SYNC: Download jobs and update only what changed
     * This is efficient UI update for the jobs/shifts page - only changed jobs are updated
     */
    suspend fun syncJobsWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<Job> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential job sync from Google Sheets...")
            
            // STEP 1: Download jobs from sheets (TEMP_DB)
            val remoteJobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            println("📥 Downloaded ${remoteJobs.size} jobs from sheets")
            
            // STEP 2: Get current local jobs (MAIN_DB)
            val mainJobs = repository.getAllJobs().first()
            println("📊 Current local data: ${mainJobs.size} jobs")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val jobChanges = differentialSyncService.compareJobs(remoteJobs, mainJobs)
            println("📋 Changes detected: ${jobChanges.new.size} new, ${jobChanges.modified.size} modified, ${jobChanges.deleted.size} deleted")
            
            // STEP 4: Apply changes to database
            if (jobChanges.hasChanges) {
                jobChanges.new.forEach { repository.insertJob(it) }
                jobChanges.modified.forEach { repository.updateJob(it) }
                jobChanges.deleted.forEach { repository.deleteJob(it) }
                println("✅ Applied ${jobChanges.totalChanges} job changes to database")
            } else {
                println("ℹ️ No job changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential job sync completed successfully")
            
            // Return changes for UI to apply targeted updates
            jobChanges
            
        } catch (e: Exception) {
            println("❌ Differential job sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential job sync failed: ${e.message}", e)
        }
        }
    }
    
    suspend fun syncJobTypesOnly() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            val remoteJobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
            repository.clearAllJobTypeConfigs()
            for (config in remoteJobTypeConfigs) {
                repository.insertJobTypeConfig(config)
            }
            println("Synced ${remoteJobTypeConfigs.size} job types")
        } catch (e: Exception) {
            println("Failed to sync job types: ${e.message}")
            throw e
        }
        }
    }
    
    /**
     * DIFFERENTIAL JOB TYPE SYNC: Download job types and update only what changed
     * This is efficient UI update for the job types settings page - only changed types are updated
     */
    suspend fun syncJobTypesWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<JobTypeConfig> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential job type sync from Google Sheets...")
            
            // STEP 1: Download job types from sheets (TEMP_DB)
            val remoteJobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
            println("📥 Downloaded ${remoteJobTypeConfigs.size} job types from sheets")
            
            // STEP 2: Get current local job types (MAIN_DB)
            val mainJobTypeConfigs = repository.getAllJobTypeConfigs().first()
            println("📊 Current local data: ${mainJobTypeConfigs.size} job types")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val jobTypeChanges = differentialSyncService.compareJobTypeConfigs(remoteJobTypeConfigs, mainJobTypeConfigs)
            println("📋 Changes detected: ${jobTypeChanges.new.size} new, ${jobTypeChanges.modified.size} modified, ${jobTypeChanges.deleted.size} deleted")
            
            // STEP 4: Apply changes to database
            if (jobTypeChanges.hasChanges) {
                jobTypeChanges.new.forEach { repository.insertJobTypeConfig(it) }
                jobTypeChanges.modified.forEach { repository.updateJobTypeConfig(it) }
                jobTypeChanges.deleted.forEach { repository.deleteJobTypeConfig(it) }
                println("✅ Applied ${jobTypeChanges.totalChanges} job type changes to database")
            } else {
                println("ℹ️ No job type changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential job type sync completed successfully")
            
            // Return changes for UI to apply targeted updates
            jobTypeChanges
            
        } catch (e: Exception) {
            println("❌ Differential job type sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential job type sync failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * DIFFERENTIAL VENUE SYNC: Download venues and update only what changed
     * This is efficient UI update for the venues settings page - only changed venues are updated
     */
    suspend fun syncVenuesWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<VenueEntity> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential venue sync from Google Sheets...")
            
            // STEP 1: Download venues from sheets (TEMP_DB)
            val remoteVenues = googleSheetsService.syncVenuesFromSheets()
            println("📥 Downloaded ${remoteVenues.size} venues from sheets")
            
            // STEP 2: Get current local venues (MAIN_DB)
            val mainVenues = repository.getAllVenues().first()
            println("📊 Current local data: ${mainVenues.size} venues")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB
            val venueChanges = differentialSyncService.compareVenues(remoteVenues, mainVenues)
            println("📋 Changes detected: ${venueChanges.new.size} new, ${venueChanges.modified.size} modified, ${venueChanges.deleted.size} deleted")
            
            // STEP 4: Apply changes to database
            if (venueChanges.hasChanges) {
                venueChanges.new.forEach { repository.insertVenue(it) }
                venueChanges.modified.forEach { repository.updateVenue(it) }
                venueChanges.deleted.forEach { repository.deleteVenue(it) }
                println("✅ Applied ${venueChanges.totalChanges} venue changes to database")
            } else {
                println("ℹ️ No venue changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential venue sync completed successfully")
            
            // Return changes for UI to apply targeted updates
            venueChanges
            
        } catch (e: Exception) {
            println("❌ Differential venue sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential venue sync failed: ${e.message}", e)
        }
        }
    }
    
    /**
     * BACKUP SPECIFIC DATASET: Upload specific dataset to Google Sheets
     * This is used when user makes changes to specific data
     */
    suspend fun backupGuestsToSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            val guests = repository.getAllGuests().first()
            val venues = repository.getAllVenues().first()
            println("📊 Retrieved ${guests.size} guests from repository for backup")
            
            // Log guest details for debugging
            guests.forEachIndexed { index, guest ->
                println("  Guest ${index + 1}: ${guest.name} (ID: ${guest.id})")
            }
            
            googleSheetsService.syncGuestsToSheets(guests, venues)
            println("✅ Backed up ${guests.size} guests to Google Sheets")
        } catch (e: Exception) {
            println("❌ Failed to backup guests: ${e.message}")
            throw e
        }
        }
    }
    
    suspend fun backupVolunteersToSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            // Get ALL volunteers (both active and inactive) to ensure complete backup
            val volunteers = repository.getAllVolunteers().first()
            println("📊 Retrieved ${volunteers.size} volunteers from repository for backup")
            
            // Log volunteer details for debugging
            volunteers.forEachIndexed { index, volunteer ->
                println("  Volunteer ${index + 1}: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
            }
            
            googleSheetsService.syncVolunteersToSheets(volunteers)
            println("✅ Backed up ${volunteers.size} volunteers to Google Sheets")
        } catch (e: Exception) {
            println("❌ Failed to backup volunteers: ${e.message}")
            throw e
        }
        }
    }
    
    suspend fun backupJobsToSheets() = withContext(Dispatchers.IO) {
        // Prevent concurrent backup operations
        if (isBackingUp) {
            println("⚠️ Backup already in progress, skipping duplicate backup request")
            return@withContext
        }
        
        try {
            isBackingUp = true
            
            sheetsOpMutex.withLock {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            println("Starting backup of jobs to Google Sheets...")
            googleSheetsService.initializeSheetsService()
            val jobs = repository.getAllJobs().first()
            val venues = repository.getAllVenues().first()
            println("📊 Retrieved ${jobs.size} jobs from repository for backup")
            
            // Log job details for debugging
            jobs.forEachIndexed { index, job ->
                println("  Job ${index + 1}: ${job.jobTypeName} (ID: ${job.id}, Volunteer: ${job.volunteerId})")
            }
            
            // Add a small delay to prevent rapid successive calls
            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncJobsToSheets(jobs, venues)
            println("✅ Successfully backed up ${jobs.size} jobs to Google Sheets")
            }
        } catch (e: Exception) {
            println("❌ Failed to backup jobs: ${e.message}")
            throw e
        } finally {
            isBackingUp = false
        }
    }
    
    suspend fun backupJobTypesToSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            println("Starting backup of job types to Google Sheets...")
            googleSheetsService.initializeSheetsService()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            println("📊 Retrieved ${jobTypeConfigs.size} job types from repository for backup")
            
            // Log job type details for debugging
            jobTypeConfigs.forEachIndexed { index, config ->
                println("  Job Type ${index + 1}: ${config.name} (ID: ${config.id}, Active: ${config.isActive})")
            }
            
            // Add a small delay to prevent rapid successive calls
            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncJobTypeConfigsToSheets(jobTypeConfigs)
            println("✅ Successfully backed up ${jobTypeConfigs.size} job types to Google Sheets")
        } catch (e: Exception) {
            println("❌ Failed to backup job types: ${e.message}")
            throw e
        }
        }
    }
    
    suspend fun backupVenuesToSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            println("Starting backup of venues to Google Sheets...")
            googleSheetsService.initializeSheetsService()
            val venues = repository.getAllVenues().first()
            println("📊 Retrieved ${venues.size} venues from repository for backup")
            
            // Log venue details for debugging
            venues.forEachIndexed { index, venue ->
                println("  Venue ${index + 1}: ${venue.name} (ID: ${venue.id}, Active: ${venue.isActive})")
            }
            
            // Add a small delay to prevent rapid successive calls
            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncVenuesToSheets(venues)
            println("✅ Successfully backed up ${venues.size} venues to Google Sheets")
        } catch (e: Exception) {
            println("❌ Failed to backup venues: ${e.message}")
            throw e
        }
        }
    }
    
    /**
     * UTILITY METHODS
     */
    private fun isGoogleSheetsConfigured(): Boolean {
        return settingsManager.isConfigured()
    }
    
    private suspend fun updateLastSyncTime() {
        val currentTime = System.currentTimeMillis()
        settingsManager.saveLastSyncTime(currentTime)
    }
    
    suspend fun getLastSyncTime(): Long {
        return settingsManager.getLastSyncTime()
    }
    
    /**
     * VALIDATION: Ensure Google Sheets has correct structure
     */
    suspend fun validateGoogleSheetsStructure(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            if (!isGoogleSheetsConfigured()) {
                return@withContext mapOf("error" to "Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            val diagnostics = mutableMapOf<String, Any>()
            
            // Test each sheet structure
            try {
                val guests = googleSheetsService.syncGuestsFromSheets()
                diagnostics["guests"] = mapOf(
                    "status" to "OK",
                    "count" to guests.size,
                    "headers" to listOf("Name", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified")
                )
            } catch (e: Exception) {
                diagnostics["guests"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            try {
                val volunteers = googleSheetsService.syncVolunteersFromSheets()
                diagnostics["volunteers"] = mapOf(
                    "status" to "OK",
                    "count" to volunteers.size,
                    "headers" to listOf("ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth", "Rank", "Active", "Last Modified")
                )
            } catch (e: Exception) {
                diagnostics["volunteers"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            try {
                val jobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
                val jobs = googleSheetsService.syncJobsFromSheets(jobTypeConfigs)
                diagnostics["jobs"] = mapOf(
                    "status" to "OK",
                    "count" to jobs.size,
                    "headers" to listOf("Volunteer ID", "Job Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified")
                )
            } catch (e: Exception) {
                diagnostics["jobs"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            try {
                val jobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
                diagnostics["job_types"] = mapOf(
                    "status" to "OK",
                    "count" to jobTypeConfigs.size,
                    "headers" to listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Description", "Last Modified")
                )
            } catch (e: Exception) {
                diagnostics["job_types"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            diagnostics
            
        } catch (e: Exception) {
            mapOf("error" to "Validation failed: ${e.message}")
        }
    }
}
