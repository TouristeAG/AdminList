package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.AccountHolderKey
import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer

object AccountBalanceService {
    fun computeBalance(
        holderType: AccountHolderType,
        holderId: String,
        transfers: List<AccountTransfer>
    ): Double {
        val sum = transfers
            .asSequence()
            .filter { it.holderType == holderType && it.holderId == holderId }
            .sumOf { it.amount }
        return maxOf(0.0, sum)
    }

    fun computeAllBalances(transfers: List<AccountTransfer>): Map<AccountHolderKey, Double> {
        val sums = mutableMapOf<AccountHolderKey, Double>()
        for (transfer in transfers) {
            val key = AccountHolderKey(transfer.holderType, transfer.holderId)
            sums[key] = (sums[key] ?: 0.0) + transfer.amount
        }
        return sums.mapValues { (_, balance) -> maxOf(0.0, balance) }
    }

    fun patchBalance(
        current: Map<AccountHolderKey, Double>,
        transfer: AccountTransfer
    ): Map<AccountHolderKey, Double> {
        val key = AccountHolderKey(transfer.holderType, transfer.holderId)
        val updated = (current[key] ?: 0.0) + transfer.amount
        return current + (key to maxOf(0.0, updated))
    }
}
