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
