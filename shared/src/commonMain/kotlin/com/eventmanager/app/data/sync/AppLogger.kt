package com.eventmanager.app.data.sync

expect object AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, exception: Throwable)
}
