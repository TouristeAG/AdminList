package com.eventmanager.app.wallet

import com.eventmanager.app.data.sync.SettingsManager

object WalletPassService {
    fun createPassBytes(
        settingsManager: SettingsManager,
        certificateBytes: ByteArray?,
        request: WalletPassRequest,
        logoBytes: ByteArray?,
    ): ByteArray? {
        val signingConfig = WalletPassSigningConfigLoader.fromSettings(settingsManager, certificateBytes)
        val images = WalletPassImageRenderer.render(request, logoBytes)
        return DigitalWalletPassBuilder.buildPassBytes(request, images, signingConfig)
    }

    fun isAppleWalletSigningConfigured(settingsManager: SettingsManager, certificateBytes: ByteArray?): Boolean =
        WalletPassSigningConfigLoader.fromSettings(settingsManager, certificateBytes) != null
}
