package com.eventmanager.app.platform.hardware

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

sealed class DesktopQrScanResult {
    data class Success(val payload: String) : DesktopQrScanResult()
    data class Error(val message: String) : DesktopQrScanResult()
    data object Cancelled : DesktopQrScanResult()
    data object NotFound : DesktopQrScanResult()
}

/** Image-file QR decode (webcam scanning is handled by [DesktopWebcamQrScanView]). */
class DesktopQrScanner {
    suspend fun scanFromImageFile(): DesktopQrScanResult = withContext(Dispatchers.Main) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open QR code image"
            fileFilter = FileNameExtensionFilter("Image files", "png", "jpg", "jpeg", "gif", "bmp", "webp")
            isAcceptAllFileFilterUsed = true
        }
        when (chooser.showOpenDialog(null)) {
            JFileChooser.APPROVE_OPTION -> {
                val file = chooser.selectedFile ?: return@withContext DesktopQrScanResult.Cancelled
                withContext(Dispatchers.IO) {
                    runCatching {
                        val image = ImageIO.read(file)
                            ?: return@withContext DesktopQrScanResult.Error("Could not read image file.")
                        decodeBufferedImage(image)?.let { DesktopQrScanResult.Success(it) }
                            ?: DesktopQrScanResult.NotFound
                    }.getOrElse { DesktopQrScanResult.Error(it.message ?: "Failed to decode QR from image.") }
                }
            }
            else -> DesktopQrScanResult.Cancelled
        }
    }

    private fun decodeBufferedImage(image: BufferedImage): String? {
        val reader = MultiFormatReader()
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decode(bitmap).text
        } catch (_: NotFoundException) {
            null
        }
    }
}
