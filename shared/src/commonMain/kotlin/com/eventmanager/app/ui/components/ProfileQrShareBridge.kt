package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.PlatformContext

internal expect object ProfileQrShareBridge {
    fun shareProfileQrCode(
        platformContext: PlatformContext,
        qrPayload: String,
        fileName: String,
        title: String,
    )
}
