package com.eventmanager.app.data.models

/**
 * Deposit ("consigne") products. A product flagged [SalesSheetItem.isDeposit] shows up twice in
 * the POS grid: the product itself, and a return tile priced at the negative of it that pays the
 * deposit back onto the account.
 *
 * The return tile is synthetic — it is never a row in `sales_sheet_items`. It borrows the
 * product's id negated, which is what lets cart merging, grid keys and the ledger's
 * `posItemsJson` tell a purchase apart from a return without a second catalogue entry.
 */
object PosDeposit {

    fun isReturnId(itemId: Long?): Boolean = itemId != null && itemId < 0L

    fun productIdForReturn(returnItemId: Long): Long = -returnItemId

    /** Synthetic tile for handing [item] back: same category, sub-category and emoji, negated price. */
    fun returnItemFor(item: SalesSheetItem, returnName: String): SalesSheetItem = item.copy(
        id = -item.id,
        sheetsId = null,
        name = returnName,
        price = -item.price,
        hasDiscount = false,
    )

    /** True when every letter is uppercase, so "VERRE" gets "RETOUR VERRE" rather than "Retour VERRE". */
    fun isAllCaps(name: String): Boolean {
        val letters = name.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it.isUpperCase() }
    }

    /**
     * Return tile name for [item], picking between the normal and the all-caps translation of
     * "Return %1$s" so the casing follows the product's own.
     */
    fun returnNameFor(item: SalesSheetItem, returnNameFormat: String, allCapsFormat: String): String =
        String.format(if (isAllCaps(item.name)) allCapsFormat else returnNameFormat, item.name)

    /**
     * [items] in display order with a return tile inserted right after every deposit product, so
     * the pair always sits together in the grid.
     */
    fun expandForPos(
        items: List<SalesSheetItem>,
        returnNameFor: (SalesSheetItem) -> String,
    ): List<SalesSheetItem> = items.flatMap { item ->
        if (item.isDeposit && item.id > 0L) {
            listOf(item, returnItemFor(item, returnNameFor(item)))
        } else {
            listOf(item)
        }
    }
}
