package com.eventmanager.app.ui.components

import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.Volunteer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object StatsGraphDataBuilder {
    fun buildGuestTrend(guests: List<Guest>, period: TimePeriod): List<DataPoint> =
        bucketByPeriod(guests.map { it.lastModified to 1f }, period)

    fun buildVolunteerTrend(volunteers: List<Volunteer>, period: TimePeriod): List<DataPoint> =
        bucketByPeriod(volunteers.map { it.lastModified to 1f }, period)

    fun buildJobTrend(jobs: List<Job>, period: TimePeriod): List<DataPoint> =
        bucketByPeriod(jobs.map { it.date to 1f }, period)

    fun buildSummaryPoints(guests: List<Guest>, volunteers: List<Volunteer>, jobs: List<Job>): List<DataPoint> =
        listOf(
            DataPoint("Guests", guests.size.toFloat(), System.currentTimeMillis()),
            DataPoint("Volunteers", volunteers.size.toFloat(), System.currentTimeMillis()),
            DataPoint("Jobs", jobs.size.toFloat(), System.currentTimeMillis())
        )

    private fun bucketByPeriod(entries: List<Pair<Long, Float>>, period: TimePeriod): List<DataPoint> {
        if (entries.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val startMs = if (period == TimePeriod.MAX) entries.minOf { it.first }
        else now - period.days * 24L * 60 * 60 * 1000
        val filtered = entries.filter { it.first >= startMs }
        val fmt = SimpleDateFormat(
            when (period) {
                TimePeriod.SIX_MONTHS, TimePeriod.ONE_YEAR, TimePeriod.MAX -> "MMM yyyy"
                else -> "MMM d"
            },
            Locale.getDefault()
        )
        val buckets = linkedMapOf<String, Float>()
        filtered.forEach { (ts, value) ->
            val label = fmt.format(Date(ts))
            buckets[label] = (buckets[label] ?: 0f) + value
        }
        return buckets.entries.mapIndexed { index, (label, value) ->
            DataPoint(label, value, startMs + index)
        }
    }
}
