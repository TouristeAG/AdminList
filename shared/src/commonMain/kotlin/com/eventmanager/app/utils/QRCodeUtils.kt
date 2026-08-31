package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap

expect object QRCodeUtils {
    fun generateQrImageBitmap(content: String, sizePx: Int = 512): ImageBitmap?

    /**
     * QR preview for door staff: visibly a code, but too blurred to scan or copy.
     */
    fun generateStaffObfuscatedQrImageBitmap(content: String, sizePx: Int = 512): ImageBitmap?
}
