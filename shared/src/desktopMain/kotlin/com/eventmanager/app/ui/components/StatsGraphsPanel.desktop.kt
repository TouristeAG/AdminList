package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.utils.GraphExportUtils

@Composable
actual fun StatsGraphsPanel(
    platformContext: PlatformContext,
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    venues: List<VenueEntity>,
    jobTypeConfigs: List<JobTypeConfig>,
    isPhone: Boolean,
    modifier: Modifier
) {
    val settings = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
    var period by remember { mutableStateOf(TimePeriod.ONE_MONTH) }

    Column(modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Statistics & Trends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePeriod.entries.take(5).forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p; settings.saveSelectedGraphTimePeriod(p.name) },
                    label = { Text(p.displayName) }
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Guests: ${guests.size} · Volunteers: ${volunteers.size} · Jobs: ${jobs.size}")
                Text("Period: ${period.displayName}", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = {
            runCatching {
                GraphExportUtils.exportToXLSX(
                    platformContext, "NoctuList Stats", emptyList(), emptyList(), period, null
                )
            }
        }) { Text("Export XLSX") }
        OutlinedButton(onClick = {
            runCatching {
                GraphExportUtils.exportToJPG(
                    platformContext, "NoctuList Stats", emptyList(), emptyList(), period
                )
            }
        }) { Text("Export JPG") }
    }
}
