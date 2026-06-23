package com.eventmanager.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.ui.utils.GuestListDefaultZoneId
import com.eventmanager.app.ui.utils.isTablet
import com.eventmanager.app.ui.utils.rememberGuestListEffectiveToday
import com.eventmanager.app.ui.StatCardV2
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleterieHomeScreen(
    guests: List<Guest>,
    repository: EventManagerRepository,
    viewModel: EventManagerViewModel,
    dashboardScrollState: ScrollState,
    onBack: () -> Unit,
    onOpenGuestList: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { settingsManagerFor(context) }
    val isPhone = !isTablet()
    val dateChangeOffsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val guestListZone = GuestListDefaultZoneId
    val guestListEffectiveToday = rememberGuestListEffectiveToday(
        zone = guestListZone,
        offsetHours = dateChangeOffsetHours
    )

    val (permanentGuestCount, temporaryGuestCount) = remember(guests, guestListEffectiveToday) {
        var permanent = 0
        var temporary = 0
        guests.forEach { guest ->
            when {
                guest.isTemporaryGuest -> {
                    val ts = guest.temporaryEventDate ?: return@forEach
                    val eventDate = java.time.Instant.ofEpochMilli(ts)
                        .atZone(guestListZone)
                        .toLocalDate()
                    if (eventDate == guestListEffectiveToday) temporary++
                }
                guest.isVolunteerBenefit -> { }
                else -> permanent++
            }
        }
        permanent to temporary
    }

    val volunteersOnList = remember(guests) {
        guests.count { it.isVolunteerBenefit }
    }

    val totalWithoutInvites = permanentGuestCount + temporaryGuestCount + volunteersOnList

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billeterie", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.setup_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = context.getString(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(dashboardScrollState)
                .padding(if (isPhone) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                StatCardV2(
                    title = context.getString(R.string.billeterie_stat_permanent_guests),
                    value = permanentGuestCount.toString(),
                    icon = Icons.Default.Person,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                StatCardV2(
                    title = context.getString(R.string.billeterie_stat_temporary_guests_today),
                    value = temporaryGuestCount.toString(),
                    icon = Icons.Default.Event,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                StatCardV2(
                    title = context.getString(R.string.billeterie_stat_volunteers_on_list),
                    value = volunteersOnList.toString(),
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                StatCardV2(
                    title = context.getString(R.string.billeterie_stat_total_without_invites),
                    value = totalWithoutInvites.toString(),
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenGuestList,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.billeterie_button_guest_list), fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onOpenScanner,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.billeterie_button_scanner), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleterieSettingsScreen(viewModel: EventManagerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.setup_back))
                    }
                }
            )
        }
    ) { padding ->
        SettingsScreen(
            viewModel = viewModel,
            variant = SettingsScreenVariant.BilleterieBasic,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
