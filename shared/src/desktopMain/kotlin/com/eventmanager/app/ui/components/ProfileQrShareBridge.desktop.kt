package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.DesktopFileActions
import com.eventmanager.app.platform.PlatformContext
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal actual object ProfileQrShareBridge {
    actual fun shareProfileQrCode(
        platformContext: PlatformContext,
        qrPayload: String,
        fileName: String,
        title: String,
    ) {
        if (qrPayload.isBlank()) return
        runCatching {
            val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 512, 512, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    image.setRGB(x, y, if (bitMatrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
                }
            }
            val file = File(platformContext.dataDir, fileName)
            ImageIO.write(image, "png", file)
            DesktopFileActions.share(file)
        }
    }
}
