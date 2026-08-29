package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetsSyncSequenceCharacterizationTest {

    @Test
    fun catalogContainsRequiredIntents() {
        val required = listOf(
            "afterGuestSaved",
            "afterGuestDeleted",
            "afterTemporaryGuestBatch",
            "afterJobSavedWithCredits",
            "afterBenefitEntryConsumed",
            "afterTransfersChanged",
            "afterInstitutionSettingsChanged",
            "performManualSync",
            "bootstrapPosSession",
            "prepareForAdminGate",
        )
        required.forEach { key ->
            assertTrue(SheetsSyncSequenceCatalog.allSequences.containsKey(key), "Missing sequence: $key")
            assertTrue(SheetsSyncSequenceCatalog.allSequences.getValue(key).isNotEmpty(), "Empty sequence: $key")
        }
    }

    @Test
    fun guestSaveSequenceIncludesBackupAndVolunteerList() {
        val seq = SheetsSyncSequenceCatalog.afterGuestSaved
        assertEquals("twoWaySyncService.backupGuestsToSheets", seq.first())
        assertTrue(seq.any { it.contains("VolunteerGuestList") || it.contains("volunteer") })
    }

    @Test
    fun guestDeleteTracksThenBacksUp() {
        val seq = SheetsSyncSequenceCatalog.afterGuestDeleted
        assertTrue(seq.any { it.contains("trackGuestDeletion") })
        assertTrue(seq.any { it.contains("backupGuestsToSheets") })
    }

    @Test
    fun jobSavedWithCreditsBacksUpTransfers() {
        val seq = SheetsSyncSequenceCatalog.afterJobSavedWithCredits
        assertTrue(seq.any { it.contains("Job") || it.contains("job") })
        assertTrue(seq.any { it.contains("backupTransfersToSheets") })
    }

    @Test
    fun benefitEntrySequenceUpdatesJobs() {
        val seq = SheetsSyncSequenceCatalog.afterBenefitEntryConsumed
        assertTrue(seq.any { it.contains("Job") || it.contains("job") })
    }

    @Test
    fun transferSequenceIsBackupOnly() {
        assertEquals(
            listOf("twoWaySyncService.backupTransfersToSheets"),
            SheetsSyncSequenceCatalog.afterTransfersChanged,
        )
    }

    @Test
    fun institutionSettingsSequenceIsBackupOnly() {
        assertEquals(
            listOf("twoWaySyncService.backupInstitutionSettingsToSheets"),
            SheetsSyncSequenceCatalog.afterInstitutionSettingsChanged,
        )
    }

    @Test
    fun manualSyncInitializesThenMerges() {
        val seq = SheetsSyncSequenceCatalog.performManualSync
        assertTrue(seq.any { it.contains("initializeSheetsService") })
        assertTrue(seq.any { it.contains("backupToGoogleSheets") })
        assertTrue(seq.any { it.contains("performFullSync") })
    }

    @Test
    fun posBootstrapPullsSalesAndTransfers() {
        val seq = SheetsSyncSequenceCatalog.bootstrapPosSession
        assertTrue(seq.any { it.contains("Sales", ignoreCase = true) })
        assertTrue(seq.any { it.contains("Transfer", ignoreCase = true) })
    }

    @Test
    fun adminGateRepairsOrFullSyncs() {
        val seq = SheetsSyncSequenceCatalog.prepareForAdminGate
        assertTrue(seq.any { it.contains("repair") || it.contains("FullSync") || it.contains("performFullSync") })
    }

    @Test
    fun backendTypeDefaultsToSheets() {
        assertEquals(BackendType.SHEETS, BackendType.fromStorage(null))
        assertEquals(BackendType.SHEETS, BackendType.fromStorage(""))
        assertEquals(BackendType.FIREBASE, BackendType.fromStorage("firebase"))
    }

    @Test
    fun desktopSpikeDocumentsPullFallback() {
        val spike = DesktopFirebaseSpike.probe()
        assertTrue(spike.notes.contains("30s") || !spike.listenersAdequate)
    }
}
