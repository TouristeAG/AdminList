package com.eventmanager.app.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosDepositTest {

    private val returnNameFormat = "Retour %1\$s"
    private val allCapsFormat = "RETOUR %1\$s"

    private fun product(name: String, isDeposit: Boolean = true, id: Long = 7L) = SalesSheetItem(
        id = id,
        name = name,
        price = 2.0,
        isDeposit = isDeposit,
        categories = SalesCategory.formatList(setOf(SalesCategory.BAR)),
        subcategory = "Consignes",
        emoji = "🥃",
    )

    @Test
    fun returnName_followsTheProductCasing() {
        assertEquals("Retour Verre", PosDeposit.returnNameFor(product("Verre"), returnNameFormat, allCapsFormat))
        assertEquals("RETOUR VERRE", PosDeposit.returnNameFor(product("VERRE"), returnNameFormat, allCapsFormat))
        assertEquals("Retour verre", PosDeposit.returnNameFor(product("verre"), returnNameFormat, allCapsFormat))
        assertEquals("Retour Verre 33cl", PosDeposit.returnNameFor(product("Verre 33cl"), returnNameFormat, allCapsFormat))
    }

    @Test
    fun isAllCaps_ignoresDigitsAndPunctuationButNeedsALetter() {
        assertTrue(PosDeposit.isAllCaps("VERRE 33CL"))
        assertFalse(PosDeposit.isAllCaps("Verre 33CL"))
        assertFalse(PosDeposit.isAllCaps("33"))
    }

    @Test
    fun returnItem_mirrorsTheProductWithANegatedPrice() {
        val returnItem = PosDeposit.returnItemFor(product("Verre"), "Retour Verre")

        assertEquals(-7L, returnItem.id)
        assertEquals(-2.0, returnItem.price)
        assertEquals("🥃", returnItem.emoji)
        assertEquals("Consignes", returnItem.subcategory)
        assertEquals(product("Verre").categories, returnItem.categories)
        assertFalse(returnItem.hasDiscount)
    }

    @Test
    fun expandForPos_insertsTheReturnRightAfterItsProduct() {
        val items = listOf(
            product("Bière", isDeposit = false, id = 1L),
            product("Verre", id = 7L),
            product("Chips", isDeposit = false, id = 9L),
        )
        val expanded = PosDeposit.expandForPos(items) { "Retour ${it.name}" }

        assertEquals(listOf("Bière", "Verre", "Retour Verre", "Chips"), expanded.map { it.name })
    }

    @Test
    fun returnIds_roundTrip() {
        assertTrue(PosDeposit.isReturnId(-7L))
        assertFalse(PosDeposit.isReturnId(7L))
        assertFalse(PosDeposit.isReturnId(null))
        assertEquals(7L, PosDeposit.productIdForReturn(-7L))
    }
}
