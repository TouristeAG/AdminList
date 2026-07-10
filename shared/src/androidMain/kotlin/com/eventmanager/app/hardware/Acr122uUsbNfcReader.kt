package com.eventmanager.app.hardware

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object Acr122uUsbNfcReader {
    /** One open/claim/CCID session at a time across the process (avoids interleaved bulk I/O). */
    private val usbAccessMutex = Mutex()

    private const val ACS_VENDOR_ID = 0x072F
    private const val TAG_PERMISSION_TIMEOUT_MS = 8000L
    private const val USB_BULK_TIMEOUT_MS = 2000

    /** Public action for [UsbManager.requestPermission]; register the same action to detect grants. */
    const val USB_PERMISSION_ACTION = "com.eventmanager.app.USB_PERMISSION_ACR122U"

    data class Result(
        val uid: String? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = !uid.isNullOrBlank()

        /** Transient errors (no card, timing) vs fatal (no device, permission denied). */
        fun shouldRetryUsbPoll(): Boolean {
            if (isSuccess) return false
            return when (error) {
                "USB permission denied",
                "ACR122U not connected",
                "USB manager unavailable" -> false
                else -> true
            }
        }
    }

    suspend fun readUid(context: Context): Result = usbAccessMutex.withLock {
        withContext(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return@withContext Result(error = "USB manager unavailable")

            val device = findCompatibleDevice(usbManager)
                ?: return@withContext Result(error = "ACR122U not connected")

            val hasPermission = ensurePermission(context, usbManager, device)
            if (!hasPermission) {
                return@withContext Result(error = "USB permission denied")
            }

            val deviceToOpen = findCompatibleDevice(usbManager) ?: device
            val connection = usbManager.openDevice(deviceToOpen)
                ?: return@withContext Result(error = "Unable to open USB connection")

            var prepared: PreparedUsb? = null
            try {
                prepared = prepareIo(deviceToOpen, connection)
                    ?: return@withContext Result(error = "Unable to claim ACR122U USB interface")

                val usbIo = prepared.usbIo
                val powerOnResponse = ccidPowerOn(usbIo, sequence = 0x01)
                    ?: return@withContext Result(error = "Reader did not answer to power on")

                if (!powerOnResponse.isStatusOk()) {
                    return@withContext Result(error = "Reader power-on failed (${powerOnResponse.statusText()})")
                }

                // Pseudo-APDU (PC/SC): Get UID -> FF CA 00 00 00
                val uidResponse = ccidTransmit(
                    usbIo = usbIo,
                    sequence = 0x02,
                    apdu = byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00)
                ) ?: return@withContext Result(error = "No response from ACR122U")

                if (!uidResponse.isStatusOk()) {
                    return@withContext Result(error = "ACR122U command failed (${uidResponse.statusText()})")
                }

                val data = uidResponse.data
                if (data.size < 2) {
                    return@withContext Result(error = "Invalid card response")
                }

                val sw1 = data[data.size - 2]
                val sw2 = data[data.size - 1]
                if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
                    return@withContext Result(error = "No card detected on reader")
                }

                val uidBytes = data.copyOfRange(0, data.size - 2)
                if (uidBytes.isEmpty()) {
                    return@withContext Result(error = "Empty card UID")
                }

                Result(uid = uidBytes.toHexUid())
            } finally {
                teardownUsbSession(prepared, connection)
            }
        }
    }

    fun isConnected(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        return findCompatibleDevice(usbManager) != null
    }

    /**
     * `true` if no ACR122U-class reader is plugged in, or the OS has already granted access to it.
     * Use before showing another runtime permission (e.g. camera) so [requestPermission] is not racing
     * another system dialog and cancelling the suspended [readUid]/[ensurePermission] flow.
     */
    fun hasUsbPermissionForConnectedReader(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return true
        val device = findCompatibleDevice(usbManager) ?: return true
        return usbManager.hasPermission(device)
    }

    /** True only when an ACS-class reader is plugged in **and** this app may open it. */
    fun hasUsbDeviceWithPermission(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val device = findCompatibleDevice(usbManager) ?: return false
        return usbManager.hasPermission(device)
    }

    private fun findCompatibleDevice(usbManager: UsbManager): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            val productName = device.productName.orEmpty().lowercase(Locale.US)
            val deviceName = device.deviceName.lowercase(Locale.US)
            device.vendorId == ACS_VENDOR_ID ||
                productName.contains("acr122") ||
                productName.contains("acs") ||
                deviceName.contains("acr122")
        }
    }

    private suspend fun ensurePermission(
        context: Context,
        usbManager: UsbManager,
        device: UsbDevice
    ): Boolean {
        if (usbManager.hasPermission(device)) return true

        val granted = suspendCancellableCoroutine { continuation ->
            val appContext = context.applicationContext
            val completed = AtomicBoolean(false)
            val mainHandler = Handler(Looper.getMainLooper())

            @SuppressLint("InlinedApi")
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // System fills extras on the pending intent for USB permission delivery.
                    PendingIntent.FLAG_MUTABLE
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

            // Targeting API 34+: mutable PendingIntent + implicit Intent is disallowed; scope to our package.
            val usbPermissionIntent = Intent(USB_PERMISSION_ACTION).apply {
                setPackage(appContext.packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(
                appContext,
                device.deviceId,
                usbPermissionIntent,
                pendingFlags
            )

            var receiverRegistered = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != USB_PERMISSION_ACTION) return
                    mainHandler.post {
                        val grantedExtra = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val current = findCompatibleDevice(usbManager)
                        // After grant the OS may re-enumerate USB with a new deviceId; trust hasPermission
                        // on the current compatible device, not only the stale Parcelable id.
                        val hasNow = current != null && usbManager.hasPermission(current)
                        val ok = grantedExtra || hasNow
                        if (completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(ok)
                        }
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {
                            // Receiver already unregistered.
                        }
                    }
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(
                        receiver,
                        IntentFilter(USB_PERMISSION_ACTION),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(receiver, IntentFilter(USB_PERMISSION_ACTION))
                }
                receiverRegistered = true
                usbManager.requestPermission(device, permissionIntent)
            } catch (_: Exception) {
                if (completed.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(false)
                }
            }

            val timeoutThread = Thread {
                try {
                    Thread.sleep(TAG_PERMISSION_TIMEOUT_MS)
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(false)
                    }
                } catch (_: Exception) {
                    // Ignore interruption.
                }
            }
            timeoutThread.isDaemon = true
            timeoutThread.start()

            continuation.invokeOnCancellation {
                timeoutThread.interrupt()
                completed.set(true)
                if (receiverRegistered) {
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (_: Exception) {
                        // Ignore cleanup failures.
                    }
                }
            }
        }

        if (!granted) return false

        // System can report EXTRA_PERMISSION_GRANTED before hasPermission/openDevice work on some devices.
        repeat(30) {
            val d = findCompatibleDevice(usbManager)
            if (d != null && usbManager.hasPermission(d)) return true
            delay(40L)
        }
        val last = findCompatibleDevice(usbManager)
        return last != null && usbManager.hasPermission(last)
    }

    private data class UsbIo(
        val connection: UsbDeviceConnection,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint
    )

    private data class PreparedUsb(
        val usbIo: UsbIo,
        val usbInterface: UsbInterface
    )

    /**
     * Power off the card / RF, release the claimed interface, and close the connection so the ACR122U
     * can detect the next tap. Without IccPowerOff, some readers stay "busy" and the next poll fails or
     * the host never sees a new UID despite the hardware beep.
     */
    private fun teardownUsbSession(prepared: PreparedUsb?, connection: UsbDeviceConnection) {
        try {
            prepared?.let { p ->
                runCatching {
                    ccidPowerOff(p.usbIo, sequence = 0x03)
                    readCcidResponseIgnoringErrors(p.usbIo)
                }
                runCatching {
                    connection.releaseInterface(p.usbInterface)
                }
            }
        } finally {
            runCatching { connection.close() }
        }
    }

    private data class CcidResponse(
        val status: Int,
        val error: Int,
        val data: ByteArray
    ) {
        fun isStatusOk(): Boolean = status == 0x00
        fun statusText(): String = "status=0x%02X,error=0x%02X".format(status, error)
    }

    private fun prepareIo(device: UsbDevice, connection: UsbDeviceConnection): PreparedUsb? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var endpointIn: UsbEndpoint? = null
            var endpointOut: UsbEndpoint? = null

            for (j in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(j)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint
                } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    endpointOut = endpoint
                }
            }

            if (endpointIn != null && endpointOut != null) {
                val claimed = connection.claimInterface(intf, true)
                if (claimed) {
                    return PreparedUsb(UsbIo(connection, endpointIn, endpointOut), intf)
                }
            }
        }
        return null
    }

    private fun ccidPowerOn(usbIo: UsbIo, sequence: Int): CcidResponse? {
        val command = byteArrayOf(
            0x62, // PC_to_RDR_IccPowerOn
            0x00, 0x00, 0x00, 0x00, // dwLength
            0x00, // bSlot
            sequence.toByte(), // bSeq
            0x00, // bPowerSelect
            0x00, // abRFU[0]
            0x00 // abRFU[1]
        )
        if (!writeBulk(usbIo, command)) return null
        return readCcidResponse(usbIo)
    }

    private fun ccidPowerOff(usbIo: UsbIo, sequence: Int): Boolean {
        val command = byteArrayOf(
            0x63, // PC_to_RDR_IccPowerOff
            0x00, 0x00, 0x00, 0x00, // dwLength
            0x00, // bSlot
            sequence.toByte(), // bSeq
            0x00, // abRFU
            0x00,
            0x00
        )
        return writeBulk(usbIo, command)
    }

    private fun ccidTransmit(usbIo: UsbIo, sequence: Int, apdu: ByteArray): CcidResponse? {
        val length = apdu.size
        val command = ByteArray(10 + length)
        command[0] = 0x6F // PC_to_RDR_XfrBlock
        command[1] = (length and 0xFF).toByte()
        command[2] = ((length shr 8) and 0xFF).toByte()
        command[3] = ((length shr 16) and 0xFF).toByte()
        command[4] = ((length shr 24) and 0xFF).toByte()
        command[5] = 0x00 // bSlot
        command[6] = sequence.toByte() // bSeq
        command[7] = 0x00 // bBWI
        command[8] = 0x00 // wLevelParameter low
        command[9] = 0x00 // wLevelParameter high
        apdu.copyInto(command, destinationOffset = 10)

        if (!writeBulk(usbIo, command)) return null
        return readCcidResponse(usbIo)
    }

    private fun writeBulk(usbIo: UsbIo, data: ByteArray): Boolean {
        val written = usbIo.connection.bulkTransfer(
            usbIo.endpointOut,
            data,
            data.size,
            USB_BULK_TIMEOUT_MS
        )
        return written == data.size
    }

    private fun readCcidResponse(usbIo: UsbIo): CcidResponse? {
        val buffer = ByteArray(300)
        val read = usbIo.connection.bulkTransfer(
            usbIo.endpointIn,
            buffer,
            buffer.size,
            USB_BULK_TIMEOUT_MS
        )
        if (read < 10) return null
        if (buffer[0] != 0x80.toByte()) return null // RDR_to_PC_DataBlock

        val dataLength = (
            (buffer[1].toInt() and 0xFF) or
                ((buffer[2].toInt() and 0xFF) shl 8) or
                ((buffer[3].toInt() and 0xFF) shl 16) or
                ((buffer[4].toInt() and 0xFF) shl 24)
            ).coerceAtLeast(0)

        val status = buffer[7].toInt() and 0xFF
        val error = buffer[8].toInt() and 0xFF
        val availableDataLength = (read - 10).coerceAtLeast(0)
        val payloadLength = dataLength.coerceAtMost(availableDataLength)
        val data = if (payloadLength > 0) {
            buffer.copyOfRange(10, 10 + payloadLength)
        } else {
            ByteArray(0)
        }
        return CcidResponse(status = status, error = error, data = data)
    }

    /** After IccPowerOff the reply may differ; drain the IN endpoint without failing teardown. */
    private fun readCcidResponseIgnoringErrors(usbIo: UsbIo) {
        val buffer = ByteArray(300)
        usbIo.connection.bulkTransfer(
            usbIo.endpointIn,
            buffer,
            buffer.size,
            400
        )
    }

    private fun ByteArray.toHexUid(): String = joinToString(separator = "") { byte ->
        "%02X".format(byte)
    }
}
