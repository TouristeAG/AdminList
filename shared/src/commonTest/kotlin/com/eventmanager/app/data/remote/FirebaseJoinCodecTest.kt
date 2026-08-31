package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirebaseJoinCodecTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val payload = FirebaseJoinPayload(
            orgId = "club-demo",
            projectId = "my-project",
            applicationId = "1:123:web:abc",
            apiKey = "AIzaSyDemoKey",
            webClientId = "123-abc.apps.googleusercontent.com",
            webClientSecret = "GOCSPX-demo-secret",
        )
        val encoded = FirebaseJoinCodec.encode(payload)
        assertTrue(encoded.startsWith("noctulist-fb:1:"))
        val decoded = FirebaseJoinCodec.decode(encoded).getOrThrow()
        assertEquals(payload, decoded)
    }

    @Test
    fun decodeRejectsWrongPrefix() {
        val result = FirebaseJoinCodec.decode("not-a-join-code")
        assertTrue(result.isFailure)
    }

    @Test
    fun parseStrictJsonFirebaseConfig() {
        val raw = """
            {
              "apiKey": "AIzaSyJson",
              "appId": "1:99:web:xyz",
              "projectId": "proj-json",
              "messagingSenderId": "99",
              "storageBucket": "proj-json.appspot.com"
            }
        """.trimIndent()
        val cfg = FirebaseJoinCodec.parseFirebaseWebConfig(raw).getOrThrow()
        assertEquals("AIzaSyJson", cfg.apiKey)
        assertEquals("1:99:web:xyz", cfg.applicationId)
        assertEquals("proj-json", cfg.projectId)
        assertEquals("99", cfg.gcmSenderId)
    }

    @Test
    fun parseJsStyleFirebaseConfig() {
        val raw = """
            const firebaseConfig = {
              apiKey: "AIzaSyJs",
              authDomain: "x.firebaseapp.com",
              projectId: "proj-js",
              appId: "1:1:web:js"
            };
        """.trimIndent()
        val cfg = FirebaseJoinCodec.parseFirebaseWebConfig(raw).getOrThrow()
        assertEquals("AIzaSyJs", cfg.apiKey)
        assertEquals("proj-js", cfg.projectId)
        assertEquals("1:1:web:js", cfg.applicationId)
    }

    @Test
    fun encodeIncludesOAuthSecretForTeamQr() {
        val payload = FirebaseJoinPayload(
            orgId = "club-demo",
            projectId = "my-project",
            applicationId = "1:123:web:abc",
            apiKey = "AIzaSyDemoKey",
            webClientId = "123-abc.apps.googleusercontent.com",
            webClientSecret = "GOCSPX-demo-secret",
        )
        val encoded = FirebaseJoinCodec.encode(payload)
        assertTrue(encoded.startsWith("noctulist-fb:1:"))
        val decoded = FirebaseJoinCodec.decode(encoded).getOrThrow()
        assertEquals("GOCSPX-demo-secret", decoded.webClientSecret)
    }

    @Test
    fun encodePublicOmitsSecret() {
        val payload = FirebaseJoinPayload(
            orgId = "club-demo",
            projectId = "my-project",
            applicationId = "1:123:web:abc",
            apiKey = "AIzaSyDemoKey",
            webClientId = "123-abc.apps.googleusercontent.com",
            webClientSecret = "GOCSPX-demo-secret",
        )
        val encoded = FirebaseJoinCodec.encodePublic(payload.toPublicPayload())
        assertTrue(encoded.startsWith("noctulist-fb:2:"))
        assertFalse(encoded.contains("GOCSPX"))
        val decoded = FirebaseJoinCodec.decode(encoded).getOrThrow()
        assertEquals("", decoded.webClientSecret)
        assertEquals("club-demo", decoded.orgId)
    }

    @Test
    fun memberRoleFromStorageDefaultsToMember() {
        assertEquals(MemberRole.MEMBER, MemberRole.fromStorage("door"))
        assertEquals(MemberRole.MEMBER, MemberRole.fromStorage(null))
        assertEquals(MemberRole.ADMIN, MemberRole.fromStorage("admin"))
    }

    @Test
    fun firebaseSecretsVisibleForJoin_onlyWhenProjectMissing() {
        assertFalse(firebaseSecretsVisibleForJoin(projectOptionsAlreadyPresent = true))
        assertTrue(firebaseSecretsVisibleForJoin(projectOptionsAlreadyPresent = false))
    }

    @Test
    fun announcementHasFirebaseProjectOptions() {
        val incomplete = InstitutionBackendAnnouncement(
            backendType = BackendType.FIREBASE,
            migrationId = "m1",
            migratedAt = 1L,
            firebaseOrgId = "org",
            firebaseProjectId = "p",
        )
        assertFalse(incomplete.hasFirebaseProjectOptions())
        val complete = incomplete.copy(
            firebaseApplicationId = "1:1:web:a",
            firebaseApiKey = "key",
        )
        assertTrue(complete.hasFirebaseProjectOptions())
    }
}
