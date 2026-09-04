package com.eventmanager.app.data.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountCreditServiceTest {

    private fun line(
        price: Double,
        qty: Int = 1,
        eligible: Boolean = true,
    ) = PosCartLine(
        itemId = 1L,
        name = "Beer",
        unitPrice = price,
        quantity = qty,
        barDiscountEligible = eligible,
    )

    @Test
    fun computePosPayment_appliesCreditBeforeBarDiscount() {
        val cart = listOf(line(14.0))
        val payment = computePosPayment(cart, accountBalance = 6.0, barDiscountPercent = 50)

        assertEquals(6.0, payment.creditPaid, 1e-9)
        assertEquals(8.0, payment.cashOrCardBeforeDiscount, 1e-9)
        assertEquals(4.0, payment.cashOrCardDue, 1e-9)
        assertEquals(10.0, payment.effectiveTotal, 1e-9)
    }

    @Test
    fun computePosPayment_noCredit_discountsFullEligibleCart() {
        val cart = listOf(line(14.0))
        val payment = computePosPayment(cart, accountBalance = 0.0, barDiscountPercent = 50)

        assertEquals(0.0, payment.creditPaid, 1e-9)
        assertEquals(14.0, payment.cashOrCardBeforeDiscount, 1e-9)
        assertEquals(7.0, payment.cashOrCardDue, 1e-9)
    }

    @Test
    fun computePosPayment_multiLineFifoCreditThenDiscount() {
        val cart = listOf(
            line(10.0, eligible = true),
            line(4.0, eligible = true),
        )
        val payment = computePosPayment(cart, accountBalance = 6.0, barDiscountPercent = 50)

        assertEquals(6.0, payment.creditPaid, 1e-9)
        assertEquals(8.0, payment.cashOrCardBeforeDiscount, 1e-9)
        assertEquals(4.0, payment.cashOrCardDue, 1e-9)
    }

    @Test
    fun computePosLedgerAmount_partialCredit_noExtraCashDebt() {
        val payment = computePosPayment(listOf(line(14.0)), accountBalance = 6.0, barDiscountPercent = 50)
        assertEquals(-6.0, computePosLedgerAmount(payment), 1e-9)
    }

    @Test
    fun computePosLedgerAmount_fullCashWithDiscount_recordsCashDebt() {
        val payment = computePosPayment(listOf(line(14.0)), accountBalance = 0.0, barDiscountPercent = 50)
        assertEquals(-7.0, computePosLedgerAmount(payment), 1e-9)
    }
}
