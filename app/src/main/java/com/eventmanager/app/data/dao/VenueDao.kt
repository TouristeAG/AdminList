package com.eventmanager.app.data.dao

import androidx.room.*
import com.eventmanager.app.data.models.VenueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VenueDao {
    @Query("SELECT * FROM venues WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveVenues(): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues ORDER BY name ASC")
    fun getAllVenues(): Flow<List<VenueEntity>>

    @Query("SELECT * FROM venues WHERE id = :id")
    suspend fun getVenueById(id: Long): VenueEntity?

    @Query("SELECT * FROM venues WHERE name = :name")
    suspend fun getVenueByName(name: String): VenueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVenue(venue: VenueEntity): Long

    @Update
    suspend fun updateVenue(venue: VenueEntity)

    @Delete
    suspend fun deleteVenue(venue: VenueEntity)

    @Query("DELETE FROM venues WHERE id = :id")
    suspend fun deleteVenueById(id: Long)

    @Query("UPDATE venues SET isActive = :isActive WHERE id = :id")
    suspend fun updateVenueStatus(id: Long, isActive: Boolean)

    @Query("DELETE FROM venues")
    suspend fun deleteAllVenues()
}
