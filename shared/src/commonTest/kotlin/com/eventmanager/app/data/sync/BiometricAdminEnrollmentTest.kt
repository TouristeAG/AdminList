package com.eventmanager.app.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiometricAdminEnrollmentTest {

    @Test
    fun encodeAndDecodeSingleEnrollment() {
        val enrollment = BiometricAdminOrgEnrollment(
            orgId = "org-a",
            link = BiometricAdminProfileLink(BiometricAdminProfileType.VOLUNTEER, "vol-1"),
        )
        val encoded = BiometricAdminOrgEnrollment.encodeList(listOf(enrollment))
        val decoded = BiometricAdminOrgEnrollment.decodeList(encoded)
        assertEquals(1, decoded.size)
        assertEquals("org-a", decoded.first().orgId)
        assertEquals(BiometricAdminProfileType.VOLUNTEER, decoded.first().link.type)
        assertEquals("vol-1", decoded.first().link.profileId)
    }

    @Test
    fun encodeAndDecodeMultipleEnrollments() {
        val enrollments = listOf(
            BiometricAdminOrgEnrollment(
                orgId = "org-a",
                link = BiometricAdminProfileLink(BiometricAdminProfileType.VOLUNTEER, "vol-1"),
            ),
            BiometricAdminOrgEnrollment(
                orgId = "org-b",
                link = BiometricAdminProfileLink(BiometricAdminProfileType.GUEST, "guest-2"),
            ),
        )
        val encoded = BiometricAdminOrgEnrollment.encodeList(enrollments)
        val decoded = BiometricAdminOrgEnrollment.decodeList(encoded)
        assertEquals(2, decoded.size)
        assertEquals("org-a", decoded[0].orgId)
        assertEquals("org-b", decoded[1].orgId)
        assertEquals(BiometricAdminProfileType.GUEST, decoded[1].link.type)
    }

    @Test
    fun decodeIgnoresInvalidEntries() {
        val decoded = BiometricAdminOrgEnrollment.decodeList("bad|org-a:VOLUNTEER:vol-1")
        assertEquals(1, decoded.size)
        assertEquals("org-a", decoded.first().orgId)
    }

    @Test
    fun decodeBlankReturnsEmpty() {
        assertTrue(BiometricAdminOrgEnrollment.decodeList(null).isEmpty())
        assertTrue(BiometricAdminOrgEnrollment.decodeList("").isEmpty())
    }

    @Test
    fun legacyProfileLinkStillDecodes() {
        val link = BiometricAdminProfileLink.decode("GUEST:nano-123")
        assertEquals(BiometricAdminProfileType.GUEST, link?.type)
        assertEquals("nano-123", link?.profileId)
        assertNull(BiometricAdminProfileLink.decode("invalid"))
    }
}
