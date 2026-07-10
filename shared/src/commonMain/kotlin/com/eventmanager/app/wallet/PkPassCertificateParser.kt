package com.eventmanager.app.wallet

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

data class WalletPassCertificateInfo(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val passTypeIdentifier: String?,
    val teamIdentifier: String?,
)

object PkPassCertificateParser {
    fun loadPkcs12(certificateBytes: ByteArray, password: CharArray): WalletPassCertificateInfo? = runCatching {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(ByteArrayInputStream(certificateBytes), password)
        val alias = keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) } ?: return null
        val privateKey = keyStore.getKey(alias, password) as? PrivateKey ?: return null
        val certificate = keyStore.getCertificate(alias) as? X509Certificate ?: return null
        WalletPassCertificateInfo(
            privateKey = privateKey,
            certificate = certificate,
            passTypeIdentifier = extractPassTypeIdentifier(certificate),
            teamIdentifier = extractTeamIdentifier(certificate),
        )
    }.getOrNull()

    private fun extractPassTypeIdentifier(certificate: X509Certificate): String? {
        val subject = certificate.subjectX500Principal.getName(X500Principal.RFC1779)
        val cn = subject.split(",").map { it.trim() }.firstOrNull { it.startsWith("CN=") }?.removePrefix("CN=")
            ?: return null
        return when {
            cn.startsWith("Pass Type ID: ", ignoreCase = true) -> cn.removePrefix("Pass Type ID: ").trim()
            cn.startsWith("Pass Type ID:", ignoreCase = true) -> cn.removePrefix("Pass Type ID:").trim()
            cn.startsWith("Pass Type ID ", ignoreCase = true) -> cn.removePrefix("Pass Type ID ").trim()
            else -> null
        }
    }

    private fun extractTeamIdentifier(certificate: X509Certificate): String? {
        val subject = certificate.subjectX500Principal.getName(X500Principal.RFC1779)
        return subject.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("OU=") }
            ?.removePrefix("OU=")
            ?.trim()
            ?.takeIf { it.length == 10 && it.all { ch -> ch.isLetterOrDigit() } }
    }
}
