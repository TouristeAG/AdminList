package com.eventmanager.app.wallet

data class WalletPassRequest(
    val serialNumber: String,
    val holderName: String,
    val qrPayload: String,
    val associationName: String = "Collectif Nocturne",
)

data class WalletPassImages(
    val files: Map<String, ByteArray>,
)

data class WalletPassSigningConfig(
    val passTypeIdentifier: String,
    val teamIdentifier: String,
    val certificateBytes: ByteArray,
    val certificatePassword: CharArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WalletPassSigningConfig) return false
        return passTypeIdentifier == other.passTypeIdentifier &&
            teamIdentifier == other.teamIdentifier &&
            certificateBytes.contentEquals(other.certificateBytes) &&
            certificatePassword.contentEquals(other.certificatePassword)
    }

    override fun hashCode(): Int {
        var result = passTypeIdentifier.hashCode()
        result = 31 * result + teamIdentifier.hashCode()
        result = 31 * result + certificateBytes.contentHashCode()
        result = 31 * result + certificatePassword.contentHashCode()
        return result
    }
}
