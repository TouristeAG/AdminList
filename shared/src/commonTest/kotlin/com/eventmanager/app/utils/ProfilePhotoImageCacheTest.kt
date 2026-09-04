package com.eventmanager.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProfilePhotoImageCacheTest {
    @Test
    fun cacheKey_isStableAndDiffersByUrl() {
        val a = ProfilePhotoImageCache.cacheKey("https://example.com/a.jpg?token=1")
        val b = ProfilePhotoImageCache.cacheKey("https://example.com/a.jpg?token=1")
        val c = ProfilePhotoImageCache.cacheKey("https://example.com/a.jpg?token=2")
        assertEquals(40, a.length)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun revisionState_isIndependentPerCacheId() {
        val a = ProfilePhotoImageCache.revisionState("https://example.com/a.jpg")
        val aAgain = ProfilePhotoImageCache.revisionState("https://example.com/a.jpg")
        val b = ProfilePhotoImageCache.revisionState("https://example.com/b.jpg")
        assertEquals(a, aAgain)
        assertNotEquals(a, b)
        assertEquals(0L, a.value)
        assertEquals(0L, b.value)
    }
}
