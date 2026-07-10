package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

actual object QRCodeUtils {
    actual fun generateQrImageBitmap(content: String, sizePx: Int): ImageBitmap? {
        if (content.isEmpty()) return null
        return try {
            val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
                }
            }
            image.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
