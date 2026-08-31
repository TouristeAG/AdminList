package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.BenefitCalculator
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.NovaJobType
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.VolunteerRank

data class VolunteerNamedSeries(
    val key: String,
    val points: List<PosSeriesPoint>,
)

data class TemporaryGuestBucket(
    val artistName: String,
    val eventDate: Long?,
    val count: Int,
    val isOther: Boolean = false,
)

data class NfcEnrollmentSnapshot(
    val volunteerEnrolled: Int,
    val volunteerTotal: Int,
    val guestEnrolled: Int,
    val guestTotal: Int,
) {
    val volunteerPercent: Double
        get() = percent(volunteerEnrolled, volunteerTotal)
    val guestPercent: Double
        get() = percent(guestEnrolled, guestTotal)

    companion object {
        val EMPTY = NfcEnrollmentSnapshot(0, 0, 0, 0)

        fun percent(enrolled: Int, total: Int): Double =
            if (total <= 0) 0.0 else 100.0 * enrolled / total
    }
}

data class VolunteerDashboardSnapshot(
    val rankPie: List<PosNamedAmount>,
    val rankNovaOverTime: List<PosSeriesPoint>,
    val rankGalaxieOverTime: List<PosSeriesPoint>,
    val rankOrionOverTime: List<PosSeriesPoint>,
    val shiftsByJobType: List<VolunteerNamedSeries>,
    val recencyHistogram: List<PosNamedAmount>,
    val shiftTimePie: List<PosNamedAmount>,
    val shiftTimeProfitedOverTime: List<PosSeriesPoint>,
    val shiftTimeNonProfitedOverTime: List<PosSeriesPoint>,
    val nfcEnrollment: NfcEnrollmentSnapshot,
    val futureEntryPool: Int,
    val temporaryGuestsByEvent: List<TemporaryGuestBucket>,
) {
    companion object {
        val EMPTY = VolunteerDashboardSnapshot(
            rankPie = emptyList(),
            rankNovaOverTime = emptyList(),
            rankGalaxieOverTime = emptyList(),
            rankOrionOverTime = emptyList(),
            shiftsByJobType = emptyList(),
            recencyHistogram = emptyList(),
            shiftTimePie = emptyList(),
            shiftTimeProfitedOverTime = emptyList(),
            shiftTimeNonProfitedOverTime = emptyList(),
            nfcEnrollment = NfcEnrollmentSnapshot.EMPTY,
            futureEntryPool = 0,
            temporaryGuestsByEvent = emptyList(),
        )
    }
}

object VolunteerDashboardStats {
    const val RANK_NOVA = "NOVA"
    const val RANK_GALAXIE = "GALAXIE"
    const val RANK_ORION = "ORION"
    const val RANK_VETERAN = "VETERAN"
    const val RANK_OTHER = "OTHER"

    const val RECENCY_D0_30 = "D0_30"
    const val RECENCY_D30_90 = "D30_90"
    const val RECENCY_D90_365 = "D90_365"
    const val RECENCY_INACTIVE = "INACTIVE"

    const val SHIFT_PROFITED = "PROFITED"
    const val SHIFT_NON_PROFITED = "NON_PROFITED"

    const val OTHER_KEY = "__other__"
    const val TOTAL_KEY = "__total__"

