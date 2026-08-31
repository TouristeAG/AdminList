package com.eventmanager.app.data.security.crypto

import org.bouncycastle.crypto.digests.SHA256Digest

object BootstrapCodeHash {
    fun hash(code: String): String {
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return ""
        val digest = SHA256Digest()
        val bytes = normalized.encodeToByteArray()
        digest.update(bytes, 0, bytes.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    fun matches(storedHash: String?, storedPlain: String?, submittedCode: String): Boolean {
        val submitted = submittedCode.trim()
        if (submitted.isEmpty()) return false
        if (!storedHash.isNullOrBlank()) {
            return storedHash == hash(submitted)
        }
        if (!storedPlain.isNullOrBlank()) {
            return storedPlain.equals(submitted, ignoreCase = true)
        }
        return false
    }
}
