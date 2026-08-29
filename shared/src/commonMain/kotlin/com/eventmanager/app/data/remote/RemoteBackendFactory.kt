package com.eventmanager.app.data.remote

import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.DeletionTracker
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.SyncManager
import com.eventmanager.app.data.sync.TwoWaySyncService
import com.eventmanager.app.platform.PlatformContext

/**
 * Manual composition root for remote backends (no DI framework in this project).
 */
object RemoteBackendFactory {
    fun createSyncCoordinator(
        platformContext: PlatformContext,
        repository: EventManagerRepository,
        googleSheetsService: GoogleSheetsService,
        twoWaySyncService: TwoWaySyncService,
        syncManager: SyncManager,
        deletionTracker: DeletionTracker?,
        onVolunteerGuestListRecalc: (suspend () -> Unit)? = null,
        onBackgroundDifferentialSync: (suspend () -> Unit)? = null,
        onPeopleCounterUpdate: (suspend (com.eventmanager.app.data.models.VenueEntity, Int) -> Unit)? = null,
        onTemporaryGuestsRefresh: (suspend () -> Unit)? = null,
        pendingRemoteWriteDao: com.eventmanager.app.data.dao.PendingRemoteWriteDao? = null,
        onFirebaseRemoteRepositoryChanged: (() -> Unit)? = null,
        onFirebaseSyncStatusChanged: (() -> Unit)? = null,
    ): SyncCoordinator {
        val settingsManager = SettingsManager(platformContext)
        val sheets = SheetsRemoteBackend(
            platformContext = platformContext,
            repository = repository,
            googleSheetsService = googleSheetsService,
            twoWaySyncService = twoWaySyncService,
            syncManager = syncManager,
            deletionTracker = deletionTracker,
            settingsManager = settingsManager,
            onVolunteerGuestListRecalc = onVolunteerGuestListRecalc,
            onBackgroundDifferentialSync = onBackgroundDifferentialSync,
            onPeopleCounterUpdate = onPeopleCounterUpdate,
            onTemporaryGuestsRefresh = onTemporaryGuestsRefresh,
        )
        val gateway = createFirestoreGateway(platformContext, settingsManager)
        val ledger = FirebaseLedgerService(repository, settingsManager, gateway)
        val pendingQueue = PendingRemoteWriteQueue(
            dao = pendingRemoteWriteDao,
            activeOrgId = {
                val active = settingsManager.getFirebaseOrgId().trim()
                when {
                    active.isNotBlank() && !isFirebaseOrgAllSentinel(active) -> active
                    else -> settingsManager.getFirebaseLastSingleOrgId().trim()
                }
            },
        )
        pendingQueue.onPendingCountChanged = { onFirebaseSyncStatusChanged?.invoke() }
        val firebase = FirebaseRemoteBackend(
            platformContext = platformContext,
            repository = repository,
            settingsManager = settingsManager,
            firestoreGateway = gateway,
            ledgerService = ledger,
            pendingWrites = pendingQueue,
        )
        val coordinator = SyncCoordinator(settingsManager, sheets, firebase)
        firebase.onInstitutionBackendAnnouncementChanged = {
            coordinator.refreshInstitutionBackendGuard()
        }
        firebase.onRemoteRepositoryChanged = onFirebaseRemoteRepositoryChanged
        firebase.onSyncStatusChanged = onFirebaseSyncStatusChanged
        sheets.onAfterBackgroundSync = {
            coordinator.refreshInstitutionBackendGuard()
        }
        return coordinator
    }
}

expect fun createFirestoreGateway(
    platformContext: PlatformContext?,
    settingsManager: SettingsManager?,
): FirestoreGateway
