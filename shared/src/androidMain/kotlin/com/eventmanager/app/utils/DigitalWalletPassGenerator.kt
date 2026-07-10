package com.eventmanager.app.utils

import android.content.Context
import android.net.Uri
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.platform.createPlatformContext
import com.eventmanager.app.wallet.WalletPassRequest
import com.eventmanager.app.wallet.WalletPassService
import java.io.File

/**
 * Generates a .pkpass package for email attachments.
 * When an Apple Pass Type ID certificate (.p12) is configured in settings, the pass is signed
 * for Apple Wallet. Otherwise an unsigned pass is produced (works with some Android wallet apps).
 */
object DigitalWalletPassGenerator {

    fun createPassFile(
        context: Context,
        serialNumber: String,
        holderName: String,
        qrPayload: String,
        logoUriString: String? = null,
        associationName: String = "Collectif Nocturne",
    ): File? {
        val platformContext = createPlatformContext(context)
        val settingsManager = settingsManagerFor(platformContext)
        val fileManager = PlatformFileManager(platformContext)
        val certBytes = fileManager.getWalletPassCertificateFile()?.readBytes()
        val logoBytes = loadLogoBytes(context, logoUriString) ?: fileManager.getEmailLogoFile()?.readBytes()
        val request = WalletPassRequest(
            serialNumber = serialNumber,
            holderName = holderName,
            qrPayload = qrPayload,
            associationName = associationName,
        )
        val passBytes = WalletPassService.createPassBytes(settingsManager, certBytes, request, logoBytes)
            ?: return null
        val passFile = File(context.cacheDir, "digital_wallet_pass_${serialNumber}.pkpass")
        passFile.writeBytes(passBytes)
        return passFile
    }

    private fun loadLogoBytes(context: Context, logoUriString: String?): ByteArray? {
        if (logoUriString.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(logoUriString))?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
