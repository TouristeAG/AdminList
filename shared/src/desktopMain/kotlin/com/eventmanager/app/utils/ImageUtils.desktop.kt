package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import org.jetbrains.skia.Image
import java.io.File
import javax.imageio.ImageIO

actual object ImageUtils {
    actual fun loadScaledImageBitmap(
        platformContext: PlatformContext,
        resourceName: String,
        maxWidthDp: Dp,
        maxHeightDp: Dp
    ): ImageBitmap? {
        val candidates = listOf(
            File(platformContext.appDataDir, resourceName),
            File(platformContext.appDataDir, "assets/$resourceName"),
            File("assets/$resourceName")
        )
        val file = candidates.firstOrNull { it.exists() } ?: return null
        return runCatching {
            val buffered = ImageIO.read(file) ?: return null
            val maxW = maxWidthDp.value.toInt().coerceAtLeast(1) * 2
            val maxH = maxHeightDp.value.toInt().coerceAtLeast(1) * 2
            val scale = minOf(maxW.toFloat() / buffered.width, maxH.toFloat() / buffered.height, 1f)
            val w = (buffered.width * scale).toInt().coerceAtLeast(1)
            val h = (buffered.height * scale).toInt().coerceAtLeast(1)
            val scaled = buffered.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)
            val output = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            output.graphics.drawImage(scaled, 0, 0, null)
            val bytes = java.io.ByteArrayOutputStream().use { baos ->
                ImageIO.write(output, "png", baos)
                baos.toByteArray()
            }
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
}
