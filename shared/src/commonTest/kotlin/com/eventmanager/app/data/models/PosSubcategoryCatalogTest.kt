package com.eventmanager.app.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosSubcategoryCatalogTest {

    private fun barItem(name: String, subcategory: String) = SalesSheetItem(
        name = name,
        price = 5.0,
        categories = SalesCategory.formatList(setOf(SalesCategory.BAR)),
        subcategory = subcategory,
    )

    @Test
    fun encodeDecode_roundTrips() {
        val catalog = listOf(
            PosSubcategory(SalesCategory.BAR, "Alcool"),
            PosSubcategory(SalesCategory.BAR, "Consignes"),
            PosSubcategory(SalesCategory.MERCH, "Textile"),
        )
        assertEquals(catalog, PosSubcategoryCatalog.decode(PosSubcategoryCatalog.encode(catalog)))
    }

    @Test
    fun decode_toleratesEmptyAndGarbage() {
        assertEquals(emptyList(), PosSubcategoryCatalog.decode(""))
        assertEquals(emptyList(), PosSubcategoryCatalog.decode("   "))
        assertEquals(emptyList(), PosSubcategoryCatalog.decode("not json"))
        assertEquals(emptyList(), PosSubcategoryCatalog.decode("""[{"category":"WAT","name":"x"}]"""))
    }

    @Test
    fun add_normalizesAndRejectsDuplicates() {
        var catalog = PosSubcategoryCatalog.add(emptyList(), SalesCategory.BAR, "  Sans   alcool ")
        assertEquals(listOf(PosSubcategory(SalesCategory.BAR, "Sans alcool")), catalog)

        catalog = PosSubcategoryCatalog.add(catalog, SalesCategory.BAR, "sans alcool")
        assertEquals(1, catalog.size)

        catalog = PosSubcategoryCatalog.add(catalog, SalesCategory.BAR, "   ")
        assertEquals(1, catalog.size)

        // Same name under a different general category is a distinct sub-category.
        catalog = PosSubcategoryCatalog.add(catalog, SalesCategory.MERCH, "Sans alcool")
        assertEquals(2, catalog.size)
    }

    @Test
    fun remove_isCaseInsensitiveAndScopedToCategory() {
        val catalog = listOf(
            PosSubcategory(SalesCategory.BAR, "Alcool"),
            PosSubcategory(SalesCategory.MERCH, "Alcool"),
        )
        val removed = PosSubcategoryCatalog.remove(catalog, SalesCategory.BAR, "alcool")
        assertEquals(listOf(PosSubcategory(SalesCategory.MERCH, "Alcool")), removed)
    }

    @Test
    fun visibleFor_hidesSubcategoriesWithoutProducts() {
        val catalog = listOf(
            PosSubcategory(SalesCategory.BAR, "Alcool"),
            PosSubcategory(SalesCategory.BAR, "Consignes"),
        )
        val items = listOf(barItem("Bière", "alcool"), barItem("Chips", ""))
        val visible = PosSubcategoryCatalog.visibleFor(catalog, SalesCategory.BAR, items)
        assertEquals(listOf(PosSubcategory(SalesCategory.BAR, "Alcool")), visible)
    }

    /** No products tagged at all: the POS filter bar must stay hidden. */
    @Test
    fun visibleFor_isEmptyWhenNothingIsTagged() {
        val catalog = listOf(PosSubcategory(SalesCategory.BAR, "Alcool"))
        val items = listOf(barItem("Bière", ""), barItem("Chips", ""))
        assertTrue(PosSubcategoryCatalog.visibleFor(catalog, SalesCategory.BAR, items).isEmpty())
    }

    @Test
    fun matchesSubcategory_treatsNoFilterAsEverything() {
        val tagged = barItem("Bière", "Alcool")
        val untagged = barItem("Chips", "")
        assertTrue(tagged.matchesSubcategory(null))
        assertTrue(untagged.matchesSubcategory(null))
        assertTrue(tagged.matchesSubcategory("alcool"))
        assertFalse(untagged.matchesSubcategory("Alcool"))
    }
}
