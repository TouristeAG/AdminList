package com.eventmanager.app.data.security.crypto

import org.bouncycastle.crypto.engines.AESFastEngine
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.digests.SHA256Digest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

interface OrgCryptoService {
    fun isConfigured(orgId: String): Boolean
    fun encrypt(plainText: String, orgId: String): String
    fun decrypt(cipherText: String, orgId: String): String
    fun hashForLookup(value: String, orgId: String): String
    fun invalidateCachedKey(orgId: String) {}
}

@OptIn(ExperimentalEncodingApi::class)
class DefaultOrgCryptoService(
    private val passphraseProvider: (orgId: String) -> String?,
) : OrgCryptoService {

    @Volatile
    private var keyCache: Map<String, ByteArray> = emptyMap()

    @Volatile
    private var configuredCache: Map<String, Boolean> = emptyMap()

    override fun isConfigured(orgId: String): Boolean {
        val id = orgId.trim()
        if (id.isBlank()) return false
        configuredCache[id]?.let { return it }
        val configured = !passphraseProvider(id).isNullOrBlank()
        configuredCache = configuredCache + (id to configured)
        return configured
    }

    override fun encrypt(plainText: String, orgId: String): String {
        if (plainText.isEmpty()) return ""
        if (plainText.startsWith("v1:")) return plainText
        val key = deriveKey(orgId) ?: return plainText
        val nonce = ByteArray(12) { Random.nextInt(0, 256).toByte() }
        val plain = plainText.encodeToByteArray()
        val cipher = GCMBlockCipher(AESFastEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val out = ByteArray(cipher.getOutputSize(plain.size))
        val len = cipher.processBytes(plain, 0, plain.size, out, 0)
        cipher.doFinal(out, len)
        val payload = nonce + out
        return "v1:" + Base64.encode(payload)
    }

    override fun decrypt(cipherText: String, orgId: String): String {
        if (cipherText.isEmpty()) return ""
        if (!cipherText.startsWith("v1:")) return cipherText
        val key = deriveKey(orgId) ?: return cipherText
        val blob = runCatching { Base64.decode(cipherText.removePrefix("v1:")) }.getOrNull() ?: return cipherText
        if (blob.size < 13) return cipherText
        val nonce = blob.copyOfRange(0, 12)
        val cipherBytes = blob.copyOfRange(12, blob.size)
        return runCatching {
            val cipher = GCMBlockCipher(AESFastEngine())
            cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
            val out = ByteArray(cipher.getOutputSize(cipherBytes.size))
            val len = cipher.processBytes(cipherBytes, 0, cipherBytes.size, out, 0)
            val finalLen = cipher.doFinal(out, len)
            out.decodeToString(0, len + finalLen)
        }.getOrDefault(cipherText)
    }

    override fun hashForLookup(value: String, orgId: String): String {
        if (value.isBlank()) return ""
        val digest = SHA256Digest()
        val orgBytes = orgId.trim().encodeToByteArray()
        val valueBytes = value.trim().uppercase().encodeToByteArray()
        digest.update(orgBytes, 0, orgBytes.size)
        digest.update(byteArrayOf(0), 0, 1)
        digest.update(valueBytes, 0, valueBytes.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    override fun invalidateCachedKey(orgId: String) {
        val id = orgId.trim()
        if (id.isBlank()) {
            keyCache = emptyMap()
            configuredCache = emptyMap()
            return
        }
        keyCache = keyCache - id
        configuredCache = configuredCache - id
    }

    private fun deriveKey(orgId: String): ByteArray? {
        val id = orgId.trim()
        if (id.isBlank()) return null
        keyCache[id]?.let { return it }
        val passphrase = passphraseProvider(id)?.takeIf { it.isNotBlank() } ?: run {
            configuredCache = configuredCache + (id to false)
            return null
        }
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(
            passphrase.encodeToByteArray(),
            id.encodeToByteArray(),
            600_000,
        )
        val key = (gen.generateDerivedParameters(256) as KeyParameter).key
        keyCache = keyCache + (id to key)
        configuredCache = configuredCache + (id to true)
        return key
    }
}

object OrgCryptoRegistry {
    private var instance: OrgCryptoService = DefaultOrgCryptoService { null }

    fun install(service: OrgCryptoService) {
        instance = service
    }

    fun get(): OrgCryptoService = instance

    fun invalidateCachedKey(orgId: String) {
        instance.invalidateCachedKey(orgId)
    }
}
