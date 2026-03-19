package com.eventmanager.app.data.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.parcelize.Parcelize
import com.eventmanager.app.data.utils.NanoIdGenerator

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
    val email: String = "", // Guest email address
    val phoneNumber: String = "", // Guest phone number
    val invitations: Int,
    val venueName: String, // Store actual venue name for unlimited venue support
    val notes: String = "",
    val isVolunteerBenefit: Boolean = false,
    val volunteerId: String? = null, // NanoID of the volunteer this guest entry represents (for volunteer benefits)
    val lastModified: Long = System.currentTimeMillis(),
    val isTemporaryGuest: Boolean = false,
    val temporaryArtistName: String = "",
    val temporaryEventDate: Long? = null,
    val temporaryContactPhone: String = "",
    val nfcCardUid: String = ""
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
    @PrimaryKey
    val id: String = NanoIdGenerator.generateVolunteerId(), // NanoID generated at object creation
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
    val lastModified: Long = System.currentTimeMillis(),
    val nfcCardUid: String = ""
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
    val volunteerId: String, // NanoID of the volunteer
    val jobType: JobType,
    val jobTypeName: String, // Store the actual job type name for personalized types
    val venueName: String, // Store actual venue name for unlimited venue support
    val date: Long, // Store as timestamp
    val shiftTime: ShiftTime,
    val benefitUsed: Boolean? = null, // null = not an after-midnight shift, false = benefit not yet redeemed, true = benefit redeemed
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
    val volunteerId: String, // NanoID of the volunteer
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
    
    /**
     * Pre-computed context for efficient batch benefit calculations.
     * This avoids repeated filtering and date computations when calculating
     * benefits for multiple volunteers.
     */
    class CalculationContext(
        jobTypeConfigs: List<JobTypeConfig>,
        val currentTime: Long = System.currentTimeMillis(),
        val offsetHours: Int = 0
    ) {
        // Pre-computed job type lookups (computed once, reused for all volunteers)
        val shiftJobTypeNames: Set<String> = jobTypeConfigs
            .filter { it.isShiftJob && it.isActive }
            .map { it.name }
            .toSet()
        
        val orionJobTypeNames: Set<String> = jobTypeConfigs
            .filter { it.isOrionJob && it.isActive }
            .map { it.name }
            .toSet()
        
        val manualRewardJobTypes: Map<String, JobTypeConfig> = jobTypeConfigs
            .filter { it.benefitSystemType == BenefitSystemType.MANUAL && it.manualRewards != null }
            .associateBy { it.name }
        
        val jobTypeConfigsByName: Map<String, JobTypeConfig> = jobTypeConfigs.associateBy { it.name }
        
        // Pre-computed date ranges (computed once, reused for all volunteers)
        val monthStart: Long = com.eventmanager.app.data.utils.DateTimeUtils.getStartOfMonthWithOffset(currentTime, offsetHours)
        val monthEnd: Long = com.eventmanager.app.data.utils.DateTimeUtils.getEndOfMonthWithOffset(currentTime, offsetHours) + 1
        
        // Calendar values for benefit calculations
        private val calendar = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }
        val currentMonth: Int = calendar.get(java.util.Calendar.MONTH)
        val currentYear: Int = calendar.get(java.util.Calendar.YEAR)
    }
    
    fun calculateVolunteerBenefitStatus(
        volunteer: Volunteer, 
        jobs: List<Job>, 
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long = System.currentTimeMillis(),
        offsetHours: Int = 0
    ): VolunteerBenefitStatus {
        // NOTE: This method intentionally accepts ALL jobs for backward compatibility.
        // For performance when computing statuses for many volunteers, prefer calling
        // calculateVolunteerBenefitStatusFromVolunteerJobs with a pre-filtered list.
        val volunteerJobs = jobs.filter { it.volunteerId == volunteer.id }
        return calculateVolunteerBenefitStatusFromVolunteerJobs(
            volunteer = volunteer,
            volunteerJobs = volunteerJobs,
            jobTypeConfigs = jobTypeConfigs,
            currentTime = currentTime,
            offsetHours = offsetHours
        )
    }

    /**
     * Caller passes jobs already filtered for the volunteer.
     * This avoids O(totalJobs) filtering per volunteer when computing many statuses.
     */
    fun calculateVolunteerBenefitStatusFromVolunteerJobs(
        volunteer: Volunteer,
        volunteerJobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long = System.currentTimeMillis(),
        offsetHours: Int = 0
    ): VolunteerBenefitStatus {
        // Create context for this calculation (pre-computes lookups and date ranges)
        val ctx = CalculationContext(jobTypeConfigs, currentTime, offsetHours)
        return calculateWithContext(volunteer, volunteerJobs, ctx)
    }
    
    /**
     * Calculate benefit status using pre-computed context.
     * Use this when calculating benefits for multiple volunteers to avoid
     * redundant filtering and date computations.
     */
    fun calculateWithContext(
        volunteer: Volunteer,
        volunteerJobs: List<Job>,
        ctx: CalculationContext
    ): VolunteerBenefitStatus {
        val lastJobDate = volunteerJobs.maxOfOrNull { it.date }
        val monthlyShifts = getMonthlyShiftCountOptimized(volunteerJobs, ctx)
        
        // Check for manual rewards first (overrides everything)
        val manualRewardsBenefit = calculateManualRewardsBenefitOptimized(volunteerJobs, ctx)
        
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
                isEligibleForEtoile = hasAfterMidnightShiftOptimized(volunteerJobs, ctx),
                isEligibleForNova = hasBeforeMidnightShiftOptimized(volunteerJobs, ctx)
            )
        }
        
        // Collect ALL applicable benefits from each qualifying rank
        val allApplicableBenefits = mutableListOf<Benefit>()
        var primaryRank: VolunteerRank? = null
        
        // Pre-compute orion jobs once (used by both VETERAN and ORION checks)
        val orionJobs = volunteerJobs
            .filter { ctx.orionJobTypeNames.contains(it.jobTypeName) }
            .sortedByDescending { it.date }
        
        // Check for VETERAN rank
        if (isVolunteerVeteranOptimized(orionJobs, ctx)) {
            val benefit = calculateBenefitsForRankOptimized(VolunteerRank.VETERAN, volunteerJobs, orionJobs, ctx)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                primaryRank = VolunteerRank.VETERAN
            }
        }
        
        // Check for ORION rank
        if (isVolunteerOrionOptimized(orionJobs, ctx)) {
            val benefit = calculateBenefitsForRankOptimized(VolunteerRank.ORION, volunteerJobs, orionJobs, ctx)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank != VolunteerRank.VETERAN) primaryRank = VolunteerRank.ORION
            }
        }
        
        // Check for GALAXIE rank
        if (monthlyShifts >= 3) {
            val benefit = calculateBenefitsForRankOptimized(VolunteerRank.GALAXIE, volunteerJobs, orionJobs, ctx)
            if (benefit.isActive) {
                allApplicableBenefits.add(benefit)
                if (primaryRank == null) primaryRank = VolunteerRank.GALAXIE
            }
        }
        
        // Check for ETOILE rank.
        // Only include the ETOILE benefit when the volunteer still has at least
        // one unused after-midnight shift entry. Once every shift's benefit has
        // been redeemed (benefitUsed == true), the ETOILE contribution is removed
        // so the UI correctly reflects the consumed state.
        if (hasAfterMidnightShiftOptimized(volunteerJobs, ctx)) {
            // Single-pass check: look for any unredeemed after-midnight shift.
            // Using `any` with the negated condition avoids allocating a filtered list.
            val hasUnusedAfterMidnight = volunteerJobs.any {
                it.shiftTime == ShiftTime.AFTER_MIDNIGHT &&
                    ctx.shiftJobTypeNames.contains(it.jobTypeName) &&
                    it.benefitUsed != true
            }

            if (hasUnusedAfterMidnight) {
                val benefit = calculateBenefitsForRankOptimized(VolunteerRank.ETOILE, volunteerJobs, orionJobs, ctx)
                if (benefit.isActive) {
                    allApplicableBenefits.add(benefit)
                    if (primaryRank == null) primaryRank = VolunteerRank.ETOILE
                }
            }
        }
        
        // Check for NOVA rank (lowest priority, but should still be included if applicable)
        if (hasBeforeMidnightShiftOptimized(volunteerJobs, ctx)) {
            val benefit = calculateBenefitsForRankOptimized(VolunteerRank.NOVA, volunteerJobs, orionJobs, ctx)
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
            isEligibleForEtoile = hasAfterMidnightShiftOptimized(volunteerJobs, ctx),
            isEligibleForNova = hasBeforeMidnightShiftOptimized(volunteerJobs, ctx)
        )
    }
    
    // ========== OPTIMIZED HELPER METHODS ==========
    
    private fun getMonthlyShiftCountOptimized(jobs: List<Job>, ctx: CalculationContext): Int {
        return jobs.count { job ->
            job.date >= ctx.monthStart && job.date < ctx.monthEnd && ctx.shiftJobTypeNames.contains(job.jobTypeName)
        }
    }
    
    private fun hasAfterMidnightShiftOptimized(jobs: List<Job>, ctx: CalculationContext): Boolean {
        return jobs.any { job ->
            job.date >= ctx.monthStart && job.date < ctx.monthEnd && 
            job.shiftTime == ShiftTime.AFTER_MIDNIGHT && 
            ctx.shiftJobTypeNames.contains(job.jobTypeName)
        }
    }
    
    private fun hasBeforeMidnightShiftOptimized(jobs: List<Job>, ctx: CalculationContext): Boolean {
        return jobs.any { job ->
            job.date >= ctx.monthStart && job.date < ctx.monthEnd && 
            job.shiftTime == ShiftTime.BEFORE_MIDNIGHT && 
            ctx.shiftJobTypeNames.contains(job.jobTypeName)
        }
    }
    
    private fun isVolunteerVeteranOptimized(orionJobs: List<Job>, ctx: CalculationContext): Boolean {
        if (orionJobs.isNotEmpty()) {
            val orionStartDate = orionJobs.first().date
            val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
            val twoYearsAfterOrion = orionStartDate + (2L * 365L * 24 * 60 * 60 * 1000)
            return ctx.currentTime >= oneYearAfterOrion && ctx.currentTime < twoYearsAfterOrion
        }
        return false
    }
    
    private fun isVolunteerOrionOptimized(orionJobs: List<Job>, ctx: CalculationContext): Boolean {
        if (orionJobs.isNotEmpty()) {
            val orionStartDate = orionJobs.first().date
            val oneYearAfterOrion = orionStartDate + (365L * 24 * 60 * 60 * 1000)
            return ctx.currentTime >= orionStartDate && ctx.currentTime < oneYearAfterOrion
        }
        return false
    }
    
    private fun calculateManualRewardsBenefitOptimized(jobs: List<Job>, ctx: CalculationContext): Benefit? {
        // Find the most recent job with manual rewards using pre-computed lookup
        val manualRewardJobs = jobs.filter { job ->
            ctx.manualRewardJobTypes.containsKey(job.jobTypeName)
        }
        
        if (manualRewardJobs.isEmpty()) {
            return null
        }
        
        val mostRecentJob = manualRewardJobs.maxByOrNull { it.date } ?: return null
        val jobTypeConfig = ctx.manualRewardJobTypes[mostRecentJob.jobTypeName]
        val manualRewards = jobTypeConfig?.manualRewards ?: return null
        
        // Calculate valid until based on duration
        val validUntil = mostRecentJob.date + (manualRewards.durationDays * 24L * 60 * 60 * 1000)
        val isActive = ctx.currentTime <= validUntil
        
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
            rank = VolunteerRank.SPECIAL,
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
    
    private fun calculateBenefitsForRankOptimized(
        rank: VolunteerRank?, 
        jobs: List<Job>, 
        orionJobs: List<Job>,
        ctx: CalculationContext
    ): Benefit {
        return when (rank) {
            VolunteerRank.NOVA -> {
                val lastNovaShift = jobs
                    .filter { it.shiftTime == ShiftTime.BEFORE_MIDNIGHT }
                    .maxByOrNull { it.date }
                val endOfDay = if (lastNovaShift != null) {
                    com.eventmanager.app.data.utils.DateTimeUtils.getEndOfDayWithOffset(lastNovaShift.date, ctx.offsetHours).timeInMillis
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
                    isActive = endOfDay?.let { ctx.currentTime <= it } ?: false
                )
            }
            
            VolunteerRank.ETOILE -> {
                val lastEtoileShift = jobs
                    .filter { it.shiftTime == ShiftTime.AFTER_MIDNIGHT && ctx.shiftJobTypeNames.contains(it.jobTypeName) }
                    .maxByOrNull { it.date }
                
                val validUntil = if (lastEtoileShift != null) {
                    lastEtoileShift.date + (31L * 24 * 60 * 60 * 1000)
                } else {
                    ctx.currentTime + (31L * 24 * 60 * 60 * 1000)
                }
                
                val isActive = ctx.currentTime <= validUntil
                
                Benefit(
                    rank = rank,
                    description = "Free entry (same night); plus within 31 days: free entry + 1 guest for another event",
                    freeEntry = true,
                    friendInvitation = true,
                    inviteCount = 1,
                    barDiscount = 0,
                    guestListAccess = true,
                    validUntil = validUntil,
                    isActive = isActive
                )
            }
            
            VolunteerRank.GALAXIE -> {
                val nextMonthCalendar = java.util.Calendar.getInstance()
                nextMonthCalendar.set(ctx.currentYear, ctx.currentMonth + 1, 1, 0, 0, 0)
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
                    isActive = ctx.currentTime < validUntil
                )
            }
            
            VolunteerRank.ORION -> {
                val validUntil = if (orionJobs.isNotEmpty()) {
                    val orionStartDate = orionJobs.first().date
                    orionStartDate + (365L * 24 * 60 * 60 * 1000)
                } else {
                    val nextYearCalendar = java.util.Calendar.getInstance()
                    nextYearCalendar.set(ctx.currentYear + 1, ctx.currentMonth, 1, 0, 0, 0)
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
                    isActive = ctx.currentTime < validUntil
                )
            }
            
            VolunteerRank.VETERAN -> {
                val validUntil = if (orionJobs.isNotEmpty()) {
                    val orionStartDate = orionJobs.first().date
                    orionStartDate + (2L * 365L * 24 * 60 * 60 * 1000)
                } else {
                    val nextYearCalendar = java.util.Calendar.getInstance()
                    nextYearCalendar.set(ctx.currentYear + 1, ctx.currentMonth, 1, 0, 0, 0)
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
                    isActive = ctx.currentTime < validUntil
                )
            }
            
            VolunteerRank.SPECIAL -> {
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
     * 
     * OPTIMIZED: Uses pre-computed context and pre-grouped jobs to avoid
     * O(volunteers * jobs) complexity
     * 
     * @param volunteers List of volunteers to calculate drinks for
     * @param jobs List of all jobs
     * @param jobTypeConfigs List of job type configurations
     * @param currentTime Current timestamp for calculations
     * @return Total number of free drinks across all volunteers
     */
    fun calculateTotalFreeDrinks(
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long = System.currentTimeMillis()
    ): Int {
        // OPTIMIZED: Create calculation context once for all volunteers
        val ctx = CalculationContext(jobTypeConfigs, currentTime)
        
        // OPTIMIZED: Pre-group jobs by volunteer ID (String/NanoID) once
        val jobsByVolunteerId: Map<String, List<Job>> = jobs.groupBy { it.volunteerId }
        
        return volunteers.sumOf { volunteer ->
            // Get pre-filtered jobs for this volunteer using NanoID
            val volunteerJobs = jobsByVolunteerId[volunteer.id] ?: emptyList()
            
            // Calculate benefit status using optimized context
            val benefitStatus = calculateWithContext(volunteer, volunteerJobs, ctx)
            
            // Sum up drink tokens from primary aggregated benefit
            if (benefitStatus.benefits.isActive) benefitStatus.benefits.drinkTokens else 0
        }
    }
}
