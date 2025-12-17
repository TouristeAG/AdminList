package com.eventmanager.app.data.dao

import androidx.room.*
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.VolunteerRank
import kotlinx.coroutines.flow.Flow

@Dao
interface VolunteerDao {
    @Query("SELECT * FROM volunteers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveVolunteers(): Flow<List<Volunteer>>

    @Query("SELECT * FROM volunteers ORDER BY name ASC")
    fun getAllVolunteers(): Flow<List<Volunteer>>
    
    @Query("SELECT * FROM volunteers WHERE isActive = 0 ORDER BY name ASC")
    fun getInactiveVolunteers(): Flow<List<Volunteer>>

    @Query("SELECT * FROM volunteers WHERE id = :id")
    suspend fun getVolunteerById(id: Long): Volunteer?

    @Query("SELECT * FROM volunteers WHERE currentRank = :rank AND isActive = 1")
    fun getVolunteersByRank(rank: VolunteerRank): Flow<List<Volunteer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolunteer(volunteer: Volunteer): Long

    @Update
    suspend fun updateVolunteer(volunteer: Volunteer)

    @Delete
    suspend fun deleteVolunteer(volunteer: Volunteer)

    @Query("DELETE FROM volunteers WHERE id = :id")
    suspend fun deleteVolunteerById(id: Long)

    @Query("SELECT * FROM volunteers WHERE lastModified > :timestamp")
    suspend fun getVolunteersModifiedAfter(timestamp: Long): List<Volunteer>

    @Query("UPDATE volunteers SET lastModified = :timestamp WHERE id = :id")
    suspend fun updateLastModified(id: Long, timestamp: Long)

    @Query("UPDATE volunteers SET isActive = :isActive WHERE id = :id")
    suspend fun updateVolunteerStatus(id: Long, isActive: Boolean)
    
    @Query("SELECT * FROM volunteers WHERE sheetsId = :sheetsId")
    suspend fun getVolunteerBySheetsId(sheetsId: String): Volunteer?
    
    @Query("SELECT * FROM volunteers WHERE name = :name")
    suspend fun getVolunteerByName(name: String): Volunteer?
    
    @Query("DELETE FROM volunteers")
    suspend fun deleteAllVolunteers()
}

