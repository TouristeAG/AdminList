package com.eventmanager.app.data.sync

actual object AppLogger {
    actual fun d(tag: String, message: String) = println("D/$tag: $message")
    actual fun i(tag: String, message: String) = println("I/$tag: $message")
    actual fun w(tag: String, message: String) = println("W/$tag: $message")
    actual fun e(tag: String, message: String) = println("E/$tag: $message")
    actual fun e(tag: String, message: String, exception: Throwable) {
        println("E/$tag: $message")
        exception.printStackTrace()
    }
}
