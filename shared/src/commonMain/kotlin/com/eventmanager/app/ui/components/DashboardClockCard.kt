package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.utils.GuestListDefaultZoneId
import com.eventmanager.app.ui.utils.rememberGuestListEffectiveToday
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun DashboardClockCard(
    settingsManager: SettingsManager,
    isPhone: Boolean
) {
    val zone = GuestListDefaultZoneId
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val timeFormatPattern = settingsManager.getTimeFormat()
    val dateFormatPattern = settingsManager.getDateFormat()
    val effectiveToday = rememberGuestListEffectiveToday(zone = zone, offsetHours = offsetHours)
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(1_000L)
        }
    }

    val locale = Locale.getDefault()
    val timeFormatter = remember(timeFormatPattern, locale) {
        DateTimeFormatter.ofPattern(timeFormatPattern, locale)
    }
    val dateFormatter = remember(dateFormatPattern, locale) {
        DateTimeFormatter.ofPattern(dateFormatPattern, locale)
    }
    val effectiveNow = remember(now, offsetHours) {
        now.toLocalDateTime().minusHours(offsetHours.toLong())
    }
    val effectiveTimeText = effectiveNow.format(timeFormatter)
    val offsetValueText = when (offsetHours) {
        0 -> stringResource(Res.string.dashboard_date_offset_zero)
        else -> {
            val signedHours = if (offsetHours > 0) "+$offsetHours" else offsetHours.toString()
            stringResource(Res.string.dashboard_date_offset_hours, signedHours)
        }
    }
    val calculationTimeInfo = stringResource(
        Res.string.dashboard_calculation_time_info,
        effectiveTimeText,
        offsetValueText
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(if (isPhone) 32.dp else 40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = now.format(timeFormatter),
                    style = if (isPhone) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = effectiveToday.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    text = calculationTimeInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
