package com.eventmanager.app.data.remote

/**
 * Firebase org member role. Enforced by Security Rules in Firebase mode only.
 *
 * [ADMIN] — org setup / member management (Firestore metadata).
 * [MEMBER] — team device with sync access; app Admin UI still requires local `isAdmin` + card.
 *
 * Legacy values `door` and `pos` deserialize as [MEMBER].
 */
enum class MemberRole {
    ADMIN,
    MEMBER;

    fun storageValue(): String = name.lowercase()

    companion object {
        fun fromStorage(value: String?): MemberRole =
            when (value?.trim()?.lowercase()) {
                "admin" -> ADMIN
                else -> MEMBER // door, pos, member, unknown
            }
    }
}
