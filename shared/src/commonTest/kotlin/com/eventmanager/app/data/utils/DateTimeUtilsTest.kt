package com.eventmanager.app.data.utils

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeUtilsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Zurich")

    private fun zurichMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun getStartOfDayWithOffset_usesZurichNotUtc() {
        // 02:30 on 1 Feb 2026 in Zurich — with UTC host TZ this used to roll back to 31 Jan.
        val instant = zurichMillis(2026, 2, 1, 2, 30)
        val start = DateTimeUtils.getStartOfDayWithOffset(instant, offsetHours = 0)
        val cal = Calendar.getInstance(AppTimeZone.java).apply { timeInMillis = start }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun getStartOfDayWithOffset_appliesVenueOffset() {
        val instant = zurichMillis(2026, 2, 1, 4, 0)
        val start = DateTimeUtils.getStartOfDayWithOffset(instant, offsetHours = 3)
        val cal = Calendar.getInstance(AppTimeZone.java).apply { timeInMillis = start }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(3, cal.get(Calendar.HOUR_OF_DAY))
    }
}
