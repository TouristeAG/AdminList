package com.eventmanager.app.hardware

import android.content.Context

/**
 * ACS external UID readers: **USB** (ACR122U-class via [Acr122uUsbNfcReader]) or **Bluetooth LE**
 * **ACR1255U-J1** via [Acr1255uj1BleNfcReader]. USB is preferred when both are connected.
 */
object ExternalAcsUidReader {

    fun isConnected(context: Context): Boolean =
        Acr122uUsbNfcReader.isConnected(context) ||
            Acr1255uj1BleNfcReader.isReaderAvailable(context)

    /**
     * When true, skip [android.nfc.NfcAdapter.enableReaderMode] on the phone: an **authorized**
     * USB CCID session can fight the built-in NFC stack on some devices. If a USB reader is only
     * plugged in but this app has no [UsbManager] access (e.g. user chose another default handler,
     * or permission was revoked), this returns false so phone NFC still works while [readUid]
     * requests or waits for USB access.
     */
    fun shouldSuppressPhoneNfcReaderMode(context: Context): Boolean =
        Acr122uUsbNfcReader.hasUsbDeviceWithPermission(context)

    sealed class ReadOutcome {
        data class Success(val uid: String) : ReadOutcome()
        data class Retryable(val error: String?) : ReadOutcome()
        data class Fatal(val error: String?) : ReadOutcome()
        data object NoReader : ReadOutcome()
    }

    suspend fun readUid(context: Context): ReadOutcome {
        return when {
            Acr122uUsbNfcReader.isConnected(context) -> {
                val r = Acr122uUsbNfcReader.readUid(context)
                when {
                    r.isSuccess -> ReadOutcome.Success(r.uid!!)
                    !r.shouldRetryUsbPoll() -> ReadOutcome.Fatal(r.error)
                    else -> ReadOutcome.Retryable(r.error)
                }
            }
            Acr1255uj1BleNfcReader.isReaderAvailable(context) -> {
                val r = Acr1255uj1BleNfcReader.readUid(context)
                when {
                    r.isSuccess -> ReadOutcome.Success(r.uid!!)
                    !r.shouldRetryPoll() -> ReadOutcome.Fatal(r.error)
                    else -> ReadOutcome.Retryable(r.error)
                }
            }
            else -> ReadOutcome.NoReader
        }
    }

    /**
     * Drives the ACR1255U-J1 status LEDs (ACS `E0…29` escape via [Acr1255uj1BleNfcReader]) after
     * the app has decided whether access is **granted** or **denied**. No-op when no BLE reader is
     * configured, when a USB ACR122U session takes priority, or when the firmware rejects the
     * escape (older firmware / wrong mode).
     */
    suspend fun feedbackBleAccessOutcome(context: Context, granted: Boolean) {
        if (!Acr1255uj1BleNfcReader.isReaderAvailable(context)) return
        if (Acr122uUsbNfcReader.isConnected(context)) return
        Acr1255uj1BleNfcReader.feedbackAccessOutcome(context.applicationContext, granted)
    }

    /**
     * Drops any cached external-reader session state before a new enrollment flow starts.
     *
     * This is intentionally BLE-only: USB reads are already stateless per call.
     */
    fun resetForFreshEnrollmentRead() {
        Acr1255uj1BleNfcReader.shutdown()
    }
}
