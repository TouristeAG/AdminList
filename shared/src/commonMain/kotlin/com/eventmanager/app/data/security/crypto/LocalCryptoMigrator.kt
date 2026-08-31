package com.eventmanager.app.data.security.crypto

import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.SettingsManager
import kotlinx.coroutines.flow.first

/**
 * Background re-encryption of existing Room rows after org passphrase is configured.
 */
object LocalCryptoMigrator {
    suspend fun migrateIfNeeded(repository: EventManagerRepository, settingsManager: SettingsManager) {
        val orgId = settingsManager.getFirebaseOrgId().trim()
        if (orgId.isBlank() || !OrgCryptoRegistry.get().isConfigured(orgId)) return
        if (settingsManager.isLocalCryptoMigrationDone(orgId)) return

        repository.getAllGuestsOnce().forEach { guest ->
            val encrypted = SensitiveFieldCodec.encryptGuestFields(guest.copy(firebaseOrgId = orgId))
            if (encrypted != guest) repository.updateGuest(encrypted)
        }
        repository.getAllVolunteersOnce().forEach { volunteer ->
            val encrypted = SensitiveFieldCodec.encryptVolunteerFields(volunteer.copy(firebaseOrgId = orgId))
            if (encrypted != volunteer) repository.updateVolunteer(encrypted)
        }
        repository.getAllAccountTransfersOnce().forEach { transfer ->
            val encrypted = SensitiveFieldCodec.encryptTransferFields(transfer.copy(firebaseOrgId = orgId))
            if (encrypted != transfer) repository.updateAccountTransfer(encrypted)
        }
        settingsManager.markLocalCryptoMigrationDone(orgId)
    }
}
