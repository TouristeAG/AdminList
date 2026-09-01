package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfilePhotoStorageParseTest {
    @Test
    fun parseFirebaseDownloadUrl_decodesEncodedSlashes() {
        val url =
            "https://firebasestorage.googleapis.com/v0/b/demo.appspot.com/o/" +
                "orgs%2Forg-a%2FprofilePhotos%2Fguests%2Fid.jpg?alt=media&token=abc-123"
        val parsed = parseFirebaseStorageDownloadUrl(url)!!
        assertEquals("demo.appspot.com", parsed.bucket)
        assertEquals("orgs/org-a/profilePhotos/guests/id.jpg", parsed.path)
    }

    @Test
    fun parseGsUrl() {
        val parsed = parseFirebaseStorageDownloadUrl(
            "gs://demo.firebasestorage.app/orgs/org-a/profilePhotos/volunteers/v1.jpg",
        )!!
        assertEquals("demo.firebasestorage.app", parsed.bucket)
        assertEquals("orgs/org-a/profilePhotos/volunteers/v1.jpg", parsed.path)
    }

    @Test
    fun parseIgnoresBlankAndNonStorageUrls() {
        assertNull(parseFirebaseStorageDownloadUrl(""))
        assertNull(parseFirebaseStorageDownloadUrl("https://example.com/photo.jpg"))
    }

    @Test
    fun resolvedVolunteerPathFallsBackToCanonicalStorageObject() {
        val volunteer = com.eventmanager.app.data.models.Volunteer(
            id = "vol-1",
            name = "Bea",
            lastNameAbbreviation = "B",
            email = "b@x.com",
            phoneNumber = "1",
            firebaseOrgId = "org-a",
        )
        assertEquals(
            "orgs/org-a/profilePhotos/volunteers/vol-1.jpg",
            volunteer.resolvedProfilePhotoPath(),
        )
    }
}
