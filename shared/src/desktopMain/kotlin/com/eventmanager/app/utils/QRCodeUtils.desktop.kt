package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel

actual object QRCodeUtils {
    actual fun generateQrImageBitmap(content: String, sizePx: Int): ImageBitmap? {
        return generateQrBufferedImage(content, sizePx)?.toComposeImageBitmap()
    }

    actual fun generateStaffObfuscatedQrImageBitmap(content: String, sizePx: Int): ImageBitmap? {
        val source = generateQrBufferedImage(content, sizePx) ?: return null
        val tinySize = (sizePx / 32).coerceIn(8, 16)
        val tiny = scaleImage(source, tinySize, tinySize)
        val upscaled = scaleImage(tiny, sizePx, sizePx)
        return boxBlur(upscaled, radius = 8).toComposeImageBitmap()
    }

    private fun generateQrBufferedImage(content: String, sizePx: Int): BufferedImage? {
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
            image
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleImage(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(source, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    /** Compose Desktop `Modifier.blur` is not always applied; bake a blur into the bitmap. */
    private fun boxBlur(source: BufferedImage, radius: Int): BufferedImage {
        val size = (radius * 2 + 1).coerceAtLeast(3)
        val weight = 1f / size
        val kernel1d = FloatArray(size) { weight }
        val horizontal = ConvolveOp(Kernel(size, 1, kernel1d), ConvolveOp.EDGE_NO_OP, null)
        val vertical = ConvolveOp(Kernel(1, size, kernel1d), ConvolveOp.EDGE_NO_OP, null)
        val tmp = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val dest = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        horizontal.filter(source, tmp)
        vertical.filter(tmp, dest)
        return dest
    }
}
