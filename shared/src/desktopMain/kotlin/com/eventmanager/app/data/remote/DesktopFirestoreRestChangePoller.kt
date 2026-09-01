package com.eventmanager.app.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Near-real-time Firestore sync for desktop packaged builds.
 *
 * Polls each collection with a [lastModified]-only field mask (~lightweight), then fetches
 * full documents only when a row changed. Replaces gRPC GitLive snapshot listeners which
 * hang in jlink runtimes.
 */
internal class DesktopFirestoreRestChangePoller(
    private val client: DesktopFirestoreRestClient,
    private val orgIds: List<String>,
    private val collections: List<String>,
    private val pollIntervalMs: Long,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val lastKnownModified = ConcurrentHashMap<String, Long>()
    private val lastKnownDocIds = ConcurrentHashMap<String, MutableSet<String>>()

    fun start(onChange: suspend (FirestoreRemoteChange) -> Unit) {
        stop()
        pollJob = scope.launch {
            seedCaches()
            while (isActive) {
                for (orgId in orgIds) {
                    for (collection in collections) {
                        pollCollection(orgId, collection, onChange)
                    }
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun seedCaches() {
        for (orgId in orgIds) {
            for (collection in collections) {
                val keyPrefix = cachePrefix(orgId, collection)
                val ids = mutableSetOf<String>()
                for ((docId, summary) in client.listCollection(orgId, collection, pollMask(collection))) {
                    ids += docId
                    lastKnownModified[cacheKey(orgId, collection, docId)] = lastModifiedOf(summary) ?: 0L
                }
                lastKnownDocIds[keyPrefix] = ids
            }
        }
    }

    private suspend fun pollCollection(
        orgId: String,
        collection: String,
        onChange: suspend (FirestoreRemoteChange) -> Unit,
    ) {
        val keyPrefix = cachePrefix(orgId, collection)
        val summaries = client.listCollection(orgId, collection, pollMask(collection))
        val currentIds = mutableSetOf<String>()

        for ((docId, summary) in summaries) {
            currentIds += docId
            val docCacheKey = cacheKey(orgId, collection, docId)
            val remoteLm = lastModifiedOf(summary)
            val previousLm = lastKnownModified[docCacheKey]
            if (remoteLm != null && previousLm != null && remoteLm <= previousLm) continue
            if (remoteLm == null && previousLm != null) continue

            val fullData = client.getDocument(orgId, collection, docId)
            val effectiveLm = remoteLm ?: lastModifiedOf(fullData.orEmpty()) ?: System.currentTimeMillis()
            if (fullData.isNullOrEmpty()) {
                lastKnownModified[docCacheKey] = effectiveLm
                continue
            }
            lastKnownModified[docCacheKey] = effectiveLm
            onChange(
                FirestoreRemoteChange(
                    orgId = orgId,
                    collection = collection,
                    documentId = docId,
                    data = fullData,
                    deleted = false,
                ),
            )
        }

        val previousIds = lastKnownDocIds[keyPrefix].orEmpty()
        for (removedId in previousIds - currentIds) {
            lastKnownModified.remove(cacheKey(orgId, collection, removedId))
            onChange(
                FirestoreRemoteChange(
                    orgId = orgId,
                    collection = collection,
                    documentId = removedId,
                    data = null,
                    deleted = true,
                ),
            )
        }
        lastKnownDocIds[keyPrefix] = currentIds
    }

    private fun cachePrefix(orgId: String, collection: String): String = "$orgId/$collection"

    private fun cacheKey(orgId: String, collection: String, docId: String): String =
        "$orgId/$collection/$docId"

    private fun lastModifiedOf(data: Map<String, Any?>): Long? {
        for (key in listOf("lastModified", "migratedAt", "updatedAt")) {
            when (val raw = data[key]) {
                is Number -> return raw.toLong()
                is String -> raw.toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    companion object {
        private val LAST_MODIFIED_MASK = listOf("lastModified")

        private fun pollMask(collection: String): List<String> = when (collection) {
            "metadata" -> listOf("migratedAt", "lastModified", "updatedAt")
            else -> LAST_MODIFIED_MASK
        }
    }
}
