package com.eventmanager.app.ui.utils
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eventmanager.app.data.utils.AppTimeZone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

val GuestListDefaultZoneId: ZoneId = AppTimeZone.zoneId

@Composable
fun rememberGuestListEffectiveToday(
    zone: ZoneId = GuestListDefaultZoneId,
    offsetHours: Int
): LocalDate {
    var now by remember(zone) { mutableStateOf(LocalDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = LocalDateTime.now(zone)
            delay(60_000L)
        }
    }
    return remember(now, offsetHours) {
        now.minusHours(offsetHours.toLong()).toLocalDate()
    }
}
