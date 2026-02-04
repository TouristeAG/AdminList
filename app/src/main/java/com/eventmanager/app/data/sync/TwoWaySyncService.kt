package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    context: Context,
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
     * 
     * OPTIMIZED: Uses parallel API calls for downloading data and batch database operations
     */
    suspend fun syncFromGoogleSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            println("Starting sync from Google Sheets...")
            
            // OPTIMIZATION: Download data in parallel (except jobs which depends on jobTypeConfigs)
            val (remoteJobTypeConfigs, remoteGuests, remoteVolunteers, remoteVenues) = coroutineScope {
                val jobTypeConfigsDeferred = async { googleSheetsService.syncJobTypeConfigsFromSheets() }
                val guestsDeferred = async { googleSheetsService.syncGuestsFromSheets() }
                val volunteersDeferred = async { googleSheetsService.syncVolunteersFromSheets() }
                val venuesDeferred = async { googleSheetsService.syncVenuesFromSheets() }
                
                // Await all parallel downloads
                val jobTypeConfigs = jobTypeConfigsDeferred.await()
                val guests = guestsDeferred.await()
                val volunteers = volunteersDeferred.await()
                val venues = venuesDeferred.await()
                
                Quad(jobTypeConfigs, guests, volunteers, venues)
            }
            
            // Jobs depend on job type configs, so download after
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            
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
            
            // OPTIMIZATION: Use batch database operations instead of individual inserts
            
            // Merge job type configs (batch insert)
            repository.clearAllJobTypeConfigs()
            if (remoteJobTypeConfigs.isNotEmpty()) {
                repository.insertJobTypeConfigsAll(remoteJobTypeConfigs)
            }
            
            // Merge venues (batch insert)
            repository.clearAllVenues()
            if (remoteVenues.isNotEmpty()) {
                repository.insertVenuesAll(remoteVenues)
            }
            
            // Merge guests (batch insert)
            repository.clearAllGuests()
            if (remoteGuests.isNotEmpty()) {
                repository.insertGuestsAll(remoteGuests)
            }
            
            // Merge volunteers (preserve local volunteers not in remote data)
            // Google Sheets is the source of truth for NanoIDs
            val localVolunteers = repository.getAllVolunteers().first()
            val remoteVolunteersMap = remoteVolunteers.associateBy { it.sheetsId }
            val localVolunteersBySheetsId = localVolunteers.associateBy { it.sheetsId }
            val localVolunteersByName = localVolunteers.associateBy { it.name }
            
            // Process each remote volunteer - Google Sheets NanoID is source of truth
            for (volunteer in remoteVolunteers) {
                // Try to find existing volunteer by sheetsId first, then by name
                val existingVolunteer = localVolunteersBySheetsId[volunteer.sheetsId]
                    ?: localVolunteersByName[volunteer.name]
                
                if (existingVolunteer != null) {
                    if (existingVolunteer.id != volunteer.id) {
                        // NanoID changed - Google Sheets has the correct ID
                        // Update all jobs that reference the old NanoID to use the new one
                        println("🔄 Volunteer '${volunteer.name}' NanoID changed: '${existingVolunteer.id}' → '${volunteer.id}' (adopting Google Sheets ID)")
                        repository.updateJobsVolunteerId(existingVolunteer.id, volunteer.id)
                        
                        // Delete old record and insert new one with correct NanoID
                        repository.deleteVolunteer(existingVolunteer)
                        repository.insertVolunteer(volunteer)
                    } else {
                        // Same NanoID - just update the data
                        repository.updateVolunteer(volunteer)
                    }
                } else {
                    // New volunteer from sheets - use the NanoID from sheets as-is
                    repository.insertVolunteer(volunteer)
                }
            }
            
            // Keep local volunteers that don't exist in remote data
            // Also check by name to avoid preserving volunteers that exist in sheets with different sheetsId
            val remoteVolunteerNames = remoteVolunteers.map { it.name }.toSet()
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                (localVolunteer.sheetsId == null || remoteVolunteersMap[localVolunteer.sheetsId] == null) &&
                !remoteVolunteerNames.contains(localVolunteer.name)
            }
            
            // Re-insert local volunteers that weren't in remote data (batch)
            if (localVolunteersToKeep.isNotEmpty()) {
                try {
                    repository.insertVolunteersAll(localVolunteersToKeep)
                    println("Preserved ${localVolunteersToKeep.size} local volunteers not found in remote data")
                } catch (e: Exception) {
                    println("Failed to preserve some local volunteers: ${e.message}")
                }
            }
            
            // Merge jobs (batch insert)
            repository.clearAllJobs()
            if (remoteJobs.isNotEmpty()) {
                repository.insertJobsAll(remoteJobs)
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
    
    // Helper class for parallel downloads (destructuring 4 values)
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    
    /**
     * DIFFERENTIAL SYNC MODE: Download from Google Sheets and update only what changed
     * This is the new efficient sync that avoids full-page UI reloads
     * 
     * Steps:
     * 1. Download all remote data from Google Sheets (TEMP_DB) - OPTIMIZED: parallel downloads
     * 2. Get current local data (MAIN_DB) - OPTIMIZED: parallel reads
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
            
            // STEP 1: Download all data from sheets (TEMP_DB) - OPTIMIZED: parallel downloads
            val (remoteJobTypeConfigs, remoteGuests, remoteVolunteers, remoteVenues) = coroutineScope {
                val jobTypeConfigsDeferred = async { googleSheetsService.syncJobTypeConfigsFromSheets() }
                val guestsDeferred = async { googleSheetsService.syncGuestsFromSheets() }
                val volunteersDeferred = async { googleSheetsService.syncVolunteersFromSheets() }
                val venuesDeferred = async { googleSheetsService.syncVenuesFromSheets() }
                
                Quad(
                    jobTypeConfigsDeferred.await(),
                    guestsDeferred.await(),
                    volunteersDeferred.await(),
                    venuesDeferred.await()
                )
            }
            
            // Jobs depend on job type configs, so download after
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs)
            
            println("📥 Downloaded from sheets: ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues")
            
            // Safety check: Only proceed if we have remote data to prevent data loss
            val hasRemoteData = remoteGuests.isNotEmpty() || remoteVolunteers.isNotEmpty() || 
                               remoteJobs.isNotEmpty() || remoteJobTypeConfigs.isNotEmpty() || remoteVenues.isNotEmpty()
            
            if (!hasRemoteData) {
                println("⚠️ No data found in Google Sheets - returning empty differential result")
                return@withContext DifferentialSyncService.DifferentialSyncResult()
            }
            
            // STEP 2: Get current local data (MAIN_DB) - OPTIMIZED: parallel reads
            val (mainGuests, mainVolunteers, mainJobs, mainJobTypeConfigs, mainVenues) = coroutineScope {
                val guestsDeferred = async { repository.getAllGuests().first() }
                val volunteersDeferred = async { repository.getAllVolunteers().first() }
                val jobsDeferred = async { repository.getAllJobs().first() }
                val jobTypeConfigsDeferred = async { repository.getAllJobTypeConfigs().first() }
                val venuesDeferred = async { repository.getAllVenues().first() }
                
                Quint(
                    guestsDeferred.await(),
                    volunteersDeferred.await(),
                    jobsDeferred.await(),
                    jobTypeConfigsDeferred.await(),
                    venuesDeferred.await()
                )
            }
            
            println("📊 Current local data: ${mainGuests.size} guests (${mainGuests.count { it.isVolunteerBenefit }} volunteer benefits), ${mainVolunteers.size} volunteers, ${mainJobs.size} jobs, ${mainJobTypeConfigs.size} job types, ${mainVenues.size} venues")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB - OPTIMIZED: parallel comparisons
            // CRITICAL: Exclude volunteer benefit guests from comparison - they're managed separately
            // and are not synced from Google Sheets (computed locally from volunteer ranks)
            val regularMainGuests = mainGuests.filter { !it.isVolunteerBenefit }
            
            val (guestChanges, volunteerChanges, jobChanges, jobTypeChanges, venueChanges) = coroutineScope {
                val guestChangesDeferred = async { differentialSyncService.compareGuests(remoteGuests, regularMainGuests) }
                val volunteerChangesDeferred = async { differentialSyncService.compareVolunteers(remoteVolunteers, mainVolunteers) }
                val jobChangesDeferred = async { differentialSyncService.compareJobs(remoteJobs, mainJobs) }
                val jobTypeChangesDeferred = async { differentialSyncService.compareJobTypeConfigs(remoteJobTypeConfigs, mainJobTypeConfigs) }
                val venueChangesDeferred = async { differentialSyncService.compareVenues(remoteVenues, mainVenues) }
                
                Quint(
                    guestChangesDeferred.await(),
                    volunteerChangesDeferred.await(),
                    jobChangesDeferred.await(),
                    jobTypeChangesDeferred.await(),
                    venueChangesDeferred.await()
                )
            }
            
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
            
            // STEP 5: Apply changes to database (merge TEMP_DB → MAIN_DB) - OPTIMIZED: batch operations
            if (result.hasAnyChanges()) {
                differentialSyncService.applyChangesBatched(result)
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
    
    // Helper class for parallel downloads (destructuring 5 values)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    
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
            // CRITICAL: Exclude volunteer benefit guests - they're managed separately
            val regularMainGuests = mainGuests.filter { !it.isVolunteerBenefit }
            println("📊 Current local data: ${mainGuests.size} guests (${mainGuests.size - regularMainGuests.size} volunteer benefits excluded from comparison)")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB (regular guests only)
            val guestChanges = differentialSyncService.compareGuests(remoteGuests, regularMainGuests)
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
            
            // Create maps for efficient lookup - Google Sheets is source of truth for NanoIDs
            val remoteVolunteersMap = remoteVolunteers.associateBy { it.sheetsId }
            val localVolunteersBySheetsId = localVolunteers.associateBy { it.sheetsId }
            val localVolunteersByName = localVolunteers.associateBy { it.name }
            
            // Update or insert remote volunteers - use NanoID from Google Sheets
            for (volunteer in remoteVolunteers) {
                try {
                    // Try to find existing volunteer by sheetsId first, then by name
                    val existingVolunteer = localVolunteersBySheetsId[volunteer.sheetsId]
                        ?: localVolunteersByName[volunteer.name]
                    
                    if (existingVolunteer != null) {
                        if (existingVolunteer.id != volunteer.id) {
                            // NanoID changed - Google Sheets has the correct ID
                            println("🔄 Volunteer '${volunteer.name}' NanoID changed: '${existingVolunteer.id}' → '${volunteer.id}' (adopting Google Sheets ID)")
                            repository.updateJobsVolunteerId(existingVolunteer.id, volunteer.id)
                            repository.deleteVolunteer(existingVolunteer)
                            repository.insertVolunteer(volunteer)
                        } else {
                            // Same NanoID - just update the data
                            repository.updateVolunteer(volunteer)
                        }
                        println("Updated volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    } else {
                        // New volunteer from sheets - use NanoID from sheets as-is
                        repository.insertVolunteer(volunteer)
                        println("Inserted new volunteer: ${volunteer.name} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    }
                } catch (e: Exception) {
                    println("Failed to sync volunteer ${volunteer.name}: ${e.message}")
                    // Continue with other volunteers even if one fails
                }
            }
            
            // Keep local volunteers that don't exist in remote data (preserve inactive volunteers)
            // Also check by name to avoid preserving volunteers that exist in sheets with different sheetsId
            val remoteVolunteerNames = remoteVolunteers.map { it.name }.toSet()
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                (localVolunteer.sheetsId == null || remoteVolunteersMap[localVolunteer.sheetsId] == null) &&
                !remoteVolunteerNames.contains(localVolunteer.name)
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
    
    private fun updateLastSyncTime() {
        val currentTime = System.currentTimeMillis()
        settingsManager.saveLastSyncTime(currentTime)
    }
    
    fun getLastSyncTime(): Long {
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
                    "headers" to listOf("Name", "Email", "Phone", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified")
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
