package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.Guest

/**
 * Volunteer-benefit guest rows must keep their Room/Firestore identity ([Guest.nanoId],
 * [Guest.firebaseOrgId]) across recalculation. Building a fresh [Guest] defaults a new nanoId
 * and blank org, which forks the Firestore document and hides the local row.
 */
object VolunteerBenefitGuestMerge {
    fun prepareForInsert(computed: Guest, orgId: String): Guest {
        val org = computed.firebaseOrgId.trim().ifBlank { orgId.trim() }
        return computed.copy(
            firebaseOrgId = org,
            isVolunteerBenefit = true,
        )
    }

    fun prepareForUpdate(existing: Guest, computed: Guest): Guest {
        val org = existing.firebaseOrgId.trim().ifBlank { computed.firebaseOrgId.trim() }
        return existing.copy(
            name = computed.name,
            lastNameAbbreviation = computed.lastNameAbbreviation,
            invitations = computed.invitations,
            notes = computed.notes,
            nfcCardUid = computed.nfcCardUid,
            venueName = computed.venueName.ifBlank { existing.venueName },
            isVolunteerBenefit = true,
            volunteerId = existing.volunteerId ?: computed.volunteerId,
            firebaseOrgId = org,
            lastModified = maxOf(existing.lastModified, computed.lastModified, System.currentTimeMillis()),
        )
    }
}
