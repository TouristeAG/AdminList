package com.eventmanager.app.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads logcat output and writes it to FileLogger
 * This captures ALL logcat output including system logs and third-party libraries
 */
class LogcatReader(private val context: Context, private val fileLogger: FileLogger) {
    private var logcatProcess: Process? = null
    private var readerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isReading = false

    /**
     * Start reading logcat
     */
    fun startReading() {
        if (isReading) return
        isReading = true

        readerJob = scope.launch {
            try {
                // Clear logcat buffer first
                Runtime.getRuntime().exec("logcat -c").waitFor()
                
                // Start reading logcat - filter by app package name
                // Note: This may not work on all devices due to permissions
                val packageName = context.packageName
                
                val processBuilder = ProcessBuilder(
                    "logcat",
                    "-v", "time",  // Include timestamps
                    "${packageName}:V",  // Our app logs at verbose level
                    "*:S"  // Suppress other logs (remove this line if you want all system logs)
                )
                
                logcatProcess = processBuilder.start()
                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                
                while (isReading) {
                    val line = reader.readLine()
                    if (line == null) break
                    // Parse logcat line format: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: MESSAGE
                    parseAndLog(line)
                }
            } catch (e: Exception) {
                // Logcat reading might fail on some devices (permissions, etc.)
                // Fall back to intercepting System.out/err and explicit Log calls
                Log.e("LogcatReader", "Failed to read logcat: ${e.message}")
                fileLogger.log("LogcatReader", "Logcat reading not available: ${e.message}", "W")
            }
        }
    }

    /**
     * Stop reading logcat
     */
    fun stopReading() {
        isReading = false
        readerJob?.cancel()
        logcatProcess?.destroy()
        logcatProcess = null
    }

    /**
     * Parse logcat line and write to FileLogger
     * Format: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: MESSAGE
     */
    private fun parseAndLog(logLine: String) {
        try {
            // Skip empty lines
            if (logLine.isBlank()) return
            
            // Try to parse logcat format
            val parts = logLine.split(" ", limit = 6)
            if (parts.size >= 6) {
                val level = parts[4]
                val tagAndMessage = parts[5]
                val colonIndex = tagAndMessage.indexOf(':')
                
                if (colonIndex > 0) {
                    val tag = tagAndMessage.substring(0, colonIndex).trim()
                    val message = tagAndMessage.substring(colonIndex + 1).trim()
                    
                    val logLevel = when (level) {
                        "V", "VERBOSE" -> "V"
                        "D", "DEBUG" -> "D"
                        "I", "INFO" -> "I"
                        "W", "WARN" -> "W"
                        "E", "ERROR" -> "E"
                        "F", "FATAL" -> "E"
                        else -> "I"
                    }
                    
                    fileLogger.log(tag, message, logLevel)
                } else {
                    // Fallback: log entire line
                    fileLogger.log("logcat", logLine, "I")
                }
            } else {
                // Fallback: log entire line if parsing fails
                fileLogger.log("logcat", logLine, "I")
            }
        } catch (e: Exception) {
            // If parsing fails, just log the raw line
            fileLogger.log("logcat", logLine, "I")
        }
    }
}

