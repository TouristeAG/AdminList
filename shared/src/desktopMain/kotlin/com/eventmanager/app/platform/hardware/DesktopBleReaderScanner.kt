package com.eventmanager.app.platform.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.regex.Pattern

/**
 * Desktop discovery for ACR1255U-J1-class BLE NFC readers.
 *
 * On desktop the reader is used through the OS PC/SC stack once it is paired in system
 * Bluetooth settings and the ACS driver is installed. This scanner lists both PC/SC terminals
 * (ready to read) and paired Bluetooth devices (helpful when the driver is not installed yet).
 */
object DesktopBleReaderScanner {

    private val macPattern = Pattern.compile(
        "([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}"
    )

    data class DiscoveredReader(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val bonded: Boolean,
        val matchesAcr1255: Boolean,
        /** `true` when the reader is already visible to PC/SC and can be used immediately. */
        val pcscReady: Boolean
    )

    sealed class ScanState {
        data object Idle : ScanState()
        data class Scanning(val devices: List<DiscoveredReader>) : ScanState()
        data class Failed(val reason: Reason) : ScanState()

        enum class Reason {
            BluetoothUnavailable,
            LowLevelError
        }
    }

    fun nameMatchesAcr1255(name: String?): Boolean {
        val n = name?.lowercase(Locale.US).orEmpty()
        if (n.isBlank()) return false
        return n.contains("acr1255") ||
            n.contains("1255u-j1") ||
            n.contains("1255u") ||
            (n.contains("acr") && n.contains("1255"))
    }