    const val TOP_JOB_TYPES = 6
    const val TOP_TEMPORARY_EVENTS = 8

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun build(
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        guests: List<Guest>,
        jobTypeConfigs: List<JobTypeConfig>,
        startTime: Long,
        endTime: Long,
        aggregationMs: Long,
        now: Long = endTime,
        offsetHours: Int = 0,
    ): VolunteerDashboardSnapshot {
        if (aggregationMs <= 0L) return VolunteerDashboardSnapshot.EMPTY

        val configsByName = jobTypeConfigs.associateBy { it.name }
        val jobsByVolunteer = jobs.groupBy { it.volunteerId }.mapValues { (_, volunteerJobs) ->
            volunteerJobs.sortedBy { it.date }
        }

        val rankPie = rankPie(volunteers, jobsByVolunteer, jobTypeConfigs, now, offsetHours)
        val (nova, galaxie, orion) = rankSeries(
            volunteers = volunteers,
            jobsByVolunteer = jobsByVolunteer,
            jobTypeConfigs = jobTypeConfigs,
            startTime = startTime,
            endTime = endTime,
            aggregationMs = aggregationMs,
            offsetHours = offsetHours,
        )
        val shiftsByJobType = shiftsByJobType(
            jobs = jobs,
            jobTypeConfigs = jobTypeConfigs,
            startTime = startTime,
            endTime = endTime,
            aggregationMs = aggregationMs,
        )
        val recencyHistogram = recencyHistogram(volunteers, jobsByVolunteer, now)
        val relevantJobs = jobs.filter { isShiftTimeRelevant(it, configsByName, jobTypeConfigs) }
        val shiftTimePie = shiftTimePie(relevantJobs, startTime, endTime)
        val shiftTimeProfited = bucketSeries(startTime, endTime, aggregationMs) { bucketStart, bucketEnd ->
            relevantJobs.count {
                it.date in bucketStart until bucketEnd && it.shiftTime == ShiftTime.BEFORE_MIDNIGHT
            }.toDouble()
        }
        val shiftTimeNonProfited = bucketSeries(startTime, endTime, aggregationMs) { bucketStart, bucketEnd ->
            relevantJobs.count {
                it.date in bucketStart until bucketEnd && it.shiftTime == ShiftTime.AFTER_MIDNIGHT
            }.toDouble()
        }

        return VolunteerDashboardSnapshot(
            rankPie = rankPie,
            rankNovaOverTime = nova,
            rankGalaxieOverTime = galaxie,
            rankOrionOverTime = orion,
            shiftsByJobType = shiftsByJobType,
            recencyHistogram = recencyHistogram,
            shiftTimePie = shiftTimePie,
            shiftTimeProfitedOverTime = shiftTimeProfited,
            shiftTimeNonProfitedOverTime = shiftTimeNonProfited,
            nfcEnrollment = nfcEnrollment(volunteers, jobsByVolunteer, guests),
            futureEntryPool = futureEntryPool(jobs, configsByName, now, offsetHours),
            temporaryGuestsByEvent = temporaryGuestsByEvent(guests),
        )
    }

    internal fun rankKey(rank: VolunteerRank?): String = when (rank) {
        VolunteerRank.NOVA -> RANK_NOVA
        VolunteerRank.GALAXIE -> RANK_GALAXIE
        VolunteerRank.ORION -> RANK_ORION
        VolunteerRank.VETERAN -> RANK_VETERAN
        VolunteerRank.ETOILE, VolunteerRank.SPECIAL, null -> RANK_OTHER
    }

    internal fun isShiftTimeRelevant(
        job: Job,
        configsByName: Map<String, JobTypeConfig>,
        jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    ): Boolean {
        val config = configsByName[job.jobTypeName]
            ?: jobTypeConfigs.firstOrNull { it.name.equals(job.jobTypeName, ignoreCase = true) }
        return config?.let { it.novaJobType == NovaJobType.DEFAULT_SHIFT && it.requiresShiftTime } ?: true
    }

