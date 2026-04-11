package com.eventmanager.app.data.dao

import androidx.room.*
import com.eventmanager.app.data.models.Guest
import kotlinx.coroutines.flow.Flow

@Dao
interface GuestDao {
    @Query("SELECT * FROM guests WHERE venueName = :venueName ORDER BY name ASC")
    fun getGuestsByVenue(venueName: String): Flow<List<Guest>>

    @Query("SELECT * FROM guests ORDER BY name ASC")
    fun getAllGuests(): Flow<List<Guest>>

    @Query("SELECT * FROM guests WHERE id = :id")
    suspend fun getGuestById(id: Long): Guest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuest(guest: Guest): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuestsAll(guests: List<Guest>): List<Long>

    @Update
    suspend fun updateGuest(guest: Guest)

    @Update
    suspend fun updateGuestsAll(guests: List<Guest>)

    @Delete
    suspend fun deleteGuest(guest: Guest)

    @Delete
    suspend fun deleteGuestsAll(guests: List<Guest>)

    @Query("DELETE FROM guests WHERE id = :id")
    suspend fun deleteGuestById(id: Long)

    @Query("SELECT * FROM guests WHERE lastModified > :timestamp")
    suspend fun getGuestsModifiedAfter(timestamp: Long): List<Guest>
    
    @Query("SELECT * FROM guests WHERE isVolunteerBenefit = 1")
    suspend fun getVolunteerBenefitGuests(): List<Guest>
    
    @Query("SELECT * FROM guests WHERE isVolunteerBenefit = 1 AND volunteerId = :volunteerId")
    suspend fun getVolunteerBenefitGuest(volunteerId: String): Guest?
    
    @Query("SELECT * FROM guests WHERE sheetsId = :sheetsId")
    suspend fun getGuestBySheetsId(sheetsId: String): Guest?

    @Query("SELECT * FROM guests WHERE nanoId = :nanoId LIMIT 1")
    suspend fun getGuestByNanoId(nanoId: String): Guest?
    
    @Query("SELECT * FROM guests WHERE name = :name")
    suspend fun getGuestByName(name: String): Guest?
    
    @Query("DELETE FROM guests")
    suspend fun deleteAllGuests()

    @Query("DELETE FROM guests WHERE isTemporaryGuest = 1")
    suspend fun deleteTemporaryGuests()
}

