package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An institution typed its display name, "Collectif Nocturne", as the org ID. It was stored
 * verbatim, never satisfied [FirebaseOrgBootstrap.isValidOrgId], and left the setup wizard's
 * Continue button greyed out with nothing on screen explaining why.
 */
class FirebaseOrgIdSanitizeTest {

    @Test
    fun `spaces become hyphens`() {
        assertEquals("Collectif-Nocturne", FirebaseOrgBootstrap.sanitizeOrgId("Collectif Nocturne"))
    }

    @Test
    fun `sanitized display names are valid org ids`() {
        assertFalse(FirebaseOrgBootstrap.isValidOrgId("Collectif Nocturne"))
        assertTrue(FirebaseOrgBootstrap.isValidOrgId(FirebaseOrgBootstrap.sanitizeOrgId("Collectif Nocturne")))
    }

    @Test
    fun `accents and punctuation are dropped`() {
        assertEquals("Societe-detudiants", FirebaseOrgBootstrap.sanitizeOrgId("Société d'étudiants"))
    }

    @Test
    fun `leading separators are trimmed but hyphens and underscores survive`() {
        assertEquals("noctulist_club-2", FirebaseOrgBootstrap.sanitizeOrgId("  -noctulist_club-2"))
    }

    @Test
    fun `a trailing space stays visible as a hyphen so typing can continue`() {
        assertEquals("Collectif-", FirebaseOrgBootstrap.sanitizeOrgId("Collectif "))
    }

    @Test
    fun `already valid ids are left untouched`() {
        assertEquals("noctulist-club", FirebaseOrgBootstrap.sanitizeOrgId("noctulist-club"))
    }

    @Test
    fun `length is capped at the Firestore path segment limit`() {
        assertEquals(64, FirebaseOrgBootstrap.sanitizeOrgId("a".repeat(200)).length)
    }
}
