package com.eventmanager.app.utils

import com.eventmanager.app.data.remote.downloadProfilePhotoBytes
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ProfilePhotoDisplayQuality {
    Thumbnail,
    Full,
}

/**
 * Disk + memory cache for profile photos.
 *
 * Avatars persist only a tiny JPEG. Full-size bytes are downloaded when the photo is
 * opened fullscreen, then reused from disk on later enlargements.
 *
 * Revisions are per cache id so a put/evict only invalidates that photo's observers.
 */
object ProfilePhotoImageCache {
    private const val DIR = "profile_photos"
    private const val MEMORY_LIMIT = 96
    private val mutex = Mutex()
    private val revisionFlows = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val blankRevision: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()
    private val memory = object : LinkedHashMap<String, ByteArray>(MEMORY_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > MEMORY_LIMIT
    }

    /** Per-key revision; only collectors for this [cacheId] see bumps. */
    fun revisionState(cacheId: String): StateFlow<Long> {
        val trimmed = cacheId.trim()
        if (trimmed.isBlank()) return blankRevision
        val key = cacheKey(trimmed)
        return revisionFlows.getOrPut(key) { MutableStateFlow(0L) }
    }

    suspend fun load(
        platformContext: PlatformContext,
        url: String,
        quality: ProfilePhotoDisplayQuality,
        storagePath: String = "",
    ): ByteArray? = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val trimmedPath = storagePath.trim()
        val cacheId = trimmedUrl.ifBlank { trimmedPath }
        if (cacheId.isBlank()) return@withContext null

        mutex.withLock {
            cachedBytes(platformContext, cacheId, quality)?.let { return@withContext it }
        }

        val downloaded = downloadProfilePhotoBytes(platformContext, trimmedUrl, trimmedPath)
            ?: return@withContext null

        mutex.withLock {
            storeDownloaded(platformContext, cacheId, downloaded)
            when (quality) {
                ProfilePhotoDisplayQuality.Full -> downloaded
                ProfilePhotoDisplayQuality.Thumbnail ->
                    memory[memoryKey(cacheId, ProfilePhotoDisplayQuality.Thumbnail)] ?: downloaded
            }
        }
    }

    suspend fun putLocal(
        platformContext: PlatformContext,
        url: String,
        jpegBytes: ByteArray,
        extraCacheIds: List<String> = emptyList(),
    ) {
        val keys = (listOf(url) + extraCacheIds).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty() || jpegBytes.isEmpty()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                keys.forEach { storeDownloaded(platformContext, it, jpegBytes) }
                bumpRevisionLocked(keys)
            }
        }
    }

    suspend fun evict(platformContext: PlatformContext?, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        mutex.withLock {
            memory.remove(memoryKey(trimmed, ProfilePhotoDisplayQuality.Thumbnail))
            memory.remove(memoryKey(trimmed, ProfilePhotoDisplayQuality.Full))
            val ctx = platformContext
            if (ctx != null) {
                val dir = cacheDir(ctx)
                runCatching { fileFor(dir, trimmed, ProfilePhotoDisplayQuality.Thumbnail).delete() }
                runCatching { fileFor(dir, trimmed, ProfilePhotoDisplayQuality.Full).delete() }
            }
            bumpRevisionLocked(listOf(trimmed))
        }
    }

    private fun bumpRevisionLocked(cacheIds: Collection<String>) {
        for (id in cacheIds) {
            val trimmed = id.trim()
            if (trimmed.isBlank()) continue
            val key = cacheKey(trimmed)
            val flow = revisionFlows.getOrPut(key) { MutableStateFlow(0L) }
            flow.value = flow.value + 1L
        }
    }

    private fun cachedBytes(
        platformContext: PlatformContext,
        cacheId: String,
        quality: ProfilePhotoDisplayQuality,
    ): ByteArray? {
        val memKey = memoryKey(cacheId, quality)
        memory[memKey]?.let { return it }
        val dir = cacheDir(platformContext)
        val thumbFile = fileFor(dir, cacheId, ProfilePhotoDisplayQuality.Thumbnail)
        val fullFile = fileFor(dir, cacheId, ProfilePhotoDisplayQuality.Full)
        return when (quality) {
            ProfilePhotoDisplayQuality.Thumbnail -> {
                if (thumbFile.exists() && thumbFile.length() > 0L) {
                    return remember(memKey, thumbFile.readBytes())
                }
                val fullBytes = memory[memoryKey(cacheId, ProfilePhotoDisplayQuality.Full)]
                    ?: fullFile.takeIf { it.exists() && it.length() > 0L }?.readBytes()?.also { bytes ->
                        memory[memoryKey(cacheId, ProfilePhotoDisplayQuality.Full)] = bytes
                    }
                    ?: return null
                val thumb = ProfilePhotoCodec.compressToThumbnailJpeg(fullBytes) ?: return null
                runCatching { thumbFile.writeBytes(thumb) }
                remember(memKey, thumb)
            }
            ProfilePhotoDisplayQuality.Full -> {
                if (fullFile.exists() && fullFile.length() > 0L) {
                    remember(memKey, fullFile.readBytes())
                } else {
                    null
                }
            }
        }
    }

    private fun storeDownloaded(
        platformContext: PlatformContext,
        cacheId: String,
        jpegBytes: ByteArray,
    ) {
        val dir = cacheDir(platformContext)
        val fullFile = fileFor(dir, cacheId, ProfilePhotoDisplayQuality.Full)
        val thumbFile = fileFor(dir, cacheId, ProfilePhotoDisplayQuality.Thumbnail)
        runCatching { fullFile.writeBytes(jpegBytes) }
        memory[memoryKey(cacheId, ProfilePhotoDisplayQuality.Full)] = jpegBytes
        val thumb = ProfilePhotoCodec.compressToThumbnailJpeg(jpegBytes)
        if (thumb != null) {
            runCatching { thumbFile.writeBytes(thumb) }
            memory[memoryKey(cacheId, ProfilePhotoDisplayQuality.Thumbnail)] = thumb
        }
    }

    private fun remember(memKey: String, bytes: ByteArray): ByteArray {
        memory[memKey] = bytes
        return bytes
    }

    private fun cacheDir(platformContext: PlatformContext): File =
        File(PlatformFileManager(platformContext).getCacheDirectory(), DIR).also { it.mkdirs() }

    private fun fileFor(dir: File, url: String, quality: ProfilePhotoDisplayQuality): File {
        val suffix = if (quality == ProfilePhotoDisplayQuality.Thumbnail) "thumb" else "full"
        return File(dir, "${cacheKey(url)}.$suffix.jpg")
    }

    private fun memoryKey(url: String, quality: ProfilePhotoDisplayQuality): String =
        "${quality.name}:${cacheKey(url)}"

    internal fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(40)
    }
}
