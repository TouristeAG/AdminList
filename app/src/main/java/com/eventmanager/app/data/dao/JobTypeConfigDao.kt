package com.eventmanager.app.data.dao

import androidx.room.*
import com.eventmanager.app.data.models.JobTypeConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface JobTypeConfigDao {
    @Query("SELECT * FROM job_type_configs WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveJobTypeConfigs(): Flow<List<JobTypeConfig>>

    @Query("SELECT * FROM job_type_configs ORDER BY name ASC")
    fun getAllJobTypeConfigs(): Flow<List<JobTypeConfig>>

    @Query("SELECT * FROM job_type_configs WHERE id = :id")
    suspend fun getJobTypeConfigById(id: Long): JobTypeConfig?

    @Query("SELECT * FROM job_type_configs WHERE name = :name")
    suspend fun getJobTypeConfigByName(name: String): JobTypeConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobTypeConfig(config: JobTypeConfig): Long

    @Update
    suspend fun updateJobTypeConfig(config: JobTypeConfig)

    @Delete
    suspend fun deleteJobTypeConfig(config: JobTypeConfig)

    @Query("DELETE FROM job_type_configs WHERE id = :id")
    suspend fun deleteJobTypeConfigById(id: Long)

    @Query("UPDATE job_type_configs SET isActive = :isActive WHERE id = :id")
    suspend fun updateJobTypeConfigStatus(id: Long, isActive: Boolean)

    @Query("SELECT * FROM job_type_configs WHERE isShiftJob = 1 AND isActive = 1")
    fun getShiftJobTypes(): Flow<List<JobTypeConfig>>

    @Query("SELECT * FROM job_type_configs WHERE isOrionJob = 1 AND isActive = 1")
    fun getOrionJobTypes(): Flow<List<JobTypeConfig>>

    @Query("DELETE FROM job_type_configs")
    suspend fun deleteAllJobTypeConfigs()
}
