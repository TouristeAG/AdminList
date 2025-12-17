package com.eventmanager.app.data.sync

import android.content.Context

/**
 * Global logger instance and extension functions for easy logging
 */
object AppLogger {
    private var fileLogger: FileLogger? = null
    private var logcatReader: LogcatReader? = null

    fun init(context: Context, settingsManager: SettingsManager) {
        fileLogger = FileLogger(context, settingsManager)
        val logger = fileLogger ?: return
        
        // Initialize log interceptor to capture System.out/err and explicit Log calls
        LogInterceptor.init(logger)
        
        // Initialize logcat reader to capture ALL logcat output
        logcatReader = LogcatReader(context, logger)
        
        // Start intercepting if debug mode is enabled
        if (settingsManager.getDebugMode()) {
            LogInterceptor.startIntercepting()
            logcatReader?.startReading()
        }
    }
    
    /**
     * Enable or disable log interception
     */
    fun setIntercepting(enabled: Boolean) {
        if (enabled) {
            LogInterceptor.startIntercepting()
            logcatReader?.startReading()
        } else {
            LogInterceptor.stopIntercepting()
            logcatReader?.stopReading()
        }
    }

    fun d(tag: String, message: String) {
        fileLogger?.log(tag, message, "D")
    }

    fun i(tag: String, message: String) {
        fileLogger?.log(tag, message, "I")
    }

    fun w(tag: String, message: String) {
        fileLogger?.log(tag, message, "W")
    }

    fun e(tag: String, message: String) {
        fileLogger?.log(tag, message, "E")
    }

    fun e(tag: String, message: String, exception: Throwable) {
        fileLogger?.logException(tag, message, exception)
    }

    fun getLatestLogFile() = fileLogger?.getLatestLogFile()
    fun getAllLogFiles() = fileLogger?.getAllLogFiles() ?: emptyList()
    fun clearAllLogs() = fileLogger?.clearAllLogs()
    fun getTotalLogSize() = fileLogger?.getTotalLogSize() ?: 0L
    fun getLatestLogContent() = fileLogger?.getLatestLogContent()
    fun getLogsDirectoryPath() = fileLogger?.getLogsDirectoryPath() ?: ""
}
