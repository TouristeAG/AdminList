package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState

internal fun stampLedgerTransfer(
    transfer: AccountTransfer,
    targetOrg: String,
    syncState: AccountTransferSyncState,
): AccountTransfer = transfer.copy(
    syncState = syncState,
    firebaseOrgId = targetOrg.trim().ifBlank { transfer.firebaseOrgId },
)
