package com.eventmanager.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eventmanager.app.data.models.PendingRemoteWrite
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingRemoteWriteDao {
    @Query("SELECT * FROM pending_remote_writes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingRemoteWrite>>

    @Query("SELECT * FROM pending_remote_writes ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PendingRemoteWrite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(write: PendingRemoteWrite): Long

    @Query("DELETE FROM pending_remote_writes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM pending_remote_writes WHERE collection = :collection AND documentId = :documentId",
    )
    suspend fun deleteByCollectionAndDocument(collection: String, documentId: String)

    @Query("UPDATE pending_remote_writes SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM pending_remote_writes")
    suspend fun count(): Int

    @Query("SELECT * FROM pending_remote_writes WHERE orgId = :orgId ORDER BY createdAt ASC")
    suspend fun getAllForOrgOnce(orgId: String): List<PendingRemoteWrite>

    @Query("DELETE FROM pending_remote_writes WHERE orgId = :orgId")
    suspend fun clearForOrg(orgId: String)

    @Query("DELETE FROM pending_remote_writes")
    suspend fun clearAll()
}
