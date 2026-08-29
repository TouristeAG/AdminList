package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.InstitutionSettingsKeys
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.ChangeType
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * GitLive-backed Firestore gateway with real set/get/listen when Firebase is initialized.
 */
class GitLiveFirestoreGateway(
    private val platformContext: PlatformContext? = null,
    private val settingsManager: SettingsManager? = null,
) : FirestoreGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listenerJobs = mutableListOf<CoroutineJob>()
    private val mutex = Mutex()

    private val watchedCollections = listOf(
        "guests",
        "volunteers",
        "jobs",
        "jobTypeConfigs",
        "venues",
        "salesItems",
        "transfers",
        "accounts",
        "institutionSettings",
        "metadata",
    )

    private fun ensureReady(): Boolean {
        val settings = settingsManager
        val ctx = platformContext
        if (settings != null && ctx != null) {
            FirebaseBootstrap.ensureInitialized(ctx, FirebaseOptionsReader.fromSettings(settings))
        }
        return FirebaseBootstrap.isInitialized() || runCatching {
            Class.forName("dev.gitlive.firebase.Firebase")
            Firebase.firestore
            true
        }.getOrDefault(false)
    }

    private fun db(): FirebaseFirestore? = runCatching {
        if (!ensureReady()) return null
        Firebase.firestore
    }.getOrNull()

    override fun isAvailable(): Boolean = ensureReady() && db() != null

    override suspend fun startOrgListeners(orgIds: List<String>, onChange: suspend (FirestoreRemoteChange) -> Unit) {
        val distinct = orgIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return
        val firestore = db() ?: return
        stopOrgListeners()
        mutex.withLock {
            for (orgId in distinct) {
                for (collection in watchedCollections) {
                    val job = scope.launch {
                        firestore.collection("orgs").document(orgId).collection(collection)
                            .snapshots
                            .catch { /* keep other listeners alive */ }
                            .collect { snapshot ->
                                snapshot.documentChanges.forEach { change ->
                                    val docId = change.document.id
                                    val deleted = change.type == ChangeType.REMOVED
                                    val data = if (deleted) {
                                        null
                                    } else {
                                        decodeSnapshot(change.document).ifEmpty { null }
                                    }
                                    onChange(
                                        FirestoreRemoteChange(
                                            orgId = orgId,
                                            collection = collection,
                                            documentId = docId,
                                            data = data,
                                            deleted = deleted,
                                        ),
                                    )
                                }
                            }
                    }
                    listenerJobs.add(job)
                }
            }
        }
    }

    override fun stopOrgListeners() {
        listenerJobs.forEach { it.cancel() }
        listenerJobs.clear()
    }

    override suspend fun flushPendingWrites() {
        // GitLive / native SDK flush is automatic; hook kept for API parity.
    }

    override suspend fun upsertDocument(orgId: String, collection: String, docId: String, data: Map<String, Any?>) {
        val firestore = db() ?: throw IllegalStateException("Firestore is not initialized")
        if (orgId.isBlank() || docId.isBlank()) return
        val ref = firestore.collection("orgs").document(orgId).collection(collection).document(docId)
        val fields = ruleCompatibleFirestoreMap(data)
        when (collection) {
            // Append-only ledger: create once, never update (firestore.rules).
            "transfers" -> {
                val exists = runCatching { ref.get().exists }.getOrDefault(false)
                if (!exists) {
                    ref.set(toFirestoreFieldMap(fields), merge = false)
                }
            }
            // First member must be a single create with role set (update requires admin).
            "members" -> {
                val exists = runCatching { ref.get().exists }.getOrDefault(false)
                if (exists) {
                    ref.set(toFirestoreFieldMap(fields), merge = true)
                } else {
                    ref.set(toFirestoreFieldMap(fields), merge = false)
                }
            }
            else -> {
                ref.set(toFirestoreFieldMap(fields), merge = true)
            }
        }
    }

    private fun decodeSnapshot(doc: dev.gitlive.firebase.firestore.DocumentSnapshot): Map<String, Any?> {
        runCatching {
            val asJson = doc.data<kotlinx.serialization.json.JsonObject>()
            val mapped = FirestoreJsonCodec.fromJsonObject(asJson)
            if (mapped.isNotEmpty()) return mapped
        }
        runCatching {
            val envelope = doc.data<FirestoreJsonEnvelope>()
            if (envelope.json.isNotBlank()) {
                val mapped = FirestoreJsonCodec.fromEnvelope(envelope)
                if (mapped.isNotEmpty()) return mapped
            }
        }
        return emptyMap()
    }

    override suspend fun deleteDocument(orgId: String, collection: String, docId: String) {
        val firestore = db() ?: return
        if (orgId.isBlank() || docId.isBlank()) return
        firestore.collection("orgs").document(orgId).collection(collection).document(docId).delete()
    }

    override suspend fun pullAllIntoRepository(orgId: String, repository: EventManagerRepository) {
        val firestore = db() ?: return
        if (orgId.isBlank()) return
        for (collection in watchedCollections) {
            val snap = firestore.collection("orgs").document(orgId).collection(collection).get()
            for (doc in snap.documents) {
                val data = decodeSnapshot(doc)
                if (data.isEmpty()) continue
                if (collection == "institutionSettings") {
                    val value = data["value"]?.toString().orEmpty()
                    val lm = when (val raw = data["lastModified"]) {
                        is Number -> raw.toLong()
                        is String -> raw.toLongOrNull() ?: System.currentTimeMillis()
                        else -> System.currentTimeMillis()
                    }
                    settingsManager?.applyInstitutionSettingFromRemote(doc.id, value, lm)
                    continue
                }
                if (collection == "metadata") continue
                FirestoreChangeApplier.apply(
                    FirestoreRemoteChange(
                        orgId = orgId,
                        collection = collection,
                        documentId = doc.id,
                        data = data,
                        deleted = false,
                    ),
                    repository,
                )
            }
        }
    }

    override suspend fun applyChangeToRepository(change: FirestoreRemoteChange, repository: EventManagerRepository) {
        FirestoreChangeApplier.apply(change, repository)
    }

    override suspend fun readBackendAnnouncement(orgId: String): InstitutionBackendAnnouncement? {
        val firestore = db() ?: return null
        if (orgId.isBlank()) return null
        return runCatching {
            val snap = firestore.collection("orgs").document(orgId)
                .collection("metadata").document("config").get()
            if (!snap.exists) return null
            val data = decodeSnapshot(snap)
            val typeRaw = data["backendType"] as? String ?: return null
            InstitutionBackendAnnouncement(
                backendType = BackendType.fromStorage(typeRaw),
                migrationId = (data["migrationId"] as? String).orEmpty(),
                migratedAt = (data["migratedAt"] as? Number)?.toLong()
                    ?: (data["migratedAt"] as? String)?.toLongOrNull()
                    ?: 0L,
                migratedBy = (data["migratedBy"] as? String).orEmpty(),
                firebaseOrgId = (data["firebaseOrgId"] as? String)?.takeIf { it.isNotBlank() },
                sheetsSpreadsheetIdHint = (data["sheetsSpreadsheetIdHint"] as? String)
                    ?.takeIf { it.isNotBlank() },
                firebaseProjectId = (data["firebaseProjectId"] as? String)?.takeIf { it.isNotBlank() },
                firebaseApplicationId = (data["firebaseApplicationId"] as? String)?.takeIf { it.isNotBlank() },
                firebaseApiKey = (data["firebaseApiKey"] as? String)?.takeIf { it.isNotBlank() },
                firebaseWebClientId = (data["firebaseWebClientId"] as? String)?.takeIf { it.isNotBlank() },
                firebaseWebClientSecret = (data["firebaseWebClientSecret"] as? String)?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    override suspend fun readMemberRole(orgId: String, uid: String): String? {
        return readMemberRole(orgId, uid, fromServer = false)
    }

    override suspend fun isOrgAccessibleOnServer(orgId: String, uid: String): Boolean =
        readMemberRole(orgId, uid, fromServer = true) != null

    private suspend fun readMemberRole(orgId: String, uid: String, fromServer: Boolean): String? {
        val firestore = db() ?: return null
        if (orgId.isBlank() || uid.isBlank()) return null
        return runCatching {
            val snap = if (fromServer) {
                firestore.collection("orgs").document(orgId)
                    .collection("members").document(uid).get(source = Source.SERVER)
            } else {
                firestore.collection("orgs").document(orgId)
                    .collection("members").document(uid).get()
            }
            if (!snap.exists) return null
            decodeSnapshot(snap)["role"] as? String
        }.getOrNull()
    }

    override suspend fun writeBackendAnnouncement(orgId: String, announcement: InstitutionBackendAnnouncement) {
        upsertDocument(
            orgId,
            "institutionSettings",
            InstitutionSettingsKeys.BACKEND_TYPE,
            mapOf(
                "value" to announcement.backendType.name,
                "lastModified" to announcement.migratedAt,
            ),
        )
        upsertDocument(
            orgId,
            "metadata",
            "config",
            mapOf(
                "backendType" to announcement.backendType.name,
                "migrationId" to announcement.migrationId,
                "migratedAt" to announcement.migratedAt,
                "migratedBy" to announcement.migratedBy,
                "firebaseOrgId" to announcement.firebaseOrgId,
                "sheetsSpreadsheetIdHint" to announcement.sheetsSpreadsheetIdHint,
                "firebaseProjectId" to announcement.firebaseProjectId,
                "firebaseApplicationId" to announcement.firebaseApplicationId,
                "firebaseApiKey" to announcement.firebaseApiKey,
                "firebaseWebClientId" to announcement.firebaseWebClientId,
                "firebaseWebClientSecret" to announcement.firebaseWebClientSecret,
            ),
        )
    }

    override suspend fun runPeopleCounterTransaction(
        orgId: String,
        venueName: String,
        count: Int,
        deviceId: String,
    ) {
        val firestore = db() ?: return
        if (orgId.isBlank() || venueName.isBlank()) return
        firestore.runTransaction {
            val ref = firestore.collection("orgs").document(orgId).collection("venues").document(venueName)
            val snap = get(ref)
            val existing = if (snap.exists) decodeSnapshot(snap) else emptyMap()
            val currentWriter = existing["peopleCounterWriterDeviceId"]?.toString().orEmpty().trim()
            if (currentWriter.isNotEmpty() && currentWriter != deviceId && deviceId.isNotEmpty()) {
                // Soft arbitration: another device holds the counter; only force-steal paths pass empty→deviceId.
                // Callers that need steal should pass the new deviceId after reading remote (claim with force).
            }
            val merged = existing.toMutableMap().apply {
                put("peopleCounterCount", count)
                put("peopleCounterWriterDeviceId", deviceId)
                put("peopleCounterLastModified", System.currentTimeMillis())
                put("name", venueName)
                put("lastModified", maxOf(
                    (existing["lastModified"] as? Number)?.toLong() ?: 0L,
                    System.currentTimeMillis(),
                ))
            }
            set(ref, toFirestoreFieldMap(ruleCompatibleFirestoreMap(merged)), merge = true)
        }
    }

    override suspend fun runLedgerTransaction(
        orgId: String,
        transfer: AccountTransfer,
        holderKey: String,
        newBalance: Double,
        buffer: Double,
    ): Boolean {
        val firestore = db() ?: return false
        if (orgId.isBlank()) return false
        return try {
            var accepted = false
            firestore.runTransaction {
                val transferRef = firestore.collection("orgs").document(orgId)
                    .collection("transfers").document(transfer.sourceReference)
                val accountRef = firestore.collection("orgs").document(orgId)
                    .collection("accounts").document(holderKey)

                val existingTransfer = get(transferRef)
                if (existingTransfer.exists) {
                    accepted = true
                    return@runTransaction
                }

                val accountSnap = get(accountRef)
                val currentBalance = if (accountSnap.exists) {
                    val mapped = decodeSnapshot(accountSnap)
                    when (val v = mapped["balance"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } else {
                    newBalance - transfer.amount
                }
                val nextBalance = currentBalance + transfer.amount
                if (nextBalance < -buffer) {
                    accepted = false
                    return@runTransaction
                }

                // Flat fields so rules can read balance; transfers are create-only (append-only rules).
                val transferFields = ruleCompatibleFirestoreMap(transferToMap(transfer))
                val accountFields = ruleCompatibleFirestoreMap(
                    mapOf(
                        "balance" to nextBalance,
                        "updatedAt" to System.currentTimeMillis(),
                    ),
                )
                set(transferRef, toFirestoreFieldMap(transferFields), merge = false)
                set(accountRef, toFirestoreFieldMap(accountFields), merge = true)
                accepted = true
            }
            accepted
        } catch (_: Exception) {
            false
        }
    }

    override fun guestToMap(guest: Guest) = mapOf(
        "nanoId" to guest.nanoId,
        "name" to guest.name,
        "lastNameAbbreviation" to guest.lastNameAbbreviation,
        "email" to guest.email,
        "phone" to guest.phoneNumber,
        "invitations" to guest.invitations,
        "venueName" to guest.venueName,
        "notes" to guest.notes,
        "isVolunteerBenefit" to guest.isVolunteerBenefit,
        "volunteerId" to guest.volunteerId,
        "lastModified" to guest.lastModified,
        "isTemporaryGuest" to guest.isTemporaryGuest,
        "temporaryArtistName" to guest.temporaryArtistName,
        "temporaryEventDate" to guest.temporaryEventDate,
        "temporaryContactPhone" to guest.temporaryContactPhone,
        "nfcCardUid" to guest.nfcCardUid,
        "isAdmin" to guest.isAdmin,
    )

    override fun volunteerToMap(volunteer: Volunteer) = mapOf(
        "id" to volunteer.id,
        "name" to volunteer.name,
        "lastNameAbbreviation" to volunteer.lastNameAbbreviation,
        "email" to volunteer.email,
        "phone" to volunteer.phoneNumber,
        "dateOfBirth" to volunteer.dateOfBirth,
        "gender" to volunteer.gender?.name,
        "currentRank" to volunteer.currentRank?.name,
        "isActive" to volunteer.isActive,
        "lastShiftDate" to volunteer.lastShiftDate,
        "lastModified" to volunteer.lastModified,
        "nfcCardUid" to volunteer.nfcCardUid,
        "isAdmin" to volunteer.isAdmin,
    )

    override fun jobToMap(job: Job) = mapOf(
        "jobNanoId" to job.jobNanoId,
        "volunteerId" to job.volunteerId,
        "jobTypeName" to job.jobTypeName,
        "venueName" to job.venueName,
        "date" to job.date,
        "shiftTime" to job.shiftTime.name,
        "benefitFutureEntriesRemaining" to job.benefitFutureEntriesRemaining,
        "benefitFutureEntryInvites" to job.benefitFutureEntryInvites,
        "notes" to job.notes,
        "lastModified" to job.lastModified,
    )

    override fun jobTypeToMap(config: JobTypeConfig) = mapOf(
        "name" to config.name,
        "isActive" to config.isActive,
        "isShiftJob" to config.isShiftJob,
        "isOrionJob" to config.isOrionJob,
        "requiresShiftTime" to config.requiresShiftTime,
        "novaJobType" to config.novaJobType.name,
        "benefitSystemType" to config.benefitSystemType.name,
        "manualRewards" to com.eventmanager.app.data.models.Converters().fromManualRewards(config.manualRewards),
        "accountCreditChf" to config.accountCreditChf,
        "description" to config.description,
        "lastModified" to config.lastModified,
    )

    override fun venueToMap(venue: VenueEntity) = mapOf(
        "name" to venue.name,
        "isActive" to venue.isActive,
        "peopleCounterCount" to venue.peopleCounterCount,
        "peopleCounterWriterDeviceId" to venue.peopleCounterWriterDeviceId,
        "peopleCounterLastModified" to venue.peopleCounterLastModified,
        "announcementTitle" to venue.announcementTitle,
        "announcementMessage" to venue.announcementMessage,
        "announcementSentAt" to venue.announcementSentAt,
        "announcementSenderDeviceId" to venue.announcementSenderDeviceId,
        "lastModified" to venue.lastModified,
    )

    override fun salesItemToMap(item: SalesSheetItem) = mapOf(
        "name" to item.name,
        "price" to item.price,
        "categories" to item.categories,
        "emoji" to item.emoji,
        "availableVenues" to item.availableVenues,
        "isActive" to item.isActive,
        "hasDiscount" to item.hasDiscount,
        "requiredRank" to item.requiredRank?.name,
        "lastModified" to item.lastModified,
    )

    override fun transferToMap(transfer: AccountTransfer) = mapOf(
        "transferId" to transfer.transferId,
        "sourceReference" to transfer.sourceReference,
        "holderType" to transfer.holderType.name,
        "holderId" to transfer.holderId,
        "holderName" to transfer.holderName,
        "amount" to transfer.amount,
        "type" to transfer.type.name,
        "currencyCode" to transfer.currencyCode,
        "description" to transfer.description,
        "jobReferenceKey" to transfer.jobReferenceKey,
        "jobTypeName" to transfer.jobTypeName,
        "jobDate" to transfer.jobDate,
        "creditAmountPaid" to transfer.creditAmountPaid,
        "cashAmountPaid" to transfer.cashAmountPaid,
        "posBarDiscountPercent" to transfer.posBarDiscountPercent,
        "posItemsJson" to transfer.posItemsJson,
        "posVenueName" to transfer.posVenueName,
        "createdAt" to transfer.createdAt,
        "lastModified" to transfer.lastModified,
        "syncState" to transfer.syncState.name,
    )
}
