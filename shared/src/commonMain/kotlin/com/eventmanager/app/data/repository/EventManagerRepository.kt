package com.eventmanager.app.data.repository

import com.eventmanager.app.data.dao.AccountTransferDao
import com.eventmanager.app.data.dao.GuestDao
import com.eventmanager.app.data.dao.JobDao
import com.eventmanager.app.data.dao.JobTypeConfigDao
import com.eventmanager.app.data.dao.SalesSheetItemDao
import com.eventmanager.app.data.dao.VenueDao
import com.eventmanager.app.data.dao.VolunteerDao
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.NanoIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
    }
}

class EventManagerRepository(
    private val guestDao: GuestDao,
    private val volunteerDao: VolunteerDao,
    private val jobDao: JobDao,
    private val jobTypeConfigDao: JobTypeConfigDao,
    private val venueDao: VenueDao,
    private val salesSheetItemDao: SalesSheetItemDao,
    private val accountTransferDao: AccountTransferDao
) {
    // Guest operations
    fun getAllGuests(): Flow<List<Guest>> = guestDao.getAllGuests()
    suspend fun insertGuest(guest: Guest): Guest {
        // Ensure guest has a valid NanoID before insertion
        val validatedGuest = if (NanoIdGenerator.needsRegeneration(guest.nanoId)) {
            val newId = NanoIdGenerator.ensureValidNanoId(guest.nanoId, guest.name)
            println("⚠️ Repository: Generated NanoID for guest '${guest.name}': '$newId'")
            guest.copy(nanoId = newId)
        } else {
            guest
        }
        // Uniqueness is by NanoID (multiple guests may share the same display name)
        val existingByNanoId = guestDao.getGuestByNanoId(validatedGuest.nanoId)
        if (existingByNanoId != null) {
            throw IllegalArgumentException("A guest with NanoID '${validatedGuest.nanoId}' already exists")
        }
        val rowId = guestDao.insertGuest(validatedGuest)
        return validatedGuest.copy(id = rowId)
    }
    
    suspend fun updateGuest(guest: Guest) {
        // Ensure guest has a valid NanoID before update
        val validatedGuest = if (NanoIdGenerator.needsRegeneration(guest.nanoId)) {
            val newId = NanoIdGenerator.ensureValidNanoId(guest.nanoId, guest.name)
            println("⚠️ Repository: Generated NanoID for guest '${guest.name}': '$newId'")
            guest.copy(nanoId = newId)
        } else {
            guest
        }
        val existingByNanoId = guestDao.getGuestByNanoId(validatedGuest.nanoId)
        if (existingByNanoId != null && existingByNanoId.id != validatedGuest.id) {
            throw IllegalArgumentException("A guest with NanoID '${validatedGuest.nanoId}' already exists")
        }
        guestDao.updateGuest(validatedGuest)
    }
    
    suspend fun deleteGuest(guest: Guest) = guestDao.deleteGuest(guest)

    // Batch guest operations for optimized sync
    suspend fun insertGuestsAll(guests: List<Guest>): List<Long> = guestDao.insertGuestsAll(guests)
    suspend fun updateGuestsAll(guests: List<Guest>) = guestDao.updateGuestsAll(guests)
    suspend fun deleteGuestsAll(guests: List<Guest>) = guestDao.deleteGuestsAll(guests)

    suspend fun replaceTemporaryGuests(guests: List<Guest>) {
        guestDao.deleteTemporaryGuests()
        if (guests.isNotEmpty()) {
            guestDao.insertGuestsAll(guests)
        }
    }

    // Volunteer-benefit guest helpers
    suspend fun getVolunteerBenefitGuests(): List<Guest> = guestDao.getVolunteerBenefitGuests()

    suspend fun getGuestByNanoId(nanoId: String): Guest? = guestDao.getGuestByNanoId(nanoId)

    suspend fun getGuestByNanoIdAndOrg(nanoId: String, orgId: String): Guest? =
        guestDao.getGuestByNanoIdAndOrg(nanoId, orgId)

    suspend fun getVenueByNameAndOrg(name: String, orgId: String): VenueEntity? =
        venueDao.getVenueByNameAndOrg(name, orgId)

    suspend fun getJobTypeConfigByNameAndOrg(name: String, orgId: String): JobTypeConfig? =
        jobTypeConfigDao.getJobTypeConfigByNameAndOrg(name, orgId)

    suspend fun getSalesSheetItemByNameAndOrg(name: String, orgId: String): SalesSheetItem? =
        salesSheetItemDao.getSalesSheetItemByNameAndOrg(name, orgId)

    suspend fun deleteAllDataForOrg(orgId: String) {
        if (orgId.isBlank()) return
        guestDao.deleteAllForOrg(orgId)
        volunteerDao.deleteAllForOrg(orgId)
        jobDao.deleteAllForOrg(orgId)
        jobTypeConfigDao.deleteAllForOrg(orgId)
        venueDao.deleteAllForOrg(orgId)
        salesSheetItemDao.deleteAllForOrg(orgId)
        accountTransferDao.deleteAllForOrg(orgId)
    }

    suspend fun deleteAllDataNotInOrgs(orgIds: List<String>) {
        if (orgIds.isEmpty()) return
        guestDao.deleteAllNotInOrgs(orgIds)
        volunteerDao.deleteAllNotInOrgs(orgIds)
        jobDao.deleteAllNotInOrgs(orgIds)
        jobTypeConfigDao.deleteAllNotInOrgs(orgIds)
        venueDao.deleteAllNotInOrgs(orgIds)
        salesSheetItemDao.deleteAllNotInOrgs(orgIds)
        accountTransferDao.deleteAllNotInOrgs(orgIds)
    }

    suspend fun backfillEmptyOrgIds(activeOrgId: String) {
        if (activeOrgId.isBlank()) return
        guestDao.getAllGuests().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { guestDao.updateGuest(it.copy(firebaseOrgId = activeOrgId)) }
        volunteerDao.getAllVolunteers().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { volunteerDao.updateVolunteer(it.copy(firebaseOrgId = activeOrgId)) }
        jobDao.getAllJobs().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { jobDao.updateJob(it.copy(firebaseOrgId = activeOrgId)) }
        jobTypeConfigDao.getAllJobTypeConfigs().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { jobTypeConfigDao.updateJobTypeConfig(it.copy(firebaseOrgId = activeOrgId)) }
        venueDao.getAllVenues().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { venueDao.updateVenue(it.copy(firebaseOrgId = activeOrgId)) }
        salesSheetItemDao.getAllSalesSheetItems().first()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { salesSheetItemDao.updateSalesSheetItem(it.copy(firebaseOrgId = activeOrgId)) }
        accountTransferDao.getAllAccountTransfersOnce()
            .filter { it.firebaseOrgId.isBlank() }
            .forEach { accountTransferDao.updateAccountTransfer(it.copy(firebaseOrgId = activeOrgId)) }
    }

    // Volunteer operations
    fun getAllActiveVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllActiveVolunteers()
    fun getAllVolunteers(): Flow<List<Volunteer>> = volunteerDao.getAllVolunteers()
    suspend fun getVolunteerById(id: String): Volunteer? = volunteerDao.getVolunteerById(id)
    fun getVolunteersByRank(rank: VolunteerRank): Flow<List<Volunteer>> = volunteerDao.getVolunteersByRank(rank)
    suspend fun insertVolunteer(volunteer: Volunteer): Volunteer {
        // Validate and fix invalid NanoID before insertion
        // This is the data access boundary - validation is essential here
        val validatedVolunteer = if (NanoIdGenerator.needsRegeneration(volunteer.id)) {
            val newId = NanoIdGenerator.ensureValidNanoId(volunteer.id, volunteer.name)
            println("⚠️ Repository: Fixed invalid NanoID for volunteer '${volunteer.name}': '${volunteer.id}' → '$newId'")
            volunteer.copy(id = newId)
        } else {
            volunteer
        }
        
        // Find existing volunteer - prioritize NanoID, then exact name+abbreviation.
        // sheetsId (row number) is not stable after full-sheet rewrites, so use it only
        // as a final fallback for legacy records.
        val existingVolunteer = volunteerDao.getVolunteerById(validatedVolunteer.id)
            ?: volunteerDao.getVolunteerByNameAndAbbreviation(
                validatedVolunteer.name,
                validatedVolunteer.lastNameAbbreviation
            )
            ?: validatedVolunteer.sheetsId?.let { volunteerDao.getVolunteerBySheetsId(it) }
        
        if (existingVolunteer != null) {
            if (existingVolunteer.id != validatedVolunteer.id) {
                // NanoID differs - Google Sheets has a different (but valid) NanoID
                // We need to: 1) Delete old record, 2) Insert new record, 3) Update jobs
                println("🔄 Volunteer '${validatedVolunteer.name} ${validatedVolunteer.lastNameAbbreviation}' NanoID changed: '${existingVolunteer.id}' → '${validatedVolunteer.id}' (adopting Google Sheets ID)")
                
                // Update all jobs that reference the old volunteer ID to use the new one
                jobDao.updateJobsVolunteerId(existingVolunteer.id, validatedVolunteer.id)
                println("   ✅ Updated jobs to reference new NanoID")
                
                // Delete the old volunteer record
                volunteerDao.deleteVolunteer(existingVolunteer)
                
                // Insert the new volunteer with the correct NanoID from Sheets
                val updated = validatedVolunteer.copy(
                    sheetsId = validatedVolunteer.sheetsId ?: existingVolunteer.sheetsId
                )
                volunteerDao.insertVolunteer(updated)
                println("   ✅ Volunteer record updated with Google Sheets NanoID")
                return updated
            } else {
                // Same NanoID - just update the data
                val updated = validatedVolunteer.copy(
                    sheetsId = validatedVolunteer.sheetsId ?: existingVolunteer.sheetsId
                )
                volunteerDao.updateVolunteer(updated)
                return updated
            }
        } else {
            volunteerDao.insertVolunteer(validatedVolunteer)
            return validatedVolunteer
        }
    }
    
    suspend fun updateVolunteer(volunteer: Volunteer) {
        // Validate and fix invalid NanoID before update
        val validatedVolunteer = if (NanoIdGenerator.needsRegeneration(volunteer.id)) {
            val newId = NanoIdGenerator.ensureValidNanoId(volunteer.id, volunteer.name)
            println("⚠️ Repository: Fixed invalid NanoID for volunteer '${volunteer.name}': '${volunteer.id}' → '$newId'")
            volunteer.copy(id = newId)
        } else {
            volunteer
        }
        
        // Find existing volunteer - prioritize NanoID, then exact name+abbreviation.
        // Keep sheetsId only as last fallback for legacy rows.
        val existingVolunteer = volunteerDao.getVolunteerById(validatedVolunteer.id)
            ?: volunteerDao.getVolunteerByNameAndAbbreviation(
                validatedVolunteer.name,
                validatedVolunteer.lastNameAbbreviation
            )
            ?: validatedVolunteer.sheetsId?.let { volunteerDao.getVolunteerBySheetsId(it) }
        
        if (existingVolunteer != null && existingVolunteer.id != validatedVolunteer.id) {
            // NanoID differs - Google Sheets has the correct NanoID
            // We need to: 1) Update jobs, 2) Delete old record, 3) Insert new record
            println("🔄 Repository: Volunteer '${validatedVolunteer.name} ${validatedVolunteer.lastNameAbbreviation}' NanoID changed: '${existingVolunteer.id}' → '${validatedVolunteer.id}' (adopting Google Sheets ID)")
            
            // Update all jobs that reference the old volunteer ID to use the new one
            jobDao.updateJobsVolunteerId(existingVolunteer.id, validatedVolunteer.id)
            println("   ✅ Updated jobs to reference new NanoID")
            
            // Delete the old volunteer record
            volunteerDao.deleteVolunteer(existingVolunteer)
            
            // Insert the new volunteer with the correct NanoID from Sheets
            volunteerDao.insertVolunteer(validatedVolunteer)
            println("   ✅ Volunteer record updated with Google Sheets NanoID")
        } else {
            volunteerDao.updateVolunteer(validatedVolunteer)
        }
    }
    
    suspend fun deleteVolunteer(volunteer: Volunteer) = volunteerDao.deleteVolunteer(volunteer)
    suspend fun updateVolunteerStatus(id: String, isActive: Boolean) = volunteerDao.updateVolunteerStatus(id, isActive)

    // Batch volunteer operations for optimized sync
    suspend fun insertVolunteersAll(volunteers: List<Volunteer>) {
        // Validate and fix all invalid NanoIDs before batch insertion
        val validatedVolunteers = volunteers.map { volunteer ->
            if (NanoIdGenerator.needsRegeneration(volunteer.id)) {
                val newId = NanoIdGenerator.ensureValidNanoId(volunteer.id, volunteer.name)
                volunteer.copy(id = newId)
            } else {
                volunteer
            }
        }
        volunteerDao.insertVolunteersAll(validatedVolunteers)
    }
    
    suspend fun updateVolunteersAll(volunteers: List<Volunteer>) {
        // Process each volunteer individually to handle NanoID changes properly
        // Google Sheets is source of truth for NanoIDs
        for (volunteer in volunteers) {
            // Validate and fix invalid NanoID
            val validatedVolunteer = if (NanoIdGenerator.needsRegeneration(volunteer.id)) {
                val newId = NanoIdGenerator.ensureValidNanoId(volunteer.id, volunteer.name)
                volunteer.copy(id = newId)
            } else {
                volunteer
            }
            
            // Find existing volunteer - prioritize NanoID, then exact name+abbreviation.
            // Keep sheetsId only as last fallback for legacy rows.
            val existingVolunteer = volunteerDao.getVolunteerById(validatedVolunteer.id)
                ?: volunteerDao.getVolunteerByNameAndAbbreviation(
                    validatedVolunteer.name,
                    validatedVolunteer.lastNameAbbreviation
                )
                ?: validatedVolunteer.sheetsId?.let { volunteerDao.getVolunteerBySheetsId(it) }
            
            if (existingVolunteer != null && existingVolunteer.id != validatedVolunteer.id) {
                // NanoID differs - Google Sheets has the correct NanoID
                println("🔄 Repository (batch): Volunteer '${validatedVolunteer.name} ${validatedVolunteer.lastNameAbbreviation}' NanoID changed: '${existingVolunteer.id}' → '${validatedVolunteer.id}'")
                jobDao.updateJobsVolunteerId(existingVolunteer.id, validatedVolunteer.id)
                volunteerDao.deleteVolunteer(existingVolunteer)
                volunteerDao.insertVolunteer(validatedVolunteer)
            } else {
                volunteerDao.updateVolunteer(validatedVolunteer)
            }
        }
    }
    suspend fun deleteVolunteersAll(volunteers: List<Volunteer>) = volunteerDao.deleteVolunteersAll(volunteers)

    // Job operations
    fun getAllJobs(): Flow<List<Job>> = jobDao.getAllJobs()
    suspend fun getJobById(id: Long): Job? = jobDao.getJobById(id)
    suspend fun getJobByJobNanoId(jobNanoId: String): Job? =
        jobNanoId.takeIf { it.isNotBlank() }?.let { jobDao.getJobByJobNanoId(it) }
    suspend fun insertJob(job: Job): Long = jobDao.insertJob(job)
    suspend fun updateJob(job: Job) = jobDao.updateJob(job)
    suspend fun deleteJob(job: Job) = jobDao.deleteJob(job)
    
    /**
     * Updates all jobs referencing an old volunteer ID to use a new volunteer ID.
     * Used when a volunteer's NanoID changes during sync (Google Sheets is source of truth).
     */
    suspend fun updateJobsVolunteerId(oldVolunteerId: String, newVolunteerId: String) {
        jobDao.updateJobsVolunteerId(oldVolunteerId, newVolunteerId)
    }

    // Batch job operations for optimized sync
    suspend fun insertJobsAll(jobs: List<Job>): List<Long> = jobDao.insertJobsAll(jobs)
    suspend fun updateJobsAll(jobs: List<Job>) = jobDao.updateJobsAll(jobs)
    suspend fun deleteJobsAll(jobs: List<Job>) = jobDao.deleteJobsAll(jobs)

    // Job Type Config operations
    fun getAllJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllJobTypeConfigs()
    fun getAllActiveJobTypeConfigs(): Flow<List<JobTypeConfig>> = jobTypeConfigDao.getAllActiveJobTypeConfigs()
    suspend fun insertJobTypeConfig(config: JobTypeConfig): Long = jobTypeConfigDao.insertJobTypeConfig(config)
    suspend fun updateJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.updateJobTypeConfig(config)
    suspend fun deleteJobTypeConfig(config: JobTypeConfig) = jobTypeConfigDao.deleteJobTypeConfig(config)

    // Batch job type config operations for optimized sync
    suspend fun insertJobTypeConfigsAll(configs: List<JobTypeConfig>): List<Long> = jobTypeConfigDao.insertJobTypeConfigsAll(configs)
    suspend fun updateJobTypeConfigsAll(configs: List<JobTypeConfig>) = jobTypeConfigDao.updateJobTypeConfigsAll(configs)
    suspend fun deleteJobTypeConfigsAll(configs: List<JobTypeConfig>) = jobTypeConfigDao.deleteJobTypeConfigsAll(configs)

    // Venue operations
    fun getAllVenues(): Flow<List<VenueEntity>> = venueDao.getAllVenues()
    suspend fun getVenueById(id: Long): VenueEntity? = venueDao.getVenueById(id)
    suspend fun insertVenue(venue: VenueEntity): Long = venueDao.insertVenue(venue)
    suspend fun updateVenue(venue: VenueEntity) = venueDao.updateVenue(venue)
    suspend fun deleteVenue(venue: VenueEntity) = venueDao.deleteVenue(venue)
    suspend fun updateVenueStatus(id: Long, isActive: Boolean) = venueDao.updateVenueStatus(id, isActive)
    suspend fun clearAllVenues() = venueDao.deleteAllVenues()

    // Batch venue operations for optimized sync
    suspend fun insertVenuesAll(venues: List<VenueEntity>): List<Long> = venueDao.insertVenuesAll(venues)
    suspend fun updateVenuesAll(venues: List<VenueEntity>) = venueDao.updateVenuesAll(venues)
    suspend fun deleteVenuesAll(venues: List<VenueEntity>) = venueDao.deleteVenuesAll(venues)

    // Sales sheet item operations
    fun getAllSalesSheetItems(): Flow<List<SalesSheetItem>> = salesSheetItemDao.getAllSalesSheetItems()
    suspend fun getSalesSheetItemById(id: Long): SalesSheetItem? = salesSheetItemDao.getSalesSheetItemById(id)
    suspend fun insertSalesSheetItem(item: SalesSheetItem): Long = salesSheetItemDao.insertSalesSheetItem(item)
    suspend fun updateSalesSheetItem(item: SalesSheetItem) = salesSheetItemDao.updateSalesSheetItem(item)
    suspend fun deleteSalesSheetItem(item: SalesSheetItem) = salesSheetItemDao.deleteSalesSheetItem(item)
    suspend fun updateSalesSheetItemStatus(id: Long, isActive: Boolean) = salesSheetItemDao.updateSalesSheetItemStatus(id, isActive)
    suspend fun clearAllSalesSheetItems() = salesSheetItemDao.deleteAllSalesSheetItems()

    // Batch sales sheet item operations for optimized sync
    suspend fun insertSalesSheetItemsAll(items: List<SalesSheetItem>): List<Long> = salesSheetItemDao.insertSalesSheetItemsAll(items)
    suspend fun updateSalesSheetItemsAll(items: List<SalesSheetItem>) = salesSheetItemDao.updateSalesSheetItemsAll(items)
    suspend fun deleteSalesSheetItemsAll(items: List<SalesSheetItem>) = salesSheetItemDao.deleteSalesSheetItemsAll(items)

    // Account transfer operations
    fun getAllAccountTransfers(): Flow<List<AccountTransfer>> = accountTransferDao.getAllAccountTransfers()
    suspend fun getAllAccountTransfersOnce(): List<AccountTransfer> = accountTransferDao.getAllAccountTransfersOnce()
    suspend fun getTransfersForHolder(holderType: AccountHolderType, holderId: String): List<AccountTransfer> =
        accountTransferDao.getTransfersForHolder(holderType, holderId)
    suspend fun getRecentTransfersForHolder(holderType: AccountHolderType, holderId: String, limit: Int): List<AccountTransfer> =
        accountTransferDao.getRecentTransfersForHolder(holderType, holderId, limit)
    suspend fun getAccountTransferBySourceReference(sourceReference: String): AccountTransfer? =
        accountTransferDao.getBySourceReference(sourceReference)
    suspend fun getTransfersBetween(startMs: Long, endMs: Long): List<AccountTransfer> =
        accountTransferDao.getTransfersBetween(startMs, endMs)
    suspend fun insertAccountTransfer(transfer: AccountTransfer): Long = accountTransferDao.insertAccountTransfer(transfer)
    suspend fun updateAccountTransfer(transfer: AccountTransfer) = accountTransferDao.updateAccountTransfer(transfer)
    suspend fun deleteAccountTransfer(transfer: AccountTransfer) = accountTransferDao.deleteAccountTransfer(transfer)
    suspend fun insertAccountTransfersAll(transfers: List<AccountTransfer>): List<Long> =
        accountTransferDao.insertAccountTransfersAll(transfers)
    suspend fun updateAccountTransfersAll(transfers: List<AccountTransfer>) =
        accountTransferDao.updateAccountTransfersAll(transfers)
    suspend fun deleteAccountTransfersAll(transfers: List<AccountTransfer>) =
        accountTransferDao.deleteAccountTransfersAll(transfers)
    suspend fun clearAllAccountTransfers() = accountTransferDao.deleteAllAccountTransfers()

    fun getJobsByVolunteer(volunteerId: String): Flow<List<Job>> = jobDao.getJobsByVolunteer(volunteerId)

    // Get volunteer benefit status with time-based calculations
    suspend fun getVolunteerBenefitStatus(volunteerId: String, offsetHours: Int = 0): VolunteerBenefitStatus? {
        val volunteer = getVolunteerById(volunteerId) ?: return null
        val jobs = getAllJobs().first()
        val jobTypeConfigs = getAllActiveJobTypeConfigs().first()
        return BenefitCalculator.calculateVolunteerBenefitStatus(
            volunteer, jobs, jobTypeConfigs, offsetHours = offsetHours
        )
    }
    
    // Get all volunteers with their current benefit status
    suspend fun getAllVolunteerBenefitStatuses(offsetHours: Int = 0): List<VolunteerBenefitStatus> {
        val volunteers = getAllVolunteers().first() // Include both active and inactive volunteers
        val jobs = getAllJobs().first()
        val jobTypeConfigs = getAllActiveJobTypeConfigs().first()
        
        // OPTIMIZED: Use pre-computed calculation context to avoid repeated filtering
        // This creates lookups and date ranges once, then reuses them for all volunteers
        val ctx = BenefitCalculator.CalculationContext(jobTypeConfigs, offsetHours = offsetHours)
        
        // OPTIMIZED: group jobs once to avoid O(volunteers * jobs) filtering
        // Uses String (NanoID) as the key type
        val jobsByVolunteerId: Map<String, List<Job>> = jobs.groupBy { it.volunteerId }
        
        return volunteers.map { volunteer ->
            val volunteerJobs = jobsByVolunteerId[volunteer.id] ?: emptyList()
            BenefitCalculator.calculateWithContext(
                volunteer = volunteer,
                volunteerJobs = volunteerJobs,
                ctx = ctx
            )
        }
    }
    
    // Clear all data (for clean sync)
    suspend fun clearAllData() {
        guestDao.deleteAllGuests()
        volunteerDao.deleteAllVolunteers()
        jobDao.deleteAllJobs()
        jobTypeConfigDao.deleteAllJobTypeConfigs()
        venueDao.deleteAllVenues()
        salesSheetItemDao.deleteAllSalesSheetItems()
        accountTransferDao.deleteAllAccountTransfers()
    }
    
    suspend fun clearAllGuests() {
        guestDao.deleteAllGuests()
    }
    
    suspend fun clearAllVolunteers() {
        volunteerDao.deleteAllVolunteers()
    }
    
    suspend fun clearAllJobs() {
        jobDao.deleteAllJobs()
    }
    
    suspend fun clearAllJobTypeConfigs() {
        jobTypeConfigDao.deleteAllJobTypeConfigs()
    }

}

