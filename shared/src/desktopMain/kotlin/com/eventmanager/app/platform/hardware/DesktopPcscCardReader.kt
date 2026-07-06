package com.eventmanager.app.platform.hardware

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.UidReadResult
import jnasmartcardio.Smartcardio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.Security
import java.util.Locale
import javax.smartcardio.Card
import javax.smartcardio.CardException
import javax.smartcardio.CardNotPresentException
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.TerminalFactory

/**
 * PC/SC NFC readers on desktop: USB (ACR122U) and Bluetooth (ACR1255U-J1 after OS pairing).
 */
class DesktopPcscCardReader {

    data class DiagnosticResult(val success: Boolean, val details: String)

    data class TerminalInfo(
        val terminal: CardTerminal,
        val name: String,
        val kind: ReaderKind
    )

    enum class ReaderKind { USB, BLE, OTHER }

    private val accessMutex = Mutex()
    private val pcscAvailable: Boolean by lazy { ensurePcscProvider() }

    @Volatile private var lastDispatchedUid: String? = null
    @Volatile private var lastDispatchAtMs: Long = 0L
    @Volatile private var awaitingCardRemoval: Boolean = false

    fun isReaderAvailable(): Boolean = pcscAvailable && listTerminals().isNotEmpty()

    fun hasUsbReader(): Boolean = listTerminalInfos().any { it.kind == ReaderKind.USB }

    fun firstUsbTerminalName(): String? =
        listTerminalInfos().firstOrNull { it.kind == ReaderKind.USB }?.name

    fun readerName(): String = selectTerminal(null)?.name ?: if (pcscAvailable) {
        "No PC/SC reader"
    } else {
        "PC/SC unavailable"
    }

    fun listReaderNames(): List<String> = listTerminals().map { it.name }

    fun listTerminalInfos(): List<TerminalInfo> = listTerminals().map { terminal ->
        val name = terminal.name.orEmpty()
        TerminalInfo(terminal = terminal, name = name, kind = classifyTerminal(name))
    }

    fun findBleTerminal(savedReaderId: String, savedReaderName: String): CardTerminal? =
        selectBleTerminal(savedReaderId, savedReaderName)

    fun singleAutoBleTerminalId(): String? {
        val ble = listTerminalInfos().filter { it.kind == ReaderKind.BLE }
        if (ble.size != 1) return null
        return terminalId(ble.first().name)
    }

    fun clearBleSession() {
        awaitingCardRemoval = false
        lastDispatchedUid = null
        lastDispatchAtMs = 0L
    }

