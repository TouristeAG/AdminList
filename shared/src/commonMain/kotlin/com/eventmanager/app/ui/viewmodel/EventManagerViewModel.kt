package com.eventmanager.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.FactoryReset
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.BiometricAdminProfileLink
import com.eventmanager.app.data.sync.BiometricAdminProfileType
import com.eventmanager.app.data.sync.DeletionTracker
import com.eventmanager.app.data.sync.TwoWaySyncService
import com.eventmanager.app.data.sync.SyncManager
import com.eventmanager.app.data.sync.SyncResult
import com.eventmanager.app.data.sync.ValidationResult
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.data.utils.NanoIdGenerator
import com.eventmanager.app.data.sync.DifferentialSyncService
import com.eventmanager.app.data.sync.DifferentialSyncResult
import com.eventmanager.app.data.sync.VolunteerSyncResult
import com.eventmanager.app.data.sync.GuestSyncResult
import com.eventmanager.app.data.sync.JobSyncResult
import com.eventmanager.app.data.sync.JobTypeSyncResult
import com.eventmanager.app.data.sync.SalesSheetItemSyncResult
import com.eventmanager.app.data.sync.TransferSyncResult
import com.eventmanager.app.data.sync.VenueSyncResult
import com.eventmanager.app.data.sync.SyncErrorManager
import com.eventmanager.app.data.sync.AppLogger
import com.eventmanager.app.data.utils.AccountBalanceService
import com.eventmanager.app.data.utils.AccountCreditService
import com.eventmanager.app.data.utils.PosCartLine
import com.eventmanager.app.data.utils.PosSaleResult
import com.eventmanager.app.data.remote.BackendType
import com.eventmanager.app.data.remote.FirebaseOrgBootstrap
import com.eventmanager.app.data.remote.FirebaseSyncStatus
import com.eventmanager.app.data.remote.InstitutionBackendAnnouncement
import com.eventmanager.app.data.remote.RemoteBackendFactory
import com.eventmanager.app.data.remote.SyncCoordinator
import com.eventmanager.app.data.remote.BackendMigrationService
import com.eventmanager.app.data.remote.SheetsMirrorExporter
import com.eventmanager.app.data.remote.FirebaseLedgerService
import com.eventmanager.app.data.remote.FirebaseLedgerResult
import com.eventmanager.app.data.remote.PendingRemoteWriteQueue
import com.eventmanager.app.data.remote.createFirestoreGateway
import com.eventmanager.app.data.remote.createFirebaseAuthService
import com.eventmanager.app.data.utils.effectiveBenefitFutureEntriesRemaining
import com.eventmanager.app.data.utils.effectiveBenefitFutureEntryInvites
import com.eventmanager.app.data.utils.jobTypeSupportsTrackedFutureEntries
import com.eventmanager.app.data.update.UpdateChecker
import com.eventmanager.app.data.update.UpdateCheckResult
import com.eventmanager.app.data.update.UpdateDownloader
import com.eventmanager.app.data.update.DownloadState
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.data.security.LocalAdminAccessResult
import com.eventmanager.app.data.security.displayName
import com.eventmanager.app.data.security.isAdminFlag
import com.eventmanager.app.ui.components.ScannerMatch
import com.eventmanager.app.ui.components.shouldShowSyncError
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.eventmanager.app.platform.PlatformContext

fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
    }
}

