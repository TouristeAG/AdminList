package com.eventmanager.app.data.models

data class OrgScoped<T>(
    val orgId: String,
    val value: T,
)

data class OrgVenueKey(
    val orgId: String,
    val venueId: Long,
) {
    companion object {
        fun from(venue: VenueEntity): OrgVenueKey =
            OrgVenueKey(orgId = venue.firebaseOrgId, venueId = venue.id)
    }
}
