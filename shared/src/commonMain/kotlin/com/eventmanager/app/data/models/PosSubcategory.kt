package com.eventmanager.app.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Admin-defined refinement of a [SalesCategory] — for example "Alcool", "Sans alcool" or
 * "Consignes" inside [SalesCategory.BAR].
 *
 * Firebase-only: the catalogue rides on an institution setting and the per-product link is a
 * Firestore field, so the Google Sheets product contract stays untouched.
 */
data class PosSubcategory(
    val category: SalesCategory,
    val name: String,
)

/**
 * Serializes the admin-defined sub-category catalogue into the single string an institution
 * setting can hold, and keeps the add/remove rules in one place.
 */
object PosSubcategoryCatalog {

    const val MAX_NAME_LENGTH = 28
    const val MAX_PER_CATEGORY = 24

    /** Stored as plain strings so a category added in a future release cannot break decoding. */
    @Serializable
    private data class Entry(val category: String = "", val name: String = "")

    private val json = Json { ignoreUnknownKeys = true }
    private val entryListSerializer = ListSerializer(Entry.serializer())

    /** Collapses whitespace and trims, so " Sans   alcool " and "Sans alcool" are one entry. */
    fun normalizeName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    fun encode(subcategories: List<PosSubcategory>): String {
        if (subcategories.isEmpty()) return ""
        val entries = subcategories.map { Entry(it.category.name, it.name) }
        return json.encodeToString(entryListSerializer, entries)
    }

    fun decode(raw: String): List<PosSubcategory> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val entries = runCatching {
            json.decodeFromString(entryListSerializer, trimmed)
        }.getOrElse { return emptyList() }
        return entries.mapNotNull { entry ->
            val category = SalesCategory.entries.find { it.name == entry.category.trim().uppercase() }
                ?: return@mapNotNull null
            val name = normalizeName(entry.name)
            if (name.isEmpty()) null else PosSubcategory(category, name)
        }.distinctBy { it.category to it.name.lowercase() }
    }

    fun forCategory(catalog: List<PosSubcategory>, category: SalesCategory): List<PosSubcategory> =
        catalog.filter { it.category == category }

    /** Case-insensitive: "Alcool" and "alcool" are the same sub-category. */
    fun contains(catalog: List<PosSubcategory>, category: SalesCategory, name: String): Boolean {
        val normalized = normalizeName(name)
        return catalog.any { it.category == category && it.name.equals(normalized, ignoreCase = true) }
    }

    /** No-op when the name is blank, already present, or the category is already full. */
    fun add(catalog: List<PosSubcategory>, category: SalesCategory, name: String): List<PosSubcategory> {
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return catalog
        if (contains(catalog, category, normalized)) return catalog
        if (forCategory(catalog, category).size >= MAX_PER_CATEGORY) return catalog
        return catalog + PosSubcategory(category, normalized)
    }

    fun remove(catalog: List<PosSubcategory>, category: SalesCategory, name: String): List<PosSubcategory> {
        val normalized = normalizeName(name)
        return catalog.filterNot { it.category == category && it.name.equals(normalized, ignoreCase = true) }
    }

    /**
     * Sub-categories that actually have a product in [items] for [category]. The POS filter bar
     * hides empty ones, and hides itself entirely when nothing is left.
     */
    fun visibleFor(
        catalog: List<PosSubcategory>,
        category: SalesCategory,
        items: List<SalesSheetItem>,
    ): List<PosSubcategory> {
        val used = items
            .filter { SalesCategory.parseList(it.categories).contains(category) }
            .map { normalizeName(it.subcategory).lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return forCategory(catalog, category).filter { used.contains(it.name.lowercase()) }
    }
}

/** True when [subcategory] is the one selected in the POS filter bar (case-insensitive). */
fun SalesSheetItem.matchesSubcategory(subcategory: String?): Boolean {
    if (subcategory.isNullOrBlank()) return true
    return this.subcategory.trim().equals(subcategory.trim(), ignoreCase = true)
}
