package com.eventmanager.app.data.sync

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Intercepts all Android Log calls and redirects them to FileLogger
 * This ensures all logcat output is also written to debug log files
 */
object LogInterceptor {
    private var fileLogger: FileLogger? = null
    private var originalOut: PrintStream? = null
    private var originalErr: PrintStream? = null
    private var interceptedOut: PrintStream? = null
    private var interceptedErr: PrintStream? = null
    private var isIntercepting = false

    fun init(fileLogger: FileLogger) {
        this.fileLogger = fileLogger
    }

    /**
     * Start intercepting all logs
     */
    fun startIntercepting() {
        if (isIntercepting) return
        isIntercepting = true

        // Intercept System.out (println statements)
        originalOut = System.out
        val outBuffer = ByteArrayOutputStream()
        interceptedOut = object : PrintStream(outBuffer) {
            override fun println(x: String?) {
                val message = x ?: "null"
                fileLogger?.log("System.out", message, "I")
                originalOut?.println(message)
            }
            
            override fun println(x: Any?) {
                val message = x?.toString() ?: "null"
                fileLogger?.log("System.out", message, "I")
                originalOut?.println(message)
            }
            
            override fun print(s: String?) {
                val message = s ?: "null"
                fileLogger?.log("System.out", message, "I")
                originalOut?.print(message)
            }
            
            override fun print(obj: Any?) {
                val message = obj?.toString() ?: "null"
                fileLogger?.log("System.out", message, "I")
                originalOut?.print(message)
            }
        }
        System.setOut(interceptedOut)

        // Intercept System.err (error prints)
        originalErr = System.err
        val errBuffer = ByteArrayOutputStream()
        interceptedErr = object : PrintStream(errBuffer) {
            override fun println(x: String?) {
                val message = x ?: "null"
                fileLogger?.log("System.err", message, "E")
                originalErr?.println(message)
            }
            
            override fun println(x: Any?) {
                val message = x?.toString() ?: "null"
                fileLogger?.log("System.err", message, "E")
                originalErr?.println(message)
            }
            
            override fun print(s: String?) {
                val message = s ?: "null"
                fileLogger?.log("System.err", message, "E")
                originalErr?.print(message)
            }
            
            override fun print(obj: Any?) {
                val message = obj?.toString() ?: "null"
                fileLogger?.log("System.err", message, "E")
                originalErr?.print(message)
            }
        }
        System.setErr(interceptedErr)
    }

    /**
     * Stop intercepting logs
     */
    fun stopIntercepting() {
        if (!isIntercepting) return
        isIntercepting = false

        originalOut?.let { System.setOut(it) }
        originalErr?.let { System.setErr(it) }
        
        originalOut = null
        originalErr = null
        interceptedOut = null
        interceptedErr = null
    }

    /**
     * Intercept Log.v calls
     */
    fun v(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "V")
        return Log.v(tag, msg)
    }

    /**
     * Intercept Log.v calls with throwable
     */
    fun v(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.v(tag, msg, tr)
    }

    /**
     * Intercept Log.d calls
     */
    fun d(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "D")
        return Log.d(tag, msg)
    }

    /**
     * Intercept Log.d calls with throwable
     */
    fun d(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.d(tag, msg, tr)
    }

    /**
     * Intercept Log.i calls
     */
    fun i(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "I")
        return Log.i(tag, msg)
    }

    /**
     * Intercept Log.i calls with throwable
     */
    fun i(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.i(tag, msg, tr)
    }

    /**
     * Intercept Log.w calls
     */
    fun w(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "W")
        return Log.w(tag, msg)
    }

    /**
     * Intercept Log.w calls with throwable
     */
    fun w(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.w(tag, msg, tr)
    }

    /**
     * Intercept Log.w calls (warning without message)
     */
    fun w(tag: String, tr: Throwable?): Int {
        tr?.let { fileLogger?.logException(tag, "Warning", it) }
        return Log.w(tag, tr)
    }

    /**
     * Intercept Log.e calls
     */
    fun e(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "E")
        return Log.e(tag, msg)
    }

    /**
     * Intercept Log.e calls with throwable
     */
    fun e(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.e(tag, msg, tr)
    }

    /**
     * Intercept Log.println calls
     */
    fun println(priority: Int, tag: String, msg: String): Int {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "I"
        }
        fileLogger?.log(tag, msg, level)
        return Log.println(priority, tag, msg)
    }

    /**
     * Intercept Log.wtf calls (What a Terrible Failure)
     */
    fun wtf(tag: String, msg: String): Int {
        fileLogger?.log(tag, msg, "E")
        return Log.wtf(tag, msg)
    }

    /**
     * Intercept Log.wtf calls with throwable
     */
    fun wtf(tag: String, msg: String, tr: Throwable): Int {
        fileLogger?.logException(tag, msg, tr)
        return Log.wtf(tag, msg, tr)
    }
}

