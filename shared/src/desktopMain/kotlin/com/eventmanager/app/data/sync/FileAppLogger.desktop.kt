package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileAppLogger {
    private var logsDirectory: File? = null
    private var settingsManager: SettingsManager? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val filenameDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    private const val LOG_DIR = "debug_logs"
    private const val MAX_LOG_FILES = 10
    private const val LOG_FILE_SIZE_LIMIT = 5 * 1024 * 1024L

    fun init(platformContext: PlatformContext, settings: SettingsManager) {
        settingsManager = settings
        logsDirectory = File(platformContext.appDataDir, LOG_DIR).apply { mkdirs() }
    }

    fun d(tag: String, message: String) = log(tag, message, "D")
    fun i(tag: String, message: String) = log(tag, message, "I")
    fun w(tag: String, message: String) = log(tag, message, "W")
    fun e(tag: String, message: String) = log(tag, message, "E")
    fun e(tag: String, message: String, exception: Throwable) {
        log(tag, "$message — ${exception.message}", "E")
        exception.printStackTrace()
    }

    fun getLogsDirectoryPath(): String = logsDirectory?.absolutePath ?: ""

    fun getAllLogFiles(): List<File> = getLogFiles()

    fun getLatestLogFile(): File? = getLogFiles().lastOrNull()

    fun getLatestLogContent(): String? = try {
        getLatestLogFile()?.readText()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    fun clearAllLogs() {
        getLogFiles().forEach { it.delete() }
    }

    fun getTotalLogSize(): Long = getLogFiles().sumOf { it.length() }

    private fun getLogFiles(): List<File> {
        val dir = logsDirectory ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".log") || it.name.startsWith("app_log_")) }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }

    private fun log(tag: String, message: String, level: String) {
        if (settingsManager?.getDebugMode() != true) return
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
        }.onFailure { it.printStackTrace() }
        println("$level/$tag: $message")
    }

    private fun currentLogFile(dir: File): File {
        val existing = getLogFiles().lastOrNull()
        return existing ?: File(dir, "noctulist_${filenameDateFormat.format(Date())}.log")
    }

    private fun rotateLogs(dir: File) {
        val files = getLogFiles().sortedByDescending { it.lastModified() }
        files.drop(MAX_LOG_FILES - 1).forEach { it.delete() }
    }
}
