package com.eventmanager.app.data.utils

import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * Canonical venue timezone for NoctuList (Geneva / Zurich operations).
 *
 * Many JVM date APIs default to the host OS timezone. On desktop that is often UTC,
 * which shifts displayed dates and day-boundary math (guest list, benefits, POS, sync labels).
 */
object AppTimeZone {
    const val ID: String = "Europe/Zurich"

    val zoneId: ZoneId = ZoneId.of(ID)
    val java: TimeZone = TimeZone.getTimeZone(ID)

    fun calendar(): Calendar = Calendar.getInstance(java)

    /** Align JVM default timezone with venue time so legacy [Calendar.getInstance] calls stay correct. */
    fun installAsJvmDefault() {
        if (TimeZone.getDefault().id != java.id) {
            TimeZone.setDefault(java)
        }
    }
}
