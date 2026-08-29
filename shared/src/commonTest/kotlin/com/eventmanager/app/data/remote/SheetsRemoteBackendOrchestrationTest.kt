package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Locks SheetsRemoteBackend source against the characterization catalog (not only string lists).
 */
class SheetsRemoteBackendOrchestrationTest {

    private fun sheetsRemoteBackendSource(): String {
        val candidates = listOf(
            "src/commonMain/kotlin/com/eventmanager/app/data/remote/SheetsRemoteBackend.kt",
            "shared/src/commonMain/kotlin/com/eventmanager/app/data/remote/SheetsRemoteBackend.kt",
            "../shared/src/commonMain/kotlin/com/eventmanager/app/data/remote/SheetsRemoteBackend.kt",
        )
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("SheetsRemoteBackend.kt not found from cwd=${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun temporaryGuestBatchAppendsThenRefreshes() {
        val src = sheetsRemoteBackendSource()
        val method = src.substringAfter("override suspend fun afterTemporaryGuestBatch")
            .substringBefore("override suspend fun afterVolunteerSaved")
        assertTrue(method.contains("appendTemporaryGuestManualBatch"))
        assertTrue(method.contains("onTemporaryGuestsRefresh"))
        assertTrue(
            method.indexOf("appendTemporaryGuestManualBatch") <
                method.indexOf("onTemporaryGuestsRefresh"),
        )
    }

    @Test
    fun venueAnnouncementUsesCellWritesNotFullBackupFirst() {
        val src = sheetsRemoteBackendSource()
        val method = src.substringAfter("override suspend fun sendVenueAnnouncement")
            .substringBefore("private fun parseAnnouncement")
        assertTrue(method.contains("syncManager.sendAnnouncement"))
        assertTrue(method.contains("backupVenuesToSheets"))
        assertTrue(
            method.indexOf("sendAnnouncement") < method.indexOf("backupVenuesToSheets"),
        )
    }

    @Test
    fun manualSyncInitializesThenBacksUpThenFullSync() {
        val src = sheetsRemoteBackendSource()
        val method = src.substringAfter("override suspend fun performManualSync")
            .substringBefore("override suspend fun performPageChangeSync")
        val init = method.indexOf("initializeSheetsService")
        val backup = method.indexOf("backupToGoogleSheets")
        val full = method.indexOf("performFullSync")
        assertTrue(init >= 0 && backup >= 0 && full >= 0)
        assertTrue(init < backup && backup < full)
    }

    @Test
    fun catalogManualSyncMatchesImplementationTokens() {
        val expected = SheetsSyncSequenceCatalog.performManualSync
        assertEquals("googleSheetsService.initializeSheetsService", expected[0])
        assertEquals("twoWaySyncService.backupToGoogleSheets", expected[1])
        assertEquals("syncManager.performFullSync", expected[2])
    }

    @Test
    fun queueAcknowledgeRequiredAfterDrain() = runBlocking {
        val queue = PendingRemoteWriteQueue()
        queue.enqueueUpsert("guests", "g1", "{}")
        val drained = queue.drain()
        assertEquals(1, drained.size)
        // drain no longer clears — acknowledge must remove
        assertEquals(1, queue.drain().size)
        queue.acknowledge(drained.single().id)
        assertTrue(queue.drain().isEmpty())
    }
}
