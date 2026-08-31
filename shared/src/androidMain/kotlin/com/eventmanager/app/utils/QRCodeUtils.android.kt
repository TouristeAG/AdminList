package com.eventmanager.app.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

actual object QRCodeUtils {
    actual fun generateQrImageBitmap(content: String, sizePx: Int): ImageBitmap? {
        return generateQrBitmap(content, sizePx)?.asImageBitmap()
    }

    actual fun generateStaffObfuscatedQrImageBitmap(content: String, sizePx: Int): ImageBitmap? {
        val source = generateQrBitmap(content, sizePx) ?: return null
        val tinySize = (sizePx / 32).coerceIn(8, 16)
        val tiny = Bitmap.createScaledBitmap(source, tinySize, tinySize, true)
        val obfuscated = Bitmap.createScaledBitmap(tiny, sizePx, sizePx, true)
        if (tiny !== source) tiny.recycle()
        return obfuscated.asImageBitmap()
    }

    private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
        if (content.isEmpty()) return null
        return try {
            val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