    private fun rankPie(
        volunteers: List<Volunteer>,
        jobsByVolunteer: Map<String, List<Job>>,
        jobTypeConfigs: List<JobTypeConfig>,
        now: Long,
        offsetHours: Int,
    ): List<PosNamedAmount> {
        if (volunteers.isEmpty()) return emptyList()
        val ctx = BenefitCalculator.CalculationContext(jobTypeConfigs, now, offsetHours)
        val counts = linkedMapOf(
            RANK_NOVA to 0,
            RANK_GALAXIE to 0,
            RANK_ORION to 0,
            RANK_VETERAN to 0,
            RANK_OTHER to 0,
        )
        volunteers.forEach { volunteer ->
            val volunteerJobs = jobsByVolunteer[volunteer.id].orEmpty()
            val rank = BenefitCalculator.calculateWithContext(volunteer, volunteerJobs, ctx).rank
            val key = rankKey(rank)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries
            .filter { it.value > 0 }
            .map { (key, count) -> PosNamedAmount(key, count.toDouble(), count) }
    }

    private fun rankSeries(
        volunteers: List<Volunteer>,
        jobsByVolunteer: Map<String, List<Job>>,
        jobTypeConfigs: List<JobTypeConfig>,
        startTime: Long,
        endTime: Long,
        aggregationMs: Long,
        offsetHours: Int,
    ): Triple<List<PosSeriesPoint>, List<PosSeriesPoint>, List<PosSeriesPoint>> {
        val nova = ArrayList<PosSeriesPoint>()
        val galaxie = ArrayList<PosSeriesPoint>()
        val orion = ArrayList<PosSeriesPoint>()
        var current = startTime
        var guard = 0
        while (current <= endTime && guard < 10_000) {
            val bucketEnd = current + aggregationMs
            val ctx = BenefitCalculator.CalculationContext(jobTypeConfigs, bucketEnd, offsetHours)
            var novaCount = 0
            var galaxieCount = 0
            var orionCount = 0
            for (volunteer in volunteers) {
                val volunteerJobs = jobsOnOrBefore(jobsByVolunteer[volunteer.id].orEmpty(), bucketEnd)
                when (rankKey(BenefitCalculator.calculateWithContext(volunteer, volunteerJobs, ctx).rank)) {
                    RANK_NOVA -> novaCount++
                    RANK_GALAXIE -> galaxieCount++
                    RANK_ORION -> orionCount++
                }
            }
            nova.add(PosSeriesPoint(current, novaCount.toDouble()))
            galaxie.add(PosSeriesPoint(current, galaxieCount.toDouble()))
            orion.add(PosSeriesPoint(current, orionCount.toDouble()))
            current += aggregationMs
            guard++
        }
        return Triple(nova, galaxie, orion)
    }

    private fun shiftsByJobType(
        jobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        startTime: Long,
        endTime: Long,
        aggregationMs: Long,
    ): List<VolunteerNamedSeries> {
        val activeNames = jobTypeConfigs.filter { it.isActive }.map { it.name }.toSet()
        val inRange = jobs.filter { it.date in startTime..endTime }
        if (inRange.isEmpty()) return emptyList()

        val totals = linkedMapOf<String, Int>()
        inRange.forEach { job ->
            val key = namedJobTypeKey(job.jobTypeName, activeNames)
            totals[key] = (totals[key] ?: 0) + 1
        }
        val ranked = totals.entries
            .filter { it.key != OTHER_KEY }
            .sortedByDescending { it.value }
        val topKeys = ranked.take(TOP_JOB_TYPES).map { it.key }.toSet()
        val hasOther = totals.any { (key, count) ->
            count > 0 && (key == OTHER_KEY || key !in topKeys)
        }

        val seriesKeys = ranked.take(TOP_JOB_TYPES).map { it.key } +
            if (hasOther) listOf(OTHER_KEY) else emptyList()

        val namedSeries = seriesKeys.map { key ->
            VolunteerNamedSeries(
                key = key,
                points = bucketSeries(startTime, endTime, aggregationMs) { bucketStart, bucketEnd ->
                    inRange.count { job ->
                        job.date in bucketStart until bucketEnd &&
                            collapsedJobTypeKey(job.jobTypeName, activeNames, topKeys) == key
                    }.toDouble()
                },
            )
        }
        val total = VolunteerNamedSeries(
            key = TOTAL_KEY,
            points = bucketSeries(startTime, endTime, aggregationMs) { bucketStart, bucketEnd ->
                inRange.count { it.date in bucketStart until bucketEnd }.toDouble()
            },
        )
        return namedSeries + total
    }

    private fun namedJobTypeKey(jobTypeName: String, activeNames: Set<String>): String {
        if (activeNames.isEmpty()) return jobTypeName.ifBlank { OTHER_KEY }
        return if (jobTypeName in activeNames) jobTypeName else OTHER_KEY
    }

    private fun collapsedJobTypeKey(
        jobTypeName: String,
        activeNames: Set<String>,
        topKeys: Set<String>,
    ): String {
        val named = namedJobTypeKey(jobTypeName, activeNames)
        return if (named in topKeys) named else OTHER_KEY
    }

    private fun recencyHistogram(
        volunteers: List<Volunteer>,
        jobsByVolunteer: Map<String, List<Job>>,
        now: Long,
    ): List<PosNamedAmount> {
        val counts = linkedMapOf(
            RECENCY_D0_30 to 0,
            RECENCY_D30_90 to 0,
            RECENCY_D90_365 to 0,
            RECENCY_INACTIVE to 0,
        )
        volunteers.forEach { volunteer ->
            val lastShift = jobsByVolunteer[volunteer.id]?.maxOfOrNull { it.date }
            val key = recencyKey(lastShift, now)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.map { (key, count) -> PosNamedAmount(key, count.toDouble(), count) }
    }

    internal fun recencyKey(lastShift: Long?, now: Long): String {
        if (lastShift == null) return RECENCY_INACTIVE
        val ageDays = (now - lastShift).toDouble() / DAY_MS
        return when {
            ageDays > 365.0 -> RECENCY_INACTIVE
            ageDays <= 30.0 -> RECENCY_D0_30
            ageDays <= 90.0 -> RECENCY_D30_90
            else -> RECENCY_D90_365
        }
    }

    private fun shiftTimePie(
        relevantJobs: List<Job>,
        startTime: Long,
        endTime: Long,
    ): List<PosNamedAmount> {
        val inRange = relevantJobs.filter { it.date in startTime..endTime }
        val profited = inRange.count { it.shiftTime == ShiftTime.BEFORE_MIDNIGHT }
        val nonProfited = inRange.count { it.shiftTime == ShiftTime.AFTER_MIDNIGHT }
        return listOf(
            PosNamedAmount(SHIFT_PROFITED, profited.toDouble(), profited),
            PosNamedAmount(SHIFT_NON_PROFITED, nonProfited.toDouble(), nonProfited),
        ).filter { it.quantity > 0 }
    }

    private fun nfcEnrollment(
        volunteers: List<Volunteer>,
        jobsByVolunteer: Map<String, List<Job>>,
        guests: List<Guest>,
    ): NfcEnrollmentSnapshot {
        val activeVolunteers = volunteers.filter { volunteer ->
            VolunteerActivityManager.isVolunteerActive(volunteer, jobsByVolunteer[volunteer.id])
        }
        val eligibleGuests = guests.filter { !it.isTemporaryGuest && !it.isVolunteerBenefit }
        return NfcEnrollmentSnapshot(
            volunteerEnrolled = activeVolunteers.count { it.nfcCardUid.isNotBlank() },
            volunteerTotal = activeVolunteers.size,
            guestEnrolled = eligibleGuests.count { it.nfcCardUid.isNotBlank() },
            guestTotal = eligibleGuests.size,
        )
    }

    private fun futureEntryPool(
        jobs: List<Job>,
        configsByName: Map<String, JobTypeConfig>,
        now: Long,
        offsetHours: Int,
    ): Int {
        return jobs.sumOf { job ->
            effectiveBenefitFutureEntriesRemaining(
                job = job,
                config = configsByName[job.jobTypeName],
                evaluationTime = now,
                offsetHours = offsetHours,
            )
        }
    }

    private fun temporaryGuestsByEvent(guests: List<Guest>): List<TemporaryGuestBucket> {
        val grouped = guests
            .filter { it.isTemporaryGuest }
            .groupBy { guest ->
                guest.temporaryArtistName.trim() to guest.temporaryEventDate
            }
            .map { (key, rows) ->
                TemporaryGuestBucket(
                    artistName = key.first,
                    eventDate = key.second,
                    count = rows.size,
                )
            }
            .sortedWith(compareByDescending<TemporaryGuestBucket> { it.count }.thenBy { it.artistName })
        if (grouped.size <= TOP_TEMPORARY_EVENTS) return grouped
        val top = grouped.take(TOP_TEMPORARY_EVENTS)
        val restCount = grouped.drop(TOP_TEMPORARY_EVENTS).sumOf { it.count }
        return if (restCount > 0) {
            top + TemporaryGuestBucket(artistName = "", eventDate = null, count = restCount, isOther = true)
        } else {
            top
        }
    }

    private fun jobsOnOrBefore(sortedJobs: List<Job>, limit: Long): List<Job> {
        if (sortedJobs.isEmpty()) return emptyList()
        var lo = 0
        var hi = sortedJobs.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedJobs[mid].date <= limit) lo = mid + 1 else hi = mid
        }
        return if (lo == 0) emptyList() else sortedJobs.subList(0, lo)
    }

    private fun bucketSeries(
        startTime: Long,
        endTime: Long,
        aggregationMs: Long,
        valueAt: (bucketStart: Long, bucketEnd: Long) -> Double,
    ): List<PosSeriesPoint> {
        val points = ArrayList<PosSeriesPoint>()
        var current = startTime
        var guard = 0
        while (current <= endTime && guard < 10_000) {
            points.add(PosSeriesPoint(current, valueAt(current, current + aggregationMs)))
            current += aggregationMs
            guard++
        }
        return points
    }
}
