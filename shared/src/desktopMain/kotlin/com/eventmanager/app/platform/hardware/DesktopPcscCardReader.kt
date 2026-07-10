package com.eventmanager.app.platform.hardware

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.UidReadResult
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.desktop_pcsc_error
import com.eventmanager.app.resources.desktop_pcsc_library_unavailable
import com.eventmanager.app.resources.desktop_pcsc_no_ble_reader
import com.eventmanager.app.resources.desktop_pcsc_no_reader
import com.eventmanager.app.resources.desktop_pcsc_no_readers_found
import com.eventmanager.app.resources.desktop_pcsc_no_readers_hint
import com.eventmanager.app.resources.desktop_pcsc_provider_failed
import com.eventmanager.app.resources.desktop_pcsc_reader_disconnected
import com.eventmanager.app.resources.desktop_pcsc_reader_error
import com.eventmanager.app.resources.desktop_pcsc_reader_ok_place_card
import com.eventmanager.app.resources.desktop_pcsc_readers_header
import com.eventmanager.app.resources.desktop_pcsc_unable_select_reader
import com.eventmanager.app.resources.desktop_pcsc_unavailable
import com.eventmanager.app.resources.desktop_pcsc_using_reader
import com.eventmanager.app.resources.desktop_pcsc_waiting_card
import jnasmartcardio.Smartcardio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.security.Security
import java.util.Locale
import javax.smartcardio.Card
import javax.smartcardio.CardException
import javax.smartcardio.CardNotPresentException
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.ResponseAPDU
import javax.smartcardio.TerminalFactory

