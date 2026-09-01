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

    /**
     * Photos are independent of volunteer/guest [lastModified] except for an explicit clear.
     * A newer local shift update must not drop a remote photo, and a missing remote field
     * must not erase a local photo. A [PROFILE_PHOTO_CLEARED_SENTINEL] with a newer-or-equal
     * clock wins so Remove is not resurrected by a stale snapshot.
     */
    fun mergeProfilePhotoFields(
        localPath: String,
        localUrl: String,
        remotePath: String,
        remoteUrl: String,
        localLastModified: Long = 0L,
        remoteLastModified: Long = 0L,
    ): ProfilePhotoFields {
        val localCleared = isProfilePhotoCleared(localPath, localUrl)
        val remoteCleared = isProfilePhotoCleared(remotePath, remoteUrl)
        if (localCleared && localLastModified >= remoteLastModified) {
            return ProfilePhotoFields(PROFILE_PHOTO_CLEARED_SENTINEL, PROFILE_PHOTO_CLEARED_SENTINEL)
        }
        if (remoteCleared && remoteLastModified >= localLastModified) {
            return ProfilePhotoFields(PROFILE_PHOTO_CLEARED_SENTINEL, PROFILE_PHOTO_CLEARED_SENTINEL)
        }
        val remoteP = remotePath.trim().takeIf { it.isStoredProfilePhotoRef() }.orEmpty()
        val remoteU = remoteUrl.trim().takeIf { it.isStoredProfilePhotoRef() }.orEmpty()
        val localP = localPath.trim().takeIf { it.isStoredProfilePhotoRef() }.orEmpty()
        val localU = localUrl.trim().takeIf { it.isStoredProfilePhotoRef() }.orEmpty()
        return ProfilePhotoFields(
            path = remoteP.ifBlank { localP },
            url = remoteU.ifBlank { localU },
        )
    }

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

data class ProfilePhotoFields(
    val path: String,
    val url: String,
)

data class PeopleCounterSnapshot(
    val count: Int,
    val writerDeviceId: String,
    val writerAccountEmail: String,
    val lastModified: Long,
)
