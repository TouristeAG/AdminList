package com.eventmanager.app.platform

import com.eventmanager.app.platform.hardware.DesktopPcscCardReader

actual fun createCardReaderService(context: PlatformContext): CardReaderService =
    DesktopCardReaderService()

private class DesktopCardReaderService : CardReaderService {
    private val reader = DesktopPcscCardReader()

    override fun isReaderConnected(): Boolean = reader.isReaderAvailable()

    override fun shouldSuppressBuiltInNfc(): Boolean = false

    override suspend fun readUid(): UidReadResult = reader.readUid()

    override fun readerDescription(): String = reader.readerName()
}
