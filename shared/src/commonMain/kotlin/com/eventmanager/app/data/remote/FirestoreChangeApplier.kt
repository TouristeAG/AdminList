package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountHolderType
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
import kotlinx.coroutines.flow.first

/**
 * Applies a remote Firestore document change into Room using lastModified LWW.
 * Ignores echo is handled by callers via [sourceDeviceId] before invoking this.
 */
object FirestoreChangeApplier {

    suspend fun apply(change: FirestoreRemoteChange, repository: EventManagerRepository) {
        val orgId = change.orgId
        val data = change.data
        if (change.deleted || data == null) {
            applyDelete(orgId, change.collection, change.documentId, repository)
            return
        }
        when (change.collection) {
            "guests" -> applyGuest(orgId, change.documentId, data, repository)
            "volunteers" -> applyVolunteer(orgId, change.documentId, data, repository)
            "jobs" -> applyJob(orgId, change.documentId, data, repository)
            "jobTypeConfigs" -> applyJobType(orgId, change.documentId, data, repository)
            "venues" -> applyVenue(orgId, change.documentId, data, repository)
            "salesItems" -> applySalesItem(orgId, change.documentId, data, repository)
            "transfers" -> applyTransfer(orgId, change.documentId, data, repository)
            else -> Unit
        }
    }

