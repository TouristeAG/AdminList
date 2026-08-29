package com.eventmanager.app.data.remote

/**
 * Characterization baseline: expected Sheets orchestration sequences for key intents.
 * Used by commonTest to guard SheetsRemoteBackend against accidental reordering.
 */
object SheetsSyncSequenceCatalog {

    val afterGuestSaved = listOf(
        "twoWaySyncService.backupGuestsToSheets",
        "recalcAndUploadVolunteerGuestList",
    )

    val afterGuestDeleted = listOf(
        "deletionTracker.trackGuestDeletion",
        "twoWaySyncService.backupGuestsToSheets",
        "recalcAndUploadVolunteerGuestList",
    )

    val afterTemporaryGuestBatch = listOf(
        "googleSheetsService.appendTemporaryGuestManualBatch",
        "refreshTemporaryGuestsFromSheets",
    )

    val afterJobSavedWithCredits = listOf(
        "googleSheetsService.addJobToSheets|backupJobsToSheets",
        "twoWaySyncService.backupTransfersToSheets",
        "recalcAndUploadVolunteerGuestList",
    )

    val afterBenefitEntryConsumed = listOf(
        "googleSheetsService.updateJobInSheets|backupJobsToSheets",
        "recalcAndUploadVolunteerGuestList",
    )

    val afterTransfersChanged = listOf(
        "twoWaySyncService.backupTransfersToSheets",
    )

    val afterInstitutionSettingsChanged = listOf(
        "twoWaySyncService.backupInstitutionSettingsToSheets",
    )

    val performManualSync = listOf(
        "googleSheetsService.initializeSheetsService",
        "twoWaySyncService.backupToGoogleSheets",
        "syncManager.performFullSync",
    )

    val bootstrapPosSession = listOf(
        "syncManager.performSalesSheetItemDifferentialSync",
        "syncManager.performTransferDifferentialSync",
    )

    val prepareForAdminGate = listOf(
        "syncManager.repairSheetStructureThenFullDownload|performFullSync",
    )

    /** All catalogued sequences — used by characterization tests. */
    val allSequences: Map<String, List<String>> = mapOf(
        "afterGuestSaved" to afterGuestSaved,
        "afterGuestDeleted" to afterGuestDeleted,
        "afterTemporaryGuestBatch" to afterTemporaryGuestBatch,
        "afterJobSavedWithCredits" to afterJobSavedWithCredits,
        "afterBenefitEntryConsumed" to afterBenefitEntryConsumed,
        "afterTransfersChanged" to afterTransfersChanged,
        "afterInstitutionSettingsChanged" to afterInstitutionSettingsChanged,
        "performManualSync" to performManualSync,
        "bootstrapPosSession" to bootstrapPosSession,
        "prepareForAdminGate" to prepareForAdminGate,
    )
}
