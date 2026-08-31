package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.AccountTransferSyncState
import com.eventmanager.app.data.models.AccountTransferType
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
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
    fun persistDedupeVenues_collapsesUntaggedTwinOfTaggedName() {
        val venues = listOf(
            VenueEntity(id = 1, name = "Lieu 1", firebaseOrgId = "", isActive = true),
            VenueEntity(id = 2, name = "Lieu 1", firebaseOrgId = "org-a", isActive = true),
            VenueEntity(id = 3, name = "Lieu 2", firebaseOrgId = "", isActive = true),
            VenueEntity(id = 4, name = "Lieu 2", firebaseOrgId = "org-a", isActive = true),
        )
        val kept = PersistIdentityDedupe.venues(venues)
        assertEquals(listOf("Lieu 1", "Lieu 2"), kept.map { it.name }.sorted())
        assertTrue(kept.all { it.firebaseOrgId == "org-a" })
    }

    @Test
    fun persistDedupeVenues_keepsSameNameAcrossDifferentOrgs() {
        val venues = listOf(
            VenueEntity(id = 1, name = "Lieu 1", firebaseOrgId = "org-a", isActive = true),
            VenueEntity(id = 2, name = "Lieu 1", firebaseOrgId = "org-b", isActive = true),
        )
        val kept = PersistIdentityDedupe.venues(venues)
        assertEquals(2, kept.size)
        assertEquals(setOf("org-a", "org-b"), kept.map { it.firebaseOrgId }.toSet())
    }

    @Test
    fun persistDedupeVenues_collapsesSameOrgNameFromPullAndListener() {
        val venues = listOf(
            VenueEntity(id = 1, name = "Lieu 1", description = "local", firebaseOrgId = "org-a", isActive = true),
            VenueEntity(id = 2, name = "Lieu 1", description = "", firebaseOrgId = "org-a", isActive = true),
        )
        val kept = PersistIdentityDedupe.venues(venues)
        assertEquals(1, kept.size)
        assertEquals("org-a", kept.single().firebaseOrgId)
    }

    @Test
    fun persistDedupeVenues_keepsUntaggedWhenNoTaggedTwin() {
        val venues = listOf(
            VenueEntity(id = 1, name = "Legacy", firebaseOrgId = "", isActive = true),
            VenueEntity(id = 2, name = "Lieu 1", firebaseOrgId = "org-a", isActive = true),
        )
        val kept = PersistIdentityDedupe.venues(venues)
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.name == "Legacy" && it.firebaseOrgId.isBlank() })
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
    fun volunteerBenefitDiff_deletesForkedRowsForSameVolunteer() {
        val computed = Guest(
            name = "Leonardo",
            lastNameAbbreviation = "Mondada",
            invitations = 2,
            venueName = "BOTH",
            notes = "Volunteer benefit - SPECIAL",
            isVolunteerBenefit = true,
            volunteerId = "vol-leo",
            firebaseOrgId = "org-a",
        )
        val keep = computed.copy(id = 1, nanoId = "aaaaaaaaaaaaaaaaaaaaa", lastModified = 30L)
        val fork1 = computed.copy(id = 2, nanoId = "bbbbbbbbbbbbbbbbbbbbb", lastModified = 10L, invitations = 1)
        val fork2 = computed.copy(id = 3, nanoId = "ccccccccccccccccccccc", lastModified = 20L, invitations = 0)
        val diff = VolunteerBenefitGuestMerge.diff(
            existing = listOf(keep, fork1, fork2),
            computed = listOf(computed),
            orgIdForInsert = "org-a",
        )
        assertEquals(0, diff.toInsert.size)
        assertEquals(setOf(2L, 3L), diff.toDelete.map { it.id }.toSet())
        assertTrue(diff.toUpdate.isEmpty() || diff.toUpdate.single().id == 1L)
        val collapsed = VolunteerBenefitGuestMerge.collapseDuplicates(listOf(keep, fork1, fork2))
        assertEquals(1, collapsed.size)
        assertEquals(1L, collapsed.single().id)
    }

    @Test
    fun volunteerBenefitDiff_doesNotInsertWhenVolunteerAlreadyHasARow() {
        val existing = Guest(
            id = 4,
            nanoId = "ddddddddddddddddddddd",
            name = "Leonardo",
            lastNameAbbreviation = "M",
            invitations = 1,
            venueName = "BOTH",
            notes = "Volunteer benefit - SPECIAL",
            isVolunteerBenefit = true,
            volunteerId = "vol-leo",
            firebaseOrgId = "org-a",
            lastModified = 5L,
        )
        val computed = existing.copy(id = 0, nanoId = "eeeeeeeeeeeeeeeeeeeee", invitations = 3, lastNameAbbreviation = "Mondada")
        val diff = VolunteerBenefitGuestMerge.diff(
            existing = listOf(existing),
            computed = listOf(computed),
            orgIdForInsert = "org-a",
        )
        assertEquals(0, diff.toInsert.size)
        assertEquals(0, diff.toDelete.size)
        assertEquals(1, diff.toUpdate.size)
        assertEquals(4L, diff.toUpdate.single().id)
        assertEquals(existing.nanoId, diff.toUpdate.single().nanoId)
        assertEquals(3, diff.toUpdate.single().invitations)
        assertEquals("Mondada", diff.toUpdate.single().lastNameAbbreviation)
    }

    @Test
    fun nextPeopleCounterTimestamp_isStrictlyMonotonic() {
        assertEquals(100L, FirestoreApplyPolicy.nextPeopleCounterTimestamp(previous = 50L, now = 100L))
        assertEquals(51L, FirestoreApplyPolicy.nextPeopleCounterTimestamp(previous = 50L, now = 50L))
        assertEquals(51L, FirestoreApplyPolicy.nextPeopleCounterTimestamp(previous = 50L, now = 40L))
    }

    @Test
    fun mergePeopleCounter_keepsNewerLocalWhenVenueSnapshotIsStale() {
        val local = PeopleCounterSnapshot(
            count = 42,
            writerDeviceId = "device-a",
            writerAccountEmail = "a@example.com",
            lastModified = 200L,
        )
        val merged = FirestoreApplyPolicy.mergePeopleCounter(
            local = local,
            remoteCount = 30,
            remoteWriterDeviceId = "device-a",
            remoteWriterAccountEmail = "a@example.com",
            remoteLastModified = 150L,
        )
        assertEquals(42, merged.count)
        assertEquals(200L, merged.lastModified)
    }

    @Test
    fun mergePeopleCounter_equalTimestampDoesNotRollBackLocalCount() {
        val local = PeopleCounterSnapshot(
            count = 18,
            writerDeviceId = "device-a",
            writerAccountEmail = "",
            lastModified = 90L,
        )
        val merged = FirestoreApplyPolicy.mergePeopleCounter(
            local = local,
            remoteCount = 11,
            remoteWriterDeviceId = "device-a",
            remoteWriterAccountEmail = "",
            remoteLastModified = 90L,
        )
        assertEquals(18, merged.count)
        assertEquals(90L, merged.lastModified)
    }

    @Test
    fun mergePeopleCounter_takesNewerRemoteCount() {
        val local = PeopleCounterSnapshot(
            count = 5,
            writerDeviceId = "device-a",
            writerAccountEmail = "",
            lastModified = 10L,
        )
        val merged = FirestoreApplyPolicy.mergePeopleCounter(
            local = local,
            remoteCount = 12,
            remoteWriterDeviceId = "device-b",
            remoteWriterAccountEmail = "b@example.com",
            remoteLastModified = 20L,
        )
        assertEquals(12, merged.count)
        assertEquals("device-b", merged.writerDeviceId)
        assertEquals("b@example.com", merged.writerAccountEmail)
        assertEquals(20L, merged.lastModified)
    }

    @Test
    fun mergePeopleCounter_insertUsesRemoteWhenNoLocal() {
        val merged = FirestoreApplyPolicy.mergePeopleCounter(
            local = null,
            remoteCount = 3,
            remoteWriterDeviceId = "device-a",
            remoteWriterAccountEmail = "a@example.com",
            remoteLastModified = 5L,
        )
        assertEquals(3, merged.count)
        assertEquals("device-a", merged.writerDeviceId)
        assertEquals(5L, merged.lastModified)
    }

    @Test
    fun peopleCounterWrite_skipsWhenRemoteTimestampIsNewer() {
        assertTrue(FirestoreApplyPolicy.shouldKeepLocal(existingLastModified = 200L, remoteLastModified = 150L))
        assertFalse(FirestoreApplyPolicy.shouldKeepLocal(existingLastModified = 150L, remoteLastModified = 200L))
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
