package com.eventmanager.app.data.remote

import com.eventmanager.app.platform.PlatformContext
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal actual suspend fun firebaseStoragePutJpeg(
    bucket: String,
    path: String,
    jpegBytes: ByteArray,
    platformContext: PlatformContext?,
): String? = withContext(Dispatchers.IO) {
    if (jpegBytes.isEmpty() || path.isBlank() || bucket.isBlank()) {
        error("Missing image, path, or Storage bucket")
    }
    val storage = storageForBucket(bucket)
    val ref = storage.getReference(path)
    val metadata = StorageMetadata.Builder()
        .setContentType("image/jpeg")
        .build()
    ref.putBytes(jpegBytes, metadata).awaitStorage()
    ref.downloadUrl.awaitStorage().toString().takeIf { it.isNotBlank() }
        ?: error("Storage upload succeeded but no download URL was returned")
}

internal actual suspend fun firebaseStorageDeleteObject(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): Boolean = withContext(Dispatchers.IO) {
    if (path.isBlank() || bucket.isBlank()) return@withContext false
    runCatching {
        storageForBucket(bucket).getReference(path).delete().awaitStorage()
        true
    }.getOrElse { e ->
        val message = e.message.orEmpty().lowercase()
        "object does not exist" in message || "not found" in message || "404" in message
    }
}

private const val PROFILE_PHOTO_MAX_BYTES = 5L * 1024 * 1024

internal actual suspend fun firebaseStorageGetBytes(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): ByteArray? = withContext(Dispatchers.IO) {
    if (path.isBlank() || bucket.isBlank()) return@withContext null
    val fromSdk = runCatching {
        storageForBucket(bucket).getReference(path).getBytes(PROFILE_PHOTO_MAX_BYTES).awaitStorage()
    }.onFailure { e ->
        println("Profile photo Storage SDK download failed ($bucket/$path): ${e.message}")
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    if (fromSdk != null) return@withContext fromSdk
    val token = androidFirebaseIdToken() ?: return@withContext null
    firebaseStorageDownloadJpegRest(bucket, path, token)
}

private suspend fun androidFirebaseIdToken(): String? {
    val gitlive = runCatching { Firebase.auth.currentUser?.getIdToken(false) }.getOrNull()
    if (!gitlive.isNullOrBlank()) return gitlive
    val nativeUser = FirebaseAuth.getInstance().currentUser ?: return null
    return runCatching { nativeUser.getIdToken(false).awaitStorage().token }.getOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun storageForBucket(bucket: String): FirebaseStorage {
    val normalized = normalizeFirebaseStorageBucket(bucket)
    val app = FirebaseApp.getInstance()
    return FirebaseStorage.getInstance(app, "gs://$normalized")
}

private suspend fun <T> Task<T>.awaitStorage(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else {
                cont.resumeWithException(
                    task.exception ?: RuntimeException("Firebase Storage task failed"),
                )
            }
        }
    }
