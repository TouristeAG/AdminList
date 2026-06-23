package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
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
 * Two-way sync service implementing the sync rules:
 * 1. Backup Mode: Local Changes → Google Sheets (read-merge-write to preserve other devices' data)
 * 2. Sync Mode: Google Sheets → App (download and replace local data)
 * 3. Page Change Sync: Download current + new page only
 * 4. Manual/Scheduled Sync: Download entire dataset
 * 5. Merge-before-upload: backups read the sheet first, merge remote-only entries from
 *    other devices using last-modified-wins, then upload the combined result.
 * 6. Differential Sync: Efficient UI updates via data comparison
 */
class TwoWaySyncService(
    platformContext: PlatformContext,
    private val repository: EventManagerRepository,
    private val googleSheetsService: GoogleSheetsService
) {
    
    private val settingsManager = SettingsManager(createAppStorage(platformContext))
    private val differentialSyncService = DifferentialSyncService(repository)
    private val deletionTracker = DeletionTracker(platformContext)
    
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
            
            // Validate and repair sheet structure before backup
            googleSheetsService.validateAndRepairSheetsStructure()
            
            // Get all local data
            val guests = repository.getAllGuests().first()
            val volunteers = repository.getAllVolunteers().first()
            val jobs = repository.getAllJobs().first()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val venues = repository.getAllVenues().first()
            val salesSheetItems = repository.getAllSalesSheetItems().first()
            
            println("Starting backup to Google Sheets...")
            println("Backing up: ${guests.size} guests, ${volunteers.size} volunteers, ${jobs.size} jobs, ${jobTypeConfigs.size} job types, ${venues.size} venues, ${salesSheetItems.size} sales items")
            
            // Read current remote data in parallel for merge
            val (remoteJobTypeConfigs, remoteGuests, remoteVolunteers, remoteVenues, remoteSalesSheetItems) = try {
                coroutineScope {
                    val jtcDef = async { try { googleSheetsService.syncJobTypeConfigsFromSheets() } catch (_: Exception) { emptyList() } }
                    val gDef = async { try { googleSheetsService.syncGuestsFromSheets() } catch (_: Exception) { emptyList() } }
                    val vDef = async { try { googleSheetsService.syncVolunteersFromSheets() } catch (_: Exception) { emptyList() } }
                    val vnDef = async { try { googleSheetsService.syncVenuesFromSheets() } catch (_: Exception) { emptyList() } }
                    val siDef = async { try { googleSheetsService.syncSalesSheetItemsFromSheets() } catch (_: Exception) { emptyList() } }
                    Quint(jtcDef.await(), gDef.await(), vDef.await(), vnDef.await(), siDef.await())
                }
            } catch (e: Exception) {
                println("⚠️ Could not read remote data for merge, uploading local only: ${e.message}")
                Quint(emptyList<JobTypeConfig>(), emptyList<Guest>(), emptyList<Volunteer>(), emptyList<VenueEntity>(), emptyList<SalesSheetItem>())
            }

            val remoteJobs = try {
                val configsForParsing = remoteJobTypeConfigs.ifEmpty { jobTypeConfigs }
                googleSheetsService.syncJobsFromSheets(configsForParsing, remoteVolunteers)
            } catch (_: Exception) { emptyList() }

            // Load deletion tracker keys for all entity types
            val delGuest = deletionTracker.getDeletedBusinessKeys("guest")
            val delVolunteer = deletionTracker.getDeletedBusinessKeys("volunteer")
            val delJob = deletionTracker.getDeletedBusinessKeys("job")
            val delJobType = deletionTracker.getDeletedBusinessKeys("job_type")
            val delVenue = deletionTracker.getDeletedBusinessKeys("venue")
            val delSalesItem = deletionTracker.getDeletedBusinessKeys("sales_item")

            // Merge each entity type
            val mergedJobTypeConfigs = mergeLocalWithRemote(
                jobTypeConfigs, remoteJobTypeConfigs, { it.name }, { it.lastModified }, delJobType
            )

            val localRegularGuests = guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }
            val remoteRegularGuests = remoteGuests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }
            val mergedRegularGuests = mergeLocalWithRemote(
                localRegularGuests, remoteRegularGuests, { it.nanoId }, { it.lastModified }, delGuest
            )
            val allGuestsForUpload = mergedRegularGuests + guests.filter { it.isVolunteerBenefit || it.isTemporaryGuest }

            val mergedVolunteers = mergeLocalWithRemote(
                volunteers, remoteVolunteers, { it.id }, { it.lastModified }, delVolunteer
            )

            val mergedJobs = mergeLocalWithRemote(
                jobs, remoteJobs,
                { "${it.volunteerId}_${it.jobTypeName}_${it.date}_${it.venueName}_${it.shiftTime}" },
                { it.lastModified }, delJob
            )

            val mergedVenues = mergeLocalWithRemote(
                venues, remoteVenues, { it.name }, { it.lastModified }, delVenue
            )
            val mergedSalesSheetItems = mergeLocalWithRemote(
                salesSheetItems, remoteSalesSheetItems, { it.name }, { it.lastModified }, delSalesItem
            )

            println("📊 After merge: ${mergedRegularGuests.size} guests, ${mergedVolunteers.size} volunteers, ${mergedJobs.size} jobs, ${mergedJobTypeConfigs.size} job types, ${mergedVenues.size} venues, ${mergedSalesSheetItems.size} sales items")
            
            // Upload merged datasets
            googleSheetsService.syncJobTypeConfigsToSheets(mergedJobTypeConfigs)
            googleSheetsService.syncGuestsToSheets(allGuestsForUpload, mergedVenues)
            val volunteerRanksForSheet = BenefitCalculator.volunteerPrimaryRanksForSheetUpload(
                mergedVolunteers, mergedJobs, mergedJobTypeConfigs
            )
            googleSheetsService.syncVolunteersToSheets(mergedVolunteers, volunteerRanksForSheet)
            googleSheetsService.syncJobsToSheets(mergedJobs, mergedVenues, mergedJobTypeConfigs, mergedVolunteers)
            googleSheetsService.syncVenuesToSheets(mergedVenues)
            googleSheetsService.syncSalesSheetItemsToSheets(mergedSalesSheetItems)

            // Reflect merged snapshot locally so UI updates without a second sync.
            applyMergedSnapshotToLocal(
                guests = allGuestsForUpload,
                volunteers = mergedVolunteers,
                jobs = mergedJobs,
                jobTypeConfigs = mergedJobTypeConfigs,
                venues = mergedVenues,
                salesSheetItems = mergedSalesSheetItems
            )

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
            
            // Validate and repair sheet structure before reading
            googleSheetsService.validateAndRepairSheetsStructure()
            
            println("Starting sync from Google Sheets...")
            
            // OPTIMIZATION: Download data in parallel (except jobs which depends on jobTypeConfigs)
            val (remoteJobTypeConfigs, remoteGuests, remoteVolunteers, remoteVenues, remoteSalesSheetItems) = coroutineScope {
                val jobTypeConfigsDeferred = async { googleSheetsService.syncJobTypeConfigsFromSheets() }
                val guestsDeferred = async { googleSheetsService.syncGuestsFromSheets() }
                val volunteersDeferred = async { googleSheetsService.syncVolunteersFromSheets() }
                val venuesDeferred = async { googleSheetsService.syncVenuesFromSheets() }
                val salesSheetItemsDeferred = async { googleSheetsService.syncSalesSheetItemsFromSheets() }
                
                // Await all parallel downloads
                val jobTypeConfigs = jobTypeConfigsDeferred.await()
                val guests = guestsDeferred.await()
                val volunteers = volunteersDeferred.await()
                val venues = venuesDeferred.await()
                
                Quint(jobTypeConfigs, guests, volunteers, venues, salesSheetItemsDeferred.await())
            }
            
            // Jobs depend on job type configs, so download after
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs, remoteVolunteers)
            
            println("Downloaded from sheets: ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues, ${remoteSalesSheetItems.size} sales items")
            
            // Safety check: Only replace local data if we have remote data to prevent data loss
            val hasRemoteData = remoteGuests.isNotEmpty() || remoteVolunteers.isNotEmpty() || 
                               remoteJobs.isNotEmpty() || remoteJobTypeConfigs.isNotEmpty() || remoteVenues.isNotEmpty() || remoteSalesSheetItems.isNotEmpty()
            
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

            // Merge sales sheet items (batch insert)
            repository.clearAllSalesSheetItems()
            if (remoteSalesSheetItems.isNotEmpty()) {
                repository.insertSalesSheetItemsAll(remoteSalesSheetItems)
            }
            
            // Merge guests (batch insert)
            repository.clearAllGuests()
            if (remoteGuests.isNotEmpty()) {
                repository.insertGuestsAll(remoteGuests)
            }
            
            // Merge volunteers (preserve local volunteers not in remote data)
            // Google Sheets is the source of truth for NanoIDs
            val localVolunteers = repository.getAllVolunteers().first()
            val remoteVolunteersById = remoteVolunteers.associateBy { it.id }
            val localVolunteersById = localVolunteers.associateBy { it.id }
            // Use name + abbreviation as key to allow multiple volunteers with same first name
            val localVolunteersByFullName = localVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}" }
            
            // Process each remote volunteer - Google Sheets NanoID is source of truth
            for (volunteer in remoteVolunteers) {
                // Try to find existing volunteer by NanoID first, then by name+abbreviation
                val existingVolunteer = localVolunteersById[volunteer.id]
                    ?: localVolunteersByFullName["${volunteer.name}_${volunteer.lastNameAbbreviation}"]
                
                if (existingVolunteer != null) {
                    if (existingVolunteer.id != volunteer.id) {
                        // NanoID changed - Google Sheets has the correct ID
                        // Update all jobs that reference the old NanoID to use the new one
                        println("🔄 Volunteer '${volunteer.name} ${volunteer.lastNameAbbreviation}' NanoID changed: '${existingVolunteer.id}' → '${volunteer.id}' (adopting Google Sheets ID)")
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
            // Check by NanoID and name+abbreviation to avoid preserving duplicates
            val remoteVolunteerFullNames = remoteVolunteers.map { "${it.name}_${it.lastNameAbbreviation}" }.toSet()
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                remoteVolunteersById[localVolunteer.id] == null &&
                !remoteVolunteerFullNames.contains("${localVolunteer.name}_${localVolunteer.lastNameAbbreviation}")
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
            
            println("✅ Successfully replaced local data with ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues and ${remoteSalesSheetItems.size} sales items from Google Sheets")
            
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
            
            // Validate and repair sheet structure before reading
            googleSheetsService.validateAndRepairSheetsStructure()
            
            println("🔄 Starting differential sync from Google Sheets...")
            
            // STEP 1: Download all data from sheets (TEMP_DB) - OPTIMIZED: parallel downloads
            val (remoteJobTypeConfigs, remoteGuests, remoteVolunteers, remoteVenues, remoteSalesSheetItems) = coroutineScope {
                val jobTypeConfigsDeferred = async { googleSheetsService.syncJobTypeConfigsFromSheets() }
                val guestsDeferred = async { googleSheetsService.syncGuestsFromSheets() }
                val volunteersDeferred = async { googleSheetsService.syncVolunteersFromSheets() }
                val venuesDeferred = async { googleSheetsService.syncVenuesFromSheets() }
                val salesItemsDeferred = async { googleSheetsService.syncSalesSheetItemsFromSheets() }
                
                Quint(
                    jobTypeConfigsDeferred.await(),
                    guestsDeferred.await(),
                    volunteersDeferred.await(),
                    venuesDeferred.await(),
                    salesItemsDeferred.await()
                )
            }
            
            // Jobs depend on job type configs, so download after
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs, remoteVolunteers)
            
            println("📥 Downloaded from sheets: ${remoteGuests.size} guests, ${remoteVolunteers.size} volunteers, ${remoteJobs.size} jobs, ${remoteJobTypeConfigs.size} job types, ${remoteVenues.size} venues, ${remoteSalesSheetItems.size} sales items")
            
            // Safety check: Only proceed if we have remote data to prevent data loss
            val hasRemoteData = remoteGuests.isNotEmpty() || remoteVolunteers.isNotEmpty() || 
                               remoteJobs.isNotEmpty() || remoteJobTypeConfigs.isNotEmpty() || remoteVenues.isNotEmpty() || remoteSalesSheetItems.isNotEmpty()
            
            if (!hasRemoteData) {
                println("⚠️ No data found in Google Sheets - returning empty differential result")
                return@withContext DifferentialSyncService.DifferentialSyncResult()
            }
            
            // STEP 2: Get current local data (MAIN_DB) - OPTIMIZED: parallel reads
            val (mainGuests, mainVolunteers, mainJobs, mainJobTypeConfigs, mainVenues, mainSalesSheetItems) = coroutineScope {
                val guestsDeferred = async { repository.getAllGuests().first() }
                val volunteersDeferred = async { repository.getAllVolunteers().first() }
                val jobsDeferred = async { repository.getAllJobs().first() }
                val jobTypeConfigsDeferred = async { repository.getAllJobTypeConfigs().first() }
                val venuesDeferred = async { repository.getAllVenues().first() }
                val salesItemsDeferred = async { repository.getAllSalesSheetItems().first() }
                
                Sext(
                    guestsDeferred.await(),
                    volunteersDeferred.await(),
                    jobsDeferred.await(),
                    jobTypeConfigsDeferred.await(),
                    venuesDeferred.await(),
                    salesItemsDeferred.await()
                )
            }
            
            println("📊 Current local data: ${mainGuests.size} guests (${mainGuests.count { it.isVolunteerBenefit }} volunteer benefits), ${mainVolunteers.size} volunteers, ${mainJobs.size} jobs, ${mainJobTypeConfigs.size} job types, ${mainVenues.size} venues, ${mainSalesSheetItems.size} sales items")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB - OPTIMIZED: parallel comparisons
            // CRITICAL: Exclude both volunteer benefits and temporary guests from comparison.
            // - Volunteer benefits are computed locally.
            // - Temporary guests come from a dedicated sheet and must not be deleted by regular guest sync.
            val regularMainGuests = mainGuests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }
            
            val (guestChanges, volunteerChanges, jobChanges, jobTypeChanges, venueChanges, salesItemChanges) = coroutineScope {
                val guestChangesDeferred = async { differentialSyncService.compareGuests(remoteGuests, regularMainGuests) }
                val volunteerChangesDeferred = async { differentialSyncService.compareVolunteers(remoteVolunteers, mainVolunteers) }
                val jobChangesDeferred = async { differentialSyncService.compareJobs(remoteJobs, mainJobs) }
                val jobTypeChangesDeferred = async { differentialSyncService.compareJobTypeConfigs(remoteJobTypeConfigs, mainJobTypeConfigs) }
                val venueChangesDeferred = async { differentialSyncService.compareVenues(remoteVenues, mainVenues) }
                val salesItemsChangesDeferred = async { differentialSyncService.compareSalesSheetItems(remoteSalesSheetItems, mainSalesSheetItems) }
                
                Sext(
                    guestChangesDeferred.await(),
                    volunteerChangesDeferred.await(),
                    jobChangesDeferred.await(),
                    jobTypeChangesDeferred.await(),
                    venueChangesDeferred.await(),
                    salesItemsChangesDeferred.await()
                )
            }
            
            // STEP 4: Build result with detailed change information
            val result = DifferentialSyncService.DifferentialSyncResult(
                guests = guestChanges,
                volunteers = volunteerChanges,
                jobs = jobChanges,
                jobTypeConfigs = jobTypeChanges,
                salesSheetItems = salesItemChanges,
                venues = venueChanges,
                syncTime = System.currentTimeMillis()
            )
            
            println("📋 Changes detected: ${result.summary()}")
            
            // STEP 5: Apply changes to database (merge TEMP_DB → MAIN_DB) - OPTIMIZED: batch operations
            if (result.hasAnyChanges()) {
                differentialSyncService.applyChangesBatched(result)
                println("✅ Applied ${result.guests.totalChanges + result.volunteers.totalChanges + result.jobs.totalChanges + result.jobTypeConfigs.totalChanges + result.venues.totalChanges + result.salesSheetItems.totalChanges} changes to local database")
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
    // Helper class for parallel downloads (destructuring 6 values)
    private data class Sext<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)

    /**
     * Merges local and remote entity lists for upload, using last-modified-wins semantics.
     * This prevents data loss when multiple devices make concurrent changes:
     * - Entities only in local: included (our changes)
     * - Entities only in remote AND not in [deletedKeys]: included (other device's additions)
     * - Entities only in remote AND in [deletedKeys]: excluded (deleted locally)
     * - Entities in both: the version with the newer lastModified wins
     */
    private fun <T> mergeLocalWithRemote(
        local: List<T>,
        remote: List<T>,
        keyExtractor: (T) -> String,
        lastModifiedExtractor: (T) -> Long,
        deletedKeys: Set<String> = emptySet()
    ): List<T> {
        if (remote.isEmpty()) return local

        val localByKey = LinkedHashMap<String, T>()
        for (item in local) localByKey[keyExtractor(item)] = item

        val remoteByKey = LinkedHashMap<String, T>()
        for (item in remote) remoteByKey[keyExtractor(item)] = item

        val result = mutableListOf<T>()

        for ((key, localItem) in localByKey) {
            val remoteItem = remoteByKey[key]
            if (remoteItem != null && lastModifiedExtractor(remoteItem) > lastModifiedExtractor(localItem)) {
                result.add(remoteItem)
            } else {
                result.add(localItem)
            }
        }

        var remoteOnlyCount = 0
        var skippedDeletedCount = 0
        for ((key, remoteItem) in remoteByKey) {
            if (!localByKey.containsKey(key)) {
                if (deletedKeys.contains(key)) {
                    skippedDeletedCount++
                } else {
                    result.add(remoteItem)
                    remoteOnlyCount++
                }
            }
        }

        if (remoteOnlyCount > 0) {
            println("🔀 Merge: preserved $remoteOnlyCount remote-only entries from other devices")
        }
        if (skippedDeletedCount > 0) {
            println("🗑️ Merge: excluded $skippedDeletedCount locally-deleted entries")
        }

        return result
    }

    /**
     * Apply a merged snapshot back to local DB so backup mode also refreshes UI.
     * This avoids requiring a second sync to display remote-only entries preserved by merge.
     */
    private suspend fun applyMergedSnapshotToLocal(
        guests: List<Guest>? = null,
        volunteers: List<Volunteer>? = null,
        jobs: List<Job>? = null,
        jobTypeConfigs: List<JobTypeConfig>? = null,
        venues: List<VenueEntity>? = null,
        salesSheetItems: List<SalesSheetItem>? = null
    ) {
        if (jobTypeConfigs != null) {
            repository.clearAllJobTypeConfigs()
            if (jobTypeConfigs.isNotEmpty()) repository.insertJobTypeConfigsAll(jobTypeConfigs)
        }
        if (venues != null) {
            repository.clearAllVenues()
            if (venues.isNotEmpty()) repository.insertVenuesAll(venues)
        }
        if (salesSheetItems != null) {
            repository.clearAllSalesSheetItems()
            if (salesSheetItems.isNotEmpty()) repository.insertSalesSheetItemsAll(salesSheetItems)
        }
        if (guests != null) {
            repository.clearAllGuests()
            if (guests.isNotEmpty()) repository.insertGuestsAll(guests)
        }
        if (volunteers != null) {
            repository.clearAllVolunteers()
            if (volunteers.isNotEmpty()) repository.insertVolunteersAll(volunteers)
        }
        if (jobs != null) {
            repository.clearAllJobs()
            if (jobs.isNotEmpty()) repository.insertJobsAll(jobs)
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

            if (pagesToSync.contains("sales_items") ||
                pagesToSync.contains("management:sales-items")) {
                syncSalesSheetItemsWithDifferentialUpdate()
            }

            if (pagesToSync.contains("venues") ||
                pagesToSync.contains("venue_management") ||
                pagesToSync.contains("management:venue")) {
                syncVenuesWithDifferentialUpdate()
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
            updateLastSyncTime()
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
            // CRITICAL: Exclude volunteer benefits and temporary guests from regular guest comparison.
            // Temporary guests are managed from the dedicated temporary guest sheet.
            val regularMainGuests = mainGuests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }
            println("📊 Current local data: ${mainGuests.size} guests (${mainGuests.size - regularMainGuests.size} volunteer/temporary guests excluded from comparison)")
            
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
            val remoteVolunteersById = remoteVolunteers.associateBy { it.id }
            val localVolunteersById = localVolunteers.associateBy { it.id }
            // Use name + abbreviation as key to allow multiple volunteers with same first name
            val localVolunteersByFullName = localVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}" }
            
            // Update or insert remote volunteers - use NanoID from Google Sheets
            for (volunteer in remoteVolunteers) {
                try {
                    // Try to find existing volunteer by NanoID first, then by name+abbreviation
                    val existingVolunteer = localVolunteersById[volunteer.id]
                        ?: localVolunteersByFullName["${volunteer.name}_${volunteer.lastNameAbbreviation}"]
                    
                    if (existingVolunteer != null) {
                        if (existingVolunteer.id != volunteer.id) {
                            // NanoID changed - Google Sheets has the correct ID
                            println("🔄 Volunteer '${volunteer.name} ${volunteer.lastNameAbbreviation}' NanoID changed: '${existingVolunteer.id}' → '${volunteer.id}' (adopting Google Sheets ID)")
                            repository.updateJobsVolunteerId(existingVolunteer.id, volunteer.id)
                            repository.deleteVolunteer(existingVolunteer)
                            repository.insertVolunteer(volunteer)
                        } else {
                            // Same NanoID - just update the data
                            repository.updateVolunteer(volunteer)
                        }
                        println("Updated volunteer: ${volunteer.name} ${volunteer.lastNameAbbreviation} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    } else {
                        // New volunteer from sheets - use NanoID from sheets as-is
                        repository.insertVolunteer(volunteer)
                        println("Inserted new volunteer: ${volunteer.name} ${volunteer.lastNameAbbreviation} (ID: ${volunteer.id}, Active: ${volunteer.isActive})")
                    }
                } catch (e: Exception) {
                    println("Failed to sync volunteer ${volunteer.name} ${volunteer.lastNameAbbreviation}: ${e.message}")
                    // Continue with other volunteers even if one fails
                }
            }
            
            // Keep local volunteers that don't exist in remote data (preserve inactive volunteers)
            // Check by NanoID and name+abbreviation to avoid preserving duplicates
            val remoteVolunteerFullNames = remoteVolunteers.map { "${it.name}_${it.lastNameAbbreviation}" }.toSet()
            val localVolunteersToKeep = localVolunteers.filter { localVolunteer ->
                remoteVolunteersById[localVolunteer.id] == null &&
                !remoteVolunteerFullNames.contains("${localVolunteer.name}_${localVolunteer.lastNameAbbreviation}")
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
            updateLastSyncTime()
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
            val volunteers = repository.getAllVolunteers().first()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs, volunteers)
            repository.clearAllJobs()
            for (job in remoteJobs) {
                repository.insertJob(job)
            }
            println("Synced ${remoteJobs.size} jobs")
            updateLastSyncTime()
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
            val volunteers = repository.getAllVolunteers().first()
            val remoteJobs = googleSheetsService.syncJobsFromSheets(remoteJobTypeConfigs, volunteers)
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
            updateLastSyncTime()
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

    suspend fun syncSalesSheetItemsOnly() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            val remoteItems = googleSheetsService.syncSalesSheetItemsFromSheets()
            repository.clearAllSalesSheetItems()
            if (remoteItems.isNotEmpty()) {
                repository.insertSalesSheetItemsAll(remoteItems)
            }
            println("Synced ${remoteItems.size} sales sheet items")
            updateLastSyncTime()
        } catch (e: Exception) {
            println("Failed to sync sales sheet items: ${e.message}")
            throw e
        }
        }
    }

    suspend fun syncSalesSheetItemsWithDifferentialUpdate(): DifferentialSyncService.SyncChanges<SalesSheetItem> =
        withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            println("🔄 Starting differential sales sheet item sync from Google Sheets...")

            val remoteItems = googleSheetsService.syncSalesSheetItemsFromSheets()
            println("📥 Downloaded ${remoteItems.size} sales sheet items from sheets")

            val mainItems = repository.getAllSalesSheetItems().first()
            println("📊 Current local data: ${mainItems.size} sales sheet items")

            val itemChanges = differentialSyncService.compareSalesSheetItems(remoteItems, mainItems)
            println("📋 Changes detected: ${itemChanges.new.size} new, ${itemChanges.modified.size} modified, ${itemChanges.deleted.size} deleted")

            if (itemChanges.hasChanges) {
                itemChanges.new.forEach { repository.insertSalesSheetItem(it) }
                itemChanges.modified.forEach { repository.updateSalesSheetItem(it) }
                itemChanges.deleted.forEach { repository.deleteSalesSheetItem(it) }
                println("✅ Applied ${itemChanges.totalChanges} sales sheet item changes to database")
            } else {
                println("ℹ️ No sales sheet item changes detected - data is already in sync")
            }

            updateLastSyncTime()
            println("✅ Differential sales sheet item sync completed successfully")
            itemChanges
        } catch (e: Exception) {
            println("❌ Differential sales sheet item sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential sales sheet item sync failed: ${e.message}", e)
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
            
            val myId = settingsManager.getOrCreatePersistentDeviceId()
            val mainByKey = mainVenues.associateBy { venueDifferentialKey(it) }
            val mergedModified = venueChanges.modified.map { remote ->
                val local = mainByKey[venueDifferentialKey(remote)]
                var merged = mergeVenueFromSheetKeepingPriorityCounter(remote, local, myId)
                if (local != null &&
                    shouldPreserveLocalPeopleCounterOnVenuePull(local, myId) &&
                    venuePeopleCounterCellsDiffer(remote, local)
                ) {
                    val row = local.sheetsId?.toIntOrNull()
                    if (row != null) {
                        try {
                            val now = System.currentTimeMillis()
                            val writer = local.peopleCounterWriterDeviceId.trim().ifBlank { myId }
                            googleSheetsService.updateVenuePeopleCounterCells(
                                row,
                                local.peopleCounterCount,
                                writer,
                                now
                            )
                            merged = merged.copy(
                                peopleCounterWriterDeviceId = writer,
                                peopleCounterLastModified = now
                            )
                        } catch (e: Exception) {
                            println("⚠️ People counter push after venue pull failed: ${e.message}")
                        }
                    }
                }
                merged
            }

            // STEP 4: Apply changes to database
            if (venueChanges.hasChanges) {
                venueChanges.new.forEach { repository.insertVenue(it) }
                mergedModified.forEach { repository.updateVenue(it) }
                venueChanges.deleted.forEach { repository.deleteVenue(it) }
                println("✅ Applied ${venueChanges.totalChanges} venue changes to database")
            } else {
                println("ℹ️ No venue changes detected - data is already in sync")
            }
            
            // Update sync time
            updateLastSyncTime()
            
            println("✅ Differential venue sync completed successfully")
            
            // Return merged rows so in-memory _venues matches DB (priority counter not clobbered by stale sheet)
            if (venueChanges.hasChanges) {
                venueChanges.copy(modified = mergedModified)
            } else {
                venueChanges
            }
            
        } catch (e: Exception) {
            println("❌ Differential venue sync failed: ${e.message}")
            e.printStackTrace()
            throw IOException("Differential venue sync failed: ${e.message}", e)
        }
        }
    }

    private fun venueDifferentialKey(v: VenueEntity): String = v.name

    private fun shouldPreserveLocalPeopleCounterOnVenuePull(local: VenueEntity, myId: String): Boolean {
        if (!settingsManager.isPeopleCounterPriority(local.id)) return false
        val w = local.peopleCounterWriterDeviceId.trim()
        return w.isEmpty() || w == myId
    }

    private fun venuePeopleCounterCellsDiffer(remote: VenueEntity, local: VenueEntity): Boolean =
        remote.peopleCounterCount != local.peopleCounterCount ||
            remote.peopleCounterWriterDeviceId.trim() != local.peopleCounterWriterDeviceId.trim()

    /**
     * Applies sheet row [remote] to local id, but keeps E–G from [local] when this device owns priority
     * for that venue so a stale sheet cannot overwrite an in-progress local count.
     */
    private fun mergeVenueFromSheetKeepingPriorityCounter(
        remote: VenueEntity,
        local: VenueEntity?,
        myId: String
    ): VenueEntity {
        if (local == null) return remote
        val base = remote.copy(id = local.id)
        if (!shouldPreserveLocalPeopleCounterOnVenuePull(local, myId)) return base
        return base.copy(
            peopleCounterCount = local.peopleCounterCount,
            peopleCounterWriterDeviceId = local.peopleCounterWriterDeviceId,
            peopleCounterLastModified = local.peopleCounterLastModified
        )
    }

    /**
     * Single-row update for venue people counter (columns E–G), serialized with other sheet operations.
     */
    suspend fun updateVenuePeopleCounterOnSheets(
        sheetRow1Based: Int,
        peopleCount: Int,
        writerDeviceId: String,
        counterLastModifiedMs: Long
    ) = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            googleSheetsService.initializeSheetsService()
            googleSheetsService.updateVenuePeopleCounterCells(
                sheetRow1Based,
                peopleCount,
                writerDeviceId,
                counterLastModifiedMs
            )
        }
    }

    /**
     * Reads the current people-counter cells (E–G) for a venue row from Sheets.
     * Serialized with other sheet operations so reads see a consistent state with writes.
     */
    suspend fun readVenuePeopleCounterFromSheet(sheetRow1Based: Int): Triple<Int, String, Long>? =
        withContext(Dispatchers.IO) {
            sheetsOpMutex.withLock {
                if (!isGoogleSheetsConfigured()) {
                    null
                } else {
                    googleSheetsService.initializeSheetsService()
                    googleSheetsService.readVenuePeopleCounterCells(sheetRow1Based)
                }
            }
        }

    /**
     * Updates announcement columns (H–K) for a single venue row on Google Sheets.
     */
    suspend fun updateVenueAnnouncementOnSheets(
        sheetRow1Based: Int,
        title: String,
        message: String,
        sentAtMs: Long,
        senderDeviceId: String
    ) = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }
            googleSheetsService.initializeSheetsService()
            googleSheetsService.updateVenueAnnouncementCells(
                sheetRow1Based,
                title,
                message,
                sentAtMs,
                senderDeviceId
            )
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
            googleSheetsService.validateAndRepairSheetsStructure()
            val guests = repository.getAllGuests().first()
            val venues = repository.getAllVenues().first()
            println("📊 Retrieved ${guests.size} guests from repository for backup")
            
            val remoteGuests = try {
                googleSheetsService.syncGuestsFromSheets()
            } catch (e: Exception) {
                println("⚠️ Could not read remote guests for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val localRegular = guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }
            val remoteRegular = remoteGuests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }

            val deletedGuestKeys = deletionTracker.getDeletedBusinessKeys("guest")

            val mergedRegular = mergeLocalWithRemote(
                local = localRegular,
                remote = remoteRegular,
                keyExtractor = { it.nanoId },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedGuestKeys
            )

            val allForUpload = mergedRegular + guests.filter { it.isVolunteerBenefit || it.isTemporaryGuest }
            googleSheetsService.syncGuestsToSheets(allForUpload, venues)
            applyMergedSnapshotToLocal(guests = allForUpload)
            println("✅ Backed up ${mergedRegular.size} regular guests to Google Sheets (${localRegular.size} local, ${mergedRegular.size - localRegular.size} preserved from other devices)")
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
            googleSheetsService.validateAndRepairSheetsStructure()
            val volunteers = repository.getAllVolunteers().first()
            println("📊 Retrieved ${volunteers.size} volunteers from repository for backup")
            
            val remoteVolunteers = try {
                googleSheetsService.syncVolunteersFromSheets()
            } catch (e: Exception) {
                println("⚠️ Could not read remote volunteers for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val deletedVolunteerKeys = deletionTracker.getDeletedBusinessKeys("volunteer")

            val merged = mergeLocalWithRemote(
                local = volunteers,
                remote = remoteVolunteers,
                keyExtractor = { it.id },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedVolunteerKeys
            )

            val jobs = repository.getAllJobs().first()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val volunteerRanksForSheet = BenefitCalculator.volunteerPrimaryRanksForSheetUpload(
                merged, jobs, jobTypeConfigs
            )
            googleSheetsService.syncVolunteersToSheets(merged, volunteerRanksForSheet)
            applyMergedSnapshotToLocal(volunteers = merged)
            println("✅ Backed up ${merged.size} volunteers to Google Sheets (${volunteers.size} local, ${merged.size - volunteers.size} preserved from other devices)")
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
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val volunteers = repository.getAllVolunteers().first()
            println("📊 Retrieved ${jobs.size} jobs from repository for backup")
            
            val remoteJobs = try {
                googleSheetsService.syncJobsFromSheets(jobTypeConfigs, volunteers)
            } catch (e: Exception) {
                println("⚠️ Could not read remote jobs for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val deletedJobKeys = deletionTracker.getDeletedBusinessKeys("job")

            val merged = mergeLocalWithRemote(
                local = jobs,
                remote = remoteJobs,
                keyExtractor = { "${it.volunteerId}_${it.jobTypeName}_${it.date}_${it.venueName}_${it.shiftTime}" },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedJobKeys
            )

            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncJobsToSheets(merged, venues, jobTypeConfigs, volunteers)
            applyMergedSnapshotToLocal(jobs = merged)
            println("✅ Successfully backed up ${merged.size} jobs to Google Sheets (${jobs.size} local, ${merged.size - jobs.size} preserved from other devices)")
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
            
            val remoteJobTypes = try {
                googleSheetsService.syncJobTypeConfigsFromSheets()
            } catch (e: Exception) {
                println("⚠️ Could not read remote job types for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val deletedJobTypeKeys = deletionTracker.getDeletedBusinessKeys("job_type")

            val merged = mergeLocalWithRemote(
                local = jobTypeConfigs,
                remote = remoteJobTypes,
                keyExtractor = { it.name },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedJobTypeKeys
            )

            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncJobTypeConfigsToSheets(merged)
            applyMergedSnapshotToLocal(jobTypeConfigs = merged)
            println("✅ Successfully backed up ${merged.size} job types to Google Sheets (${jobTypeConfigs.size} local, ${merged.size - jobTypeConfigs.size} preserved from other devices)")
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
            
            val remoteVenues = try {
                googleSheetsService.syncVenuesFromSheets()
            } catch (e: Exception) {
                println("⚠️ Could not read remote venues for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val deletedVenueKeys = deletionTracker.getDeletedBusinessKeys("venue")

            val merged = mergeLocalWithRemote(
                local = venues,
                remote = remoteVenues,
                keyExtractor = { it.name },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedVenueKeys
            )

            kotlinx.coroutines.delay(100)
            
            googleSheetsService.syncVenuesToSheets(merged)
            applyMergedSnapshotToLocal(venues = merged)
            println("✅ Successfully backed up ${merged.size} venues to Google Sheets (${venues.size} local, ${merged.size - venues.size} preserved from other devices)")
        } catch (e: Exception) {
            println("❌ Failed to backup venues: ${e.message}")
            throw e
        }
        }
    }

    suspend fun backupSalesSheetItemsToSheets() = withContext(Dispatchers.IO) {
        sheetsOpMutex.withLock {
        try {
            if (!isGoogleSheetsConfigured()) {
                throw IOException("Google Sheets not configured")
            }

            println("Starting backup of sales sheet items to Google Sheets...")
            googleSheetsService.initializeSheetsService()
            val items = repository.getAllSalesSheetItems().first()
            println("📊 Retrieved ${items.size} sales sheet items from repository for backup")

            val remoteItems = try {
                googleSheetsService.syncSalesSheetItemsFromSheets()
            } catch (e: Exception) {
                println("⚠️ Could not read remote sales sheet items for merge, uploading local only: ${e.message}")
                emptyList()
            }

            val deletedKeys = deletionTracker.getDeletedBusinessKeys("sales_item")
            val merged = mergeLocalWithRemote(
                local = items,
                remote = remoteItems,
                keyExtractor = { it.name },
                lastModifiedExtractor = { it.lastModified },
                deletedKeys = deletedKeys
            )

            googleSheetsService.syncSalesSheetItemsToSheets(merged)
            applyMergedSnapshotToLocal(salesSheetItems = merged)
            println("✅ Successfully backed up ${merged.size} sales sheet items to Google Sheets (${items.size} local, ${merged.size - items.size} preserved from other devices)")
        } catch (e: Exception) {
            println("❌ Failed to backup sales sheet items: ${e.message}")
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
        settingsManager.recordSheetsPullAt(System.currentTimeMillis())
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
                    "headers" to listOf("Name", "Email", "Phone", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified", "NFC UID", "ID")
                )
            } catch (e: Exception) {
                diagnostics["guests"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            try {
                val volunteers = googleSheetsService.syncVolunteersFromSheets()
                diagnostics["volunteers"] = mapOf(
                    "status" to "OK",
                    "count" to volunteers.size,
                    "headers" to listOf("ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth", "Rank", "Active", "Last Modified", "NFC UID")
                )
            } catch (e: Exception) {
                diagnostics["volunteers"] = mapOf("status" to "ERROR", "message" to e.message)
            }
            
            try {
                val jobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
                val jobs = googleSheetsService.syncJobsFromSheets(jobTypeConfigs, emptyList())
                diagnostics["jobs"] = mapOf(
                    "status" to "OK",
                    "count" to jobs.size,
                    "headers" to listOf("Volunteer ID", "Volunteer Name", "Job Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified", "Entries left")
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
