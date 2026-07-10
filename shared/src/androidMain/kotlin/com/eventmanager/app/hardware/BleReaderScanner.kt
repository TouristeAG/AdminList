package com.eventmanager.app.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Active BLE scanner focused on ACS `ACR1255U-J1`-class NFC readers.
 *
 * Emits the set of currently-visible devices. The ACR1255U-J1 is a BLE GATT accessory and does
 * **not** show up reliably in Android's system Bluetooth pairing screen, so the app needs its
 * own in-settings picker that runs this scanner and stores the chosen MAC for later
 * [BluetoothAdapter.getRemoteDevice] connections.
 */
object BleReaderScanner {

    /** Bluetooth device the user can pick from the in-app reader picker. */
    data class DiscoveredReader(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val bonded: Boolean,
        /** True if the name strongly looks like an ACS ACR1255U class reader. */
        val matchesAcr1255: Boolean
    )

    sealed class ScanState {
        data object Idle : ScanState()
        data class Scanning(val devices: List<DiscoveredReader>) : ScanState()
        data class Failed(val reason: Reason) : ScanState()

        enum class Reason {
            BluetoothOff,
            BluetoothUnavailable,
            ScanPermissionDenied,
            ConnectPermissionDenied,
            LocationPermissionDenied,
            ScannerUnavailable,
            LowLevelError
        }
    }

    fun hasScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun scanPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    /** Name match for ACR1255U / ACR1255U-J1 / 1255U variants reported by ACS. */
    fun nameMatchesAcr1255(name: String?): Boolean {
        val n = name?.lowercase(Locale.US).orEmpty()
        if (n.isBlank()) return false
        return n.contains("acr1255") ||
            n.contains("1255u-j1") ||
            n.contains("1255u") ||
            (n.contains("acr") && n.contains("1255"))
    }

    /**
     * Start a BLE scan and emit [ScanState] updates. Cancellation stops the scan and releases
     * native callbacks. When [preferAcrOnly] is `true`, only devices that look like ACR1255U-class
     * readers are reported.
     */
    @SuppressLint("MissingPermission")
    fun scan(
        context: Context,
        preferAcrOnly: Boolean = false
    ): Flow<ScanState> = callbackFlow {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasScanPermission(context)) {
            trySend(ScanState.Failed(ScanState.Reason.ScanPermissionDenied))
            close()
            return@callbackFlow
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !hasScanPermission(context)) {
            trySend(ScanState.Failed(ScanState.Reason.LocationPermissionDenied))
            close()
            return@callbackFlow
        }
        if (!Acr1255uj1BleNfcReader.hasBluetoothConnectPermission(context)) {
            trySend(ScanState.Failed(ScanState.Reason.ConnectPermissionDenied))
            close()
            return@callbackFlow
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (manager == null || adapter == null) {
            trySend(ScanState.Failed(ScanState.Reason.BluetoothUnavailable))
            close()
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            trySend(ScanState.Failed(ScanState.Reason.BluetoothOff))
            close()
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            trySend(ScanState.Failed(ScanState.Reason.ScannerUnavailable))
            close()
            return@callbackFlow
        }

        val bonded: Set<String> = try {
            adapter.bondedDevices?.map { it.address }?.toSet().orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        }

        val discovered = linkedMapOf<String, DiscoveredReader>()
        // Seed with bonded ACR1255 devices so they show up immediately even if they don't
        // advertise while we are scanning.
        try {
            adapter.bondedDevices?.forEach { dev ->
                if (nameMatchesAcr1255(dev.name)) {
                    discovered[dev.address] = DiscoveredReader(
                        mac = dev.address,
                        name = dev.name,
                        rssi = Int.MIN_VALUE,
                        bonded = true,
                        matchesAcr1255 = true
                    )
                }
            }
        } catch (_: SecurityException) {
            // Ignore — we'll fall back to scan results.
        }
        if (discovered.isNotEmpty()) {
            trySend(ScanState.Scanning(snapshot(discovered, preferAcrOnly)))
        } else {
            trySend(ScanState.Scanning(emptyList()))
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                handleResult(device, result.rssi)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { r -> handleResult(r.device, r.rssi) }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(ScanState.Failed(ScanState.Reason.LowLevelError))
            }

            private fun handleResult(device: BluetoothDevice, rssi: Int) {
                val name = try {
                    device.name
                } catch (_: SecurityException) {
                    null
                }
                val mac = device.address ?: return
                val prev = discovered[mac]
                discovered[mac] = DiscoveredReader(
                    mac = mac,
                    name = name ?: prev?.name,
                    rssi = rssi,
                    bonded = mac in bonded,
                    matchesAcr1255 = nameMatchesAcr1255(name ?: prev?.name)
                )
                trySend(ScanState.Scanning(snapshot(discovered, preferAcrOnly)))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // No service UUID filter: the reader does not advertise a known standard service, and
        // scanning with an empty filter list is required for device-name-based matching on
        // some stacks.
        val filters = emptyList<ScanFilter>()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (_: SecurityException) {
            trySend(ScanState.Failed(ScanState.Reason.ScanPermissionDenied))
            close()
            return@callbackFlow
        } catch (_: IllegalStateException) {
            trySend(ScanState.Failed(ScanState.Reason.BluetoothOff))
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Ignore on teardown.
            } catch (_: IllegalStateException) {
                // Ignore.
            }
        }
    }

    private fun snapshot(
        map: Map<String, DiscoveredReader>,
        preferAcrOnly: Boolean
    ): List<DiscoveredReader> {
        val values = if (preferAcrOnly) map.values.filter { it.matchesAcr1255 } else map.values
        return values.sortedWith(
            compareByDescending<DiscoveredReader> { it.matchesAcr1255 }
                .thenByDescending { it.bonded }
                .thenByDescending { it.rssi }
        )
    }
}
