package com.eventmanager.app.platform

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.hardware.DesktopExternalNfcReader

actual fun createCardReaderService(context: PlatformContext): CardReaderService =
    DesktopCardReaderService(context)

private class DesktopCardReaderService(private val context: PlatformContext) : CardReaderService {
    private val settings: SettingsManager by lazy {
        SettingsManager(createAppStorage(context))
    }

    override fun isReaderConnected(): Boolean = DesktopExternalNfcReader.isConnected(settings)

    override fun shouldSuppressBuiltInNfc(): Boolean = false

    override fun getNfcInputAvailability(): NfcInputAvailability =
        if (isReaderConnected()) NfcInputAvailability.ExternalReader else NfcInputAvailability.Unavailable

    override suspend fun readUid(): UidReadResult = DesktopExternalNfcReader.readUid(settings)

    override fun readerDescription(): String = DesktopExternalNfcReader.readerDescription(settings)
}