    fun scan(preferAcrOnly: Boolean = true): Flow<ScanState> = callbackFlow {
        trySend(ScanState.Scanning(emptyList()))
        while (isActive) {
            val snapshot = runCatching { discoverReaders(preferAcrOnly) }.getOrElse {
                trySend(ScanState.Failed(ScanState.Reason.LowLevelError))
                emptyList()
            }
            trySend(ScanState.Scanning(snapshot))
            delay(2_000)
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    /** `true` when ACS provides a PC/SC Bluetooth driver (Windows only). */
    fun isBlePcscSupportedOnPlatform(): Boolean = currentOsFamily() == OsFamily.WINDOWS

    fun discoverReaders(preferAcrOnly: Boolean = true): List<DiscoveredReader> {
        val byId = linkedMapOf<String, DiscoveredReader>()
        val hasUsbReader = DesktopExternalNfcReader.isUsbConnected()

        DesktopExternalNfcReader.listTerminalInfosForBle()
            .filter {
                it.kind == DesktopPcscCardReader.ReaderKind.BLE &&
                    !DesktopPcscCardReader.isContactInterfaceOnly(it.name)
            }
            .forEach { info ->
                val id = DesktopPcscCardReader.terminalId(info.name)
                byId[id] = DiscoveredReader(
                    mac = id,
                    name = info.name,
                    rssi = Int.MAX_VALUE,
                    bonded = true,
                    matchesAcr1255 = true,
                    pcscReady = true
                )
            }

        // Paired Bluetooth devices are only useful when no USB reader is active and the OS
        // supports ACS BLE PC/SC (Windows). On macOS/Linux there is no BLE PC/SC driver.
        if (!hasUsbReader && isBlePcscSupportedOnPlatform()) {
            listPairedBluetoothDevices().forEach { bt ->
                if (preferAcrOnly && !bt.matchesAcr1255) return@forEach
                val existing = byId[bt.mac]
                if (existing == null) {
                    byId[bt.mac] = bt
                } else if (!existing.pcscReady && bt.bonded) {
                    byId[bt.mac] = existing.copy(
                        name = existing.name ?: bt.name,
                        bonded = true
                    )
                }
            }
        }

        return byId.values.sortedWith(
            compareByDescending<DiscoveredReader> { it.pcscReady }
                .thenByDescending { it.matchesAcr1255 }
                .thenByDescending { it.bonded }
                .thenByDescending { it.rssi }
        )
    }

    private fun listPairedBluetoothDevices(): List<DiscoveredReader> {
        return when (currentOsFamily()) {
            OsFamily.MACOS -> parseMacOsBluetooth()
            OsFamily.WINDOWS -> parseWindowsBluetooth()
            OsFamily.LINUX -> parseLinuxBluetooth()
            OsFamily.OTHER -> emptyList()
        }
    }

    private enum class OsFamily { MACOS, WINDOWS, LINUX, OTHER }

    private fun currentOsFamily(): OsFamily {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.US)
        return when {
            os.contains("mac") || os.contains("darwin") -> OsFamily.MACOS
            os.contains("win") -> OsFamily.WINDOWS
            os.contains("linux") -> OsFamily.LINUX
            else -> OsFamily.OTHER
        }
    }

    private fun parseMacOsBluetooth(): List<DiscoveredReader> {
        val output = runCommand("system_profiler", "SPBluetoothDataType") ?: return emptyList()
        val devices = mutableListOf<DiscoveredReader>()
        var currentName: String? = null
        var currentMac: String? = null
        var connected = false

        fun flush() {
            val mac = currentMac?.uppercase(Locale.US) ?: return
            val name = currentName
            devices += DiscoveredReader(
                mac = mac,
                name = name,
                rssi = Int.MIN_VALUE,
                bonded = true,
                matchesAcr1255 = nameMatchesAcr1255(name),
                pcscReady = false
            )
        }

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.endsWith(":") && !line.contains("Address") -> {
                    flush()
                    currentName = line.removeSuffix(":").trim()
                    currentMac = null
                    connected = false
                }
                line.startsWith("Address:") -> {
                    currentMac = line.substringAfter(":").trim()
                }
                line.startsWith("Connected:") -> {
                    connected = line.substringAfter(":").trim().equals("Yes", ignoreCase = true)
                }
            }
        }
        flush()
        return devices.distinctBy { it.mac }
    }

    private fun parseWindowsBluetooth(): List<DiscoveredReader> {
        val script = """
            Get-PnpDevice -Class Bluetooth -ErrorAction SilentlyContinue |
            Where-Object { ${'$'}_.FriendlyName } |
            ForEach-Object { "${'$'}(${'$'}_.InstanceId)|${'$'}(${'$'}_.FriendlyName)" }
        """.trimIndent()
        val output = runCommand("powershell", "-NoProfile", "-Command", script) ?: return emptyList()
        return output.lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val instanceId = parts[0]
                val name = parts[1].trim()
                val mac = extractMac(instanceId) ?: return@mapNotNull null
                DiscoveredReader(
                    mac = mac,
                    name = name,
                    rssi = Int.MIN_VALUE,
                    bonded = true,
                    matchesAcr1255 = nameMatchesAcr1255(name),
                    pcscReady = false
                )
            }
            .distinctBy { it.mac }
            .toList()
    }

    private fun parseLinuxBluetooth(): List<DiscoveredReader> {
        val output = runCommand("bluetoothctl", "devices", "Paired") ?: return emptyList()
        return output.lineSequence()
            .mapNotNull { line ->
                if (!line.startsWith("Device ")) return@mapNotNull null
                val tokens = line.split(" ", limit = 3)
                if (tokens.size < 3) return@mapNotNull null
                val mac = tokens[1].uppercase(Locale.US)
                val name = tokens[2].trim()
                DiscoveredReader(
                    mac = mac,
                    name = name,
                    rssi = Int.MIN_VALUE,
                    bonded = true,
                    matchesAcr1255 = nameMatchesAcr1255(name),
                    pcscReady = false
                )
            }
            .distinctBy { it.mac }
            .toList()
    }

    private fun extractMac(text: String): String? {
        val matcher = macPattern.matcher(text.uppercase(Locale.US))
        return if (matcher.find()) matcher.group().replace('-', ':') else null
    }

    private fun runCommand(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() == 0) output else output.takeIf { it.isNotBlank() }
    }.getOrNull()
}
