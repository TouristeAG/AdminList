package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountTransferType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DepositReturnPolicyTest {

    private val now = 1_700_000_000_000L
    private val glassId = 7L
    private val returnId = -7L

    private fun sale(
        posItemsJson: String,
        createdAt: Long = now - 60_000,
        syncState: AccountTransferSyncState = AccountTransferSyncState.CONFIRMED,
        type: AccountTransferType = AccountTransferType.POS_SALE,
    ) = AccountTransfer(
        holderType = AccountHolderType.GUEST,
        holderId = "g1",
        holderName = "Bob",
        amount = -2.0,
        type = type,
        sourceReference = "ref-$createdAt-$posItemsJson",
        posItemsJson = posItemsJson,
        createdAt = createdAt,
        syncState = syncState,
    )

    private fun returnLine(quantity: Int) = PosCartLine(
        itemId = returnId,
        name = "Retour Verre",
        unitPrice = -2.0,
        quantity = quantity,
    )

    @Test
    fun returnableCounts_countsPurchasesAndSubtractsReturns() {
        val transfers = listOf(
            sale("$glassId:Verre:2.0:3"),
            sale("$returnId:Retour Verre:-2.0:1"),
        )
        assertEquals(2, DepositReturnPolicy.returnableCounts(transfers, now)[glassId])
    }

    @Test
    fun returnableCounts_ignoresPurchasesOlderThanTheWindow() {
        val stale = now - DepositReturnPolicy.WINDOW_MS - 1
        val transfers = listOf(sale("$glassId:Verre:2.0:2", createdAt = stale))
        assertEquals(null, DepositReturnPolicy.returnableCounts(transfers, now)[glassId])
    }

    @Test
    fun returnableCounts_ignoresRejectedAndNonPosRows() {
        val transfers = listOf(
            sale("$glassId:Verre:2.0:2", syncState = AccountTransferSyncState.REJECTED),
            sale("$glassId:Verre:2.0:5", type = AccountTransferType.MANUAL_ADJUSTMENT),
        )
        assertEquals(null, DepositReturnPolicy.returnableCounts(transfers, now)[glassId])
    }

    @Test
    fun returnableCounts_neverGoesNegative() {
        val transfers = listOf(sale("$returnId:Retour Verre:-2.0:3"))
        assertEquals(0, DepositReturnPolicy.returnableCounts(transfers, now)[glassId])
    }

    @Test
    fun returnableCounts_readsQuantityPastColonsInTheName() {
        val transfers = listOf(sale("$glassId:Verre: grand:2.0:2"))
        assertEquals(2, DepositReturnPolicy.returnableCounts(transfers, now)[glassId])
    }

    @Test
    fun firstRefusal_allowsAReturnBackedByAPurchase() {
        val transfers = listOf(sale("$glassId:Verre:2.0:1"))
        assertNull(DepositReturnPolicy.firstRefusal(listOf(returnLine(1)), transfers, now))
    }

    @Test
    fun firstRefusal_blocksAReturnWithNoPurchaseAtAll() {
        val refusal = DepositReturnPolicy.firstRefusal(listOf(returnLine(1)), emptyList(), now)

        assertNotNull(refusal)
        assertEquals(glassId, refusal.productItemId)
        assertEquals("Retour Verre", refusal.returnName)
        assertEquals(0, refusal.allowed)
    }

    @Test
    fun firstRefusal_blocksASecondReturnBackedByASinglePurchase() {
        val transfers = listOf(sale("$glassId:Verre:2.0:1"))
        val refusal = DepositReturnPolicy.firstRefusal(listOf(returnLine(2)), transfers, now)

        assertNotNull(refusal)
        assertEquals(2, refusal.requested)
        assertEquals(1, refusal.allowed)
    }

    @Test
    fun firstRefusal_ignoresCartsWithoutReturns() {
        val purchase = PosCartLine(itemId = glassId, name = "Verre", unitPrice = 2.0, quantity = 4)
        assertNull(DepositReturnPolicy.firstRefusal(listOf(purchase), emptyList(), now))
    }
}
