package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.Guest

/**
 * Volunteer-benefit guest rows must keep their Room/Firestore identity ([Guest.nanoId],
 * [Guest.firebaseOrgId]) across recalculation. Building a fresh [Guest] defaults a new nanoId
 * and blank org, which forks the Firestore document and hides the local row.
 *
 * Identity is [Guest.volunteerId] (optionally scoped by org). Matching by name is only a
 * fallback for legacy rows that never stored a volunteerId — using name as the primary key
 * created extra rows whenever the displayed name drifted.
 */
object VolunteerBenefitGuestMerge {
    data class Diff(
        val toInsert: List<Guest>,
        val toUpdate: List<Guest>,
        val toDelete: List<Guest>,
    )

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
            volunteerId = existing.volunteerId?.trim()?.takeIf { it.isNotEmpty() } ?: computed.volunteerId,
            firebaseOrgId = org,
            lastModified = maxOf(existing.lastModified, computed.lastModified, System.currentTimeMillis()),
        )
    }

    fun pickCanonical(group: List<Guest>): Guest? {
        if (group.isEmpty()) return null
        return group.maxWithOrNull(
            compareBy<Guest> { it.lastModified }
                .thenBy { if (it.id > 0L) 1 else 0 }
                .thenBy { it.nanoId },
        )
    }

    fun isSameBenefitRow(a: Guest, b: Guest): Boolean {
        if (a.id > 0L && b.id > 0L) return a.id == b.id
        return a.nanoId.isNotBlank() && a.nanoId == b.nanoId
    }

    /**
     * One visible benefit row per volunteer. Extra forks (new nanoId on each recalc / remote echo)
     * are dropped; the newest row is kept.
     */
    fun collapseDuplicates(guests: List<Guest>): List<Guest> {
        val benefits = guests.filter { it.isVolunteerBenefit }
        val others = guests.filter { !it.isVolunteerBenefit }
        if (benefits.isEmpty()) return guests
        val collapsed = benefits.groupBy { benefitIdentityKey(it) }.values.map { group ->
            pickCanonical(group) ?: group.first()
        }
        return others + collapsed
    }

    fun benefitIdentityKey(guest: Guest): String {
        val volId = guest.volunteerId?.trim().orEmpty()
        if (volId.isNotEmpty()) return "vol:$volId"
        val org = guest.firebaseOrgId.trim()
        return "name:$org\u0000${guest.name.trim().lowercase()}\u0000${guest.lastNameAbbreviation.trim().lowercase()}"
    }

    fun diff(existing: List<Guest>, computed: List<Guest>, orgIdForInsert: String): Diff {
        val existingBenefits = existing.filter { it.isVolunteerBenefit }
        val computedByVol = LinkedHashMap<String, Guest>()
        for (guest in computed.filter { it.isVolunteerBenefit }) {
            val volId = guest.volunteerId?.trim().orEmpty()
            if (volId.isEmpty()) continue
            computedByVol[volId] = guest
        }

        val existingByVol = existingBenefits
            .filter { !it.volunteerId.isNullOrBlank() }
            .groupBy { it.volunteerId!!.trim() }
        val unmatchedNameOnly = existingBenefits
            .filter { it.volunteerId.isNullOrBlank() }
            .toMutableList()

        val toInsert = mutableListOf<Guest>()
        val toUpdate = mutableListOf<Guest>()
        val toDelete = mutableListOf<Guest>()
        val consumed = mutableSetOf<String>()

        fun rowKey(guest: Guest): String =
            if (guest.id > 0L) "id:${guest.id}" else "n:${guest.nanoId}"

        for ((volId, computedGuest) in computedByVol) {
            val group = existingByVol[volId].orEmpty()
            if (group.isNotEmpty()) {
                val canonical = pickCanonical(group) ?: group.first()
                val updated = prepareForUpdate(canonical, computedGuest)
                if (benefitFieldsChanged(canonical, updated)) {
                    toUpdate.add(updated)
                }
                group.filter { !isSameBenefitRow(it, canonical) }.forEach { extra ->
                    toDelete.add(extra)
                    consumed.add(rowKey(extra))
                }
                consumed.add(rowKey(canonical))
                continue
            }
            val nameMatchIndex = unmatchedNameOnly.indexOfFirst { orphan ->
                orphan.name.equals(computedGuest.name, ignoreCase = true) &&
                    orphan.lastNameAbbreviation.equals(computedGuest.lastNameAbbreviation, ignoreCase = true)
            }
            if (nameMatchIndex >= 0) {
                val orphan = unmatchedNameOnly.removeAt(nameMatchIndex)
                toUpdate.add(prepareForUpdate(orphan, computedGuest))
                consumed.add(rowKey(orphan))
            } else {
                toInsert.add(prepareForInsert(computedGuest, orgIdForInsert))
            }
        }

        for (existingGuest in existingBenefits) {
            val key = rowKey(existingGuest)
            if (key in consumed) continue
            val volId = existingGuest.volunteerId?.trim().orEmpty()
            if (volId.isNotEmpty() && volId in computedByVol) continue
            toDelete.add(existingGuest)
        }

        return Diff(
            toInsert = toInsert,
            toUpdate = toUpdate,
            toDelete = toDelete.distinctBy { rowKey(it) },
        )
    }

    private fun benefitFieldsChanged(existing: Guest, updated: Guest): Boolean =
        existing.name != updated.name ||
            existing.lastNameAbbreviation != updated.lastNameAbbreviation ||
            existing.invitations != updated.invitations ||
            existing.notes != updated.notes ||
            existing.nfcCardUid != updated.nfcCardUid ||
            existing.volunteerId != updated.volunteerId ||
            existing.venueName != updated.venueName ||
            existing.firebaseOrgId != updated.firebaseOrgId
}
