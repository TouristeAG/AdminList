package com.eventmanager.app.platform.hardware

import com.github.sarxos.webcam.Webcam
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop QR scanner using the default webcam and ZXing.
 */
class DesktopQrScanner {
    suspend fun scanOnce(timeoutMs: Long = 30_000): String? = withContext(Dispatchers.IO) {
        val webcam = Webcam.getDefault() ?: return@withContext null
        if (!webcam.isOpen) webcam.open()
        try {
            val deadline = System.currentTimeMillis() + timeoutMs
            val reader = MultiFormatReader()
            while (System.currentTimeMillis() < deadline) {
                val image = webcam.image ?: continue
                val source = BufferedImageLuminanceSource(image)
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                try {
                    return@withContext reader.decode(bitmap).text
                } catch (_: NotFoundException) {
                    Thread.sleep(100)
                }
            }
            null
        } finally {
            if (webcam.isOpen) webcam.close()
        }
    }
}
