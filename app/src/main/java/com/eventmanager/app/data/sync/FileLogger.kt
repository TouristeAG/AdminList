package com.eventmanager.app.data.sync

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * File-based logger for saving debug logs to device storage
 * Allows log recovery after crashes when debug mode is enabled
 * Logs are stored in: Android/data/com.eventmanager.app/cache/debug_logs
 */
class FileLogger(private val context: Context, private val settingsManager: SettingsManager) {
    companion object {
        private const val LOG_DIR = "debug_logs"
        private const val MAX_LOG_FILES = 10
        private const val LOG_FILE_SIZE_LIMIT = 5 * 1024 * 1024 // 5MB per file
        private const val TAG = "FileLogger"
    }

    private val logsDirectory: File by lazy {
        // Try external cache first (accessible via file manager), fallback to internal cache
        val externalCache = context.externalCacheDir
        val cacheDir = if (externalCache != null && externalCache.exists()) {
            externalCache
        } else {
            context.cacheDir
        }
        File(cacheDir, LOG_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val filenameDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * Get the absolute path to the logs directory for display purposes
     */
    fun getLogsDirectoryPath(): String {
        return logsDirectory.absolutePath
    }

    /**
     * Log a message to file if debug mode is enabled
     */
    fun log(tag: String, message: String, level: String = "I") {
        if (!settingsManager.getDebugMode()) return

        try {
            val timestamp = dateFormat.format(Date())
            val logMessage = "[$timestamp] [$level/$tag] $message\n"
            
            // Write to current log file
            val currentLogFile = getCurrentLogFile()
            
            // Check if file size exceeds limit
            if (currentLogFile.exists() && currentLogFile.length() > LOG_FILE_SIZE_LIMIT) {
                createNewLogFile()
            }
            
            FileWriter(currentLogFile, true).use { writer ->
                writer.append(logMessage)
                writer.flush()
            }
            
            // Also print to logcat
            Log.println(when(level) {
                "E" -> Log.ERROR
                "W" -> Log.WARN
                "D" -> Log.DEBUG
                else -> Log.INFO
            }, tag, message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    /**
     * Log with exception details
     */
    fun logException(tag: String, message: String, exception: Throwable) {
        if (!settingsManager.getDebugMode()) return

        val exceptionMessage = "$message\n${exception.stackTraceToString()}"
        log(tag, exceptionMessage, "E")
    }

    /**
     * Get current log file, creating a new one if needed
     */
    private fun getCurrentLogFile(): File {
        val logFiles = getLogFiles()
        
        return if (logFiles.isEmpty()) {
            createNewLogFile()
        } else {
            logFiles.last()
        }
    }

    /**
     * Create a new log file with timestamp
     */
    private fun createNewLogFile(): File {
        val timestamp = filenameDateFormat.format(Date())
        val filename = "app_log_$timestamp.txt"
        val newLogFile = File(logsDirectory, filename)
        
        newLogFile.createNewFile()
        
        // Clean up old log files if exceeding max count
        cleanupOldLogs()
        
        return newLogFile
    }

    /**
     * Get all log files sorted by date
     */
    private fun getLogFiles(): List<File> {
        return logsDirectory.listFiles()?.filter { it.name.startsWith("app_log_") }
            ?.sortedBy { it.lastModified() } ?: emptyList()
    }

    /**
     * Clean up old log files, keeping only the most recent ones
     */
    private fun cleanupOldLogs() {
        val logFiles = getLogFiles()
        if (logFiles.size > MAX_LOG_FILES) {
            val filesToDelete = logFiles.dropLast(MAX_LOG_FILES)
            filesToDelete.forEach { file ->
                file.delete()
                Log.d(TAG, "Deleted old log file: ${file.name}")
            }
        }
    }

    /**
     * Get the path to the most recent log file
     */
    fun getLatestLogFile(): File? {
        return getLogFiles().lastOrNull()
    }

    /**
     * Get all log files
     */
    fun getAllLogFiles(): List<File> {
        return getLogFiles()
    }

    /**
     * Clear all log files
     */
    fun clearAllLogs() {
        getLogFiles().forEach { it.delete() }
        Log.d(TAG, "Cleared all log files")
    }

    /**
     * Get total size of all log files
     */
    fun getTotalLogSize(): Long {
        return getLogFiles().sumOf { it.length() }
    }

    /**
     * Read content of latest log file
     */
    fun getLatestLogContent(): String? {
        return try {
            getLatestLogFile()?.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log file: ${e.message}")
            null
        }
    }
}
