package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import kotlinx.coroutines.flow.first

/**
 * Sync Manager that provides a clean interface for different sync operations
 * and handles page change sync logic
 */
class SyncManager(
    private val context: Context,
    private val repository: EventManagerRepository,
    private val googleSheetsService: GoogleSheetsService
) {
    
    private val twoWaySyncService = TwoWaySyncService(context, repository, googleSheetsService)
    private val settingsManager = SettingsManager(context)
    private val dataStructureValidator = DataStructureValidator(context, googleSheetsService)
    
    /**
     * SYNC MODE: Download entire dataset from Google Sheets and replace local data
     * This is used for manual sync and scheduled sync
     */
    suspend fun performFullSync(): SyncResult {
        return try {
            twoWaySyncService.syncFromGoogleSheets()
            SyncResult.Success("Full sync completed successfully")
        } catch (e: Exception) {
            SyncResult.Error("Full sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL SYNC MODE: Download from Google Sheets and update only what changed
     * This is the new efficient sync that avoids full-page UI reloads
     * 
     * Returns detailed information about what changed (new, modified, deleted items)
     * so the UI can apply targeted updates instead of refreshing everything
     */
    suspend fun performDifferentialSync(): DifferentialSyncResult {
        return try {
            val syncResult = twoWaySyncService.syncFromGoogleSheetsWithDifferentialUpdate()
            DifferentialSyncResult.Success(syncResult)
        } catch (e: Exception) {
            DifferentialSyncResult.Error("Differential sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL VOLUNTEER SYNC: Download and update only changed volunteers
     * This is used for the volunteer page to avoid full-page reload
     */
    suspend fun performVolunteerDifferentialSync(): VolunteerSyncResult {
        return try {
            val changes = twoWaySyncService.syncVolunteersWithDifferentialUpdate()
            VolunteerSyncResult.Success(changes)
        } catch (e: Exception) {
            VolunteerSyncResult.Error("Volunteer differential sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL GUEST SYNC: Download and update only changed guests
     * This is used for the guest page to avoid full-page reload
     */
    suspend fun performGuestDifferentialSync(): GuestSyncResult {
        return try {
            val changes = twoWaySyncService.syncGuestsWithDifferentialUpdate()
            GuestSyncResult.Success(changes)
        } catch (e: Exception) {
            GuestSyncResult.Error("Guest differential sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL JOB SYNC: Download and update only changed jobs
     * This is used for the jobs/shifts page to avoid full-page reload
     */
    suspend fun performJobDifferentialSync(): JobSyncResult {
        return try {
            val changes = twoWaySyncService.syncJobsWithDifferentialUpdate()
            JobSyncResult.Success(changes)
        } catch (e: Exception) {
            JobSyncResult.Error("Job differential sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL JOB TYPE SYNC: Download and update only changed job types
     * This is used for the job types settings page to avoid full-page reload
     */
    suspend fun performJobTypeDifferentialSync(): JobTypeSyncResult {
        return try {
            val changes = twoWaySyncService.syncJobTypesWithDifferentialUpdate()
            JobTypeSyncResult.Success(changes)
        } catch (e: Exception) {
            JobTypeSyncResult.Error("Job type differential sync failed: ${e.message}")
        }
    }
    
    /**
     * DIFFERENTIAL VENUE SYNC: Download and update only changed venues
     * This is used for the venues settings page to avoid full-page reload
     */
    suspend fun performVenueDifferentialSync(): VenueSyncResult {
        return try {
            val changes = twoWaySyncService.syncVenuesWithDifferentialUpdate()
            VenueSyncResult.Success(changes)
        } catch (e: Exception) {
            VenueSyncResult.Error("Venue differential sync failed: ${e.message}")
        }
    }
    
    /**
     * PAGE CHANGE SYNC: Download only current page and new page data
     * This is used when user changes pages in the app
     */
    suspend fun performPageChangeSync(currentPage: String, newPage: String): SyncResult {
        return try {
            twoWaySyncService.syncPageChange(currentPage, newPage)
            SyncResult.Success("Page change sync completed successfully")
        } catch (e: Exception) {
            SyncResult.Error("Page change sync failed: ${e.message}")
        }
    }
    
    /**
     * BACKUP MODE: Upload entire local dataset to Google Sheets
     * This overwrites the corresponding Google Sheet tab completely
     */
    suspend fun performBackupToSheets(): SyncResult {
        return try {
            twoWaySyncService.backupToGoogleSheets()
            SyncResult.Success("Backup to Google Sheets completed successfully")
        } catch (e: Exception) {
            SyncResult.Error("Backup failed: ${e.message}")
        }
    }
    
    /**
     * BACKUP SPECIFIC DATASET: Upload specific dataset to Google Sheets
     */
    suspend fun backupGuestsToSheets(): SyncResult {
        return try {
            twoWaySyncService.backupGuestsToSheets()
            SyncResult.Success("Guests backed up successfully")
        } catch (e: Exception) {
            SyncResult.Error("Failed to backup guests: ${e.message}")
        }
    }
    
    suspend fun backupVolunteersToSheets(): SyncResult {
        return try {
            twoWaySyncService.backupVolunteersToSheets()
            SyncResult.Success("Volunteers backed up successfully")
        } catch (e: Exception) {
            SyncResult.Error("Failed to backup volunteers: ${e.message}")
        }
    }
    
    suspend fun backupJobsToSheets(): SyncResult {
        return try {
            twoWaySyncService.backupJobsToSheets()
            SyncResult.Success("Jobs backed up successfully")
        } catch (e: Exception) {
            SyncResult.Error("Failed to backup jobs: ${e.message}")
        }
    }
    
    suspend fun backupJobTypesToSheets(): SyncResult {
        return try {
            twoWaySyncService.backupJobTypesToSheets()
            SyncResult.Success("Job types backed up successfully")
        } catch (e: Exception) {
            SyncResult.Error("Failed to backup job types: ${e.message}")
        }
    }
    
    /**
     * VALIDATION: Check Google Sheets structure
     */
    suspend fun validateGoogleSheetsStructure(): Map<String, Any> {
        return twoWaySyncService.validateGoogleSheetsStructure()
    }
    
    /**
     * DATA STRUCTURE VALIDATION: Ensure consistent headers and data structure
     */
    suspend fun validateDataStructure(): ValidationResult {
        return dataStructureValidator.validateAllSheets()
    }
    
    /**
     * CREATE OR FIX SHEET STRUCTURE: Ensure all sheets have correct headers
     */
    suspend fun createOrFixSheetStructure(): ValidationResult {
        return dataStructureValidator.createOrFixSheetStructure()
    }
    
    /**
     * GET EXPECTED HEADERS: Get the expected headers for a sheet type
     */
    fun getExpectedHeaders(sheetType: String): List<String>? {
        return dataStructureValidator.getExpectedHeaders(sheetType)
    }
    
    /**
     * GET ALL EXPECTED HEADERS: Get all expected headers for all sheet types
     */
    fun getAllExpectedHeaders(): Map<String, List<String>> {
        return dataStructureValidator.getAllExpectedHeaders()
    }
    
    /**
     * UTILITY METHODS
     */
    fun isGoogleSheetsConfigured(): Boolean {
        return settingsManager.isConfigured()
    }
    
    suspend fun getLastSyncTime(): Long {
        return twoWaySyncService.getLastSyncTime()
    }
    
    /**
     * PAGE MAPPING: Map UI pages to sync operations
     */
    fun getPageSyncMapping(): Map<String, List<String>> {
        return mapOf(
            "guests" to listOf("guests", "guest_list"),
            "volunteers" to listOf("volunteers", "volunteer_list"),
            "jobs" to listOf("jobs", "job_list"),
            "job_types" to listOf("job_types", "job_type_configs")
        )
    }
    
    /**
     * Determine which datasets need to be synced based on page changes
     */
    fun getDatasetsToSync(currentPage: String, newPage: String): Set<String> {
        val pageMapping = getPageSyncMapping()
        val datasetsToSync = mutableSetOf<String>()
        
        // Find datasets for current page
        pageMapping.forEach { (dataset, pages) ->
            if (pages.contains(currentPage) || pages.contains(newPage)) {
                datasetsToSync.add(dataset)
            }
        }
        
        return datasetsToSync
    }
    
    /**
     * SMART PAGE CHANGE SYNC: Only sync datasets that are relevant to the page change
     */
    suspend fun performSmartPageChangeSync(currentPage: String, newPage: String): SyncResult {
        return try {
            val datasetsToSync = getDatasetsToSync(currentPage, newPage)
            
            if (datasetsToSync.isEmpty()) {
                return SyncResult.Success("No sync needed for page change: $currentPage → $newPage")
            }
            
            println("Smart page change sync: $currentPage → $newPage, syncing: $datasetsToSync")
            
            // Sync only relevant datasets
            datasetsToSync.forEach { dataset ->
                when (dataset) {
                    "guests" -> twoWaySyncService.syncGuestsOnly()
                    "volunteers" -> twoWaySyncService.syncVolunteersOnly()
                    "jobs" -> twoWaySyncService.syncJobsOnly()
                    "job_types" -> twoWaySyncService.syncJobTypesOnly()
                }
            }
            
            SyncResult.Success("Smart page change sync completed for: $datasetsToSync")
        } catch (e: Exception) {
            SyncResult.Error("Smart page change sync failed: ${e.message}")
        }
    }
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of a differential sync operation
 * Contains detailed information about what changed
 */
sealed class DifferentialSyncResult {
    data class Success(val changes: DifferentialSyncService.DifferentialSyncResult) : DifferentialSyncResult()
    data class Error(val message: String) : DifferentialSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of volunteer differential sync operation
 * Contains information about volunteer changes (new, modified, deleted)
 */
sealed class VolunteerSyncResult {
    data class Success(val changes: DifferentialSyncService.SyncChanges<com.eventmanager.app.data.models.Volunteer>) : VolunteerSyncResult()
    data class Error(val message: String) : VolunteerSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of guest differential sync operation
 * Contains information about guest changes (new, modified, deleted)
 */
sealed class GuestSyncResult {
    data class Success(val changes: DifferentialSyncService.SyncChanges<com.eventmanager.app.data.models.Guest>) : GuestSyncResult()
    data class Error(val message: String) : GuestSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of job differential sync operation
 * Contains information about job changes (new, modified, deleted)
 */
sealed class JobSyncResult {
    data class Success(val changes: DifferentialSyncService.SyncChanges<com.eventmanager.app.data.models.Job>) : JobSyncResult()
    data class Error(val message: String) : JobSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of job type differential sync operation
 * Contains information about job type changes (new, modified, deleted)
 */
sealed class JobTypeSyncResult {
    data class Success(val changes: DifferentialSyncService.SyncChanges<com.eventmanager.app.data.models.JobTypeConfig>) : JobTypeSyncResult()
    data class Error(val message: String) : JobTypeSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Result of venue differential sync operation
 * Contains information about venue changes (new, modified, deleted)
 */
sealed class VenueSyncResult {
    data class Success(val changes: DifferentialSyncService.SyncChanges<com.eventmanager.app.data.models.VenueEntity>) : VenueSyncResult()
    data class Error(val message: String) : VenueSyncResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}
