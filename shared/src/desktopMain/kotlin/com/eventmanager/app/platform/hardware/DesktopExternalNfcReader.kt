package com.eventmanager.app.platform.hardware

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.UidReadResult
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.desktop_bluetooth_nfc_reader
import com.eventmanager.app.resources.desktop_bluetooth_nfc_reader_offline
import com.eventmanager.app.resources.desktop_no_external_reader
import com.eventmanager.app.resources.desktop_usb_nfc_reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop external NFC readers: **USB** (ACR122U via PC/SC) and **Bluetooth** (ACR1255U-J1 via
 * PC/SC after OS pairing). USB is preferred when both are connected.
 *
 * On **macOS/Linux**, status APIs are synchronous (pre-Windows-rewrite behaviour) so a plugged
 * USB reader is recognised immediately via [TerminalFactory.getDefault].
 *
 * On **Windows**, sync getters return a **cached** snapshot so Compose / Swing never block on
 * Winscard. Refresh happens on a dedicated daemon thread or via [refreshStatus].
 *
 * BLE "ready" = SoftDevice **present in PC/SC**. We deliberately do **not** CONNECT to probe
 * liveness on Windows — CONNECT probes race SoftDevice Escape UID polls.
 */
object DesktopExternalNfcReader {

    private val pcsc = DesktopPcscCardReader()

    enum class BleLinkState {
        /** No BLE reader saved in settings. */
        NotConfigured,
        /** Saved, but SoftDevice not visible to PC/SC (asleep / BT not connected). */
        Offline,
        /**
         * SoftDevice listed. Kept as a distinct label if we later infer mid-reconnect;
         * currently same as Ready when SoftDevice is present.
         */
        Connecting,
        /** SoftDevice listed in PC/SC — scans can run. */
        Ready,
    }

    data class StatusSnapshot(
        val usbConnected: Boolean = false,
        val usbName: String? = null,
        /** Saved BLE reader in settings (may be offline). */
        val bleConfigured: Boolean = false,
        /** SoftDevice name is present in the PC/SC list. */
        val blePcscPresent: Boolean = false,
        /** SoftDevice present — OK to poll UIDs (no CONNECT probe). */
        val bleAvailable: Boolean = false,
        val bleLinkState: BleLinkState = BleLinkState.NotConfigured,
        val bleTerminalName: String? = null,
        val description: String = "",
        val atMs: Long = 0L,
        val initialized: Boolean = false,
    )

    @Volatile
    private var snapshot = StatusSnapshot()

