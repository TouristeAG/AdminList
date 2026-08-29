package com.eventmanager.app.data.remote

/**
 * Active remote backend for this device / institution.
 * Default for all existing installs is [SHEETS].
 */
enum class BackendType {
    SHEETS,
    FIREBASE;

    companion object {
        fun fromStorage(value: String?): BackendType =
            when (value?.trim()?.uppercase()) {
                "FIREBASE" -> FIREBASE
                else -> SHEETS
            }
    }
}
