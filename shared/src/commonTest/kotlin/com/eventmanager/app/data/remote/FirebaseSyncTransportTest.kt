package com.eventmanager.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class FirebaseSyncTransportTest {

    @Test
    fun liveRequiresServerConfirmedSnapshot() {
        assertEquals(
            FirebaseSyncTransport.LIVE,
            firebaseSyncTransport(
                orgConfigured = true,
                sdkAvailable = true,
                listenersActive = true,
                serverReachable = true,
                pullJobActive = true,
            ),
        )
    }

    @Test
    fun listenersWithoutServerAreOffline() {
        assertEquals(
            FirebaseSyncTransport.OFFLINE,
            firebaseSyncTransport(
                orgConfigured = true,
                sdkAvailable = true,
                listenersActive = true,
                serverReachable = false,
                pullJobActive = true,
            ),
        )
    }

    @Test
    fun pullFallbackWhenListenersAreOff() {
        assertEquals(
            FirebaseSyncTransport.PULL,
            firebaseSyncTransport(
                orgConfigured = true,
                sdkAvailable = true,
                listenersActive = false,
                serverReachable = false,
                pullJobActive = true,
            ),
        )
    }

    @Test
    fun missingSdkIsOffline() {
        assertEquals(
            FirebaseSyncTransport.OFFLINE,
            firebaseSyncTransport(
                orgConfigured = true,
                sdkAvailable = false,
                listenersActive = true,
                serverReachable = true,
                pullJobActive = true,
            ),
        )
    }
}
