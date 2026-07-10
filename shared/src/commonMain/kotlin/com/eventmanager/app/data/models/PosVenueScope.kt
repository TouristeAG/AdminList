package com.eventmanager.app.data.models

import com.eventmanager.app.data.reports.PosReportVenueScope

object PosVenueScope {
    const val GLOBAL = "Global"

    fun parseVenueList(raw: String): Set<String> =
        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun formatVenueList(venues: Set<String>): String =
        venues.joinToString(",")

    fun isItemAvailableAt(itemVenues: Set<String>, selectedVenue: String): Boolean {
        if (itemVenues.isEmpty()) return true
        return itemVenues.any { it.equals(selectedVenue, ignoreCase = true) }
    }

    fun matchesTransferVenue(
        transferVenue: String,
        scope: PosReportVenueScope,
        venueName: String? = null,
    ): Boolean = when (scope) {
        PosReportVenueScope.ALL -> true
        PosReportVenueScope.GLOBAL -> transferVenue == GLOBAL
        PosReportVenueScope.VENUE -> {
            val target = venueName.orEmpty()
            target.isNotEmpty() && transferVenue.equals(target, ignoreCase = true)
        }
    }

    fun normalizeSelectedVenue(stored: String?, activeVenueNames: Collection<String>): String {
        val value = stored?.trim().orEmpty().ifEmpty { GLOBAL }
        if (value == GLOBAL) return GLOBAL
        return activeVenueNames.firstOrNull { it.equals(value, ignoreCase = true) } ?: GLOBAL
    }

    fun venueFromJobReferenceKey(jobReferenceKey: String): String {
        val parts = jobReferenceKey.split("|")
        return parts.getOrNull(3)?.trim().orEmpty()
    }
}
