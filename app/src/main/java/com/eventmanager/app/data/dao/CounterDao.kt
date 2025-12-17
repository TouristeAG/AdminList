package com.eventmanager.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eventmanager.app.data.models.CounterData
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {
    @Query("SELECT * FROM people_counter WHERE id = 1")
    fun getCounter(): Flow<CounterData?>

    @Query("SELECT * FROM people_counter WHERE id = 1")
    suspend fun getCounterOnce(): CounterData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCounter(counter: CounterData)

    @Update
    suspend fun updateCounter(counter: CounterData)

    @Query("DELETE FROM people_counter")
    suspend fun deleteCounter()
}