    private val refreshing = AtomicBoolean(false)
    private val refreshExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "noctulist-pcsc-status").apply { isDaemon = true }
    }

    fun isUsbConnected(): Boolean {
        if (!isWindowsOs()) {
            return pcsc.hasUsbReader()
        }
        scheduleRefresh(settings = null)
        return snapshot.usbConnected
    }

    fun isBleConfigured(settings: SettingsManager): Boolean =
        resolveBleReaderId(settings).isNotBlank()

    fun isBleAvailable(settings: SettingsManager): Boolean {
        if (!isWindowsOs()) {
            return pcsc.findBleTerminal(
                resolveBleReaderId(settings),
                settings.getExternalBleReaderName(),
            ) != null
        }
        scheduleRefresh(settings)
        return snapshot.bleAvailable
    }

    fun isBleLinkActive(settings: SettingsManager): Boolean = isBleAvailable(settings)

    fun bleLinkState(settings: SettingsManager): BleLinkState {
        if (!isWindowsOs()) {
            return when {
                !isBleConfigured(settings) -> BleLinkState.NotConfigured
                isBleAvailable(settings) -> BleLinkState.Ready
                else -> BleLinkState.Offline
            }
        }
        scheduleRefresh(settings)
        return snapshot.bleLinkState
    }

    fun isConnected(settings: SettingsManager): Boolean {
        if (!isWindowsOs()) {
            return isUsbConnected() || isBleAvailable(settings)
        }
        scheduleRefresh(settings)
        return snapshot.usbConnected || snapshot.bleAvailable
    }

    fun readerDescription(settings: SettingsManager): String {
        if (!isWindowsOs()) {
            return runBlocking {
                when {
                    isUsbConnected() -> pcsc.firstUsbTerminalName()
                        ?: getString(Res.string.desktop_usb_nfc_reader)
                    isBleAvailable(settings) -> {
                        settings.getExternalBleReaderName().ifBlank {
                            pcsc.findBleTerminal(
                                resolveBleReaderId(settings),
                                settings.getExternalBleReaderName(),
                            )?.name.orEmpty()
                        }.ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader) }
                    }
                    isBleConfigured(settings) -> settings.getExternalBleReaderName()
                        .ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader_offline) }
                    else -> getString(Res.string.desktop_no_external_reader)
                }
            }
        }
        scheduleRefresh(settings)
        val cached = snapshot.description
        if (cached.isNotBlank() || snapshot.initialized) return cached
        return when {
            snapshot.usbConnected -> snapshot.usbName ?: "USB NFC reader"
            snapshot.bleAvailable -> snapshot.bleTerminalName
                ?: settings.getExternalBleReaderName().ifBlank { "Bluetooth NFC reader" }
            else -> "No external reader"
        }
    }

    /**
     * Blocking PC/SC probe on [Dispatchers.IO]. Prefer this from coroutine UI polls instead of
     * the sync getters when an up-to-date value is required immediately.
     */
    suspend fun refreshStatus(settings: SettingsManager?): StatusSnapshot = withContext(Dispatchers.IO) {
        if (!isWindowsOs()) {
            // Live synchronous snapshot for macOS — no deferred cache.
            val usbConnected = pcsc.hasUsbReader()
            val usbName = pcsc.firstUsbTerminalName()
            val bleConfigured = settings != null && isBleConfigured(settings)
            val bleId = settings?.let { resolveBleReaderIdFromSettings(it) }.orEmpty()
            val bleName = settings?.getExternalBleReaderName().orEmpty()
            val bleTerminal = if (settings != null && (bleId.isNotBlank() || bleName.isNotBlank())) {
                pcsc.findBleTerminal(bleId, bleName)
            } else {
                null
            }
            val bleLive = bleTerminal != null
            val description = when {
                usbConnected -> usbName ?: getString(Res.string.desktop_usb_nfc_reader)
                bleLive -> bleName.ifBlank { bleTerminal?.name.orEmpty() }
                    .ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader) }
                else -> getString(Res.string.desktop_no_external_reader)
            }
            snapshot = StatusSnapshot(
                usbConnected = usbConnected,
                usbName = usbName,
                bleConfigured = bleConfigured,
                blePcscPresent = bleLive,
                bleAvailable = bleLive,
                bleLinkState = when {
                    !bleConfigured -> BleLinkState.NotConfigured
                    bleLive -> BleLinkState.Ready
                    else -> BleLinkState.Offline
                },
                bleTerminalName = bleTerminal?.name,
                description = description,
                atMs = System.currentTimeMillis(),
                initialized = true,
            )
            return@withContext snapshot
        }
        refreshBlocking(settings, force = true)
        snapshot
    }

    suspend fun readUid(settings: SettingsManager): UidReadResult {
        val result = pcsc.readUid(settings)
        if (result is UidReadResult.Success) {
            // SoftDevice answered — bump status cache without an extra CONNECT probe.
            val current = snapshot
            if (current.bleConfigured || current.blePcscPresent) {
                snapshot = current.copy(
                    bleAvailable = true,
                    blePcscPresent = true,
                    bleLinkState = BleLinkState.Ready,
                    atMs = System.currentTimeMillis(),
                )
            }
        }
        return result
    }

    suspend fun runDiagnostic(): DesktopPcscCardReader.DiagnosticResult = pcsc.runDiagnostic()

    suspend fun runBleDiagnostic(settings: SettingsManager): DesktopPcscCardReader.DiagnosticResult =
        pcsc.runBleDiagnostic(settings)

    suspend fun listPcscReadersReport(): String = withContext(Dispatchers.IO) {
        pcsc.formatTerminalListing()
    }

    /** Shared terminal listing so BLE discovery reuses the same Winscard context (IO only). */
    fun listTerminalInfosForBle(): List<DesktopPcscCardReader.TerminalInfo> =
        pcsc.listTerminalInfos()

    fun shutdownBle() {
        pcsc.clearBleSession()
        scheduleRefresh(settings = null, force = true)
    }

    fun resetForFreshEnrollmentRead() {
        pcsc.clearBleSession()
    }

    /** After picking a BLE SoftDevice reader — enable RF poll and refresh status. */
    suspend fun prepareBleReader(settings: SettingsManager) {
        pcsc.clearBleSession()
        pcsc.warmBleSoftDevice(settings)
        refreshStatus(settings)
    }

    internal fun resolveBleReaderId(settings: SettingsManager): String =
        resolveBleReaderIdFromSettings(settings)

    private fun scheduleRefresh(settings: SettingsManager?, force: Boolean = false) {
        if (!isWindowsOs()) return
        val now = System.currentTimeMillis()
        val age = now - snapshot.atMs
        if (!force && snapshot.initialized && age < CACHE_TTL_MS) return
        if (!refreshing.compareAndSet(false, true)) return
        refreshExecutor.execute {
            try {
                refreshBlocking(settings, force = false)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun refreshBlocking(settings: SettingsManager?, force: Boolean) {
        // Skip when a UID read holds the PC/SC mutex — listing while SoftDevice Escape is in
        // flight is a frequent cause of multi-second stalls on Windows.
        pcsc.tryWithIdlePcscAccess {
            refreshBlockingLocked(settings)
        }
    }

    private fun refreshBlockingLocked(settings: SettingsManager?) {
        val usbConnected = pcsc.hasWiredReader()
        val usbName = pcsc.firstUsbTerminalName()
        val bleConfigured = settings != null && isBleConfigured(settings)
        val bleId = settings?.let { resolveBleReaderIdFromSettings(it) }.orEmpty()
        val bleName = settings?.getExternalBleReaderName().orEmpty()
        val bleTerminal = if (settings != null && (bleId.isNotBlank() || bleName.isNotBlank())) {
            pcsc.findBleTerminal(bleId, bleName)
        } else if (!bleConfigured) {
            null
        } else {
            pcsc.findBleTerminal("", "")
        }
        // SoftDevice vanishing from PC/SC is the reliable "BT down / asleep" signal.
        // Do not CONNECT here — that races Escape UID polls and wrecks SoftDevice latency.
        val blePcscPresent = bleTerminal != null
        val bleLive = blePcscPresent
        val bleLinkState = when {
            !bleConfigured -> BleLinkState.NotConfigured
            bleLive -> BleLinkState.Ready
            else -> BleLinkState.Offline
        }
        val description = runBlocking {
            when {
                usbConnected -> usbName ?: getString(Res.string.desktop_usb_nfc_reader)
                bleLive -> {
                    bleName.ifBlank { bleTerminal?.name.orEmpty() }
                        .ifBlank { getString(Res.string.desktop_bluetooth_nfc_reader) }
                }
                else -> getString(Res.string.desktop_no_external_reader)
            }
        }
        snapshot = StatusSnapshot(
            usbConnected = usbConnected,
            usbName = usbName,
            bleConfigured = bleConfigured,
            blePcscPresent = blePcscPresent,
            bleAvailable = bleLive,
            bleLinkState = bleLinkState,
            bleTerminalName = bleTerminal?.name,
            description = description,
            atMs = System.currentTimeMillis(),
            initialized = true,
        )
    }

    private fun resolveBleReaderIdFromSettings(settings: SettingsManager): String {
        val saved = settings.getExternalBleReaderMac().trim()
        if (saved.isNotEmpty()) return saved
        return pcsc.singleAutoBleTerminalId().orEmpty()
    }

    private fun isWindowsOs(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.US).contains("win")

    private const val CACHE_TTL_MS = 1_200L
}
