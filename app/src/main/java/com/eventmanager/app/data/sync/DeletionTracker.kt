package com.eventmanager.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks deleted items to prevent them from being re-downloaded during sync
 * and to ensure they are properly deleted from Google Sheets
 */
class DeletionTracker(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("deletion_tracker", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DELETED_GUESTS = "deleted_guests"
        private const val KEY_DELETED_VOLUNTEERS = "deleted_volunteers"
        private const val KEY_DELETED_JOBS = "deleted_jobs"
        private const val KEY_DELETED_JOB_TYPES = "deleted_job_types"
        private const val KEY_DELETED_VENUES = "deleted_venues"
    }
    
    data class DeletedItem(
        val id: String,
        val sheetsId: String?,
        val deletionTime: Long,
        val type: String
    )
    
    suspend fun trackGuestDeletion(guestId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(guestId, sheetsId, deletionTime, "guest")
        addDeletedItem(KEY_DELETED_GUESTS, deletedItem)
    }
    
    suspend fun trackVolunteerDeletion(volunteerId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(volunteerId, sheetsId, deletionTime, "volunteer")
        addDeletedItem(KEY_DELETED_VOLUNTEERS, deletedItem)
    }
    
    suspend fun trackJobDeletion(jobId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(jobId, sheetsId, deletionTime, "job")
        addDeletedItem(KEY_DELETED_JOBS, deletedItem)
    }
    
    suspend fun trackJobTypeDeletion(jobTypeId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(jobTypeId, sheetsId, deletionTime, "job_type")
        addDeletedItem(KEY_DELETED_JOB_TYPES, deletedItem)
    }
    
    suspend fun trackVenueDeletion(venueId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(venueId, sheetsId, deletionTime, "venue")
        addDeletedItem(KEY_DELETED_VENUES, deletedItem)
    }
    
    private fun addDeletedItem(key: String, deletedItem: DeletedItem) {
        val existingJson = prefs.getString(key, "[]") ?: "[]"
        val jsonArray = JSONArray(existingJson)
        
        val itemJson = JSONObject().apply {
            put("id", deletedItem.id)
            put("sheetsId", deletedItem.sheetsId ?: "")
            put("deletionTime", deletedItem.deletionTime)
            put("type", deletedItem.type)
        }
        
        jsonArray.put(itemJson)
        prefs.edit().putString(key, jsonArray.toString()).apply()
        
        println("Tracked deletion: ${deletedItem.type} with ID ${deletedItem.id}")
    }
    
    suspend fun getDeletedGuests(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_GUESTS)
    }
    
    suspend fun getDeletedVolunteers(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_VOLUNTEERS)
    }
    
    suspend fun getDeletedJobs(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_JOBS)
    }
    
    suspend fun getDeletedJobTypes(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_JOB_TYPES)
    }
    
    suspend fun getDeletedVenues(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_VENUES)
    }
    
    private fun getDeletedItems(key: String): List<DeletedItem> {
        val jsonString = prefs.getString(key, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        val items = mutableListOf<DeletedItem>()
        
        for (i in 0 until jsonArray.length()) {
            val itemJson = jsonArray.getJSONObject(i)
            items.add(
                DeletedItem(
                    id = itemJson.getString("id"),
                    sheetsId = if (itemJson.getString("sheetsId").isEmpty()) null else itemJson.getString("sheetsId"),
                    deletionTime = itemJson.getLong("deletionTime"),
                    type = itemJson.getString("type")
                )
            )
        }
        
        return items
    }
    
    suspend fun isItemDeleted(itemId: String, itemType: String): Boolean = withContext(Dispatchers.IO) {
        val key = when (itemType) {
            "guest" -> KEY_DELETED_GUESTS
            "volunteer" -> KEY_DELETED_VOLUNTEERS
            "job" -> KEY_DELETED_JOBS
            "job_type" -> KEY_DELETED_JOB_TYPES
            "venue" -> KEY_DELETED_VENUES
            else -> return@withContext false
        }
        
        val deletedItems = getDeletedItems(key)
        deletedItems.any { it.id == itemId }
    }
    
    suspend fun isItemDeletedBySheetsId(sheetsId: String, itemType: String): Boolean = withContext(Dispatchers.IO) {
        val key = when (itemType) {
            "guest" -> KEY_DELETED_GUESTS
            "volunteer" -> KEY_DELETED_VOLUNTEERS
            "job" -> KEY_DELETED_JOBS
            "job_type" -> KEY_DELETED_JOB_TYPES
            "venue" -> KEY_DELETED_VENUES
            else -> return@withContext false
        }
        
        val deletedItems = getDeletedItems(key)
        deletedItems.any { it.sheetsId == sheetsId }
    }
    
    suspend fun clearOldDeletions(olderThanDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        
        val keys = listOf(KEY_DELETED_GUESTS, KEY_DELETED_VOLUNTEERS, KEY_DELETED_JOBS, KEY_DELETED_JOB_TYPES, KEY_DELETED_VENUES)
        
        keys.forEach { key ->
            val deletedItems = getDeletedItems(key)
            val recentItems = deletedItems.filter { it.deletionTime > cutoffTime }
            
            val jsonArray = JSONArray()
            recentItems.forEach { item ->
                val itemJson = JSONObject().apply {
                    put("id", item.id)
                    put("sheetsId", item.sheetsId ?: "")
                    put("deletionTime", item.deletionTime)
                    put("type", item.type)
                }
                jsonArray.put(itemJson)
            }
            
            prefs.edit().putString(key, jsonArray.toString()).apply()
        }
        
        println("Cleared old deletion records older than $olderThanDays days")
    }
    
    suspend fun clearAllDeletions() = withContext(Dispatchers.IO) {
        val keys = listOf(KEY_DELETED_GUESTS, KEY_DELETED_VOLUNTEERS, KEY_DELETED_JOBS, KEY_DELETED_JOB_TYPES)
        keys.forEach { key ->
            prefs.edit().putString(key, "[]").apply()
        }
        println("Cleared all deletion records")
    }
}
