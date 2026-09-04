package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileAppLogger {
    private var logsDirectory: File? = null
    private var settingsManager: SettingsManager? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val filenameDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    private var originalOut: PrintStream? = null
    private var originalErr: PrintStream? = null
    private var isIntercepting = false
    private val inCapture = ThreadLocal.withInitial { false }

    private const val LOG_DIR = "debug_logs"
    private const val MAX_LOG_FILES = 10
    private const val LOG_FILE_SIZE_LIMIT = 5 * 1024 * 1024L

    fun init(platformContext: PlatformContext, settings: SettingsManager) {
        settingsManager = settings
        logsDirectory = File(platformContext.appDataDir, LOG_DIR).apply { mkdirs() }
        if (settings.getDebugMode()) {
            setIntercepting(true)
        }
    }

    /**
     * Mirror Android: capture System.out / System.err (app [println] traffic) into the debug log file.
     */
    fun setIntercepting(enabled: Boolean) {
        if (enabled) startIntercepting() else stopIntercepting()
    }

    fun d(tag: String, message: String) = log(tag, message, "D")
    fun i(tag: String, message: String) = log(tag, message, "I")
    fun w(tag: String, message: String) = log(tag, message, "W")
    fun e(tag: String, message: String) = log(tag, message, "E")
    fun e(tag: String, message: String, exception: Throwable) {
        log(tag, "$message — ${exception.message}", "E")
        exception.printStackTrace(originalErr ?: System.err)
    }

    fun getLogsDirectoryPath(): String = logsDirectory?.absolutePath ?: ""

    fun getAllLogFiles(): List<File> = getLogFiles()

    fun getLatestLogFile(): File? = getLogFiles().lastOrNull()

    fun getLatestLogContent(): String? = try {
        getLatestLogFile()?.readText()
    } catch (e: Exception) {
        e.printStackTrace(originalErr ?: System.err)
        null
    }

    fun clearAllLogs() {
        getLogFiles().forEach { it.delete() }
    }

    fun getTotalLogSize(): Long = getLogFiles().sumOf { it.length() }

    private fun startIntercepting() {
        if (isIntercepting) return
        isIntercepting = true
        originalOut = System.out
        originalErr = System.err
        System.setOut(
            PrintStream(
                CapturingOutputStream(originalOut!!) { line ->
                    appendCaptured("System.out", line, "I")
                },
                true,
                Charsets.UTF_8,
            )
        )
        System.setErr(
            PrintStream(
                CapturingOutputStream(originalErr!!) { line ->
                    appendCaptured("System.err", line, "E")
                },
                true,
                Charsets.UTF_8,
            )
        )
    }

    private fun stopIntercepting() {
        if (!isIntercepting) return
        isIntercepting = false
        originalOut?.let { System.setOut(it) }
        originalErr?.let { System.setErr(it) }
        originalOut = null
        originalErr = null
    }

    private fun getLogFiles(): List<File> {
        val dir = logsDirectory ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".log") || it.name.startsWith("app_log_")) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }

    private fun log(tag: String, message: String, level: String) {
        if (settingsManager?.getDebugMode() != true) return
        writeToFile(tag, message, level)
        // Always echo via the real console stream to avoid re-capturing our own lines.
        (originalOut ?: System.out).println("$level/$tag: $message")
    }

    private fun appendCaptured(tag: String, message: String, level: String) {
        if (settingsManager?.getDebugMode() != true) return
        if (message.isBlank()) return
        if (inCapture.get() == true) return
        inCapture.set(true)
        try {
            writeToFile(tag, message, level)
        } finally {
            inCapture.set(false)
        }
    }

    private fun writeToFile(tag: String, message: String, level: String) {
        val dir = logsDirectory ?: return
        runCatching {
            val current = currentLogFile(dir)
            if (current.exists() && current.length() > LOG_FILE_SIZE_LIMIT) {
                rotateLogs(dir)
            }
            val timestamp = dateFormat.format(Date())
            FileWriter(current, true).use { writer ->
                writer.append("[$timestamp] [$level/$tag] $message\n")
            }
        }.onFailure {
            it.printStackTrace(originalErr ?: System.err)
        }
    }

    private fun currentLogFile(dir: File): File {
        val existing = getLogFiles().lastOrNull()
        return existing ?: File(dir, "noctulist_${filenameDateFormat.format(Date())}.log")
    }

    private fun rotateLogs(dir: File) {
        val files = getLogFiles().sortedByDescending { it.lastModified() }
        files.drop(MAX_LOG_FILES - 1).forEach { it.delete() }
    }

    /**
     * Forwards every byte to [delegate] and emits complete lines to [onLine].
     */
    private class CapturingOutputStream(
        private val delegate: OutputStream,
        private val onLine: (String) -> Unit,
    ) : OutputStream() {
        private val lineBuffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            delegate.write(b)
            when (b) {
                '\n'.code -> {
                    onLine(lineBuffer.toString(Charsets.UTF_8))
                    lineBuffer.reset()
                }
                '\r'.code -> Unit
                else -> lineBuffer.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) {
                write(b[i].toInt() and 0xFF)
            }
        }

        override fun flush() = delegate.flush()

        override fun close() {
            if (lineBuffer.size() > 0) {
                onLine(lineBuffer.toString(Charsets.UTF_8))
                lineBuffer.reset()
            }
            delegate.close()
        }
    }
}
