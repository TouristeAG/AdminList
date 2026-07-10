package com.eventmanager.app.platform.hardware

import com.github.eduramiba.webcamcapture.drivers.NativeDriver
import com.github.sarxos.webcam.Webcam

/**
 * Sarxos 0.3.12 defaults to OpenIMAJ, which fails on modern macOS (UnsatisfiedLinkError).
 * The native AVFoundation driver works on current Mac/Windows/Linux releases.
 */
object DesktopWebcamSupport {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            Webcam.setDriver(NativeDriver())
            initialized = true
        }
    }
}
