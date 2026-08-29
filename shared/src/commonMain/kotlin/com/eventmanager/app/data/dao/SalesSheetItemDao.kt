package com.eventmanager.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eventmanager.app.data.models.SalesSheetItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesSheetItemDao {
    @Query("SELECT * FROM sales_sheet_items WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveSalesSheetItems(): Flow<List<SalesSheetItem>>

    @Query("SELECT * FROM sales_sheet_items ORDER BY name ASC")
    fun getAllSalesSheetItems(): Flow<List<SalesSheetItem>>

    @Query("SELECT * FROM sales_sheet_items WHERE id = :id")
    suspend fun getSalesSheetItemById(id: Long): SalesSheetItem?

    @Query("SELECT * FROM sales_sheet_items WHERE name = :name")
    suspend fun getSalesSheetItemByName(name: String): SalesSheetItem?

    @Query("SELECT * FROM sales_sheet_items WHERE name = :name AND firebaseOrgId = :orgId LIMIT 1")
    suspend fun getSalesSheetItemByNameAndOrg(name: String, orgId: String): SalesSheetItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesSheetItem(item: SalesSheetItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesSheetItemsAll(items: List<SalesSheetItem>): List<Long>

    @Update
    suspend fun updateSalesSheetItem(item: SalesSheetItem)

    @Update
    suspend fun updateSalesSheetItemsAll(items: List<SalesSheetItem>)

    @Delete
    suspend fun deleteSalesSheetItem(item: SalesSheetItem)

    @Delete
    suspend fun deleteSalesSheetItemsAll(items: List<SalesSheetItem>)

    @Query("UPDATE sales_sheet_items SET isActive = :isActive WHERE id = :id")
    suspend fun updateSalesSheetItemStatus(id: Long, isActive: Boolean)

    @Query("DELETE FROM sales_sheet_items")
    suspend fun deleteAllSalesSheetItems()

    @Query("DELETE FROM sales_sheet_items WHERE firebaseOrgId = :orgId")
    suspend fun deleteAllForOrg(orgId: String)

    @Query("DELETE FROM sales_sheet_items WHERE firebaseOrgId != '' AND firebaseOrgId NOT IN (:orgIds)")
    suspend fun deleteAllNotInOrgs(orgIds: List<String>)
}
