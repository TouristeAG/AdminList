package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.utils.TemporaryGuestBucket
import com.eventmanager.app.data.utils.VolunteerDashboardSnapshot
import com.eventmanager.app.data.utils.VolunteerDashboardStats
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.pos_stats_other_products
import com.eventmanager.app.resources.shift_time_evening_non_profited
import com.eventmanager.app.resources.shift_time_evening_profited
import com.eventmanager.app.resources.stats_future_entry_pool
import com.eventmanager.app.resources.stats_future_entry_pool_empty
import com.eventmanager.app.resources.stats_future_entry_pool_subtitle
import com.eventmanager.app.resources.stats_nfc_enrollment
import com.eventmanager.app.resources.stats_nfc_enrollment_description
import com.eventmanager.app.resources.stats_nfc_enrollment_empty
import com.eventmanager.app.resources.stats_nfc_enrollment_subtitle
import com.eventmanager.app.resources.stats_nfc_enrollment_value
import com.eventmanager.app.resources.stats_no_volunteer_data
import com.eventmanager.app.resources.stats_rank_distribution
import com.eventmanager.app.resources.stats_rank_distribution_description
import com.eventmanager.app.resources.stats_rank_galaxie
import com.eventmanager.app.resources.stats_rank_nova
import com.eventmanager.app.resources.stats_rank_orion
import com.eventmanager.app.resources.stats_rank_other
import com.eventmanager.app.resources.stats_rank_over_time
import com.eventmanager.app.resources.stats_rank_over_time_description
import com.eventmanager.app.resources.stats_rank_veteran
import com.eventmanager.app.resources.stats_recency_0_30
import com.eventmanager.app.resources.stats_recency_30_90
import com.eventmanager.app.resources.stats_recency_90_365
import com.eventmanager.app.resources.stats_recency_inactive
import com.eventmanager.app.resources.stats_shift_recency
import com.eventmanager.app.resources.stats_shift_recency_description
import com.eventmanager.app.resources.stats_shift_time_over_time
import com.eventmanager.app.resources.stats_shift_time_over_time_description
import com.eventmanager.app.resources.stats_shift_time_split
import com.eventmanager.app.resources.stats_shift_time_split_description
import com.eventmanager.app.resources.stats_shifts_by_job_type
import com.eventmanager.app.resources.stats_shifts_by_job_type_description
import com.eventmanager.app.resources.stats_temporary_guests
import com.eventmanager.app.resources.stats_temporary_guests_description
import com.eventmanager.app.resources.total
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RankColors = mapOf(
    VolunteerDashboardStats.RANK_NOVA to Color(0xFF5B8FF9),
    VolunteerDashboardStats.RANK_GALAXIE to Color(0xFFF6BD16),
    VolunteerDashboardStats.RANK_ORION to Color(0xFF9270CA),
    VolunteerDashboardStats.RANK_VETERAN to Color(0xFF5AD8A6),
    VolunteerDashboardStats.RANK_OTHER to Color(0xFFB8B8B8),
)

