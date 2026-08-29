package com.eventmanager.app.data.remote

object FirestoreErrors {
    fun isPermissionDenied(error: Throwable?): Boolean {
        val msg = error?.message.orEmpty()
        return msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            msg.contains("Missing or insufficient permissions", ignoreCase = true)
    }
}
