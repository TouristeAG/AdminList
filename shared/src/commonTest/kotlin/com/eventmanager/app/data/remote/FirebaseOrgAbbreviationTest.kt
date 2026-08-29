package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class FirebaseOrgAbbreviationTest {
    @Test
    fun multiWordUsesInitials() {
        assertEquals("CN", FirebaseOrgAbbreviation.abbreviate("Collectif-Nocturne"))
        assertEquals("CN", FirebaseOrgAbbreviation.abbreviate("Collectif Nocturne"))
        assertEquals("LMT", FirebaseOrgAbbreviation.abbreviate("Lethal Maximal Tempo"))
    }

    @Test
    fun ignoresStopWords() {
        assertEquals("CN", FirebaseOrgAbbreviation.abbreviate("Collectif et Nocturne"))
    }

    @Test
    fun singleWordWithDigits() {
        assertEquals("C25", FirebaseOrgAbbreviation.abbreviate("Corner25"))
        assertEquals("S33", FirebaseOrgAbbreviation.abbreviate("Studio 33"))
    }

    @Test
    fun fallbackFirstThreeLetters() {
        assertEquals("MON", FirebaseOrgAbbreviation.abbreviate("Monorg"))
    }
}
