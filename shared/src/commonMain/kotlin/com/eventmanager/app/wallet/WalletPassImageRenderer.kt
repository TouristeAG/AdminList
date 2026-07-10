package com.eventmanager.app.wallet

expect object WalletPassImageRenderer {
    fun render(request: WalletPassRequest, logoBytes: ByteArray?): WalletPassImages
}
