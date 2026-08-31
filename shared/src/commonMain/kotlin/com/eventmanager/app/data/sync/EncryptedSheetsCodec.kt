package com.eventmanager.app.data.sync

import com.eventmanager.app.data.security.crypto.SensitiveFieldCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted Google Sheets row format: id | lastModified | payload_enc
 * Dual-format reader detects legacy column layouts vs encrypted payload column.
 */
object EncryptedSheetsCodec {
    const val PAYLOAD_ENC_COLUMN = "payload_enc"
    const val ENCRYPTED_HEADER_ID = "id"
    const val ENCRYPTED_HEADER_LAST_MODIFIED = "lastModified"

    private val json = Json { ignoreUnknownKeys = true }

    fun isEncryptedRow(row: List<Any?>): Boolean {
        if (row.size < 3) return false
        val third = row[2].toString()
        return third.startsWith("v1:") || row.getOrNull(1)?.toString()?.toLongOrNull() != null &&
            third.isNotBlank() && !looksLikeLegacyGuestRow(row)
    }

    private fun looksLikeLegacyGuestRow(row: List<Any?>): Boolean {
        // Legacy guest rows have name in col 0 and numeric invitations in col 3
        return row.size >= 4 && row[3].toString().toIntOrNull() != null && row[0].toString().isNotBlank()
    }

    fun buildEncryptedRow(id: String, lastModified: Long, payloadEnc: String): List<Any?> =
        listOf(id, lastModified.toString(), payloadEnc)

    @Serializable
    data class GuestPayload(
        val name: String,
        val email: String = "",
        val phoneNumber: String = "",
        val invitations: Int = 1,
        val venueName: String = "",
        val notes: String = "",
        val isVolunteerBenefit: Boolean = false,
        val nfcCardUid: String = "",
        val isAdmin: Boolean = false,
        val lastNameAbbreviation: String = "",
        val isTemporaryGuest: Boolean = false,
        val temporaryArtistName: String = "",
        val temporaryEventDate: Long? = null,
        val temporaryContactPhone: String = "",
        val volunteerId: String? = null,
        val nanoId: String = "",
    )

    fun encodeGuestPayload(guest: com.eventmanager.app.data.models.Guest, orgId: String): String {
        val payload = GuestPayload(
            name = guest.name,
            email = guest.email,
            phoneNumber = guest.phoneNumber,
            invitations = guest.invitations,
            venueName = guest.venueName,
            notes = guest.notes,
            isVolunteerBenefit = guest.isVolunteerBenefit,
            nfcCardUid = guest.nfcCardUid,
            isAdmin = guest.isAdmin,
            lastNameAbbreviation = guest.lastNameAbbreviation,
            isTemporaryGuest = guest.isTemporaryGuest,
            temporaryArtistName = guest.temporaryArtistName,
            temporaryEventDate = guest.temporaryEventDate,
            temporaryContactPhone = guest.temporaryContactPhone,
            volunteerId = guest.volunteerId,
            nanoId = guest.nanoId,
        )
        val plain = json.encodeToString(payload)
        return SensitiveFieldCodec.encryptPayloadJson(plain, orgId)
    }

    fun decodeGuestPayload(payloadEnc: String, orgId: String): GuestPayload? = runCatching {
        val plain = SensitiveFieldCodec.decryptPayloadJson(payloadEnc, orgId)
        json.decodeFromString<GuestPayload>(plain)
    }.getOrNull()
}
