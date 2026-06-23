package com.eventmanager.app.data.sync

actual object AppLogger {
    actual fun d(tag: String, message: String) = FileAppLogger.d(tag, message)
    actual fun i(tag: String, message: String) = FileAppLogger.i(tag, message)
    actual fun w(tag: String, message: String) = FileAppLogger.w(tag, message)
    actual fun e(tag: String, message: String) = FileAppLogger.e(tag, message)
    actual fun e(tag: String, message: String, exception: Throwable) = FileAppLogger.e(tag, message, exception)
}
