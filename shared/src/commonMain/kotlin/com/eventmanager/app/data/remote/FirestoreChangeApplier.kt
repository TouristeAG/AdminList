package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.security.crypto.SensitiveFieldCodec
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.BenefitSystemType
import com.eventmanager.app.data.models.Gender
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobType
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.VolunteerRank
import com.eventmanager.app.data.repository.EventManagerRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Applies a remote Firestore document change into Room using lastModified LWW.
 * Ignores echo is handled by callers via [sourceDeviceId] before invoking this.
 */
object FirestoreChangeApplier {

    suspend fun apply(change: FirestoreRemoteChange, repository: EventManagerRepository) {
        try {
            val orgId = change.orgId
            val data = change.data
            if (FirestoreApplyPolicy.isRemoteDelete(change.deleted)) {
                applyDelete(orgId, change.collection, change.documentId, repository)
                return
            }
            if (FirestoreApplyPolicy.shouldSkipIncompleteSnapshot(change.deleted, data)) {
                return
            }
            val payload = data ?: return
            when (change.collection) {
                "guests" -> applyGuest(orgId, change.documentId, payload, repository)
                "volunteers" -> applyVolunteer(orgId, change.documentId, payload, repository)
                "jobs" -> applyJob(orgId, change.documentId, payload, repository)
                "jobTypeConfigs" -> applyJobType(orgId, change.documentId, payload, repository)
                "venues" -> applyVenue(orgId, change.documentId, payload, repository)
                "salesItems" -> applySalesItem(orgId, change.documentId, payload, repository)
                "transfers" -> applyTransfer(orgId, change.documentId, payload, repository)
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println(
                "Firestore apply failed for ${change.collection}/${change.documentId} " +
                    "(org=${change.orgId}): ${e.message}"
            )
        }
    }

    private suspend fun applyDelete(
        orgId: String,
        collection: String,
        documentId: String,
        repository: EventManagerRepository,
    ) {
        when (collection) {
            "guests" -> {
                val existing = repository.getGuestByNanoIdAndOrg(documentId, orgId)
                    ?: repository.getGuestByNanoId(documentId)
                existing?.let { repository.deleteGuest(it) }
            }
            "volunteers" -> repository.getVolunteerById(documentId)?.takeIf { it.firebaseOrgId == orgId }
                ?.let { repository.deleteVolunteer(it) }
            "jobs" -> repository.getJobByJobNanoId(documentId)?.takeIf { it.firebaseOrgId == orgId }
                ?.let { repository.deleteJob(it) }
            "jobTypeConfigs" -> repository.getJobTypeConfigByNameAndOrg(documentId, orgId)
                ?.let { repository.deleteJobTypeConfig(it) }
            "venues" -> repository.getVenueByNameAndOrg(documentId, orgId)
                ?.let { repository.deleteVenue(it) }
            "salesItems" -> repository.getSalesSheetItemByNameAndOrg(documentId, orgId)
                ?.let { repository.deleteSalesSheetItem(it) }
            "transfers" -> {
                repository.getAccountTransferBySourceReference(documentId)?.takeIf { it.firebaseOrgId == orgId }
                    ?.let { existing ->
                        // Never drop a local PENDING row on a remote REMOVED echo / race.
                        if (existing.syncState == AccountTransferSyncState.PENDING) return
                        repository.deleteAccountTransfer(existing)
                    }
            }
        }
    }

    private suspend fun applyGuest(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val decrypted = SensitiveFieldCodec.decryptGuestMap(data, orgId)
        val remoteLm = longOf(decrypted["lastModified"]) ?: return
        // NanoID is globally unique in Room. Look up without requiring an org match so a
        // live snapshot cannot crash when the local row has a blank or different org id.
        val existing = repository.getGuestByNanoIdAndOrg(docId, orgId)
            ?: repository.getGuestByNanoId(docId)
        if (existing != null && FirestoreApplyPolicy.shouldKeepLocal(existing.lastModified, remoteLm)) {
            backfillBlankOrg(existing.firebaseOrgId, orgId) { tagged ->
                repository.updateGuest(existing.copy(firebaseOrgId = tagged))
            }
            return
        }
        val remote = Guest(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            nanoId = docId,
            name = stringOf(decrypted["name"]).orEmpty(),
            lastNameAbbreviation = stringOf(decrypted["lastNameAbbreviation"]).orEmpty(),
            email = stringOf(decrypted["email"]).orEmpty(),
            phoneNumber = stringOf(decrypted["phone"]).orEmpty(),
            invitations = intOf(decrypted["invitations"]) ?: 0,
            venueName = stringOf(decrypted["venueName"]).orEmpty(),
            notes = stringOf(decrypted["notes"]).orEmpty(),
            isVolunteerBenefit = boolOf(decrypted["isVolunteerBenefit"]) ?: false,
            volunteerId = stringOf(decrypted["volunteerId"]),
            lastModified = remoteLm,
            isTemporaryGuest = boolOf(decrypted["isTemporaryGuest"]) ?: false,
            temporaryArtistName = stringOf(decrypted["temporaryArtistName"]).orEmpty(),
            temporaryEventDate = longOf(decrypted["temporaryEventDate"]),
            temporaryContactPhone = stringOf(decrypted["temporaryContactPhone"]).orEmpty(),
            nfcCardUid = stringOf(decrypted["nfcCardUid"]).orEmpty(),
            nfcCardUidHash = stringOf(decrypted["nfcCardUidHash"]).orEmpty(),
            isAdmin = boolOf(decrypted["isAdmin"]) ?: false,
            firebaseOrgId = FirestoreApplyPolicy.orgIdToPersist(existing?.firebaseOrgId.orEmpty(), orgId),
        )
        if (existing == null) {
            try {
                repository.insertGuest(remote)
            } catch (_: IllegalArgumentException) {
                val raced = repository.getGuestByNanoId(docId) ?: return
                if (raced.lastModified < remoteLm) {
                    repository.updateGuest(remote.copy(id = raced.id, sheetsId = raced.sheetsId ?: remote.sheetsId))
                }
            }
        } else {
            repository.updateGuest(remote)
        }
    }

    private suspend fun applyVolunteer(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val decrypted = SensitiveFieldCodec.decryptVolunteerMap(data, orgId)
        val remoteLm = longOf(decrypted["lastModified"]) ?: return
        val existing = repository.getVolunteerById(docId)
        if (existing != null && FirestoreApplyPolicy.shouldKeepLocal(existing.lastModified, remoteLm)) {
            backfillBlankOrg(existing.firebaseOrgId, orgId) { tagged ->
                repository.updateVolunteer(existing.copy(firebaseOrgId = tagged))
            }
            return
        }
        val remote = Volunteer(
            id = docId,
            sheetsId = existing?.sheetsId,
            name = stringOf(decrypted["name"]).orEmpty(),
            lastNameAbbreviation = stringOf(decrypted["lastNameAbbreviation"]).orEmpty(),
            email = stringOf(decrypted["email"]).orEmpty(),
            phoneNumber = stringOf(decrypted["phone"]).orEmpty(),
            dateOfBirth = stringOf(decrypted["dateOfBirth"]).orEmpty(),
            gender = enumOrNull<Gender>(stringOf(decrypted["gender"])),
            currentRank = enumOrNull<VolunteerRank>(stringOf(decrypted["currentRank"])),
            isActive = boolOf(decrypted["isActive"]) ?: true,
            lastShiftDate = longOf(decrypted["lastShiftDate"]),
            lastModified = remoteLm,
            nfcCardUid = stringOf(decrypted["nfcCardUid"]).orEmpty(),
            nfcCardUidHash = stringOf(decrypted["nfcCardUidHash"]).orEmpty(),
            isAdmin = boolOf(decrypted["isAdmin"]) ?: false,
            firebaseOrgId = FirestoreApplyPolicy.orgIdToPersist(existing?.firebaseOrgId.orEmpty(), orgId),
        )
        if (existing == null) repository.insertVolunteer(remote) else repository.updateVolunteer(remote)
    }

    private suspend fun applyJob(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val remoteLm = longOf(data["lastModified"]) ?: return
        val existing = repository.getJobByJobNanoId(docId)
        if (existing != null && FirestoreApplyPolicy.shouldKeepLocal(existing.lastModified, remoteLm)) {
            backfillBlankOrg(existing.firebaseOrgId, orgId) { tagged ->
                repository.updateJob(existing.copy(firebaseOrgId = tagged))
            }
            return
        }
        val jobTypeName = stringOf(data["jobTypeName"]).orEmpty()
        val remote = Job(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            jobNanoId = docId,
            volunteerId = stringOf(data["volunteerId"]).orEmpty(),
            jobType = enumOrNull<JobType>(jobTypeName) ?: JobType.OTHER,
            jobTypeName = jobTypeName.ifBlank { existing?.jobTypeName.orEmpty() },
            venueName = stringOf(data["venueName"]).orEmpty(),
            date = longOf(data["date"]) ?: existing?.date ?: 0L,
            shiftTime = enumOrNull<ShiftTime>(stringOf(data["shiftTime"])) ?: ShiftTime.BEFORE_MIDNIGHT,
            benefitFutureEntriesRemaining = intOf(data["benefitFutureEntriesRemaining"]),
            benefitFutureEntryInvites = intOf(data["benefitFutureEntryInvites"]),
            notes = stringOf(data["notes"]) ?: existing?.notes.orEmpty(),
            lastModified = remoteLm,
            firebaseOrgId = FirestoreApplyPolicy.orgIdToPersist(existing?.firebaseOrgId.orEmpty(), orgId),
        )
        if (existing == null) repository.insertJob(remote) else repository.updateJob(remote)
    }

    private suspend fun applyJobType(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val existing = repository.getJobTypeConfigByNameAndOrg(docId, orgId)
        val remoteLm = longOf(data["lastModified"]) ?: existing?.lastModified ?: 1L
        if (existing != null && existing.lastModified >= remoteLm) return
        val converters = com.eventmanager.app.data.models.Converters()
        val remote = JobTypeConfig(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            name = docId,
            isActive = boolOf(data["isActive"]) ?: true,
            isShiftJob = boolOf(data["isShiftJob"]) ?: existing?.isShiftJob ?: true,
            isOrionJob = boolOf(data["isOrionJob"]) ?: existing?.isOrionJob ?: false,
            requiresShiftTime = boolOf(data["requiresShiftTime"]) ?: existing?.requiresShiftTime ?: true,
            novaJobType = enumOrNull<com.eventmanager.app.data.models.NovaJobType>(stringOf(data["novaJobType"]))
                ?: existing?.novaJobType
                ?: com.eventmanager.app.data.models.NovaJobType.DEFAULT_SHIFT,
            benefitSystemType = enumOrNull<BenefitSystemType>(stringOf(data["benefitSystemType"]))
                ?: existing?.benefitSystemType
                ?: BenefitSystemType.STELLAR,
            manualRewards = converters.toManualRewards(stringOf(data["manualRewards"]))
                ?: existing?.manualRewards,
            accountCreditChf = doubleOf(data["accountCreditChf"]) ?: existing?.accountCreditChf,
            description = stringOf(data["description"]) ?: existing?.description.orEmpty(),
            lastModified = remoteLm,
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertJobTypeConfig(remote) else repository.updateJobTypeConfig(remote)
    }

    private suspend fun applyVenue(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val existing = repository.getVenueByNameAndOrg(docId, orgId)
        val remoteLm = longOf(data["lastModified"])
            ?: longOf(data["peopleCounterLastModified"])
            ?: existing?.lastModified
            ?: 1L
        if (existing != null && existing.lastModified >= remoteLm &&
            (longOf(data["peopleCounterLastModified"]) ?: 0L) <= existing.peopleCounterLastModified
        ) {
            return
        }
        val remote = VenueEntity(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            name = docId,
            description = existing?.description.orEmpty(),
            isActive = boolOf(data["isActive"]) ?: existing?.isActive ?: true,
            peopleCounterCount = intOf(data["peopleCounterCount"]) ?: existing?.peopleCounterCount ?: 0,
            peopleCounterWriterDeviceId = stringOf(data["peopleCounterWriterDeviceId"])
                ?: existing?.peopleCounterWriterDeviceId.orEmpty(),
            peopleCounterWriterAccountEmail = stringOf(data["peopleCounterWriterAccountEmail"])
                ?: existing?.peopleCounterWriterAccountEmail.orEmpty(),
            peopleCounterLastModified = longOf(data["peopleCounterLastModified"])
                ?: existing?.peopleCounterLastModified
                ?: 0L,
            announcementTitle = stringOf(data["announcementTitle"]) ?: existing?.announcementTitle.orEmpty(),
            announcementMessage = stringOf(data["announcementMessage"]) ?: existing?.announcementMessage.orEmpty(),
            announcementSentAt = longOf(data["announcementSentAt"]) ?: existing?.announcementSentAt ?: 0L,
            announcementSenderDeviceId = stringOf(data["announcementSenderDeviceId"])
                ?: existing?.announcementSenderDeviceId.orEmpty(),
            lastModified = maxOf(remoteLm, existing?.lastModified ?: 0L),
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertVenue(remote) else repository.updateVenue(remote)
    }

    private suspend fun applySalesItem(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val existing = repository.getSalesSheetItemByNameAndOrg(docId, orgId)
        val remoteLm = longOf(data["lastModified"]) ?: existing?.lastModified ?: 1L
        if (existing != null && existing.lastModified >= remoteLm) return
        val remote = SalesSheetItem(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            name = docId,
            price = doubleOf(data["price"]) ?: existing?.price ?: 0.0,
            categories = stringOf(data["categories"]) ?: existing?.categories.orEmpty(),
            emoji = stringOf(data["emoji"]) ?: existing?.emoji.orEmpty(),
            availableVenues = stringOf(data["availableVenues"]) ?: existing?.availableVenues.orEmpty(),
            isActive = boolOf(data["isActive"]) ?: existing?.isActive ?: true,
            hasDiscount = boolOf(data["hasDiscount"]) ?: existing?.hasDiscount ?: false,
            requiredRank = enumOrNull<VolunteerRank>(stringOf(data["requiredRank"])) ?: existing?.requiredRank,
            lastModified = remoteLm,
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertSalesSheetItem(remote) else repository.updateSalesSheetItem(remote)
    }

    private suspend fun applyTransfer(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val decrypted = SensitiveFieldCodec.decryptTransferMap(data, orgId)
        val remoteLm = longOf(decrypted["lastModified"]) ?: longOf(decrypted["createdAt"]) ?: return
        val existing = repository.getAccountTransferBySourceReference(docId)
        if (existing != null && FirestoreApplyPolicy.shouldKeepLocal(existing.lastModified, remoteLm)) {
            backfillBlankOrg(existing.firebaseOrgId, orgId) { tagged ->
                repository.updateAccountTransfer(
                    existing.copy(
                        firebaseOrgId = tagged,
                        syncState = if (existing.syncState == AccountTransferSyncState.PENDING) {
                            AccountTransferSyncState.CONFIRMED
                        } else {
                            existing.syncState
                        },
                    ),
                )
            }
            return
        }
        val holderType = enumOrNull<AccountHolderType>(stringOf(decrypted["holderType"]))
            ?: existing?.holderType
            ?: return
        val remote = AccountTransfer(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            transferId = stringOf(decrypted["transferId"]) ?: existing?.transferId
                ?: com.eventmanager.app.data.utils.NanoIdGenerator.generateGuestId(),
            sourceReference = docId,
            holderType = holderType,
            holderId = stringOf(decrypted["holderId"]) ?: existing?.holderId.orEmpty(),
            holderName = stringOf(decrypted["holderName"]) ?: existing?.holderName.orEmpty(),
            amount = doubleOf(decrypted["amount"]) ?: existing?.amount ?: 0.0,
            type = enumOrNull<AccountTransferType>(stringOf(decrypted["type"]))
                ?: existing?.type
                ?: AccountTransferType.MANUAL_ADJUSTMENT,
            currencyCode = stringOf(decrypted["currencyCode"]) ?: existing?.currencyCode ?: "CHF",
            description = stringOf(decrypted["description"]) ?: existing?.description.orEmpty(),
            jobReferenceKey = stringOf(decrypted["jobReferenceKey"]) ?: existing?.jobReferenceKey.orEmpty(),
            jobTypeName = stringOf(decrypted["jobTypeName"]) ?: existing?.jobTypeName.orEmpty(),
            jobDate = longOf(decrypted["jobDate"]) ?: existing?.jobDate,
            creditAmountPaid = doubleOf(decrypted["creditAmountPaid"]) ?: existing?.creditAmountPaid,
            cashAmountPaid = doubleOf(decrypted["cashAmountPaid"]) ?: existing?.cashAmountPaid,
            posBarDiscountPercent = intOf(decrypted["posBarDiscountPercent"]) ?: existing?.posBarDiscountPercent,
            posItemsJson = stringOf(decrypted["posItemsJson"]) ?: existing?.posItemsJson.orEmpty(),
            posVenueName = stringOf(decrypted["posVenueName"]) ?: existing?.posVenueName.orEmpty(),
            createdAt = longOf(decrypted["createdAt"]) ?: existing?.createdAt ?: remoteLm,
            lastModified = remoteLm,
            syncState = enumOrNull<AccountTransferSyncState>(stringOf(decrypted["syncState"]))
                ?: existing?.syncState
                ?: AccountTransferSyncState.CONFIRMED,
            firebaseOrgId = FirestoreApplyPolicy.orgIdToPersist(existing?.firebaseOrgId.orEmpty(), orgId),
        )
        if (existing == null) repository.insertAccountTransfer(remote) else repository.updateAccountTransfer(remote)
    }

    private suspend fun backfillBlankOrg(
        existingOrg: String,
        remoteOrg: String,
        persist: suspend (String) -> Unit,
    ) {
        if (FirestoreApplyPolicy.needsOrgBackfill(existingOrg, remoteOrg)) {
            persist(remoteOrg)
        }
    }

    private fun stringOf(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        else -> value.toString()
    }

    private fun longOf(value: Any?): Long? = when (value) {
        null -> null
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    private fun intOf(value: Any?): Int? = when (value) {
        null -> null
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun doubleOf(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun boolOf(value: Any?): Boolean? = when (value) {
        null -> null
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> null
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
