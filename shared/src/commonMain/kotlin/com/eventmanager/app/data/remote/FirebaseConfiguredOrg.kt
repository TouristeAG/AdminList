package com.eventmanager.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class FirebaseConfiguredOrg(
    val orgId: String,
    val colorArgb: Long,
)

object FirebaseConfiguredOrgCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Distinct accent colors for org switcher (ARGB). */
    val paletteArgb: List<Long> = listOf(
        0xFFE53935L, // red
        0xFF1E88E5L, // blue
        0xFF43A047L, // green
        0xFFFB8C00L, // orange
        0xFF8E24AAL, // purple
        0xFF00ACC1L, // cyan
        0xFFFDD835L, // yellow
        0xFF6D4C41L, // brown
        0xFF546E7AL, // blue grey
        0xFFD81B60L, // pink
    )

    fun defaultColorForIndex(index: Int): Long =
        paletteArgb[index % paletteArgb.size]

    fun nextAvailableColor(used: Collection<Long>): Long {
        val usedSet = used.toSet()
        return paletteArgb.firstOrNull { it !in usedSet } ?: defaultColorForIndex(usedSet.size)
    }

    fun encode(orgs: List<FirebaseConfiguredOrg>): String =
        json.encodeToString(ListSerializer(FirebaseConfiguredOrg.serializer()), orgs)

    fun decode(raw: String): List<FirebaseConfiguredOrg> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(FirebaseConfiguredOrg.serializer()), raw.trim())
        }.getOrDefault(emptyList())
    }

    fun normalize(orgs: List<FirebaseConfiguredOrg>): List<FirebaseConfiguredOrg> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<FirebaseConfiguredOrg>()
        orgs.forEachIndexed { index, entry ->
            val id = entry.orgId.trim()
            if (id.isBlank() || id in seen) return@forEachIndexed
            seen += id
            val color = if (entry.colorArgb != 0L) {
                entry.colorArgb
            } else {
                defaultColorForIndex(index)
            }
            result += FirebaseConfiguredOrg(orgId = id, colorArgb = color)
        }
        require(result.isNotEmpty()) { "At least one Firebase org ID is required" }
        return result
    }

    fun migrateFromSingleOrgId(orgId: String): List<FirebaseConfiguredOrg> {
        val id = orgId.trim()
        require(id.isNotBlank())
        return listOf(FirebaseConfiguredOrg(orgId = id, colorArgb = defaultColorForIndex(0)))
    }
}
