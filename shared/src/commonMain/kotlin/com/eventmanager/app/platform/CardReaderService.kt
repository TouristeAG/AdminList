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
interface CardReaderService {
    fun isReaderConnected(): Boolean
    fun shouldSuppressBuiltInNfc(): Boolean
    suspend fun readUid(): UidReadResult
    fun readerDescription(): String
}

expect fun createCardReaderService(context: PlatformContext): CardReaderService
