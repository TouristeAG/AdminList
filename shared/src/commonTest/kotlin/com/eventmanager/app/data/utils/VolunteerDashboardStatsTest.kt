package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobType
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.NovaJobType
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.VolunteerRank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolunteerDashboardStatsTest {

    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L
    private val start = now - 7 * dayMs
    private val aggregation = dayMs

    private fun volunteer(
        id: String,
        isActive: Boolean = true,
        nfc: String = "",
    ) = Volunteer(
        id = id,
        name = id,
        lastNameAbbreviation = id.take(1),
        email = "$id@example.com",
        phoneNumber = "",
        isActive = isActive,
        nfcCardUid = nfc,
    )

    private fun barConfig() = JobTypeConfig(
        name = "Bar",
        isShiftJob = true,
        isActive = true,
        novaJobType = NovaJobType.DEFAULT_SHIFT,
        requiresShiftTime = true,
    )

    private fun meetingConfig() = JobTypeConfig(
        name = "Meeting",
        isShiftJob = true,
        isActive = true,
        novaJobType = NovaJobType.MEETING,
        requiresShiftTime = false,
    )

    private fun cloakroomConfig() = JobTypeConfig(
        name = "Cloakroom",
        isShiftJob = true,
        isActive = true,
        novaJobType = NovaJobType.DEFAULT_SHIFT,
        requiresShiftTime = true,
    )

    private fun job(
        volunteerId: String,
        date: Long,
        typeName: String = "Bar",
        shiftTime: ShiftTime = ShiftTime.BEFORE_MIDNIGHT,
        remaining: Int? = null,
    ) = Job(
        volunteerId = volunteerId,
        jobType = JobType.BAR,
        jobTypeName = typeName,
        venueName = "Main",
        date = date,
        shiftTime = shiftTime,
        benefitFutureEntriesRemaining = remaining,
    )

    private fun guest(
        name: String,
        temporary: Boolean = false,
        volunteerBenefit: Boolean = false,
        artist: String = "",
        eventDate: Long? = null,
        nfc: String = "",
    ) = Guest(
        name = name,
        invitations = 1,
        venueName = "Main",
        isTemporaryGuest = temporary,
        isVolunteerBenefit = volunteerBenefit,
        temporaryArtistName = artist,
        temporaryEventDate = eventDate,
        nfcCardUid = nfc,
    )

    private fun stats(
        volunteers: List<Volunteer>,
        jobs: List<Job>,
        guests: List<Guest> = emptyList(),
        configs: List<JobTypeConfig> = listOf(barConfig(), meetingConfig(), cloakroomConfig()),
        from: Long = start,
        until: Long = now,
    ) = VolunteerDashboardStats.build(
        volunteers = volunteers,
        jobs = jobs,
        guests = guests,
        jobTypeConfigs = configs,
        startTime = from,
        endTime = until,
        aggregationMs = aggregation,
        now = until,
        offsetHours = 0,
    )

    @Test
    fun rankPiePutsNovaGalaxieVeteranAndOther() {
        val novaVolunteer = volunteer("nova")
        val otherVolunteer = volunteer("special")
        val veteranVolunteer = volunteer("veteran")
        val jobs = listOf(
            job(novaVolunteer.id, now - 2 * 60 * 60 * 1000),
        )
        val snapshot = stats(
            volunteers = listOf(novaVolunteer, otherVolunteer, veteranVolunteer),
            jobs = jobs,
        )
        val byKey = snapshot.rankPie.associate { it.key to it.quantity }
        assertEquals(1, byKey[VolunteerDashboardStats.RANK_NOVA])
        assertEquals(2, byKey[VolunteerDashboardStats.RANK_OTHER])
        assertTrue(VolunteerDashboardStats.RANK_VETERAN !in byKey)
    }

    @Test
    fun rankKeyMapsSpecialAndEtoileToOther() {
        assertEquals(VolunteerDashboardStats.RANK_OTHER, VolunteerDashboardStats.rankKey(null))
        assertEquals(VolunteerDashboardStats.RANK_OTHER, VolunteerDashboardStats.rankKey(VolunteerRank.SPECIAL))
        assertEquals(VolunteerDashboardStats.RANK_OTHER, VolunteerDashboardStats.rankKey(VolunteerRank.ETOILE))
        assertEquals(VolunteerDashboardStats.RANK_NOVA, VolunteerDashboardStats.rankKey(VolunteerRank.NOVA))
        assertEquals(VolunteerDashboardStats.RANK_VETERAN, VolunteerDashboardStats.rankKey(VolunteerRank.VETERAN))
    }

    @Test
    fun recencyHistogramUsesLastJobNotStoredFlag() {
        val recent = volunteer("recent")
        val medium = volunteer("medium")
        val old = volunteer("old")
        val inactive = volunteer("inactive")
        val never = volunteer("never")
        val jobs = listOf(
            job(recent.id, now - 10 * dayMs),
            job(medium.id, now - 40 * dayMs),
            job(old.id, now - 200 * dayMs),
            job(inactive.id, now - 400 * dayMs),
        )
        val snapshot = stats(
            volunteers = listOf(recent, medium, old, inactive, never),
            jobs = jobs,
        )
        val byKey = snapshot.recencyHistogram.associate { it.key to it.quantity }
        assertEquals(1, byKey[VolunteerDashboardStats.RECENCY_D0_30])
        assertEquals(1, byKey[VolunteerDashboardStats.RECENCY_D30_90])
        assertEquals(1, byKey[VolunteerDashboardStats.RECENCY_D90_365])
        assertEquals(2, byKey[VolunteerDashboardStats.RECENCY_INACTIVE])
    }

    @Test
    fun shiftTimePieIgnoresMeetingJobsAndCountsProfited() {
        val volunteer = volunteer("v1")
        val jobs = listOf(
            job(volunteer.id, start + dayMs + 1_000, shiftTime = ShiftTime.BEFORE_MIDNIGHT),
            job(volunteer.id, start + dayMs + 2_000, shiftTime = ShiftTime.AFTER_MIDNIGHT),
            job(
                volunteer.id,
                start + dayMs + 3_000,
                typeName = "Meeting",
                shiftTime = ShiftTime.AFTER_MIDNIGHT,
            ),
        )
        val snapshot = stats(volunteers = listOf(volunteer), jobs = jobs)
        val byKey = snapshot.shiftTimePie.associate { it.key to it.quantity }
        assertEquals(1, byKey[VolunteerDashboardStats.SHIFT_PROFITED])
        assertEquals(1, byKey[VolunteerDashboardStats.SHIFT_NON_PROFITED])
        assertEquals(1.0, snapshot.shiftTimeProfitedOverTime.sumOf { it.value })
        assertEquals(1.0, snapshot.shiftTimeNonProfitedOverTime.sumOf { it.value })
    }

    @Test
    fun jobTypeSeriesKeepsTopTypesAndAddsTotal() {
        val volunteer = volunteer("v1")
        val extraConfigs = (1..8).map { index ->
            JobTypeConfig(name = "Post$index", isShiftJob = true, isActive = true)
        }
        val jobs = extraConfigs.flatMapIndexed { index, config ->
            List(8 - index) { slot ->
                job(volunteer.id, start + dayMs + slot * 1_000L + index, typeName = config.name)
            }
        }
        val snapshot = stats(
            volunteers = listOf(volunteer),
            jobs = jobs,
            configs = extraConfigs,
        )
        val keys = snapshot.shiftsByJobType.map { it.key }
        assertEquals(VolunteerDashboardStats.TOP_JOB_TYPES + 2, keys.size)
        assertEquals(VolunteerDashboardStats.OTHER_KEY, keys[keys.lastIndex - 1])
        assertEquals(VolunteerDashboardStats.TOTAL_KEY, keys.last())
        assertEquals(jobs.size.toDouble(), snapshot.shiftsByJobType.last().points.sumOf { it.value })
    }

    @Test
    fun nfcEnrollmentSkipsTemporaryAndVolunteerBenefitGuests() {
        val wallNow = System.currentTimeMillis()
        val active = volunteer("active", nfc = "UID-1")
        val inactive = volunteer("inactive", nfc = "UID-2")
        val guests = listOf(
            guest("perm-nfc", nfc = "G1"),
            guest("perm-none"),
            guest("temp", temporary = true, nfc = "T1"),
            guest("vb", volunteerBenefit = true, nfc = "V1"),
        )
        val snapshot = VolunteerDashboardStats.build(
            volunteers = listOf(active, inactive),
            jobs = listOf(job(active.id, wallNow - dayMs)),
            guests = guests,
            jobTypeConfigs = listOf(barConfig()),
            startTime = wallNow - 7 * dayMs,
            endTime = wallNow,
            aggregationMs = aggregation,
            now = wallNow,
        )
        assertEquals(1, snapshot.nfcEnrollment.volunteerTotal)
        assertEquals(1, snapshot.nfcEnrollment.volunteerEnrolled)
        assertEquals(2, snapshot.nfcEnrollment.guestTotal)
        assertEquals(1, snapshot.nfcEnrollment.guestEnrolled)
        assertEquals(50.0, snapshot.nfcEnrollment.guestPercent)
    }

    @Test
    fun futureEntryPoolSumsRemainingOnStartedShiftDays() {
        val volunteer = volunteer("v1")
        val jobDate = now - 3 * 60 * 60 * 1000
        val jobs = listOf(
            job(volunteer.id, jobDate, remaining = 2, shiftTime = ShiftTime.AFTER_MIDNIGHT),
            job(volunteer.id, now + 2 * dayMs, remaining = 5, shiftTime = ShiftTime.AFTER_MIDNIGHT),
        )
        val snapshot = stats(volunteers = listOf(volunteer), jobs = jobs)
        assertEquals(2, snapshot.futureEntryPool)
    }

    @Test
    fun temporaryGuestsCollapseAfterTopEight() {
        val guests = (1..10).map { index ->
            guest(
                name = "g$index",
                temporary = true,
                artist = "Artist$index",
                eventDate = now + index * dayMs,
            )
        } + guest("g-extra", temporary = true, artist = "Artist1", eventDate = now + dayMs)
        val snapshot = stats(volunteers = emptyList(), jobs = emptyList(), guests = guests)
        assertEquals(VolunteerDashboardStats.TOP_TEMPORARY_EVENTS + 1, snapshot.temporaryGuestsByEvent.size)
        assertTrue(snapshot.temporaryGuestsByEvent.last().isOther)
        assertEquals(2, snapshot.temporaryGuestsByEvent.last().count)
        assertEquals(2, snapshot.temporaryGuestsByEvent.first { it.artistName == "Artist1" }.count)
    }
}
