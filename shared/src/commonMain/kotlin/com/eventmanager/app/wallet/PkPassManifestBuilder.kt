package com.eventmanager.app.wallet

import java.security.MessageDigest

internal object PkPassManifestBuilder {
    fun buildManifestJson(files: Map<String, ByteArray>): String {
        val entries = files.keys.sorted().joinToString(",") { name ->
            val hash = sha1Hex(files.getValue(name))
            "\"$name\":\"$hash\""
        }
        return "{$entries}"
    }

    private fun sha1Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
