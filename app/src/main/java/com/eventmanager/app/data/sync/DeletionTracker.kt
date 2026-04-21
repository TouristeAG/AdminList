package com.eventmanager.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks deleted items to prevent them from being re-downloaded during sync
 * and to ensure they are properly deleted from Google Sheets.
 *
 * [businessKey] stores the same key used by the merge logic (e.g. nanoId for guests,
 * volunteer NanoID, name for job types / venues, composite key for jobs) so that the
 * merge-before-upload step can recognise locally-deleted items on the sheet and skip them.
 */
class DeletionTracker(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("deletion_tracker", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DELETED_GUESTS = "deleted_guests"
        private const val KEY_DELETED_VOLUNTEERS = "deleted_volunteers"
        private const val KEY_DELETED_JOBS = "deleted_jobs"
        private const val KEY_DELETED_JOB_TYPES = "deleted_job_types"
        private const val KEY_DELETED_VENUES = "deleted_venues"
        private const val KEY_DELETED_SALES_ITEMS = "deleted_sales_items"
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
    
    data class DeletedItem(
        val id: String,
        val sheetsId: String?,
        val deletionTime: Long,
        val type: String,
        val businessKey: String? = null
    )
    
    suspend fun trackGuestDeletion(guestId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(guestId, sheetsId, deletionTime, "guest", businessKey)
        addDeletedItem(KEY_DELETED_GUESTS, deletedItem)
    }
    
    suspend fun trackVolunteerDeletion(volunteerId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(volunteerId, sheetsId, deletionTime, "volunteer", businessKey ?: volunteerId)
        addDeletedItem(KEY_DELETED_VOLUNTEERS, deletedItem)
    }
    
    suspend fun trackJobDeletion(jobId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(jobId, sheetsId, deletionTime, "job", businessKey)
        addDeletedItem(KEY_DELETED_JOBS, deletedItem)
    }
    
    suspend fun trackJobTypeDeletion(jobTypeId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(jobTypeId, sheetsId, deletionTime, "job_type", businessKey)
        addDeletedItem(KEY_DELETED_JOB_TYPES, deletedItem)
    }
    
    suspend fun trackVenueDeletion(venueId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(venueId, sheetsId, deletionTime, "venue", businessKey)
        addDeletedItem(KEY_DELETED_VENUES, deletedItem)
    }

    suspend fun trackSalesSheetItemDeletion(itemId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(itemId, sheetsId, deletionTime, "sales_item", businessKey)
        addDeletedItem(KEY_DELETED_SALES_ITEMS, deletedItem)
    }
    
    private fun addDeletedItem(key: String, deletedItem: DeletedItem) {
        val existingJson = prefs.getString(key, "[]") ?: "[]"
        val jsonArray = JSONArray(existingJson)
        
        val itemJson = JSONObject().apply {
            put("id", deletedItem.id)
            put("sheetsId", deletedItem.sheetsId ?: "")
            put("deletionTime", deletedItem.deletionTime)
            put("type", deletedItem.type)
            put("businessKey", deletedItem.businessKey ?: "")
        }
        
        jsonArray.put(itemJson)

        // Prune entries older than MAX_AGE_MS to prevent unbounded growth
        val now = System.currentTimeMillis()
        val pruned = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (now - obj.getLong("deletionTime") < MAX_AGE_MS) {
                pruned.put(obj)
            }
        }

        prefs.edit().putString(key, pruned.toString()).apply()
        
        println("Tracked deletion: ${deletedItem.type} with ID ${deletedItem.id} (businessKey=${deletedItem.businessKey})")
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

    suspend fun getDeletedSalesSheetItems(): List<DeletedItem> = withContext(Dispatchers.IO) {
        getDeletedItems(KEY_DELETED_SALES_ITEMS)
    }

    /**
     * Returns the set of business keys for all tracked deletions of a given entity type.
     * Used by the merge-before-upload logic to exclude locally-deleted items.
     */
    suspend fun getDeletedBusinessKeys(entityType: String): Set<String> = withContext(Dispatchers.IO) {
        val key = when (entityType) {
            "guest" -> KEY_DELETED_GUESTS
            "volunteer" -> KEY_DELETED_VOLUNTEERS
            "job" -> KEY_DELETED_JOBS
            "job_type" -> KEY_DELETED_JOB_TYPES
            "venue" -> KEY_DELETED_VENUES
            "sales_item" -> KEY_DELETED_SALES_ITEMS
            else -> return@withContext emptySet()
        }
        getDeletedItems(key).mapNotNull { it.businessKey?.takeIf(String::isNotEmpty) }.toSet()
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
                    type = itemJson.getString("type"),
                    businessKey = itemJson.optString("businessKey", "").takeIf { it.isNotEmpty() }
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
            "sales_item" -> KEY_DELETED_SALES_ITEMS
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
            "sales_item" -> KEY_DELETED_SALES_ITEMS
            else -> return@withContext false
        }
        
        val deletedItems = getDeletedItems(key)
        deletedItems.any { it.sheetsId == sheetsId }
    }
    
}
