package com.eventmanager.app.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformFileManager actual constructor(private val context: PlatformContext) {
    private fun serviceAccountPath(): File = File(context.appDataDir, "service_account.json")

    actual fun getServiceAccountFile(): File? =
        serviceAccountPath().takeIf { it.exists() }

    actual fun saveServiceAccountJson(json: String): Boolean = runCatching {
        serviceAccountPath().writeText(json)
        true
    }.getOrDefault(false)

    actual fun readServiceAccountJson(): String? =
        serviceAccountPath().takeIf { it.exists() }?.readText()

    private fun gmailOAuthClientPath(): File = File(context.appDataDir, "gmail_oauth_client.json")

    actual fun getGmailOAuthClientFile(): File? =
        gmailOAuthClientPath().takeIf { it.exists() }

    actual fun saveGmailOAuthClientJson(json: String): Boolean = runCatching {
        gmailOAuthClientPath().writeText(json)
        true
    }.getOrDefault(false)

    actual fun readGmailOAuthClientJson(): String? =
        gmailOAuthClientPath().takeIf { it.exists() }?.readText()

    actual fun getLogsDirectory(): File =
        File(context.appDataDir, "logs").also { it.mkdirs() }

    actual fun getCacheDirectory(): File =
        File(context.appDataDir, "cache").also { it.mkdirs() }

    actual fun getUpdatesDirectory(): File =
        File(context.appDataDir, "updates").also { it.mkdirs() }

    private suspend fun pickFile(configure: JFileChooser.() -> Unit): File? = suspendCoroutine { cont ->
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply(configure)
            val file = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else {
                null
            }
            cont.resume(file)
        }
    }

    actual suspend fun pickServiceAccountJsonFile(): String? {
        val file = pickFile {
            dialogTitle = "Select Google Service Account JSON"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            isAcceptAllFileFilterUsed = false
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        }
    }

    actual suspend fun pickGmailOAuthClientJsonFile(): String? {
        val file = pickFile {
            dialogTitle = "Select Gmail OAuth Client JSON"
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            isAcceptAllFileFilterUsed = false
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { file.readText() }.getOrNull()
        }
    }

    private fun emailLogoPath(): File = File(context.appDataDir, "email_logo.png")

    actual fun getEmailLogoFile(): File? = emailLogoPath().takeIf { it.exists() }

    actual fun clearEmailLogoFile(): Boolean = runCatching {
        emailLogoPath().delete()
        true
    }.getOrDefault(false)

    private fun walletPassCertificatePath(): File = File(context.appDataDir, "wallet_pass_certificate.p12")

    actual fun getWalletPassCertificateFile(): File? = walletPassCertificatePath().takeIf { it.exists() }

    actual fun saveWalletPassCertificate(bytes: ByteArray): Boolean = runCatching {
        walletPassCertificatePath().writeBytes(bytes)
        true
    }.getOrDefault(false)

    actual suspend fun pickWalletPassCertificateFile(): ByteArray? {
        val file = pickFile {
            dialogTitle = "Select Apple Wallet Pass Type ID Certificate (.p12)"
            fileFilter = FileNameExtensionFilter("PKCS#12 certificate", "p12", "pfx")
            isAcceptAllFileFilterUsed = false
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { file.readBytes() }.getOrNull()
        }
    }

    actual suspend fun pickEmailLogoImageFile(): String? {
        val selected = pickFile {
            dialogTitle = "Select Email Logo Image"
            fileFilter = FileNameExtensionFilter("Image files", "png", "jpg", "jpeg", "webp", "gif")
            isAcceptAllFileFilterUsed = false
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val buffered = javax.imageio.ImageIO.read(selected) ?: return@runCatching null
                val destination = emailLogoPath()
                destination.parentFile?.mkdirs()
                javax.imageio.ImageIO.write(buffered, "png", destination)
                destination.absolutePath
            }.getOrNull()
        }
    }
}
