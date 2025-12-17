package com.eventmanager.app.data.dao

import androidx.room.*
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.VolunteerRank
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY date DESC")
    fun getAllJobs(): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE volunteerId = :volunteerId ORDER BY date DESC")
    fun getJobsByVolunteer(volunteerId: Long): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE venueName = :venueName ORDER BY date DESC")
    fun getJobsByVenue(venueName: String): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getJobById(id: Long): Job?

    @Query("SELECT * FROM jobs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getJobsByDateRange(startDate: Long, endDate: Long): Flow<List<Job>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: Job): Long

    @Update
    suspend fun updateJob(job: Job)

    @Delete
    suspend fun deleteJob(job: Job)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJobById(id: Long)

    @Query("SELECT * FROM jobs WHERE lastModified > :timestamp")
    suspend fun getJobsModifiedAfter(timestamp: Long): List<Job>

    @Query("UPDATE jobs SET lastModified = :timestamp WHERE id = :id")
    suspend fun updateLastModified(id: Long, timestamp: Long)

    // Query to get volunteer rank based on job history
    @Query("""
        SELECT COUNT(*) FROM jobs 
        WHERE volunteerId = :volunteerId 
        AND date >= :monthStart 
        AND date <= :monthEnd
    """)
    suspend fun getJobCountForMonth(volunteerId: Long, monthStart: Long, monthEnd: Long): Int

    @Query("""
        SELECT COUNT(*) FROM jobs 
        WHERE volunteerId = :volunteerId 
        AND shiftTime = 'AFTER_MIDNIGHT'
        AND date >= :monthStart 
        AND date <= :monthEnd
    """)
    suspend fun getAfterMidnightJobCount(volunteerId: Long, monthStart: Long, monthEnd: Long): Int

    @Query("""
        SELECT COUNT(*) FROM jobs 
        WHERE volunteerId = :volunteerId 
        AND shiftTime = 'BEFORE_MIDNIGHT'
        AND date >= :monthStart 
        AND date <= :monthEnd
    """)
    suspend fun getBeforeMidnightJobCount(volunteerId: Long, monthStart: Long, monthEnd: Long): Int
    
    @Query("SELECT * FROM jobs WHERE sheetsId = :sheetsId")
    suspend fun getJobBySheetsId(sheetsId: String): Job?
    
    @Query("DELETE FROM jobs")
    suspend fun deleteAllJobs()
}

