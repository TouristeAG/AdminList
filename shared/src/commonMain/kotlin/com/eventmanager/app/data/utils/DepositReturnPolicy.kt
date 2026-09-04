package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.PosDeposit

/**
 * Guards deposit returns. A "Retour X" line only pays out when the very same account bought an X
 * within [WINDOW_MS], and each purchase can be handed back exactly once — two glasses returned
 * need two glasses bought. Without this, ringing up returns alone would let anyone credit their
 * own account indefinitely.
 */
object DepositReturnPolicy {

    const val WINDOW_MS: Long = 24L * 60 * 60 * 1000

    data class Refusal(
        val productItemId: Long,
        /** Name of the refused return line, e.g. "Retour Verre" — used verbatim in the message. */
        val returnName: String,
        val requested: Int,
        val allowed: Int,
    )

    /** Deposit product id → how many of it this holder may still hand back right now. */
    fun returnableCounts(transfers: List<AccountTransfer>, now: Long): Map<Long, Int> {
        val since = now - WINDOW_MS
        val net = mutableMapOf<Long, Int>()
        transfers.asSequence()
            .filter { it.type == AccountTransferType.POS_SALE }
            .filter { it.syncState != AccountTransferSyncState.REJECTED }
            .filter { it.createdAt in since..now }
            .forEach { transfer ->
                ledgerLines(transfer.posItemsJson).forEach { (itemId, quantity) ->
                    if (itemId > 0L) {
                        net[itemId] = (net[itemId] ?: 0) + quantity
                    } else {
                        val productId = PosDeposit.productIdForReturn(itemId)
                        net[productId] = (net[productId] ?: 0) - quantity
                    }
                }
            }
        return net.mapValues { (_, count) -> count.coerceAtLeast(0) }
    }

    /** The first return line [cart] is not entitled to, or null when the whole cart is allowed. */
    fun firstRefusal(
        cart: List<PosCartLine>,
        transfers: List<AccountTransfer>,
        now: Long,
    ): Refusal? {
        val returnLines = cart.filter { PosDeposit.isReturnId(it.itemId) }
        if (returnLines.isEmpty()) return null
        val returnable = returnableCounts(transfers, now)
        return returnLines
            .groupBy { PosDeposit.productIdForReturn(it.itemId!!) }
            .entries
            .firstNotNullOfOrNull { (productId, lines) ->
                val requested = lines.sumOf { it.quantity }
                val allowed = returnable[productId] ?: 0
                if (requested <= allowed) {
                    null
                } else {
                    Refusal(productId, lines.first().name, requested, allowed)
                }
            }
    }

    /**
     * `itemId:name:price:quantity|…` as written by [AccountCreditService]. Product names may
     * themselves contain `:`, so quantity is read off the end rather than by index.
     */
    private fun ledgerLines(posItemsJson: String): List<Pair<Long, Int>> {
        if (posItemsJson.isBlank()) return emptyList()
        return posItemsJson.split("|").mapNotNull { segment ->
            val parts = segment.split(":")
            if (parts.size < 4) return@mapNotNull null
            val itemId = parts.first().toLongOrNull() ?: return@mapNotNull null
            val quantity = parts.last().toIntOrNull() ?: return@mapNotNull null
            if (itemId == 0L || quantity <= 0) null else itemId to quantity
        }
    }
}
