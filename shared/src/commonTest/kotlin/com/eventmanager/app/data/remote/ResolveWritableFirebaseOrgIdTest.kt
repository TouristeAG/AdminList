package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveWritableFirebaseOrgIdTest {
    @Test
    fun prefersActiveSingleOrg() {
        assertEquals(
            "club-a",
            resolveWritableFirebaseOrgId("club-a", "club-b", listOf("club-c")),
        )
    }

    @Test
    fun fallsBackFromAllSentinel() {
        assertEquals(
            "club-b",
            resolveWritableFirebaseOrgId(FIREBASE_ORG_ALL_SENTINEL, "club-b", listOf("club-c")),
        )
        assertEquals(
            "club-c",
            resolveWritableFirebaseOrgId(FIREBASE_ORG_ALL_SENTINEL, "", listOf("club-c")),
        )
        assertEquals(
            "",
            resolveWritableFirebaseOrgId(FIREBASE_ORG_ALL_SENTINEL, "", emptyList()),
        )
    }
}
