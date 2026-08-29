package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FirestoreRulesClipboardContentTest {
    @Test
    fun sanitizeKeepsValidRulesHeader() {
        val text = FirestoreRulesClipboardContent.sanitizeForFirebaseConsole(
            "\uFEFFrules_version = '2';\nservice cloud.firestore {\n}\n",
        )
        assertTrue(text.startsWith("rules_version = '2';"))
        assertFalse(text.startsWith("\uFEFF"))
    }

    @Test
    fun loadFromComposeResourceMatchesDeployFile() = runBlocking {
        val text = FirestoreRulesClipboardContent.load()
        assertTrue(
            text.startsWith("rules_version = '2';"),
            "unexpected start: ${text.take(80)}",
        )
        assertTrue(text.contains("service cloud.firestore"))
        assertTrue(text.contains("match /orgs/{orgId}/guests/{docId}"))
        assertTrue(text.contains("\$(database)"))
        assertFalse(text.contains("\${'\$'}"))
        assertFalse(text.contains("\\$(database)"))
    }
}
