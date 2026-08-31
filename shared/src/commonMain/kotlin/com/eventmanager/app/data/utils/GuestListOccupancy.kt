package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.Benefit
import com.eventmanager.app.data.models.BenefitCalculator
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.Volunteer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Headcount of the guest list: permanent/temporary guests and shift-based volunteers,
 * each plus the friend invitations they are entitled to.
 */
object GuestListOccupancy {
    private val defaultZone: ZoneId = ZoneId.of("Europe/Zurich")

    data class Snapshot(
        val permanentGuests: Int,
        val permanentGuestInvites: Int,
        val temporaryGuests: Int,
        val temporaryGuestInvites: Int,
        val volunteersOnList: Int,
        val volunteerFriendInvites: Int,
    ) {
        val guestHeads: Int get() = permanentGuests + temporaryGuests
        val guestFriendInvites: Int get() = permanentGuestInvites + temporaryGuestInvites

        /** Dashboard "Total List": people on the list plus the friend invites they may bring. */
        val totalList: Int
            get() = guestHeads + guestFriendInvites + volunteersOnList + volunteerFriendInvites
    }

    data class SeriesPoint(
        val timestamp: Long,
        val volunteersOnList: Int,
        val volunteerFriendInvites: Int,
        val guestHeads: Int,
        val guestFriendInvites: Int,
    ) {
        val totalList: Int
            get() = volunteersOnList + volunteerFriendInvites + guestHeads + guestFriendInvites
    }

    fun friendInvitesFromBenefit(benefits: Benefit): Int {
        val fromCount = benefits.inviteCount.coerceAtLeast(0)
        if (fromCount > 0) return fromCount
        return if (benefits.friendInvitation) 1 else 0
    }

    fun hasGuestListAccess(benefits: Benefit, sampleTime: Long): Boolean {
        if (!benefits.guestListAccess) return false
        if (!benefits.isActive && (benefits.futureEventEntriesRemaining ?: 0) <= 0) return false
        val until = benefits.validUntil
        return until == null || sampleTime <= until
    }

    fun eventLocalDate(timestamp: Long, zone: ZoneId = defaultZone): LocalDate {
        return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    }

    fun snapshot(
        guests: List<Guest>,
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        currentTime: Long,
        offsetHours: Int,
        zone: ZoneId = defaultZone,
        isTemporaryOnList: (LocalDate) -> Boolean,
    ): Snapshot {
        val jobsHorizon = DateTimeUtils.getEndOfDayWithOffset(currentTime, offsetHours).timeInMillis
        val jobsByVolunteer = jobs
            .filter { it.date <= jobsHorizon }
            .groupBy { it.volunteerId }
        return snapshotFromGroupedJobs(
            guests = guests,
            volunteers = volunteers,
            jobsByVolunteer = jobsByVolunteer,
            jobTypeConfigs = jobTypeConfigs,
            sampleTime = currentTime,
            offsetHours = offsetHours,
            zone = zone,
            isTemporaryOnList = isTemporaryOnList,
        )
    }

    /**
     * One sample per aggregation bucket. Benefits are evaluated at the end of the venue day
     * (or the end of the bucket) so an evening shift on 02.02 counts on 02.02.
     */
    fun historicalSeries(
        guests: List<Guest>,
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        jobTypeConfigs: List<JobTypeConfig>,
        startTime: Long,
        now: Long,
        aggregationMs: Long,
        offsetHours: Int,
        zone: ZoneId = defaultZone,
    ): List<SeriesPoint> {
        if (aggregationMs <= 0L || now < startTime) return emptyList()
        val jobsByVolunteer = jobs.groupBy { it.volunteerId }
        val points = mutableListOf<SeriesPoint>()
        var bucketStart = DateTimeUtils.getStartOfDayWithOffset(startTime, offsetHours)
        while (bucketStart <= now) {
            val periodEnd = bucketStart + aggregationMs
            val sampleAnchor = minOf(now, periodEnd - 1L).coerceAtLeast(bucketStart)
            val endOfSampleDay = DateTimeUtils.getEndOfDayWithOffset(sampleAnchor, offsetHours).timeInMillis
            val sampleTime = minOf(now, endOfSampleDay)
            val bucketStartDate = eventLocalDate(bucketStart, zone)
            val bucketEndDate = eventLocalDate(sampleAnchor, zone)
            val jobsUpToSample = jobsByVolunteer.mapValues { (_, volunteerJobs) ->
                volunteerJobs.filter { it.date <= sampleTime }
            }
            val snap = snapshotFromGroupedJobs(
                guests = guests,
                volunteers = volunteers,
                jobsByVolunteer = jobsUpToSample,
                jobTypeConfigs = jobTypeConfigs,
                sampleTime = sampleTime,
                offsetHours = offsetHours,
                zone = zone,
                isTemporaryOnList = { eventDate ->
                    !eventDate.isBefore(bucketStartDate) && !eventDate.isAfter(bucketEndDate)
                },
            )
            points.add(
                SeriesPoint(
                    timestamp = bucketStart,
                    volunteersOnList = snap.volunteersOnList,
                    volunteerFriendInvites = snap.volunteerFriendInvites,
                    guestHeads = snap.guestHeads,
                    guestFriendInvites = snap.guestFriendInvites,
                )
            )
            bucketStart = periodEnd
        }
        return points
    }

    private fun snapshotFromGroupedJobs(
        guests: List<Guest>,
        volunteers: List<Volunteer>,
        jobsByVolunteer: Map<String, List<Job>>,
        jobTypeConfigs: List<JobTypeConfig>,
        sampleTime: Long,
        offsetHours: Int,
        zone: ZoneId,
        isTemporaryOnList: (LocalDate) -> Boolean,
    ): Snapshot {
        var permanentGuests = 0
        var permanentGuestInvites = 0
        var temporaryGuests = 0
        var temporaryGuestInvites = 0
        guests.forEach { guest ->
            when {
                guest.isTemporaryGuest -> {
                    val ts = guest.temporaryEventDate ?: return@forEach
                    if (!isTemporaryOnList(eventLocalDate(ts, zone))) return@forEach
                    temporaryGuests++
                    temporaryGuestInvites += guest.invitations.coerceAtLeast(0)
                }
                guest.isVolunteerBenefit -> Unit
                else -> {
                    permanentGuests++
                    permanentGuestInvites += guest.invitations.coerceAtLeast(0)
                }
            }
        }

        var volunteersOnList = 0
        var volunteerFriendInvites = 0
        volunteers.forEach { volunteer ->
            val volunteerJobs = jobsByVolunteer[volunteer.id] ?: emptyList()
            val benefits = BenefitCalculator.calculateVolunteerBenefitStatusFromVolunteerJobs(
                volunteer = volunteer,
                volunteerJobs = volunteerJobs,
                jobTypeConfigs = jobTypeConfigs,
                currentTime = sampleTime,
                offsetHours = offsetHours,
            ).benefits
            if (!hasGuestListAccess(benefits, sampleTime)) return@forEach
            volunteersOnList++
            volunteerFriendInvites += friendInvitesFromBenefit(benefits)
        }

        return Snapshot(
            permanentGuests = permanentGuests,
            permanentGuestInvites = permanentGuestInvites,
            temporaryGuests = temporaryGuests,
            temporaryGuestInvites = temporaryGuestInvites,
            volunteersOnList = volunteersOnList,
            volunteerFriendInvites = volunteerFriendInvites,
        )
    }
}
