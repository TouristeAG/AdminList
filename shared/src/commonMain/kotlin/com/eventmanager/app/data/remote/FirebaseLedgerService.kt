package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountHolderKey
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.utils.AccountBalanceService

sealed class FirebaseLedgerResult {
    data class Confirmed(val transfer: AccountTransfer) : FirebaseLedgerResult()
    data class Pending(val transfer: AccountTransfer) : FirebaseLedgerResult()
    data class Rejected(val reason: String, val transfer: AccountTransfer) : FirebaseLedgerResult()
}

/**
 * Transactional ledger for Firebase mode: accounts/{holderKey} + transfers/{sourceReference}.
 */
class FirebaseLedgerService(
    private val repository: EventManagerRepository,
    private val settingsManager: SettingsManager,
    private val firestoreGateway: FirestoreGateway,
) {
    suspend fun commitTransfer(transfer: AccountTransfer, orgId: String? = null): FirebaseLedgerResult {
        val targetOrg = orgId?.trim()?.takeIf { it.isNotBlank() }
            ?: transfer.firebaseOrgId.trim().takeIf { it.isNotBlank() }
            ?: settingsManager.getFirebaseOrgId().trim().takeIf { !isFirebaseOrgAllSentinel(it) }
            ?: settingsManager.getFirebaseLastSingleOrgId().trim()
        val pending = stampLedgerTransfer(transfer, targetOrg, AccountTransferSyncState.PENDING)
        val existing = repository.getAccountTransferBySourceReference(transfer.sourceReference)
        val persisted = if (existing == null) {
            val rowId = repository.insertAccountTransfer(pending)
            pending.copy(id = rowId)
        } else {
            val withId = pending.copy(id = existing.id)
            repository.updateAccountTransfer(withId)
            withId
        }

        if (targetOrg.isBlank() || !firestoreGateway.isAvailable()) {
            return FirebaseLedgerResult.Pending(persisted)
        }

        val holderKey = AccountHolderKey(transfer.holderType, transfer.holderId).storageKey()
        val holderTransfers = repository.getTransfersForHolder(transfer.holderType, transfer.holderId)
            .filter { it.firebaseOrgId.isBlank() || it.firebaseOrgId == targetOrg }
        val balanceBefore = AccountBalanceService.computeBalance(
            transfer.holderType,
            transfer.holderId,
            holderTransfers.filter { it.sourceReference != transfer.sourceReference },
        )
        val newBalance = balanceBefore + transfer.amount
        val buffer = settingsManager.getPurchaseCreditBuffer()

        val ok = try {
            firestoreGateway.runLedgerTransaction(
                orgId = targetOrg,
                transfer = persisted,
                holderKey = holderKey,
                newBalance = newBalance,
                buffer = buffer,
            )
        } catch (e: Exception) {
            val rejected = stampLedgerTransfer(persisted, targetOrg, AccountTransferSyncState.REJECTED)
            repository.updateAccountTransfer(rejected)
            return FirebaseLedgerResult.Rejected(e.message ?: "Ledger transaction failed", rejected)
        }

        return if (ok) {
            val confirmed = stampLedgerTransfer(persisted, targetOrg, AccountTransferSyncState.CONFIRMED)
            repository.updateAccountTransfer(confirmed)
            FirebaseLedgerResult.Confirmed(confirmed)
        } else {
            val rejected = stampLedgerTransfer(persisted, targetOrg, AccountTransferSyncState.REJECTED)
            repository.updateAccountTransfer(rejected)
            FirebaseLedgerResult.Rejected("Insufficient balance after peer updates (buffer=$buffer)", rejected)
        }
    }
}