    suspend fun readUid(settings: SettingsManager? = null): UidReadResult = accessMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!pcscAvailable) return@withContext UidReadResult.Fatal("PC/SC library unavailable")
            val terminal = selectTerminal(settings) ?: return@withContext UidReadResult.NoReader
            readUidFromTerminal(terminal)
        }
    }

    suspend fun runDiagnostic(): DiagnosticResult = runDiagnosticInternal(selectTerminal(null))

    suspend fun runBleDiagnostic(settings: SettingsManager): DiagnosticResult {
        val terminal = selectBleTerminal(
            DesktopExternalNfcReader.resolveBleReaderId(settings),
            settings.getExternalBleReaderName()
        )
        return runDiagnosticInternal(terminal, bleOnly = true)
    }

    private suspend fun runDiagnosticInternal(
        terminal: CardTerminal?,
        bleOnly: Boolean = false
    ): DiagnosticResult = accessMutex.withLock {
        withContext(Dispatchers.IO) {
            val details = StringBuilder()
            if (!pcscAvailable) {
                return@withContext DiagnosticResult(
                    success = false,
                    details = "PC/SC provider failed to load.\nEnsure PC/SC is installed and restart the app."
                )
            }
            val terminals = listTerminalInfos()
            if (terminals.isEmpty()) {
                return@withContext DiagnosticResult(
                    success = false,
                    details = if (bleOnly) {
                        "No Bluetooth NFC reader in PC/SC.\nPair the ACR1255U-J1 in system Bluetooth settings and install the ACS driver."
                    } else {
                        "No PC/SC readers found.\nPlug in a USB reader or pair a Bluetooth ACR1255U-J1."
                    }
                )
            }
            details.appendLine("Readers (${terminals.size}):")
            terminals.forEach { info ->
                val tag = when (info.kind) {
                    ReaderKind.USB -> "USB"
                    ReaderKind.BLE -> "BLE"
                    ReaderKind.OTHER -> "PC/SC"
                }
                details.appendLine(" • [$tag] ${info.name}")
            }
            val selected = terminal ?: return@withContext DiagnosticResult(
                success = false,
                details = details.appendLine("Unable to select a reader.").toString().trimEnd()
            )
            details.appendLine("Using: ${selected.name}")
            when (val result = readUidFromTerminal(selected)) {
                is UidReadResult.Success -> {
                    details.appendLine("UID: ${result.uid}")
                    DiagnosticResult(success = true, details = details.toString().trimEnd())
                }
                is UidReadResult.Retryable -> DiagnosticResult(
                    success = false,
                    details = details.appendLine(result.error ?: "Place a card on the reader and test again.")
                        .toString().trimEnd()
                )
                is UidReadResult.Fatal -> DiagnosticResult(
                    success = false,
                    details = details.appendLine(result.error ?: "Reader error").toString().trimEnd()
                )
                UidReadResult.NoReader -> DiagnosticResult(
                    success = false,
                    details = details.appendLine("Reader disconnected.").toString().trimEnd()
                )
            }
        }
    }

    private fun ensurePcscProvider(): Boolean = runCatching {
        if (Security.getProvider(Smartcardio.PROVIDER_NAME) == null) {
            Security.insertProviderAt(Smartcardio(), 1)
        }
        TerminalFactory.getDefault()
        true
    }.getOrDefault(false)

    private fun listTerminals(): List<CardTerminal> = runCatching {
        TerminalFactory.getDefault().terminals().list()
    }.getOrDefault(emptyList())

    private fun selectTerminal(settings: SettingsManager?): CardTerminal? {
        val usb = listTerminalInfos().firstOrNull { it.kind == ReaderKind.USB }
        if (usb != null) return usb.terminal

        val savedId = settings?.getExternalBleReaderMac()?.trim().orEmpty()
        val savedName = settings?.getExternalBleReaderName()?.trim().orEmpty()
        return selectBleTerminal(savedId, savedName)
    }

    private fun selectBleTerminal(savedReaderId: String, savedReaderName: String): CardTerminal? {
        val bleTerminals = listTerminalInfos().filter { it.kind == ReaderKind.BLE }
        if (bleTerminals.isEmpty()) return null

        if (savedReaderId.isNotBlank()) {
            bleTerminals.firstOrNull { terminalMatchesId(it, savedReaderId) }?.terminal?.let { return it }
        }
        if (savedReaderName.isNotBlank()) {
            val hint = savedReaderName.lowercase(Locale.US)
            bleTerminals.firstOrNull { it.name.lowercase(Locale.US).contains(hint) }?.terminal?.let { return it }
        }
        if (bleTerminals.size == 1) return bleTerminals.first().terminal
        return null
    }

    private fun terminalMatchesId(info: TerminalInfo, savedReaderId: String): Boolean {
        if (savedReaderId.equals(info.name, ignoreCase = true)) return true
        if (savedReaderId.equals(terminalId(info.name), ignoreCase = true)) return true
        if (savedReaderId.startsWith(PCSC_ID_PREFIX, ignoreCase = true)) {
            val expected = savedReaderId.substring(PCSC_ID_PREFIX.length)
            return info.name.equals(expected, ignoreCase = true)
        }
        val normalizedMac = savedReaderId.uppercase(Locale.US)
        return info.name.uppercase(Locale.US).contains(normalizedMac.replace(':', '-')) ||
            info.name.uppercase(Locale.US).contains(normalizedMac)
    }

    private fun readUidFromTerminal(terminal: CardTerminal): UidReadResult {
        return try {
            if (awaitingCardRemoval) {
                if (terminal.isCardPresent) {
                    return UidReadResult.Retryable("Waiting for card removal")
                }
                awaitingCardRemoval = false
                lastDispatchedUid = null
            }

            if (!terminal.isCardPresent) {
                return UidReadResult.Retryable("No card present")
            }

            val card = terminal.connect("*")
            try {
                val response = card.basicChannel.transmit(GET_UID_APDU)
                if (response.sw != SW_SUCCESS) {
                    return UidReadResult.Retryable("No card detected (SW=${String.format("%04X", response.sw)})")
                }
                val uidBytes = response.data
                if (uidBytes.isEmpty()) return UidReadResult.Retryable("Empty UID")
                val uid = uidBytes.toHexUid()

                val now = System.currentTimeMillis()
                val normalized = uid.uppercase(Locale.US)
                if (normalized == lastDispatchedUid && now - lastDispatchAtMs < UID_REPLAY_GAP_MS) {
                    return UidReadResult.Retryable("Duplicate tap")
                }

                awaitingCardRemoval = true
                waitForCardAbsentQuietly(terminal, card)
                lastDispatchedUid = normalized
                lastDispatchAtMs = now
                UidReadResult.Success(uid)
            } finally {
                runCatching { card.disconnect(false) }
            }
        } catch (_: CardNotPresentException) {
            UidReadResult.Retryable("No card present")
        } catch (e: CardException) {
            UidReadResult.Retryable(e.message ?: "PC/SC error")
        } catch (e: Exception) {
            UidReadResult.Fatal(e.message ?: "PC/SC error")
        }
    }

    private fun waitForCardAbsentQuietly(terminal: CardTerminal, card: Card) {
        runCatching {
            terminal.waitForCardAbsent(CARD_ABSENT_WAIT_MS)
        }
        runCatching {
            if (terminal.isCardPresent) {
                card.disconnect(true)
            }
        }
    }

    private fun classifyTerminal(name: String): ReaderKind {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.contains("acr122") || lower.contains("usb") -> ReaderKind.USB
            lower.contains("acr125") || lower.contains("1255") || lower.contains("bluetooth") -> ReaderKind.BLE
            lower.contains("acs") && lower.contains("nfc") -> ReaderKind.USB
            else -> ReaderKind.OTHER
        }
    }

    private fun ByteArray.toHexUid(): String = joinToString(separator = "") { "%02X".format(it) }

    companion object {
        private const val SW_SUCCESS = 0x9000
        private const val PCSC_ID_PREFIX = "pcsc:"
        private const val UID_REPLAY_GAP_MS = 850L
        private const val CARD_ABSENT_WAIT_MS = 400L
        private val GET_UID_APDU = CommandAPDU(0xFF, 0xCA, 0x00, 0x00, 0)

        fun terminalId(terminalName: String): String = "$PCSC_ID_PREFIX$terminalName"

        fun isPcscTerminalId(id: String): Boolean =
            id.startsWith(PCSC_ID_PREFIX, ignoreCase = true)
    }
}
