package com.eventmanager.app.wallet

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual object WalletPassImageRenderer {
    actual fun render(request: WalletPassRequest, logoBytes: ByteArray?): WalletPassImages {
        val configuredLogo = logoBytes?.let { bytes ->
            runCatching { ImageIO.read(bytes.inputStream()) }.getOrNull()
        }
        val icon = if (configuredLogo != null) {
            fitCenterImage(configuredLogo, 120, 120, Color(0x11, 0x18, 0x27))
        } else {
            createGradientImage(120, 120, Color(0x4F, 0x46, 0xE5), Color(0x7C, 0x3A, 0xED), request.holderName)
        }
        val logo = if (configuredLogo != null) {
            fitCenterImage(configuredLogo, 320, 100, Color(0x0F, 0x17, 0x2A))
        } else {
            createGradientImage(320, 100, Color(0x0F, 0x17, 0x2A), Color(0x1E, 0x29, 0x3B), request.associationName)
        }
        val strip = createGradientImage(624, 246, Color(0x31, 0x2E, 0x81), Color(0x6D, 0x28, 0xD9), "Digital Wallet Pass")

        val files = linkedMapOf(
            "icon.png" to icon.toPngBytes(),
            "icon@2x.png" to scaleImage(icon, 240, 240).toPngBytes(),
            "logo.png" to logo.toPngBytes(),
            "logo@2x.png" to scaleImage(logo, 640, 200).toPngBytes(),
            "strip.png" to strip.toPngBytes(),
            "strip@2x.png" to scaleImage(strip, 1248, 492).toPngBytes(),
        )
        return WalletPassImages(files)
    }

    private fun BufferedImage.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { baos ->
            ImageIO.write(this, "png", baos)
            baos.toByteArray()
        }

    private fun scaleImage(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, width, height, null)
        g.dispose()
        return scaled
    }

    private fun createGradientImage(width: Int, height: Int, start: Color, end: Color, text: String): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, start, width.toFloat(), height.toFloat(), end)
        g.fillRect(0, 0, width, height)
        g.color = Color.WHITE
        g.font = Font(Font.SANS_SERIF, Font.BOLD, (height * 0.18f).toInt().coerceAtLeast(18))
        val metrics = g.fontMetrics
        g.drawString(text.take(18), (width - metrics.stringWidth(text.take(18))) / 2, height / 2 + metrics.ascent / 3)
        g.dispose()
        return image
    }

    private fun fitCenterImage(source: BufferedImage, width: Int, height: Int, background: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = background
        g.fillRect(0, 0, width, height)
        val scale = minOf(width.toDouble() / source.width, height.toDouble() / source.height)
        val drawWidth = (source.width * scale).toInt()
        val drawHeight = (source.height * scale).toInt()
        val x = (width - drawWidth) / 2
        val y = (height - drawHeight) / 2
        g.drawImage(source, x, y, drawWidth, drawHeight, null)
        g.dispose()
        return image
    }
}
