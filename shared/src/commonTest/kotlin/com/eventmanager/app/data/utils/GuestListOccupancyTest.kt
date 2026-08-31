package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobType
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.NovaJobType
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.data.models.Volunteer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class GuestListOccupancyTest {

    private val zone: ZoneId = ZoneId.of("Europe/Zurich")
    private val dayMs = 24L * 60 * 60 * 1000

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun volunteer(id: String = "volunteer-x") = Volunteer(
        id = id,
        name = "X",
        lastNameAbbreviation = "X",
        email = "x@example.com",
        phoneNumber = "",
    )

    private fun barConfig() = JobTypeConfig(
        name = "Bar",
        isShiftJob = true,
        novaJobType = NovaJobType.DEFAULT_SHIFT,
    )

    private fun shift(volunteerId: String, date: Long) = Job(
        volunteerId = volunteerId,
        jobType = JobType.BAR,
        jobTypeName = "Bar",
        venueName = "Main",
        date = date,
        shiftTime = ShiftTime.BEFORE_MIDNIGHT,
    )

    private fun permanentGuest(name: String, invitations: Int) = Guest(
        name = name,
        invitations = invitations,
        venueName = "Main",
    )

    private fun temporaryGuest(name: String, invitations: Int, eventDate: Long) = Guest(
        name = name,
        invitations = invitations,
        venueName = "Main",
        isTemporaryGuest = true,
        temporaryEventDate = eventDate,
    )

    private fun volunteerBenefitRow(volunteerId: String, invitations: Int) = Guest(
        name = "X",
        invitations = invitations,
        venueName = "Main",
        isVolunteerBenefit = true,
        volunteerId = volunteerId,
    )

    @Test
    fun novaShiftOnFebruarySecondGrantsOneFriendInviteThatDay() {
        val volunteer = volunteer()
        val jobDate = at(2026, 2, 2, 22, 0)
        val jobs = listOf(shift(volunteer.id, jobDate))
        val configs = listOf(barConfig())
        val feb2End = DateTimeUtils.getEndOfDayWithOffset(at(2026, 2, 2, 12), 0).timeInMillis
        val feb1End = DateTimeUtils.getEndOfDayWithOffset(at(2026, 2, 1, 12), 0).timeInMillis

        val onShiftDay = GuestListOccupancy.snapshot(
            guests = emptyList(),
            volunteers = listOf(volunteer),
            jobs = jobs,
            jobTypeConfigs = configs,
            currentTime = feb2End,
            offsetHours = 0,
            zone = zone,
            isTemporaryOnList = { false },
        )
        assertEquals(1, onShiftDay.volunteersOnList)
        assertEquals(1, onShiftDay.volunteerFriendInvites)
        assertEquals(2, onShiftDay.totalList)

        val dayBefore = GuestListOccupancy.snapshot(
            guests = emptyList(),
            volunteers = listOf(volunteer),
            jobs = jobs,
            jobTypeConfigs = configs,
            currentTime = feb1End,
            offsetHours = 0,
            zone = zone,
            isTemporaryOnList = { false },
        )
        assertEquals(0, dayBefore.volunteersOnList)
        assertEquals(0, dayBefore.volunteerFriendInvites)
    }

    @Test
    fun invitationSeriesCountsEveningShiftOnItsCalendarDay() {
        val volunteer = volunteer()
        val jobDate = at(2026, 2, 2, 22, 0)
        val series = GuestListOccupancy.historicalSeries(
            guests = emptyList(),
            volunteers = listOf(volunteer),
            jobs = listOf(shift(volunteer.id, jobDate)),
            jobTypeConfigs = listOf(barConfig()),
            startTime = at(2026, 2, 1, 12),
            now = at(2026, 2, 3, 12),
            aggregationMs = dayMs,
            offsetHours = 0,
            zone = zone,
        )
        val byDate = series.associateBy { GuestListOccupancy.eventLocalDate(it.timestamp, zone) }
        assertEquals(1, byDate[LocalDate.of(2026, 2, 2)]?.volunteerFriendInvites)
        assertEquals(1, byDate[LocalDate.of(2026, 2, 2)]?.volunteersOnList)
        assertEquals(0, byDate[LocalDate.of(2026, 2, 1)]?.volunteerFriendInvites)
        assertEquals(0, byDate[LocalDate.of(2026, 2, 3)]?.volunteerFriendInvites)
    }

    @Test
    fun totalListAddsGuestAndVolunteerHeadsPlusFriendInvites() {
        val volunteer = volunteer()
        val shiftDay = at(2026, 2, 2, 22, 0)
        val guests = listOf(
            permanentGuest("Pat", invitations = 2),
            temporaryGuest("Tem", invitations = 1, eventDate = at(2026, 2, 2, 20)),
            temporaryGuest("OtherNight", invitations = 9, eventDate = at(2026, 2, 3, 20)),
            volunteerBenefitRow(volunteer.id, invitations = 1),
        )
        val today = LocalDate.of(2026, 2, 2)
        val snap = GuestListOccupancy.snapshot(
            guests = guests,
            volunteers = listOf(volunteer),
            jobs = listOf(shift(volunteer.id, shiftDay)),
            jobTypeConfigs = listOf(barConfig()),
            currentTime = DateTimeUtils.getEndOfDayWithOffset(at(2026, 2, 2, 12), 0).timeInMillis,
            offsetHours = 0,
            zone = zone,
            isTemporaryOnList = { it == today },
        )
        assertEquals(1, snap.permanentGuests)
        assertEquals(2, snap.permanentGuestInvites)
        assertEquals(1, snap.temporaryGuests)
        assertEquals(1, snap.temporaryGuestInvites)
        assertEquals(1, snap.volunteersOnList)
        assertEquals(1, snap.volunteerFriendInvites)
        // 1 perm + 2 invites + 1 temp + 1 invite + 1 volunteer + 1 invite
        assertEquals(7, snap.totalList)
    }

    @Test
    fun historicalTotalIncludesPermanentGuestsEveryDayAndTempsOnEventDay() {
        val volunteer = volunteer()
        val guests = listOf(
            permanentGuest("Pat", invitations = 2),
            temporaryGuest("Tem", invitations = 1, eventDate = at(2026, 2, 2, 20)),
        )
        val series = GuestListOccupancy.historicalSeries(
            guests = guests,
            volunteers = listOf(volunteer),
            jobs = listOf(shift(volunteer.id, at(2026, 2, 2, 22))),
            jobTypeConfigs = listOf(barConfig()),
            startTime = at(2026, 2, 1, 12),
            now = at(2026, 2, 3, 12),
            aggregationMs = dayMs,
            offsetHours = 0,
            zone = zone,
        )
        val byDate = series.associateBy { GuestListOccupancy.eventLocalDate(it.timestamp, zone) }
        val feb1 = byDate.getValue(LocalDate.of(2026, 2, 1))
        val feb2 = byDate.getValue(LocalDate.of(2026, 2, 2))
        assertEquals(1, feb1.guestHeads)
        assertEquals(2, feb1.guestFriendInvites)
        assertEquals(3, feb1.totalList)
        assertEquals(2, feb2.guestHeads)
        assertEquals(3, feb2.guestFriendInvites)
        assertEquals(7, feb2.totalList)
    }
}
