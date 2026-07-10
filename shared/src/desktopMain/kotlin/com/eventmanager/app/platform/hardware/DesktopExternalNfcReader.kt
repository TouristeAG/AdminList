package com.eventmanager.app.platform.hardware

import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.desktop_bluetooth_nfc_reader
import com.eventmanager.app.resources.desktop_bluetooth_nfc_reader_offline
import com.eventmanager.app.resources.desktop_no_external_reader
import com.eventmanager.app.resources.desktop_usb_nfc_reader
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.UidReadResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

/**
 * Desktop external NFC readers: **USB** (ACR122U via PC/SC) and **Bluetooth** (ACR1255U-J1 via
 * PC/SC after OS pairing). USB is preferred when both are connected.
 */
object DesktopExternalNfcReader {

    private val pcsc = DesktopPcscCardReader()

    fun isUsbConnected(): Boolean = pcsc.hasUsbReader()

    fun isBleConfigured(settings: SettingsManager): Boolean =
        resolveBleReaderId(settings).isNotBlank()

    fun isBleAvailable(settings: SettingsManager): Boolean =
        pcsc.findBleTerminal(resolveBleReaderId(settings), settings.getExternalBleReaderName()) != null

    fun isBleLinkActive(settings: SettingsManager): Boolean = isBleAvailable(settings)

    fun isConnected(settings: SettingsManager): Boolean =
        isUsbConnected() || isBleAvailable(settings)

    fun readerDescription(settings: SettingsManager): String = runBlocking {
        when {
            isUsbConnected() -> pcsc.firstUsbTerminalName()
                ?: getString(Res.string.desktop_usb_nfc_reader)
            isBleAvailable(settings) -> {
                settings.getExternalBleReaderName().ifBlank {
                    pcsc.findBleTerminal(
                        resolveBleReaderId(settings),
                        settings.getExternalBleReaderName()
                    )?.name.orEmpty()
                }.ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader) }
            }
            isBleConfigured(settings) -> settings.getExternalBleReaderName()
                .ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader_offline) }
            else -> getString(Res.string.desktop_no_external_reader)
        }
    }

    suspend fun readUid(settings: SettingsManager): UidReadResult = pcsc.readUid(settings)

    suspend fun runDiagnostic(): DesktopPcscCardReader.DiagnosticResult = pcsc.runDiagnostic()

    suspend fun runBleDiagnostic(settings: SettingsManager): DesktopPcscCardReader.DiagnosticResult =
        pcsc.runBleDiagnostic(settings)

    fun listPcscReadersReport(): String = pcsc.formatTerminalListing()

    fun shutdownBle() {
        pcsc.clearBleSession()
    }

    fun resetForFreshEnrollmentRead() {
        pcsc.clearBleSession()
    }

    internal fun resolveBleReaderId(settings: SettingsManager): String {
        val saved = settings.getExternalBleReaderMac().trim()
        if (saved.isNotEmpty()) return saved
        return pcsc.singleAutoBleTerminalId().orEmpty()
    }
}
