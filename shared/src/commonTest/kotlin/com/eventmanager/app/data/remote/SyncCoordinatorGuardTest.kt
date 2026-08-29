package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PendingRemoteWriteQueueTest {

    @Test
    fun enqueueAndDrainPreservesOrderUntilAck() = runBlocking {
        val queue = PendingRemoteWriteQueue()
        queue.enqueueUpsert("guests", "g1", "{\"name\":\"A\"}")
        queue.enqueueDelete("guests", "g2")
        queue.enqueueUpsert("jobs", "j1", "{\"jobNanoId\":\"j1\"}")
        val drained = queue.drain()
        assertEquals(3, drained.size)
        assertEquals("UPSERT", drained[0].operation)
        assertEquals("guests", drained[0].collection)
        assertEquals("DELETE", drained[1].operation)
        assertEquals("jobs", drained[2].collection)
        // Still present until acknowledged
        assertEquals(3, queue.drain().size)
        drained.forEach { queue.acknowledge(it.id) }
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun acknowledgeRemovesById() = runBlocking {
        val queue = PendingRemoteWriteQueue()
        queue.enqueueUpsert("venues", "v1", "{}")
        val first = queue.drain().single()
        queue.enqueueUpsert("venues", "v2", "{}")
        queue.acknowledge(first.id)
        val remaining = queue.drain()
        assertEquals(1, remaining.size)
        assertEquals("v2", remaining.single().documentId)
    }

    @Test
    fun enqueueDedupesByCollectionAndDocument() = runBlocking {
        val queue = PendingRemoteWriteQueue()
        queue.enqueueUpsert("guests", "g1", "{\"v\":1}")
        queue.enqueueUpsert("guests", "g1", "{\"v\":2}")
        val drained = queue.drain()
        assertEquals(1, drained.size)
        assertTrue(drained.single().payloadJson.contains("v\":2"))
    }
}
