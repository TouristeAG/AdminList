package com.eventmanager.app.ui

import com.eventmanager.app.platform.elapsedRealtimeMs
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal const val ADMIN_SESSION_IDLE_TIMEOUT_MS = 10 * 60 * 1000L

interface AdminSessionHost {
    val adminSessionWatchdog: AdminSessionWatchdog
    var adminSessionAutoLogout: (() -> Unit)?
}

class AdminSessionWatchdog {
    @Volatile var monitoring: Boolean = false
    val lastInteractionElapsedMs = AtomicLong(elapsedRealtimeMs())
    private val logoutAfterSleepPending = AtomicBoolean(false)

    fun onUserInput() {
        if (monitoring) lastInteractionElapsedMs.set(elapsedRealtimeMs())
    }

    fun onDisplayTurnedOff() {
        if (monitoring) logoutAfterSleepPending.set(true)
    }

    fun consumeLogoutAfterSleepIfPending(): Boolean {
        if (!monitoring) {
            logoutAfterSleepPending.set(false)
            return false
        }
        return logoutAfterSleepPending.compareAndSet(true, false)
    }

    fun stopMonitoring() {
        monitoring = false
        logoutAfterSleepPending.set(false)
    }
}