@Composable
fun VolunteerExtraGraphs(
    stats: VolunteerDashboardSnapshot,
    timePeriod: TimePeriod,
    isPhone: Boolean,
    now: Long,
) {
    val emptyText = stringResource(Res.string.stats_no_volunteer_data)
    val otherLabel = stringResource(Res.string.pos_stats_other_products)
    val totalLabel = stringResource(Res.string.total)
    val novaLabel = stringResource(Res.string.stats_rank_nova)
    val galaxieLabel = stringResource(Res.string.stats_rank_galaxie)
    val orionLabel = stringResource(Res.string.stats_rank_orion)
    val veteranLabel = stringResource(Res.string.stats_rank_veteran)
    val rankOtherLabel = stringResource(Res.string.stats_rank_other)
    val recency030 = stringResource(Res.string.stats_recency_0_30)
    val recency3090 = stringResource(Res.string.stats_recency_30_90)
    val recency90365 = stringResource(Res.string.stats_recency_90_365)
    val recencyInactive = stringResource(Res.string.stats_recency_inactive)
    val profitedLabel = stringResource(Res.string.shift_time_evening_profited)
    val nonProfitedLabel = stringResource(Res.string.shift_time_evening_non_profited)

    fun rankLabel(key: String): String = when (key) {
        VolunteerDashboardStats.RANK_NOVA -> novaLabel
        VolunteerDashboardStats.RANK_GALAXIE -> galaxieLabel
        VolunteerDashboardStats.RANK_ORION -> orionLabel
        VolunteerDashboardStats.RANK_VETERAN -> veteranLabel
        else -> rankOtherLabel
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatsPieCard(
            title = stringResource(Res.string.stats_rank_distribution),
            description = stringResource(Res.string.stats_rank_distribution_description),
            icon = Icons.Default.MilitaryTech,
            slices = stats.rankPie.toPieSlices(
                colorFor = { item, _ -> RankColors[item.key] ?: SlicePalette.last() },
                labelFor = { rankLabel(it.key) },
                detailFor = { item, pct -> "${item.quantity} (${formatPct(pct)})" },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )
        val rankSeries = listOf(
            Triple(novaLabel, stats.rankNovaOverTime.toGraphPoints(timePeriod, now), RankColors.getValue(VolunteerDashboardStats.RANK_NOVA)),
            Triple(galaxieLabel, stats.rankGalaxieOverTime.toGraphPoints(timePeriod, now), RankColors.getValue(VolunteerDashboardStats.RANK_GALAXIE)),
            Triple(orionLabel, stats.rankOrionOverTime.toGraphPoints(timePeriod, now), RankColors.getValue(VolunteerDashboardStats.RANK_ORION)),
        )
        if (rankSeries.any { it.second.size >= 2 }) {
            MultiLineGraph(
                label = stringResource(Res.string.stats_rank_over_time),
                description = stringResource(Res.string.stats_rank_over_time_description),
                seriesData = rankSeries,
                timePeriod = timePeriod,
                isPhone = isPhone,
            )
        }

        val jobTypeSeries = stats.shiftsByJobType.mapIndexed { index, series ->
            val label = when (series.key) {
                VolunteerDashboardStats.OTHER_KEY -> otherLabel
                VolunteerDashboardStats.TOTAL_KEY -> totalLabel
                else -> series.key
            }
            val color = if (series.key == VolunteerDashboardStats.TOTAL_KEY) {
                MaterialTheme.colorScheme.primary
            } else {
                SlicePalette[index % SlicePalette.size]
            }
            Triple(label, series.points.toGraphPoints(timePeriod, now), color)
        }
        if (jobTypeSeries.any { it.second.size >= 2 }) {
            MultiLineGraph(
                label = stringResource(Res.string.stats_shifts_by_job_type),
                description = stringResource(Res.string.stats_shifts_by_job_type_description),
                seriesData = jobTypeSeries,
                timePeriod = timePeriod,
                isPhone = isPhone,
            )
        }

        StatsBarCard(
            title = stringResource(Res.string.stats_shift_recency),
            description = stringResource(Res.string.stats_shift_recency_description),
            icon = Icons.Default.History,
            bars = stats.recencyHistogram.toBarItems(
                colorFor = { _, index -> SlicePalette[index % SlicePalette.size] },
                labelFor = { item ->
                    when (item.key) {
                        VolunteerDashboardStats.RECENCY_D0_30 -> recency030
                        VolunteerDashboardStats.RECENCY_D30_90 -> recency3090
                        VolunteerDashboardStats.RECENCY_D90_365 -> recency90365
                        else -> recencyInactive
                    }
                },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )

        StatsPieCard(
            title = stringResource(Res.string.stats_shift_time_split),
            description = stringResource(Res.string.stats_shift_time_split_description),
            icon = Icons.Default.Nightlife,
            slices = stats.shiftTimePie.toPieSlices(
                colorFor = { item, _ ->
                    if (item.key == VolunteerDashboardStats.SHIFT_PROFITED) SlicePalette[0] else SlicePalette[1]
                },
                labelFor = { item ->
                    if (item.key == VolunteerDashboardStats.SHIFT_PROFITED) profitedLabel else nonProfitedLabel
                },
                detailFor = { item, pct -> "${item.quantity} (${formatPct(pct)})" },
            ),
            isPhone = isPhone,
            emptyText = emptyText,
        )
        val shiftTimeSeries = listOf(
            Triple(profitedLabel, stats.shiftTimeProfitedOverTime.toGraphPoints(timePeriod, now), SlicePalette[0]),
            Triple(nonProfitedLabel, stats.shiftTimeNonProfitedOverTime.toGraphPoints(timePeriod, now), SlicePalette[1]),
        )
        if (shiftTimeSeries.any { it.second.size >= 2 }) {
            MultiLineGraph(
                label = stringResource(Res.string.stats_shift_time_over_time),
                description = stringResource(Res.string.stats_shift_time_over_time_description),
                seriesData = shiftTimeSeries,
                timePeriod = timePeriod,
                isPhone = isPhone,
            )
        }

        val nfc = stats.nfcEnrollment
        PosHighlightCard(
            title = stringResource(Res.string.stats_nfc_enrollment),
            icon = Icons.Default.Nfc,
            isPhone = isPhone,
            value = if (nfc.volunteerTotal + nfc.guestTotal > 0) {
                stringResource(
                    Res.string.stats_nfc_enrollment_value,
                    formatPct(nfc.volunteerPercent.toFloat()),
                    formatPct(nfc.guestPercent.toFloat()),
                )
            } else {
                null
            },
            subtitle = if (nfc.volunteerTotal + nfc.guestTotal > 0) {
                stringResource(
                    Res.string.stats_nfc_enrollment_subtitle,
                    nfc.volunteerEnrolled,
                    nfc.volunteerTotal,
                    nfc.guestEnrolled,
                    nfc.guestTotal,
                )
            } else {
                stringResource(Res.string.stats_nfc_enrollment_description)
            },
            emptyText = stringResource(Res.string.stats_nfc_enrollment_empty),
        )
    }
}

@Composable
fun GuestListExtraGraphs(
    stats: VolunteerDashboardSnapshot,
    isPhone: Boolean,
) {
    val emptyText = stringResource(Res.string.stats_no_volunteer_data)
    val otherLabel = stringResource(Res.string.pos_stats_other_products)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PosHighlightCard(
            title = stringResource(Res.string.stats_future_entry_pool),
            icon = Icons.Default.ConfirmationNumber,
            isPhone = isPhone,
            value = stats.futureEntryPool.toString(),
            subtitle = stringResource(Res.string.stats_future_entry_pool_subtitle),
            emptyText = stringResource(Res.string.stats_future_entry_pool_empty),
        )
        StatsBarCard(
            title = stringResource(Res.string.stats_temporary_guests),
            description = stringResource(Res.string.stats_temporary_guests_description),
            icon = Icons.Default.TheaterComedy,
            bars = stats.temporaryGuestsByEvent.mapIndexed { index, bucket ->
                StatsBarItem(
                    label = temporaryGuestLabel(bucket, otherLabel),
                    value = bucket.count.toFloat(),
                    color = SlicePalette[index % SlicePalette.size],
                    count = bucket.count,
                )
            },
            isPhone = isPhone,
            emptyText = emptyText,
        )
    }
}

private fun temporaryGuestLabel(bucket: TemporaryGuestBucket, otherLabel: String): String {
    if (bucket.isOther) return otherLabel
    val date = bucket.eventDate?.takeIf { it > 0L }?.let { millis ->
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
    }.orEmpty()
    val artist = bucket.artistName.ifBlank { "—" }
    return if (date.isBlank()) artist else "$artist\n$date"
}
