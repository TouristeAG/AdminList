package com.eventmanager.app.wallet

internal object PassJsonBuilder {
    fun build(
        request: WalletPassRequest,
        passTypeIdentifier: String,
        teamIdentifier: String,
    ): String {
        val safeName = escapeJson(request.holderName)
        val safePayload = escapeJson(request.qrPayload)
        val safeSerial = escapeJson(request.serialNumber)
        val safeAssociationName = escapeJson(request.associationName.ifBlank { "Collectif Nocturne" })
        val safePassType = escapeJson(passTypeIdentifier)
        val safeTeam = escapeJson(teamIdentifier)
        return """
            {"formatVersion":1,"passTypeIdentifier":"$safePassType","serialNumber":"$safeSerial","teamIdentifier":"$safeTeam","organizationName":"$safeAssociationName","description":"Digital Wallet Pass","logoText":"$safeAssociationName","foregroundColor":"rgb(255, 255, 255)","backgroundColor":"rgb(38, 38, 38)","labelColor":"rgb(203, 213, 225)","barcodes":[{"format":"PKBarcodeFormatQR","message":"$safePayload","messageEncoding":"iso-8859-1","altText":"$safeName"}],"barcode":{"format":"PKBarcodeFormatQR","message":"$safePayload","messageEncoding":"iso-8859-1","altText":"$safeName"},"eventTicket":{"primaryFields":[{"key":"holder","label":"PASS HOLDER","value":"$safeName"}],"secondaryFields":[{"key":"entry","label":"ENTRY","value":"Use QR at check-in"}],"auxiliaryFields":[{"key":"reference","label":"REFERENCE","value":"$safeSerial"}],"backFields":[{"key":"compatibility","label":"Digital Wallet Pass","value":"Present this pass at check-in. Works with Apple Wallet and compatible Android wallet apps."}]}}
        """.trimIndent()
    }

    private fun escapeJson(value: String): String =
        buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
}
