package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseEmailDomainPolicyTest {
    @Test
    fun normalizeAndParse() {
        assertEquals("school.ch", FirebaseEmailDomainPolicy.normalizeDomain("@School.CH"))
        assertEquals(
            listOf("a.ch", "b.org"),
            FirebaseEmailDomainPolicy.parseStoredList("@a.ch, b.org; @a.ch"),
        )
    }

    @Test
    fun emptyAllowlistAcceptsAnyone() {
        assertTrue(FirebaseEmailDomainPolicy.isEmailAllowed("perso@gmail.com", emptyList()))
    }

    @Test
    fun allowlistRejectsOtherDomains() {
        val allowed = listOf("quelquechose.ch", "autrechose.ch")
        assertTrue(FirebaseEmailDomainPolicy.isEmailAllowed("alice@quelquechose.ch", allowed))
        assertTrue(FirebaseEmailDomainPolicy.isEmailAllowed("bob@AutreChose.CH", allowed))
        assertFalse(FirebaseEmailDomainPolicy.isEmailAllowed("eve@gmail.com", allowed))
        assertFalse(FirebaseEmailDomainPolicy.isEmailAllowed(null, allowed))
    }

    @Test
    fun firestoreMapKeys() {
        val map = FirebaseEmailDomainPolicy.domainsToFirestoreMap(listOf("@Foo.ch", "bar.org"))
        assertEquals(true, map["foo.ch"])
        assertEquals(true, map["bar.org"])
    }
}
