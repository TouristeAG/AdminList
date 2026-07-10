package com.eventmanager.app.wallet

import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.security.cert.X509Certificate

internal object PkPassSigner {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun signManifest(
        manifestBytes: ByteArray,
        certificateInfo: WalletPassCertificateInfo,
        wwdrCertificate: X509Certificate,
    ): ByteArray {
        val generator = CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(
            JcaSimpleSignerInfoGeneratorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build("SHA1withRSA", certificateInfo.privateKey, certificateInfo.certificate)
        )
        generator.addCertificates(
            JcaCertStore(listOf(certificateInfo.certificate, wwdrCertificate))
        )
        val signedData = generator.generate(CMSProcessableByteArray(manifestBytes), false)
        return signedData.encoded
    }
}
