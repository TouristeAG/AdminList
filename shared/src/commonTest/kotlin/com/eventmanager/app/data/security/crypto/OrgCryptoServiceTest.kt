package com.eventmanager.app.data.security.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OrgCryptoServiceTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val service = DefaultOrgCryptoService { "test-passphrase-not-for-production" }
        val orgId = "org-roundtrip"
        val plain = "guest@example.com"
        val cipher = service.encrypt(plain, orgId)
        assertTrue(cipher.startsWith("v1:"))
        assertNotEquals(plain, cipher)
        assertEquals(plain, service.decrypt(cipher, orgId))
    }

    @Test
    fun deriveKeyUsesPassphraseProviderOncePerOrg() {
        var providerCalls = 0
        val service = DefaultOrgCryptoService {
            providerCalls++
            "cached-passphrase"
        }
        val orgId = "org-cache"
        repeat(10) { i ->
            val cipher = service.encrypt("field-$i", orgId)
            assertEquals("field-$i", service.decrypt(cipher, orgId))
        }
        assertEquals(1, providerCalls)
    }

    @Test
    fun isConfiguredIsCachedPerOrg() {
        var providerCalls = 0
        val service = DefaultOrgCryptoService {
            providerCalls++
            "configured"
        }
        repeat(8) {
            assertTrue(service.isConfigured("org-configured"))
        }
        assertEquals(1, providerCalls)
    }

    @Test
    fun invalidateCachedKeyForcesRederive() {
        var providerCalls = 0
        val service = DefaultOrgCryptoService {
            providerCalls++
            "pass-$providerCalls"
        }
        val orgId = "org-invalidate"
        service.encrypt("alpha", orgId)
        assertEquals(1, providerCalls)
        service.invalidateCachedKey(orgId)
        service.encrypt("beta", orgId)
        assertEquals(2, providerCalls)
    }

    @Test
    fun skipsAlreadyEncryptedPayload() {
        var providerCalls = 0
        val service = DefaultOrgCryptoService {
            providerCalls++
            "pass"
        }
        val first = service.encrypt("hello", "org-skip")
        val second = service.encrypt(first, "org-skip")
        assertEquals(first, second)
        assertEquals(1, providerCalls)
    }

    @Test
    fun decryptCorruptPayloadDoesNotThrow() {
        val service = DefaultOrgCryptoService { "pass" }
        val orgId = "org-corrupt"
        val garbage = "v1:not-valid-base64-or-gcm!!!"
        val result = service.decrypt(garbage, orgId)
        assertEquals(garbage, result)
    }
}
