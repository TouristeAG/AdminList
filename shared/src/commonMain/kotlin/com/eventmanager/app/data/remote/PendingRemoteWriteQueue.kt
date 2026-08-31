package com.eventmanager.app.data.remote

import com.eventmanager.app.data.dao.PendingRemoteWriteDao
import com.eventmanager.app.data.models.PendingRemoteWrite
import com.eventmanager.app.data.security.crypto.SensitiveFieldCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Durable + in-memory offline queue for Firebase upserts/deletes.
 * Used when the Firestore SDK is unavailable or a write fails transiently.
 */
class PendingRemoteWriteQueue(
    private val dao: PendingRemoteWriteDao? = null,
    private val maxAttempts: Int = 12,
    private val activeOrgId: () -> String = { "" },
) {
    private val mutex = Mutex()
    private val memory = ArrayDeque<PendingRemoteWrite>()
    private var nextMemoryId = 1L

    suspend fun enqueueUpsert(collection: String, documentId: String, payloadJson: String, orgId: String? = null) {
        enqueue(collection, documentId, payloadJson, "UPSERT", orgId)
    }

    suspend fun enqueueDelete(collection: String, documentId: String, orgId: String? = null) {
        enqueue(collection, documentId, "{}", "DELETE", orgId)
    }

    /** Snapshot count without draining the queue. */
    suspend fun count(): Int = mutex.withLock {
        if (dao != null) {
            dao.count()
        } else {
            memory.size
        }
    }

    suspend fun countWithFailedAttempts(): Int = mutex.withLock {
        if (dao != null) {
            dao.countWithFailedAttempts()
        } else {
            memory.count { it.attempts > 0 }
        }
    }

    private suspend fun notifyPendingChanged() {
        onPendingCountChanged?.invoke(count())
    }

    var onPendingCountChanged: ((Int) -> Unit)? = null

    private suspend fun enqueue(
        collection: String,
        documentId: String,
        payloadJson: String,
        operation: String,
        orgIdOverride: String? = null,
    ) {
        mutex.withLock {
            if (dao != null) {
                dao.deleteByCollectionAndDocument(collection, documentId)
            } else {
                memory.removeAll { it.collection == collection && it.documentId == documentId }
            }
            val orgId = orgIdOverride?.trim()?.takeIf { it.isNotBlank() } ?: activeOrgId().trim()
            val storedPayload = if (operation == "DELETE") {
                payloadJson
            } else {
                SensitiveFieldCodec.encryptPayloadJson(payloadJson, orgId)
            }
            val row = if (dao != null) {
                PendingRemoteWrite(
                    orgId = orgId,
                    collection = collection,
                    documentId = documentId,
                    payloadJson = storedPayload,
                    operation = operation,
                )
            } else {
                PendingRemoteWrite(
                    id = nextMemoryId++,
                    orgId = orgId,
                    collection = collection,
                    documentId = documentId,
                    payloadJson = storedPayload,
                    operation = operation,
                )
            }
            if (dao != null) {
                dao.insert(row)
            } else {
                memory.addLast(row)
            }
        }
        notifyPendingChanged()
    }

    /** Snapshot of pending rows for [orgId]. Rows remain until [acknowledge]. */
    suspend fun drainForOrg(orgId: String): List<PendingRemoteWrite> = mutex.withLock {
        if (dao != null) {
            dao.getAllForOrgOnce(orgId)
        } else {
            memory.filter { it.orgId == orgId || (it.orgId.isBlank() && orgId.isBlank()) }
        }
    }

    /** Snapshot of pending rows. Rows remain until [acknowledge] — safe for mid-flush failures. */
    suspend fun drain(): List<PendingRemoteWrite> = drainForOrg(activeOrgId())

    suspend fun acknowledge(id: Long) {
        mutex.withLock {
            if (dao != null) {
                dao.deleteById(id)
            } else {
                memory.removeAll { it.id == id }
            }
        }
        notifyPendingChanged()
    }

    suspend fun recordFailedAttempt(id: Long) {
        mutex.withLock {
            if (dao != null) {
                dao.incrementAttempts(id)
            } else {
                val index = memory.indexOfFirst { it.id == id }
                if (index >= 0) {
                    val row = memory[index]
                    memory[index] = row.copy(attempts = row.attempts + 1)
                }
            }
        }
    }

    suspend fun dropExceededMaxAttempts() {
        mutex.withLock {
            if (dao != null) {
                dao.getAllOnce()
                    .filter { it.attempts >= maxAttempts }
                    .forEach { dao.deleteById(it.id) }
            } else {
                memory.removeAll { it.attempts >= maxAttempts }
            }
        }
        notifyPendingChanged()
    }

    suspend fun clearAll() {
        mutex.withLock {
            if (dao != null) {
                dao.clearAll()
            } else {
                memory.clear()
            }
        }
        notifyPendingChanged()
    }
}
