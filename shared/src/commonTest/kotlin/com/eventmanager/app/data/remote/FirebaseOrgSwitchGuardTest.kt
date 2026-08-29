package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class FirebaseOrgSwitchGuardTest {
    @Test
    fun drainForOrgFiltersByOrgId() = runBlocking {
        var activeOrg = "org-a"
        val queue = PendingRemoteWriteQueue(activeOrgId = { activeOrg })
        queue.enqueueUpsert("guests", "g1", "{}")
        activeOrg = "org-b"
        queue.enqueueUpsert("guests", "g2", "{}")
        assertEquals(1, queue.drainForOrg("org-a").size)
        assertEquals(1, queue.drainForOrg("org-b").size)
        assertEquals("g1", queue.drainForOrg("org-a").single().documentId)
        assertEquals("g2", queue.drainForOrg("org-b").single().documentId)
    }
}
