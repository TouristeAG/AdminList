package com.eventmanager.app.platform.hardware

import com.eventmanager.app.platform.UidReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PC/SC NFC reader via javax.smartcardio (USB ACS ACR122U and compatible readers).
 * Uses reflection so desktop builds work when java.smartcardio is available at runtime only.
 */
class DesktopPcscCardReader {

    fun isReaderAvailable(): Boolean = listTerminals().isNotEmpty()

    fun readerName(): String = listTerminals().firstOrNull()?.let { terminalName(it) } ?: "No PC/SC reader"

    suspend fun readUid(): UidReadResult = withContext(Dispatchers.IO) {
        val terminals = listTerminals()
        if (terminals.isEmpty()) return@withContext UidReadResult.NoReader
        readUidFromTerminal(terminals.first())
    }

    private fun listTerminals(): List<Any> = runCatching {
        val factoryClass = Class.forName("javax.smartcardio.TerminalFactory")
        val factory = factoryClass.getMethod("getDefault").invoke(null) ?: return emptyList()
        val terminals = factoryClass.getMethod("terminals").invoke(factory) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        (terminals.javaClass.getMethod("list").invoke(terminals) as? List<*>)?.filterNotNull().orEmpty()
    }.getOrDefault(emptyList())

    private fun terminalName(terminal: Any): String = runCatching {
        terminal.javaClass.getMethod("getName").invoke(terminal) as? String
    }.getOrNull() ?: "PC/SC reader"

    private fun readUidFromTerminal(terminal: Any): UidReadResult {
        return try {
            val isPresent = terminal.javaClass.getMethod("isCardPresent").invoke(terminal) as? Boolean ?: false
            if (!isPresent) return UidReadResult.Retryable("No card present")
            val card = terminal.javaClass.getMethod("connect", String::class.java).invoke(terminal, "*") ?: return UidReadResult.Fatal("No card")
            val channel = card.javaClass.getMethod("getBasicChannel").invoke(card) ?: return UidReadResult.Fatal("No channel")
            val apduClass = Class.forName("javax.smartcardio.CommandAPDU")
            val apdu = apduClass.getConstructor(
                Byte::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType
            ).newInstance(0xFF.toByte(), 0xCA.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
            val response = channel.javaClass.getMethod("transmit", apduClass).invoke(channel, apdu) ?: return UidReadResult.Fatal("No response")
            val bytes = response.javaClass.getMethod("getBytes").invoke(response) as? ByteArray
            card.javaClass.getMethod("disconnect", Boolean::class.javaPrimitiveType).invoke(card, false)
            val uid = bytes?.takeIf { it.size >= 4 }?.joinToString("") { "%02X".format(it) }
            if (uid.isNullOrBlank()) UidReadResult.Fatal("Empty UID") else UidReadResult.Success(uid)
        } catch (e: Exception) {
            UidReadResult.Fatal(e.message ?: "PC/SC error")
        }
    }
}
