package com.eventmanager.app.platform

sealed class UidReadResult {
    data class Success(val uid: String) : UidReadResult()
    data class Retryable(val error: String?) : UidReadResult()
    data class Fatal(val error: String?) : UidReadResult()
    data object NoReader : UidReadResult()
}

/**
 * NFC card reader abstraction (phone NFC, USB ACR122U, BLE ACR1255 on Android; PC/SC on desktop).
 */
enum class NfcInputAvailability {
    ExternalReader,
    BuiltIn,
    BuiltInDisabled,
    Unavailable,
}

interface CardReaderService {
    fun isReaderConnected(): Boolean
    fun shouldSuppressBuiltInNfc(): Boolean
    fun getNfcInputAvailability(): NfcInputAvailability
    suspend fun readUid(): UidReadResult
    fun readerDescription(): String

    /**
     * Refresh hardware connection status off the UI thread when needed.
     * Desktop PC/SC probes block Winscard; Android no-ops (sync USB/BLE checks are cheap).
     */
    suspend fun refreshConnectionState() {}
}

expect fun createCardReaderService(context: PlatformContext): CardReaderService