class EventManagerViewModel(
    val repository: EventManagerRepository,
    private val googleSheetsService: GoogleSheetsService,
    private val platformContext: PlatformContext? = null,
    private val pendingRemoteWriteDao: com.eventmanager.app.data.dao.PendingRemoteWriteDao? = null,
) : ViewModel() {
    private val benefitConsumeMutex = Mutex()
    
    // Deletion tracker for handling deletions properly
    private val deletionTracker = platformContext?.let { DeletionTracker(platformContext) }
    
    // New two-way sync service
    private val twoWaySyncService = platformContext?.let { 
        TwoWaySyncService(platformContext, repository, googleSheetsService) 
    }
    
    // Sync manager for clean interface
    private val syncManager = platformContext?.let { 
        SyncManager(platformContext, repository, googleSheetsService) 
    }

    private val settingsManagerCached = platformContext?.let { SettingsManager(it) }

    /** Routes Sheets vs Firebase intents. Null when no platform context. */
    private val syncCoordinator: SyncCoordinator? = if (
        platformContext != null && twoWaySyncService != null && syncManager != null
    ) {
        RemoteBackendFactory.createSyncCoordinator(
            platformContext = platformContext,
            repository = repository,
            googleSheetsService = googleSheetsService,
            twoWaySyncService = twoWaySyncService,
            syncManager = syncManager,
            deletionTracker = deletionTracker,
            onVolunteerGuestListRecalc = { recalcAndUploadVolunteerGuestList() },
            onBackgroundDifferentialSync = {
                withContext(Dispatchers.IO) {
                    syncManager.performDifferentialSync()
                }
            },
            onTemporaryGuestsRefresh = {
                if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) {
                    refreshTemporaryGuestsFromSheets()
                }
            },
            pendingRemoteWriteDao = pendingRemoteWriteDao,
            onFirebaseRemoteRepositoryChanged = {
                viewModelScope.launch {
                    withContext(Dispatchers.Main) { refreshAllData() }
                    refreshFirebaseSyncStatus()
                    if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                        restartSheetsMirrorSchedule()
                    }
                }
            },
            onFirebaseSyncStatusChanged = {
                viewModelScope.launch { refreshFirebaseSyncStatus() }
            },
        )
    } else null

    /** Cached so [cancelBackendMigration] hits the in-flight migration instance. */
    @Volatile
    private var cachedMigrationService: BackendMigrationService? = null

    val pendingBackendFollow = syncCoordinator?.pendingBackendFollow
    val crudSoftLocked = syncCoordinator?.crudSoftLocked

    private fun isCrudSoftLocked(): Boolean = syncCoordinator?.crudSoftLocked?.value == true

    private fun rejectIfCrudSoftLocked(action: String): Boolean {
        if (!isCrudSoftLocked()) return false
        _syncError.value =
            "Institution backend migration pending — open Admin to reconnect before $action."
        return true
    }

    fun clearPendingBackendFollow(migrationId: String) {
        syncCoordinator?.clearPendingFollowAfterSuccess(migrationId)
    }

    suspend fun followPendingBackendMigration(
        announcement: InstitutionBackendAnnouncement,
        orgId: String?,
        spreadsheetId: String?,
    ): SyncResult {
        val service = backendMigrationServiceOrNull()
            ?: return SyncResult.Error("Migration service unavailable")
        syncCoordinator?.stopBackgroundRemoteSync()
        val result = service.followMigration(
            announcement = announcement,
            spreadsheetIdOverride = spreadsheetId,
            orgIdOverride = orgId,
        )
        if (result is SyncResult.Success) {
            syncCoordinator?.clearPendingFollowAfterSuccess(announcement.migrationId)
            syncCoordinator?.stopBackgroundRemoteSync()
            syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
            withContext(Dispatchers.Main) { refreshAllData() }
        }
        return result
    }

    fun getFirebaseConfiguredOrgs(): List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg> =
        settingsManagerCached?.getFirebaseConfiguredOrgs().orEmpty()

    fun getActiveFirebaseOrgId(): String {
        val settings = settingsManagerCached ?: return ""
        return if (settings.getFirebaseOrgViewMode() == com.eventmanager.app.data.remote.FirebaseOrgViewMode.ALL) {
            com.eventmanager.app.data.remote.FIREBASE_ORG_ALL_SENTINEL
        } else {
            settings.getFirebaseOrgId()
        }
    }

    fun getFirebaseOrgViewMode(): com.eventmanager.app.data.remote.FirebaseOrgViewMode =
        settingsManagerCached?.getFirebaseOrgViewMode()
            ?: com.eventmanager.app.data.remote.FirebaseOrgViewMode.SINGLE

    fun isFirebaseAllOrgsMode(): Boolean =
        getFirebaseOrgViewMode() == com.eventmanager.app.data.remote.FirebaseOrgViewMode.ALL

    private fun resolveActiveFirebaseOrgIdForWrites(): String {
        val settings = settingsManagerCached ?: return ""
        val active = settings.getFirebaseOrgId().trim()
        if (active.isNotBlank() && !com.eventmanager.app.data.remote.isFirebaseOrgAllSentinel(active)) {
            return active
        }
        return settings.getFirebaseLastSingleOrgId().trim()
    }

    private fun tagGuestWithActiveOrg(guest: Guest): Guest {
        if (guest.firebaseOrgId.isNotBlank()) return guest
        val orgId = resolveActiveFirebaseOrgIdForWrites()
        return if (orgId.isNotBlank()) guest.copy(firebaseOrgId = orgId) else guest
    }

    private fun tagVolunteerWithActiveOrg(volunteer: Volunteer): Volunteer {
        if (volunteer.firebaseOrgId.isNotBlank()) return volunteer
        val orgId = resolveActiveFirebaseOrgIdForWrites()
        return if (orgId.isNotBlank()) volunteer.copy(firebaseOrgId = orgId) else volunteer
    }

    fun enterAllOrgsModeAsync() {
        viewModelScope.launch {
            val result = enterAllOrgsMode()
            if (result is SyncResult.Error) {
                showSyncErrorIfNotSuppressed(result.message)
            }
        }
    }

    fun enterSingleOrgModeAsync(orgId: String, purgeOtherOrgs: Boolean = false) {
        viewModelScope.launch {
            val result = enterSingleOrgMode(orgId, purgeOtherOrgs)
            if (result is SyncResult.Error) {
                showSyncErrorIfNotSuppressed(result.message)
            }
        }
    }

    suspend fun enterAllOrgsMode(): SyncResult {
        val settings = settingsManagerCached
            ?: return SyncResult.Error("Settings unavailable")
        if (settings.getBackendType() != BackendType.FIREBASE) {
            return SyncResult.Error("Firebase mode required")
        }
        val configured = settings.getFirebaseConfiguredOrgs()
        if (configured.size < 2) {
            return SyncResult.Error("At least two configured orgs required")
        }
        if (settings.getFirebaseOrgViewMode() == com.eventmanager.app.data.remote.FirebaseOrgViewMode.ALL) {
            return SyncResult.Success("Already viewing all organizations")
        }
        if (_firebaseOrgSwitching.value) {
            return SyncResult.Error("Org switch already in progress")
        }
        _firebaseOrgSwitching.value = true
        return try {
            val currentOrg = settings.getFirebaseOrgId().trim()
            if (currentOrg.isNotBlank() && !com.eventmanager.app.data.remote.isFirebaseOrgAllSentinel(currentOrg)) {
                settings.setFirebaseLastSingleOrgId(currentOrg)
            }
            settings.setFirebaseOrgViewMode(com.eventmanager.app.data.remote.FirebaseOrgViewMode.ALL)
            settings.setFirebaseOrgId(com.eventmanager.app.data.remote.FIREBASE_ORG_ALL_SENTINEL)
            val backfillOrg = settings.getFirebaseLastSingleOrgId().ifBlank { currentOrg }
            if (backfillOrg.isNotBlank()) {
                repository.backfillEmptyOrgIds(backfillOrg)
            }
            platformContext?.let {
                com.eventmanager.app.data.remote.createFirebaseAuthService(it).restoreSession()
            }
            configured.forEach { org ->
                runCatching { syncCoordinator?.ensureFirebaseOrgReady(org.orgId) }
            }
            syncCoordinator?.stopBackgroundRemoteSync()
            val syncResult = syncCoordinator?.pullAllConfiguredFirebaseOrgs()
                ?: SyncResult.Error("Sync coordinator unavailable")
            if (syncResult is SyncResult.Error) {
                settings.setFirebaseOrgViewMode(com.eventmanager.app.data.remote.FirebaseOrgViewMode.SINGLE)
                if (currentOrg.isNotBlank() && !com.eventmanager.app.data.remote.isFirebaseOrgAllSentinel(currentOrg)) {
                    settings.setFirebaseOrgId(currentOrg)
                } else {
                    val fallback = settings.getFirebaseLastSingleOrgId().ifBlank { configured.first().orgId }
                    settings.setFirebaseOrgId(fallback)
                }
                return syncResult
            }
            syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
            withContext(Dispatchers.Main) { refreshAllData() }
            refreshFirebaseSyncStatus()
            syncResult
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Failed to enter all-orgs mode")
        } finally {
            _firebaseOrgSwitching.value = false
        }
    }

    suspend fun enterSingleOrgMode(orgId: String, purgeOtherOrgs: Boolean = false): SyncResult {
        val settings = settingsManagerCached
            ?: return SyncResult.Error("Settings unavailable")
        if (settings.getBackendType() != BackendType.FIREBASE) {
            return SyncResult.Error("Firebase mode required")
        }
        val trimmed = orgId.trim()
        if (trimmed.isBlank()) return SyncResult.Error("Org ID required")
        if (settings.getFirebaseConfiguredOrgs().none { it.orgId == trimmed }) {
            return SyncResult.Error("Org not configured")
        }
        if (_firebaseOrgSwitching.value) {
            return SyncResult.Error("Org switch already in progress")
        }
        _firebaseOrgSwitching.value = true
        return try {
            platformContext?.let {
                com.eventmanager.app.data.remote.createFirebaseAuthService(it).restoreSession()
            }
            syncCoordinator?.ensureFirebaseOrgReady(trimmed)
                ?: return SyncResult.Error("Sync coordinator unavailable")
            syncCoordinator?.stopBackgroundRemoteSync()
            settings.setFirebaseOrgViewMode(com.eventmanager.app.data.remote.FirebaseOrgViewMode.SINGLE)
            settings.setFirebaseOrgId(trimmed)
            settings.setFirebaseLastSingleOrgId(trimmed)
            if (purgeOtherOrgs) {
                repository.deleteAllDataNotInOrgs(listOf(trimmed))
            }
            val syncResult = syncCoordinator?.performStartupSync()
                ?: SyncResult.Error("Sync coordinator unavailable")
            syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
            runCatching { syncCoordinator?.replicateFirebaseConfiguredOrgs() }
            withContext(Dispatchers.Main) { refreshAllData() }
            refreshFirebaseSyncStatus()
            syncResult
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Failed to enter single-org mode")
        } finally {
            _firebaseOrgSwitching.value = false
        }
    }

    fun orgColorArgb(orgId: String): Long =
        settingsManagerCached?.getFirebaseOrgColorArgb(orgId)
            ?: com.eventmanager.app.data.remote.FirebaseConfiguredOrgCodec.defaultColorForIndex(0)

    fun balanceForHolder(
        holderType: AccountHolderType,
        holderId: String,
        orgId: String,
    ): Double {
        val transfers = if (isFirebaseAllOrgsMode() && orgId.isNotBlank()) {
            _accountTransfers.value.filter { it.firebaseOrgId.isBlank() || it.firebaseOrgId == orgId }
        } else {
            _accountTransfers.value
        }
        return AccountBalanceService.computeBalance(holderType, holderId, transfers)
    }

    fun updateFirebaseConfiguredOrgsLocal(orgs: List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>) {
        settingsManagerCached?.setFirebaseConfiguredOrgs(orgs)
    }

    fun provisionFirebaseOrg(orgId: String) {
        viewModelScope.launch {
            val settings = settingsManagerCached ?: return@launch
            if (settings.getBackendType() != BackendType.FIREBASE) return@launch
            if (!FirebaseOrgBootstrap.isValidOrgId(orgId)) return@launch
            try {
                platformContext?.let { createFirebaseAuthService(it).restoreSession() }
                syncCoordinator?.provisionFirebaseOrg(orgId.trim())
                syncCoordinator?.replicateFirebaseConfiguredOrgs()
            } catch (e: Exception) {
                showSyncErrorIfNotSuppressed(
                    "Could not create Firebase organization \"$orgId\": ${e.message}",
                )
            }
        }
    }

    /** @deprecated Use [updateFirebaseConfiguredOrgsLocal] + [provisionFirebaseOrg] on field commit. */
    fun saveFirebaseConfiguredOrgs(orgs: List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>) {
        updateFirebaseConfiguredOrgsLocal(orgs)
    }

    fun switchFirebaseOrgAsync(orgId: String) {
        viewModelScope.launch {
            val result = switchFirebaseOrg(orgId)
            if (result is SyncResult.Error) {
                showSyncErrorIfNotSuppressed(result.message)
            }
        }
    }

    suspend fun switchFirebaseOrg(orgId: String): SyncResult {
        val settings = settingsManagerCached
            ?: return SyncResult.Error("Settings unavailable")
        if (settings.getBackendType() != BackendType.FIREBASE) {
            return SyncResult.Error("Firebase mode required")
        }
        val trimmed = orgId.trim()
        if (trimmed.isBlank()) return SyncResult.Error("Org ID required")
        val configured = settings.getFirebaseConfiguredOrgs()
        if (configured.none { it.orgId == trimmed }) {
            return SyncResult.Error("Org not configured")
        }
        if (settings.getFirebaseOrgId().trim() == trimmed) {
            return SyncResult.Success("Already on this organization")
        }
        if (_firebaseOrgSwitching.value) {
            return SyncResult.Error("Org switch already in progress")
        }
        _firebaseOrgSwitching.value = true
        val previousOrg = settings.getFirebaseOrgId().trim()
        val leavingAllMode = com.eventmanager.app.data.remote.isFirebaseOrgAllSentinel(previousOrg)
        return try {
            platformContext?.let {
                com.eventmanager.app.data.remote.createFirebaseAuthService(it).restoreSession()
            }
            syncCoordinator?.ensureFirebaseOrgReady(trimmed)
                ?: return SyncResult.Error("Sync coordinator unavailable")
            syncCoordinator?.stopBackgroundRemoteSync()
            if (leavingAllMode) {
                configured.map { it.orgId }.forEach { orgId ->
                    syncCoordinator?.flushFirebasePendingWritesForOrg(orgId)
                }
            } else {
                syncCoordinator?.flushFirebasePendingWritesForOrg(previousOrg)
            }
            pendingRemoteWriteDao?.clearAll()
            repository.clearAllData()
            settings.setFirebaseOrgViewMode(com.eventmanager.app.data.remote.FirebaseOrgViewMode.SINGLE)
            settings.setFirebaseOrgId(trimmed)
            settings.setFirebaseLastSingleOrgId(trimmed)
            val syncResult = syncCoordinator?.performStartupSync()
                ?: return SyncResult.Error("Sync coordinator unavailable")
            if (syncResult is SyncResult.Error) {
                settings.setFirebaseOrgId(previousOrg)
                repository.clearAllData()
                pendingRemoteWriteDao?.clearAll()
                syncCoordinator?.performStartupSync()
                syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
                withContext(Dispatchers.Main) { refreshAllData() }
                return SyncResult.Error(
                    "Could not switch to \"$trimmed\": ${syncResult.message}",
                )
            }
            syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
            runCatching { syncCoordinator?.replicateFirebaseConfiguredOrgs() }
            withContext(Dispatchers.Main) { refreshAllData() }
            refreshFirebaseSyncStatus()
            if (settings.getBackendType() == BackendType.FIREBASE) {
                restartSheetsMirrorSchedule()
            }
            syncResult
        } catch (e: Exception) {
            if (settings.getFirebaseOrgId().trim() == trimmed && previousOrg.isNotBlank()) {
                settings.setFirebaseOrgId(previousOrg)
                runCatching {
                    repository.clearAllData()
                    pendingRemoteWriteDao?.clearAll()
                    syncCoordinator?.performStartupSync()
                    syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
                    withContext(Dispatchers.Main) { refreshAllData() }
                }
            }
            SyncResult.Error("Failed to switch org: ${e.message}")
        } finally {
            _firebaseOrgSwitching.value = false
        }
    }

    suspend fun migrateBackendToFirebase(orgId: String, migratedBy: String = "admin"): SyncResult {
        val service = backendMigrationServiceOrNull()
            ?: return SyncResult.Error("Migration service unavailable")
        syncCoordinator?.stopBackgroundRemoteSync()
        return service.migrateSheetsToFirebase(orgId, migratedBy).also {
            cachedMigrationService = null
            if (it is SyncResult.Success) {
                syncCoordinator?.stopBackgroundRemoteSync()
                syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
                withContext(Dispatchers.Main) { refreshAllData() }
            }
        }
    }

    suspend fun migrateBackendToSheets(spreadsheetId: String, migratedBy: String = "admin"): SyncResult {
        val service = backendMigrationServiceOrNull()
            ?: return SyncResult.Error("Migration service unavailable")
        return service.migrateFirebaseToSheets(spreadsheetId, migratedBy).also {
            cachedMigrationService = null
            if (it is SyncResult.Success) {
                syncCoordinator?.stopBackgroundRemoteSync()
                syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
                withContext(Dispatchers.Main) { refreshAllData() }
            }
        }
    }

    suspend fun exportSheetsMirrorNow(): SyncResult {
        val exporter = sheetsMirrorExporterOrNull()
            ?: return SyncResult.Error("Sheets mirror unavailable")
        val result = exporter.exportNow()
        if (result is SyncResult.Success) {
            backupInstitutionSettingsToSheets()
        }
        return result
    }

    fun onSheetsMirrorSettingsChanged() {
        restartSheetsMirrorSchedule()
        backupInstitutionSettingsToSheets()
    }

    fun backendMigrationServiceOrNull(): BackendMigrationService? {
        cachedMigrationService?.let { return it }
        val ctx = platformContext ?: return null
        val tws = twoWaySyncService ?: return null
        val sm = syncManager ?: return null
        val settings = settingsManagerCached ?: return null
        if (syncCoordinator == null) return null
        val gateway = createFirestoreGateway(ctx, settings)
        val firebase = com.eventmanager.app.data.remote.FirebaseRemoteBackend(
            platformContext = ctx,
            repository = repository,
            settingsManager = settings,
            firestoreGateway = gateway,
            ledgerService = FirebaseLedgerService(repository, settings, gateway),
            pendingWrites = PendingRemoteWriteQueue(
                dao = pendingRemoteWriteDao,
                activeOrgId = { settings.getFirebaseOrgId().trim() },
            ),
        )
        val sheetsBackend = com.eventmanager.app.data.remote.SheetsRemoteBackend(
            platformContext = ctx,
            repository = repository,
            googleSheetsService = googleSheetsService,
            twoWaySyncService = tws,
            syncManager = sm,
            deletionTracker = deletionTracker,
            settingsManager = settings,
        )
        return BackendMigrationService(
            platformContext = ctx,
            repository = repository,
            settingsManager = settings,
            sheetsBackend = sheetsBackend,
            firebaseBackend = firebase,
            twoWaySyncService = tws,
            googleSheetsService = googleSheetsService,
        ).also { cachedMigrationService = it }
    }

    fun cancelBackendMigration() {
        cachedMigrationService?.requestCancel()
    }

    fun sheetsMirrorExporterOrNull(): SheetsMirrorExporter? {
        val tws = twoWaySyncService ?: return null
        val settings = settingsManagerCached ?: return null
        return SheetsMirrorExporter(settings, tws)
    }

    // State for guests
    private val _guests = MutableStateFlow<List<Guest>>(emptyList())
    val guests: StateFlow<List<Guest>> = _guests

    // State for volunteers
    private val _volunteers = MutableStateFlow<List<Volunteer>>(emptyList())
    val volunteers: StateFlow<List<Volunteer>> = _volunteers

    // State for jobs
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    // State for job type configs
    private val _jobTypeConfigs = MutableStateFlow<List<JobTypeConfig>>(emptyList())
    val jobTypeConfigs: StateFlow<List<JobTypeConfig>> = _jobTypeConfigs

    private val _venues = MutableStateFlow<List<VenueEntity>>(emptyList())
    val venues: StateFlow<List<VenueEntity>> = _venues

    private val _salesSheetItems = MutableStateFlow<List<SalesSheetItem>>(emptyList())
    val salesSheetItems: StateFlow<List<SalesSheetItem>> = _salesSheetItems

    private val _accountTransfers = MutableStateFlow<List<AccountTransfer>>(emptyList())
    val accountTransfers: StateFlow<List<AccountTransfer>> = _accountTransfers

    private val _accountBalances = MutableStateFlow<Map<AccountHolderKey, Double>>(emptyMap())
    val accountBalances: StateFlow<Map<AccountHolderKey, Double>> = _accountBalances

    private val accountCreditService: AccountCreditService by lazy {
        AccountCreditService(repository) {
            platformContext?.let { SettingsManager(it).getCurrencyCode() } ?: "CHF"
        }
    }

    private val _peopleCounterSelectedVenueId = MutableStateFlow(0L)
    val peopleCounterSelectedVenueId: StateFlow<Long> = _peopleCounterSelectedVenueId.asStateFlow()

    private val _peopleCounterPriority = MutableStateFlow(false)
    val peopleCounterPriority: StateFlow<Boolean> = _peopleCounterPriority.asStateFlow()

    private val _peopleCounterUiHint = MutableStateFlow<String?>(null)
    val peopleCounterUiHint: StateFlow<String?> = _peopleCounterUiHint.asStateFlow()

    private val peopleCounterUploadMutex = Mutex()
    private val peopleCounterQuietRefreshMutex = Mutex()
    private val peopleCounterUserSelectionGraceMs = 8_000L
    @Volatile private var peopleCounterLastUserSelectedVenueId: Long = 0L
    @Volatile private var peopleCounterLastUserSelectionAtMs: Long = 0L

    private data class PeopleCounterThrottle(
        var lastUploadAtMs: Long = 0L,
        var countAtLastUpload: Int? = null
    )

    private val peopleCounterThrottleByVenue = mutableMapOf<Long, PeopleCounterThrottle>()

    // State for sync status
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Skip duplicate sheet repair+download on Admin tap when a pull already completed recently. */
    private val adminGateFreshSyncMs = 3 * 60 * 1000L

    // State for sync error
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var posSessionBootstrapDone = false
    
    // State for sync error dialog visibility
    private val _showSyncErrorDialog = MutableStateFlow(false)
    val showSyncErrorDialog: StateFlow<Boolean> = _showSyncErrorDialog.asStateFlow()
    
    // State for sync status message and dialog visibility
    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()
    
    private val _showSyncStatusDialog = MutableStateFlow(false)
    val showSyncStatusDialog: StateFlow<Boolean> = _showSyncStatusDialog.asStateFlow()
    
    // Announcements state
    private val _pendingAnnouncements = MutableStateFlow<List<com.eventmanager.app.ui.components.AnnouncementDisplay>>(emptyList())
    val pendingAnnouncements: StateFlow<List<com.eventmanager.app.ui.components.AnnouncementDisplay>> = _pendingAnnouncements.asStateFlow()

    private val _showSendAnnouncementDialog = MutableStateFlow(false)
    val showSendAnnouncementDialog: StateFlow<Boolean> = _showSendAnnouncementDialog.asStateFlow()

    private val _isAnnouncementSending = MutableStateFlow(false)
    val isAnnouncementSending: StateFlow<Boolean> = _isAnnouncementSending.asStateFlow()

    // Error manager for "do not tell me again today"
    private val syncErrorManager = platformContext?.let { SyncErrorManager(it) }

    // State for last sync time
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    /** null = admin gate sync in progress; true/false = outcome for NFC/QR/biometric gate. */
    private val _adminGateSyncSucceeded = MutableStateFlow<Boolean?>(null)
    val adminGateSyncSucceeded: StateFlow<Boolean?> = _adminGateSyncSucceeded.asStateFlow()

    private val _firebaseSyncStatus = MutableStateFlow(FirebaseSyncStatus.offline())
    val firebaseSyncStatus: StateFlow<FirebaseSyncStatus> = _firebaseSyncStatus.asStateFlow()

    private val _firebaseOrgSwitching = MutableStateFlow(false)
    val firebaseOrgSwitching: StateFlow<Boolean> = _firebaseOrgSwitching.asStateFlow()

    fun getActiveBackendType(): BackendType =
        settingsManagerCached?.getBackendType() ?: BackendType.SHEETS

    // Background sync job
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null
    private var sheetsMirrorJob: kotlinx.coroutines.Job? = null

    fun restartSheetsMirrorSchedule() {
        sheetsMirrorJob?.cancel()
        sheetsMirrorJob = null
        sheetsMirrorJob = sheetsMirrorExporterOrNull()?.startScheduled(viewModelScope)
    }

    // Update check state
    private val _updateCheckState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckState: StateFlow<UpdateCheckResult?> = _updateCheckState.asStateFlow()

    private val updateChecker: UpdateChecker? = platformContext?.let { UpdateChecker(it) }
    
    // Update download state
    private val _updateDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val updateDownloadState: StateFlow<DownloadState> = _updateDownloadState.asStateFlow()
    
    private val updateDownloader: UpdateDownloader? = platformContext?.let { UpdateDownloader(it) }

    init {
        loadData()
        startBackgroundSync()
        loadLastSyncTime()
        viewModelScope.launch { refreshFirebaseSyncStatus() }
        // Clean up any existing duplicates in the database
        cleanupDuplicates()
        // Ensure volunteer activity is calculated after initial data load
        viewModelScope.launch {
            delay(800) // Small delay to ensure all data is loaded
            updateVolunteerActivityFromCurrentJobs()
        }
        viewModelScope.launch {
            delay(1200)
            evaluatePendingShiftCreditsIfNeeded()
        }
        platformContext?.let { ctx ->
            val sm = SettingsManager(ctx)
            val savedVenue = sm.getPeopleCounterSelectedVenueId()
            if (savedVenue > 0L) {
                _peopleCounterSelectedVenueId.value = savedVenue
            }
            _peopleCounterPriority.value = sm.isPeopleCounterPriority(_peopleCounterSelectedVenueId.value)
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
    fun installUpdate(filePath: String) {
        updateDownloader?.installUpdate(filePath)
    }
    
    override fun onCleared() {
        super.onCleared()
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
        sheetsMirrorJob?.cancel()
        sheetsMirrorJob = null
        println("ViewModel cleared - background sync stopped")
    }

    private fun loadLastSyncTime() {
        platformContext?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            _lastSyncTime.value = settingsManager.getLastSyncTime()
        }
    }

    private suspend fun refreshFirebaseSyncStatus() {
        if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) return
        _firebaseSyncStatus.value = syncCoordinator?.snapshotFirebaseSyncStatus()
            ?: FirebaseSyncStatus.offline()
    }

    private fun startBackgroundSync() {
        platformContext?.let { ctx ->
            val settingsManager = SettingsManager(ctx)
            backgroundSyncJob?.cancel()

            when (settingsManager.getBackendType()) {
                BackendType.FIREBASE -> {
                    println("Starting Firebase live sync (listeners)")
                    viewModelScope.launch {
                        val auth = com.eventmanager.app.data.remote.createFirebaseAuthService(ctx)
                        auth.restoreSession()
                        when (val repair = syncCoordinator?.repairFirebaseActiveOrgIfNeeded()) {
                            is com.eventmanager.app.data.remote.FirebaseOrgRepairResult.Recovered -> {
                                repository.clearAllData()
                                syncCoordinator?.performStartupSync()
                            }
                            else -> Unit
                        }
                        syncCoordinator?.startBackgroundRemoteSync(viewModelScope)
                        syncCoordinator?.performStartupSync()
                        restartSheetsMirrorSchedule()
                        refreshFirebaseSyncStatus()
                        withContext(Dispatchers.Main) { refreshAllData() }
                    }
                }
                BackendType.SHEETS -> {
                    // Prefer SheetsRemoteBackend timer so interval/enable flags stay in one place
                    val coordinator = syncCoordinator
                    if (coordinator != null) {
                        println("Starting Sheets background sync via SyncCoordinator")
                        coordinator.startBackgroundRemoteSync(viewModelScope)
                    } else {
                        val syncInterval = settingsManager.getSyncInterval()
                        println("Starting background sync with interval: $syncInterval minutes")
                        backgroundSyncJob = viewModelScope.launch {
                            while (true) {
                                try {
                                    delay(syncInterval * 60 * 1000L)
                                    if (isGoogleSheetsConfigured()) {
                                        performDifferentialFullSync()
                                    }
                                } catch (e: Exception) {
                                    println("Background sync error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateSyncInterval() {
        platformContext?.let { ctx ->
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
        if (shouldShowSyncError(errorMessage) && 
            syncErrorManager?.shouldSuppressError() == false) {
            _syncError.value = errorMessage
            _showSyncErrorDialog.value = true
        } else if (!shouldShowSyncError(errorMessage)) {
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
                repository.getAllGuests().collect { guestList ->
                    // Validate and fix any guests with invalid NanoIDs
                    val validatedGuests = mutableListOf<Guest>()
                    val guestsToFix = mutableListOf<Guest>()

                    for (guest in guestList) {
                        if (NanoIdGenerator.needsRegeneration(guest.nanoId)) {
                            val newId = NanoIdGenerator.ensureValidNanoId(guest.nanoId, guest.name)
                            println("⚠️ ViewModel: Fixed invalid NanoID for guest '${guest.name}': '${guest.nanoId}' → '$newId'")
                            val fixedGuest = guest.copy(nanoId = newId)
                            validatedGuests.add(fixedGuest)
                            guestsToFix.add(fixedGuest)
                        } else {
                            validatedGuests.add(guest)
                        }
                    }

                    if (guestsToFix.isNotEmpty()) {
                        launch {
                            try {
                                guestsToFix.forEach { fixedGuest ->
                                    repository.updateGuest(fixedGuest)
                                }
                                println("✅ Updated ${guestsToFix.size} guest(s) with fixed NanoIDs in local database")
                                when {
                                    settingsManagerCached?.getBackendType() == BackendType.FIREBASE -> {
                                        guestsToFix.forEach { syncCoordinator?.afterGuestSaved(it) }
                                    }
                                    isGoogleSheetsConfigured() -> {
                                        try {
                                            twoWaySyncService?.backupGuestsToSheets()
                                            println("✅ Synced all guests (including ${guestsToFix.size} with fixed NanoIDs) to Google Sheets")
                                        } catch (e: Exception) {
                                            println("⚠️ Failed to sync fixed guest NanoIDs to Google Sheets: ${e.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Failed to update guests with fixed NanoIDs: ${e.message}")
                            }
                        }
                    }

                    _guests.value = removeDuplicateGuests(validatedGuests)
                }
            } catch (e: Exception) {
                println("Failed to load guests: ${e.message}")
                _guests.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllVolunteers().collect { volunteers ->
                    // Validate and fix any volunteers with invalid NanoIDs
                    val validatedVolunteers = mutableListOf<Volunteer>()
                    val volunteersToFix = mutableListOf<Volunteer>()
                    
                    for (volunteer in volunteers) {
                        if (NanoIdGenerator.needsRegeneration(volunteer.id)) {
                            val newId = NanoIdGenerator.ensureValidNanoId(volunteer.id, volunteer.name)
                            println("⚠️ ViewModel: Fixed invalid NanoID for volunteer '${volunteer.name}': '${volunteer.id}' → '$newId'")
                            val fixedVolunteer = volunteer.copy(id = newId)
                            validatedVolunteers.add(fixedVolunteer)
                            volunteersToFix.add(fixedVolunteer)
                        } else {
                            validatedVolunteers.add(volunteer)
                        }
                    }
                    
                    // Update volunteers with fixed IDs in the database and sync to Google Sheets
                    if (volunteersToFix.isNotEmpty()) {
                        launch {
                            try {
                                // Update all fixed volunteers in the database first
                                // Note: Repository will validate again, but since we already fixed the IDs,
                                // the validation will just pass through quickly (defense-in-depth pattern)
                                volunteersToFix.forEach { fixedVolunteer ->
                                    repository.updateVolunteer(fixedVolunteer)
                                }
                                println("✅ Updated ${volunteersToFix.size} volunteer(s) with fixed NanoIDs in local database")
                                when {
                                    settingsManagerCached?.getBackendType() == BackendType.FIREBASE -> {
                                        volunteersToFix.forEach { syncCoordinator?.afterVolunteerSaved(it) }
                                    }
                                    isGoogleSheetsConfigured() -> {
                                        try {
                                            twoWaySyncService?.backupVolunteersToSheets()
                                            println("✅ Synced all volunteers (including ${volunteersToFix.size} with fixed NanoIDs) to Google Sheets")
                                        } catch (e: Exception) {
                                            println("⚠️ Failed to sync fixed NanoIDs to Google Sheets: ${e.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Failed to update volunteers with fixed IDs: ${e.message}")
                            }
                        }
                    }
                    
                    val updatedVolunteers = removeDuplicateVolunteers(validatedVolunteers)
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
        viewModelScope.launch {
            try {
                repository.getAllSalesSheetItems().collect {
                    _salesSheetItems.value = it
                }
            } catch (e: Exception) {
                println("Failed to load sales sheet items: ${e.message}")
                _salesSheetItems.value = emptyList()
            }
        }
        viewModelScope.launch {
            try {
                repository.getAllAccountTransfers().collect { transfers ->
                    _accountTransfers.value = transfers
                    _accountBalances.value = AccountBalanceService.computeAllBalances(transfers)
                }
            } catch (e: Exception) {
                println("Failed to load account transfers: ${e.message}")
                _accountTransfers.value = emptyList()
                _accountBalances.value = emptyMap()
            }
        }
    }

    fun getVolunteerAccountBalance(volunteerId: String): Double =
        _accountBalances.value[AccountHolderKey(AccountHolderType.VOLUNTEER, volunteerId)] ?: 0.0

    fun getGuestAccountBalance(guestNanoId: String): Double =
        _accountBalances.value[AccountHolderKey(AccountHolderType.GUEST, guestNanoId)] ?: 0.0

    private suspend fun refreshAccountBalancesFromDb() {
        val transfers = repository.getAllAccountTransfersOnce()
        _accountTransfers.value = transfers
        _accountBalances.value = AccountBalanceService.computeAllBalances(transfers)
    }

    private suspend fun evaluatePendingShiftCreditsIfNeeded() {
        val offset = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
        val created = accountCreditService.evaluatePendingShiftCredits(_jobTypeConfigs.value, offset)
        if (created.isNotEmpty()) {
            refreshAccountBalancesFromDb()
            syncCreatedTransfers(created)
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
                if (rejectIfCrudSoftLocked("adding guests")) return@launch
                val saved = repository.insertGuest(guest)
                syncCoordinator?.afterGuestSaved(saved)
                    ?: twoWaySyncService?.backupGuestsToSheets()?.also {
                        recalcAndUploadVolunteerGuestList()
                    }
            } catch (e: Exception) {
            println("Failed to add guest: ${e.message}")
            _syncError.value = "Failed to add guest: ${e.message}"
            }
        }
    }

    /**
     * Adds one or more temporary guests as separate rows on the temp guest sheet, then
     * reloads temporary guests from Sheets (same source of truth as edits/deletes).
     */
    fun addTemporaryGuestBatch(batch: ManualTemporaryGuestBatch) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("adding temporary guests")) return@launch
                val names = batch.guestNames.map { it.trim() }.filter { it.isNotEmpty() }
                if (names.isEmpty()) return@launch
                val prepared = batch.copy(guestNames = names)
                if (syncCoordinator != null) {
                    syncCoordinator.afterTemporaryGuestBatch(prepared)
                    if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                        withContext(Dispatchers.Main) { refreshGuestData() }
                    }
                } else {
                    if (!isGoogleSheetsConfigured()) {
                        _syncError.value = "Google Sheets is not configured."
                        return@launch
                    }
                    googleSheetsService.appendTemporaryGuestManualBatch(prepared)
                    refreshTemporaryGuestsFromSheets()
                }
            } catch (e: Exception) {
                println("Failed to add temporary guests: ${e.message}")
                _syncError.value = "Failed to add temporary guests: ${e.message}"
            }
        }
    }

    fun updateGuest(guest: Guest) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("updating guests")) return@launch
                // Update lastModified timestamp
                val updatedGuest = guest.copy(lastModified = System.currentTimeMillis())
                if (updatedGuest.isTemporaryGuest) {
                    repository.updateGuest(updatedGuest)
                    syncCoordinator?.afterGuestSaved(updatedGuest)
                        ?: run {
                            googleSheetsService.updateTemporaryGuestInSheets(updatedGuest)
                            refreshTemporaryGuestsFromSheets()
                        }
                    if (syncCoordinator != null &&
                        settingsManagerCached?.getBackendType() != BackendType.FIREBASE
                    ) {
                        refreshTemporaryGuestsFromSheets()
                    }
                } else {
                    repository.updateGuest(updatedGuest)
                    syncCoordinator?.afterGuestSaved(updatedGuest)
                        ?: twoWaySyncService?.backupGuestsToSheets()?.also {
                            recalcAndUploadVolunteerGuestList()
                        }
                }
            } catch (e: Exception) {
                println("Failed to update guest: ${e.message}")
                _syncError.value = "Failed to update guest: ${e.message}"
            }
        }
    }

    fun deleteGuest(guest: Guest) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("deleting guests")) return@launch
                if (guest.isTemporaryGuest) {
                    repository.deleteGuest(guest)
                    syncCoordinator?.afterGuestDeleted(guest)
                        ?: run {
                            googleSheetsService.deleteTemporaryGuestFromSheets(guest.sheetsId)
                            refreshTemporaryGuestsFromSheets()
                        }
                    if (syncCoordinator != null &&
                        settingsManagerCached?.getBackendType() != BackendType.FIREBASE
                    ) {
                        refreshTemporaryGuestsFromSheets()
                    }
                    println("Successfully deleted temporary guest: ${guest.name}")
                } else {
                    // Track the deletion (businessKey = nanoId for merge-safe upload)
                    deletionTracker?.trackGuestDeletion(guest.id.toString(), guest.sheetsId, businessKey = guest.nanoId)

                    // Delete from local database
                    repository.deleteGuest(guest)

                    syncCoordinator?.afterGuestDeleted(guest)
                        ?: twoWaySyncService?.backupGuestsToSheets()?.also {
                            recalcAndUploadVolunteerGuestList()
                        }

                    println("Successfully deleted guest: ${guest.name}")
                }
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
                if (rejectIfCrudSoftLocked("adding volunteers")) return@launch
                repository.insertVolunteer(volunteer)
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                syncCoordinator?.afterVolunteerSaved(volunteer) ?: twoWaySyncService?.backupVolunteersToSheets()
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
            if (rejectIfCrudSoftLocked("updating volunteers")) return@launch
            repository.updateVolunteer(volunteer)
                // BACKUP MODE: Upload entire volunteer dataset to Google Sheets
                syncCoordinator?.afterVolunteerSaved(volunteer) ?: twoWaySyncService?.backupVolunteersToSheets()
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
            println("Failed to update volunteer: ${e.message}")
            _syncError.value = "Failed to update volunteer: ${e.message}"
            }
        }
    }

    fun deleteVolunteer(volunteer: Volunteer, deleteShifts: Boolean = false) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("deleting volunteers")) return@launch
                // If deleteShifts is true, delete all associated jobs/shifts first
                if (deleteShifts) {
                    val allJobs = repository.getAllJobs().first()
                    val volunteerJobs = allJobs.filter { it.volunteerId == volunteer.id }
                    
                    println("Deleting ${volunteerJobs.size} job(s) for volunteer ${volunteer.name}")
                    
                    for (job in volunteerJobs) {
                        try {
                            deletionTracker?.trackJobDeletion(
                                job.id.toString(),
                                job.sheetsId,
                                businessKey = "${job.volunteerId}_${job.jobTypeName}_${job.date}_${job.venueName}_${job.shiftTime}",
                            )
                            repository.deleteJob(job)
                            syncCoordinator?.afterJobDeleted(job)
                                ?: if (job.sheetsId != null) {
                                    try {
                                        googleSheetsService.deleteJobFromSheets(job.id.toString(), job.sheetsId)
                                    } catch (_: Exception) {
                                        twoWaySyncService?.backupJobsToSheets()
                                    }
                                } else {
                                    twoWaySyncService?.backupJobsToSheets()
                                }
                            val vol = repository.getVolunteerById(job.volunteerId)
                            if (vol != null) {
                                val offset = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
                                val reversals = accountCreditService.reverseShiftCredits(
                                    job, vol, _jobTypeConfigs.value, offset
                                )
                                syncCreatedTransfers(reversals)
                            }
                        } catch (e: Exception) {
                            println("Failed to delete job ${job.id} (${job.jobTypeName}) for volunteer ${volunteer.name}: ${e.message}")
                        }
                    }
                    
                    if (volunteerJobs.isNotEmpty()) {
                        delay(100)
                        refreshAccountBalancesFromDb()
                    }
                }
                
                // Track the deletion
                deletionTracker?.trackVolunteerDeletion(volunteer.id, volunteer.sheetsId)
                
                // Delete from local database
                repository.deleteVolunteer(volunteer)
                
                syncCoordinator?.afterVolunteerDeleted(volunteer, deleteShifts)
                    ?: twoWaySyncService?.backupVolunteersToSheets()
                
                println("Successfully deleted volunteer: ${volunteer.name}")
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to delete volunteer: ${e.message}")
                _syncError.value = "Failed to delete volunteer: ${e.message}"
            }
        }
    }

    /**
     * Returns true if any guest or volunteer in the local DB has isAdmin == true.
     */
    suspend fun hasAnyAdmin(): Boolean {
        val guests = repository.getAllGuests().first()
        val volunteers = repository.getAllVolunteers().first()
        return guests.any { it.isAdmin } || volunteers.any { it.isAdmin }
    }

    /**
     * Creates a guest with isAdmin = true, persists it, and syncs to Sheets.
     * Calls [onResult] with (success, errorMessage?).
     */
    fun createAdminGuest(guest: Guest, onResult: (Boolean, Guest?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("creating admin")) {
                    onResult(false, null, "Institution backend migration pending")
                    return@launch
                }
                val adminGuest = tagGuestWithActiveOrg(guest.copy(isAdmin = true))
                val saved = repository.insertGuest(adminGuest)
                syncCoordinator?.afterGuestSaved(saved)
                    ?: twoWaySyncService?.backupGuestsToSheets()
                recalcAndUploadVolunteerGuestList()
                withContext(Dispatchers.Main) { refreshGuestData() }
                withContext(Dispatchers.Main) { onResult(true, saved, null) }
            } catch (e: Exception) {
                println("Failed to create admin guest: ${e.message}")
                _syncError.value = "Failed to create admin guest: ${e.message}"
                withContext(Dispatchers.Main) { onResult(false, null, e.message) }
            }
        }
    }

    /**
     * Creates a volunteer with isAdmin = true, persists it, and syncs to Sheets.
     * Calls [onResult] with (success, savedVolunteer?, errorMessage?).
     */
    fun createAdminVolunteer(volunteer: Volunteer, onResult: (Boolean, Volunteer?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("creating admin")) {
                    onResult(false, null, "Institution backend migration pending")
                    return@launch
                }
                val adminVolunteer = tagVolunteerWithActiveOrg(volunteer.copy(isAdmin = true))
                val saved = repository.insertVolunteer(adminVolunteer)
                syncCoordinator?.afterVolunteerSaved(saved) ?: twoWaySyncService?.backupVolunteersToSheets()
                recalcAndUploadVolunteerGuestList()
                withContext(Dispatchers.Main) { refreshVolunteerData() }
                withContext(Dispatchers.Main) { onResult(true, saved, null) }
            } catch (e: Exception) {
                println("Failed to create admin volunteer: ${e.message}")
                _syncError.value = "Failed to create admin volunteer: ${e.message}"
                withContext(Dispatchers.Main) { onResult(false, null, e.message) }
            }
        }
    }

    /**
     * Assigns an NFC UID to the admin guest (identified by nanoId) or admin volunteer (identified by id).
     */
    fun assignNfcUidToAdmin(isGuest: Boolean, entityId: String, uid: String) {
        viewModelScope.launch {
            try {
                if (isGuest) {
                    val guests = repository.getAllGuests().first()
                    val guest = guests.find { it.nanoId == entityId }
                    if (guest != null) {
                        val updated = guest.copy(nfcCardUid = uid, lastModified = System.currentTimeMillis())
                        repository.updateGuest(updated)
                        syncCoordinator?.afterGuestSaved(updated) ?: twoWaySyncService?.backupGuestsToSheets()
                    }
                } else {
                    val volunteer = repository.getVolunteerById(entityId)
                    if (volunteer != null) {
                        val updated = volunteer.copy(nfcCardUid = uid, lastModified = System.currentTimeMillis())
                        repository.updateVolunteer(updated)
                        syncCoordinator?.afterVolunteerSaved(updated) ?: twoWaySyncService?.backupVolunteersToSheets()
                    }
                }
            } catch (e: Exception) {
                println("Failed to assign NFC UID to admin: ${e.message}")
                _syncError.value = "Failed to assign NFC UID: ${e.message}"
            }
        }
    }

    /**
     * Re-loads the volunteer or guest from the local database before admin checks.
     * Avoids flaky denies when the UI list is briefly stale after sync while Room already has the latest flags.
     */
    suspend fun resolveFreshAdminScanMatch(match: ScannerMatch): ScannerMatch = withContext(Dispatchers.IO) {
        when (match) {
            is ScannerMatch.VolunteerMatch -> {
                val fresh = repository.getVolunteerById(match.volunteer.id)
                if (fresh != null) ScannerMatch.VolunteerMatch(fresh) else match
            }
            is ScannerMatch.GuestMatch -> {
                val fresh = repository.getGuestByNanoId(match.guest.nanoId)
                if (fresh != null) ScannerMatch.GuestMatch(fresh) else match
            }
        }
    }

    suspend fun resolveFreshBiometricAdminLink(link: BiometricAdminProfileLink): ScannerMatch? =
        withContext(Dispatchers.IO) {
            when (link.type) {
                BiometricAdminProfileType.VOLUNTEER ->
                    repository.getVolunteerById(link.profileId)?.let { ScannerMatch.VolunteerMatch(it) }
                BiometricAdminProfileType.GUEST ->
                    repository.getGuestByNanoId(link.profileId)?.let { ScannerMatch.GuestMatch(it) }
            }
        }

    /**
     * Central local admin check: re-reads Room and verifies `isAdmin` on guest/volunteer.
     * Revokes biometric enrollment when the linked profile is no longer admin.
     */
    suspend fun verifyLocalAdminAccess(candidates: List<ScannerMatch>): LocalAdminAccessResult =
        withContext(Dispatchers.IO) {
            if (candidates.isEmpty()) return@withContext LocalAdminAccessResult.NotFound
            var deniedName: String? = null
            for (candidate in candidates) {
                val fresh = try {
                    resolveFreshAdminScanMatch(candidate)
                } catch (_: Exception) {
                    candidate
                }
                if (fresh.isAdminFlag()) {
                    return@withContext LocalAdminAccessResult.Granted(fresh, fresh.displayName())
                }
                deniedName = fresh.displayName()
                revokeBiometricAdminIfMatches(fresh)
            }
            LocalAdminAccessResult.Denied(deniedName.orEmpty())
        }

    suspend fun verifyLocalAdminAccess(match: ScannerMatch): LocalAdminAccessResult =
        verifyLocalAdminAccess(listOf(match))

    fun revokeBiometricAdminIfMatches(match: ScannerMatch) {
        val ctx = platformContext ?: return
        val settings = SettingsManager(ctx)
        val link = settings.getBiometricAdminProfileLink() ?: return
        val matches = when (match) {
            is ScannerMatch.VolunteerMatch ->
                link.type == BiometricAdminProfileType.VOLUNTEER && link.profileId == match.volunteer.id
            is ScannerMatch.GuestMatch ->
                link.type == BiometricAdminProfileType.GUEST && link.profileId == match.guest.nanoId
        }
        if (matches) {
            settings.setBiometricAdminLoginEnabled(false)
        }
    }

    private fun applyInitialBenefitFutureEntries(
        job: Job,
        configs: List<JobTypeConfig>,
        meetingNovaBenefitsExcludedForOrion: Boolean
    ): Job {
        if (job.benefitFutureEntriesRemaining != null) return job
        val config = configs.find { it.name == job.jobTypeName } ?: return job
        if (config.benefitSystemType == BenefitSystemType.MANUAL) {
            val n = config.manualRewards?.futureSingleUseEntries ?: 0
            if (n > 0) {
                val inv = config.manualRewards?.futureSingleUseEntryInvites ?: 1
                return job.copy(benefitFutureEntriesRemaining = n, benefitFutureEntryInvites = inv)
            }
            return job
        }
        if (config.benefitSystemType == BenefitSystemType.STELLAR && config.isShiftJob) {
            val entries: Int
            val invites: Int
            when (config.novaJobType) {
                NovaJobType.DEFAULT_SHIFT -> {
                    if (job.shiftTime != ShiftTime.AFTER_MIDNIGHT) return job
                    entries = 1; invites = 1
                }
                NovaJobType.MEETING -> {
                    if (meetingNovaBenefitsExcludedForOrion) return job
                    entries = 1; invites = 1
                }
                NovaJobType.PHOTOGRAPHER_VIDEOGRAPHER -> { entries = 1; invites = 1 }
                NovaJobType.GRAPHIC_DESIGNER_EVENT -> { entries = 1; invites = 1 }
                NovaJobType.GRAPHIC_DESIGNER_ASSOCIATION -> { entries = 2; invites = 1 }
            }
            return job.copy(benefitFutureEntriesRemaining = entries, benefitFutureEntryInvites = invites)
        }
        return job
    }

    private fun volunteerDisplayNameForSheetsJob(volunteerId: String): String {
        val v = _volunteers.value.find { it.id == volunteerId }
        return if (v != null) "${v.name} ${v.lastNameAbbreviation}".trim() else ""
    }

    private suspend fun syncCreatedTransfers(transfers: List<AccountTransfer>) {
        if (transfers.isEmpty()) return
        if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
            for (transfer in transfers) {
                when (val ledger = syncCoordinator?.commitTransfer(transfer)) {
                    is FirebaseLedgerResult.Rejected -> {
                        _syncError.value = ledger.reason
                    }
                    else -> Unit
                }
            }
        } else {
            syncCoordinator?.afterTransfersChanged() ?: twoWaySyncService?.backupTransfersToSheets()
        }
    }

    // Job operations
    fun addJob(job: Job) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("adding shifts")) return@launch
                val volunteerJobsSame = _jobs.value.filter { it.volunteerId == job.volunteerId }
                val meetingNovaExcluded = BenefitCalculator.isVolunteerOrionActive(
                    volunteerJobsSame, _jobTypeConfigs.value
                )
                val jobWithBenefit = applyInitialBenefitFutureEntries(
                    job, _jobTypeConfigs.value, meetingNovaExcluded
                )
                
                // Insert job into local database first
                val jobId = repository.insertJob(jobWithBenefit)
                var jobWithId = jobWithBenefit.copy(id = jobId)

                val coordinator = syncCoordinator
                if (coordinator != null) {
                    coordinator.afterJobSaved(jobWithId)
                } else if (isGoogleSheetsConfigured()) {
                    // Fallback when coordinator unavailable
                    val sheetsId = googleSheetsService.addJobToSheets(
                        jobWithId,
                        _venues.value,
                        _jobTypeConfigs.value,
                        volunteerDisplayNameForSheetsJob(jobWithId.volunteerId),
                    )
                    jobWithId = jobWithId.copy(sheetsId = sheetsId)
                    repository.updateJob(jobWithId)
                }
                
                println("Successfully added job: ${job.jobTypeName}")
                val volunteer = repository.getVolunteerById(jobWithId.volunteerId)
                if (volunteer != null) {
                    val offset = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
                    val credits = accountCreditService.applyShiftCredits(
                        jobWithId, volunteer, _jobTypeConfigs.value, offset
                    )
                    refreshAccountBalancesFromDb()
                    syncCreatedTransfers(credits)
                }
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
                if (rejectIfCrudSoftLocked("updating shifts")) return@launch
                repository.updateJob(job)
                val coordinator = syncCoordinator
                if (coordinator != null) {
                    coordinator.afterJobSaved(job)
                } else if (job.sheetsId != null) {
                        try {
                            googleSheetsService.updateJobInSheets(
                                job,
                                _venues.value,
                                _jobTypeConfigs.value,
                                volunteerDisplayNameForSheetsJob(job.volunteerId),
                            )
                        } catch (_: Exception) {
                            twoWaySyncService?.backupJobsToSheets()
                        }
                    } else {
                        twoWaySyncService?.backupJobsToSheets()
                    }
                
                println("Successfully updated job: ${job.jobTypeName}")
                if (coordinator == null) {
                    recalcAndUploadVolunteerGuestList()
                }
            } catch (e: Exception) {
                println("Failed to update job: ${e.message}")
                _syncError.value = "Failed to update job: ${e.message}"
            }
        }
    }

    fun deleteJob(job: Job) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("deleting shifts")) return@launch
                deletionTracker?.trackJobDeletion(
                    job.id.toString(),
                    job.sheetsId,
                    businessKey = "${job.volunteerId}_${job.jobTypeName}_${job.date}_${job.venueName}_${job.shiftTime}",
                )
                
                repository.deleteJob(job)
                
                syncCoordinator?.afterJobDeleted(job)
                    ?: if (job.sheetsId != null) {
                        try {
                            googleSheetsService.deleteJobFromSheets(job.id.toString(), job.sheetsId)
                        } catch (_: Exception) {
                            twoWaySyncService?.backupJobsToSheets()
                        }
                    } else {
                        twoWaySyncService?.backupJobsToSheets()
                    }
                
                println("Successfully deleted job: ${job.jobTypeName}")
                val volunteer = repository.getVolunteerById(job.volunteerId)
                if (volunteer != null) {
                    val offset = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
                    val reversals = accountCreditService.reverseShiftCredits(
                        job, volunteer, _jobTypeConfigs.value, offset
                    )
                    refreshAccountBalancesFromDb()
                    syncCreatedTransfers(reversals)
                }
                recalcAndUploadVolunteerGuestList()
            } catch (e: Exception) {
                println("Failed to delete job: ${e.message}")
                _syncError.value = "Failed to delete job: ${e.message}"
            }
        }
    }

    /**
     * Consumes one future event entry from a job (stellar after-midnight or manual single-use pool).
     * Updates the job locally and via SyncCoordinator, then recalculates the guest list.
     */
    fun markBenefitAsUsed(job: Job, selectedInvitesOverride: Int? = null) {
        viewModelScope.launch {
            benefitConsumeMutex.withLock {
                try {
                    if (rejectIfCrudSoftLocked("consuming benefits")) return@withLock
                    val offsetHours = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
                    val now = System.currentTimeMillis()
                    val configsByName = _jobTypeConfigs.value.associateBy { it.name }
                    val allJobs = _jobs.value
                    val meetingNovaExcluded = BenefitCalculator.isVolunteerOrionActive(
                        allJobs.filter { it.volunteerId == job.volunteerId },
                        _jobTypeConfigs.value,
                        now,
                        offsetHours
                    )
                    val selectedInvites = selectedInvitesOverride
                        ?: job.benefitFutureEntryInvites
                        ?: effectiveBenefitFutureEntryInvites(job, configsByName[job.jobTypeName])

                    val targetJob = allJobs.firstOrNull { candidate ->
                        val sameRecord = when {
                            job.sheetsId != null && candidate.sheetsId != null -> candidate.sheetsId == job.sheetsId
                            else -> candidate.id == job.id
                        }
                        val cfg = configsByName[candidate.jobTypeName]
                        sameRecord &&
                            jobTypeSupportsTrackedFutureEntries(candidate, cfg) &&
                            effectiveBenefitFutureEntriesRemaining(
                                candidate, cfg, now, offsetHours, meetingNovaExcluded
                            ) > 0 &&
                            effectiveBenefitFutureEntryInvites(candidate, cfg) == selectedInvites
                    } ?: allJobs
                        .asSequence()
                        .filter { it.volunteerId == job.volunteerId }
                        .filter { candidate ->
                            val cfg = configsByName[candidate.jobTypeName]
                            jobTypeSupportsTrackedFutureEntries(candidate, cfg) &&
                                effectiveBenefitFutureEntriesRemaining(
                                    candidate, cfg, now, offsetHours, meetingNovaExcluded
                                ) > 0 &&
                                effectiveBenefitFutureEntryInvites(candidate, cfg) == selectedInvites
                        }
                        .sortedBy { it.date }
                        .firstOrNull()
                        ?: return@withLock

                    val rem = targetJob.benefitFutureEntriesRemaining ?: return@withLock
                    if (rem <= 0) return@withLock
                    val config = _jobTypeConfigs.value.find { it.name == targetJob.jobTypeName }
                    val effectiveInvites = effectiveBenefitFutureEntryInvites(targetJob, config)
                    val updatedJob = targetJob.copy(
                        benefitFutureEntriesRemaining = rem - 1,
                        benefitFutureEntryInvites = effectiveInvites,
                        lastModified = System.currentTimeMillis()
                    )

                    repository.updateJob(updatedJob)

                    syncCoordinator?.afterBenefitEntryConsumed(updatedJob)
                        ?: if (updatedJob.sheetsId != null) {
                            try {
                                googleSheetsService.updateJobInSheets(
                                    updatedJob,
                                    _venues.value,
                                    _jobTypeConfigs.value,
                                    volunteerDisplayNameForSheetsJob(updatedJob.volunteerId),
                                )
                            } catch (_: Exception) {
                                twoWaySyncService?.backupJobsToSheets()
                            }
                        } else {
                            twoWaySyncService?.backupJobsToSheets()
                        }

                    refreshJobData()
                    recalcAndUploadVolunteerGuestList()
                    refreshGuestData()
                } catch (e: Exception) {
                    _syncError.value = "Failed to mark benefit as used: ${e.message}"
                }
            }
        }
    }

    // Job type config operations
    fun addJobTypeConfig(config: JobTypeConfig) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("adding shift types")) return@launch
                // Insert job type config into local database
                repository.insertJobTypeConfig(config)
                syncCoordinator?.afterJobTypeSaved(config) ?: twoWaySyncService?.backupJobTypesToSheets()
                
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
                if (rejectIfCrudSoftLocked("updating shift types")) return@launch
                // Update job type config in local database
                repository.updateJobTypeConfig(config)
                syncCoordinator?.afterJobTypeSaved(config) ?: twoWaySyncService?.backupJobTypesToSheets()
                
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
                if (rejectIfCrudSoftLocked("deleting shift types")) return@launch
                // Track the deletion (businessKey = name for merge-safe upload)
                deletionTracker?.trackJobTypeDeletion(config.id.toString(), config.sheetsId, businessKey = config.name)
                
                // Delete from local database
                repository.deleteJobTypeConfig(config)
                
                syncCoordinator?.afterJobTypeDeleted(config) ?: twoWaySyncService?.backupJobTypesToSheets()
                
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
                if (rejectIfCrudSoftLocked("adding venues")) return@launch
                // Insert venue into local database
                repository.insertVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                syncCoordinator?.afterVenueSaved(venue) ?: twoWaySyncService?.backupVenuesToSheets()
                
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
                if (rejectIfCrudSoftLocked("updating venues")) return@launch
                // Update venue in local database
                repository.updateVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                syncCoordinator?.afterVenueSaved(venue) ?: twoWaySyncService?.backupVenuesToSheets()
                
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
                if (rejectIfCrudSoftLocked("deleting venues")) return@launch
                // Track the deletion (businessKey = name for merge-safe upload)
                deletionTracker?.trackVenueDeletion(venue.id.toString(), venue.sheetsId, businessKey = venue.name)
                
                // Delete from local database
                repository.deleteVenue(venue)
                
                // BACKUP MODE: Upload entire venue dataset to Google Sheets
                // This ensures Google Sheets has the complete, up-to-date dataset
                syncCoordinator?.afterVenueDeleted(venue) ?: twoWaySyncService?.backupVenuesToSheets()
                
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
                if (rejectIfCrudSoftLocked("updating venue status")) return@launch
                repository.updateVenueStatus(id, isActive)
                val venue = repository.getVenueById(id)
                if (venue != null) {
                    syncCoordinator?.afterVenueSaved(venue) ?: twoWaySyncService?.backupVenuesToSheets()
                } else {
                    twoWaySyncService?.backupVenuesToSheets()
                }
                
                println("Successfully updated venue status: $id to $isActive")
            } catch (e: Exception) {
                println("Failed to update venue status: ${e.message}")
                _syncError.value = "Failed to update venue status: ${e.message}"
            }
        }
    }

    // Sales sheet item operations
    fun addSalesSheetItem(item: SalesSheetItem) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("adding sales items")) return@launch
                repository.insertSalesSheetItem(item)
                syncCoordinator?.afterSalesItemSaved(item) ?: twoWaySyncService?.backupSalesSheetItemsToSheets()
                println("Successfully added sales sheet item: ${item.name}")
            } catch (e: Exception) {
                println("Failed to add sales sheet item: ${e.message}")
                _syncError.value = "Failed to add sales sheet item: ${e.message}"
            }
        }
    }

    fun updateSalesSheetItem(item: SalesSheetItem) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("updating sales items")) return@launch
                repository.updateSalesSheetItem(item)
                syncCoordinator?.afterSalesItemSaved(item) ?: twoWaySyncService?.backupSalesSheetItemsToSheets()
                println("Successfully updated sales sheet item: ${item.name}")
            } catch (e: Exception) {
                println("Failed to update sales sheet item: ${e.message}")
                _syncError.value = "Failed to update sales sheet item: ${e.message}"
            }
        }
    }

    fun deleteSalesSheetItem(item: SalesSheetItem) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("deleting sales items")) return@launch
                deletionTracker?.trackSalesSheetItemDeletion(item.id.toString(), item.sheetsId, businessKey = item.name)
                repository.deleteSalesSheetItem(item)
                syncCoordinator?.afterSalesItemDeleted(item) ?: twoWaySyncService?.backupSalesSheetItemsToSheets()
                println("Successfully deleted sales sheet item: ${item.name}")
            } catch (e: Exception) {
                println("Failed to delete sales sheet item: ${e.message}")
                _syncError.value = "Failed to delete sales sheet item: ${e.message}"
            }
        }
    }

    private var institutionSettingsBackupJob: kotlinx.coroutines.Job? = null

    /** Upload shared institution settings (email texts, currency, date offset) to Google Sheets. Debounced for text fields. */
    fun backupInstitutionSettingsToSheets() {
        institutionSettingsBackupJob?.cancel()
        institutionSettingsBackupJob = viewModelScope.launch {
            delay(1500)
            try {
                if (rejectIfCrudSoftLocked("updating institution settings")) return@launch
                syncCoordinator?.afterInstitutionSettingsChanged()
                    ?: twoWaySyncService?.backupInstitutionSettingsToSheets()
            } catch (e: Exception) {
                println("Failed to backup institution settings: ${e.message}")
                _syncError.value = "Failed to backup institution settings: ${e.message}"
            }
        }
    }

    fun updateSalesSheetItemStatus(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("updating sales item status")) return@launch
                repository.updateSalesSheetItemStatus(id, isActive)
                val item = repository.getAllSalesSheetItems().first().find { it.id == id }
                if (item != null) {
                    syncCoordinator?.afterSalesItemSaved(item) ?: twoWaySyncService?.backupSalesSheetItemsToSheets()
                } else {
                    twoWaySyncService?.backupSalesSheetItemsToSheets()
                }
                println("Successfully updated sales sheet item status: $id to $isActive")
            } catch (e: Exception) {
                println("Failed to update sales sheet item status: ${e.message}")
                _syncError.value = "Failed to update sales sheet item status: ${e.message}"
            }
        }
    }

    // Job assignment operations - simplified for current Job model
    @Suppress("unused")
    fun assignJobToVolunteer(job: Job, volunteer: Volunteer) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("assigning jobs")) return@launch
                val updatedJob = job.copy(
                    volunteerId = volunteer.id,
                    lastModified = System.currentTimeMillis(),
                )
                repository.updateJob(updatedJob)
                
                // Update volunteer's last shift date
                val updatedVolunteer = volunteer.copy(lastShiftDate = System.currentTimeMillis())
                repository.updateVolunteer(updatedVolunteer)
                syncCoordinator?.afterJobSaved(updatedJob)
                syncCoordinator?.afterVolunteerSaved(updatedVolunteer)
                
                println("Successfully assigned job ${job.jobTypeName} to volunteer ${volunteer.name}")
        } catch (e: Exception) {
                println("Failed to assign job: ${e.message}")
                _syncError.value = "Failed to assign job: ${e.message}"
            }
        }
    }

    @Suppress("unused")
    fun updateVolunteerStatus(volunteer: Volunteer, isActive: Boolean) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("updating volunteers")) return@launch
                val updatedVolunteer = volunteer.copy(
                    isActive = isActive,
                    lastModified = System.currentTimeMillis(),
                )
                repository.updateVolunteer(updatedVolunteer)
                syncCoordinator?.afterVolunteerSaved(updatedVolunteer)
                    ?: twoWaySyncService?.backupVolunteersToSheets()
            } catch (e: Exception) {
                println("Failed to update volunteer status: ${e.message}")
                _syncError.value = "Failed to update volunteer status: ${e.message}"
            }
        }
    }

    // Single element upload methods (App Priority)
    @Suppress("unused")
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
            
            // Update local guest with sheets ID if it changed
            if (guest.sheetsId != sheetsId) {
                val updatedGuest = guest.copy(sheetsId = sheetsId)
                repository.updateGuest(updatedGuest)
            }
            
            println("Successfully uploaded single guest: ${guest.name}")
        } catch (e: Exception) {
            println("Failed to upload single guest: ${e.message}")
        }
    }
    
    @Suppress("unused")
    private suspend fun uploadSingleVolunteerToSheets(volunteer: Volunteer) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val jobs = repository.getAllJobs().first()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val benefitPrimaryRank = BenefitCalculator.calculateVolunteerBenefitStatus(
                volunteer, jobs, jobTypeConfigs
            ).rank
            
            val sheetsId = if (volunteer.sheetsId == null) {
                // New volunteer - add to sheets
                googleSheetsService.addVolunteerToSheets(volunteer, benefitPrimaryRank)
            } else {
                // Existing volunteer - update in sheets
                googleSheetsService.updateVolunteerInSheets(volunteer, benefitPrimaryRank)
                volunteer.sheetsId
            }
            
            // Update local volunteer with sheets ID if it changed
            if (volunteer.sheetsId != sheetsId) {
                val updatedVolunteer = volunteer.copy(sheetsId = sheetsId)
                repository.updateVolunteer(updatedVolunteer)
            }
            
            println("Successfully uploaded single volunteer: ${volunteer.name}")
        } catch (e: Exception) {
            println("Failed to upload single volunteer: ${e.message}")
        }
    }
    
    @Suppress("unused")
    private suspend fun uploadSingleJobToSheets(job: Job) {
        try {
            if (!isGoogleSheetsConfigured()) return
            googleSheetsService.initializeSheetsService()
            
            val sheetsId = if (job.sheetsId == null) {
                // New job - add to sheets
                googleSheetsService.addJobToSheets(
                    job,
                    _venues.value,
                    _jobTypeConfigs.value,
                    volunteerDisplayNameForSheetsJob(job.volunteerId)
                )
            } else {
                // Existing job - update in sheets
                googleSheetsService.updateJobInSheets(
                    job,
                    _venues.value,
                    _jobTypeConfigs.value,
                    volunteerDisplayNameForSheetsJob(job.volunteerId)
                )
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
    
    @Suppress("unused")
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
            
            // Update local job type with sheets ID if it changed
            if (config.sheetsId != sheetsId) {
                val updatedConfig = config.copy(sheetsId = sheetsId)
                repository.updateJobTypeConfig(updatedConfig)
            }
            
            println("Successfully uploaded single job type: ${config.name}")
        } catch (e: Exception) {
            println("Failed to upload single job type: ${e.message}")
        }
    }

    // Targeted sync operations for specific data types (Sheets Priority)
    @Suppress("unused")
    fun syncGuestsOnly() {
        viewModelScope.launch {
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
                
                // Perform database operations on IO dispatcher
                withContext(Dispatchers.IO) {
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
                }
                
                // CRITICAL: Refresh guest data on Main dispatcher to trigger UI update
                withContext(Dispatchers.Main) {
                    val updatedGuests = repository.getAllGuests().first()
                    _guests.value = removeDuplicateGuests(updatedGuests)
                    println("✅ Guest UI refreshed on Main dispatcher: ${_guests.value.size} guests")
                }
                
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
    
    @Suppress("unused")
    fun syncVolunteersOnly() {
        viewModelScope.launch {
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
                
                // Perform database operations on IO dispatcher
                withContext(Dispatchers.IO) {
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
                }
                
                // CRITICAL: Refresh volunteer data on Main dispatcher
                withContext(Dispatchers.Main) {
                    refreshVolunteerData()
                    println("✅ Volunteer UI refreshed on Main dispatcher")
                }
                
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
    
    @Suppress("unused")
    fun syncJobsOnly() {
        viewModelScope.launch {
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
                
                // Perform database operations on IO dispatcher
                withContext(Dispatchers.IO) {
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
                }
                
                // CRITICAL: Refresh job data on Main dispatcher
                withContext(Dispatchers.Main) {
                    refreshJobData()
                    println("✅ Job UI refreshed on Main dispatcher")
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

    @Suppress("unused")
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
                val coordinator = syncCoordinator
                if (coordinator != null) {
                    println("Manual sync via SyncCoordinator")
                    val result = withContext(Dispatchers.IO) { coordinator.performManualSync() }
                    if (result.isSuccess) {
                        refreshAllData()
                        recalcAndUploadVolunteerGuestList()
                        if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) {
                            refreshTemporaryGuestsFromSheets()
                        }
                        updateSyncTime()
                    } else {
                        val errorMsg = (result as? SyncResult.Error)?.message ?: "Sync failed"
                        _syncError.value = errorMsg
                        showSyncErrorIfNotSuppressed(errorMsg)
                    }
                    return@launch
                }

                println("Starting smart bidirectional sync with Google Sheets")
                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }
                
                googleSheetsService.initializeSheetsService()
                val localGuests = repository.getAllGuests().first()
                val localVolunteers = repository.getAllActiveVolunteers().first()
                val localJobs = repository.getAllJobs().first()
                val localJobTypeConfigs = repository.getAllJobTypeConfigs().first()
                val localVenues = repository.getAllVenues().first()
                twoWaySyncService?.backupToGoogleSheets()
                val (remoteGuests, remoteVolunteers, remoteJobs, remoteJobTypeConfigs, remoteVenues) = downloadChangesFromSheets()
                smartMergeData(localGuests, localVolunteers, localJobs, localJobTypeConfigs, localVenues,
                              remoteGuests, remoteVolunteers, remoteJobs, remoteJobTypeConfigs, remoteVenues)
                refreshAllData()
                updateSyncTime()
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
            val volunteerRanksForSheet = BenefitCalculator.volunteerPrimaryRanksForSheetUpload(
                localVolunteers, localJobs, localJobTypeConfigs
            )
            googleSheetsService.syncVolunteersToSheets(localVolunteers, volunteerRanksForSheet)
            googleSheetsService.syncJobsToSheets(localJobs, localVenues, localJobTypeConfigs, localVolunteers)
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
                it.businessKey == remoteConfig.name
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
        val smVenue = platformContext?.let { SettingsManager(it) }
        val myDeviceIdVenue = smVenue?.getOrCreatePersistentDeviceId()
        var venuesAdded = 0
        var venuesUpdated = 0
        for (remoteVenue in remoteVenues) {
            // Check if this item was deleted locally
            val isDeleted = deletedVenues.any {
                it.businessKey == remoteVenue.name
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
            } else {
                val keepLocalPeopleCounter = smVenue != null && myDeviceIdVenue != null &&
                    shouldKeepLocalPeopleCounterWhenPullingFromSheet(smVenue, myDeviceIdVenue, localVenue)
                val remoteVenueMetaNewer = remoteVenue.lastModified > localVenue.lastModified
                val remoteCounterNewer = remoteVenue.peopleCounterLastModified > localVenue.peopleCounterLastModified
                if (remoteVenueMetaNewer) {
                    try {
                        val merged = if (keepLocalPeopleCounter) {
                            remoteVenue.copy(
                                id = localVenue.id,
                                peopleCounterCount = localVenue.peopleCounterCount,
                                peopleCounterWriterDeviceId = localVenue.peopleCounterWriterDeviceId,
                                peopleCounterLastModified = localVenue.peopleCounterLastModified
                            )
                        } else {
                            remoteVenue.copy(id = localVenue.id)
                        }
                        repository.updateVenue(merged)
                        venuesUpdated++
                        println("Updated venue: ${remoteVenue.name}")
                    } catch (e: Exception) {
                        println("Failed to update venue: ${remoteVenue.name} - ${e.message}")
                    }
                } else if (remoteCounterNewer && !keepLocalPeopleCounter) {
                    try {
                        repository.updateVenue(
                            localVenue.copy(
                                peopleCounterCount = remoteVenue.peopleCounterCount,
                                peopleCounterWriterDeviceId = remoteVenue.peopleCounterWriterDeviceId,
                                peopleCounterLastModified = remoteVenue.peopleCounterLastModified
                            )
                        )
                        venuesUpdated++
                        println("Updated venue counter from sheets: ${remoteVenue.name}")
                    } catch (e: Exception) {
                        println("Failed to update venue counter: ${remoteVenue.name} - ${e.message}")
                    }
                }
            }
        }
        
        // Merge Guests
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any {
                it.businessKey == remoteGuest.nanoId
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { guestSameSyncIdentity(it, remoteGuest) }
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
                it.businessKey == remoteVolunteer.id
            }
            
            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name}")
                continue
            }
            
            val localVolunteer = localVolunteers.find {
                it.id == remoteVolunteer.id ||
                (it.name == remoteVolunteer.name && it.lastNameAbbreviation == remoteVolunteer.lastNameAbbreviation)
            }
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
                // Google Sheets is source of truth for NanoIDs - adopt the remote ID
                try {
                    if (localVolunteer.id != remoteVolunteer.id) {
                        // NanoID changed - need to update jobs that reference this volunteer
                        println("🔄 Volunteer '${remoteVolunteer.name}' NanoID changed: '${localVolunteer.id}' → '${remoteVolunteer.id}'")
                        repository.updateJobsVolunteerId(localVolunteer.id, remoteVolunteer.id)
                        repository.deleteVolunteer(localVolunteer)
                        repository.insertVolunteer(remoteVolunteer)
                    } else {
                        repository.updateVolunteer(remoteVolunteer)
                    }
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
            val remoteJobBusinessKey =
                "${remoteJob.volunteerId}_${remoteJob.jobTypeName}_${remoteJob.date}_${remoteJob.venueName}_${remoteJob.shiftTime}"
            val isDeleted = deletedJobs.any {
                it.businessKey == remoteJobBusinessKey
            }
            
            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }
            
            val localJob = localJobs.find { 
                it.volunteerId == remoteJob.volunteerId &&
                it.jobTypeName == remoteJob.jobTypeName &&
                it.date == remoteJob.date &&
                it.venueName == remoteJob.venueName &&
                it.shiftTime == remoteJob.shiftTime
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
    @Suppress("unused")
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    
    // Helper methods for targeted sync operations
    @Suppress("unused")
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
    
    @Suppress("unused")
    private suspend fun uploadVolunteersToSheets(volunteers: List<Volunteer>) {
        try {
            val jobs = repository.getAllJobs().first()
            val jobTypeConfigs = repository.getAllJobTypeConfigs().first()
            val volunteerRanksForSheet = BenefitCalculator.volunteerPrimaryRanksForSheetUpload(
                volunteers, jobs, jobTypeConfigs
            )
            googleSheetsService.syncVolunteersToSheets(volunteers, volunteerRanksForSheet)
            println("Uploaded ${volunteers.size} volunteers to sheets")
            } catch (e: Exception) {
            println("Failed to upload volunteers: ${e.message}")
            throw e
        }
    }
    
    @Suppress("unused")
    private suspend fun uploadJobsToSheets(jobs: List<Job>) {
        try {
            val venues = repository.getAllVenues().first()
            val volunteers = repository.getAllVolunteers().first()
            googleSheetsService.syncJobsToSheets(jobs, venues, _jobTypeConfigs.value, volunteers)
            println("Uploaded ${jobs.size} jobs to sheets")
        } catch (e: Exception) {
            println("Failed to upload jobs: ${e.message}")
            throw e
        }
    }
    
    @Suppress("unused")
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

    private suspend fun refreshTemporaryGuestsFromSheets() = withContext(Dispatchers.IO) {
        try {
            val ctx = platformContext ?: return@withContext
            val settingsManager = SettingsManager(ctx)
            if (settingsManager.getBackendType() == BackendType.FIREBASE) {
                // Temp guests live in Room + Firestore; do not wipe from Sheets.
                withContext(Dispatchers.Main) { refreshGuestData() }
                return@withContext
            }

            if (!settingsManager.isConfigured()) {
                println("Skipping temporary guest refresh - Google Sheets not configured")
                return@withContext
            }

            val tempService = googleSheetsService
            if (tempService.getSheetsService() == null) {
                tempService.initializeSheetsService()
            }

            val tempGuestsRaw = tempService.syncTempGuestsFromSheets()

            // Respect the custom “day change” offset from settings (e.g. 3h → day switches at 03:00)
            val offsetHours = settingsManager.getDateChangeOffsetHours()
            val zone = java.time.ZoneId.of("Europe/Zurich")
            val now = java.time.ZonedDateTime.now(zone)
            val effectiveNow = if (offsetHours != 0 && now.hour < offsetHours) {
                now.minusDays(1)
            } else {
                now
            }
            val effectiveToday = effectiveNow.toLocalDate()

            val guests = tempGuestsRaw.map { temp ->
                Guest(
                    sheetsId = temp.rowNumber.toString(),
                    nanoId = NanoIdGenerator.ensureValidNanoId(temp.nanoId, temp.guestName),
                    name = temp.guestName,
                    email = "",
                    phoneNumber = "",
                    invitations = 1,
                    venueName = "BOTH",
                    notes = temp.comment,
                    isVolunteerBenefit = false,
                    volunteerId = null,
                    lastModified = temp.modificationDate.atStartOfDay(zone).toEpochSecond() * 1000,
                    isTemporaryGuest = true,
                    temporaryArtistName = temp.artistName,
                    temporaryEventDate = temp.eventDate.atStartOfDay(zone).toEpochSecond() * 1000,
                    temporaryContactPhone = temp.artistContactPhone
                )
            }

            repository.replaceTemporaryGuests(guests)
            withContext(Dispatchers.Main) {
                refreshGuestData()
            }
            updateSyncTime()
            println("Refreshed ${guests.size} temporary guests from sheets (effective today: $effectiveToday, offsetHours=$offsetHours)")
        } catch (e: Exception) {
            println("Failed to refresh temporary guests from sheets: ${e.message}")
        }
    }

    fun refreshTemporaryGuests() {
        viewModelScope.launch {
            refreshTemporaryGuestsFromSheets()
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
            val volunteers = repository.getAllVolunteers().first()
            val jobs = googleSheetsService.syncJobsFromSheets(jobTypeConfigs, volunteers)
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
    
    @Suppress("unused")
    private suspend fun mergeGuestData(localGuests: List<Guest>, remoteGuests: List<Guest>) {
        val deletedGuests = deletionTracker?.getDeletedGuests() ?: emptyList()
        var guestsAdded = 0
        var guestsUpdated = 0
        
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any {
                it.businessKey == remoteGuest.nanoId
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { guestSameSyncIdentity(it, remoteGuest) }
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
    
    @Suppress("unused")
    private suspend fun mergeVolunteerData(localVolunteers: List<Volunteer>, remoteVolunteers: List<Volunteer>) {
        val deletedVolunteers = deletionTracker?.getDeletedVolunteers() ?: emptyList()
        var volunteersAdded = 0
        var volunteersUpdated = 0
        
        // Get all jobs to calculate activity - OPTIMIZED: group once instead of filtering for each volunteer
        val allJobs = repository.getAllJobs().first()
        val jobsByVolunteerId = VolunteerActivityManager.groupJobsByVolunteerId(allJobs)
        
        // OPTIMIZED: Create lookup maps for O(1) access instead of O(n) find operations
        val localVolunteersById = localVolunteers.associateBy { it.id }
        // Use name + abbreviation as key to allow multiple volunteers with same first name
        val localVolunteersByFullName = localVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}" }
        
        // OPTIMIZED: Create sets for O(1) deletion checks instead of O(n) any operations
        val deletedVolunteerKeys = deletedVolunteers.mapNotNullTo(mutableSetOf()) { it.businessKey }
        
        for (remoteVolunteer in remoteVolunteers) {
            // OPTIMIZED: Use set lookup instead of any (O(1) vs O(n))
            val isDeleted = remoteVolunteer.id in deletedVolunteerKeys
            
            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name} ${remoteVolunteer.lastNameAbbreviation}")
                continue
            }
            
            // OPTIMIZED: Use map lookup instead of find (O(1) vs O(n))
            val localVolunteer = localVolunteersById[remoteVolunteer.id]
                ?: localVolunteersByFullName["${remoteVolunteer.name}_${remoteVolunteer.lastNameAbbreviation}"]
            if (localVolunteer == null) {
                // New volunteer from sheets
                try {
                    // Calculate activity based on job assignments - OPTIMIZED: uses map lookup
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobsMap(remoteVolunteer, jobsByVolunteerId)
                    repository.insertVolunteer(updatedVolunteer)
                    volunteersAdded++
                    println("Added new volunteer: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to add volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else if (remoteVolunteer.lastModified > localVolunteer.lastModified) {
                // Remote version is newer
                // Google Sheets is source of truth for NanoIDs - adopt the remote ID
                try {
                    // Calculate activity based on job assignments - OPTIMIZED: uses map lookup
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobsMap(remoteVolunteer, jobsByVolunteerId)
                    if (localVolunteer.id != remoteVolunteer.id) {
                        // NanoID changed - need to update jobs that reference this volunteer
                        println("🔄 Volunteer '${remoteVolunteer.name}' NanoID changed: '${localVolunteer.id}' → '${remoteVolunteer.id}'")
                        repository.updateJobsVolunteerId(localVolunteer.id, remoteVolunteer.id)
                        repository.deleteVolunteer(localVolunteer)
                        repository.insertVolunteer(updatedVolunteer)
                    } else {
                        repository.updateVolunteer(updatedVolunteer)
                    }
                    volunteersUpdated++
                    println("Updated volunteer: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to update volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else {
                // Local version is newer or same - update activity based on jobs
                try {
                    // Calculate activity based on job assignments - OPTIMIZED: uses map lookup
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobsMap(localVolunteer, jobsByVolunteerId)
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
    @Suppress("unused")
    private suspend fun mergeGuestDataSheetsPriority(localGuests: List<Guest>, remoteGuests: List<Guest>) {
        val deletedGuests = deletionTracker?.getDeletedGuests() ?: emptyList()
        var guestsAdded = 0
        var guestsUpdated = 0
        
        for (remoteGuest in remoteGuests) {
            // Check if this item was deleted locally
            val isDeleted = deletedGuests.any {
                it.businessKey == remoteGuest.nanoId
            }
            
            if (isDeleted) {
                println("Skipping deleted guest: ${remoteGuest.name}")
                continue
            }
            
            val localGuest = localGuests.find { guestSameSyncIdentity(it, remoteGuest) }
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
    
    @Suppress("unused")
    private suspend fun mergeVolunteerDataSheetsPriority(localVolunteers: List<Volunteer>, remoteVolunteers: List<Volunteer>) {
        val deletedVolunteers = deletionTracker?.getDeletedVolunteers() ?: emptyList()
        var volunteersAdded = 0
        var volunteersUpdated = 0

        // Get all jobs to calculate activity - OPTIMIZED: group once instead of filtering for each volunteer
        val allJobs = repository.getAllJobs().first()
        val jobsByVolunteerId = VolunteerActivityManager.groupJobsByVolunteerId(allJobs)

        // OPTIMIZED: Create lookup maps for O(1) access instead of O(n) find operations
        val localVolunteersById = localVolunteers.associateBy { it.id }
        // Use name + abbreviation as key to allow multiple volunteers with same first name
        val localVolunteersByFullName = localVolunteers.associateBy { "${it.name}_${it.lastNameAbbreviation}" }

        // OPTIMIZED: Create sets for O(1) deletion checks instead of O(n) any operations
        val deletedVolunteerKeys = deletedVolunteers.mapNotNullTo(mutableSetOf()) { it.businessKey }

        for (remoteVolunteer in remoteVolunteers) {
            // OPTIMIZED: Use set lookup instead of any (O(1) vs O(n))
            val isDeleted = remoteVolunteer.id in deletedVolunteerKeys

            if (isDeleted) {
                println("Skipping deleted volunteer: ${remoteVolunteer.name} ${remoteVolunteer.lastNameAbbreviation}")
                continue
            }

            // OPTIMIZED: Use map lookup instead of find (O(1) vs O(n))
            val localVolunteer = localVolunteersById[remoteVolunteer.id]
                ?: localVolunteersByFullName["${remoteVolunteer.name}_${remoteVolunteer.lastNameAbbreviation}"]
            if (localVolunteer == null) {
                // New volunteer from sheets
                try {
                    // Calculate activity based on job assignments - OPTIMIZED: uses map lookup
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobsMap(remoteVolunteer, jobsByVolunteerId)
                    repository.insertVolunteer(updatedVolunteer)
                    volunteersAdded++
                    println("Added new volunteer from sheets: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to add volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            } else {
                // Always use remote version (sheets priority)
                // Google Sheets is source of truth for NanoIDs - adopt the remote ID
                try {
                    // Calculate activity based on job assignments - OPTIMIZED: uses map lookup
                    val updatedVolunteer = VolunteerActivityManager.calculateActivityFromJobsMap(remoteVolunteer, jobsByVolunteerId)
                    if (localVolunteer.id != remoteVolunteer.id) {
                        // NanoID changed - need to update jobs that reference this volunteer
                        println("🔄 Volunteer '${remoteVolunteer.name}' NanoID changed: '${localVolunteer.id}' → '${remoteVolunteer.id}'")
                        repository.updateJobsVolunteerId(localVolunteer.id, remoteVolunteer.id)
                        repository.deleteVolunteer(localVolunteer)
                        repository.insertVolunteer(updatedVolunteer)
                    } else {
                        repository.updateVolunteer(updatedVolunteer)
                    }
                    volunteersUpdated++
                    println("Updated volunteer from sheets: ${remoteVolunteer.name}")
                } catch (e: Exception) {
                    println("Failed to update volunteer: ${remoteVolunteer.name} - ${e.message}")
                }
            }
        }

        println("Sheets priority volunteer merge results: +$volunteersAdded ~$volunteersUpdated")
    }
    
    @Suppress("unused")
    private suspend fun mergeJobDataSheetsPriority(localJobs: List<Job>, remoteJobs: List<Job>) {
        val deletedJobs = deletionTracker?.getDeletedJobs() ?: emptyList()
        var jobsAdded = 0
        var jobsUpdated = 0

        for (remoteJob in remoteJobs) {
            // Check if this item was deleted locally
            val remoteJobBusinessKey =
                "${remoteJob.volunteerId}_${remoteJob.jobTypeName}_${remoteJob.date}_${remoteJob.venueName}_${remoteJob.shiftTime}"
            val isDeleted = deletedJobs.any {
                it.businessKey == remoteJobBusinessKey
            }

            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }

            val localJob = localJobs.find {
                it.volunteerId == remoteJob.volunteerId &&
                it.jobTypeName == remoteJob.jobTypeName &&
                it.date == remoteJob.date &&
                it.venueName == remoteJob.venueName &&
                it.shiftTime == remoteJob.shiftTime
            }
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
    
    @Suppress("unused")
    private suspend fun mergeJobTypeDataSheetsPriority(localJobTypes: List<JobTypeConfig>, remoteJobTypes: List<JobTypeConfig>) {
        val deletedJobTypes = deletionTracker?.getDeletedJobTypes() ?: emptyList()
        var jobTypesAdded = 0
        var jobTypesUpdated = 0

        for (remoteJobType in remoteJobTypes) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobTypes.any {
                it.businessKey == remoteJobType.name
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
    
    @Suppress("unused")
    private suspend fun mergeJobData(localJobs: List<Job>, remoteJobs: List<Job>) {
        val deletedJobs = deletionTracker?.getDeletedJobs() ?: emptyList()
        var jobsAdded = 0
        var jobsUpdated = 0
        
        for (remoteJob in remoteJobs) {
            // Check if this item was deleted locally
            val remoteJobBusinessKey =
                "${remoteJob.volunteerId}_${remoteJob.jobTypeName}_${remoteJob.date}_${remoteJob.venueName}_${remoteJob.shiftTime}"
            val isDeleted = deletedJobs.any {
                it.businessKey == remoteJobBusinessKey
            }
            
            if (isDeleted) {
                println("Skipping deleted job: ${remoteJob.jobTypeName}")
                continue
            }
            
            val localJob = localJobs.find {
                it.volunteerId == remoteJob.volunteerId &&
                it.jobTypeName == remoteJob.jobTypeName &&
                it.date == remoteJob.date &&
                it.venueName == remoteJob.venueName &&
                it.shiftTime == remoteJob.shiftTime
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
    
    @Suppress("unused")
    private suspend fun mergeJobTypeData(localJobTypes: List<JobTypeConfig>, remoteJobTypes: List<JobTypeConfig>) {
        val deletedJobTypes = deletionTracker?.getDeletedJobTypes() ?: emptyList()
        var jobTypesAdded = 0
        var jobTypesUpdated = 0
        
        for (remoteJobType in remoteJobTypes) {
            // Check if this item was deleted locally
            val isDeleted = deletedJobTypes.any {
                it.businessKey == remoteJobType.name
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
        val updatedVenues = withContext(Dispatchers.IO) {
            repository.getAllVenues().first()
        }
        withContext(Dispatchers.Main) {
            _venues.value = removeDuplicateVenues(updatedVenues)
        }
    }

    private suspend fun refreshSalesSheetItemData() {
        val updatedItems = repository.getAllSalesSheetItems().first()
        _salesSheetItems.value = updatedItems
    }
    
    private fun updateSyncTime() {
        val currentTime = System.currentTimeMillis()
        platformContext?.let { ctx ->
            SettingsManager(ctx).recordSheetsPullAt(currentTime)
        }
        _lastSyncTime.value = currentTime
    }

    // Sync app data to Google Sheets (merge-safe: reads remote data before overwriting)
    @Suppress("unused")
    private suspend fun syncToGoogleSheets() {
        try {
            if (!isGoogleSheetsConfigured()) {
                println("Google Sheets not configured, skipping sync")
                return
            }

            twoWaySyncService?.backupToGoogleSheets()

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
            performFullSyncAwait(suppressSyncErrorDialog = false)
        }
    }

    /**
     * Same as [performFullSync] but suspends until the sync job finishes. Use this for security
     * gates (first-admin precheck) where fire-and-forget would leave local Room empty while the UI
     * already decided there is no remote admin.
     */
    suspend fun performFullSyncAwait(suppressSyncErrorDialog: Boolean = false): SyncResult {
        _isSyncing.value = true
        _syncError.value = null

        AppLogger.i("EventManagerViewModel", "Starting full sync")

        return try {
            val result = withContext(Dispatchers.IO) {
                syncCoordinator?.performStartupSync() ?: syncManager?.performFullSync()
            } ?: SyncResult.Error("Full sync failed")

            if (result.isSuccess) {
                println("🔄 Sync successful, refreshing UI data...")
                println("📊 Before refresh - Guests: ${_guests.value.size}, Volunteers: ${_volunteers.value.size}")

                withContext(Dispatchers.Main) {
                    refreshAllData()
                    println("✅ UI data refreshed on Main dispatcher after sync")
                    println("📊 After refresh - Guests: ${_guests.value.size}, Volunteers: ${_volunteers.value.size}")
                }

                recalcAndUploadVolunteerGuestList()
                if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) {
                    refreshTemporaryGuestsFromSheets()
                }
                updateSyncTime()

                AppLogger.i("EventManagerViewModel", "Full sync completed successfully")
                println("✅✅✅ Full sync completed successfully - UI should be updated now")
                result
            } else {
                val errorMsg = (result as? SyncResult.Error)?.message ?: "Full sync failed"
                _syncError.value = errorMsg
                AppLogger.e("EventManagerViewModel", "Full sync failed: $errorMsg")
                if (!suppressSyncErrorDialog) {
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
                try {
                    recalcAndUploadVolunteerGuestList(skipSheetsUpload = true)
                } catch (re: Exception) {
                    println("⚠️ Volunteer guest list recalc after failed sync: ${re.message}")
                }
                result
            }
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("429") == true || e.message?.contains("Rate limit") == true ->
                    "Rate limit exceeded. Please try again later."
                else -> "Full sync failed: ${e.message}"
            }
            _syncError.value = errorMsg
            AppLogger.e("EventManagerViewModel", "Full sync exception", e)
            if (!suppressSyncErrorDialog) {
                showSyncErrorIfNotSuppressed(errorMsg)
            }
            println("Full sync error: $errorMsg")
            try {
                recalcAndUploadVolunteerGuestList(skipSheetsUpload = true)
            } catch (re: Exception) {
                println("⚠️ Volunteer guest list recalc after sync exception: ${re.message}")
            }
            SyncResult.Error(errorMsg)
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Ensures sheet structure + data are ready before the admin NFC/QR gate.
     * Skips a full network repair/download when a successful Sheets pull already happened
     * recently (startup precheck), so tapping Admin is not delayed by a second full sync.
     */
    fun prepareForAdminAuthentication() {
        viewModelScope.launch {
            _adminGateSyncSucceeded.value = null
            _syncError.value = null
            try {
                // Startup precheck may still be running — wait for it instead of stacking another sync.
                if (_isSyncing.value) {
                    AppLogger.i(
                        "EventManagerViewModel",
                        "Admin gate waiting for in-flight sync instead of starting another"
                    )
                    isSyncing.first { !it }
                    if (hasFreshRemotePullForAdminGate()) {
                        withContext(Dispatchers.Main) { refreshAllData() }
                        _adminGateSyncSucceeded.value = true
                        AppLogger.i("EventManagerViewModel", "Admin gate preparation reused fresh sync")
                        return@launch
                    }
                } else if (hasFreshRemotePullForAdminGate()) {
                    withContext(Dispatchers.Main) { refreshAllData() }
                    _adminGateSyncSucceeded.value = true
                    AppLogger.i(
                        "EventManagerViewModel",
                        "Admin gate preparation skipped full sync (data fresh)"
                    )
                    return@launch
                }

                _isSyncing.value = true
                AppLogger.i(
                    "EventManagerViewModel",
                    "Preparing remote sync for admin authentication gate"
                )
                val result = withContext(Dispatchers.IO) {
                    syncCoordinator?.prepareForAdminGate()
                        ?: syncManager?.repairSheetStructureThenFullDownload()
                }
                if (result?.isSuccess == true) {
                    println("🔄 Admin gate sync successful, refreshing UI data...")
                    withContext(Dispatchers.Main) {
                        refreshAllData()
                    }
                    recalcAndUploadVolunteerGuestList()
                    if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) {
                        refreshTemporaryGuestsFromSheets()
                    }
                    updateSyncTime()
                    _adminGateSyncSucceeded.value = true
                    AppLogger.i("EventManagerViewModel", "Admin gate preparation completed")
                } else {
                    val errorResult = result as? SyncResult.Error
                    val errorMsg = errorResult?.message ?: "Admin gate sync failed"
                    _syncError.value = errorMsg
                    _adminGateSyncSucceeded.value = false
                    AppLogger.e("EventManagerViewModel", "Admin gate sync failed: $errorMsg")
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true ->
                        "Rate limit exceeded. Please try again later."
                    else -> "Admin gate sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                _adminGateSyncSucceeded.value = false
                AppLogger.e("EventManagerViewModel", "Admin gate sync exception", e)
                showSyncErrorIfNotSuppressed(errorMsg)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun retryAdminGateSync() {
        prepareForAdminAuthentication()
    }

    private fun hasFreshRemotePullForAdminGate(
        maxAgeMs: Long = adminGateFreshSyncMs,
    ): Boolean {
        val memory = _lastSyncTime.value
        val persisted = platformContext?.let { SettingsManager(it).getLastSheetsPullAt() } ?: 0L
        val last = maxOf(memory, persisted)
        return last > 0L && System.currentTimeMillis() - last < maxAgeMs
    }

    // Unified guest list: regular guests + volunteer benefits (computed locally)
    private suspend fun computeVolunteerGuestEntries(): List<Guest> {
        try {
            val offsetHours = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
            val statuses = repository.getAllVolunteerBenefitStatuses(offsetHours)
            val entries = mutableListOf<Guest>()
            
            // Batch load all volunteers instead of querying one-by-one
            val allVolunteers = repository.getAllVolunteers().first()
            val volunteersById = allVolunteers.associateBy { it.id }
            
            for (status in statuses) {
                val benefits = status.benefits
                if (!benefits.guestListAccess) continue
                val futureEntryPoolRemaining = benefits.futureEventEntriesRemaining ?: 0
                if (!benefits.isActive && futureEntryPoolRemaining <= 0) continue

                // Guest list row only if free entry, +1 / invites, or redeemable future entries — not drinks/bar alone
                val hasEntryOrTickets = benefits.freeEntry ||
                    benefits.friendInvitation ||
                    benefits.inviteCount > 0 ||
                    futureEntryPoolRemaining > 0
                if (!hasEntryOrTickets) continue

                val volunteer = volunteersById[status.volunteerId] ?: continue
                entries.add(
                    Guest(
                        name = volunteer.name,
                        lastNameAbbreviation = volunteer.lastNameAbbreviation,
                        invitations = benefits.inviteCount,
                        venueName = "BOTH",
                        notes = "Volunteer benefit - ${getRankDisplayName(status.rank)}",
                        isVolunteerBenefit = true,
                        volunteerId = volunteer.id,
                        nfcCardUid = volunteer.nfcCardUid
                    )
                )
            }
            
            return entries
        } catch (e: Exception) {
            println("Error computing volunteer guest entries: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    suspend fun recalcAndUploadVolunteerGuestList(skipSheetsUpload: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            println("Starting volunteer guest list recalculation with differential updates...")

            // Compute new volunteer entries
            val newVolunteerGuests = computeVolunteerGuestEntries()
            println("Computed ${newVolunteerGuests.size} volunteer guest entries")

            // Get existing volunteer benefit guests
            val existingVolunteerGuests = repository.getVolunteerBenefitGuests()
            println("Found ${existingVolunteerGuests.size} existing volunteer benefit guests")

            // Create maps for efficient comparison using volunteerId as key
            val existingByVolunteerId = existingVolunteerGuests.associateBy { it.volunteerId }
            val newByVolunteerId = newVolunteerGuests.associateBy { it.volunteerId }

            // Determine changes
            val toDelete = mutableListOf<Guest>()
            val toInsert = mutableListOf<Guest>()
            val toUpdate = mutableListOf<Guest>()

            // Find deleted (in existing but not in new)
            for ((volunteerId, existingGuest) in existingByVolunteerId) {
                if (volunteerId != null && !newByVolunteerId.containsKey(volunteerId)) {
                    toDelete.add(existingGuest)
                }
            }

            // Find new and modified
            for ((volunteerId, newGuest) in newByVolunteerId) {
                val existingGuest = existingByVolunteerId[volunteerId]
                if (existingGuest == null) {
                    toInsert.add(newGuest)
                } else if (existingGuest.name != newGuest.name ||
                           existingGuest.invitations != newGuest.invitations ||
                           existingGuest.notes != newGuest.notes ||
                           existingGuest.nfcCardUid != newGuest.nfcCardUid) {
                    // Modified - update with existing ID
                    toUpdate.add(newGuest.copy(id = existingGuest.id))
                }
                // If unchanged, do nothing
            }

            println("Volunteer benefit changes: ${toInsert.size} new, ${toUpdate.size} modified, ${toDelete.size} deleted")

            // Apply database changes
            if (toDelete.isNotEmpty()) {
                toDelete.forEach { repository.deleteGuest(it) }
            }
            if (toInsert.isNotEmpty()) {
                toInsert.forEach { repository.insertGuest(it) }
            }
            if (toUpdate.isNotEmpty()) {
                toUpdate.forEach { repository.updateGuest(it) }
            }

            // Apply targeted UI updates only if there were changes
            val hasChanges = toDelete.isNotEmpty() || toInsert.isNotEmpty() || toUpdate.isNotEmpty()
            if (hasChanges) {
                withContext(Dispatchers.Main) {
                    val currentGuests = _guests.value.toMutableList()
                    
                    // Remove deleted volunteer benefits from UI
                    toDelete.forEach { deleted ->
                        currentGuests.removeAll { it.volunteerId == deleted.volunteerId && it.isVolunteerBenefit }
                    }
                    
                    // Add new volunteer benefits to UI
                    currentGuests.addAll(toInsert)
                    
                    // Update modified volunteer benefits in UI
                    toUpdate.forEach { updated ->
                        val index = currentGuests.indexOfFirst { it.volunteerId == updated.volunteerId && it.isVolunteerBenefit }
                        if (index >= 0) {
                            currentGuests[index] = updated
                        } else {
                            // If not found, add it
                            currentGuests.add(updated)
                        }
                    }
                    
                    _guests.value = removeDuplicateGuests(currentGuests)
                    println("✅ Applied ${toInsert.size + toUpdate.size + toDelete.size} volunteer benefit UI updates")
                }
            } else {
                println("No volunteer benefit changes to apply to UI")
            }

            // Upload to Google Sheets only after local/UI state is updated so the guest list reacts
            // immediately even if network sync is slow or rate-limited.
            val firebaseMode = settingsManagerCached?.getBackendType() == BackendType.FIREBASE
            if (!skipSheetsUpload && !firebaseMode && isGoogleSheetsConfigured()) {
                println("Uploading volunteer guest list to Google Sheets...")
                googleSheetsService.initializeSheetsService()
                googleSheetsService.syncVolunteerGuestListToSheets(newVolunteerGuests, _venues.value)
                println("Successfully uploaded volunteer guest list to Google Sheets")
            } else if (firebaseMode) {
                toDelete.forEach { guest ->
                    syncCoordinator?.afterGuestDeleted(guest)
                }
                (toInsert + toUpdate).forEach { guest ->
                    syncCoordinator?.afterGuestSaved(guest)
                }
            }
            
            println("Volunteer guest list recalculation completed successfully")
        } catch (e: Exception) {
            println("Failed to recalc/upload volunteer guest list: ${e.message}")
            e.printStackTrace()
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
                val result = withContext(Dispatchers.IO) {
                    syncCoordinator?.performPageChangeSync(currentPage, newPage)
                        ?: syncManager?.performSmartPageChangeSync(currentPage, newPage)
                }
                
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
    @Suppress("unused")
    fun performSimplePageChangeSync(currentPage: String, newPage: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            try {
                val result = withContext(Dispatchers.IO) {
                    syncCoordinator?.performPageChangeSync(currentPage, newPage)
                        ?: syncManager?.performPageChangeSync(currentPage, newPage)
                }
                
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
    @Suppress("unused")
    fun getExpectedHeaders(sheetType: String): List<String>? {
        return syncManager?.getExpectedHeaders(sheetType)
    }
    
    /**
     * GET ALL EXPECTED HEADERS: Get all expected headers for all sheet types
     */
    @Suppress("unused")
    fun getAllExpectedHeaders(): Map<String, List<String>> {
        return syncManager?.getAllExpectedHeaders() ?: emptyMap()
    }

    // Convenience methods for different sync modes
    @Suppress("unused")
    fun performAutoSync() = performFullSync()

    @Suppress("unused")
    fun clearSyncError() {
        _syncError.value = null
    }
    
    /**
     * UI CONVENIENCE METHODS for page change sync
     */
    @Suppress("unused")
    fun onPageChangeToGuests() {
        performPageChangeSync("", "guests")
    }
    
    @Suppress("unused")
    fun onPageChangeToVolunteers() {
        performPageChangeSync("", "volunteers")
    }
    
    @Suppress("unused")
    fun onPageChangeToJobs() {
        performPageChangeSync("", "jobs")
    }
    
    @Suppress("unused")
    fun onPageChangeToJobTypes() {
        performPageChangeSync("", "job_types")
    }
    
    @Suppress("unused")
    fun onPageChange(from: String, to: String) {
        performPageChangeSync(from, to)
    }
    
    /**
     * MANUAL SYNC TRIGGERS
     */
    @Suppress("unused")
    fun triggerManualSync() {
        performFullSync()
    }
    
    @Suppress("unused")
    fun triggerBackupToSheets() {
        performBackupToSheets()
    }
    
    @Suppress("unused")
    fun triggerValidation() {
        validateGoogleSheetsStructure()
    }
    
    @Suppress("unused")
    fun triggerDataStructureValidation() {
        validateDataStructure()
    }
    
    @Suppress("unused")
    fun triggerCreateOrFixSheetStructure() {
        createOrFixSheetStructure()
    }
    
    @Suppress("unused")
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
     * Full factory reset: wipe DB tables, preferences, and credential/cache files.
     * Caller should navigate back to the setup wizard after [onComplete].
     */
    fun factoryReset(
        settingsManager: SettingsManager,
        fileManager: PlatformFileManager,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                FactoryReset.perform(repository, settingsManager, fileManager)
                refreshAllData()
                onComplete()
            } catch (e: Exception) {
                println("Factory reset failed: ${e.message}")
                onComplete()
            }
        }
    }
    
    /**
     * FORCE REFRESH VOLUNTEERS: Force sync volunteers from Google Sheets
     * This is useful for debugging and ensuring volunteers are properly loaded
     */
    @Suppress("unused")
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
    
    @Suppress("unused")
    fun clearAllErrors() {
        _syncError.value = null
    }
    
    /**
     * FORCE REFRESH ALL DATA: Force refresh all data from database
     * This is useful for debugging and ensuring data is properly loaded
     */
    @Suppress("unused")
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
    
    private suspend fun refreshAllData() {
        try {
            println("🔄 Starting refreshAllData...")
            
            // CRITICAL: Small delay to ensure database changes have propagated
            // Room's Flow emissions can be delayed after batch operations
            delay(100)
            
            // Get data from repository on IO dispatcher
            val (guests, volunteers, jobs, jobTypeConfigs, venues, salesSheetItems) = withContext(Dispatchers.IO) {
                println("📊 Reading fresh data from database...")
                val guestsData = repository.getAllGuests().first()
                val volunteersData = repository.getAllVolunteers().first()
                val jobsData = repository.getAllJobs().first()
                val jobTypeConfigsData = repository.getAllJobTypeConfigs().first()
                val venuesData = repository.getAllVenues().first()
                val salesSheetItemsData = repository.getAllSalesSheetItems().first()
                
                println("📊 Database read complete: ${guestsData.size} guests, ${volunteersData.size} volunteers, ${jobsData.size} jobs, ${jobTypeConfigsData.size} job types, ${venuesData.size} venues, ${salesSheetItemsData.size} sales items")
                
                // Return all data as a tuple
                Sext(guestsData, volunteersData, jobsData, jobTypeConfigsData, venuesData, salesSheetItemsData)
            }
            
            // Update StateFlows on Main dispatcher to ensure Compose recomposition
            withContext(Dispatchers.Main) {
                println("🔄 Updating StateFlows on Main dispatcher...")
                val oldGuestCount = _guests.value.size
                val oldVolunteerCount = _volunteers.value.size
                
                // CRITICAL FIX: Force StateFlow emission by creating new list instances
                // StateFlow only emits if value changes, so we ensure new references
                _guests.value = removeDuplicateGuests(guests).toList()
                _volunteers.value = removeDuplicateVolunteers(volunteers).toList()
                _jobs.value = removeDuplicateJobs(jobs).toList()
                _jobTypeConfigs.value = removeDuplicateJobTypes(jobTypeConfigs).toList()
                _venues.value = removeDuplicateVenues(venues).toList()
                _salesSheetItems.value = salesSheetItems.sortedBy { it.name }
                
                println("✅ StateFlows updated - Guests: $oldGuestCount → ${_guests.value.size}, Volunteers: $oldVolunteerCount → ${_volunteers.value.size}, Jobs: ${_jobs.value.size}, Job Types: ${_jobTypeConfigs.value.size}, Venues: ${_venues.value.size}, Sales Items: ${_salesSheetItems.value.size}")
                println("✅ UI should now recompose with new data")
            }
        } catch (e: Exception) {
            println("❌ Failed to refresh data: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Helper classes for returning values from suspend function
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    private data class Sext<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)
    
    /**
     * Updates volunteer activity based on current jobs
     */
    @Suppress("unused")
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
            }
            // If no jobs are loaded yet, activity will be calculated when jobs are available
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
                if (syncCoordinator != null) {
                    syncCoordinator?.performManualSync()
                }
                
            } catch (e: Exception) {
                println("Failed to cleanup duplicates: ${e.message}")
            }
        }
    }
    
    /**
     * DUPLICATE PREVENTION: Remove duplicate entries based on unique identifiers
     * This ensures the UI only shows unique items
     */
    
    
    
    /**
     * Same logical guest row for merge/sync.
     *
     * IMPORTANT: Google Sheets row numbers are not stable because full-tab uploads
     * clear and rewrite rows. Never match guests by [sheetsId] first, otherwise two
     * rows can "swap identities" across sync cycles and appear/disappear in UI.
     *
     * Identity priority:
     * 1) Guest NanoID (stable across devices/syncs)
     * 2) Volunteer-benefit volunteerId (for benefit rows)
     */
    private fun guestSameSyncIdentity(local: Guest, remote: Guest): Boolean {
        if (NanoIdGenerator.isValidNanoId(remote.nanoId) &&
            NanoIdGenerator.isValidNanoId(local.nanoId) &&
            local.nanoId == remote.nanoId
        ) {
            return true
        }
        if (local.isVolunteerBenefit && remote.isVolunteerBenefit &&
            local.volunteerId != null && local.volunteerId == remote.volunteerId
        ) {
            return true
        }
        return false
    }

    /**
     * Remove in-memory duplicate guest rows: collapse by guest [nanoId] when valid;
     * otherwise fall back to DB row id so rows are not merged by name/content alone.
     */
    private fun removeDuplicateGuests(guests: List<Guest>): List<Guest> {
        fun dedupeKey(g: Guest): String =
            if (NanoIdGenerator.isValidNanoId(g.nanoId)) g.nanoId else "row:${g.id}"
        val byKey = guests.groupBy { dedupeKey(it) }
        return byKey.values.map { group ->
            // Keep the newest entry for each identity to avoid reverting recent changes.
            group.maxByOrNull { it.lastModified } ?: group.first()
        }
    }
    
    /**
     * Remove duplicate volunteers based on the following rules:
     * 1. If NanoID is the same → keep the OLDER one (lower lastModified)
     * 2. If NanoID is different BUT all info is exactly the same → keep the OLDER one
     * 3. Everything else should NOT be filtered
     */
    private fun removeDuplicateVolunteers(volunteers: List<Volunteer>): List<Volunteer> {
        // Group by NanoID first to handle same-ID duplicates
        val byNanoId = volunteers.groupBy { it.id }
        
        // For each NanoID group, keep only the oldest (lowest lastModified)
        val uniqueByNanoId = byNanoId.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
        
        // Now check for content duplicates (different NanoID but same info)
        // Content key = all identifying fields except NanoID and timestamps
        fun contentKey(v: Volunteer) = "${v.name}_${v.lastNameAbbreviation}_${v.email}_${v.phoneNumber}_${v.dateOfBirth}_${v.gender}_${v.currentRank}_${v.isActive}_${v.nfcCardUid}"
        
        val byContent = uniqueByNanoId.groupBy { contentKey(it) }
        
        // For each content group, keep only the oldest (lowest lastModified)
        return byContent.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
    }
    
    /**
     * Remove duplicate jobs based on the following rules:
     * 1. If ID is the same → keep the OLDER one (lower lastModified)
     * 2. If ID is different BUT all info is exactly the same → keep the OLDER one
     * 3. Everything else should NOT be filtered
     */
    private fun removeDuplicateJobs(jobs: List<Job>): List<Job> {
        // Only remove true duplicates: same database row appearing more than
        // once (identical primary key). A volunteer can legitimately have
        // multiple shifts with the same type/venue/date, so content-based
        // dedup must NOT be used -- it would silently drop valid data.
        //
        // Fast path: when no duplicates exist (common case) return the
        // original list to avoid an unnecessary allocation.
        val seen = HashSet<Long>(jobs.size)
        var hasDuplicates = false
        for (job in jobs) {
            if (!seen.add(job.id)) { hasDuplicates = true; break }
        }
        return if (hasDuplicates) {
            seen.clear()
            jobs.filter { seen.add(it.id) }
        } else {
            jobs
        }
    }
    
    /**
     * Remove duplicate job types based on the following rules:
     * 1. If ID is the same → keep the OLDER one (lower lastModified)
     * 2. If ID is different BUT all info is exactly the same → keep the OLDER one
     * 3. Everything else should NOT be filtered
     */
    private fun removeDuplicateJobTypes(jobTypes: List<JobTypeConfig>): List<JobTypeConfig> {
        // Group by ID first to handle same-ID duplicates
        val byId = jobTypes.groupBy { it.id }
        
        // For each ID group, keep only the oldest (lowest lastModified)
        val uniqueById = byId.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
        
        // Now check for content duplicates (different ID but same info)
        fun contentKey(j: JobTypeConfig) = "${j.name}_${j.isActive}_${j.isShiftJob}_${j.isOrionJob}_${j.requiresShiftTime}_${j.description}"
        
        val byContent = uniqueById.groupBy { contentKey(it) }
        
        // For each content group, keep only the oldest (lowest lastModified)
        return byContent.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
    }

    /**
     * Remove duplicate venues based on the following rules:
     * 1. If ID is the same → keep the OLDER one (lower lastModified)
     * 2. If ID is different BUT all info is exactly the same → keep the OLDER one
     * 3. Everything else should NOT be filtered
     */
    private fun removeDuplicateVenues(venues: List<VenueEntity>): List<VenueEntity> {
        // Group by ID first to handle same-ID duplicates
        val byId = venues.groupBy { it.id }
        
        // For each ID group, keep only the oldest (lowest lastModified)
        val uniqueById = byId.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
        
        // Now check for content duplicates (different ID but same info)
        fun contentKey(v: VenueEntity) = "${v.name}_${v.description}_${v.isActive}"
        
        val byContent = uniqueById.groupBy { contentKey(it) }
        
        // For each content group, keep only the oldest (lowest lastModified)
        return byContent.values.map { group ->
            group.minByOrNull { it.lastModified } ?: group.first()
        }
    }

    // Get volunteer benefits with time-based calculation
    @Suppress("unused")
    suspend fun getVolunteerBenefitStatus(volunteer: Volunteer): VolunteerBenefitStatus? {
        val offsetHours = platformContext?.let { SettingsManager(it).getDateChangeOffsetHours() } ?: 0
        return repository.getVolunteerBenefitStatus(volunteer.id, offsetHours)
    }
    

    // Check if Google Sheets is configured
    private fun isGoogleSheetsConfigured(): Boolean {
        return platformContext?.let { ctx ->
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
                    _syncStatusMessage.value = "Google Sheets is configured and ready for sync"
                } else {
                    println("Google Sheets is not configured")
                    _syncStatusMessage.value = "Google Sheets is not configured. Please check your settings."
                }
                _showSyncStatusDialog.value = true
            } catch (e: Exception) {
                println("Error testing sync status: ${e.message}")
                _syncStatusMessage.value = "Error testing sync status: ${e.message}"
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
                
                // OPTIMIZED: Create a map for O(1) lookup instead of O(n) find for each volunteer
                val volunteersById = volunteers.associateBy { it.id }
                
                // Update volunteers whose activity has changed
                var updatedCount = 0
                updatedVolunteers.forEach { updatedVolunteer ->
                    val originalVolunteer = volunteersById[updatedVolunteer.id]
                    if (originalVolunteer != null && 
                        (updatedVolunteer.lastShiftDate != originalVolunteer.lastShiftDate || 
                         updatedVolunteer.isActive != originalVolunteer.isActive)) {
                        repository.updateVolunteer(updatedVolunteer)
                        syncCoordinator?.afterVolunteerSaved(updatedVolunteer)
                        updatedCount++
                        println("Updated volunteer activity: ${updatedVolunteer.name} - last shift: ${updatedVolunteer.lastShiftDate}, active: ${updatedVolunteer.isActive}")
                    }
                }
                
                if (updatedCount > 0) {
                    println("Updated activity for $updatedCount volunteers")
                } else {
                    println("No volunteer activity changes needed")
                }
            } catch (e: Exception) {
                println("Failed to update volunteer activity from jobs: ${e.message}")
            }
        }
    }
    
    /**
     * Cleans up inactive volunteers based on customizable inactivity threshold.
     * 
     * For volunteers who have worked: uses days since last shift.
     * For volunteers who never worked: uses days since last profile modification.
     * 
     * Deletes volunteers and all their associated jobs/shifts, following the same
     * deletion mechanism as deleteVolunteer() and deleteJob() for Google Sheets sync.
     * 
     * @param yearsInactive Number of years of inactivity required for deletion (default: 4)
     */
    fun cleanupInactiveVolunteers(yearsInactive: Int = 4) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("cleaning up volunteers")) return@launch
                // Use StateFlow volunteers to match what the dialog preview shows
                // This ensures consistency between preview and actual cleanup
                val volunteers = _volunteers.value
                val jobs = repository.getAllJobs().first()
                
                println("DEBUG: Total volunteers from StateFlow: ${volunteers.size}")
                
                // Find volunteers that have been inactive for the specified number of years
                // Use the exact same calculation as the dialog preview
                val volunteersToCleanup = volunteers.filter { volunteer ->
                    val daysSinceLastActivity = VolunteerActivityManager.getDaysSinceLastActivity(volunteer)
                    val shouldDelete = daysSinceLastActivity != null && daysSinceLastActivity >= (yearsInactive * 365L)
                    
                    // Debug logging for each volunteer
                    if (daysSinceLastActivity != null) {
                        println("DEBUG: Volunteer ${volunteer.name} (ID: ${volunteer.id}): daysSinceLastActivity=$daysSinceLastActivity, threshold=${yearsInactive * 365L}, shouldDelete=$shouldDelete, lastShiftDate=${volunteer.lastShiftDate}, lastModified=${volunteer.lastModified}")
                    } else {
                        println("DEBUG: Volunteer ${volunteer.name} (ID: ${volunteer.id}): daysSinceLastActivity=null, lastShiftDate=${volunteer.lastShiftDate}, lastModified=${volunteer.lastModified}")
                    }
                    
                    shouldDelete
                }
                
                println("Found ${volunteersToCleanup.size} volunteers to cleanup (inactive for $yearsInactive+ years)")
                
                if (volunteersToCleanup.isEmpty()) {
                    _syncError.value = "No volunteers found that have been inactive for $yearsInactive+ years"
                    return@launch
                }
                
                // Optimize job lookup: create a map of volunteerId -> jobs for O(1) access instead of O(n) filtering
                val jobsByVolunteerId = jobs.groupBy { it.volunteerId }
                
                var volunteersDeleted = 0
                var jobsDeleted = 0
                val failedVolunteers = mutableListOf<String>()
                val failedJobs = mutableListOf<String>()
                val deletedJobs = mutableListOf<Job>()
                val deletedVolunteers = mutableListOf<Volunteer>()
                
                // Process each volunteer: delete all their jobs first, then delete the volunteer
                // Use proper coroutine handling to ensure deletions are awaited
                for (volunteer in volunteersToCleanup) {
                    try {
                        // Get all jobs/shifts associated with this volunteer
                        val volunteerJobs = jobsByVolunteerId[volunteer.id] ?: emptyList()
                        
                        // Delete all jobs/shifts for this volunteer
                        // Follows the same deletion pattern as deleteJob() for consistency
                        for (job in volunteerJobs) {
                            try {
                                // Track deletion first (same as deleteJob)
                                deletionTracker?.trackJobDeletion(job.id.toString(), job.sheetsId, businessKey = "${job.volunteerId}_${job.jobTypeName}_${job.date}_${job.venueName}_${job.shiftTime}")
                                
                                // Delete from local database
                                repository.deleteJob(job)
                                deletedJobs.add(job)
                                jobsDeleted++
                                
                                println("Deleted job: ${job.jobTypeName} (ID: ${job.id}) for volunteer ${volunteer.name}")
                            } catch (e: Exception) {
                                val errorMsg = "Failed to delete job ${job.id} (${job.jobTypeName}) for volunteer ${volunteer.name}: ${e.message}"
                                println(errorMsg)
                                failedJobs.add(errorMsg)
                            }
                        }
                        
                        // Delete the volunteer after all their jobs are deleted
                        // Follows the same deletion pattern as deleteVolunteer() for consistency
                        deletionTracker?.trackVolunteerDeletion(volunteer.id, volunteer.sheetsId)
                        repository.deleteVolunteer(volunteer)
                        deletedVolunteers.add(volunteer)
                        volunteersDeleted++
                        
                        println("Successfully cleaned up inactive volunteer: ${volunteer.name} (ID: ${volunteer.id}) and ${volunteerJobs.size} associated job(s)")
                    } catch (e: Exception) {
                        val errorMsg = "Failed to cleanup volunteer ${volunteer.name} (ID: ${volunteer.id}): ${e.message}"
                        println(errorMsg)
                        failedVolunteers.add(errorMsg)
                    }
                }
                
                if (volunteersDeleted > 0 || jobsDeleted > 0) {
                    // Small delay to ensure database commits are complete before syncing
                    delay(100)
                    
                    try {
                        if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                            deletedJobs.forEach { syncCoordinator?.afterJobDeleted(it) }
                            deletedVolunteers.forEach { syncCoordinator?.afterVolunteerDeleted(it, deleteShifts = true) }
                        } else {
                            twoWaySyncService?.backupVolunteersToSheets()
                            twoWaySyncService?.backupJobsToSheets()
                            println("Successfully synced deletions to Google Sheets")
                        }
                    } catch (e: Exception) {
                        println("Warning: Failed to sync cleanup deletions: ${e.message}")
                        // Continue even if sync fails - local deletion was successful
                    }
                    
                    // Refresh data to reflect changes (after sync)
                    refreshAllData()
                    
                    // Recalculate volunteer guest list (same as deleteVolunteer)
                    recalcAndUploadVolunteerGuestList()
                    
                    // Build success message
                    val volunteerText = "$volunteersDeleted volunteer${if (volunteersDeleted != 1) "s" else ""}"
                    val jobText = if (jobsDeleted > 0) " and $jobsDeleted job${if (jobsDeleted != 1) "s" else ""}" else ""
                    val warningText = if (failedVolunteers.isNotEmpty() || failedJobs.isNotEmpty()) {
                        ". Some deletions failed - check logs for details."
                    } else {
                        ""
                    }
                    
                    _syncError.value = "Cleaned up $volunteerText$jobText (inactive for $yearsInactive+ years)$warningText"
                } else {
                    _syncError.value = "No volunteers or jobs were deleted. Check logs for errors."
                }
            } catch (e: Exception) {
                println("Failed to cleanup inactive volunteers: ${e.message}")
                e.printStackTrace()
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
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                _isSyncing.value = true
                _syncError.value = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        syncCoordinator?.performManualSync()
                    }
                    withContext(Dispatchers.Main) { refreshAllData() }
                    refreshFirebaseSyncStatus()
                    if (result?.isError == true) {
                        val errorMsg = (result as SyncResult.Error).message
                        _syncError.value = errorMsg
                        showSyncErrorIfNotSuppressed(errorMsg)
                    }
                } catch (e: Exception) {
                    val errorMsg = "Firebase sync failed: ${e.message}"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                } finally {
                    _isSyncing.value = false
                }
                return@launch
            }
            _isSyncing.value = true
            _syncError.value = null
            
            AppLogger.i("EventManagerViewModel", "Starting differential full sync")
            
            try {
                println("🔄 Starting differential full sync...")
                
                // Perform sync on IO dispatcher
                val result = withContext(Dispatchers.IO) {
                    syncManager?.performDifferentialSync()
                }
                
                if (result is DifferentialSyncResult.Success) {
                    val changes = result.changes
                    val summary = changes.summary()
                    AppLogger.i("EventManagerViewModel", "Differential sync changes: $summary")
                    println("📊 $summary")
                    
                    // CRITICAL: Apply UI updates on Main dispatcher
                    withContext(Dispatchers.Main) {
                        applyDifferentialUIUpdates(changes)
                        println("✅ UI updates applied on Main dispatcher")
                    }

                    checkForNewAnnouncements(_venues.value)
                    
                    // Always recalculate volunteer benefits to ensure they're present
                    // This handles: new volunteers, rank changes from jobs, initial sync, etc.
                    // Uses differential updates internally so it won't cause full refresh
                    recalcAndUploadVolunteerGuestList()

                    if (settingsManagerCached?.getBackendType() != BackendType.FIREBASE) {
                        refreshTemporaryGuestsFromSheets()
                    }
                    
                    // Update sync time
                    updateSyncTime()
                    
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
            
            // Apply guest changes (excludes volunteer benefits - they're handled separately)
            if (changes.guests.hasChanges) {
                // Helper to get matching key for a guest (same as DifferentialSyncService)
                fun guestKey(g: Guest) = if (NanoIdGenerator.isValidNanoId(g.nanoId)) g.nanoId else "${g.name}_${g.email}_${g.phoneNumber}_${g.venueName}_${g.invitations}"
                
                // Remove deleted guests by matching key (not by id, since synced items may have id=0)
                changes.guests.deleted.forEach { deletedGuest ->
                    val deleteKey = guestKey(deletedGuest)
                    currentGuests.removeAll { guestKey(it) == deleteKey }
                }
                
                // Add new guests
                currentGuests.addAll(changes.guests.new)
                
                // Update modified guests by matching key
                changes.guests.modified.forEach { modifiedGuest ->
                    val modifyKey = guestKey(modifiedGuest)
                    val index = currentGuests.indexOfFirst { guestKey(it) == modifyKey }
                    if (index >= 0) {
                        // Preserve the local ID when updating
                        currentGuests[index] = modifiedGuest.copy(id = currentGuests[index].id)
                    }
                }
                
                _guests.value = removeDuplicateGuests(currentGuests)
                println("✅ Applied ${changes.guests.totalChanges} guest changes to UI")
            }
            
            // Apply volunteer changes
            if (changes.volunteers.hasChanges) {
                // Helper to get matching key (same as DifferentialSyncService)
                fun volunteerKey(v: Volunteer) = v.id
                
                // Remove deleted volunteers by matching key
                changes.volunteers.deleted.forEach { deletedVolunteer ->
                    val deleteKey = volunteerKey(deletedVolunteer)
                    currentVolunteers.removeAll { volunteerKey(it) == deleteKey }
                }
                
                // Add new volunteers
                currentVolunteers.addAll(changes.volunteers.new)
                
                // Update modified volunteers by matching key
                // Google Sheets is source of truth for NanoIDs - use the ID from sheets
                changes.volunteers.modified.forEach { modifiedVolunteer ->
                    val modifyKey = volunteerKey(modifiedVolunteer)
                    val index = currentVolunteers.indexOfFirst { volunteerKey(it) == modifyKey }
                    if (index >= 0) {
                        // Use the NanoID from Google Sheets (modifiedVolunteer.id)
                        currentVolunteers[index] = modifiedVolunteer
                    }
                }
                
                _volunteers.value = removeDuplicateVolunteers(currentVolunteers)
                println("✅ Applied ${changes.volunteers.totalChanges} volunteer changes to UI")
            }
            
            // Apply job changes
            if (changes.jobs.hasChanges) {
                // Helper to get matching key (same as DifferentialSyncService)
                fun jobKey(j: Job) = "${j.volunteerId}_${j.jobTypeName}_${j.date}_${j.venueName}_${j.shiftTime}"
                
                // Remove deleted jobs by matching key
                changes.jobs.deleted.forEach { deletedJob ->
                    val deleteKey = jobKey(deletedJob)
                    currentJobs.removeAll { jobKey(it) == deleteKey }
                }
                
                // Add new jobs
                currentJobs.addAll(changes.jobs.new)
                
                // Update modified jobs by matching key
                changes.jobs.modified.forEach { modifiedJob ->
                    val modifyKey = jobKey(modifiedJob)
                    val index = currentJobs.indexOfFirst { jobKey(it) == modifyKey }
                    if (index >= 0) {
                        currentJobs[index] = modifiedJob.copy(id = currentJobs[index].id)
                    }
                }
                
                _jobs.value = removeDuplicateJobs(currentJobs)
                println("✅ Applied ${changes.jobs.totalChanges} job changes to UI")
            }
            
            // Apply job type config changes
            if (changes.jobTypeConfigs.hasChanges) {
                // Helper to get matching key (same as DifferentialSyncService)
                fun jobTypeKey(c: JobTypeConfig) = c.name
                
                // Remove deleted job type configs by matching key
                changes.jobTypeConfigs.deleted.forEach { deletedConfig ->
                    val deleteKey = jobTypeKey(deletedConfig)
                    currentJobTypes.removeAll { jobTypeKey(it) == deleteKey }
                }
                
                // Add new job type configs
                currentJobTypes.addAll(changes.jobTypeConfigs.new)
                
                // Update modified job type configs by matching key
                changes.jobTypeConfigs.modified.forEach { modifiedConfig ->
                    val modifyKey = jobTypeKey(modifiedConfig)
                    val index = currentJobTypes.indexOfFirst { jobTypeKey(it) == modifyKey }
                    if (index >= 0) {
                        currentJobTypes[index] = modifiedConfig.copy(id = currentJobTypes[index].id)
                    }
                }
                
                _jobTypeConfigs.value = removeDuplicateJobTypes(currentJobTypes)
                println("✅ Applied ${changes.jobTypeConfigs.totalChanges} job type changes to UI")
            }
            
            // Apply venue changes
            if (changes.venues.hasChanges) {
                // Helper to get matching key (same as DifferentialSyncService)
                fun venueKey(v: VenueEntity) = v.name
                
                // Remove deleted venues by matching key
                changes.venues.deleted.forEach { deletedVenue ->
                    val deleteKey = venueKey(deletedVenue)
                    currentVenues.removeAll { venueKey(it) == deleteKey }
                }
                
                // Add new venues
                currentVenues.addAll(changes.venues.new)
                
                // Update modified venues by matching key
                changes.venues.modified.forEach { modifiedVenue ->
                    val modifyKey = venueKey(modifiedVenue)
                    val index = currentVenues.indexOfFirst { venueKey(it) == modifyKey }
                    if (index >= 0) {
                        currentVenues[index] = modifiedVenue.copy(id = currentVenues[index].id)
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
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
            
            // Helper to get matching key (same as DifferentialSyncService)
            fun volunteerKey(v: Volunteer) = v.id
            
            // Remove deleted volunteers by matching key
            changes.deleted.forEach { deletedVolunteer ->
                val deleteKey = volunteerKey(deletedVolunteer)
                currentVolunteers.removeAll { volunteerKey(it) == deleteKey }
                println("🗑️ Removed deleted volunteer: ${deletedVolunteer.name}")
            }
            
            // Add new volunteers
            changes.new.forEach { newVolunteer ->
                currentVolunteers.add(newVolunteer)
                println("➕ Added new volunteer: ${newVolunteer.name}")
            }
            
            // Update modified volunteers by matching key
            // Google Sheets is source of truth for NanoIDs - use the ID from sheets
            changes.modified.forEach { modifiedVolunteer ->
                val modifyKey = volunteerKey(modifiedVolunteer)
                val index = currentVolunteers.indexOfFirst { volunteerKey(it) == modifyKey }
                if (index >= 0) {
                    // Use the NanoID from Google Sheets (modifiedVolunteer.id)
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
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
            
            // Helper to get matching key (same as DifferentialSyncService)
            fun guestKey(g: Guest) = if (NanoIdGenerator.isValidNanoId(g.nanoId)) g.nanoId else "${g.name}_${g.email}_${g.phoneNumber}_${g.venueName}_${g.invitations}"
            
            // Remove deleted guests by matching key
            changes.deleted.forEach { deletedGuest ->
                val deleteKey = guestKey(deletedGuest)
                currentGuests.removeAll { guestKey(it) == deleteKey }
                println("🗑️ Removed deleted guest: ${deletedGuest.name}")
            }
            
            // Add new guests
            changes.new.forEach { newGuest ->
                currentGuests.add(newGuest)
                println("➕ Added new guest: ${newGuest.name}")
            }
            
            // Update modified guests by matching key
            changes.modified.forEach { modifiedGuest ->
                val modifyKey = guestKey(modifiedGuest)
                val index = currentGuests.indexOfFirst { guestKey(it) == modifyKey }
                if (index >= 0) {
                    currentGuests[index] = modifiedGuest.copy(id = currentGuests[index].id)
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
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.performManualSync()
                return@launch
            }
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
                        evaluatePendingShiftCreditsIfNeeded()
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
            
            // Helper to get matching key (same as DifferentialSyncService)
            fun jobKey(j: Job) = "${j.volunteerId}_${j.jobTypeName}_${j.date}_${j.venueName}_${j.shiftTime}"
            
            // Remove deleted jobs by matching key
            changes.deleted.forEach { deletedJob ->
                val deleteKey = jobKey(deletedJob)
                currentJobs.removeAll { jobKey(it) == deleteKey }
                println("🗑️ Removed deleted job: ${deletedJob.jobTypeName}")
            }
            
            // Add new jobs
            changes.new.forEach { newJob ->
                currentJobs.add(newJob)
                println("➕ Added new job: ${newJob.jobTypeName}")
            }
            
            // Update modified jobs by matching key
            changes.modified.forEach { modifiedJob ->
                val modifyKey = jobKey(modifiedJob)
                val index = currentJobs.indexOfFirst { jobKey(it) == modifyKey }
                if (index >= 0) {
                    currentJobs[index] = modifiedJob.copy(id = currentJobs[index].id)
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
            
            // Helper to get matching key (same as DifferentialSyncService)
            fun jobTypeKey(c: JobTypeConfig) = c.name
            
            // Remove deleted job types by matching key
            changes.deleted.forEach { deletedJobType ->
                val deleteKey = jobTypeKey(deletedJobType)
                currentJobTypes.removeAll { jobTypeKey(it) == deleteKey }
                println("🗑️ Removed deleted job type: ${deletedJobType.name}")
            }
            
            // Add new job types
            changes.new.forEach { newJobType ->
                currentJobTypes.add(newJobType)
                println("➕ Added new job type: ${newJobType.name}")
            }
            
            // Update modified job types by matching key
            changes.modified.forEach { modifiedJobType ->
                val modifyKey = jobTypeKey(modifiedJobType)
                val index = currentJobTypes.indexOfFirst { jobTypeKey(it) == modifyKey }
                if (index >= 0) {
                    currentJobTypes[index] = modifiedJobType.copy(id = currentJobTypes[index].id)
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
                    withContext(Dispatchers.Main) {
                        applyVenueUIUpdates(changes)
                        reconcilePeopleCounterAfterVenuesChangedInternal()
                        checkForNewAnnouncements(_venues.value)
                    }
                    
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

    fun applyManualAccountAdjustment(
        holderType: AccountHolderType,
        holderId: String,
        holderName: String,
        amount: Double,
        note: String
    ) {
        viewModelScope.launch {
            try {
                if (rejectIfCrudSoftLocked("adjusting accounts")) return@launch
                val transfer = accountCreditService.applyManualAdjustment(
                    holderType, holderId, holderName, amount, note
                )
                if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                    when (val ledger = syncCoordinator?.commitTransfer(transfer)) {
                        is FirebaseLedgerResult.Rejected -> {
                            _syncError.value = ledger.reason
                        }
                        else -> Unit
                    }
                } else {
                    syncCoordinator?.afterTransfersChanged() ?: twoWaySyncService?.backupTransfersToSheets()
                }
                refreshAccountBalancesFromDb()
            } catch (e: Exception) {
                _syncError.value = "Failed to adjust account: ${e.message}"
            }
        }
    }

    suspend fun completePosSale(
        holderType: AccountHolderType,
        holderId: String,
        holderName: String,
        cart: List<PosCartLine>,
        barDiscountPercent: Int = 0,
        posVenueName: String = PosVenueScope.GLOBAL,
        customerOrgId: String = "",
    ): PosSaleResult {
        if (isCrudSoftLocked()) {
            return PosSaleResult(
                success = false,
                totalAmount = 0.0,
                creditPaid = 0.0,
                cashOrCardDue = 0.0,
                remainingBalance = 0.0,
                message = "Institution backend migration pending — open Admin to reconnect.",
            )
        }
        val settings = settingsManagerCached
        val buffer = settings?.getPurchaseCreditBuffer() ?: 0.0

        // Firebase: transactional ledger path
        if (settings?.getBackendType() == BackendType.FIREBASE) {
            val draft = accountCreditService.completePosSale(
                holderType, holderId, holderName, cart, barDiscountPercent, posVenueName, buffer, customerOrgId
            )
            draft.transfer?.let { transfer ->
                when (val ledger = syncCoordinator?.commitTransfer(transfer, customerOrgId)) {
                    is FirebaseLedgerResult.Rejected -> {
                        _syncError.value = ledger.reason
                        return draft.copy(success = false, message = ledger.reason)
                    }
                    is FirebaseLedgerResult.Pending -> {
                        // Offline / SDK missing — keep local pending
                    }
                    else -> Unit
                }
            }
            refreshAccountBalancesFromDb()
            return draft
        }

        val result = accountCreditService.completePosSale(
            holderType, holderId, holderName, cart, barDiscountPercent, posVenueName, buffer
        )
        refreshAccountBalancesFromDb()
        viewModelScope.launch {
            try {
                syncCoordinator?.afterTransfersChanged()
                    ?: twoWaySyncService?.backupTransfersToSheets()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Transfer backup after POS sale failed: ${e.message}")
            }
        }
        return result
    }

    fun endPosSession() {
        posSessionBootstrapDone = false
    }

    fun bootstrapPosSession() {
        if (posSessionBootstrapDone) return
        posSessionBootstrapDone = true
        viewModelScope.launch {
            syncCoordinator?.bootstrapPosSessionSync() ?: run {
                syncSalesSheetItemsWithTargetedUpdates()
                syncTransfersWithTargetedUpdates()
            }
        }
    }

    fun syncTransfersWithTargetedUpdates() {
        viewModelScope.launch {
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.bootstrapPosSessionSync()
                return@launch
            }
            _isSyncing.value = true
            try {
                if (!isGoogleSheetsConfigured()) return@launch
                googleSheetsService.initializeSheetsService()
                val result = syncManager?.performTransferDifferentialSync()
                if (result is TransferSyncResult.Success) {
                    applyTransferUIUpdates(result.changes)
                    updateSyncTime()
                }
            } catch (e: Exception) {
                println("Transfer sync error: ${e.message}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun applyTransferUIUpdates(changes: DifferentialSyncService.SyncChanges<AccountTransfer>) {
        val current = _accountTransfers.value.toMutableList()
        changes.deleted.forEach { deleted ->
            current.removeAll { it.transferId == deleted.transferId }
        }
        changes.modified.forEach { modified ->
            val idx = current.indexOfFirst { it.transferId == modified.transferId }
            if (idx >= 0) current[idx] = modified else current.add(modified)
        }
        changes.new.forEach { newTransfer ->
            if (current.none { it.transferId == newTransfer.transferId }) {
                current.add(0, newTransfer)
            }
        }
        _accountTransfers.value = current.sortedByDescending { it.createdAt }
        _accountBalances.value = AccountBalanceService.computeAllBalances(_accountTransfers.value)
    }

    fun syncSalesSheetItemsWithTargetedUpdates() {
        viewModelScope.launch {
            if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                syncCoordinator?.bootstrapPosSessionSync()
                return@launch
            }
            _isSyncing.value = true
            _syncError.value = null

            try {
                println("🔄 Starting TARGETED sales sheet item sync with UI updates...")

                if (!isGoogleSheetsConfigured()) {
                    val errorMsg = "Google Sheets not configured. Please check your service account key and spreadsheet settings."
                    println(errorMsg)
                    _syncError.value = errorMsg
                    return@launch
                }

                googleSheetsService.initializeSheetsService()

                val result = syncManager?.performSalesSheetItemDifferentialSync()
                if (result is SalesSheetItemSyncResult.Success) {
                    val changes = result.changes
                    println("📋 Sales sheet item changes: ${changes.new.size} new, ${changes.modified.size} modified, ${changes.deleted.size} deleted")
                    applySalesSheetItemUIUpdates(changes)
                    updateSyncTime()
                    println("✅ Targeted sales sheet item sync completed successfully")
                } else {
                    val errorResult = result as? SalesSheetItemSyncResult.Error
                    val errorMsg = errorResult?.message ?: "Targeted sales sheet item sync failed"
                    _syncError.value = errorMsg
                    showSyncErrorIfNotSuppressed(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("429") == true || e.message?.contains("Rate limit") == true -> "Rate limit exceeded. Please try again later."
                    else -> "Targeted sales sheet item sync failed: ${e.message}"
                }
                _syncError.value = errorMsg
                showSyncErrorIfNotSuppressed(errorMsg)
                println("❌ Targeted sales sheet item sync error: $errorMsg")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun applySalesSheetItemUIUpdates(changes: DifferentialSyncService.SyncChanges<SalesSheetItem>) {
        try {
            val currentItems = _salesSheetItems.value.toMutableList()
            fun key(item: SalesSheetItem) = item.name

            changes.deleted.forEach { deleted ->
                currentItems.removeAll { key(it) == key(deleted) }
            }

            changes.new.forEach { added ->
                currentItems.add(added)
            }

            changes.modified.forEach { modified ->
                val index = currentItems.indexOfFirst { key(it) == key(modified) }
                if (index >= 0) {
                    currentItems[index] = modified.copy(id = currentItems[index].id)
                }
            }

            _salesSheetItems.value = currentItems.sortedBy { it.name }
            println("✅ Applied ${changes.totalChanges} targeted sales sheet item UI updates")
        } catch (e: Exception) {
            println("❌ Failed to apply targeted sales sheet item UI updates: ${e.message}")
            e.printStackTrace()
            refreshSalesSheetItemData()
        }
    }
    
    /**
     * APPLY TARGETED VENUE UI UPDATES
     * Updates only the venues that actually changed
     */
    private suspend fun applyVenueUIUpdates(changes: DifferentialSyncService.SyncChanges<VenueEntity>) {
        try {
            val currentVenues = _venues.value.toMutableList()

            // Helper to get matching key (same as DifferentialSyncService)
            fun venueKey(v: VenueEntity) = v.name

            // Remove deleted venues by matching key
            changes.deleted.forEach { deletedVenue ->
                val deleteKey = venueKey(deletedVenue)
                currentVenues.removeAll { venueKey(it) == deleteKey }
                println("🗑️ Removed deleted venue: ${deletedVenue.name}")
            }

            // Add new venues
            changes.new.forEach { newVenue ->
                currentVenues.add(newVenue)
                println("➕ Added new venue: ${newVenue.name}")
            }

            // Update modified venues by matching key
            changes.modified.forEach { modifiedVenue ->
                val modifyKey = venueKey(modifiedVenue)
                val index = currentVenues.indexOfFirst { venueKey(it) == modifyKey }
                if (index >= 0) {
                    currentVenues[index] = modifiedVenue.copy(id = currentVenues[index].id)
                    println("✏️ Updated venue: ${modifiedVenue.name}")
                }
            }

            withContext(Dispatchers.Main) {
                _venues.value = removeDuplicateVenues(currentVenues)
            }

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

    fun clearPeopleCounterUiHint() {
        _peopleCounterUiHint.value = null
    }

    fun refreshVenuesForPeopleCounterQuietly() {
        viewModelScope.launch {
            try {
                if (settingsManagerCached?.getBackendType() == BackendType.FIREBASE) {
                    syncCoordinator?.performStartupSync()
                    withContext(Dispatchers.Main) { reconcilePeopleCounterAfterVenuesChangedInternal() }
                } else {
                    pullVenuesDifferentialQuiet()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("Quiet venue refresh failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Tap on the counter "last updated" line: with priority for this venue, push local count;
     * without priority, pull remote venue state.
     */
    fun resyncPeopleCounterLastUpdatedLine() {
        val ctx = platformContext ?: return
        viewModelScope.launch {
            try {
                val sm = SettingsManager(ctx)
                val backendFirebase = sm.getBackendType() == BackendType.FIREBASE
                if (!backendFirebase && !isGoogleSheetsConfigured()) return@launch
                val vid = _peopleCounterSelectedVenueId.value
                if (vid <= 0L) return@launch
                if (sm.isPeopleCounterPriority(vid)) {
                    val venue = repository.getVenueById(vid) ?: return@launch
                    val myId = sm.getOrCreatePersistentDeviceId()
                    val now = System.currentTimeMillis()
                    val count = venue.peopleCounterCount
                    peopleCounterUploadMutex.withLock {
                        if (backendFirebase) {
                            syncCoordinator?.updatePeopleCounter(venue, count, venue.firebaseOrgId)
                        } else {
                            val row = venue.sheetsId?.toIntOrNull() ?: return@withLock
                            twoWaySyncService?.updateVenuePeopleCounterOnSheets(row, count, myId, now)
                        }
                        val fresh = repository.getVenueById(vid) ?: return@withLock
                        repository.updateVenue(
                            fresh.copy(peopleCounterWriterDeviceId = myId, peopleCounterLastModified = now)
                        )
                        val t = peopleCounterThrottleByVenue.getOrPut(vid) { PeopleCounterThrottle() }
                        t.lastUploadAtMs = now
                        t.countAtLastUpload = count
                    }
                } else if (backendFirebase) {
                    syncCoordinator?.performStartupSync()
                    withContext(Dispatchers.Main) { reconcilePeopleCounterAfterVenuesChangedInternal() }
                } else {
                    pullVenuesDifferentialQuiet()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("People counter resync failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun setPeopleCounterSelectedVenueId(venueId: Long) {
        val ctx = platformContext ?: return
        val sm = SettingsManager(ctx)
        peopleCounterLastUserSelectedVenueId = venueId
        peopleCounterLastUserSelectionAtMs = System.currentTimeMillis()
        sm.setPeopleCounterSelectedVenueId(venueId)
        _peopleCounterSelectedVenueId.value = venueId
        _peopleCounterPriority.value = sm.isPeopleCounterPriority(venueId)
    }

    fun setPeopleCounterPriority(enabled: Boolean) {
        val ctx = platformContext ?: return
        val venueId = _peopleCounterSelectedVenueId.value
        if (venueId <= 0L) return
        viewModelScope.launch {
            val sm = SettingsManager(ctx)
            if (!enabled) {
                sm.setPeopleCounterPriority(venueId, false)
                _peopleCounterPriority.value = false
                releasePeopleCounterWriterForCurrentVenue()
            } else {
                claimPeopleCounterWriterForCurrentSelection(ctx, forceStealFromOtherDevice = false)
            }
        }
    }

    /**
     * After a 3-second long-press on the Priority switch, claim counter writer on Sheets for this device
     * even if another device currently holds it. The count on Sheets is read first so it is not reset locally.
     */
    fun forceTakePeopleCounterPriority() {
        val ctx = platformContext ?: return
        viewModelScope.launch {
            try {
                claimPeopleCounterWriterForCurrentSelection(ctx, forceStealFromOtherDevice = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("Force take people counter priority failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun claimPeopleCounterWriterForCurrentSelection(
        ctx: PlatformContext,
        forceStealFromOtherDevice: Boolean
    ) {
        if (rejectIfCrudSoftLocked("claiming people counter")) return
        val sm = SettingsManager(ctx)
        val backendFirebase = sm.getBackendType() == BackendType.FIREBASE
        if (!backendFirebase && !isGoogleSheetsConfigured()) {
            _peopleCounterUiHint.value = "Configure Google Sheets first."
            return
        }
        if (!backendFirebase) {
            pullVenuesDifferentialQuiet()
        }
        val vid = _peopleCounterSelectedVenueId.value
        val venue = repository.getVenueById(vid)
        if (venue == null) {
            _peopleCounterUiHint.value = "Select a venue."
            return
        }
        val myId = sm.getOrCreatePersistentDeviceId()
        val w = venue.peopleCounterWriterDeviceId.trim()
        if (!forceStealFromOtherDevice && w.isNotEmpty() && w != myId) {
            _peopleCounterUiHint.value = "Another device controls the counter."
            return
        }
        val now = System.currentTimeMillis()
        val countForSheetAndDb = if (!backendFirebase) {
            val row = venue.sheetsId?.toIntOrNull()
            if (row == null) {
                _peopleCounterUiHint.value = "Venue has no sheet row."
                return
            }
            val count = if (forceStealFromOtherDevice) {
                twoWaySyncService?.readVenuePeopleCounterFromSheet(row)?.first ?: venue.peopleCounterCount
            } else {
                venue.peopleCounterCount
            }
            twoWaySyncService?.updateVenuePeopleCounterOnSheets(row, count, myId, now)
            count
        } else {
            val count = venue.peopleCounterCount
            syncCoordinator?.updatePeopleCounter(
                venue.copy(peopleCounterWriterDeviceId = myId, peopleCounterLastModified = now),
                count,
            )
            count
        }
        repository.updateVenue(
            venue.copy(
                peopleCounterCount = countForSheetAndDb,
                peopleCounterWriterDeviceId = myId,
                peopleCounterLastModified = now
            )
        )
        sm.setPeopleCounterPriority(venue.id, true)
        if (venue.id == _peopleCounterSelectedVenueId.value) {
            _peopleCounterPriority.value = true
        }
        _peopleCounterUiHint.value = null
        val t = peopleCounterThrottleByVenue.getOrPut(venue.id) { PeopleCounterThrottle() }
        t.lastUploadAtMs = now
        t.countAtLastUpload = countForSheetAndDb
    }

    fun reconcilePeopleCounterAfterVenuesChanged() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    reconcilePeopleCounterAfterVenuesChangedInternal()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                println("People counter reconcile failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun reconcilePeopleCounterAfterVenuesChangedInternal() {
        val ctx = platformContext ?: return
        val sm = SettingsManager(ctx)
        val active = _venues.value.filter { it.isActive }.sortedBy { it.name }
        if (active.isEmpty()) {
            // During sync, venues can be transiently empty; keep current selection instead of
            // snapping to 0 (which later falls back to the first venue unexpectedly).
            return
        }
        var sel = _peopleCounterSelectedVenueId.value
        if (sel == 0L || active.none { it.id == sel }) {
            val now = System.currentTimeMillis()
            val keepRecentUserSelection =
                sel == peopleCounterLastUserSelectedVenueId &&
                    now - peopleCounterLastUserSelectionAtMs <= peopleCounterUserSelectionGraceMs
            if (keepRecentUserSelection) {
                return
            }
            sel = active.first().id
            _peopleCounterSelectedVenueId.value = sel
            sm.setPeopleCounterSelectedVenueId(sel)
        }
        val current = active.find { it.id == sel } ?: _venues.value.find { it.id == sel }
        enforcePeopleCounterWriterArbitration(current)
    }

    private fun enforcePeopleCounterWriterArbitration(venue: VenueEntity?) {
        val ctx = platformContext ?: return
        val v = venue ?: return
        val sm = SettingsManager(ctx)
        val myId = sm.getOrCreatePersistentDeviceId()
        val w = v.peopleCounterWriterDeviceId.trim()
        if (sm.isPeopleCounterPriority(v.id) && w.isNotEmpty() && w != myId) {
            sm.setPeopleCounterPriority(v.id, false)
            if (v.id == _peopleCounterSelectedVenueId.value) {
                _peopleCounterPriority.value = false
            }
            _peopleCounterUiHint.value = "Priority lost — another device is writing."
        }
    }

    private suspend fun pullVenuesDifferentialQuiet() {
        if (!isGoogleSheetsConfigured()) return
        if (!peopleCounterQuietRefreshMutex.tryLock()) {
            println("Quiet venue refresh already in progress, skipping")
            return
        }
        try {
            googleSheetsService.initializeSheetsService()
            val result = syncManager?.performVenueDifferentialSync()
            withContext(Dispatchers.Main) {
                if (result is VenueSyncResult.Success) {
                    applyVenueUIUpdates(result.changes)
                }
                reconcilePeopleCounterAfterVenuesChangedInternal()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("Quiet venue pull failed: ${e.message}")
            e.printStackTrace()
        } finally {
            peopleCounterQuietRefreshMutex.unlock()
        }
    }

    private suspend fun releasePeopleCounterWriterForCurrentVenue() {
        val ctx = platformContext ?: return
        val sm = SettingsManager(ctx)
        val myId = sm.getOrCreatePersistentDeviceId()
        val vid = _peopleCounterSelectedVenueId.value
        val venue = repository.getVenueById(vid) ?: return
        if (venue.peopleCounterWriterDeviceId.trim() != myId) return
        val now = System.currentTimeMillis()
        if (sm.getBackendType() == BackendType.FIREBASE) {
            syncCoordinator?.updatePeopleCounter(
                venue.copy(peopleCounterWriterDeviceId = "", peopleCounterLastModified = now),
                venue.peopleCounterCount,
            )
        } else {
            val row = venue.sheetsId?.toIntOrNull() ?: return
            twoWaySyncService?.updateVenuePeopleCounterOnSheets(
                row,
                venue.peopleCounterCount,
                "",
                now,
            )
        }
        repository.updateVenue(
            venue.copy(peopleCounterWriterDeviceId = "", peopleCounterLastModified = now)
        )
    }

    fun adjustPeopleCounterCount(venueId: Long, delta: Int) {
        viewModelScope.launch {
            if (rejectIfCrudSoftLocked("editing people counter")) return@launch
            if (!canEditPeopleCounter(venueId)) return@launch
            val venue = repository.getVenueById(venueId) ?: return@launch
            val before = venue.peopleCounterCount
            val next = (before + delta).coerceAtLeast(0)
            if (next == before) return@launch
            repository.updateVenue(venue.copy(peopleCounterCount = next))
            maybeUploadVenueCounterAfterLocalEdit(venueId, next)
        }
    }

    fun resetPeopleCounterForVenue(venueId: Long) {
        viewModelScope.launch {
            if (rejectIfCrudSoftLocked("editing people counter")) return@launch
            if (!canEditPeopleCounter(venueId)) return@launch
            val venue = repository.getVenueById(venueId) ?: return@launch
            val prev = venue.peopleCounterCount
            if (prev == 0) return@launch
            repository.updateVenue(venue.copy(peopleCounterCount = 0))
            maybeUploadVenueCounterAfterLocalEdit(venueId, 0)
        }
    }

    /**
     * When this device has people-counter priority for a venue and is the writer (or not yet assigned),
     * sheet pulls must not overwrite an in-progress local count with stale E–G from Sheets.
     */
    private fun shouldKeepLocalPeopleCounterWhenPullingFromSheet(
        sm: SettingsManager,
        myId: String,
        localVenue: VenueEntity
    ): Boolean {
        if (!sm.isPeopleCounterPriority(localVenue.id)) return false
        val w = localVenue.peopleCounterWriterDeviceId.trim()
        return w.isEmpty() || w == myId
    }

    private suspend fun canEditPeopleCounter(venueId: Long): Boolean {
        val ctx = platformContext ?: return false
        val sm = SettingsManager(ctx)
        if (!sm.isPeopleCounterPriority(venueId)) return false
        val venue = repository.getVenueById(venueId) ?: return false
        val w = venue.peopleCounterWriterDeviceId.trim()
        val myId = sm.getOrCreatePersistentDeviceId()
        return w.isEmpty() || w == myId
    }

    private suspend fun maybeUploadVenueCounterAfterLocalEdit(venueId: Long, @Suppress("UNUSED_PARAMETER") newCount: Int) {
        val ctx = platformContext ?: return
        val sm = SettingsManager(ctx)
        if (!sm.isPeopleCounterPriority(venueId)) return
        val venue = repository.getVenueById(venueId) ?: return
        val w = venue.peopleCounterWriterDeviceId.trim()
        val myId = sm.getOrCreatePersistentDeviceId()
        if (w.isEmpty() || w != myId) return
        peopleCounterUploadMutex.withLock {
            val fresh = repository.getVenueById(venueId) ?: return@withLock
            val count = fresh.peopleCounterCount
            val t = peopleCounterThrottleByVenue.getOrPut(venueId) { PeopleCounterThrottle() }
            val now = System.currentTimeMillis()
            val backendFirebase = sm.getBackendType() == BackendType.FIREBASE
            if (!backendFirebase) {
                val lastUp = t.countAtLastUpload
                val hitTenBoundary = count % 10 == 0 && count != lastUp
                val idleOneMinuteSinceLastUpload =
                    t.lastUploadAtMs > 0L &&
                        now - t.lastUploadAtMs >= 60_000L &&
                        count != lastUp
                if (!hitTenBoundary && !idleOneMinuteSinceLastUpload) return@withLock
            }

            if (backendFirebase) {
                syncCoordinator?.updatePeopleCounter(
                    fresh.copy(peopleCounterWriterDeviceId = myId, peopleCounterLastModified = now),
                    count,
                    fresh.firebaseOrgId,
                )
            } else {
                val row = fresh.sheetsId?.toIntOrNull() ?: return@withLock
                twoWaySyncService?.updateVenuePeopleCounterOnSheets(row, count, myId, now)
            }
            repository.updateVenue(
                fresh.copy(peopleCounterWriterDeviceId = myId, peopleCounterLastModified = now)
            )
            t.lastUploadAtMs = now
            t.countAtLastUpload = count
        }
    }

    // ===================== ANNOUNCEMENTS =====================

    fun openSendAnnouncementDialog() {
        _showSendAnnouncementDialog.value = true
    }

    fun closeSendAnnouncementDialog() {
        _showSendAnnouncementDialog.value = false
    }

    fun sendAnnouncement(targetVenueIds: List<Long>, title: String, message: String) {
        val ctx = platformContext ?: return
        val settingsManager = SettingsManager(ctx)
        val deviceId = settingsManager.getOrCreatePersistentDeviceId()
        val sentAt = System.currentTimeMillis()

        viewModelScope.launch {
            if (rejectIfCrudSoftLocked("sending announcements")) return@launch
            _isAnnouncementSending.value = true
            try {
                val allVenues = _venues.value
                val targets = allVenues.filter { it.id in targetVenueIds }

                withContext(Dispatchers.IO) {
                    val coordinator = syncCoordinator
                    if (coordinator != null) {
                        coordinator.sendVenueAnnouncement(targetVenueIds, title, message)
                        for (venue in targets) {
                            repository.updateVenue(
                                venue.copy(
                                    announcementTitle = title,
                                    announcementMessage = message,
                                    announcementSentAt = sentAt,
                                    announcementSenderDeviceId = deviceId
                                )
                            )
                            settingsManager.setAnnouncementLastSeenTimestamp(
                                venue.sheetsId ?: venue.name, sentAt
                            )
                        }
                    } else {
                        for (venue in targets) {
                            val row = venue.sheetsId?.toIntOrNull() ?: continue
                            syncManager?.sendAnnouncement(row, title, message, deviceId)
                            repository.updateVenue(
                                venue.copy(
                                    announcementTitle = title,
                                    announcementMessage = message,
                                    announcementSentAt = sentAt,
                                    announcementSenderDeviceId = deviceId
                                )
                            )
                            settingsManager.setAnnouncementLastSeenTimestamp(
                                venue.sheetsId ?: venue.name, sentAt
                            )
                        }
                    }
                }

                _showSendAnnouncementDialog.value = false
            } catch (e: Exception) {
                println("❌ Failed to send announcement: ${e.message}")
            } finally {
                _isAnnouncementSending.value = false
            }
        }
    }

    fun checkForNewAnnouncements(venues: List<VenueEntity>) {
        val ctx = platformContext ?: return
        val settingsManager = SettingsManager(ctx)

        if (!settingsManager.isAnnouncementsReceptionEnabled()) return

        val deviceId = settingsManager.getOrCreatePersistentDeviceId()
        val trackedIds = settingsManager.getAnnouncementsTrackedVenueIds()
        val validityMs = settingsManager.getAnnouncementsValidityMinutes() * 60_000L
        val lastSeen = settingsManager.getAnnouncementsLastSeenTimestamps()
        val now = System.currentTimeMillis()

        val newAnnouncements = mutableListOf<com.eventmanager.app.ui.components.AnnouncementDisplay>()

        for (venue in venues) {
            if (venue.announcementSentAt == 0L) continue
            if (venue.announcementTitle.isBlank() && venue.announcementMessage.isBlank()) continue

            // Auto-exclusion: skip if this device sent it
            if (venue.announcementSenderDeviceId == deviceId) continue

            // Validity check
            if (now - venue.announcementSentAt > validityMs) continue

            // Venue filter: empty set means all venues
            val venueKey = venue.sheetsId ?: venue.name
            if (trackedIds.isNotEmpty()) {
                val venueIdStr = venue.id.toString()
                if (!trackedIds.contains(venueIdStr) && !trackedIds.contains(venueKey)) continue
            }

            // Already seen check
            val seenAt = lastSeen[venueKey] ?: 0L
            if (venue.announcementSentAt <= seenAt) continue

            newAnnouncements.add(
                com.eventmanager.app.ui.components.AnnouncementDisplay(
                    venueName = venue.name,
                    title = venue.announcementTitle,
                    message = venue.announcementMessage,
                    sentAt = venue.announcementSentAt,
                    venueKey = venueKey
                )
            )
        }

        if (newAnnouncements.isNotEmpty()) {
            _pendingAnnouncements.value = newAnnouncements
        }
    }

    fun dismissCurrentAnnouncement() {
        val ctx = platformContext ?: return
        val settingsManager = SettingsManager(ctx)
        val current = _pendingAnnouncements.value.toMutableList()
        if (current.isNotEmpty()) {
            val dismissed = current.removeAt(0)
            settingsManager.setAnnouncementLastSeenTimestamp(dismissed.venueKey, dismissed.sentAt)
            _pendingAnnouncements.value = current
        }
    }
}