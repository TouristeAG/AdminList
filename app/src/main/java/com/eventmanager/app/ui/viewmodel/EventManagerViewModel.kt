package com.eventmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.DeletionTracker
import com.eventmanager.app.data.sync.FileManager
import com.eventmanager.app.data.sync.TwoWaySyncService
import com.eventmanager.app.data.sync.SyncManager
import com.eventmanager.app.data.sync.SyncResult
import com.eventmanager.app.data.sync.ValidationResult
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.data.sync.RateLimitError
import com.eventmanager.app.data.sync.ApiRateLimitHandler
import com.eventmanager.app.data.sync.DifferentialSyncService
import com.eventmanager.app.data.sync.DifferentialSyncResult
import com.eventmanager.app.data.sync.VolunteerSyncResult
import com.eventmanager.app.data.sync.GuestSyncResult
import com.eventmanager.app.data.sync.JobSyncResult
import com.eventmanager.app.data.sync.JobTypeSyncResult
import com.eventmanager.app.data.sync.VenueSyncResult
import com.eventmanager.app.data.sync.SyncErrorManager
import com.eventmanager.app.data.sync.AppLogger
import com.eventmanager.app.data.update.UpdateChecker
import com.eventmanager.app.data.update.UpdateCheckResult
import com.eventmanager.app.data.update.UpdateDownloader
import com.eventmanager.app.data.update.DownloadState
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
    }
}

