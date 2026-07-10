package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.AppStorage
import com.eventmanager.app.platform.createAppStorage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Tracks deleted items to prevent them from being re-downloaded during sync
 * and to ensure they are properly deleted from Google Sheets.
 *
 * [businessKey] stores the same key used by the merge logic (e.g. nanoId for guests,
 * volunteer NanoID, name for job types / venues, composite key for jobs) so that the
 * merge-before-upload step can recognise locally-deleted items on the sheet and skip them.
 */
class DeletionTracker(platformContext: PlatformContext) {
    private val storage: AppStorage = createAppStorage(platformContext)
    private val gson = Gson()
    
    companion object {
        private const val KEY_DELETED_GUESTS = "deleted_guests"
        private const val KEY_DELETED_VOLUNTEERS = "deleted_volunteers"
        private const val KEY_DELETED_JOBS = "deleted_jobs"
        private const val KEY_DELETED_JOB_TYPES = "deleted_job_types"
        private const val KEY_DELETED_VENUES = "deleted_venues"
        private const val KEY_DELETED_SALES_ITEMS = "deleted_sales_items"
        private const val KEY_DELETED_TRANSFERS = "deleted_transfers"
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

    suspend fun trackTransferDeletion(transferId: String, sheetsId: String?, deletionTime: Long = System.currentTimeMillis(), businessKey: String? = null) = withContext(Dispatchers.IO) {
        val deletedItem = DeletedItem(transferId, sheetsId, deletionTime, "transfer", businessKey ?: transferId)
        addDeletedItem(KEY_DELETED_TRANSFERS, deletedItem)
    }
    
    private fun addDeletedItem(key: String, deletedItem: DeletedItem) {
        val existing = getDeletedItems(key).toMutableList()
        existing.add(deletedItem)
        val now = System.currentTimeMillis()
        val pruned = existing.filter { now - it.deletionTime < MAX_AGE_MS }
        storage.putString(key, gson.toJson(pruned))
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
            "transfer" -> KEY_DELETED_TRANSFERS
            else -> return@withContext emptySet()
        }
        getDeletedItems(key).mapNotNull { it.businessKey?.takeIf(String::isNotEmpty) }.toSet()
    }
    
    private fun getDeletedItems(key: String): List<DeletedItem> {
        val jsonString = storage.getString(key, "[]")
        if (jsonString.isBlank() || jsonString == "[]") return emptyList()
        return runCatching {
            val type = object : TypeToken<List<DeletedItem>>() {}.type
            gson.fromJson<List<DeletedItem>>(jsonString, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }
    
    suspend fun isItemDeleted(itemId: String, itemType: String): Boolean = withContext(Dispatchers.IO) {
        val key = when (itemType) {
            "guest" -> KEY_DELETED_GUESTS
            "volunteer" -> KEY_DELETED_VOLUNTEERS
            "job" -> KEY_DELETED_JOBS
            "job_type" -> KEY_DELETED_JOB_TYPES
            "venue" -> KEY_DELETED_VENUES
            "sales_item" -> KEY_DELETED_SALES_ITEMS
            "transfer" -> KEY_DELETED_TRANSFERS
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
            "transfer" -> KEY_DELETED_TRANSFERS
            else -> return@withContext false
        }
        
        val deletedItems = getDeletedItems(key)
        deletedItems.any { it.sheetsId == sheetsId }
    }
    
}
