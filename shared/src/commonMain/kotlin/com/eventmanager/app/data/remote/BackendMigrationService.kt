package com.eventmanager.app.data.remote

import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.SyncResult
import com.eventmanager.app.data.sync.TwoWaySyncService
import com.eventmanager.app.data.utils.NanoIdGenerator
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class MigrationDirection {
    SHEETS_TO_FIREBASE,
    FIREBASE_TO_SHEETS,
}

data class MigrationProgress(
    val phase: String,
    val detail: String = "",
    val fraction: Float = 0f,
    val isDone: Boolean = false,
    val error: String? = null,
    val entityCounts: Map<String, Int> = emptyMap(),
)

data class MigrationEntityCounts(
    val guests: Int,
    val volunteers: Int,
    val jobs: Int,
    val jobTypes: Int,
    val venues: Int,
    val salesItems: Int,
    val transfers: Int,
) {
    fun asMap(): Map<String, Int> = mapOf(
        "guests" to guests,
        "volunteers" to volunteers,
        "jobs" to jobs,
        "jobTypes" to jobTypes,
        "venues" to venues,
        "salesItems" to salesItems,
        "transfers" to transfers,
    )

    fun total(): Int = guests + volunteers + jobs + jobTypes + venues + salesItems + transfers
}

/**
 * Admin-only Sheets ↔ Firebase migration orchestrator.
 * Dual-announces backend_type on both backends before switching the initiator device.
 *
 * Rollback rule (original plan): any failure before announce/switch keeps the previous backend.
 */
