package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.SalesSheetItem
import com.eventmanager.app.data.models.VenueEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiOrgMergeTest {

    @Test
    fun mergeVenueFilters_combinesSameNameAcrossOrgs() {
        val venues = listOf(
            VenueEntity(id = 1, name = "Main", firebaseOrgId = "org-a", isActive = true),
            VenueEntity(id = 2, name = "Main", firebaseOrgId = "org-b", isActive = true),
            VenueEntity(id = 3, name = "Bar", firebaseOrgId = "org-a", isActive = true),
        )
        val merged = MultiOrgMerge.mergeVenueFilters(venues)
        assertEquals(2, merged.size)
        val main = merged.first { it.displayName == "Main" }
        assertEquals(listOf("org-a", "org-b"), main.orgIds.sorted())
    }

    @Test
    fun mergePosProducts_keepsDistinctSettingsSeparate() {
        val items = listOf(
            SalesSheetItem(id = 1, name = "Beer", price = 5.0, firebaseOrgId = "org-a", isActive = true),
            SalesSheetItem(id = 2, name = "Beer", price = 6.0, firebaseOrgId = "org-b", isActive = true),
        )
        val merged = MultiOrgMerge.mergePosProducts(items)
        assertEquals(2, merged.size)
    }

    @Test
    fun mergePosProducts_mergesIdenticalItems() {
        val items = listOf(
            SalesSheetItem(id = 1, name = "Beer", price = 5.0, firebaseOrgId = "org-a", isActive = true),
            SalesSheetItem(id = 2, name = "Beer", price = 5.0, firebaseOrgId = "org-b", isActive = true),
        )
        val merged = MultiOrgMerge.mergePosProducts(items)
        assertEquals(1, merged.size)
        assertEquals(2, merged.first().sources.size)
    }

    @Test
    fun configuredOrgIdsOnly_excludesUnconfiguredOrgs() {
        val configured = listOf("org-a", "org-b")
        val data = listOf("org-a", "org-b", "org-c")
        assertEquals(listOf("org-a", "org-b"), MultiOrgMerge.configuredOrgIdsOnly(configured, data))
    }

    @Test
    fun findGuestsByNfcUid_returnsAllOrgMatches() {
        val guests = listOf(
            Guest(nanoId = "g1", name = "A", invitations = 1, venueName = "Main", nfcCardUid = "ABC", firebaseOrgId = "org-a"),
            Guest(nanoId = "g2", name = "B", invitations = 1, venueName = "Main", nfcCardUid = "ABC", firebaseOrgId = "org-b"),
        )
        val matches = MultiOrgMerge.findGuestsByNfcUid(guests, "abc")
        assertEquals(2, matches.size)
        assertTrue(matches.all { it.orgId.isNotBlank() })
    }
}
