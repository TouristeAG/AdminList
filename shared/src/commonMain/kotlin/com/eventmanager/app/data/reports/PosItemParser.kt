package com.eventmanager.app.data.reports

import com.eventmanager.app.data.models.SalesCategory
import com.eventmanager.app.data.models.SalesSheetItem

object PosItemParser {

    fun parsePrimaryCategory(categories: String): SalesCategory {
        val first = categories.split(",")
            .map { it.trim().uppercase() }
            .firstOrNull { it.isNotBlank() }
        return runCatching { SalesCategory.valueOf(first ?: "OTHER") }.getOrDefault(SalesCategory.OTHER)
    }

    fun categoryLookups(
        salesItems: List<SalesSheetItem>,
    ): Pair<Map<Long, SalesCategory>, Map<String, SalesCategory>> {
        val byId = salesItems.associate { it.id to parsePrimaryCategory(it.categories) }
        val byName = salesItems.associate { it.name.lowercase() to parsePrimaryCategory(it.categories) }
        return byId to byName
    }

    fun parsePosItemsJson(
        json: String,
        itemCategoryById: Map<Long, SalesCategory>,
        itemCategoryByName: Map<String, SalesCategory>,
    ): List<PosReportLineItem> {
        if (json.isBlank()) return emptyList()
        return json.split("|").mapNotNull { segment ->
            val parts = segment.split(":")
            if (parts.size < 4) return@mapNotNull null
            val itemId = parts[0].toLongOrNull() ?: 0L
            val name = parts[1]
            val price = parts[2].toDoubleOrNull() ?: 0.0
            val qty = parts[3].toIntOrNull() ?: 0
            if (qty <= 0) return@mapNotNull null
            val category = itemCategoryById[itemId]
                ?: itemCategoryByName[name.lowercase()]
                ?: SalesCategory.OTHER
            PosReportLineItem(
                itemId = itemId,
                name = name,
                unitPrice = price,
                quantity = qty,
                lineTotal = price * qty,
                category = category,
            )
        }
    }
}