class BackendMigrationService(
    private val platformContext: PlatformContext,
    private val repository: EventManagerRepository,
    private val settingsManager: SettingsManager,
    private val sheetsBackend: SheetsRemoteBackend,
    private val firebaseBackend: FirebaseRemoteBackend,
    private val twoWaySyncService: TwoWaySyncService,
    private val googleSheetsService: GoogleSheetsService,
) {
    private val _progress = MutableStateFlow(MigrationProgress("idle"))
    val progress: StateFlow<MigrationProgress> = _progress.asStateFlow()

    @Volatile
    private var cancelRequested: Boolean = false

    fun requestCancel() {
        cancelRequested = true
    }

    private fun abortIfCancelled(): SyncResult.Error? {
        if (!cancelRequested) return null
        val msg = "Migration cancelled before backend switch"
        _progress.value = MigrationProgress("cancelled", detail = msg, error = msg, isDone = true)
        return SyncResult.Error(msg)
    }

    private suspend fun snapshotCounts(): MigrationEntityCounts = MigrationEntityCounts(
        guests = repository.getAllGuests().first().size,
        volunteers = repository.getAllVolunteers().first().size,
        jobs = repository.getAllJobs().first().size,
        jobTypes = repository.getAllJobTypeConfigs().first().size,
        venues = repository.getAllVenues().first().size,
        salesItems = repository.getAllSalesSheetItems().first().size,
        transfers = repository.getAllAccountTransfersOnce().size,
    )

    /**
     * Ensures the signed-in Firebase user is an org admin in Firestore before bulk push.
     * Without `members/{uid}`, security rules deny all transfer reads/writes (PERMISSION_DENIED).
     *
     * Membership is probed first: bootstrapping writes `role: 'admin'`, which the rules only
     * accept while `metadata/config` is absent. Re-bootstrapping an org that already exists is
     * refused, and it would also rotate the invitation code every team device already holds.
     */
    private suspend fun ensureFirestoreMigrationMember(orgId: String): SyncResult {
        if (!FirebaseAuthBridge.isSignedIn()) {
            return SyncResult.Error("Sign in with Google (Firebase) before migrating")
        }
        val uid = FirebaseAuthBridge.currentUserId().orEmpty()
        if (uid.isBlank()) {
            return SyncResult.Error("Firebase auth UID missing — sign in again")
        }
        val email = FirebaseAuthBridge.currentUserEmail()
            ?: settingsManager.getFirebaseAuthEmail().takeIf { it.isNotBlank() }
        val gateway = createFirestoreGateway(platformContext, settingsManager)
        if (!gateway.isAvailable()) {
            return SyncResult.Error("Firestore not available — check Firebase project configuration")
        }
        val probe = gateway.probeMembership(orgId, uid)
        if (probe is MembershipProbe.Unavailable) {
            return SyncResult.Error(
                "Cannot reach Firestore to check membership in \"$orgId\" — check the network " +
                    "and retry. Migrating on an unknown remote state would overwrite it.",
            )
        }
        if (probe is MembershipProbe.Member) {
            return if (MemberRole.fromStorage(probe.role) == MemberRole.ADMIN) {
                SyncResult.Success("Firestore admin member ready")
            } else {
                SyncResult.Error(
                    "This account joined \"$orgId\" as \"${probe.role}\". Only a Firebase org " +
                        "admin can migrate — ask an admin to promote it in Admin → Firebase team, " +
                        "or run the migration from an admin device.",
                )
            }
        }
        return try {
            val code = MemberRoleAdmin.bootstrapOrgAdmin(
                gateway = gateway,
                orgId = orgId,
                uid = uid,
                email = email,
                allowedEmailDomains = settingsManager.getAllowedEmailDomains(),
            )
            settingsManager.setFirebaseBootstrapCode(code)
            SyncResult.Success("Firestore admin member ready")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isFirestorePermissionDenied(e)) {
                SyncResult.Error(firebaseMigrationAdminDenialMessage(orgId, probe))
            } else {
                SyncResult.Error(
                    e.message ?: "Failed to bootstrap Firestore admin member — publish updated firestore.rules and retry",
                )
            }
        }
    }

    private fun writeLocalJsonBackup(counts: MigrationEntityCounts): String? {
        return runCatching {
            val fm = PlatformFileManager(platformContext)
            val name = "migration-backup-${System.currentTimeMillis()}.json"
            val file = java.io.File(fm.getCacheDirectory(), name)
            val body = buildString {
                appendLine("{")
                appendLine("  \"createdAt\": ${System.currentTimeMillis()},")
                appendLine("  \"backendType\": \"${settingsManager.getBackendType().name}\",")
                val entries = counts.asMap().entries.toList()
                entries.forEachIndexed { index, (k, v) ->
                    val comma = if (index < entries.size - 1) "," else ""
                    appendLine("  \"$k\": $v$comma")
                }
                appendLine("}")
            }
            file.writeText(body)
            file.absolutePath
        }.getOrNull()
    }

    private fun isMigrationAlreadyOnFirebase(announcement: InstitutionBackendAnnouncement?): Boolean =
        announcement?.backendType == BackendType.FIREBASE && announcement.migrationId.isNotBlank()

    private suspend fun readRemoteFirebaseMigration(orgId: String): InstitutionBackendAnnouncement? {
        val gateway = createFirestoreGateway(platformContext, settingsManager)
        if (!gateway.isAvailable()) return null
        return gateway.readBackendAnnouncement(orgId)
    }

    private suspend fun resetLocalBeforeRemotePull(backend: FirebaseRemoteBackend) {
        repository.clearAllData()
        backend.clearPendingWrites()
    }

    suspend fun migrateSheetsToFirebase(
        orgId: String,
        migratedBy: String,
    ): SyncResult {
        cancelRequested = false
        val previousBackend = settingsManager.getBackendType()
        val migrationId = NanoIdGenerator.generateGuestId()
        val migratedAt = System.currentTimeMillis()
        var announced = false
        return try {
            _progress.value = MigrationProgress("preflight", "Validating Firebase org", 0.05f)
            if (orgId.isBlank()) return SyncResult.Error("Firebase org ID required")
            abortIfCancelled()?.let { return it }
            settingsManager.setFirebaseOrgId(orgId)

            firebaseBackend.stopBackgroundRemoteSync()

            _progress.value = MigrationProgress("auth", "Bootstrapping Firestore admin member", 0.1f)
            val bootstrap = ensureFirestoreMigrationMember(orgId)
            if (bootstrap is SyncResult.Error) {
                _progress.value = MigrationProgress("error", detail = bootstrap.message, error = bootstrap.message, isDone = true)
                return bootstrap
            }
            abortIfCancelled()?.let { return it }

            readRemoteFirebaseMigration(orgId)?.let { existing ->
                if (isMigrationAlreadyOnFirebase(existing)) {
                    val msg =
                        "This organization is already on Firebase. Use « Connect to new database » to join — do not run migration again."
                    _progress.value = MigrationProgress("error", detail = msg, error = msg, isDone = true)
                    return SyncResult.Error(msg)
                }
            }

            _progress.value = MigrationProgress("pull", "Full download from Google Sheets", 0.15f)
            val pull = sheetsBackend.performStartupSync()
            if (pull is SyncResult.Error) return pull
            abortIfCancelled()?.let { return it }

            settingsManager.getLocalInstitutionBackendAnnouncement()?.let { local ->
                if (isMigrationAlreadyOnFirebase(local)) {
                    val msg =
                        "Institution is already announced on Firebase. Use « Connect to new database » on this device instead of migrating again."
                    _progress.value = MigrationProgress("error", detail = msg, error = msg, isDone = true)
                    return SyncResult.Error(msg)
                }
            }

            val beforeCounts = snapshotCounts()
            _progress.value = MigrationProgress(
                "backup",
                "Writing local JSON backup",
                0.3f,
                entityCounts = beforeCounts.asMap(),
            )
            writeLocalJsonBackup(beforeCounts)
            abortIfCancelled()?.let { return it }

            _progress.value = MigrationProgress("push", "Uploading Room → Firestore", 0.45f, entityCounts = beforeCounts.asMap())
            if (!createFirestoreGateway(platformContext, settingsManager).isAvailable()) {
                val msg = "Firebase SDK not initialized — configure google-services / FirebaseOptions before migrating"
                _progress.value = MigrationProgress("error", detail = msg, error = msg, isDone = true)
                return SyncResult.Error(msg)
            }
            val push = try {
                kotlinx.coroutines.withTimeout(180_000) {
                    firebaseBackend.pushAllLocalEntities()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                val msg = "Firestore upload timed out after 3 minutes — check Desktop Firestore/gRPC (see logs)"
                _progress.value = MigrationProgress("error", detail = msg, error = msg, isDone = true)
                return SyncResult.Error(msg)
            }
            if (push is SyncResult.Error) {
                _progress.value = MigrationProgress("error", detail = push.message, error = push.message, isDone = true)
                return push
            }
            abortIfCancelled()?.let { return it }

            val afterCounts = snapshotCounts()
            if (afterCounts.total() < beforeCounts.total()) {
                val msg = "Entity count dropped after push (${beforeCounts.total()} → ${afterCounts.total()})"
                _progress.value = MigrationProgress("error", detail = msg, error = msg, isDone = true, entityCounts = afterCounts.asMap())
                return SyncResult.Error(msg)
            }

            // Rebuild account balances snapshot for Firebase ledger seed
            _progress.value = MigrationProgress("accounts", "Seeding account balances", 0.65f, entityCounts = afterCounts.asMap())
            firebaseBackend.seedAccountBalancesFromLocalLedger()

            abortIfCancelled()?.let { return it }

            _progress.value = MigrationProgress("announce", "Announcing backend_type", 0.75f, entityCounts = afterCounts.asMap())
            val announcement = InstitutionBackendAnnouncement(
                backendType = BackendType.FIREBASE,
                migrationId = migrationId,
                migratedAt = migratedAt,
                migratedBy = migratedBy,
                firebaseOrgId = orgId,
                sheetsSpreadsheetIdHint = settingsManager.getSpreadsheetId(),
                firebaseProjectId = settingsManager.getFirebaseProjectId().takeIf { it.isNotBlank() },
                firebaseApplicationId = settingsManager.getFirebaseApplicationId().takeIf { it.isNotBlank() },
                firebaseWebClientId = settingsManager.getFirebaseWebClientId().takeIf { it.isNotBlank() },
            )
            // Target backend first, then switch locally, and only then tell the old backend.
            // Announcing on Sheets before Firestore is what created permanently stuck devices:
            // a refused Firestore write rolled the local backend back to Sheets while the Sheets
            // announcement already said "Firebase", so the backend guard demanded a follow that
            // could never succeed.
            firebaseBackend.announceInstitutionBackendMigration(announcement)

            _progress.value = MigrationProgress("switch", "Switching local backend", 0.9f, entityCounts = afterCounts.asMap())
            settingsManager.setBackendType(BackendType.FIREBASE)
            settingsManager.setFollowedBackendMigrationId(migrationId)
            settingsManager.applyLocalInstitutionBackendAnnouncement(announcement)
            announced = true

            val peerNotice = runCatching {
                sheetsBackend.announceInstitutionBackendMigration(announcement)
            }.exceptionOrNull()

            _progress.value = MigrationProgress("done", "Migration complete", 1f, isDone = true, entityCounts = afterCounts.asMap())
            if (peerNotice != null) {
                // This device is migrated; peers still on Sheets just were not told yet.
                SyncResult.Success(
                    "Migrated to Firebase org $orgId. Could not write the notice on Google " +
                        "Sheets (${peerNotice.message}) — other devices must join with the QR code.",
                )
            } else {
                SyncResult.Success("Migrated to Firebase org $orgId")
            }
        } catch (e: Exception) {
            // Reverting after the announcement would contradict what the remotes already say.
            if (!announced) settingsManager.setBackendType(previousBackend)
            _progress.value = MigrationProgress("error", error = e.message, isDone = true)
            SyncResult.Error(e.message ?: "Migration failed")
        }
    }

    suspend fun migrateFirebaseToSheets(
        spreadsheetId: String,
        migratedBy: String,
    ): SyncResult {
        cancelRequested = false
        val previousBackend = settingsManager.getBackendType()
        val migrationId = NanoIdGenerator.generateGuestId()
        val migratedAt = System.currentTimeMillis()
        var announced = false
        return try {
            _progress.value = MigrationProgress("preflight", "Validating spreadsheet", 0.05f)
            if (spreadsheetId.isBlank()) return SyncResult.Error("Spreadsheet ID required")
            abortIfCancelled()?.let { return it }
            settingsManager.saveSpreadsheetId(spreadsheetId)
            googleSheetsService.initializeSheetsService()

            _progress.value = MigrationProgress("pull", "Full download from Firestore", 0.2f)
            firebaseBackend.performStartupSync()
            abortIfCancelled()?.let { return it }

            val beforeCounts = snapshotCounts()
            writeLocalJsonBackup(beforeCounts)
            abortIfCancelled()?.let { return it }

            _progress.value = MigrationProgress("push", "Uploading Room → Sheets", 0.5f, entityCounts = beforeCounts.asMap())
            twoWaySyncService.backupToGoogleSheets()
            abortIfCancelled()?.let {
                settingsManager.setBackendType(previousBackend)
                return it
            }

            val afterCounts = snapshotCounts()
            _progress.value = MigrationProgress("announce", "Announcing backend_type", 0.75f, entityCounts = afterCounts.asMap())
            val announcement = InstitutionBackendAnnouncement(
                backendType = BackendType.SHEETS,
                migrationId = migrationId,
                migratedAt = migratedAt,
                migratedBy = migratedBy,
                firebaseOrgId = settingsManager.getFirebaseOrgId().takeIf { it.isNotBlank() },
                sheetsSpreadsheetIdHint = spreadsheetId,
            )
            // Target backend first, then switch locally, then notify the old one (see the
            // Sheets → Firebase path for why the reverse order strands devices).
            sheetsBackend.announceInstitutionBackendMigration(announcement)

            _progress.value = MigrationProgress("switch", "Switching local backend", 0.9f, entityCounts = afterCounts.asMap())
            settingsManager.setBackendType(BackendType.SHEETS)
            settingsManager.setFollowedBackendMigrationId(migrationId)
            settingsManager.applyLocalInstitutionBackendAnnouncement(announcement)
            announced = true

            val peerNotice = runCatching {
                firebaseBackend.announceInstitutionBackendMigration(announcement)
            }.exceptionOrNull()

            _progress.value = MigrationProgress("done", "Migration complete", 1f, isDone = true, entityCounts = afterCounts.asMap())
            if (peerNotice != null) {
                SyncResult.Success(
                    "Migrated to Google Sheets $spreadsheetId. Could not write the notice on " +
                        "Firestore (${peerNotice.message}) — other devices must be switched manually.",
                )
            } else {
                SyncResult.Success("Migrated to Google Sheets $spreadsheetId")
            }
        } catch (e: Exception) {
            if (!announced) settingsManager.setBackendType(previousBackend)
            _progress.value = MigrationProgress("error", error = e.message, isDone = true)
            SyncResult.Error(e.message ?: "Migration failed")
        }
    }

    suspend fun followMigration(
        announcement: InstitutionBackendAnnouncement,
        spreadsheetIdOverride: String? = null,
        orgIdOverride: String? = null,
    ): SyncResult {
        return try {
            sheetsBackend.stopBackgroundRemoteSync()
            firebaseBackend.stopBackgroundRemoteSync()
            when (announcement.backendType) {
                BackendType.FIREBASE -> {
                    val orgId = orgIdOverride ?: announcement.firebaseOrgId.orEmpty()
                    if (orgId.isBlank()) return SyncResult.Error("Firebase org ID required")
                    settingsManager.setFirebaseOrgId(orgId)
                    settingsManager.setBackendType(BackendType.FIREBASE)
                    // Discard stale Sheets-era Room data — Firestore is the source of truth when joining.
                    resetLocalBeforeRemotePull(firebaseBackend)
                    firebaseBackend.performStartupSync()
                }
                BackendType.SHEETS -> {
                    val sheetId = spreadsheetIdOverride
                        ?: announcement.sheetsSpreadsheetIdHint
                        ?: settingsManager.getSpreadsheetId()
                    if (sheetId.isBlank() || sheetId == "YOUR_SPREADSHEET_ID_HERE") {
                        return SyncResult.Error("Spreadsheet ID required")
                    }
                    settingsManager.saveSpreadsheetId(sheetId)
                    settingsManager.setBackendType(BackendType.SHEETS)
                    repository.clearAllData()
                    firebaseBackend.clearPendingWrites()
                    sheetsBackend.performStartupSync()
                }
            }
            settingsManager.setFollowedBackendMigrationId(announcement.migrationId)
            settingsManager.applyLocalInstitutionBackendAnnouncement(announcement)
            SyncResult.Success("Followed migration to ${announcement.backendType}")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Follow migration failed")
        }
    }
}
