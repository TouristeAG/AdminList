package com.eventmanager.app.platform

import android.nfc.NfcAdapter
import com.eventmanager.app.hardware.ExternalAcsUidReader

actual fun createCardReaderService(context: PlatformContext): CardReaderService =
    AndroidCardReaderService(context)

private class AndroidCardReaderService(private val context: PlatformContext) : CardReaderService {
    override fun isReaderConnected(): Boolean =
        ExternalAcsUidReader.isConnected(context.androidContext)

    override fun shouldSuppressBuiltInNfc(): Boolean =
        ExternalAcsUidReader.shouldSuppressPhoneNfcReaderMode(context.androidContext)

    override fun getNfcInputAvailability(): NfcInputAvailability {
        if (isReaderConnected()) return NfcInputAvailability.ExternalReader
        if (shouldSuppressBuiltInNfc()) return NfcInputAvailability.Unavailable
        val adapter = NfcAdapter.getDefaultAdapter(context.androidContext)
            ?: return NfcInputAvailability.Unavailable
        return if (adapter.isEnabled) {
            NfcInputAvailability.BuiltIn
        } else {
            NfcInputAvailability.BuiltInDisabled
        }
    }

    override suspend fun readUid(): UidReadResult = when (val outcome = ExternalAcsUidReader.readUid(context.androidContext)) {
        is ExternalAcsUidReader.ReadOutcome.Success -> UidReadResult.Success(outcome.uid)
        is ExternalAcsUidReader.ReadOutcome.Retryable -> UidReadResult.Retryable(outcome.error)
        is ExternalAcsUidReader.ReadOutcome.Fatal -> UidReadResult.Fatal(outcome.error)
        ExternalAcsUidReader.ReadOutcome.NoReader -> UidReadResult.NoReader
    }

    override fun readerDescription(): String = "Android NFC / ACS reader"
}