/**
 * PC/SC NFC readers on desktop: USB (ACR122U, ACR1255U-J1 USB) and Bluetooth (ACR1255U-J1
 * after OS pairing on Windows).
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

    private fun tr(block: suspend () -> String): String = runBlocking { block() }

    fun isReaderAvailable(): Boolean = pcscAvailable && listTerminals().isNotEmpty()

    fun hasUsbReader(): Boolean = listTerminalInfos().any { it.kind == ReaderKind.USB }

    fun firstUsbTerminalName(): String? =
        listTerminalInfos().firstOrNull { it.kind == ReaderKind.USB }?.name

    fun readerName(): String = selectTerminal(null)?.name ?: tr {
        if (pcscAvailable) getString(Res.string.desktop_pcsc_no_reader)
        else getString(Res.string.desktop_pcsc_unavailable)
    }

    fun listReaderNames(): List<String> = listTerminals().map { it.name }

    fun listTerminalInfos(): List<TerminalInfo> = listTerminals().map { terminal ->
        val name = terminal.name.orEmpty()
        TerminalInfo(terminal = terminal, name = name, kind = classifyTerminal(name))
    }

    fun formatTerminalListing(): String = tr {
        val terminals = listTerminalInfos()
        if (terminals.isEmpty()) return@tr getString(Res.string.desktop_pcsc_no_readers_found)
        buildString {
            appendLine(getString(Res.string.desktop_pcsc_readers_header, terminals.size))
            terminals.forEach { info ->
                val tag = when (info.kind) {
                    ReaderKind.USB -> "USB"
                    ReaderKind.BLE -> "BLE"
                    ReaderKind.OTHER -> "PC/SC"
                }
                appendLine(" • [$tag] ${info.name}")
            }
        }.trimEnd()
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
            if (!pcscAvailable) {
                return@withContext UidReadResult.Fatal(
                    getString(Res.string.desktop_pcsc_library_unavailable)
                )
            }
            val terminal = selectTerminal(settings) ?: return@withContext UidReadResult.NoReader
            readUidFromTerminal(terminal, cardWaitMs = CARD_WAIT_MS)
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
                    details = getString(Res.string.desktop_pcsc_provider_failed)
                )
            }
            val terminals = listTerminalInfos()
            if (terminals.isEmpty()) {
                return@withContext DiagnosticResult(
                    success = false,
                    details = if (bleOnly) {
                        getString(Res.string.desktop_pcsc_no_ble_reader)
                    } else {
                        getString(Res.string.desktop_pcsc_no_readers_hint)
                    }
                )
            }
            details.appendLine(getString(Res.string.desktop_pcsc_readers_header, terminals.size))
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
                details = details.appendLine(getString(Res.string.desktop_pcsc_unable_select_reader))
                    .toString().trimEnd()
            )
            details.appendLine(getString(Res.string.desktop_pcsc_using_reader, selected.name))
            details.appendLine(
                getString(Res.string.desktop_pcsc_waiting_card, DIAGNOSTIC_CARD_WAIT_MS / 1000)
            )

            val deadline = System.currentTimeMillis() + DIAGNOSTIC_CARD_WAIT_MS
            var result: UidReadResult = UidReadResult.Retryable(ERR_NO_CARD_PRESENT)
            while (System.currentTimeMillis() < deadline) {
                result = readUidFromTerminal(selected, cardWaitMs = DIAGNOSTIC_POLL_MS)
                when (result) {
                    is UidReadResult.Success -> break
                    is UidReadResult.Fatal -> break
                    is UidReadResult.Retryable -> {
                        val retryable = result.error == ERR_NO_CARD_PRESENT ||
                            result.error == ERR_NO_CARD_ON_READER ||
                            result.error == ERR_WAITING_REMOVAL ||
                            result.error?.startsWith("No card detected (SW=") == true
                        if (!retryable) break
                        delay(250)
                    }
                    UidReadResult.NoReader -> break
                }
            }

            when (result) {
                is UidReadResult.Success -> {
                    details.appendLine("UID: ${result.uid}")
                    DiagnosticResult(success = true, details = details.toString().trimEnd())
                }
                is UidReadResult.Retryable -> {
                    val noCard = result.error == ERR_NO_CARD_PRESENT ||
                        result.error == ERR_NO_CARD_ON_READER
                    if (noCard) {
                        details.appendLine(getString(Res.string.desktop_pcsc_reader_ok_place_card))
                        DiagnosticResult(success = true, details = details.toString().trimEnd())
                    } else {
                        DiagnosticResult(
                            success = false,
                            details = details.appendLine(
                                result.error ?: getString(Res.string.desktop_pcsc_reader_error)
                            ).toString().trimEnd()
                        )
                    }
                }
                is UidReadResult.Fatal -> DiagnosticResult(
                    success = false,
                    details = details.appendLine(
                        result.error ?: getString(Res.string.desktop_pcsc_reader_error)
                    ).toString().trimEnd()
                )
                UidReadResult.NoReader -> DiagnosticResult(
                    success = false,
                    details = details.appendLine(getString(Res.string.desktop_pcsc_reader_disconnected))
                        .toString().trimEnd()
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
        val infos = listTerminalInfos()
        infos.firstOrNull { it.kind == ReaderKind.USB }?.terminal?.let { return it }
        infos.firstOrNull { it.kind == ReaderKind.OTHER }?.terminal?.let { return it }

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

    private fun readUidFromTerminal(
        terminal: CardTerminal,
        cardWaitMs: Long = CARD_WAIT_MS
    ): UidReadResult {
        return try {
            if (awaitingCardRemoval) {
                val removed = runCatching { terminal.waitForCardAbsent(CARD_ABSENT_WAIT_MS) }
                    .getOrDefault(false)
                if (!removed && terminal.isCardPresent) {
                    return UidReadResult.Retryable(ERR_WAITING_REMOVAL)
                }
                awaitingCardRemoval = false
                lastDispatchedUid = null
            }

            val cardPresent = runCatching { terminal.waitForCardPresent(cardWaitMs) }
                .getOrElse { e ->
                    if (e is CardNotPresentException) return UidReadResult.Retryable(ERR_NO_CARD_PRESENT)
                    return UidReadResult.Retryable(e.message ?: tr { getString(Res.string.desktop_pcsc_error) })
                }
            if (!cardPresent) {
                return UidReadResult.Retryable(ERR_NO_CARD_PRESENT)
            }

            var lastSw: Int? = null
            for (protocol in CONNECT_PROTOCOLS) {
                val card = runCatching { terminal.connect(protocol) }.getOrNull() ?: continue
                try {
                    Thread.sleep(POST_CONNECT_DELAY_MS)
                    val uidResult = transmitUidApduVariants(card)
                    if (uidResult == null) continue
                    lastSw = uidResult.sw
                    if (uidResult.sw != SW_SUCCESS || uidResult.data.isEmpty()) continue

                    val uid = uidResult.data.toHexUid()
                    val now = System.currentTimeMillis()
                    val normalized = uid.uppercase(Locale.US)
                    if (normalized == lastDispatchedUid && now - lastDispatchAtMs < UID_REPLAY_GAP_MS) {
                        return UidReadResult.Retryable("Duplicate tap")
                    }

                    awaitingCardRemoval = true
                    waitForCardAbsentQuietly(terminal, card)
                    lastDispatchedUid = normalized
                    lastDispatchAtMs = now
                    return UidReadResult.Success(uid)
                } finally {
                    runCatching { card.disconnect(true) }
                }
            }

            return if (lastSw != null) {
                UidReadResult.Retryable("No card detected (SW=${String.format("%04X", lastSw)})")
            } else {
                UidReadResult.Retryable(ERR_NO_CARD_ON_READER)
            }
        } catch (_: CardNotPresentException) {
            UidReadResult.Retryable(ERR_NO_CARD_PRESENT)
        } catch (e: CardException) {
            UidReadResult.Retryable(e.message ?: tr { getString(Res.string.desktop_pcsc_error) })
        } catch (e: Exception) {
            UidReadResult.Fatal(e.message ?: tr { getString(Res.string.desktop_pcsc_error) })
        }
    }

    /**
     * ACS pseudo-APDU Get UID. Raw 5-byte frames match Android/CCID; Le variants follow the
     * ACR122U API (00 = max, 04 = typical MIFARE UID length). PC/SC stacks on macOS often need
     * T=CL + Le=04 where Le=00 returns SW=6300 even with a card on the antenna.
     */
    private fun transmitUidApduVariants(card: Card): ResponseAPDU? {
        val channel = card.basicChannel ?: return null
        var lastResponse: ResponseAPDU? = null
        for (apduBytes in GET_UID_APDU_VARIANTS) {
            val response = runCatching { channel.transmit(CommandAPDU(apduBytes)) }.getOrNull()
                ?: continue
            lastResponse = response
            if (response.sw == SW_SUCCESS && response.data.isNotEmpty()) return response
        }
        return lastResponse
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
            lower.contains("bluetooth") || lower.contains(" ble") -> ReaderKind.BLE
            lower.contains("acr122") || lower.contains("usb") -> ReaderKind.USB
            lower.contains("acr125") || lower.contains("1255") -> ReaderKind.USB
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
        private const val CARD_WAIT_MS = 3_000L
        private const val DIAGNOSTIC_CARD_WAIT_MS = 8_000L
        private const val DIAGNOSTIC_POLL_MS = 1_500L
        private const val POST_CONNECT_DELAY_MS = 120L

        /** Internal machine-readable status codes (not shown as final UX copy). */
        private const val ERR_NO_CARD_PRESENT = "No card present"
        private const val ERR_NO_CARD_ON_READER = "No card detected on reader"
        private const val ERR_WAITING_REMOVAL = "Waiting for card removal"

        /** Contactless first — required by many ACS drivers for PICC pseudo-APDUs. */
        private val CONNECT_PROTOCOLS = arrayOf("T=CL", "*", "T=1", "T=0")

        /** ACS Get UID pseudo-APDUs (see ACR122U / ACR1255 API §Get Data). */
        private val GET_UID_APDU_VARIANTS = arrayOf(
            byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x04),
            byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00),
            byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x08),
            byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x10),
        )

        fun terminalId(terminalName: String): String = "$PCSC_ID_PREFIX$terminalName"

        fun isPcscTerminalId(id: String): Boolean =
            id.startsWith(PCSC_ID_PREFIX, ignoreCase = true)
    }
}
