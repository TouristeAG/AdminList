package com.eventmanager.app.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable offline queue for Firebase remote writes (Firebase mode only).
 */
@Entity(
    tableName = "pending_remote_writes",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["collection", "documentId"]),
    ]
)
data class PendingRemoteWrite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orgId: String = "",
    val collection: String,
    val documentId: String,
    val payloadJson: String,
    val operation: String, // UPSERT | DELETE
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
