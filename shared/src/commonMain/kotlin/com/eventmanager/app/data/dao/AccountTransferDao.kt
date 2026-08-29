package com.eventmanager.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eventmanager.app.data.models.AccountHolderType
import com.eventmanager.app.data.models.AccountTransfer
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountTransferDao {
    @Query("SELECT * FROM account_transfers ORDER BY createdAt DESC")
    fun getAllAccountTransfers(): Flow<List<AccountTransfer>>

    @Query("SELECT * FROM account_transfers ORDER BY createdAt DESC")
    suspend fun getAllAccountTransfersOnce(): List<AccountTransfer>

    @Query("SELECT * FROM account_transfers WHERE holderType = :holderType AND holderId = :holderId ORDER BY createdAt DESC")
    suspend fun getTransfersForHolder(holderType: AccountHolderType, holderId: String): List<AccountTransfer>

    @Query("SELECT * FROM account_transfers WHERE holderType = :holderType AND holderId = :holderId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentTransfersForHolder(holderType: AccountHolderType, holderId: String, limit: Int): List<AccountTransfer>

    @Query("SELECT * FROM account_transfers WHERE transferId = :transferId LIMIT 1")
    suspend fun getByTransferId(transferId: String): AccountTransfer?

    @Query("SELECT * FROM account_transfers WHERE sourceReference = :sourceReference LIMIT 1")
    suspend fun getBySourceReference(sourceReference: String): AccountTransfer?

    @Query("SELECT * FROM account_transfers WHERE createdAt BETWEEN :startMs AND :endMs ORDER BY createdAt ASC")
    suspend fun getTransfersBetween(startMs: Long, endMs: Long): List<AccountTransfer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTransfer(transfer: AccountTransfer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTransfersAll(transfers: List<AccountTransfer>): List<Long>

    @Update
    suspend fun updateAccountTransfer(transfer: AccountTransfer)

    @Update
    suspend fun updateAccountTransfersAll(transfers: List<AccountTransfer>)

    @Delete
    suspend fun deleteAccountTransfer(transfer: AccountTransfer)

    @Delete
    suspend fun deleteAccountTransfersAll(transfers: List<AccountTransfer>)

    @Query("DELETE FROM account_transfers")
    suspend fun deleteAllAccountTransfers()

    @Query("DELETE FROM account_transfers WHERE firebaseOrgId = :orgId")
    suspend fun deleteAllForOrg(orgId: String)

    @Query("DELETE FROM account_transfers WHERE firebaseOrgId != '' AND firebaseOrgId NOT IN (:orgIds)")
    suspend fun deleteAllNotInOrgs(orgIds: List<String>)
}
