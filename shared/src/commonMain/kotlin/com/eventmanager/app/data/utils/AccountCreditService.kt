package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.PosVenueScope
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.account_reversal_description
import com.eventmanager.app.resources.pos_pay_cash_card
import com.eventmanager.app.resources.pos_sale_complete_message
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString

data class PosCartLine(
    val itemId: Long?,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val emoji: String = "",
    val barDiscountEligible: Boolean = false,
)

data class PosPaymentBreakdown(
    val creditPaid: Double,
    val cashOrCardDue: Double,
    val cashOrCardBeforeDiscount: Double,
) {
    val effectiveTotal: Double get() = creditPaid + cashOrCardDue
}

/** Credits are charged at full price; bar discount applies only to the cash/card portion of eligible lines. */
fun computePosPayment(
    cart: List<PosCartLine>,
    accountBalance: Double,
    barDiscountPercent: Int,
): PosPaymentBreakdown {
    if (cart.isEmpty()) {
        return PosPaymentBreakdown(0.0, 0.0, 0.0)
    }

    var creditRemaining = accountBalance.coerceAtLeast(0.0)
    var creditPaid = 0.0
    val unpaidSegments = mutableListOf<Pair<Double, Boolean>>()

    for (line in cart) {
        val lineTotal = line.unitPrice * line.quantity
        val fromCredit = minOf(creditRemaining, lineTotal)
        creditRemaining -= fromCredit
        creditPaid += fromCredit

        val unpaidAtFullPrice = lineTotal - fromCredit
        if (unpaidAtFullPrice > 0.0) {
            unpaidSegments += unpaidAtFullPrice to line.barDiscountEligible
        }
    }

    val cashBeforeDiscount = unpaidSegments.sumOf { it.first }
    val discountRate = barDiscountPercent.coerceIn(0, 100) / 100.0
    val cashDue = unpaidSegments.sumOf { (unpaid, eligible) ->
        if (eligible && discountRate > 0.0) {
            unpaid * (1.0 - discountRate)
        } else {
            unpaid
        }
    }

    return PosPaymentBreakdown(
        creditPaid = creditPaid,
        cashOrCardDue = cashDue,
        cashOrCardBeforeDiscount = cashBeforeDiscount,
    )
}

/**
 * Net ledger movement for a POS sale on the holder's credit account.
 *
 * - [PosPaymentBreakdown.creditPaid] is always debited at full price.
 * - When the holder had **no credits to apply** ([creditPaid] == 0) and pays cash/card for
 *   the shortfall, debt is the credit-priced remainder minus the discounted cash actually paid
 *   (`cashOrCardBeforeDiscount - cashOrCardDue`). That way a future shift credit is not reduced
 *   again for money already paid in cash.
 * - When credits already cover part of the basket, cash settles the remainder (incl. bar
 *   discount) and does not create extra debt beyond the credits debited.
 */
fun computePosLedgerAmount(payment: PosPaymentBreakdown): Double {
    val cashDebt = if (payment.creditPaid == 0.0 && payment.cashOrCardDue > 0.0) {
        (payment.cashOrCardBeforeDiscount - payment.cashOrCardDue).coerceAtLeast(0.0)
    } else {
        0.0
    }
    return -(payment.creditPaid + cashDebt)
}

data class PosSaleResult(
    val success: Boolean,
    val totalAmount: Double,
    val creditPaid: Double,
    val cashOrCardDue: Double,
    val cashOrCardBeforeDiscount: Double = cashOrCardDue,
    val barDiscountPercent: Int = 0,
    val remainingBalance: Double,
    val message: String
)

