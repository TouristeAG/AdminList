package com.eventmanager.app.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.parcelize.Parcelize
import java.util.Date

@Entity(
    tableName = "guests",
    indices = [
        Index(value = ["sheetsId"]),
        Index(value = ["volunteerId"]),
        Index(value = ["venueName"]),
        Index(value = ["lastModified"]),
        Index(value = ["isVolunteerBenefit"])
    ]
)
@Parcelize
data class Guest(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null, // Google Sheets row ID for syncing
    val name: String,
    val lastNameAbbreviation: String = "", // Last name abbreviation for volunteer guests
    val invitations: Int,
    val venueName: String, // Store actual venue name for unlimited venue support
    val notes: String = "",
    val isVolunteerBenefit: Boolean = false,
    val volunteerId: Long? = null, // ID of the volunteer this guest entry represents (for volunteer benefits)
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

@Entity(
    tableName = "volunteers",
    indices = [
        Index(value = ["sheetsId"]),
        Index(value = ["isActive"]),
        Index(value = ["currentRank"]),
        Index(value = ["lastModified"])
    ]
)
@Parcelize
data class Volunteer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null, // Google Sheets row ID for syncing
    val name: String,
    val lastNameAbbreviation: String,
    val email: String,
    val phoneNumber: String,
    val dateOfBirth: String = "", // Store as string for simplicity
    val gender: Gender? = null, // Gender field with nullable default
    val currentRank: VolunteerRank? = null, // No default rank - must be earned
    val isActive: Boolean = true,
    val lastShiftDate: Long? = null, // Timestamp of last shift
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

@Entity(
    tableName = "jobs",
    indices = [
        Index(value = ["volunteerId"]),
        Index(value = ["date"]),
        Index(value = ["venueName"]),
        Index(value = ["jobTypeName"]),
        Index(value = ["sheetsId"]),
        Index(value = ["lastModified"]),
        Index(value = ["volunteerId", "date"]),
        Index(value = ["date", "shiftTime"])
    ]
)
@Parcelize
data class Job(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null, // Google Sheets row ID for syncing
    val volunteerId: Long,
    val jobType: JobType,
    val jobTypeName: String, // Store the actual job type name for personalized types
    val venueName: String, // Store actual venue name for unlimited venue support
    val date: Long, // Store as timestamp
    val shiftTime: ShiftTime,
    val notes: String = "",
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class Benefit(
    val rank: VolunteerRank?,
    val description: String,
    val freeEntry: Boolean = false,
    val friendInvitation: Boolean = false,
    val inviteCount: Int = 0, // Number of invites (for manual rewards)
    val drinkTokens: Int = 0,
    val barDiscount: Int = 0,
    val guestListAccess: Boolean = false,
    val extraordinaryBenefits: Boolean = false,
    val validUntil: Long? = null, // Timestamp when benefits expire
    val isActive: Boolean = true
) : Parcelable

@Parcelize
data class VolunteerBenefitStatus(
    val volunteerId: Long,
    val rank: VolunteerRank?,
    val benefits: Benefit,
    val activeBenefits: List<Benefit> = emptyList(), // All active benefits from all applicable ranks
    val lastJobDate: Long? = null,
    val monthlyShifts: Int = 0,
    val isEligibleForGalaxie: Boolean = false,
    val isEligibleForEtoile: Boolean = false,
    val isEligibleForNova: Boolean = false
) : Parcelable

enum class Venue {
    GROOVE,
    LE_TERREAU,
    BOTH
}

enum class VolunteerRank {
    NOVA,       // Shift before midnight
    ETOILE,     // Shift after midnight  
    GALAXIE,    // 3+ shifts/month
    ORION,      // Committee roles
    VETERAN,    // Ex-Orion
    SPECIAL     // Manual rewards
}

enum class JobType {
    BAR,
    SECURITY,
    CLEANING,
    SETUP,
    SOUND_TECH,
    LIGHTING,
    ENTRANCE,
    CLOAKROOM,
    COORDINATION,
    COMMITTEE,
    COMMISSION_PRESIDENCY,
    MEETING,
    OTHER
}

enum class Gender {
    FEMALE,
    MALE,
    NON_BINARY,
    OTHER,
    PREFER_NOT_TO_DISCLOSE
}

@Entity(
    tableName = "job_type_configs",
    indices = [
        Index(value = ["sheetsId"]),
        Index(value = ["name"], unique = true),
        Index(value = ["isActive"]),
        Index(value = ["lastModified"])
    ]
)
@Parcelize
data class JobTypeConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null, // Google Sheets row ID for syncing
    val name: String,
    val isActive: Boolean = true,
    val isShiftJob: Boolean = true, // If true, counts for Nova/Etoile/Galaxie
    val isOrionJob: Boolean = false, // If true, counts for Orion rank
    val requiresShiftTime: Boolean = true, // If true, needs before/after midnight distinction
    val benefitSystemType: BenefitSystemType = BenefitSystemType.STELLAR, // Type of benefit system
    val manualRewards: ManualRewards? = null, // Manual rewards configuration (only used if benefitSystemType is MANUAL)
    val description: String = "",
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

@Entity(
    tableName = "venues",
    indices = [
        Index(value = ["sheetsId"]),
        Index(value = ["name"], unique = true),
        Index(value = ["isActive"]),
        Index(value = ["lastModified"])
    ]
)
@Parcelize
data class VenueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null, // Google Sheets row ID for syncing
    val name: String,
    val description: String = "",
    val isActive: Boolean = true,
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

@Entity(tableName = "people_counter")
@Parcelize
data class CounterData(
    @PrimaryKey
    val id: Long = 1, // Always use the same ID for single counter
    val count: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
) : Parcelable

enum class ShiftTime {
    BEFORE_MIDNIGHT,
    AFTER_MIDNIGHT
}

enum class BenefitSystemType {
    STELLAR,    // Uses the existing stellar benefits system
    MANUAL      // Uses manual rewards configuration
}

@Parcelize
data class ManualRewards(
    val durationDays: Int = 1,           // Duration of benefits in days
    val freeDrinks: Int = 0,             // How many drinks for free
    val barDiscountPercentage: Int = 0,  // Percentage of reduction at the bar
    val freeEntry: Boolean = false,      // Free entry or not
    val invites: Int = 0,                // How many invites
    val otherNotes: String = ""          // Other notes
) : Parcelable

// Type converters for Room database
class Converters {
    @TypeConverter
    fun fromVolunteerRank(rank: VolunteerRank?): String? = rank?.name

    @TypeConverter
    fun toVolunteerRank(rank: String?): VolunteerRank? = rank?.let { VolunteerRank.valueOf(it) }

    @TypeConverter
    fun fromJobType(jobType: JobType): String = jobType.name

    @TypeConverter
    fun toJobType(jobType: String): JobType = JobType.valueOf(jobType)

    @TypeConverter
    fun fromShiftTime(shiftTime: ShiftTime): String = shiftTime.name

    @TypeConverter
    fun toShiftTime(shiftTime: String): ShiftTime = ShiftTime.valueOf(shiftTime)

    @TypeConverter
    fun fromBenefitSystemType(benefitSystemType: BenefitSystemType): String = benefitSystemType.name

    @TypeConverter
    fun toBenefitSystemType(benefitSystemType: String): BenefitSystemType = BenefitSystemType.valueOf(benefitSystemType)

    @TypeConverter
    fun fromGender(gender: Gender?): String? = gender?.name

    @TypeConverter
    fun toGender(gender: String?): Gender? = gender?.let { Gender.valueOf(it) }

    @TypeConverter
    fun fromManualRewards(manualRewards: ManualRewards?): String? {
        return manualRewards?.let {
            "${it.durationDays}|${it.freeDrinks}|${it.barDiscountPercentage}|${it.freeEntry}|${it.invites}|${it.otherNotes}"
        }
    }

    @TypeConverter
    fun toManualRewards(manualRewards: String?): ManualRewards? {
        return manualRewards?.let { data ->
            val parts = data.split("|")
            if (parts.size == 6) {
                ManualRewards(
                    durationDays = parts[0].toIntOrNull() ?: 1,
                    freeDrinks = parts[1].toIntOrNull() ?: 0,
                    barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                    freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                    invites = parts[4].toIntOrNull() ?: 0,
                    otherNotes = parts[5]
                )
            } else null
        }
    }
}

object BenefitCalculator {
    
    fun calculateVolunteerBenefitStatus(
        volunteer: Volunteer, 
        jobs: List<Job>, 
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long = System.currentTimeMillis(),
        offsetHours: Int = 0
    ): VolunteerBenefitStatus {
        val volunteerJobs = jobs.filter { it.volunteerId == volunteer.id }
        val lastJobDate = volunteerJobs.maxOfOrNull { it.date }
        val monthlyShifts = getMonthlyShiftCount(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
        
        // Check for manual rewards first (overrides everything)
        val manualRewardsBenefit = calculateManualRewardsBenefit(volunteerJobs, jobTypeConfigs, currentTime)
        
        if (manualRewardsBenefit != null) {
            // Manual rewards override all stellar benefits
            return VolunteerBenefitStatus(
                volunteerId = volunteer.id,
                rank = VolunteerRank.SPECIAL,
                benefits = manualRewardsBenefit,
                activeBenefits = listOf(manualRewardsBenefit),
                lastJobDate = lastJobDate,
                monthlyShifts = monthlyShifts,
                isEligibleForGalaxie = monthlyShifts >= 3,
                isEligibleForEtoile = hasAfterMidnightShift(volunteerJobs, jobTypeConfigs, currentTime, offsetHours),
                isEligibleForNova = hasBeforeMidnightShift(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            )
        }
        
        // Collect ALL applicable benefits from each qualifying rank
        val allApplicableBenefits = mutableListOf<Benefit>()
        var primaryRank: VolunteerRank? = null
        
        // Check for VETERAN rank
        if (isVolunteerVeteran(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)) {
            val benefit = calculateBenefitsForRank(VolunteerRank.VETERAN, volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                primaryRank = VolunteerRank.VETERAN
            }
        }
        
        // Check for ORION rank
        if (isVolunteerOrion(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)) {
            val benefit = calculateBenefitsForRank(VolunteerRank.ORION, volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank != VolunteerRank.VETERAN) primaryRank = VolunteerRank.ORION
            }
        }
        
        // Check for GALAXIE rank
        if (monthlyShifts >= 3) {
            val benefit = calculateBenefitsForRank(VolunteerRank.GALAXIE, volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank == null) primaryRank = VolunteerRank.GALAXIE
            }
        }
        
        // Check for ETOILE rank
        if (hasAfterMidnightShift(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)) {
            val benefit = calculateBenefitsForRank(VolunteerRank.ETOILE, volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank == null) primaryRank = VolunteerRank.ETOILE
            }
        }
        
        // Check for NOVA rank (lowest priority, but should still be included if applicable)
        if (hasBeforeMidnightShift(volunteerJobs, jobTypeConfigs, currentTime, offsetHours)) {
            val benefit = calculateBenefitsForRank(VolunteerRank.NOVA, volunteerJobs, jobTypeConfigs, currentTime, offsetHours)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank == null) primaryRank = VolunteerRank.NOVA
            }
        }
        
        // Combine all active benefits into one aggregated benefit
        val aggregatedBenefit = if (allApplicableBenefits.isNotEmpty()) {
            aggregateBenefits(allApplicableBenefits)
        } else {
            Benefit(
                rank = null,
                description = "No benefits - no rank earned",
                freeEntry = false,
                friendInvitation = false,
                inviteCount = 0,
                drinkTokens = 0,
                barDiscount = 0,
                guestListAccess = false,
                extraordinaryBenefits = false,
                validUntil = null,
                isActive = false
            )
        }
        
        return VolunteerBenefitStatus(
            volunteerId = volunteer.id,
            rank = primaryRank,
            benefits = aggregatedBenefit,
            activeBenefits = allApplicableBenefits,
            lastJobDate = lastJobDate,
            monthlyShifts = monthlyShifts,
            isEligibleForGalaxie = monthlyShifts >= 3,
            isEligibleForEtoile = hasAfterMidnightShift(volunteerJobs, jobTypeConfigs, currentTime),
            isEligibleForNova = hasBeforeMidnightShift(volunteerJobs, jobTypeConfigs, currentTime)
        )
    }
    
    private fun determineCurrentRank(volunteer: Volunteer, jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long): VolunteerRank? {
        val orionJobTypes = jobTypeConfigs.filter { it.isOrionJob && it.isActive }.map { it.name }
        
        // Find the most recent ORION job to determine ORION start date
        val orionJobs = jobs.filter { job ->
            orionJobTypes.contains(job.jobTypeName)
        }.sortedByDescending { it.date }
        
        if (orionJobs.isNotEmpty()) {
            val mostRecentOrionJob = orionJobs.first()
            val orionStartDate = mostRecentOrionJob.date
            val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
            val twoYearsAfterOrion = orionStartDate + (2L * 365L * 24 * 60 * 60 * 1000)
            
            // Check if currently in ORION period (first year)
            if (currentTime >= orionStartDate && currentTime < oneYearAfterOrion) {
                return VolunteerRank.ORION
            }
            
            // Check if in VETERAN period (second year after ORION)
            if (currentTime >= oneYearAfterOrion && currentTime < twoYearsAfterOrion) {
                return VolunteerRank.VETERAN
            }
        }
        
        // Check for GALAXIE rank (3+ jobs in the last 30 days - shifts or meetings)
        val thirtyDaysAgo = currentTime - (30L * 24 * 60 * 60 * 1000)
        val shiftJobTypes = jobTypeConfigs.filter { it.isShiftJob && it.isActive }.map { it.name }
        val meetingJobTypes = jobTypeConfigs.filter { !it.isShiftJob && it.isActive }.map { it.name }
        val allJobTypes = shiftJobTypes + meetingJobTypes
        
        val monthlyJobs = jobs.count { job ->
            job.date > thirtyDaysAgo && allJobTypes.contains(job.jobTypeName)
        }
        
        if (monthlyJobs >= 3) {
            return VolunteerRank.GALAXIE
        }
        
        // Check for ETOILE rank (after midnight shift in the last 30 days)
        val hasAfterMidnight = jobs.any { job ->
            job.date > thirtyDaysAgo &&
            job.shiftTime == ShiftTime.AFTER_MIDNIGHT &&
            shiftJobTypes.contains(job.jobTypeName)
        }
        
        if (hasAfterMidnight) {
            return VolunteerRank.ETOILE
        }
        
        // Check for NOVA rank (before midnight shift in the last 30 days)
        val hasBeforeMidnight = jobs.any { job ->
            job.date > thirtyDaysAgo &&
            job.shiftTime == ShiftTime.BEFORE_MIDNIGHT &&
            shiftJobTypes.contains(job.jobTypeName)
        }
        
        if (hasBeforeMidnight) {
            return VolunteerRank.NOVA
        }
        
        // No rank if no qualifying jobs
        return null
    }
    
    private fun calculateBenefitsForRank(rank: VolunteerRank?, jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Benefit {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = currentTime
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        
        return when (rank) {
            VolunteerRank.NOVA -> {
                // Nova:
                // - Free entry + 1 guest invitation for the event where the volunteer worked (same day)
                // - 2 drink tokens for that night
                // - 50% discount at the bar for the event where the volunteer worked (same day)
                // Valid only until the end of the day of the most recent BEFORE_MIDNIGHT shift
                val lastNovaShift = jobs
                    .filter { it.shiftTime == ShiftTime.BEFORE_MIDNIGHT }
                    .maxByOrNull { it.date }
                val endOfDay = if (lastNovaShift != null) {
                    com.eventmanager.app.data.utils.DateTimeUtils.getEndOfDayWithOffset(lastNovaShift.date, offsetHours).timeInMillis
                } else null
                Benefit(
                    rank = rank,
                    description = "Free entry + 1 guest for the same-night event; 2 drink tokens; 50% bar discount (same night)",
                    freeEntry = true,
                    friendInvitation = true,
                    inviteCount = 1,
                    drinkTokens = 2,
                    barDiscount = 50,
                    guestListAccess = true,
                    validUntil = endOfDay,
                    isActive = endOfDay?.let { currentTime <= it } ?: false
                )
            }
            
            VolunteerRank.ETOILE -> {
                // Étoile:
                // - Free entry for the event where the volunteer worked (same day, no guest)
                // - Free entry + 1 guest invitation for another event within the next 31 days
                // Valid for 31 days from the most recent ETOILE shift
                val shiftJobTypes = jobTypeConfigs.filter { it.isShiftJob && it.isActive }.map { it.name }
                val lastEtoileShift = jobs
                    .filter { it.shiftTime == ShiftTime.AFTER_MIDNIGHT && shiftJobTypes.contains(it.jobTypeName) }
                    .maxByOrNull { it.date }
                
                val validUntil = if (lastEtoileShift != null) {
                    lastEtoileShift.date + (31L * 24 * 60 * 60 * 1000)
                } else {
                    currentTime + (31L * 24 * 60 * 60 * 1000)
                }
                
                val isActive = currentTime <= validUntil
                
                Benefit(
                    rank = rank,
                    description = "Free entry (same night); plus within 31 days: free entry + 1 guest for another event",
                    freeEntry = true,
                    friendInvitation = true, // friend invite usable on a future event within 31 days
                    inviteCount = 1,
                    barDiscount = 0,
                    guestListAccess = true,
                    validUntil = validUntil,
                    isActive = isActive
                )
            }
            
            VolunteerRank.GALAXIE -> {
                // Galaxie:
                // - Free entry + 50% discount at the bar for all events in that month
                val nextMonthCalendar = java.util.Calendar.getInstance()
                nextMonthCalendar.set(currentYear, currentMonth + 1, 1, 0, 0, 0)
                nextMonthCalendar.set(java.util.Calendar.MILLISECOND, 0)
                val validUntil = nextMonthCalendar.timeInMillis
                Benefit(
                    rank = rank,
                    description = "Free entry + 50% bar discount for all events this month",
                    freeEntry = true,
                    friendInvitation = false,
                    inviteCount = 0,
                    barDiscount = 50,
                    guestListAccess = true,
                    validUntil = validUntil,
                    isActive = currentTime < validUntil
                )
            }
            
            VolunteerRank.ORION -> {
                // Orion (1 year from ORION start date):
                // - 1 guest invitation for every event
                // - 50% bar discount at all events
                // - Special partner event/location benefits
                
                // Find the ORION start date to calculate ORION end date
                val orionJobTypes = jobTypeConfigs.filter { it.isOrionJob && it.isActive }.map { it.name }
                val orionJobs = jobs.filter { job ->
                    orionJobTypes.contains(job.jobTypeName)
                }.sortedByDescending { it.date }
                
                val validUntil = if (orionJobs.isNotEmpty()) {
                    val orionStartDate = orionJobs.first().date
                    val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
                    oneYearAfterOrion
                } else {
                    // Fallback: 1 year from now if no ORION job found
                    val nextYearCalendar = java.util.Calendar.getInstance()
                    nextYearCalendar.set(currentYear + 1, currentMonth, 1, 0, 0, 0)
                    nextYearCalendar.set(java.util.Calendar.MILLISECOND, 0)
                    nextYearCalendar.timeInMillis
                }
                
                Benefit(
                    rank = rank,
                    description = "1 guest per event; 50% bar discount; partner benefits (1 year from ORION start)",
                    freeEntry = true,
                    friendInvitation = true,
                    inviteCount = 1,
                    barDiscount = 50,
                    guestListAccess = true,
                    extraordinaryBenefits = true,
                    validUntil = validUntil,
                    isActive = currentTime < validUntil
                )
            }
            
            VolunteerRank.VETERAN -> {
                // Veteran (1 year after ORION service ends, for 1 year duration):
                // - 1 guest invitation for every event (1 year)
                // - 50% bar discount at all events (1 year)
                // - Special partner event/location benefits (1 year)
                
                // Find the ORION start date to calculate VETERAN end date
                val orionJobTypes = jobTypeConfigs.filter { it.isOrionJob && it.isActive }.map { it.name }
                val orionJobs = jobs.filter { job ->
                    orionJobTypes.contains(job.jobTypeName)
                }.sortedByDescending { it.date }
                
                val validUntil = if (orionJobs.isNotEmpty()) {
                    val orionStartDate = orionJobs.first().date
                    val twoYearsAfterOrion = orionStartDate + (2L * 365L * 24 * 60 * 60 * 1000)
                    twoYearsAfterOrion
                } else {
                    // Fallback: 1 year from now if no ORION job found
                    val nextYearCalendar = java.util.Calendar.getInstance()
                    nextYearCalendar.set(currentYear + 1, currentMonth, 1, 0, 0, 0)
                    nextYearCalendar.set(java.util.Calendar.MILLISECOND, 0)
                    nextYearCalendar.timeInMillis
                }
                
                Benefit(
                    rank = rank,
                    description = "1 guest per event; 50% bar discount; partner benefits (1 year after ORION)",
                    freeEntry = true,
                    friendInvitation = true,
                    inviteCount = 1,
                    barDiscount = 50,
                    guestListAccess = true,
                    extraordinaryBenefits = true,
                    validUntil = validUntil,
                    isActive = currentTime < validUntil
                )
            }
            
            VolunteerRank.SPECIAL -> {
                // SPECIAL rank should not be handled here as it's only for manual rewards
                // This case should never be reached in the stellar benefits calculation
                Benefit(
                    rank = rank,
                    description = "Special rank - should not appear in stellar benefits",
                    freeEntry = false,
                    friendInvitation = false,
                    inviteCount = 0,
                    drinkTokens = 0,
                    barDiscount = 0,
                    guestListAccess = false,
                    extraordinaryBenefits = false,
                    validUntil = null,
                    isActive = false
                )
            }
            
            null -> {
                // No rank - no benefits
                Benefit(
                    rank = null,
                    description = "No benefits - no rank earned",
                    freeEntry = false,
                    friendInvitation = false,
                    inviteCount = 0,
                    drinkTokens = 0,
                    barDiscount = 0,
                    guestListAccess = false,
                    extraordinaryBenefits = false,
                    validUntil = null,
                    isActive = false
                )
            }
        }
    }
    
    private fun getMonthlyShiftCount(jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Int {
        val monthStart = com.eventmanager.app.data.utils.DateTimeUtils.getStartOfMonthWithOffset(currentTime, offsetHours)
        val monthEnd = com.eventmanager.app.data.utils.DateTimeUtils.getEndOfMonthWithOffset(currentTime, offsetHours) + 1
        
        val shiftJobTypes = jobTypeConfigs.filter { it.isShiftJob && it.isActive }.map { it.name }
        return jobs.count { job ->
            job.date >= monthStart && job.date < monthEnd && shiftJobTypes.contains(job.jobTypeName)
        }
    }
    
    private fun hasAfterMidnightShift(jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Boolean {
        val monthStart = com.eventmanager.app.data.utils.DateTimeUtils.getStartOfMonthWithOffset(currentTime, offsetHours)
        val monthEnd = com.eventmanager.app.data.utils.DateTimeUtils.getEndOfMonthWithOffset(currentTime, offsetHours) + 1
        
        val shiftJobTypes = jobTypeConfigs.filter { it.isShiftJob && it.isActive }.map { it.name }
        return jobs.any { job ->
            job.date >= monthStart && job.date < monthEnd && 
            job.shiftTime == ShiftTime.AFTER_MIDNIGHT && 
            shiftJobTypes.contains(job.jobTypeName)
        }
    }
    
    private fun hasBeforeMidnightShift(jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Boolean {
        val monthStart = com.eventmanager.app.data.utils.DateTimeUtils.getStartOfMonthWithOffset(currentTime, offsetHours)
        val monthEnd = com.eventmanager.app.data.utils.DateTimeUtils.getEndOfMonthWithOffset(currentTime, offsetHours) + 1
        
        val shiftJobTypes = jobTypeConfigs.filter { it.isShiftJob && it.isActive }.map { it.name }
        return jobs.any { job ->
            job.date >= monthStart && job.date < monthEnd && 
            job.shiftTime == ShiftTime.BEFORE_MIDNIGHT && 
            shiftJobTypes.contains(job.jobTypeName)
        }
    }
    
    // Legacy method for backward compatibility
    fun getBenefitsForRank(rank: VolunteerRank?): Benefit {
        return calculateBenefitsForRank(rank, emptyList(), emptyList(), System.currentTimeMillis(), 0)
    }
    
    private fun calculateManualRewardsBenefit(
        jobs: List<Job>, 
        jobTypeConfigs: List<JobTypeConfig>, 
        currentTime: Long
    ): Benefit? {
        // Find the most recent job with manual rewards
        val manualRewardJobs = jobs.filter { job ->
            val jobTypeConfig = jobTypeConfigs.find { it.name == job.jobTypeName }
            jobTypeConfig?.benefitSystemType == BenefitSystemType.MANUAL && 
            jobTypeConfig.manualRewards != null
        }
        
        if (manualRewardJobs.isEmpty()) {
            return null
        }
        
        val mostRecentJob = manualRewardJobs.maxByOrNull { it.date } ?: return null
        val jobTypeConfig = jobTypeConfigs.find { it.name == mostRecentJob.jobTypeName }
        val manualRewards = jobTypeConfig?.manualRewards ?: return null
        
        // Calculate valid until based on duration
        val validUntil = mostRecentJob.date + (manualRewards.durationDays * 24L * 60 * 60 * 1000)
        val isActive = currentTime <= validUntil
        
        // Build description
        val descriptionParts = mutableListOf<String>()
        if (manualRewards.freeEntry) descriptionParts.add("Free entry")
        if (manualRewards.invites > 0) descriptionParts.add("${manualRewards.invites} invites")
        if (manualRewards.freeDrinks > 0) descriptionParts.add("${manualRewards.freeDrinks} free drinks")
        if (manualRewards.barDiscountPercentage > 0) descriptionParts.add("${manualRewards.barDiscountPercentage}% bar discount")
        if (manualRewards.otherNotes.isNotEmpty()) descriptionParts.add(manualRewards.otherNotes)
        
        val description = if (descriptionParts.isNotEmpty()) {
            "Manual rewards: ${descriptionParts.joinToString(", ")} (${manualRewards.durationDays} days)"
        } else {
            "Manual rewards (${manualRewards.durationDays} days)"
        }
        
        return Benefit(
            rank = VolunteerRank.SPECIAL, // Manual rewards get SPECIAL rank
            description = description,
            freeEntry = manualRewards.freeEntry,
            friendInvitation = manualRewards.invites > 0,
            inviteCount = manualRewards.invites,
            drinkTokens = manualRewards.freeDrinks,
            barDiscount = manualRewards.barDiscountPercentage,
            guestListAccess = manualRewards.freeEntry || manualRewards.invites > 0,
            extraordinaryBenefits = false,
            validUntil = validUntil,
            isActive = isActive
        )
    }

    private fun isVolunteerVeteran(jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Boolean {
        val orionJobTypes = jobTypeConfigs.filter { it.isOrionJob && it.isActive }.map { it.name }
        val orionJobs = jobs.filter { job ->
            orionJobTypes.contains(job.jobTypeName)
        }.sortedByDescending { it.date }

        if (orionJobs.isNotEmpty()) {
            val orionStartDate = orionJobs.first().date
            val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
            val twoYearsAfterOrion = orionStartDate + (2L * 365L * 24 * 60 * 60 * 1000)

            return currentTime >= oneYearAfterOrion && currentTime < twoYearsAfterOrion
        }
        return false
    }

    private fun isVolunteerOrion(jobs: List<Job>, jobTypeConfigs: List<JobTypeConfig>, currentTime: Long, offsetHours: Int = 0): Boolean {
        val orionJobTypes = jobTypeConfigs.filter { it.isOrionJob && it.isActive }.map { it.name }
        val orionJobs = jobs.filter { job ->
            orionJobTypes.contains(job.jobTypeName)
        }.sortedByDescending { it.date }

        if (orionJobs.isNotEmpty()) {
            val orionStartDate = orionJobs.first().date
            val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
            return currentTime >= orionStartDate && currentTime < oneYearAfterOrion
        }
        return false
    }

    private fun aggregateBenefits(benefits: List<Benefit>): Benefit {
        // Add specific descriptions for aggregated benefits
        val descriptionParts = mutableListOf<String>()
        if (benefits.any { it.freeEntry }) descriptionParts.add("Free entry")
        if (benefits.any { it.friendInvitation }) descriptionParts.add("Friend invitation")
        val totalInvites = benefits.sumOf { it.inviteCount }
        if (totalInvites > 0) descriptionParts.add("$totalInvites invites")
        val totalDrinkTokens = benefits.sumOf { it.drinkTokens }
        if (totalDrinkTokens > 0) descriptionParts.add("$totalDrinkTokens drink tokens")
        val maxDiscount = benefits.maxOfOrNull { it.barDiscount } ?: 0
        if (maxDiscount > 0) descriptionParts.add("$maxDiscount% bar discount")
        if (benefits.any { it.guestListAccess }) descriptionParts.add("Guest list access")
        if (benefits.any { it.extraordinaryBenefits }) descriptionParts.add("Extraordinary benefits")

        val descriptionText = if (descriptionParts.isNotEmpty()) {
            "Aggregated benefits: ${descriptionParts.joinToString(", ")}"
        } else {
            "Aggregated benefits"
        }

        return Benefit(
            rank = null, // No single primary rank for aggregated benefits
            description = descriptionText,
            freeEntry = benefits.any { it.freeEntry },
            friendInvitation = benefits.any { it.friendInvitation },
            inviteCount = benefits.sumOf { it.inviteCount },
            drinkTokens = benefits.sumOf { it.drinkTokens },
            barDiscount = benefits.maxOfOrNull { it.barDiscount } ?: 0,
            guestListAccess = benefits.any { it.guestListAccess },
            extraordinaryBenefits = benefits.any { it.extraordinaryBenefits },
            validUntil = benefits.mapNotNull { it.validUntil }.maxOrNull(),
            isActive = benefits.any { it.isActive }
        )
    }
    
    /**
     * Calculate total number of free drinks available for all active volunteers
     * based on their current volunteer benefit status
     */
    fun calculateTotalFreeDrinks(
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long = System.currentTimeMillis()
    ): Int {
        return volunteers.sumOf { volunteer ->
            // Calculate benefit status for each volunteer
            val benefitStatus = calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs, currentTime)
            
            // Sum up drink tokens from:
            // 1. Primary aggregated benefit
            val primaryDrinks = if (benefitStatus.benefits.isActive) benefitStatus.benefits.drinkTokens else 0
            
            // 2. All active benefits (from activeBenefits list)
            val activeBenefitsDrinks = benefitStatus.activeBenefits.sumOf { benefit ->
                if (benefit.isActive) benefit.drinkTokens else 0
            }
            
            // Return the maximum between primary and active benefits sum to avoid double counting
            // The aggregated benefit already includes all active benefits summed up
            primaryDrinks
        }
    }
}
