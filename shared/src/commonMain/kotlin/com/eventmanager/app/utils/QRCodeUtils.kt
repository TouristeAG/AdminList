package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap

expect object QRCodeUtils {
    fun generateQrImageBitmap(content: String, sizePx: Int = 512): ImageBitmap?
}