class AccountCreditService(
    private val repository: EventManagerRepository,
    private val currencyProvider: () -> String
) {
    suspend fun applyShiftCredits(
        job: Job,
        volunteer: Volunteer,
        jobTypeConfigs: List<JobTypeConfig>,
        offsetHours: Int
    ): List<AccountTransfer> {
        if (!ShiftCreditCalculator.isJobDayReached(job, offsetHours)) return emptyList()
        val volunteerJobs = repository.getJobsByVolunteer(job.volunteerId).first()
        val entries = ShiftCreditCalculator.creditsForAddedJob(job, volunteerJobs, jobTypeConfigs, offsetHours)
        return insertCreditEntries(entries, volunteer)
    }

    suspend fun evaluatePendingShiftCredits(
        jobTypeConfigs: List<JobTypeConfig>,
        offsetHours: Int,
        volunteerId: String? = null
    ): List<AccountTransfer> {
        val allJobs = if (volunteerId != null) {
            repository.getJobsByVolunteer(volunteerId).first()
        } else {
            repository.getAllJobs().first()
        }
        val volunteersById = repository.getAllVolunteers().first().associateBy { it.id }
        val jobsByVolunteer = if (volunteerId != null) {
            mapOf(volunteerId to allJobs)
        } else {
            allJobs.groupBy { it.volunteerId }
        }
        val created = mutableListOf<AccountTransfer>()
        val now = System.currentTimeMillis()

        for ((volId, volJobs) in jobsByVolunteer) {
            val volunteer = volunteersById[volId] ?: continue
            for (job in volJobs) {
                val entries = ShiftCreditCalculator.creditsForJob(
                    job, volJobs, jobTypeConfigs, offsetHours, now
                )
                created += insertCreditEntries(entries, volunteer)
            }
        }
        return created
    }

    private suspend fun insertCreditEntries(
        entries: List<ShiftCreditEntry>,
        volunteer: Volunteer
    ): List<AccountTransfer> {
        val created = mutableListOf<AccountTransfer>()
        for (entry in entries) {
            if (repository.getAccountTransferBySourceReference(entry.sourceReference) != null) continue
            val transfer = AccountTransfer(
                holderType = AccountHolderType.VOLUNTEER,
                holderId = volunteer.id,
                holderName = volunteer.name,
                amount = entry.amount,
                currencyCode = currencyProvider(),
                type = AccountTransferType.SHIFT_CREDIT,
                sourceReference = entry.sourceReference,
                jobReferenceKey = entry.jobReferenceKey,
                jobTypeName = entry.jobTypeName,
                jobDate = entry.jobDate,
                description = entry.description,
                posVenueName = PosVenueScope.venueFromJobReferenceKey(entry.jobReferenceKey),
            )
            repository.insertAccountTransfer(transfer)
            created += transfer
        }
        return created
    }

    suspend fun reverseShiftCredits(
        job: Job,
        volunteer: Volunteer,
        jobTypeConfigs: List<JobTypeConfig>,
        offsetHours: Int
    ): List<AccountTransfer> {
        val volunteerJobs = repository.getJobsByVolunteer(job.volunteerId).first()
        val sourceRefs = ShiftCreditCalculator.sourceReferencesForRemovedJob(job, volunteerJobs, jobTypeConfigs, offsetHours)
        val created = mutableListOf<AccountTransfer>()
        val currentBalance = AccountBalanceService.computeBalance(
            AccountHolderType.VOLUNTEER,
            volunteer.id,
            repository.getAllAccountTransfersOnce()
        )
        var remainingBalance = currentBalance

        for (sourceRef in sourceRefs) {
            val original = repository.getAccountTransferBySourceReference(sourceRef) ?: continue
            val reversalRef = "reversal:$sourceRef"
            if (repository.getAccountTransferBySourceReference(reversalRef) != null) continue
            val reversalAmount = -minOf(original.amount, remainingBalance)
            if (reversalAmount == 0.0) continue
            remainingBalance = maxOf(0.0, remainingBalance + reversalAmount)
            val transfer = AccountTransfer(
                holderType = AccountHolderType.VOLUNTEER,
                holderId = volunteer.id,
                holderName = volunteer.name,
                amount = reversalAmount,
                currencyCode = currencyProvider(),
                type = AccountTransferType.SHIFT_REVERSAL,
                sourceReference = reversalRef,
                jobReferenceKey = original.jobReferenceKey,
                jobTypeName = original.jobTypeName,
                jobDate = original.jobDate,
                description = getString(Res.string.account_reversal_description, original.description),
                posVenueName = original.posVenueName.ifBlank {
                    PosVenueScope.venueFromJobReferenceKey(original.jobReferenceKey)
                },
            )
            repository.insertAccountTransfer(transfer)
            created += transfer
        }
        return created
    }

    suspend fun applyManualAdjustment(
        holderType: AccountHolderType,
        holderId: String,
        holderName: String,
        amount: Double,
        note: String
    ): AccountTransfer {
        val transfer = AccountTransfer(
            holderType = holderType,
            holderId = holderId,
            holderName = holderName,
            amount = amount,
            currencyCode = currencyProvider(),
            type = AccountTransferType.MANUAL_ADJUSTMENT,
            sourceReference = "manual:${System.currentTimeMillis()}:${holderId}:${amount}",
            description = note
        )
        repository.insertAccountTransfer(transfer)
        return transfer
    }

    suspend fun completePosSale(
        holderType: AccountHolderType,
        holderId: String,
        holderName: String,
        cart: List<PosCartLine>,
        barDiscountPercent: Int = 0,
        posVenueName: String = PosVenueScope.GLOBAL,
    ): PosSaleResult {
        val balance = AccountBalanceService.computeBalance(
            holderType,
            holderId,
            repository.getAllAccountTransfersOnce()
        )
        val payment = computePosPayment(cart, balance, barDiscountPercent)
        val creditPaid = payment.creditPaid
        val cashDue = payment.cashOrCardDue
        val barDiscountApplied = barDiscountPercent.takeIf {
            it > 0 && cashDue > 0 && payment.cashOrCardBeforeDiscount > cashDue
        }
        val ledgerAmount = computePosLedgerAmount(payment)

        if (creditPaid > 0 || cashDue > 0) {
            val itemsSummary = cart.joinToString("; ") { "${it.quantity}x ${it.name}" }
            val posJson = cart.joinToString("|") { "${it.itemId ?: 0}:${it.name}:${it.unitPrice}:${it.quantity}" }
            val transfer = AccountTransfer(
                holderType = holderType,
                holderId = holderId,
                holderName = holderName,
                amount = ledgerAmount,
                currencyCode = currencyProvider(),
                type = AccountTransferType.POS_SALE,
                sourceReference = "pos:${System.currentTimeMillis()}:$holderId",
                description = itemsSummary,
                creditAmountPaid = creditPaid.takeIf { it > 0 },
                cashAmountPaid = cashDue.takeIf { it > 0 },
                posBarDiscountPercent = barDiscountApplied,
                posItemsJson = posJson,
                posVenueName = posVenueName,
            )
            repository.insertAccountTransfer(transfer)
        }

        val remainingBalance = AccountBalanceService.computeBalance(
            holderType,
            holderId,
            repository.getAllAccountTransfersOnce()
        )

        return PosSaleResult(
            success = true,
            totalAmount = payment.effectiveTotal,
            creditPaid = creditPaid,
            cashOrCardDue = cashDue,
            cashOrCardBeforeDiscount = payment.cashOrCardBeforeDiscount,
            barDiscountPercent = barDiscountPercent,
            remainingBalance = remainingBalance,
            message = if (cashDue > 0) {
                getString(Res.string.pos_pay_cash_card, formatMoney(cashDue, currencyProvider()))
            } else {
                getString(Res.string.pos_sale_complete_message)
            }
        )
    }
}