class EventManagerViewModel(
    val repository: EventManagerRepository,
    private val googleSheetsService: GoogleSheetsService,
    private val context: Context? = null
) : ViewModel() {
    
    // Deletion tracker for handling deletions properly
    private val deletionTracker = context?.let { DeletionTracker(it) }
    
    // New two-way sync service
    private val twoWaySyncService = context?.let { 
        TwoWaySyncService(it, repository, googleSheetsService) 
    }
    
    // Sync manager for clean interface
    private val syncManager = context?.let { 
        SyncManager(it, repository, googleSheetsService) 
    }

    // State for guests
    private val _guests = MutableStateFlow<List<Guest>>(emptyList())
    val guests: StateFlow<List<Guest>> = _guests.asStateFlow()

    // State for volunteers
    private val _volunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val volunteers: StateFlow<List<Volunteer>> = _volunteers.asStateFlow()

    // State for jobs
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    // State for job type configs
    private val _jobTypeConfigs = MutableStateFlow<List<JobTypeConfig>>(emptyList())
    val jobTypeConfigs: StateFlow<List<JobTypeConfig>> = _jobTypeConfigs.asStateFlow()

    private val _venues = MutableStateFlow<List<VenueEntity>>(emptyList())
    val venues: StateFlow<List<VenueEntity>> = _venues.asStateFlow()

    // State for sync status
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // State for sync error
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()
    
    // State for sync error dialog visibility
    private val _showSyncErrorDialog = MutableStateFlow(false)
    val showSyncErrorDialog: StateFlow<Boolean> = _showSyncErrorDialog.asStateFlow()
    
    // State for sync status message and dialog visibility
    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()
    
    private val _showSyncStatusDialog = MutableStateFlow(false)
    val showSyncStatusDialog: StateFlow<Boolean> = _showSyncStatusDialog.asStateFlow()
    
    // Error manager for "do not tell me again today"
    private val syncErrorManager = context?.let { SyncErrorManager(it) }

    // State for last sync time
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    // Background sync job
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null

    // Update check state
    private val _updateCheckState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckState: StateFlow<UpdateCheckResult?> = _updateCheckState.asStateFlow()

    private val updateChecker: UpdateChecker? = context?.let { UpdateChecker(it) }
    
    // Update download state
    private val _updateDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val updateDownloadState: StateFlow<DownloadState> = _updateDownloadState.asStateFlow()
    
    private val updateDownloader: UpdateDownloader? = context?.let { UpdateDownloader(it) }

    init {
        loadData()
        startBackgroundSync()
        loadLastSyncTime()
        // Clean up any existing duplicates in the database
        cleanupDuplicates()
        // Ensure volunteer benefits reflected in guestlist on startup (no upload on launch)
        // Delay this to allow initial data load to complete
        viewModelScope.launch {
            delay(1000) // Increased delay to allow data to load from database
            recalcVolunteerGuestListNoUpload()
        }
        // Ensure volunteer activity is calculated after initial data load
        viewModelScope.launch {
            delay(800) // Small delay to ensure all data is loaded
            updateVolunteerActivityFromCurrentJobs()
        }
    }

    /**
     * Manually trigger an update check against the remote manifest.
     * Result is exposed via [updateCheckState].
     */
    fun checkForAppUpdates() {
        val checker = updateChecker ?: return
        viewModelScope.launch {
            _updateCheckState.value = null // reset previous result
            val result = checker.checkForUpdates()
            _updateCheckState.value = result
        }
    }
    
    /**
     * Download an update APK from the given URL.
     * Progress is exposed via [updateDownloadState].
     */
    fun downloadUpdate(downloadUrl: String) {
        val downloader = updateDownloader ?: return
        viewModelScope.launch {
            downloader.downloadUpdate(downloadUrl).collect { state ->
                _updateDownloadState.value = state
            }
        }
    }
    
    /**
     * Install a downloaded APK file.
     */
    fun installUpdate(apkFile: File) {
        updateDownloader?.installUpdate(apkFile)
    }
    
    override fun onCleared() {
        super.onCleared()
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
        println("ViewModel cleared - background sync stopped")
    }

    private fun loadLastSyncTime() {
        context?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            _lastSyncTime.value = settingsManager.getLastSyncTime()
        }
    }

    private fun startBackgroundSync() {
        context?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            val syncInterval = settingsManager.getSyncInterval()
            
            // Cancel any existing job first
            backgroundSyncJob?.cancel()
            
            println("Starting background sync with interval: $syncInterval minutes")
            println("Google Sheets configured: ${isGoogleSheetsConfigured()}")
            
            backgroundSyncJob = viewModelScope.launch {
                while (true) {
                    try {
                        println("Background sync waiting for $syncInterval minutes...")
                        kotlinx.coroutines.delay(syncInterval * 60 * 1000L) // Convert minutes to milliseconds
                        
                        println("Background sync timer triggered")
                        if (isGoogleSheetsConfigured()) {
                            println("Google Sheets is configured, starting full sync...")
                            performFullSync()
                        } else {
                            println("Google Sheets not configured, skipping sync")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        println("Background sync cancelled - this is normal when updating interval")
                        break // Exit the loop when cancelled
                    } catch (e: Exception) {
                        println("Background sync error: ${e.message}")
                        e.printStackTrace()
                        // Continue the loop for other errors
                    }
                }
            }
        } ?: run {
            println("No context available for background sync")
        }
    }

    fun updateSyncInterval() {
        context?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            val syncInterval = settingsManager.getSyncInterval()
            
            println("Updating sync interval to $syncInterval minutes")
            
            // Cancel existing job and wait for it to complete
            backgroundSyncJob?.cancel()
            backgroundSyncJob = null
            
            // Start new job with updated interval
            startBackgroundSync()
        }
    }
    
    /**
     * Show sync error dialog if not suppressed
     */
    fun showSyncErrorIfNotSuppressed(errorMessage: String) {
        // Only show critical API errors, not local validation errors
        if (com.eventmanager.app.ui.components.shouldShowSyncError(errorMessage) && 
            syncErrorManager?.shouldSuppressError() == false) {
            _syncError.value = errorMessage
            _showSyncErrorDialog.value = true
        } else if (!com.eventmanager.app.ui.components.shouldShowSyncError(errorMessage)) {
            // Log non-critical errors but don't show dialog
            println("ℹ️ Non-critical sync error (not showing dialog): $errorMessage")
        }
    }
    
    /**
     * Dismiss sync error dialog
     */
    fun dismissSyncErrorDialog() {
        _showSyncErrorDialog.value = false
    }
    
    /**
     * Set "do not tell me again today" for sync errors
     */
    fun setSyncErrorSuppressedToday() {
        syncErrorManager?.setSuppressErrorToday()
    }

    // Track last update time to debounce volunteer activity updates
    private var lastVolunteerActivityUpdate = 0L
    private val volunteerActivityUpdateDebounceMs = 500L
    
    private fun loadData() {
        viewModelScope.launch {
            try {
                repository.getAllGuests().collect { 
                    _guests.value = removeDuplicateGuests(it)
                }
            } catch (e: Exception) {
                println("Failed to load guests: ${e.message}")
                _guests.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllVolunteers().collect { volunteers ->
                    val updatedVolunteers = removeDuplicateVolunteers(volunteers)
                    println("🔄 loadData() - Repository changed! Updating volunteers UI: ${updatedVolunteers.size} volunteers")
                    _volunteers.value = updatedVolunteers
                    println("🔄 loadData() - StateFlow updated! UI should show: ${_volunteers.value.size} volunteers")
                    // Debounce volunteer activity update to avoid multiple rapid calls
                    debouncedUpdateVolunteerActivity()
                }
            } catch (e: Exception) {
                println("Failed to load volunteers: ${e.message}")
                _volunteers.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllJobs().collect { jobs ->
                    _jobs.value = removeDuplicateJobs(jobs)
                    // Debounce volunteer activity update to avoid multiple rapid calls
                    debouncedUpdateVolunteerActivity()
                }
            } catch (e: Exception) {
                println("Failed to load jobs: ${e.message}")
                _jobs.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllJobTypeConfigs().collect { 
                    _jobTypeConfigs.value = removeDuplicateJobTypes(it)
                }
            } catch (e: Exception) {
                println("Failed to load job type configs: ${e.message}")
                _jobTypeConfigs.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllVenues().collect { 
                    _venues.value = removeDuplicateVenues(it)
                }
            } catch (e: Exception) {
                println("Failed to load venues: ${e.message}")
                _venues.value = emptyList()
            }
        }
    }
    
    // Debounced volunteer activity update to prevent multiple rapid calls
    private fun debouncedUpdateVolunteerActivity() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVolunteerActivityUpdate > volunteerActivityUpdateDebounceMs) {
            lastVolunteerActivityUpdate = currentTime
            updateVolunteerActivityFromCurrentJobs()
        } else {
            // Schedule update after debounce period
            viewModelScope.launch {
                delay(volunteerActivityUpdateDebounceMs - (currentTime - lastVolunteerActivityUpdate))
                updateVolunteerActivityFromCurrentJobs()
            }
        }
    }

    // Guest operations
    fun addGuest(guest: Guest) {
        viewModelScope.launch {
            try {
                repository.insertGuest(guest)
                // BACKUP MODE: Upload entire guest dataset to Google Sheets
                twoWaySyncService?.backupGuestsToSheets()
                // Keep volunteer list in sync
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
            println("Failed to add guest: ${e.message}")
            _syncError.value = "Failed to add guest: ${e.message}"
            }
        }
    }

    fun updateGuest(guest: Guest) {
        viewModelScope.launch {
            try {
                // Update lastModified timestamp
                val updatedGuest = guest.copy(lastModified = System.currentTimeMillis())
                repository.updateGuest(updatedGuest)
                // BACKUP MODE: Upload entire guest dataset to Google Sheets
                twoWaySyncService?.backupGuestsToSheets()
                // Keep volunteer list in sync
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to update guest: ${e.message}")
                _syncError.value = "Failed to update guest: ${e.message}"
            }
        }
    }

    fun deleteGuest(guest: Guest) {
        viewModelScope.launch {
            try {
                // Track the deletion
                deletionTracker?.trackGuestDeletion(guest.id.toString(), guest.sheetsId)
                
                // Delete from local database
                repository.deleteGuest(guest)
                
                // BACKUP MODE: Upload entire guest dataset to Google Sheets
                twoWaySyncService?.backupGuestsToSheets()
                
                println("Successfully deleted guest: ${guest.name}")
                // Keep volunteer list in sync
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to delete guest: ${e.message}")
                _syncError.value = "Failed to delete guest: ${e.message}"
            }
        }
    }

    // Volunteer operations
    fun addVolunteer(volunteer: Volunteer) {
        viewModelScope.launch {
            try {
                repository.insertVolunteer(volunteer)
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                twoWaySyncService?.backupVolunteersToSheets()
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
            println("Failed to add volunteer: ${e.message}")
            _syncError.value = "Failed to add volunteer: ${e.message}"
            }
        }
    }

    fun updateVolunteer(volunteer: Volunteer) {
        viewModelScope.launch {
        try {
            repository.updateVolunteer(volunteer)
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                twoWaySyncService?.backupVolunteersToSheets()
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
            println("Failed to update volunteer: ${e.message}")
            _syncError.value = "Failed to update volunteer: ${e.message}"
            }
        }
    }

    fun deleteVolunteer(volunteer: Volunteer) {
        viewModelScope.launch {
            try {
                // Track the deletion
                deletionTracker?.trackVolunteerDeletion(volunteer.id.toString(), volunteer.sheetsId)
                
                // Delete from local database
                repository.deleteVolunteer(volunteer)
                
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                twoWaySyncService?.backupVolunteersToSheets()
                
                println("Successfully deleted volunteer: ${volunteer.name}")
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to delete volunteer: ${e.message}")
                _syncError.value = "Failed to delete volunteer: ${e.message}"
            }
        }
    }

    // Job operations
    fun addJob(job: Job) {
        viewModelScope.launch {
            try {
                // Insert job into local database first
                val jobId = repository.insertJob(job)
                val jobWithId = job.copy(id = jobId)
                
                // Add individual job to Google Sheets and get sheetsId
                val sheetsId = googleSheetsService?.addJobToSheets(jobWithId, _venues.value)
                
                // Update the job with the sheetsId
                if (sheetsId != null) {
                    val jobWithSheetsId = jobWithId.copy(sheetsId = sheetsId)
                    repository.updateJob(jobWithSheetsId)
                    println("Successfully added job to Google Sheets with sheetsId: $sheetsId")
                } else {
                    // Fallback to backup mode if individual add fails
                    println("Individual job add failed, falling back to backup mode")
                    twoWaySyncService?.backupJobsToSheets()
                }
                
                println("Successfully added job: ${job.jobTypeName}")
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to add job: ${e.message}")
                _syncError.value = "Failed to add job: ${e.message}"
            }
        }
    }

    fun updateJob(job: Job) {
        viewModelScope.launch {
            try {
                // Update job in local database
                repository.updateJob(job)
                
                // Update individual job in Google Sheets if sheetsId exists
                if (job.sheetsId != null) {
                    try {
                        googleSheetsService?.updateJobInSheets(job, _venues.value)
                        println("Successfully updated job in Google Sheets: ${job.jobTypeName}")
                    } catch (e: Exception) {
                        println("Individual job update failed, falling back to backup mode: ${e.message}")
                        // Fallback to backup mode if individual update fails
                        twoWaySyncService?.backupJobsToSheets()
                    }
                } else {
                    // If no sheetsId, use backup mode
                    println("No sheetsId found, using backup mode for job update")
                    twoWaySyncService?.backupJobsToSheets()
                }
                
                println("Successfully updated job: ${job.jobTypeName}")
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to update job: ${e.message}")
                _syncError.value = "Failed to update job: ${e.message}"
            }
        }
    }

    fun deleteJob(job: Job) {
        viewModelScope.launch {
            try {
                // Track the deletion
                deletionTracker?.trackJobDeletion(job.id.toString(), job.sheetsId)
                
                // Delete from local database first
                repository.deleteJob(job)
                
                // Delete individual job from Google Sheets if sheetsId exists
                if (job.sheetsId != null) {
                    try {
                        googleSheetsService?.deleteJobFromSheets(job.id.toString(), job.sheetsId)
                        println("Successfully deleted job from Google Sheets: ${job.jobTypeName}")
                    } catch (e: Exception) {
                        println("Individual job deletion failed, falling back to backup mode: ${e.message}")
                        // Fallback to backup mode if individual deletion fails
                        twoWaySyncService?.backupJobsToSheets()
                    }
                } else {
                    // If no sheetsId, use backup mode
                    println("No sheetsId found, using backup mode for job deletion")
                    twoWaySyncService?.backupJobsToSheets()
                }
                
                println("Successfully deleted job: ${job.jobTypeName}")
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to delete job: ${e.message}")
                _syncError.value = "Failed to delete job: ${e.message}"
            }
        }
    }

    // Job type config operations
    fun addJobTypeConfig(config: JobTypeConfig) {
        viewModelScope.launch {
            try {
                // Insert job type config into local database
                repository.insertJobTypeConfig(config)
                
                // BACKUP MODE: Upload entire job type dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupJobTypesToSheets()
                
                println("Successfully added job type: ${config.name}")
            } catch (e: Exception) {
                println("Failed to add job type config: ${e.message}")
                _syncError.value = "Failed to add job type config: ${e.message}"
            }
        }
    }

    fun updateJobTypeConfig(config: JobTypeConfig) {
        viewModelScope.launch {
            try {
                // Update job type config in local database
                repository.updateJobTypeConfig(config)
                
                // BACKUP MODE: Upload entire job type dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupJobTypesToSheets()
                
                println("Successfully updated job type: ${config.name}")
            } catch (e: Exception) {
                println("Failed to update job type config: ${e.message}")
                _syncError.value = "Failed to update job type config: ${e.message}"
            }
        }
    }

    fun deleteJobTypeConfig(config: JobTypeConfig) {
        viewModelScope.launch {
            try {
                // Track the deletion
                deletionTracker?.trackJobTypeDeletion(config.id.toString(), config.sheetsId)
                
                // Delete from local database
                repository.deleteJobTypeConfig(config)
                
                // BACKUP MODE: Upload entire job type dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupJobTypesToSheets()
                
                println("Successfully deleted job type: ${config.name}")
            } catch (e: Exception) {
                println("Failed to delete job type config: ${e.message}")
                _syncError.value = "Failed to delete job type config: ${e.message}"
            }
        }
    }

    // Venue operations
    fun addVenue(venue: VenueEntity) {
        viewModelScope.launch {
            try {
                // Insert venue into local database
                repository.insertVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupVenuesToSheets()
                
                println("Successfully added venue: ${venue.name}")
            } catch (e: Exception) {
                println("Failed to add venue: ${e.message}")
                _syncError.value = "Failed to add venue: ${e.message}"
            }
        }
    }

    fun updateVenue(venue: VenueEntity) {
        viewModelScope.launch {
            try {
                // Update venue in local database
                repository.updateVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupVenuesToSheets()
                
                println("Successfully updated venue: ${venue.name}")
            } catch (e: Exception) {
                println("Failed to update venue: ${e.message}")
                _syncError.value = "Failed to update venue: ${e.message}"
            }
        }
    }

    fun deleteVenue(venue: VenueEntity) {
        viewModelScope.launch {
            try {
                // Track the deletion
                deletionTracker?.trackVenueDeletion(venue.id.toString(), venue.sheetsId)
                
                // Delete from local database
                repository.deleteVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                twoWaySyncService?.backupVenuesToSheets()
                
                println("Successfully deleted venue: ${venue.name}")
            } catch (e: Exception) {
                println("Failed to delete venue: ${e.message}")
                _syncError.value = "Failed to delete venue: ${e.message}"
            }
        }
    }

    fun updateVenueStatus(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateVenueStatus(id, isActive)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                twoWaySyncService?.backupVenuesToSheets()
                
                println("Successfully updated venue status: $id to $isActive")
            } catch (e: Exception) {
                println("Failed to update venue status: ${e.message}")
                _syncError.value = "Failed to update venue status: ${e.message}"
            }
        }
    }

    // Job assignment operations - simplified for current Job model
    fun assignJobToVolunteer(job: Job, volunteer: Volunteer) {
        viewModelScope.launch {
            try {
                val updatedJob = job.copy(
                    volunteerId = volunteer.id
                )
                repository.updateJob(updatedJob)
                
                // Update volunteer's last shift date
                val updatedVolunteer = volunteer.copy(lastShiftDate = System.currentTimeMillis())
                repository.updateVolunteer(updatedVolunteer)
                
                // Note: Individual backup methods are already called in updateJob() and updateVolunteer()
                // so we don't need to call them again here to prevent duplicates
                println("Successfully assigned job ${job.jobTypeName} to volunteer ${volunteer.name}")
        } catch (e: Exception) {
                println("Failed to assign job: ${e.message}")
                _syncError.value = "Failed to assign job: ${e.message}"
            }
        }
    }

    fun updateVolunteerStatus(volunteer: Volunteer, isActive: Boolean) {
        viewModelScope.launch {
            try {
                val updatedVolunteer = volunteer.copy(isActive = isActive)
                repository.updateVolunteer(updatedVolunteer)
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                twoWaySyncService?.backupVolunteersToSheets()
            } catch (e: Exception) {
                println("Failed to update volunteer status: ${e.message}")
                _syncError.value = "Failed to update volunteer status: ${e.message}"
            }
        }
    }

    // Single element upload methods (App Priority)
    private suspend fun uploadSingleGuestToSheets(guest: Guest) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val sheetsId = if (guest.sheetsId == null) {
                // New guest - add to sheets
                googleSheetsService.addGuestToSheets(guest, _venues.value)
            } else {
                // Existing guest - update in sheets
                googleSheetsService.updateGuestInSheets(guest, _venues.value)
                guest.sheetsId
            }
            
            // Update local guest with sheets ID
            if (sheetsId != null && guest.sheetsId != sheetsId) {
                val updatedGuest = guest.copy(sheetsId = sheetsId)
                repository.updateGuest(updatedGuest)
            }
            
            println("Successfully uploaded single guest: ${guest.name}")
        } catch (e: Exception) {
            println("Failed to upload single guest: ${e.message}")
        }
    }
    
    private suspend fun uploadSingleVolunteerToSheets(volunteer: Volunteer) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val sheetsId = if (volunteer.sheetsId == null) {
                // New volunteer - add to sheets
                googleSheetsService.addVolunteerToSheets(volunteer)
            } else {
                // Existing volunteer - update in sheets
                googleSheetsService.updateVolunteerInSheets(volunteer)
                volunteer.sheetsId
            }
            
            // Update local volunteer with sheets ID
            if (sheetsId != null && volunteer.sheetsId != sheetsId) {
                val updatedVolunteer = volunteer.copy(sheetsId = sheetsId)
                repository.updateVolunteer(updatedVolunteer)
            }
            
            println("Successfully uploaded single volunteer: ${volunteer.name}")
        } catch (e: Exception) {
            println("Failed to upload single volunteer: ${e.message}")
        }
    }
    
    private suspend fun uploadSingleJobToSheets(job: Job) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val sheetsId = if (job.sheetsId == null) {
                // New job - add to sheets
                googleSheetsService.addJobToSheets(job, _venues.value)
            } else {
                // Existing job - update in sheets
                googleSheetsService.updateJobInSheets(job, _venues.value)
                job.sheetsId
            }
            
            // Update local job with sheets ID
            if (sheetsId != null && job.sheetsId != sheetsId) {
                val updatedJob = job.copy(sheetsId = sheetsId)
                repository.updateJob(updatedJob)
            }
            
            println("Successfully uploaded single job: ${job.jobTypeName}")
        } catch (e: Exception) {
            println("Failed to upload single job: ${e.message}")
        }
    }
    
    private suspend fun uploadSingleJobTypeToSheets(config: JobTypeConfig) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val sheetsId = if (config.sheetsId == null) {
                // New job type - add to sheets
                googleSheetsService.addJobTypeToSheets(config)
            } else {
                // Existing job type - update in sheets
                googleSheetsService.updateJobTypeInSheets(config)
                config.sheetsId
            }
            
            // Update local job type with sheets ID
            if (sheetsId != null && config.sheetsId != sheetsId) {
                val updatedConfig = config.copy(sheetsId = sheetsId)
                repository.updateJobTypeConfig(updatedConfig)
            }
            
            println("Successfully uploaded single job type: ${config.name}")
        } catch (e: Exception) {
            println("Failed to upload single job type: ${e.message}")
        }
    }

    // Targeted sync operations for specific data types (Sheets Priority)
    fun syncGuestsOnly() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting complete sync for guests (sheets priority)")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Download all guests from sheets
                val remoteGuests = downloadGuestsFromSheets()
                println("Downloaded ${remoteGuests.size} guests from sheets")
                
                // Always clear and replace with sheets data to handle deletions properly
                // Clear local guests first
                repository.clearAllGuests()
                println("🧹 Cleared all local guests")
                
                // Insert remote guests (even if empty, this handles deletions)
                for (guest in remoteGuests) {
                    repository.insertGuest(guest)
                }
                
                if (remoteGuests.isNotEmpty()) {
                    println("✅ Replaced local guests with ${remoteGuests.size} guests from Google Sheets")
                } else {
                    println("✅ Cleared all local guests - Google Sheets is empty (all guests deleted)")
                }
                
                // Refresh guest data
                refreshGuestData()
                // Recompute and merge volunteer benefit entries locally
                recalcAndUploadVolunteerGuestList()
                
                // Update sync time
                updateSyncTime()
                
                println("Complete guest sync completed successfully")
                
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Guest sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Guest sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    fun syncVolunteersOnly() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting complete sync for volunteers (sheets priority)")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Download all volunteers from sheets
                val remoteVolunteers = downloadVolunteersFromSheets()
                println("Downloaded ${remoteVolunteers.size} volunteers from sheets")
                
                // Always clear and replace with sheets data to handle deletions properly
                // Clear local volunteers first
                repository.clearAllVolunteers()
                println("🧹 Cleared all local volunteers")
                
                // Insert remote volunteers (even if empty, this handles deletions)
                for (volunteer in remoteVolunteers) {
                    repository.insertVolunteer(volunteer)
                }
                
                if (remoteVolunteers.isNotEmpty()) {
                    println("✅ Replaced local volunteers with ${remoteVolunteers.size} volunteers from Google Sheets")
                } else {
                    println("✅ Cleared all local volunteers - Google Sheets is empty (all volunteers deleted)")
                }
                
                // Refresh volunteer data
                refreshVolunteerData()
                // Update activity and last time worked from jobs
                updateVolunteerActivityFromJobs()
                // Recompute guestlist benefits since ranks/volunteers may have changed
                recalcAndUploadVolunteerGuestList()
                
                // Update sync time
                updateSyncTime()
                
                println("Complete volunteer sync completed successfully")
                
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Volunteer sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Volunteer sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    fun syncJobsOnly() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting targeted sync for jobs only")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Get current local jobs and job type configs
                val localJobs = repository.getAllJobs().first()
                val localJobTypeConfigs = repository.getAllJobTypeConfigs().first()
                println("Local jobs: ${localJobs.size}, Job types: ${localJobTypeConfigs.size}")
                
                // Download all jobs from sheets
                val remoteJobs = downloadJobsFromSheets(localJobTypeConfigs)
                println("Downloaded ${remoteJobs.size} jobs from sheets")
                
                // Always clear and replace with sheets data to handle deletions properly
                // Clear local jobs first
                repository.clearAllJobs()
                println("🧹 Cleared all local jobs")
                
                // Insert remote jobs (even if empty, this handles deletions)
                for (job in remoteJobs) {
                    repository.insertJob(job)
                }
                
                if (remoteJobs.isNotEmpty()) {
                    println("✅ Replaced local jobs with ${remoteJobs.size} jobs from Google Sheets")
                } else {
                    println("✅ Cleared all local jobs - Google Sheets is empty (all jobs deleted)")
                }
                
                // Refresh job data
                refreshJobData()
                
                // Update volunteer activity based on job assignments
                updateVolunteerActivityFromJobs()
                // Recompute guestlist benefits since job history affects ranks
                recalcAndUploadVolunteerGuestList()
                
                // Update sync time
                updateSyncTime()
                
                println("Targeted job sync completed successfully")
                
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Job sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Job sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    fun syncJobTypesOnly() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting targeted sync for job types only")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Get current local job type configs
                val localJobTypeConfigs = repository.getAllJobTypeConfigs().first()
                println("Local job types: ${localJobTypeConfigs.size}")
                
                // Download all job types from sheets
                val remoteJobTypeConfigs = downloadJobTypesFromSheets()
                println("Downloaded ${remoteJobTypeConfigs.size} job types from sheets")
                
                // Clear local job types and replace with sheets data
                repository.clearAllJobTypeConfigs()
                for (config in remoteJobTypeConfigs) {
                    repository.insertJobTypeConfig(config)
                }
                
                // Refresh job type data
                refreshJobTypeData()
                
                // Update sync time
                updateSyncTime()
                
                println("Targeted job type sync completed successfully")
                
                } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Job type sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Job type sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncVenuesOnly() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting targeted sync for venues only")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Get current local venues
                val localVenues = repository.getAllVenues().first()
                println("Local venues: ${localVenues.size}")
                
                // Download all venues from sheets
                val remoteVenues = downloadVenuesFromSheets()
                println("Downloaded ${remoteVenues.size} venues from sheets")
                
                // Clear local venues and replace with sheets data
                repository.clearAllVenues()
                for (venue in remoteVenues) {
                    repository.insertVenue(venue)
                }
                
                // Refresh venue data
                refreshVenueData()
                
                // Update sync time
                updateSyncTime()
                
                println("Targeted venue sync completed successfully")
                
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Venue sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Venue sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Smart bidirectional sync operations (for full sync when needed)
    fun syncWithGoogleSheets() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Starting smart bidirectional sync with Google Sheets")
            
            // Check if Google Sheets is configured first
            if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                println("Google Sheets configuration verified")
                
                // Initialize Google Sheets service
                googleSheetsService.initializeSheetsService()
                
                // Get current local data
                val localGuests = repository.getAllGuests().first()
                val localVolunteers = repository.getAllActiveVolunteers().first()
                val localJobs = repository.getAllJobs().first()
                val localJobTypeConfigs = repository.getAllJobTypeConfigs().first()
                val localVenues = repository.getAllVenues().first()
                
                println("Local data - Guests: ${localGuests.size}, Volunteers: ${localVolunteers.size}, Jobs: ${localJobs.size}, JobTypes: ${localJobTypeConfigs.size}, Venues: ${localVenues.size}")
                
                // STEP 1: Upload local changes to Google Sheets first (App priority for local modifications)
                println("Step 1: Uploading local changes to Google Sheets...")
                uploadLocalChangesToSheets(localGuests, localVolunteers, localJobs, localJobTypeConfigs, localVenues)
                
                // STEP 2: Download changes from Google Sheets (Sheets priority for remote modifications)
                println("Step 2: Downloading changes from Google Sheets...")
                val (remoteGuests, remoteVolunteers, remoteJobs, remoteJobTypeConfigs, remoteVenues) = downloadChangesFromSheets()
                
                // STEP 3: Smart merge - resolve conflicts intelligently
                println("Step 3: Smart merging data...")
                smartMergeData(localGuests, localVolunteers, localJobs, localJobTypeConfigs, localVenues,
                              remoteGuests, remoteVolunteers, remoteJobs, remoteJobTypeConfigs, remoteVenues)
                
                    // Refresh all data after successful sync
                    refreshAllData()
            
            // Save sync time
                val currentTime = System.currentTimeMillis()
            context?.let { ctx ->
                val settingsManager = SettingsManager(ctx)
                settingsManager.saveLastSyncTime(currentTime)
            }
                _lastSyncTime.value = currentTime
            
                println("Smart bidirectional sync completed successfully")
            
        } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    // Upload local changes to Google Sheets (App priority)
    private suspend fun uploadLocalChangesToSheets(
        localGuests: List<Guest>,
        localVolunteers: List<Volunteer>,
        localJobs: List<Job>,
        localJobTypeConfigs: List<JobTypeConfig>,
        localVenues: List<VenueEntity>
    ) {
        try {
            // Safety check: Only upload if we have local data to prevent clearing existing sheets data
            val hasLocalData = localGuests.isNotEmpty() || localVolunteers.isNotEmpty() || 
                              localJobs.isNotEmpty() || localJobTypeConfigs.isNotEmpty() || localVenues.isNotEmpty()
            
            if (!hasLocalData) {
                println("⚠️ No local data found - skipping upload to prevent clearing existing Google Sheets data")
                println("This is likely a first-time setup. Will only download from Google Sheets.")
                return
            }
            
            println("📤 Local data found - uploading to Google Sheets...")
            println("Local data - Guests: ${localGuests.size}, Volunteers: ${localVolunteers.size}, Jobs: ${localJobs.size}, JobTypes: ${localJobTypeConfigs.size}, Venues: ${localVenues.size}")
            
            // Upload to Google Sheets
            googleSheetsService.syncJobTypeConfigsToSheets(localJobTypeConfigs)
            googleSheetsService.syncGuestsToSheets(localGuests, localVenues)
            googleSheetsService.syncVolunteersToSheets(localVolunteers)
            googleSheetsService.syncJobsToSheets(localJobs, localVenues)
            googleSheetsService.syncVenuesToSheets(localVenues)
            
            println("Step 1 completed: Local changes uploaded to Google Sheets")
                } catch (e: Exception) {
            println("Failed to upload local changes: ${e.message}")
            throw e
        }
    }
    
    // Download changes from Google Sheets
    private suspend fun downloadChangesFromSheets(): Tuple5<List<Guest>, List<Volunteer>, List<Job>, List<JobTypeConfig>, List<VenueEntity>> {
        try {
            // Download job type configs first
            val remoteJobTypeConfigs = googleSheetsService.syncJobTypeConfigsFromSheets()
            println("Retrieved ${remoteJobTypeConfigs.size} job type configs from sheets")
            
            // Download venues
            val remoteVenues = googleSheetsService.syncVenuesFromSheets()
            println("Retrieved ${remoteVenues.size} venues from sheets")
            
            // Download all other data
            val (remoteGuests, remoteVolunteers, remoteJobs) = googleSheetsService.syncAllFromSheetsWithJobTypes(remoteJobTypeConfigs)
            println("Retrieved from sheets - Guests: ${remoteGuests.size}, Volunteers: ${remoteVolunteers.size}, Jobs: ${remoteJobs.size}")
            
            return Tuple5(remoteGuests, remoteVolunteers, remoteJobs, remoteJobTypeConfigs, remoteVenues)
            } catch (e: Exception) {
            println("Failed to download changes from sheets: ${e.message}")
            throw e
        }
    }
    
    // Smart merge with conflict resolution
    private suspend fun smartMergeData(
        localGuests: List<Guest>,
        localVolunteers: List<Volunteer>,
        localJobs: List<Job>,
        localJobTypeConfigs: List<JobTypeConfig>,
        localVenues: List<VenueEntity>,
        remoteGuests: List<Guest>,
        remoteVolunteers: List<Volunteer>,
        remoteJobs: List<Job>,
        remoteJobTypeConfigs: List<JobTypeConfig>,
        remoteVenues: List<VenueEntity>
    ) {
        // Get deleted items to prevent re-downloading
        val deletedGuests = deletionTracker?.getDeletedGuests() ?: emptyList()
        val deletedVolunteers = deletionTracker?.getDeletedVolunteers() ?: emptyList()
        val deletedJobs = deletionTracker?.getDeletedJobs() ?: emptyList()
        val deletedJobTypes = deletionTracker?.getDeletedJobTypes() ?: emptyList()
        val deletedVenues = deletionTracker?.getDeletedVenues() ?: emptyList()
        
        println("Deletion tracking - Guests: ${deletedGuests.size}, Volunteers: ${deletedVolunteers.size}, Jobs: ${deletedJobs.size}, JobTypes: ${deletedJobTypes.size}, Venues: ${deletedVenues.size}")
            var guestsAdded = 0
        var guestsUpdated = 0
            var volunteersAdded = 0
        var volunteersUpdated = 0
            var jobsAdded = 0
        var jobsUpdated = 0
        var jobTypesAdded = 0
        var jobTypesUpdated = 0
        
        // Merge Job Type Configs
        for (remoteConfig in remoteJobTypeConfigs) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobTypes.any { 
                it.sheetsId == remoteConfig.sheetsId || it.id == remoteConfig.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted job type config: ${remoteConfig.name}")
                continue
            }
            
            val localConfig = localJobTypeConfigs.find { it.name == remoteConfig.name }
            if (localConfig == null) {
                // New config from sheets
                try {
                    repository.insertJobTypeConfig(remoteConfig)
                    jobTypesAdded++
                    println("Added new job type config: ${remoteConfig.name}")
                } catch (e: Exception) {
                    println("Failed to add job type config: ${remoteConfig.name} - ${e.message}")
                }
            } else if (remoteConfig.lastModified > localConfig.lastModified) {
                // Remote version is newer
                try {
                    repository.updateJobTypeConfig(remoteConfig.copy(id = localConfig.id))
                    jobTypesUpdated++
                    println("Updated job type config: ${remoteConfig.name}")
        } catch (e: Exception) {
                    println("Failed to update job type config: ${remoteConfig.name} - ${e.message}")
                }
            }
        }
        
        // Merge Venues
        var venuesAdded = 0
        var venuesUpdated = 0
        for (remoteVenue in remoteVenues) {
            // Check if this item was deleted locally
            val isDeleted = deletedVenues.any { 
                it.sheetsId == remoteVenue.sheetsId || it.id == remoteVenue.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted venue: ${remoteVenue.name}")
                continue
            }
            
            val localVenue = localVenues.find { it.name == remoteVenue.name }
            if (localVenue == null) {
                // New venue from sheets
                try {
                    repository.insertVenue(remoteVenue)
                    venuesAdded++
                    println("Added new venue: ${remoteVenue.name}")
                } catch (e: Exception) {
                    println("Failed to add venue: ${remoteVenue.name} - ${e.message}")
                }
            } else if (remoteVenue.lastModified > localVenue.lastModified) {
                // Remote version is newer
                try {
                    repository.updateVenue(remoteVenue.copy(id = localVenue.id))
                    venuesUpdated++
                    println("Updated venue: ${remoteVenue.name}")
                } catch (e: Exception) {
                    println("Failed to update venue: ${remoteVenue.name} - ${e.message}")
                }
            }
        }
        
        // Merge Guests
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any { 
                it.sheetsId == remoteGuest.sheetsId || it.id == remoteGuest.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { it.sheetsId == remoteGuest.sheetsId || it.name == remoteGuest.name }
            if (localGuest == null) {
                // New guest from sheets
                try {
                    repository.insertGuest(remoteGuest)
                        guestsAdded++
                    println("Added new guest: ${remoteGuest.name}")
                } catch (e: Exception) {
                    println("Failed to add guest: ${remoteGuest.name} - ${e.message}")
                }
            } else if (remoteGuest.lastModified > localGuest.lastModified) {
                // Remote version is newer
                try {
                    repository.updateGuest(remoteGuest.copy(id = localGuest.id))
                    guestsUpdated++
                    println("Updated guest: ${remoteGuest.name}")
            } catch (e: Exception) {
                    println("Failed to update guest: ${remoteGuest.name} - ${e.message}")
                }
            }
        }
        
        // Merge Volunteers
        for (remoteVolunteer in remoteVolunteers) {
            // Check if this item was deleted locally
            val isDeleted = deletedVolunteers.any { 
                it.sheetsId == remoteVolunteer.sheetsId || it.id == remoteVolunteer.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name}")
                continue
            }
            
            val localVolunteer = localVolunteers.find { it.sheetsId == remoteVolunteer.sheetsId || it.name == remoteVolunteer.name }
            if (localVolunteer == null) {
                // New volunteer from sheets
                try {
                    repository.insertVolunteer(remoteVolunteer)
                        volunteersAdded++
                    println("Added new volunteer: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to add volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else if (remoteVolunteer.lastModified > localVolunteer.lastModified) {
                // Remote version is newer
                try {
                    repository.updateVolunteer(remoteVolunteer.copy(id = localVolunteer.id))
                    volunteersUpdated++
                    println("Updated volunteer: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to update volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            }
        }
        
        // Merge Jobs
        for (remoteJob in remoteJobs) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobs.any { 
                it.sheetsId == remoteJob.sheetsId || it.id == remoteJob.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }
            
            val localJob = localJobs.find { 
                it.sheetsId == remoteJob.sheetsId || 
                (it.volunteerId == remoteJob.volunteerId && it.date == remoteJob.date && it.jobTypeName == remoteJob.jobTypeName)
            }
            if (localJob == null) {
                // New job from sheets
                try {
                    repository.insertJob(remoteJob)
                        jobsAdded++
                    println("Added new job: ${remoteJob.jobTypeName}")
            } catch (e: Exception) {
                    println("Failed to add job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            } else if (remoteJob.lastModified > localJob.lastModified) {
                // Remote version is newer
                try {
                    repository.updateJob(remoteJob.copy(id = localJob.id))
                    jobsUpdated++
                    println("Updated job: ${remoteJob.jobTypeName}")
                } catch (e: Exception) {
                    println("Failed to update job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            }
        }
        
        println("Smart merge results:")
        println("Job Types: +$jobTypesAdded ~$jobTypesUpdated")
        println("Venues: +$venuesAdded ~$venuesUpdated")
        println("Guests: +$guestsAdded ~$guestsUpdated")
        println("Volunteers: +$volunteersAdded ~$volunteersUpdated")
        println("Jobs: +$jobsAdded ~$jobsUpdated")
    }
    
    // Helper data class for returning multiple values
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    
    // Helper methods for targeted sync operations
    private suspend fun uploadGuestsToSheets(guests: List<Guest>) {
        try {
            val venues = repository.getAllVenues().first()
            googleSheetsService.syncGuestsToSheets(guests, venues)
            println("Uploaded ${guests.size} guests to sheets")
        } catch (e: Exception) {
            println("Failed to upload guests: ${e.message}")
            throw e
        }
    }
    
    private suspend fun uploadVolunteersToSheets(volunteers: List<Volunteer>) {
        try {
            googleSheetsService.syncVolunteersToSheets(volunteers)
            println("Uploaded ${volunteers.size} volunteers to sheets")
            } catch (e: Exception) {
            println("Failed to upload volunteers: ${e.message}")
            throw e
        }
    }
    
    private suspend fun uploadJobsToSheets(jobs: List<Job>) {
        try {
            val venues = repository.getAllVenues().first()
            googleSheetsService.syncJobsToSheets(jobs, venues)
            println("Uploaded ${jobs.size} jobs to sheets")
        } catch (e: Exception) {
            println("Failed to upload jobs: ${e.message}")
            throw e
        }
    }
    
    private suspend fun uploadJobTypesToSheets(jobTypes: List<JobTypeConfig>) {
        try {
            googleSheetsService.syncJobTypeConfigsToSheets(jobTypes)
            println("Uploaded ${jobTypes.size} job types to sheets")
        } catch (e: Exception) {
            println("Failed to upload job types: ${e.message}")
            throw e
        }
    }
    
    private suspend fun downloadGuestsFromSheets(): List<Guest> {
        try {
            val guests = googleSheetsService.syncGuestsFromSheets()
            println("Downloaded ${guests.size} guests from sheets")
            return guests
        } catch (e: Exception) {
            println("Failed to download guests: ${e.message}")
            throw e
        }
    }
    
    private suspend fun downloadVolunteersFromSheets(): List<Volunteer> {
        try {
            val volunteers = googleSheetsService.syncVolunteersFromSheets()
            println("Downloaded ${volunteers.size} volunteers from sheets")
            return volunteers
        } catch (e: Exception) {
            println("Failed to download volunteers: ${e.message}")
            throw e
        }
    }
    
    private suspend fun downloadJobsFromSheets(jobTypeConfigs: List<JobTypeConfig>): List<Job> {
        try {
            val jobs = googleSheetsService.syncJobsFromSheets(jobTypeConfigs)
            println("Downloaded ${jobs.size} jobs from sheets")
            return jobs
        } catch (e: Exception) {
            println("Failed to download jobs: ${e.message}")
            throw e
        }
    }
    
    private suspend fun downloadJobTypesFromSheets(): List<JobTypeConfig> {
        try {
            val jobTypes = googleSheetsService.syncJobTypeConfigsFromSheets()
            println("Downloaded ${jobTypes.size} job types from sheets")
            return jobTypes
        } catch (e: Exception) {
            println("Failed to download job types: ${e.message}")
            throw e
        }
    }

    private suspend fun downloadVenuesFromSheets(): List<VenueEntity> {
        try {
            val venues = googleSheetsService.syncVenuesFromSheets()
            println("Downloaded ${venues.size} venues from sheets")
            return venues
        } catch (e: Exception) {
            println("Failed to download venues: ${e.message}")
            throw e
        }
    }
    
    private suspend fun mergeGuestData(localGuests: List<Guest>, remoteGuests: List<Guest>) {
        val deletedGuests = deletionTracker?.getDeletedGuests() ?: emptyList()
        var guestsAdded = 0
        var guestsUpdated = 0
        
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any { 
                it.sheetsId == remoteGuest.sheetsId || it.id == remoteGuest.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { it.sheetsId == remoteGuest.sheetsId || it.name == remoteGuest.name }
            if (localGuest == null) {
                // New guest from sheets
                try {
                    repository.insertGuest(remoteGuest)
                    guestsAdded++
                    println("Added new guest: ${remoteGuest.name}")
            } catch (e: Exception) {
                    println("Failed to add guest: ${remoteGuest.name} - ${e.message}")
                }
            } else if (remoteGuest.lastModified > localGuest.lastModified) {
                // Remote version is newer
                try {
                    repository.updateGuest(remoteGuest.copy(id = localGuest.id))
                    guestsUpdated++
                    println("Updated guest: ${remoteGuest.name}")
                } catch (e: Exception) {
                    println("Failed to update guest: ${remoteGuest.name} - ${e.message}")
                }
            }
        }
        
        println("Guest merge results: +$guestsAdded ~$guestsUpdated")
    }
    
    private suspend fun mergeVolunteerData(localVolunteers: List<Volunteer>, remoteVolunteers: List<Volunteer>) {
        val deletedVolunteers = deletionTracker?.getDeletedVolunteers() ?: emptyList()
        var volunteersAdded = 0
        var volunteersUpdated = 0
        
        // Get all jobs to calculate activity
        val allJobs = repository.getAllJobs().first()
        
        for (remoteVolunteer in remoteVolunteers) {
            // Check if this item was deleted locally
            val isDeleted = deletedVolunteers.any { 
                it.sheetsId == remoteVolunteer.sheetsId || it.id == remoteVolunteer.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name}")
                continue
            }
            
            val localVolunteer = localVolunteers.find { it.sheetsId == remoteVolunteer.sheetsId || it.name == remoteVolunteer.name }
            if (localVolunteer == null) {
                // New volunteer from sheets
                try {
                    // Calculate activity based on job assignments
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobs(remoteVolunteer, allJobs)
                    repository.insertVolunteer(updatedVolunteer)
                    volunteersAdded++
                    println("Added new volunteer: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to add volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else if (remoteVolunteer.lastModified > localVolunteer.lastModified) {
                // Remote version is newer
                try {
                    // Calculate activity based on job assignments
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobs(remoteVolunteer, allJobs)
                    repository.updateVolunteer(updatedVolunteer.copy(id = localVolunteer.id))
                    volunteersUpdated++
                    println("Updated volunteer: ${remoteVolunteer.name}")
        } catch (e: Exception) {
                    println("Failed to update volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else {
                // Local version is newer or same - update activity based on jobs
                try {
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobs(localVolunteer, allJobs)
                    if (updatedVolunteer.lastShiftDate != localVolunteer.lastShiftDate || 
                        updatedVolunteer.isActive != localVolunteer.isActive) {
                        repository.updateVolunteer(updatedVolunteer)
                        volunteersUpdated++
                        println("Updated volunteer activity: ${localVolunteer.name} - active: ${updatedVolunteer.isActive}")
                    }
                } catch (e: Exception) {
                    println("Failed to update volunteer activity: ${localVolunteer.name} - ${e.message}")
                }
            }
        }
        
        println("Volunteer merge results: +$volunteersAdded ~$volunteersUpdated")
    }
    
    // Sheets priority merge methods (remote data wins conflicts)
    private suspend fun mergeGuestDataSheetsPriority(localGuests: List<Guest>, remoteGuests: List<Guest>) {
        val deletedGuests = deletionTracker?.getDeletedGuests() ?: emptyList()
        var guestsAdded = 0
        var guestsUpdated = 0
        
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any { 
                it.sheetsId == remoteGuest.sheetsId || it.id == remoteGuest.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { it.sheetsId == remoteGuest.sheetsId || it.name == remoteGuest.name }
            if (localGuest == null) {
                // New guest from sheets
                try {
                    repository.insertGuest(remoteGuest)
                    guestsAdded++
                    println("Added new guest from sheets: ${remoteGuest.name}")
                } catch (e: Exception) {
                    println("Failed to add guest: ${remoteGuest.name} - ${e.message}")
                }
            } else {
                // Always use remote version (sheets priority)
                try {
                    repository.updateGuest(remoteGuest.copy(id = localGuest.id))
                    guestsUpdated++
                    println("Updated guest from sheets: ${remoteGuest.name}")
            } catch (e: Exception) {
                    println("Failed to update guest: ${remoteGuest.name} - ${e.message}")
                }
            }
        }
        
        println("Sheets priority guest merge results: +$guestsAdded ~$guestsUpdated")
    }
    
    private suspend fun mergeVolunteerDataSheetsPriority(localVolunteers: List<Volunteer>, remoteVolunteers: List<Volunteer>) {
        val deletedVolunteers = deletionTracker?.getDeletedVolunteers() ?: emptyList()
        var volunteersAdded = 0
        var volunteersUpdated = 0

        // Get all jobs to calculate activity
        val allJobs = repository.getAllJobs().first()

        for (remoteVolunteer in remoteVolunteers) {
            // Check if this item was deleted locally
            val isDeleted = deletedVolunteers.any {
                it.sheetsId == remoteVolunteer.sheetsId || it.id == remoteVolunteer.id.toString()
            }

            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name}")
                continue
            }

            val localVolunteer = localVolunteers.find { it.sheetsId == remoteVolunteer.sheetsId || it.name == remoteVolunteer.name }
            if (localVolunteer == null) {
                // New volunteer from sheets
                try {
                    // Calculate activity based on job assignments
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobs(remoteVolunteer, allJobs)
                    repository.insertVolunteer(updatedVolunteer)
                    volunteersAdded++
                    println("Added new volunteer from sheets: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to add volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else {
                // Always use remote version (sheets priority)
                try {
                    // Calculate activity based on job assignments
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobs(remoteVolunteer, allJobs)
                    repository.updateVolunteer(updatedVolunteer.copy(id = localVolunteer.id))
                    volunteersUpdated++
                    println("Updated volunteer from sheets: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to update volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            }
        }

        println("Sheets priority volunteer merge results: +$volunteersAdded ~$volunteersUpdated")
    }
    
    private suspend fun mergeJobDataSheetsPriority(localJobs: List<Job>, remoteJobs: List<Job>) {
        val deletedJobs = deletionTracker?.getDeletedJobs() ?: emptyList()
        var jobsAdded = 0
        var jobsUpdated = 0

        for (remoteJob in remoteJobs) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobs.any {
                it.sheetsId == remoteJob.sheetsId || it.id == remoteJob.id.toString()
            }

            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }

            val localJob = localJobs.find { it.sheetsId == remoteJob.sheetsId }
            if (localJob == null) {
                // New job from sheets
                try {
                    repository.insertJob(remoteJob)
                    jobsAdded++
                    println("Added new job from sheets: ${remoteJob.jobTypeName}")
            } catch (e: Exception) {
                    println("Failed to add job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            } else {
                // Always use remote version (sheets priority)
                try {
                    repository.updateJob(remoteJob.copy(id = localJob.id))
                    jobsUpdated++
                    println("Updated job from sheets: ${remoteJob.jobTypeName}")
                } catch (e: Exception) {
                    println("Failed to update job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            }
        }

        println("Sheets priority job merge results: +$jobsAdded ~$jobsUpdated")
    }
    
    private suspend fun mergeJobTypeDataSheetsPriority(localJobTypes: List<JobTypeConfig>, remoteJobTypes: List<JobTypeConfig>) {
        val deletedJobTypes = deletionTracker?.getDeletedJobTypes() ?: emptyList()
        var jobTypesAdded = 0
        var jobTypesUpdated = 0

        for (remoteJobType in remoteJobTypes) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobTypes.any {
                it.sheetsId == remoteJobType.sheetsId || it.id == remoteJobType.id.toString()
            }

            if (isDeleted) {
                println("Skipping deleted job type: ${remoteJobType.name}")
                continue
            }

            val localJobType = localJobTypes.find { it.sheetsId == remoteJobType.sheetsId || it.name == remoteJobType.name }
            if (localJobType == null) {
                // New job type from sheets
                try {
                    repository.insertJobTypeConfig(remoteJobType)
                    jobTypesAdded++
                    println("Added new job type from sheets: ${remoteJobType.name}")
            } catch (e: Exception) {
                    println("Failed to add job type: ${remoteJobType.name} - ${e.message}")
                }
            } else {
                // Always use remote version (sheets priority)
                try {
                    repository.updateJobTypeConfig(remoteJobType.copy(id = localJobType.id))
                    jobTypesUpdated++
                    println("Updated job type from sheets: ${remoteJobType.name}")
                } catch (e: Exception) {
                    println("Failed to update job type: ${remoteJobType.name} - ${e.message}")
                }
            }
        }

        println("Sheets priority job type merge results: +$jobTypesAdded ~$jobTypesUpdated")
    }
    
    private suspend fun mergeJobData(localJobs: List<Job>, remoteJobs: List<Job>) {
        val deletedJobs = deletionTracker?.getDeletedJobs() ?: emptyList()
        var jobsAdded = 0
        var jobsUpdated = 0
        
        for (remoteJob in remoteJobs) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobs.any { 
                it.sheetsId == remoteJob.sheetsId || it.id == remoteJob.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }
            
            val localJob = localJobs.find { 
                it.sheetsId == remoteJob.sheetsId || 
                (it.volunteerId == remoteJob.volunteerId && it.date == remoteJob.date && it.jobTypeName == remoteJob.jobTypeName)
            }
            if (localJob == null) {
                // New job from sheets
                try {
                    repository.insertJob(remoteJob)
                    jobsAdded++
                    println("Added new job: ${remoteJob.jobTypeName}")
                } catch (e: Exception) {
                    println("Failed to add job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            } else if (remoteJob.lastModified > localJob.lastModified) {
                // Remote version is newer
                try {
                    repository.updateJob(remoteJob.copy(id = localJob.id))
                    jobsUpdated++
                    println("Updated job: ${remoteJob.jobTypeName}")
                } catch (e: Exception) {
                    println("Failed to update job: ${remoteJob.jobTypeName} - ${e.message}")
                }
            }
        }
        
        println("Job merge results: +$jobsAdded ~$jobsUpdated")
    }
    
    private suspend fun mergeJobTypeData(localJobTypes: List<JobTypeConfig>, remoteJobTypes: List<JobTypeConfig>) {
        val deletedJobTypes = deletionTracker?.getDeletedJobTypes() ?: emptyList()
        var jobTypesAdded = 0
        var jobTypesUpdated = 0
        
        for (remoteJobType in remoteJobTypes) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobTypes.any { 
                it.sheetsId == remoteJobType.sheetsId || it.id == remoteJobType.id.toString() 
            }
            
            if (isDeleted) {
                println("Skipping deleted job type: ${remoteJobType.name}")
                continue
            }
            
            val localJobType = localJobTypes.find { it.name == remoteJobType.name }
            if (localJobType == null) {
                // New job type from sheets
                try {
                    repository.insertJobTypeConfig(remoteJobType)
                    jobTypesAdded++
                    println("Added new job type: ${remoteJobType.name}")
                } catch (e: Exception) {
                    println("Failed to add job type: ${remoteJobType.name} - ${e.message}")
                }
            } else if (remoteJobType.lastModified > localJobType.lastModified) {
                // Remote version is newer
                try {
                    repository.updateJobTypeConfig(remoteJobType.copy(id = localJobType.id))
                    jobTypesUpdated++
                    println("Updated job type: ${remoteJobType.name}")
                } catch (e: Exception) {
                    println("Failed to update job type: ${remoteJobType.name} - ${e.message}")
                }
            }
        }
        
        println("Job type merge results: +$jobTypesAdded ~$jobTypesUpdated")
    }
    
    private suspend fun refreshGuestData() {
        val updatedGuests = repository.getAllGuests().first()
        _guests.value = removeDuplicateGuests(updatedGuests)
    }
    
    private suspend fun refreshVolunteerData() {
        val updatedVolunteers = repository.getAllVolunteers().first()
        val uniqueVolunteers = removeDuplicateVolunteers(updatedVolunteers)
        _volunteers.value = uniqueVolunteers
        println("🔄 Refreshed volunteer data: ${uniqueVolunteers.size} volunteers")
    }
    
    private suspend fun refreshJobData() {
        val updatedJobs = repository.getAllJobs().first()
        _jobs.value = removeDuplicateJobs(updatedJobs)
    }
    
    private suspend fun refreshJobTypeData() {
        val updatedJobTypes = repository.getAllJobTypeConfigs().first()
        _jobTypeConfigs.value = removeDuplicateJobTypes(updatedJobTypes)
    }
    
    private suspend fun refreshVenueData() {
        val updatedVenues = repository.getAllVenues().first()
        _venues.value = removeDuplicateVenues(updatedVenues)
    }
    
    private suspend fun updateSyncTime() {
        val currentTime = System.currentTimeMillis()
        context?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            settingsManager.saveLastSyncTime(currentTime)
        }
        _lastSyncTime.value = currentTime
    }

    // Sync app data to Google Sheets
    private suspend fun syncToGoogleSheets() {
        try {
            if (!isGoogleSheetsConfigured()) {
                println("Google Sheets not configured, skipping sync")
                return
            }
            
            // Initialize Google Sheets service
            googleSheetsService.initializeSheetsService()
            
            // Get current data
            val currentGuests = repository.getAllGuests().first()
            val currentVolunteers = repository.getAllActiveVolunteers().first()
            val currentJobs = repository.getAllJobs().first()
            val currentJobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val currentVenues = repository.getAllVenues().first()
            
            // Safety check: Only upload if we have local data to prevent clearing existing sheets data
            val hasLocalData = currentGuests.isNotEmpty() || currentVolunteers.isNotEmpty() || 
                              currentJobs.isNotEmpty() || currentJobTypeConfigs.isNotEmpty()
            
            if (!hasLocalData) {
                println("⚠️ No local data found - skipping upload to prevent clearing existing Google Sheets data")
                println("This is likely a first-time setup. Will only download from Google Sheets.")
                return
            }
            
            println("📤 Local data found - syncing to Google Sheets...")
            println("Local data - Guests: ${currentGuests.size}, Volunteers: ${currentVolunteers.size}, Jobs: ${currentJobs.size}, JobTypes: ${currentJobTypeConfigs.size}")
            
            // Sync to Google Sheets
            googleSheetsService.syncJobTypeConfigsToSheets(currentJobTypeConfigs)
            googleSheetsService.syncGuestsToSheets(currentGuests, currentVenues)
            googleSheetsService.syncVolunteersToSheets(currentVolunteers)
            googleSheetsService.syncJobsToSheets(currentJobs, currentVenues)
            
            // Update last sync time
            val currentTime = System.currentTimeMillis()
            context?.let { ctx ->
                val settingsManager = SettingsManager(ctx)
                settingsManager.saveLastSyncTime(currentTime)
            }
            _lastSyncTime.value = currentTime
            
            println("Data synced to Google Sheets successfully")
                
            } catch (e: Exception) {
            println("Failed to sync to Google Sheets: ${e.message}")
            _syncError.value = "Failed to sync to Google Sheets: ${e.message}"
        }
    }
    
    // NEW TWO-WAY SYNC METHODS
    
    /**
     * SYNC MODE: Download entire dataset from Google Sheets and replace local data
     * This is used for manual sync and scheduled sync
     */
    fun performFullSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            AppLogger.i("EventManagerViewModel", "Starting full sync")
            
            try {
                val result = syncManager?.performFullSync()
                
                if (result?.isSuccess == true) {
                    // Refresh all data after successful sync
                    refreshAllData()
                    recalcAndUploadVolunteerGuestList()
                    AppLogger.i("EventManagerViewModel", "Full sync completed successfully")
                    println("Full sync completed successfully")
                } else {
                    val errorResult = result as? SyncResult.Error
                    val errorMsg = errorResult?.message ?: "Full sync failed"
                    _syncError.value = errorMsg
                    AppLogger.e("EventManagerViewModel", "Full sync failed: $errorMsg")
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Full sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                AppLogger.e("EventManagerViewModel", "Full sync exception", e)
                showSyncErrorIfNotSuppressed(errorMsg)
                println("Full sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Unified guest list: regular guests + volunteer benefits (computed locally)
    private suspend fun computeVolunteerGuestEntries(): List<Guest> {
        try {
            val statuses = repository.getAllVolunteerBenefitStatuses()
            val now = System.currentTimeMillis()
            val entries = mutableListOf<Guest>()
            
            // Batch load all volunteers instead of querying one-by-one
            val allVolunteers = repository.getAllVolunteers().first()
            val volunteersById = allVolunteers.associateBy { it.id }
            
            println("Computing volunteer guest entries from ${statuses.size} volunteer benefit statuses with ${allVolunteers.size} volunteers")
            
            for (status in statuses) {
                val benefits = status.benefits
                if (benefits.isActive && benefits.guestListAccess && (benefits.validUntil == null || now < benefits.validUntil)) {
                    val volunteer = volunteersById[status.volunteerId]
                    if (volunteer != null) {
                        val invitations = status.benefits.inviteCount
                        entries.add(
                            Guest(
                                name = volunteer.name,
                                lastNameAbbreviation = volunteer.lastNameAbbreviation,
                                invitations = invitations,
                                venueName = "BOTH",
                                notes = "Volunteer benefit - ${getRankDisplayName(status.rank)}",
                                isVolunteerBenefit = true,
                                volunteerId = volunteer.id
                            )
                        )
                        println("Added volunteer to guest list: ${volunteer.name} (${getRankDisplayName(status.rank)}) - ${invitations} invitations")
                    } else {
                        println("Warning: Volunteer with ID ${status.volunteerId} not found for benefit status")
                    }
                } else {
                    
                }
            }
            
            println("Computed ${entries.size} volunteer guest entries")
            return entries
        } catch (e: Exception) {
            println("Error computing volunteer guest entries: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    suspend fun recalcAndUploadVolunteerGuestList() = withContext(Dispatchers.IO) {
        try {
            println("Starting volunteer guest list recalculation...")

            // Compute volunteer entries locally
            val volunteerGuests = computeVolunteerGuestEntries()
            println("Computed ${volunteerGuests.size} volunteer guest entries")

            // Update local guest table: remove stale volunteer benefit entries, then re-add current
            val existingVolunteerGuests = repository.getVolunteerBenefitGuests()
            println("Found ${existingVolunteerGuests.size} existing volunteer benefit guests to remove")

            // Remove old ones
            existingVolunteerGuests.forEach { repository.deleteGuest(it) }
            println("Removed ${existingVolunteerGuests.size} old volunteer benefit guests")

            // Insert current list
            volunteerGuests.forEach { repository.insertGuest(it) }
            println("Inserted ${volunteerGuests.size} new volunteer benefit guests")

            // Upload-only to Volunteer Guest List sheet
            if (isGoogleSheetsConfigured()) {
                println("Uploading volunteer guest list to Google Sheets...")
                googleSheetsService.initializeSheetsService()
                googleSheetsService.syncVolunteerGuestListToSheets(volunteerGuests, _venues.value)
                println("Successfully uploaded volunteer guest list to Google Sheets")
            } else {
                println("Google Sheets not configured, skipping upload")
            }

            // Refresh UI state on main thread
            withContext(Dispatchers.Main) {
                refreshGuestData()
            }
            println("Volunteer guest list recalculation completed successfully")
        } catch (e: Exception) {
            println("Failed to recalc/upload volunteer guest list: ${e.message}")
            e.printStackTrace()
        }
    }

    // Startup-only variant: recalc volunteer guest list locally without uploading
    private suspend fun recalcVolunteerGuestListNoUpload() = withContext(Dispatchers.IO) {
        try {
            println("Startup: recalculating volunteer guest list locally without upload...")
            val volunteerGuests = computeVolunteerGuestEntries()
            val existingVolunteerGuests = repository.getVolunteerBenefitGuests()
            existingVolunteerGuests.forEach { repository.deleteGuest(it) }
            volunteerGuests.forEach { repository.insertGuest(it) }
            withContext(Dispatchers.Main) {
                refreshGuestData()
            }
            println("Startup: volunteer guest list recalculation done (no upload)")
        } catch (e: Exception) {
            println("Startup: failed to recalc volunteer guest list: ${e.message}")
        }
    }
    
    /**
     * PAGE CHANGE SYNC: Download only current page and new page data
     * This is used when user changes pages in the app
     */
    fun performPageChangeSync(currentPage: String, newPage: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = syncManager?.performSmartPageChangeSync(currentPage, newPage)
                
                if (result?.isSuccess == true) {
                    // Refresh all data after successful sync
                    refreshAllData()
                    println("Page change sync completed successfully")
                } else {
                    val errorResult = result as? SyncResult.Error
                    _syncError.value = errorResult?.message ?: "Page change sync failed"
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Page change sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Page change sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * SIMPLE PAGE CHANGE SYNC: Download only current page and new page data
     * This is used when user changes pages in the app (simpler version)
     */
    fun performSimplePageChangeSync(currentPage: String, newPage: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = syncManager?.performPageChangeSync(currentPage, newPage)
                
                if (result?.isSuccess == true) {
                    // Refresh all data after successful sync
                    refreshAllData()
                    println("Simple page change sync completed successfully")
                } else {
                    val errorResult = result as? SyncResult.Error
                    val errorMsg = errorResult?.message ?: "Page change sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Page change sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("Page change sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * BACKUP MODE: Upload entire local dataset to Google Sheets
     * This overwrites the corresponding Google Sheet tab completely
     */
    fun performBackupToSheets() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = syncManager?.performBackupToSheets()
                
                if (result?.isSuccess == true) {
                    // Update sync time
                    updateSyncTime()
                    println("Backup to Google Sheets completed successfully")
                } else {
                    val errorResult = result as? SyncResult.Error
                    _syncError.value = errorResult?.message ?: "Backup failed"
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Backup failed: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Backup error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * VALIDATION: Check Google Sheets structure
     */
    fun validateGoogleSheetsStructure() {
        viewModelScope.launch {
            try {
                val diagnostics = syncManager?.validateGoogleSheetsStructure()
                if (diagnostics != null) {
                    println("Google Sheets validation: $diagnostics")
                    _syncError.value = "Validation completed. Check logs for details."
                } else {
                    _syncError.value = "Validation failed: SyncManager not available"
                }
            } catch (e: Exception) {
                _syncError.value = "Validation failed: ${e.message}"
                println("Validation error: ${e.message}")
            }
        }
    }
    
    /**
     * DATA STRUCTURE VALIDATION: Ensure consistent headers and data structure
     */
    fun validateDataStructure() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = syncManager?.validateDataStructure()
                if (result?.isSuccess == true) {
                    val successResult = result as ValidationResult.Success
                    if (successResult.isValid) {
                        _syncError.value = "Data structure validation passed: ${successResult.message}"
                    } else {
                        _syncError.value = "Data structure validation failed: ${successResult.message}"
                    }
                    println("Data structure validation: ${successResult.message}")
                } else {
                    val errorResult = result as? ValidationResult.Error
                    _syncError.value = errorResult?.message ?: "Data structure validation failed"
                }
            } catch (e: Exception) {
                _syncError.value = "Data structure validation failed: ${e.message}"
                println("Data structure validation error: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * CREATE OR FIX SHEET STRUCTURE: Ensure all sheets have correct headers
     */
    fun createOrFixSheetStructure() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = syncManager?.createOrFixSheetStructure()
                if (result?.isSuccess == true) {
                    val successResult = result as ValidationResult.Success
                    if (successResult.isValid) {
                        _syncError.value = "Sheet structure created/fixed successfully: ${successResult.message}"
                    } else {
                        _syncError.value = "Sheet structure creation/fix failed: ${successResult.message}"
                    }
                    println("Sheet structure creation/fix: ${successResult.message}")
                } else {
                    val errorResult = result as? ValidationResult.Error
                    _syncError.value = errorResult?.message ?: "Sheet structure creation/fix failed"
                }
            } catch (e: Exception) {
                _syncError.value = "Sheet structure creation/fix failed: ${e.message}"
                println("Sheet structure creation/fix error: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * GET EXPECTED HEADERS: Get the expected headers for a sheet type
     */
    fun getExpectedHeaders(sheetType: String): List<String>? {
        return syncManager?.getExpectedHeaders(sheetType)
    }
    
    /**
     * GET ALL EXPECTED HEADERS: Get all expected headers for all sheet types
     */
    fun getAllExpectedHeaders(): Map<String, List<String>> {
        return syncManager?.getAllExpectedHeaders() ?: emptyMap()
    }

    // Convenience methods for different sync modes
    fun performAutoSync() = performFullSync()

    fun clearSyncError() {
        _syncError.value = null
    }
    
    /**
     * UI CONVENIENCE METHODS for page change sync
     */
    fun onPageChangeToGuests() {
        performPageChangeSync("", "guests")
    }
    
    fun onPageChangeToVolunteers() {
        performPageChangeSync("", "volunteers")
    }
    
    fun onPageChangeToJobs() {
        performPageChangeSync("", "jobs")
    }
    
    fun onPageChangeToJobTypes() {
        performPageChangeSync("", "job_types")
    }
    
    fun onPageChange(from: String, to: String) {
        performPageChangeSync(from, to)
    }
    
    /**
     * MANUAL SYNC TRIGGERS
     */
    fun triggerManualSync() {
        performFullSync()
    }
    
    fun triggerBackupToSheets() {
        performBackupToSheets()
    }
    
    fun triggerValidation() {
        validateGoogleSheetsStructure()
    }
    
    fun triggerDataStructureValidation() {
        validateDataStructure()
    }
    
    fun triggerCreateOrFixSheetStructure() {
        createOrFixSheetStructure()
    }
    
    fun triggerDuplicateCleanup() {
        cleanupDuplicates()
    }
    
    /**
     * Clear local app data cache (database contents) without touching settings or keys.
     * This removes guests, volunteers, jobs, job types and venues so a fresh sync can repopulate them.
     */
    fun clearAppData() {
        viewModelScope.launch {
            try {
                println("Clearing all local app data (database tables)...")
                repository.clearAllData()
                
                // Refresh in-memory state so UI immediately reflects the cleared data
                refreshAllData()
                
                println("All local app data cleared successfully")
            } catch (e: Exception) {
                println("Failed to clear local app data: ${e.message}")
            }
        }
    }
    
    /**
     * FORCE REFRESH VOLUNTEERS: Force sync volunteers from Google Sheets
     * This is useful for debugging and ensuring volunteers are properly loaded
     */
    fun forceRefreshVolunteers() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Force refreshing volunteers from Google Sheets...")
                twoWaySyncService?.syncVolunteersOnly()
                
                // The continuous .collect() in loadData() will automatically pick up the repository changes
                
                println("Force refresh volunteers completed successfully")
                _syncError.value = "Volunteers refreshed successfully"
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Failed to refresh volunteers: ${e.message}"
                }
                _syncError.value = errorMsg
                println("Force refresh volunteers error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    fun clearAllErrors() {
        _syncError.value = null
    }
    
    /**
     * FORCE REFRESH ALL DATA: Force refresh all data from database
     * This is useful for debugging and ensuring data is properly loaded
     */
    fun forceRefreshAllData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("Force refreshing all data from database...")
                
                // Refresh all data
                refreshAllData()
                
                // Update volunteer activity
                updateVolunteerActivityFromJobs()
                
                // Recalculate volunteer guest list
                recalcAndUploadVolunteerGuestList()
                
                println("Force refresh all data completed successfully")
                _syncError.value = "All data refreshed successfully"
            } catch (e: Exception) {
                val errorMsg = "Failed to refresh all data: ${e.message}"
                _syncError.value = errorMsg
                println("Force refresh all data error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    private fun refreshAllData() {
        viewModelScope.launch {
            try {
                // Get data from repository
                val guests = repository.getAllGuests().first()
                val volunteers = repository.getAllVolunteers().first()
                val jobs = repository.getAllJobs().first()
                val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
                
                // Remove duplicates and update UI
                _guests.value = removeDuplicateGuests(guests)
                _volunteers.value = removeDuplicateVolunteers(volunteers)
                _jobs.value = removeDuplicateJobs(jobs)
                _jobTypeConfigs.value = removeDuplicateJobTypes(jobTypeConfigs)
                
                println("Data refreshed - Guests: ${_guests.value.size}, Volunteers: ${_volunteers.value.size}, Jobs: ${_jobs.value.size}, Job Types: ${_jobTypeConfigs.value.size}")
            } catch (e: Exception) {
                println("Failed to refresh data: ${e.message}")
            }
        }
    }
    
    /**
     * Updates volunteer activity based on current jobs
     */
    private fun updateVolunteerActivity(volunteers: List<Volunteer>): List<Volunteer> {
        val currentJobs = _jobs.value
        return if (currentJobs.isNotEmpty()) {
            VolunteerActivityManager.updateVolunteerActivityFromJobs(volunteers, currentJobs)
        } else {
            volunteers
        }
    }
    
    /**
     * Updates volunteer activity when jobs are loaded
     */
    private fun updateVolunteerActivityFromCurrentJobs() {
        // Run computation off the main thread to avoid blocking the UI
        viewModelScope.launch(Dispatchers.Default) {
            val currentVolunteers = _volunteers.value
            val currentJobs = _jobs.value

            if (currentVolunteers.isNotEmpty() && currentJobs.isNotEmpty()) {
                val updatedVolunteers =
                    VolunteerActivityManager.updateVolunteerActivityFromJobs(currentVolunteers, currentJobs)

                // Switch back to main thread only for state update
                withContext(Dispatchers.Main) {
                    _volunteers.value = updatedVolunteers
                    println("Updated volunteer activity for ${updatedVolunteers.size} volunteers based on ${currentJobs.size} jobs")
                }
            } else if (currentVolunteers.isNotEmpty() && currentJobs.isEmpty()) {
                // If no jobs are loaded yet, just set volunteers without activity calculation
                // Activity will be calculated when jobs are loaded
                println("Volunteers loaded but no jobs yet - activity will be calculated when jobs are available")
            }
        }
    }
    
    /**
     * CLEANUP DUPLICATES: Remove duplicate entries from the database
     * This is called once when the app starts to clean up any existing duplicates
     */
    private fun cleanupDuplicates() {
        viewModelScope.launch {
            try {
                println("Starting duplicate cleanup...")
                
                // Clean up duplicate guests
                val allGuests = repository.getAllGuests().first()
                val uniqueGuests = removeDuplicateGuests(allGuests)
                if (allGuests.size != uniqueGuests.size) {
                    println("Found ${allGuests.size - uniqueGuests.size} duplicate guests, cleaning up...")
                    repository.clearAllGuests()
                    uniqueGuests.forEach { repository.insertGuest(it) }
                }
                
                // Clean up duplicate volunteers
                val allVolunteers = repository.getAllVolunteers().first()
                val uniqueVolunteers = removeDuplicateVolunteers(allVolunteers)
                if (allVolunteers.size != uniqueVolunteers.size) {
                    println("Found ${allVolunteers.size - uniqueVolunteers.size} duplicate volunteers, cleaning up...")
                    repository.clearAllVolunteers()
                    uniqueVolunteers.forEach { repository.insertVolunteer(it) }
                }
                
                // Clean up duplicate jobs
                val allJobs = repository.getAllJobs().first()
                val uniqueJobs = removeDuplicateJobs(allJobs)
                if (allJobs.size != uniqueJobs.size) {
                    println("Found ${allJobs.size - uniqueJobs.size} duplicate jobs, cleaning up...")
                    repository.clearAllJobs()
                    uniqueJobs.forEach { repository.insertJob(it) }
                }
                
                // Clean up duplicate job types
                val allJobTypes = repository.getAllJobTypeConfigs().first()
                val uniqueJobTypes = removeDuplicateJobTypes(allJobTypes)
                if (allJobTypes.size != uniqueJobTypes.size) {
                    println("Found ${allJobTypes.size - uniqueJobTypes.size} duplicate job types, cleaning up...")
                    repository.clearAllJobTypeConfigs()
                    uniqueJobTypes.forEach { repository.insertJobTypeConfig(it) }
                }
                
                println("Duplicate cleanup completed")
                
            } catch (e: Exception) {
                println("Failed to cleanup duplicates: ${e.message}")
            }
        }
    }
    
    /**
     * DUPLICATE PREVENTION: Remove duplicate entries based on unique identifiers
     * This ensures the UI only shows unique items
     */
    // Cache for duplicate removal to avoid repeated work on same data
    private var lastGuestsHash: Int? = null
    private var cachedUniqueGuests: List<Guest>? = null
    
    private var lastVolunteersHash: Int? = null
    private var cachedUniqueVolunteers: List<Volunteer>? = null
    
    private var lastJobsHash: Int? = null
    private var cachedUniqueJobs: List<Job>? = null
    
    private fun removeDuplicateGuests(guests: List<Guest>): List<Guest> {
        // Use content hash for caching (simple but effective for this use case)
        val currentHash = guests.hashCode()
        if (lastGuestsHash == currentHash && cachedUniqueGuests != null) {
            return cachedUniqueGuests!!
        }
        
        val seen = mutableSetOf<String>()
        val result = guests.filter { guest ->
            val key = "${guest.name}_${guest.venueName}_${guest.invitations}"
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
        
        lastGuestsHash = currentHash
        cachedUniqueGuests = result
        return result
    }
    
    private fun removeDuplicateVolunteers(volunteers: List<Volunteer>): List<Volunteer> {
        val currentHash = volunteers.hashCode()
        if (lastVolunteersHash == currentHash && cachedUniqueVolunteers != null) {
            return cachedUniqueVolunteers!!
        }
        
        val seen = mutableSetOf<String>()
        val result = volunteers.filter { volunteer ->
            val key = "${volunteer.name}_${volunteer.email}_${volunteer.phoneNumber}"
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
        
        lastVolunteersHash = currentHash
        cachedUniqueVolunteers = result
        return result
    }
    
    private fun removeDuplicateJobs(jobs: List<Job>): List<Job> {
        val currentHash = jobs.hashCode()
        if (lastJobsHash == currentHash && cachedUniqueJobs != null) {
            return cachedUniqueJobs!!
        }
        
        val seen = mutableSetOf<String>()
        val result = jobs.filter { job ->
            // Prefer Sheets row identity when available to avoid mismatches after sync/delete
            val key = when {
                job.sheetsId != null -> "sheets_${job.sheetsId}"
                else -> "local_${job.volunteerId}_${job.jobTypeName}_${job.date}_${job.venueName}_${job.shiftTime}"
            }
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
        
        lastJobsHash = currentHash
        cachedUniqueJobs = result
        return result
    }
    
    private fun removeDuplicateJobTypes(jobTypes: List<JobTypeConfig>): List<JobTypeConfig> {
        // Job types are typically small lists, no caching needed
        val seen = mutableSetOf<String>()
        return jobTypes.filter { jobType ->
            val key = jobType.name
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
    }

    private fun removeDuplicateVenues(venues: List<VenueEntity>): List<VenueEntity> {
        // Venues are typically small lists, no caching needed
        val seen = mutableSetOf<String>()
        return venues.filter { venue ->
            val key = venue.name
            if (seen.contains(key)) {
                false
            } else {
                seen.add(key)
                true
            }
        }
    }

    // Get volunteer benefits with time-based calculation
    suspend fun getVolunteerBenefitStatus(volunteer: Volunteer): VolunteerBenefitStatus? {
        return repository.getVolunteerBenefitStatus(volunteer.id)
    }
    
    // Legacy method for backward compatibility
    fun getVolunteerBenefits(volunteer: Volunteer): Benefit {
        return BenefitCalculator.getBenefitsForRank(volunteer.currentRank)
    }

    // Check if Google Sheets is configured
    private fun isGoogleSheetsConfigured(): Boolean {
        return context?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            val isConfigured = settingsManager.isConfigured()
            val spreadsheetId = settingsManager.getSpreadsheetId()
            println("Google Sheets configuration check:")
            println("  - Spreadsheet ID: $spreadsheetId")
            println("  - Is configured: $isConfigured")
            isConfigured
        } ?: run {
            println("No context available for Google Sheets configuration check")
            false
        }
    }
    
    // Test sync status
    fun testSyncStatus() {
        viewModelScope.launch {
            try {
                if (isGoogleSheetsConfigured()) {
                    println("Google Sheets is configured")
                    _syncStatusMessage.value = context?.getString(com.eventmanager.app.R.string.sync_status_configured)
                        ?: "Google Sheets is configured and ready for sync"
                } else {
                    println("Google Sheets is not configured")
                    _syncStatusMessage.value = context?.getString(com.eventmanager.app.R.string.sync_status_not_configured)
                        ?: "Google Sheets is not configured. Please check your settings."
                }
                _showSyncStatusDialog.value = true
            } catch (e: Exception) {
                println("Error testing sync status: ${e.message}")
                _syncStatusMessage.value = context?.getString(com.eventmanager.app.R.string.sync_status_error, e.message ?: "Unknown error")
                    ?: "Error testing sync status: ${e.message}"
                _showSyncStatusDialog.value = true
            }
        }
    }
    
    /**
     * Dismiss sync status dialog
     */
    fun dismissSyncStatusDialog() {
        _showSyncStatusDialog.value = false
        _syncStatusMessage.value = null
    }
    
    // Update volunteer activity based on job assignments
    private fun updateVolunteerActivityFromJobs() {
        viewModelScope.launch {
            try {
                val volunteers = repository.getAllVolunteers().first()
                val jobs = repository.getAllJobs().first()
                
                println("Updating volunteer activity for ${volunteers.size} volunteers based on ${jobs.size} jobs")
                
                val updatedVolunteers = VolunteerActivityManager.updateVolunteerActivityFromJobs(volunteers, jobs)
                
                // Update volunteers whose activity has changed
                var updatedCount = 0
                updatedVolunteers.forEach { updatedVolunteer ->
                    val originalVolunteer = volunteers.find { it.id == updatedVolunteer.id }
                    if (originalVolunteer != null && 
                        (updatedVolunteer.lastShiftDate != originalVolunteer.lastShiftDate || 
                         updatedVolunteer.isActive != originalVolunteer.isActive)) {
                        repository.updateVolunteer(updatedVolunteer)
                        updatedCount++
                        println("Updated volunteer activity: ${updatedVolunteer.name} - last shift: ${updatedVolunteer.lastShiftDate}, active: ${updatedVolunteer.isActive}")
                    }
                }
                
                if (updatedCount > 0) {
                    println("Updated activity for $updatedCount volunteers")
                    // The continuous .collect() in loadData() will automatically pick up the repository changes
                } else {
                    println("No volunteer activity changes needed")
                }
            } catch (e: Exception) {
                println("Failed to update volunteer activity from jobs: ${e.message}")
            }
        }
    }
    
    // Cleanup inactive volunteers (customizable years without shift)
    fun cleanupInactiveVolunteers(yearsInactive: Int = 4) {
        viewModelScope.launch {
            try {
                val volunteers = repository.getAllVolunteers().first()
                val jobs = repository.getAllJobs().first()
                
                // Find volunteers that have been inactive for the specified number of years
                val volunteersToCleanup = volunteers.filter { volunteer ->
                    val daysSinceLastShift = VolunteerActivityManager.getDaysSinceLastShift(volunteer)
                    daysSinceLastShift != null && daysSinceLastShift >= (yearsInactive * 365L)
                }
                
                println("Found ${volunteersToCleanup.size} volunteers to cleanup (inactive for $yearsInactive+ years)")
                
                var volunteersDeleted = 0
                var jobsDeleted = 0
                
                volunteersToCleanup.forEach { volunteer ->
                    try {
                        // Find and delete all jobs associated with this volunteer
                        val volunteerJobs = jobs.filter { it.volunteerId == volunteer.id }
                        volunteerJobs.forEach { job ->
                            try {
                                repository.deleteJob(job)
                                jobsDeleted++
                                println("Deleted job: ${job.jobTypeName} for volunteer ${volunteer.name}")
                            } catch (e: Exception) {
                                println("Failed to delete job ${job.id} for volunteer ${volunteer.name}: ${e.message}")
                            }
                        }
                        
                        // Track the deletion
                        deletionTracker?.trackVolunteerDeletion(volunteer.id.toString(), volunteer.sheetsId)
                        
                        // Delete volunteer from local database
                        repository.deleteVolunteer(volunteer)
                        volunteersDeleted++
                        
                        println("Successfully cleaned up inactive volunteer: ${volunteer.name} and ${volunteerJobs.size} associated jobs")
                    } catch (e: Exception) {
                        println("Failed to cleanup volunteer ${volunteer.name}: ${e.message}")
                    }
                }
                
                if (volunteersDeleted > 0) {
                    // Refresh data to reflect changes
                    refreshAllData()
                    
                    // BACKUP MODE: Upload entire volunteer and job datasets to ensure cleanup is reflected
                    twoWaySyncService?.backupVolunteersToSheets()
                    twoWaySyncService?.backupJobsToSheets()
                    
                    _syncError.value = "Cleaned up $volunteersDeleted volunteer${if (volunteersDeleted != 1) "s" else ""} and $jobsDeleted job${if (jobsDeleted != 1) "s" else ""} (inactive for $yearsInactive+ years)"
                } else {
                    _syncError.value = "No volunteers found that have been inactive for $yearsInactive+ years"
                }
            } catch (e: Exception) {
                println("Failed to cleanup inactive volunteers: ${e.message}")
                _syncError.value = "Failed to cleanup inactive volunteers: ${e.message}"
            }
        }
    }

    /**
     * NEW DIFFERENTIAL FULL SYNC: Download and apply only changed data
     * 
     * This method implements efficient UI updates by:
     * 1. Downloading new data from Google Sheets (TEMP_DB)
     * 2. Comparing with local data (MAIN_DB)
     * 3. Identifying only changes (new, modified, deleted)
     * 4. Applying targeted UI updates instead of full-page reload
     * 
     * This avoids the performance hit of reloading the entire UI when
     * only a small portion of data actually changed.
     */
    fun performDifferentialFullSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            AppLogger.i("EventManagerViewModel", "Starting differential full sync")
            
            try {
                println("🔄 Starting differential full sync...")
                
                val result = syncManager?.performDifferentialSync()
                
                if (result is DifferentialSyncResult.Success) {
                    val changes = result.changes
                    val summary = changes.summary()
                    AppLogger.i("EventManagerViewModel", "Differential sync changes: $summary")
                    println("📊 $summary")
                    
                    // Apply targeted UI updates based on what actually changed
                    applyDifferentialUIUpdates(changes)
                    
                    // Recalculate volunteer guest list if needed
                    if (changes.volunteers.hasChanges) {
                        recalcAndUploadVolunteerGuestList()
                    }
                    
                    AppLogger.i("EventManagerViewModel", "Differential full sync completed successfully")
                    println("✅ Differential full sync completed successfully")
                } else {
                    val errorResult = result as? DifferentialSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Differential sync failed"
                    _syncError.value = errorMsg
                    AppLogger.e("EventManagerViewModel", "Differential sync failed: $errorMsg")
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Differential sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                AppLogger.e("EventManagerViewModel", "Differential sync exception", e)
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Differential sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY DIFFERENTIAL UI UPDATES
     * 
     * Instead of refreshing all UI state, this method applies only the necessary
     * updates based on what actually changed in the sync.
     * 
     * @param changes The DifferentialSyncResult containing new, modified, and deleted items
     */
    private suspend fun applyDifferentialUIUpdates(changes: DifferentialSyncService.DifferentialSyncResult) {
        try {
            val currentGuests = _guests.value.toMutableList()
            val currentVolunteers = _volunteers.value.toMutableList()
            val currentJobs = _jobs.value.toMutableList()
            val currentJobTypes = _jobTypeConfigs.value.toMutableList()
            val currentVenues = _venues.value.toMutableList()
            
            // Apply guest changes
            if (changes.guests.hasChanges) {
                // Remove deleted guests
                changes.guests.deleted.forEach { deletedGuest ->
                    currentGuests.removeAll { it.id == deletedGuest.id }
                }
                
                // Add new guests
                currentGuests.addAll(changes.guests.new)
                
                // Update modified guests
                changes.guests.modified.forEach { modifiedGuest ->
                    val index = currentGuests.indexOfFirst { it.id == modifiedGuest.id }
                    if (index >= 0) {
                        currentGuests[index] = modifiedGuest
                    }
                }
                
                _guests.value = removeDuplicateGuests(currentGuests)
                println("✅ Applied ${changes.guests.totalChanges} guest changes to UI")
            }
            
            // Apply volunteer changes
            if (changes.volunteers.hasChanges) {
                // Remove deleted volunteers
                changes.volunteers.deleted.forEach { deletedVolunteer ->
                    currentVolunteers.removeAll { it.id == deletedVolunteer.id }
                }
                
                // Add new volunteers
                currentVolunteers.addAll(changes.volunteers.new)
                
                // Update modified volunteers
                changes.volunteers.modified.forEach { modifiedVolunteer ->
                    val index = currentVolunteers.indexOfFirst { it.id == modifiedVolunteer.id }
                    if (index >= 0) {
                        currentVolunteers[index] = modifiedVolunteer
                    }
                }
                
                _volunteers.value = removeDuplicateVolunteers(currentVolunteers)
                println("✅ Applied ${changes.volunteers.totalChanges} volunteer changes to UI")
            }
            
            // Apply job changes
            if (changes.jobs.hasChanges) {
                // Remove deleted jobs
                changes.jobs.deleted.forEach { deletedJob ->
                    currentJobs.removeAll { it.id == deletedJob.id }
                }
                
                // Add new jobs
                currentJobs.addAll(changes.jobs.new)
                
                // Update modified jobs
                changes.jobs.modified.forEach { modifiedJob ->
                    val index = currentJobs.indexOfFirst { it.id == modifiedJob.id }
                    if (index >= 0) {
                        currentJobs[index] = modifiedJob
                    }
                }
                
                _jobs.value = removeDuplicateJobs(currentJobs)
                println("✅ Applied ${changes.jobs.totalChanges} job changes to UI")
            }
            
            // Apply job type config changes
            if (changes.jobTypeConfigs.hasChanges) {
                // Remove deleted job type configs
                changes.jobTypeConfigs.deleted.forEach { deletedConfig ->
                    currentJobTypes.removeAll { it.id == deletedConfig.id }
                }
                
                // Add new job type configs
                currentJobTypes.addAll(changes.jobTypeConfigs.new)
                
                // Update modified job type configs
                changes.jobTypeConfigs.modified.forEach { modifiedConfig ->
                    val index = currentJobTypes.indexOfFirst { it.id == modifiedConfig.id }
                    if (index >= 0) {
                        currentJobTypes[index] = modifiedConfig
                    }
                }
                
                _jobTypeConfigs.value = removeDuplicateJobTypes(currentJobTypes)
                println("✅ Applied ${changes.jobTypeConfigs.totalChanges} job type changes to UI")
            }
            
            // Apply venue changes
            if (changes.venues.hasChanges) {
                // Remove deleted venues
                changes.venues.deleted.forEach { deletedVenue ->
                    currentVenues.removeAll { it.id == deletedVenue.id }
                }
                
                // Add new venues
                currentVenues.addAll(changes.venues.new)
                
                // Update modified venues
                changes.venues.modified.forEach { modifiedVenue ->
                    val index = currentVenues.indexOfFirst { it.id == modifiedVenue.id }
                    if (index >= 0) {
                        currentVenues[index] = modifiedVenue
                    }
                }
                
                _venues.value = removeDuplicateVenues(currentVenues)
                println("✅ Applied ${changes.venues.totalChanges} venue changes to UI")
            }
            
            println("✅ All differential UI updates applied successfully")
            
        } catch (e: Exception) {
            println("❌ Failed to apply differential UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if differential update fails
            refreshAllData()
        }
    }
    
    /**
     * NEW TARGETED VOLUNTEER SYNC: Download and update only changed volunteers
     * This replaces full UI refresh with targeted updates for the volunteer page
     * 
     * Instead of:
     * - Clearing all volunteers
     * - Reloading everything
     * - Causing full page refresh
     * 
     * We:
     * - Download volunteers from Google Sheets (TEMP_DB)
     * - Compare with local data (MAIN_DB)
     * - Only update changed items (new, modified, deleted)
     * - Apply targeted UI updates
     */
    fun syncVolunteersWithTargetedUpdates() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("🔄 Starting TARGETED volunteer sync with UI updates...")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Perform differential sync that returns what changed
                val result = syncManager?.performVolunteerDifferentialSync()
                
                if (result is VolunteerSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Volunteer changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    
                    // Apply targeted UI updates instead of full refresh
                    applyVolunteerUIUpdates(changes)
                    
                    // If volunteers changed, recalc volunteer benefits
                    if (changes.hasChanges) {
                        recalcAndUpdateVolunteerBenefits()
                    }
                    
                    // Update sync time
                    updateSyncTime()
                    
                    println("✅ Targeted volunteer sync completed successfully")
                } else {
                    val errorResult = result as? VolunteerSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted volunteer sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted volunteer sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted volunteer sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY TARGETED VOLUNTEER UI UPDATES
     * 
     * Updates only the volunteers that actually changed:
     * - Remove deleted volunteers from the list
     * - Add new volunteers to the list
     * - Replace modified volunteers with their new versions
     * 
     * This avoids the performance hit of reloading the entire volunteer list
     */
    private suspend fun applyVolunteerUIUpdates(changes: DifferentialSyncService.SyncChanges<Volunteer>) {
        try {
            val currentVolunteers = _volunteers.value.toMutableList()
            
            // Remove deleted volunteers
            changes.deleted.forEach { deletedVolunteer ->
                currentVolunteers.removeAll { it.id == deletedVolunteer.id }
                println("🗑️ Removed deleted volunteer: ${deletedVolunteer.name}")
            }
            
            // Add new volunteers
            changes.new.forEach { newVolunteer ->
                currentVolunteers.add(newVolunteer)
                println("➕ Added new volunteer: ${newVolunteer.name}")
            }
            
            // Update modified volunteers
            changes.modified.forEach { modifiedVolunteer ->
                val index = currentVolunteers.indexOfFirst { it.id == modifiedVolunteer.id }
                if (index >= 0) {
                    currentVolunteers[index] = modifiedVolunteer
                    println("✏️ Updated volunteer: ${modifiedVolunteer.name}")
                }
            }
            
            // Update UI state with deduplicated list
            _volunteers.value = removeDuplicateVolunteers(currentVolunteers)
            
            println("✅ Applied ${changes.totalChanges} targeted volunteer UI updates")
            
            // Update volunteer activity based on jobs
            updateVolunteerActivityFromCurrentJobs()
            
        } catch (e: Exception) {
            println("❌ Failed to apply targeted volunteer UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if targeted update fails
            refreshVolunteerData()
        }
    }
    
    /**
     * NEW TARGETED GUEST SYNC: Download and update only changed guests
     * This replaces full UI refresh with targeted updates for the guest page
     */
    fun syncGuestsWithTargetedUpdates() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("🔄 Starting TARGETED guest sync with UI updates...")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Perform differential sync that returns what changed
                val result = syncManager?.performGuestDifferentialSync()
                
                if (result is GuestSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Guest changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    
                    // Apply targeted UI updates instead of full refresh
                    applyGuestUIUpdates(changes)
                    
                    // Update sync time
                    updateSyncTime()
                    
                    println("✅ Targeted guest sync completed successfully")
                } else {
                    val errorResult = result as? GuestSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted guest sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted guest sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted guest sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY TARGETED GUEST UI UPDATES
     * Updates only the guests that actually changed
     */
    private suspend fun applyGuestUIUpdates(changes: DifferentialSyncService.SyncChanges<Guest>) {
        try {
            val currentGuests = _guests.value.toMutableList()
            
            // Remove deleted guests
            changes.deleted.forEach { deletedGuest ->
                currentGuests.removeAll { it.id == deletedGuest.id }
                println("🗑️ Removed deleted guest: ${deletedGuest.name}")
            }
            
            // Add new guests
            changes.new.forEach { newGuest ->
                currentGuests.add(newGuest)
                println("➕ Added new guest: ${newGuest.name}")
            }
            
            // Update modified guests
            changes.modified.forEach { modifiedGuest ->
                val index = currentGuests.indexOfFirst { it.id == modifiedGuest.id }
                if (index >= 0) {
                    currentGuests[index] = modifiedGuest
                    println("✏️ Updated guest: ${modifiedGuest.name}")
                }
            }
            
            // Update UI state with deduplicated list
            _guests.value = removeDuplicateGuests(currentGuests)
            
            println("✅ Applied ${changes.totalChanges} targeted guest UI updates")
            
            // Now handle volunteer benefit guests with targeted updates (not full refresh)
            applyVolunteerBenefitUIUpdates()
            
        } catch (e: Exception) {
            println("❌ Failed to apply targeted guest UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if targeted update fails
            refreshGuestData()
        }
    }
    
    /**
     * APPLY TARGETED VOLUNTEER BENEFIT UI UPDATES
     * Instead of full refresh, only updates the volunteer benefit entries that changed
     * Uses differential comparison to identify new, modified, and deleted benefit entries
     */
    private suspend fun applyVolunteerBenefitUIUpdates() {
        try {
            println("🔄 Applying targeted volunteer benefit guest updates...")
            
            // Get current guest list
            val currentGuests = _guests.value.toMutableList()
            val existingBenefitGuests = currentGuests.filter { it.isVolunteerBenefit }
            
            // Compute new volunteer benefit entries
            val newBenefitGuests = computeVolunteerGuestEntries()
            
            println("📋 Volunteer benefits - Current: ${existingBenefitGuests.size}, New: ${newBenefitGuests.size}")
            
            // DIFFERENTIAL COMPARISON: Identify what changed in volunteer benefits
            val benefitMap = existingBenefitGuests.associateBy { "${it.volunteerId}_${it.name}" }
            val newBenefitMap = newBenefitGuests.associateBy { "${it.volunteerId}_${it.name}" }
            
            // Remove deleted benefit entries (in current but not in new)
            val deletedBenefits = existingBenefitGuests.filter { existing ->
                val key = "${existing.volunteerId}_${existing.name}"
                !newBenefitMap.containsKey(key)
            }
            deletedBenefits.forEach { deletedGuest ->
                currentGuests.removeAll { it.id == deletedGuest.id }
                println("🗑️ Removed deleted volunteer benefit: ${deletedGuest.name}")
            }
            
            // Add new benefit entries (in new but not in current)
            val newBenefits = newBenefitGuests.filter { newBenefit ->
                val key = "${newBenefit.volunteerId}_${newBenefit.name}"
                !benefitMap.containsKey(key)
            }
            currentGuests.addAll(newBenefits)
            newBenefits.forEach { newGuest ->
                println("➕ Added new volunteer benefit: ${newGuest.name}")
            }
            
            // Update modified benefit entries (same ID but different data)
            val modifiedBenefits = newBenefitGuests.filter { newBenefit ->
                val key = "${newBenefit.volunteerId}_${newBenefit.name}"
                benefitMap[key]?.let { existingBenefit ->
                    // Check if any relevant fields changed
                    existingBenefit.invitations != newBenefit.invitations ||
                    existingBenefit.notes != newBenefit.notes
                } ?: false
            }
            modifiedBenefits.forEach { modifiedGuest ->
                val index = currentGuests.indexOfFirst { 
                    it.volunteerId == modifiedGuest.volunteerId && 
                    it.isVolunteerBenefit && 
                    it.name == modifiedGuest.name 
                }
                if (index >= 0) {
                    currentGuests[index] = modifiedGuest
                    println("✏️ Updated volunteer benefit: ${modifiedGuest.name}")
                }
            }
            
            // Update UI with targeted changes (only affected rows update)
            _guests.value = removeDuplicateGuests(currentGuests)
            
            val totalChanges = deletedBenefits.size + newBenefits.size + modifiedBenefits.size
            println("✅ Applied $totalChanges targeted volunteer benefit guest updates (${deletedBenefits.size} deleted, ${newBenefits.size} new, ${modifiedBenefits.size} modified)")
            
        } catch (e: Exception) {
            println("❌ Failed to apply volunteer benefit updates: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * NEW TARGETED JOB SYNC: Download and update only changed jobs
     * This replaces full UI refresh with targeted updates for the shifts/jobs page
     */
    fun syncJobsWithTargetedUpdates() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("🔄 Starting TARGETED job sync with UI updates...")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Perform differential sync that returns what changed
                val result = syncManager?.performJobDifferentialSync()
                
                if (result is JobSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Job changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    
                    // Apply targeted UI updates instead of full refresh
                    applyJobUIUpdates(changes)
                    
                    // Update volunteer activity and benefits if jobs changed
                    if (changes.hasChanges) {
                        updateVolunteerActivityFromJobs()
                        recalcAndUpdateVolunteerBenefits()
                    }
                    
                    // Update sync time
                    updateSyncTime()
                    
                    println("✅ Targeted job sync completed successfully")
                } else {
                    val errorResult = result as? JobSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted job sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted job sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted job sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY TARGETED JOB UI UPDATES
     * Updates only the jobs that actually changed
     */
    private suspend fun applyJobUIUpdates(changes: DifferentialSyncService.SyncChanges<Job>) {
        try {
            val currentJobs = _jobs.value.toMutableList()
            
            // Remove deleted jobs
            changes.deleted.forEach { deletedJob ->
                currentJobs.removeAll { it.id == deletedJob.id }
                println("🗑️ Removed deleted job: ${deletedJob.jobTypeName}")
            }
            
            // Add new jobs
            changes.new.forEach { newJob ->
                currentJobs.add(newJob)
                println("➕ Added new job: ${newJob.jobTypeName}")
            }
            
            // Update modified jobs
            changes.modified.forEach { modifiedJob ->
                val index = currentJobs.indexOfFirst { it.id == modifiedJob.id }
                if (index >= 0) {
                    currentJobs[index] = modifiedJob
                    println("✏️ Updated job: ${modifiedJob.jobTypeName}")
                }
            }
            
            // Update UI state with deduplicated list
            _jobs.value = removeDuplicateJobs(currentJobs)
            
            println("✅ Applied ${changes.totalChanges} targeted job UI updates")
            
        } catch (e: Exception) {
            println("❌ Failed to apply targeted job UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if targeted update fails
            refreshJobData()
        }
    }

    /**
     * NEW TARGETED JOB TYPE SYNC: Download and update only changed job types
     * This replaces full UI refresh with targeted updates for the job types settings page
     */
    fun syncJobTypesWithTargetedUpdates() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("🔄 Starting TARGETED job type sync with UI updates...")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Perform differential sync that returns what changed
                val result = syncManager?.performJobTypeDifferentialSync()
                
                if (result is JobTypeSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Job type changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    
                    // Apply targeted UI updates instead of full refresh
                    applyJobTypeUIUpdates(changes)
                    
                    // Update sync time
                    updateSyncTime()
                    
                    println("✅ Targeted job type sync completed successfully")
                } else {
                    val errorResult = result as? JobTypeSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted job type sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted job type sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted job type sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY TARGETED JOB TYPE UI UPDATES
     * Updates only the job types that actually changed
     */
    private suspend fun applyJobTypeUIUpdates(changes: DifferentialSyncService.SyncChanges<JobTypeConfig>) {
        try {
            val currentJobTypes = _jobTypeConfigs.value.toMutableList()
            
            // Remove deleted job types
            changes.deleted.forEach { deletedJobType ->
                currentJobTypes.removeAll { it.id == deletedJobType.id }
                println("🗑️ Removed deleted job type: ${deletedJobType.name}")
            }
            
            // Add new job types
            changes.new.forEach { newJobType ->
                currentJobTypes.add(newJobType)
                println("➕ Added new job type: ${newJobType.name}")
            }
            
            // Update modified job types
            changes.modified.forEach { modifiedJobType ->
                val index = currentJobTypes.indexOfFirst { it.id == modifiedJobType.id }
                if (index >= 0) {
                    currentJobTypes[index] = modifiedJobType
                    println("✏️ Updated job type: ${modifiedJobType.name}")
                }
            }
            
            // Update UI state with deduplicated list
            _jobTypeConfigs.value = removeDuplicateJobTypes(currentJobTypes)
            
            println("✅ Applied ${changes.totalChanges} targeted job type UI updates")
            
        } catch (e: Exception) {
            println("❌ Failed to apply targeted job type UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if targeted update fails
            refreshJobTypeData()
        }
    }

    /**
     * NEW TARGETED VENUE SYNC: Download and update only changed venues
     * This replaces full UI refresh with targeted updates for the venues settings page
     */
    fun syncVenuesWithTargetedUpdates() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                println("🔄 Starting TARGETED venue sync with UI updates...")
                
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                
                // Perform differential sync that returns what changed
                val result = syncManager?.performVenueDifferentialSync()
                
                if (result is VenueSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Venue changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    
                    // Apply targeted UI updates instead of full refresh
                    applyVenueUIUpdates(changes)
                    
                    // Update sync time
                    updateSyncTime()
                    
                    println("✅ Targeted venue sync completed successfully")
                } else {
                    val errorResult = result as? VenueSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted venue sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted venue sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted venue sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    /**
     * APPLY TARGETED VENUE UI UPDATES
     * Updates only the venues that actually changed
     */
    private suspend fun applyVenueUIUpdates(changes: DifferentialSyncService.SyncChanges<VenueEntity>) {
        try {
            val currentVenues = _venues.value.toMutableList()
            
            // Remove deleted venues
            changes.deleted.forEach { deletedVenue ->
                currentVenues.removeAll { it.id == deletedVenue.id }
                println("🗑️ Removed deleted venue: ${deletedVenue.name}")
            }
            
            // Add new venues
            changes.new.forEach { newVenue ->
                currentVenues.add(newVenue)
                println("➕ Added new venue: ${newVenue.name}")
            }
            
            // Update modified venues
            changes.modified.forEach { modifiedVenue ->
                val index = currentVenues.indexOfFirst { it.id == modifiedVenue.id }
                if (index >= 0) {
                    currentVenues[index] = modifiedVenue
                    println("✏️ Updated venue: ${modifiedVenue.name}")
                }
            }
            
            // Update UI state with deduplicated list
            _venues.value = removeDuplicateVenues(currentVenues)
            
            println("✅ Applied ${changes.totalChanges} targeted venue UI updates")
            
        } catch (e: Exception) {
            println("❌ Failed to apply targeted venue UI updates: ${e.message}")
            e.printStackTrace()
            // Fallback to full refresh if targeted update fails
            refreshVenueData()
        }
    }

    /**
     * RECALCULATE AND UPDATE VOLUNTEER BENEFITS WITH DIFFERENTIAL SYNC
     * Uses TEMP_DB vs MAIN_DB comparison instead of full refresh
     */
    suspend fun recalcAndUpdateVolunteerBenefits() {
        try {
            println("🔄 Recalculating volunteer benefits with differential updates...")
            
            // STEP 1: Calculate new volunteer benefits (TEMP_DB)
            val newBenefitGuests = computeVolunteerGuestEntries()
            println("📥 Calculated ${newBenefitGuests.size} volunteer benefit entries")
            
            // STEP 2: Get existing volunteer benefit entries from MAIN_DB
            val existingBenefits = repository.getVolunteerBenefitGuests()
            println("📊 Current MAIN_DB: ${existingBenefits.size} volunteer benefit entries")
            
            // STEP 3: Compare TEMP_DB vs MAIN_DB using differential logic
            val benefitMap = existingBenefits.associateBy { "${it.volunteerId}_${it.name}" }
            val newBenefitMap = newBenefitGuests.associateBy { "${it.volunteerId}_${it.name}" }
            
            // Identify deleted (in MAIN_DB but not in TEMP_DB)
            val deletedBenefits = existingBenefits.filter { existing ->
                val key = "${existing.volunteerId}_${existing.name}"
                !newBenefitMap.containsKey(key)
            }
            
            // Identify new (in TEMP_DB but not in MAIN_DB)
            val addedBenefits = newBenefitGuests.filter { newBenefit ->
                val key = "${newBenefit.volunteerId}_${newBenefit.name}"
                !benefitMap.containsKey(key)
            }
            
            // Identify modified (same key but different data)
            val modifiedBenefits = newBenefitGuests.filter { newBenefit ->
                val key = "${newBenefit.volunteerId}_${newBenefit.name}"
                benefitMap[key]?.let { existingBenefit ->
                    existingBenefit.invitations != newBenefit.invitations ||
                    existingBenefit.notes != newBenefit.notes
                } ?: false
            }
            
            println("📋 Changes: ${addedBenefits.size} new, ${modifiedBenefits.size} modified, ${deletedBenefits.size} deleted")
            
            // STEP 4: Apply changes to MAIN_DB (don't clear everything)
            if (deletedBenefits.isNotEmpty() || addedBenefits.isNotEmpty() || modifiedBenefits.isNotEmpty()) {
                // Delete removed benefits
                deletedBenefits.forEach { repository.deleteGuest(it) }
                
                // Insert new benefits
                addedBenefits.forEach { repository.insertGuest(it) }
                
                // Update modified benefits
                modifiedBenefits.forEach { repository.updateGuest(it) }
                
                println("✅ Applied ${deletedBenefits.size + addedBenefits.size + modifiedBenefits.size} changes to MAIN_DB")
            } else {
                println("ℹ️ No volunteer benefit changes detected - MAIN_DB already in sync")
            }
            
            // STEP 5: Update UI with differential changes
            val currentGuests = _guests.value.toMutableList()
            
            // Remove deleted benefits from UI
            deletedBenefits.forEach { deleted ->
                currentGuests.removeAll { it.id == deleted.id }
            }
            
            // Add new benefits to UI
            currentGuests.addAll(addedBenefits)
            
            // Update modified benefits in UI
            modifiedBenefits.forEach { modified ->
                val index = currentGuests.indexOfFirst { 
                    it.volunteerId == modified.volunteerId && 
                    it.isVolunteerBenefit && 
                    it.name == modified.name 
                }
                if (index >= 0) {
                    currentGuests[index] = modified
                }
            }
            
            _guests.value = removeDuplicateGuests(currentGuests)
            
            // STEP 6: Upload to Google Sheets only if there were changes
            if (isGoogleSheetsConfigured() && (deletedBenefits.isNotEmpty() || addedBenefits.isNotEmpty() || modifiedBenefits.isNotEmpty())) {
                try {
                    println("📤 Uploading changed volunteer benefits to Google Sheets...")
                    googleSheetsService.initializeSheetsService()
                    googleSheetsService.syncVolunteerGuestListToSheets(newBenefitGuests, _venues.value)
                    println("✅ Uploaded volunteer benefits to Google Sheets")
                } catch (e: Exception) {
                    println("⚠️ Failed to upload benefits to sheets: ${e.message}")
                }
            }
            
            println("✅ Volunteer benefits update completed (${deletedBenefits.size + addedBenefits.size + modifiedBenefits.size} changes)")
            
        } catch (e: Exception) {
            println("❌ Failed to recalc volunteer benefits: ${e.message}")
            e.printStackTrace()
        }
    }
}