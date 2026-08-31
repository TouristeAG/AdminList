package com.eventmanager.app.data.sync

enum class BiometricAdminProfileType {
    VOLUNTEER,
    GUEST
}

/** Local biometric admin login is bound to one volunteer or guest profile. */
data class BiometricAdminProfileLink(
    val type: BiometricAdminProfileType,
    val profileId: String
) {
    fun encode(): String = "${type.name}:$profileId"

    companion object {
        fun decode(raw: String?): BiometricAdminProfileLink? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(":", limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            val type = runCatching { BiometricAdminProfileType.valueOf(parts[0]) }.getOrNull() ?: return null
            return BiometricAdminProfileLink(type, parts[1])
        }
    }
}

/** Per-org biometric enrollment (Firebase multi-org). */
data class BiometricAdminOrgEnrollment(
    val orgId: String,
    val link: BiometricAdminProfileLink,
) {
    fun encodeEntry(): String = "${orgId.trim()}:${link.encode()}"

    companion object {
        private const val ENTRY_SEPARATOR = "|"

        fun decodeList(raw: String?): List<BiometricAdminOrgEnrollment> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(ENTRY_SEPARATOR)
                .mapNotNull { entry -> decodeEntry(entry) }
        }

        fun encodeList(enrollments: List<BiometricAdminOrgEnrollment>): String =
            enrollments
                .filter { it.orgId.isNotBlank() }
                .distinctBy { it.orgId.trim() }
                .joinToString(ENTRY_SEPARATOR) { it.encodeEntry() }

        private fun decodeEntry(raw: String): BiometricAdminOrgEnrollment? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            val firstColon = trimmed.indexOf(':')
            if (firstColon <= 0) return null
            val orgId = trimmed.substring(0, firstColon).trim()
            val linkRaw = trimmed.substring(firstColon + 1)
            val link = BiometricAdminProfileLink.decode(linkRaw) ?: return null
            if (orgId.isBlank()) return null
            return BiometricAdminOrgEnrollment(orgId, link)
        }
    }
}
