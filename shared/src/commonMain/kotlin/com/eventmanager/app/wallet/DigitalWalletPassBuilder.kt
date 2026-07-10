package com.eventmanager.app.wallet

import com.eventmanager.app.data.sync.SettingsManager
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object WalletPassSigningConfigLoader {
    fun fromSettings(settingsManager: SettingsManager, certificateBytes: ByteArray?): WalletPassSigningConfig? {
        if (certificateBytes == null || certificateBytes.isEmpty()) return null
        val password = settingsManager.getWalletPassCertificatePassword().toCharArray()
        if (password.isEmpty()) return null
        val info = PkPassCertificateParser.loadPkcs12(certificateBytes, password) ?: return null
        val passTypeId = settingsManager.getWalletPassTypeIdentifier().ifBlank { info.passTypeIdentifier ?: return null }
        val teamId = settingsManager.getWalletPassTeamIdentifier().ifBlank { info.teamIdentifier ?: return null }
        return WalletPassSigningConfig(
            passTypeIdentifier = passTypeId,
            teamIdentifier = teamId,
            certificateBytes = certificateBytes,
            certificatePassword = password,
        )
    }
}

object DigitalWalletPassBuilder {
    private const val UNSIGNED_PASS_TYPE = "pass.com.eventmanager.app.entry"
    private const val UNSIGNED_TEAM_ID = "EVENTMGR"

    fun buildPassBytes(
        request: WalletPassRequest,
        images: WalletPassImages,
        signingConfig: WalletPassSigningConfig?,
    ): ByteArray? = runCatching {
        val passTypeId = signingConfig?.passTypeIdentifier ?: UNSIGNED_PASS_TYPE
        val teamId = signingConfig?.teamIdentifier ?: UNSIGNED_TEAM_ID
        val passJson = PassJsonBuilder.build(request, passTypeId, teamId).toByteArray(Charsets.UTF_8)

        val files = linkedMapOf<String, ByteArray>()
        files["pass.json"] = passJson
        files.putAll(images.files)

        if (signingConfig != null) {
            val certInfo = PkPassCertificateParser.loadPkcs12(
                signingConfig.certificateBytes,
                signingConfig.certificatePassword,
            ) ?: error("Invalid wallet pass certificate")
            val manifestJson = PkPassManifestBuilder.buildManifestJson(files).toByteArray(Charsets.UTF_8)
            files["manifest.json"] = manifestJson
            files["signature"] = PkPassSigner.signManifest(
                manifestBytes = manifestJson,
                certificateInfo = certInfo,
                wwdrCertificate = AppleWwdrCertificates.wwdrG4Certificate(),
            )
        }

        zipPassPackage(files)
    }.getOrNull()

    private fun zipPassPackage(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
