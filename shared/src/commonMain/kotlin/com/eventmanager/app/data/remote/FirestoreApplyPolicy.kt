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

    /**
     * Monotonic counter clock so two taps in the same millisecond cannot share a timestamp
     * (equal timestamps would let a stale in-flight Firebase write win).
     */
    fun nextPeopleCounterTimestamp(previous: Long, now: Long = System.currentTimeMillis()): Long =
        if (now > previous) now else previous + 1L

    /**
     * People-counter cells are independent of venue [lastModified]. A newer venue snapshot
     * (announcement, name, …) must never roll a newer local count back to an older remote one.
     */
    fun mergePeopleCounter(
        local: PeopleCounterSnapshot?,
        remoteCount: Int?,
        remoteWriterDeviceId: String?,
        remoteWriterAccountEmail: String?,
        remoteLastModified: Long?,
    ): PeopleCounterSnapshot {
        val remotePcm = remoteLastModified ?: 0L
        if (local != null && shouldKeepLocal(local.lastModified, remotePcm)) {
            return local
        }
        return PeopleCounterSnapshot(
            count = remoteCount ?: local?.count ?: 0,
            writerDeviceId = remoteWriterDeviceId ?: local?.writerDeviceId.orEmpty(),
            writerAccountEmail = remoteWriterAccountEmail ?: local?.writerAccountEmail.orEmpty(),
            lastModified = remoteLastModified ?: local?.lastModified ?: 0L,
        )
    }
}

data class PeopleCounterSnapshot(
    val count: Int,
    val writerDeviceId: String,
    val writerAccountEmail: String,
    val lastModified: Long,
)
