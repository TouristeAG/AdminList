package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.utils.NanoIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirestoreSyncIntegrityTest {

    @Test
    fun stampLedgerTransfer_persistsOrgAndSyncState() {
        val original = AccountTransfer(
            holderType = AccountHolderType.VOLUNTEER,
            holderId = "v1",
            holderName = "Ada",
            amount = 10.0,
            type = AccountTransferType.MANUAL_ADJUSTMENT,
            sourceReference = "manual:abc:v1",
            firebaseOrgId = "",
        )
        val stamped = stampLedgerTransfer(original, "org-a", AccountTransferSyncState.CONFIRMED)
        assertEquals("org-a", stamped.firebaseOrgId)
        assertEquals(AccountTransferSyncState.CONFIRMED, stamped.syncState)
        assertEquals(10.0, stamped.amount)
    }

    @Test
    fun applyPolicy_emptyNonDeleteSnapshotIsNotADelete() {
        assertFalse(FirestoreApplyPolicy.isRemoteDelete(deleted = false))
        assertTrue(FirestoreApplyPolicy.shouldSkipIncompleteSnapshot(deleted = false, data = null))
        assertTrue(FirestoreApplyPolicy.shouldSkipIncompleteSnapshot(deleted = false, data = emptyMap()))
        assertFalse(FirestoreApplyPolicy.shouldSkipIncompleteSnapshot(deleted = true, data = null))
        assertFalse(
            FirestoreApplyPolicy.shouldSkipIncompleteSnapshot(
                deleted = false,
                data = mapOf("lastModified" to 1L),
            ),
        )
    }

    @Test
    fun applyPolicy_lwwKeepsLocalAndBackfillsBlankOrg() {
        assertTrue(FirestoreApplyPolicy.shouldKeepLocal(existingLastModified = 50L, remoteLastModified = 50L))
        assertTrue(FirestoreApplyPolicy.shouldKeepLocal(existingLastModified = 51L, remoteLastModified = 50L))
        assertFalse(FirestoreApplyPolicy.shouldKeepLocal(existingLastModified = 49L, remoteLastModified = 50L))
        assertTrue(FirestoreApplyPolicy.needsOrgBackfill("", "org-a"))
        assertFalse(FirestoreApplyPolicy.needsOrgBackfill("org-a", "org-a"))
        assertEquals("org-a", FirestoreApplyPolicy.orgIdToPersist("", "org-a"))
        assertEquals("org-a", FirestoreApplyPolicy.orgIdToPersist("org-a", "org-b"))
    }

    @Test
    fun persistDedupeGuests_doesNotDropUntaggedWhenTaggedRowsExist() {
        val taggedId = NanoIdGenerator.generateGuestId()
        val untaggedId = NanoIdGenerator.generateGuestId()
        val guests = listOf(
            Guest(id = 1, nanoId = untaggedId, name = "Local", invitations = 1, venueName = "Main", firebaseOrgId = ""),
            Guest(id = 2, nanoId = taggedId, name = "Remote", invitations = 1, venueName = "Main", firebaseOrgId = "org-a"),
        )
        val kept = PersistIdentityDedupe.guests(guests)
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.nanoId == untaggedId && it.firebaseOrgId.isBlank() })
        assertTrue(kept.any { it.nanoId == taggedId && it.firebaseOrgId == "org-a" })
    }

    @Test
    fun persistDedupeGuests_collapsesSameNanoIdPreferringNewerTagged() {
        val nanoId = NanoIdGenerator.generateGuestId()
        val guests = listOf(
            Guest(
                id = 1,
                nanoId = nanoId,
                name = "Old",
                invitations = 1,
                venueName = "Main",
                lastModified = 10L,
                firebaseOrgId = "",
            ),
            Guest(
                id = 2,
                nanoId = nanoId,
                name = "New",
                invitations = 1,
                venueName = "Main",
                lastModified = 20L,
                firebaseOrgId = "org-a",
            ),
        )
        val kept = PersistIdentityDedupe.guests(guests)
        assertEquals(1, kept.size)
        assertEquals("New", kept.single().name)
        assertEquals("org-a", kept.single().firebaseOrgId)
    }

    @Test
    fun volunteerBenefitMerge_preservesNanoIdAndOrgOnUpdate() {
        val existing = Guest(
            id = 7,
            nanoId = "existing-nano-id-xxxxx",
            name = "Ada",
            invitations = 1,
            venueName = "BOTH",
            isVolunteerBenefit = true,
            volunteerId = "vol-1",
            firebaseOrgId = "org-a",
            lastModified = 1L,
        )
        val computed = Guest(
            name = "Ada L",
            invitations = 2,
            venueName = "BOTH",
            notes = "Volunteer benefit - SPECIAL",
            isVolunteerBenefit = true,
            volunteerId = "vol-1",
            firebaseOrgId = "",
        )
        val merged = VolunteerBenefitGuestMerge.prepareForUpdate(existing, computed)
        assertEquals(existing.id, merged.id)
        assertEquals(existing.nanoId, merged.nanoId)
        assertEquals("org-a", merged.firebaseOrgId)
        assertEquals(2, merged.invitations)
        assertEquals("Ada L", merged.name)
    }

    @Test
    fun volunteerBenefitMerge_insertStampsOrg() {
        val computed = Guest(
            name = "Ada",
            invitations = 1,
            venueName = "BOTH",
            isVolunteerBenefit = true,
            volunteerId = "vol-1",
        )
        val inserted = VolunteerBenefitGuestMerge.prepareForInsert(computed, "org-a")
        assertEquals("org-a", inserted.firebaseOrgId)
        assertTrue(inserted.isVolunteerBenefit)
    }
}
