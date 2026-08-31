package com.eventmanager.app.data.remote

/**
 * Non-destructive rules for applying Firestore snapshots into Room.
 * Empty/undecodable payloads must never be treated as deletes.
 */
object FirestoreApplyPolicy {
    fun isRemoteDelete(deleted: Boolean): Boolean = deleted

    fun shouldSkipIncompleteSnapshot(deleted: Boolean, data: Map<String, Any?>?): Boolean =
        !deleted && (data == null || data.isEmpty())

    fun shouldKeepLocal(existingLastModified: Long, remoteLastModified: Long): Boolean =
        existingLastModified >= remoteLastModified

    fun orgIdToPersist(existingOrg: String, remoteOrg: String): String =
        existingOrg.trim().ifBlank { remoteOrg.trim() }

    fun needsOrgBackfill(existingOrg: String, remoteOrg: String): Boolean =
        existingOrg.isBlank() && remoteOrg.isNotBlank()
}