    private suspend fun applyDelete(
        orgId: String,
        collection: String,
        documentId: String,
        repository: EventManagerRepository,
    ) {
        when (collection) {
            "guests" -> repository.getGuestByNanoIdAndOrg(documentId, orgId)?.let { repository.deleteGuest(it) }
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
        val remoteLm = longOf(data["lastModified"]) ?: return
        val existing = repository.getGuestByNanoIdAndOrg(docId, orgId)
        if (existing != null && existing.lastModified >= remoteLm) return
        val remote = Guest(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            nanoId = docId,
            name = stringOf(data["name"]).orEmpty(),
            lastNameAbbreviation = stringOf(data["lastNameAbbreviation"]).orEmpty(),
            email = stringOf(data["email"]).orEmpty(),
            phoneNumber = stringOf(data["phone"]).orEmpty(),
            invitations = intOf(data["invitations"]) ?: 0,
            venueName = stringOf(data["venueName"]).orEmpty(),
            notes = stringOf(data["notes"]).orEmpty(),
            isVolunteerBenefit = boolOf(data["isVolunteerBenefit"]) ?: false,
            volunteerId = stringOf(data["volunteerId"]),
            lastModified = remoteLm,
            isTemporaryGuest = boolOf(data["isTemporaryGuest"]) ?: false,
            temporaryArtistName = stringOf(data["temporaryArtistName"]).orEmpty(),
            temporaryEventDate = longOf(data["temporaryEventDate"]),
            temporaryContactPhone = stringOf(data["temporaryContactPhone"]).orEmpty(),
            nfcCardUid = stringOf(data["nfcCardUid"]).orEmpty(),
            isAdmin = boolOf(data["isAdmin"]) ?: false,
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertGuest(remote) else repository.updateGuest(remote)
    }

    private suspend fun applyVolunteer(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val remoteLm = longOf(data["lastModified"]) ?: return
        val existing = repository.getVolunteerById(docId)?.takeIf { it.firebaseOrgId == orgId }
        if (existing != null && existing.lastModified >= remoteLm) return
        val remote = Volunteer(
            id = docId,
            sheetsId = existing?.sheetsId,
            name = stringOf(data["name"]).orEmpty(),
            lastNameAbbreviation = stringOf(data["lastNameAbbreviation"]).orEmpty(),
            email = stringOf(data["email"]).orEmpty(),
            phoneNumber = stringOf(data["phone"]).orEmpty(),
            dateOfBirth = stringOf(data["dateOfBirth"]).orEmpty(),
            gender = enumOrNull<Gender>(stringOf(data["gender"])),
            currentRank = enumOrNull<VolunteerRank>(stringOf(data["currentRank"])),
            isActive = boolOf(data["isActive"]) ?: true,
            lastShiftDate = longOf(data["lastShiftDate"]),
            lastModified = remoteLm,
            nfcCardUid = stringOf(data["nfcCardUid"]).orEmpty(),
            isAdmin = boolOf(data["isAdmin"]) ?: false,
            firebaseOrgId = orgId,
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
        val existing = repository.getJobByJobNanoId(docId)?.takeIf { it.firebaseOrgId == orgId }
        if (existing != null && existing.lastModified >= remoteLm) return
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
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertJob(remote) else repository.updateJob(remote)
    }

    private suspend fun applyJobType(
        orgId: String,
        docId: String,
        data: Map<String, Any?>,
        repository: EventManagerRepository,
    ) {
        val remoteLm = longOf(data["lastModified"]) ?: return
        val existing = repository.getJobTypeConfigByNameAndOrg(docId, orgId)
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
        val remoteLm = longOf(data["lastModified"])
            ?: longOf(data["peopleCounterLastModified"])
            ?: return
        val existing = repository.getVenueByNameAndOrg(docId, orgId)
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
        val remoteLm = longOf(data["lastModified"]) ?: return
        val existing = repository.getSalesSheetItemByNameAndOrg(docId, orgId)
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
        val remoteLm = longOf(data["lastModified"]) ?: longOf(data["createdAt"]) ?: return
        val existing = repository.getAccountTransferBySourceReference(docId)?.takeIf { it.firebaseOrgId == orgId }
        if (existing != null && existing.lastModified >= remoteLm) return
        val holderType = enumOrNull<AccountHolderType>(stringOf(data["holderType"]))
            ?: existing?.holderType
            ?: return
        val remote = AccountTransfer(
            id = existing?.id ?: 0,
            sheetsId = existing?.sheetsId,
            transferId = stringOf(data["transferId"]) ?: existing?.transferId
                ?: com.eventmanager.app.data.utils.NanoIdGenerator.generateGuestId(),
            sourceReference = docId,
            holderType = holderType,
            holderId = stringOf(data["holderId"]) ?: existing?.holderId.orEmpty(),
            holderName = stringOf(data["holderName"]) ?: existing?.holderName.orEmpty(),
            amount = doubleOf(data["amount"]) ?: existing?.amount ?: 0.0,
            type = enumOrNull<AccountTransferType>(stringOf(data["type"]))
                ?: existing?.type
                ?: AccountTransferType.MANUAL_ADJUSTMENT,
            currencyCode = stringOf(data["currencyCode"]) ?: existing?.currencyCode ?: "CHF",
            description = stringOf(data["description"]) ?: existing?.description.orEmpty(),
            jobReferenceKey = stringOf(data["jobReferenceKey"]) ?: existing?.jobReferenceKey.orEmpty(),
            jobTypeName = stringOf(data["jobTypeName"]) ?: existing?.jobTypeName.orEmpty(),
            jobDate = longOf(data["jobDate"]) ?: existing?.jobDate,
            creditAmountPaid = doubleOf(data["creditAmountPaid"]) ?: existing?.creditAmountPaid,
            cashAmountPaid = doubleOf(data["cashAmountPaid"]) ?: existing?.cashAmountPaid,
            posBarDiscountPercent = intOf(data["posBarDiscountPercent"]) ?: existing?.posBarDiscountPercent,
            posItemsJson = stringOf(data["posItemsJson"]) ?: existing?.posItemsJson.orEmpty(),
            posVenueName = stringOf(data["posVenueName"]) ?: existing?.posVenueName.orEmpty(),
            createdAt = longOf(data["createdAt"]) ?: existing?.createdAt ?: remoteLm,
            lastModified = remoteLm,
            syncState = enumOrNull<AccountTransferSyncState>(stringOf(data["syncState"]))
                ?: existing?.syncState
                ?: AccountTransferSyncState.CONFIRMED,
            firebaseOrgId = orgId,
        )
        if (existing == null) repository.insertAccountTransfer(remote) else repository.updateAccountTransfer(remote)
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
