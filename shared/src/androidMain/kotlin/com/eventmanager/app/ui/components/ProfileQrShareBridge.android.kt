package com.eventmanager.app.ui.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.eventmanager.app.R
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.utils.QRCodeUtils
import java.io.File
import java.io.FileOutputStream

internal actual object ProfileQrShareBridge {
    actual fun shareProfileQrCode(
        platformContext: PlatformContext,
        qrPayload: String,
        fileName: String,
        title: String,
    ) {
        val context = platformContext.androidContext
        val bitmap = QRCodeUtils.generateQrImageBitmap(qrPayload, 512) ?: return
        runCatching {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_qr_code)))
        }
    }
}
