package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    isPhone: Boolean,
    trailingContent: @Composable (() -> Unit)? = null,
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

    val shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp)
    val contentPadding = if (isPhone) 16.dp else 20.dp

    // Single flat container — Material Card elevation/tonal layers read as a nested box on Desktop.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(if (isPhone) 32.dp else 40.dp)
                    .padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = now.format(timeFormatter),
                        style = if (isPhone) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    trailingContent?.invoke()
                }
                Text(
                    text = effectiveToday.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                Text(
                    text = calculationTimeInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
