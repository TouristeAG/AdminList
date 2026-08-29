package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirebaseConfiguredOrgCodecTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val orgs = listOf(
            FirebaseConfiguredOrg("club-a", 0xFFE53935L),
            FirebaseConfiguredOrg("club-b", 0xFF1E88E5L),
        )
        val encoded = FirebaseConfiguredOrgCodec.encode(orgs)
        val decoded = FirebaseConfiguredOrgCodec.decode(encoded)
        assertEquals(orgs, decoded)
    }

    @Test
    fun normalizeRequiresAtLeastOneOrg() {
        assertFailsWith<IllegalArgumentException> {
            FirebaseConfiguredOrgCodec.normalize(emptyList())
        }
    }

    @Test
    fun normalizeDeduplicatesOrgIds() {
        val normalized = FirebaseConfiguredOrgCodec.normalize(
            listOf(
                FirebaseConfiguredOrg("club-a", 0xFFE53935L),
                FirebaseConfiguredOrg("club-a", 0xFF1E88E5L),
                FirebaseConfiguredOrg("club-b", 0xFF43A047L),
            ),
        )
        assertEquals(2, normalized.size)
        assertEquals(listOf("club-a", "club-b"), normalized.map { it.orgId })
    }

    @Test
    fun migrateFromSingleOrgId() {
        val migrated = FirebaseConfiguredOrgCodec.migrateFromSingleOrgId("legacy-org")
        assertEquals(1, migrated.size)
        assertEquals("legacy-org", migrated.first().orgId)
        assertTrue(migrated.first().colorArgb != 0L)
    }
}
