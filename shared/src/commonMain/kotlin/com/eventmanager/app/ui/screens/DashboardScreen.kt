package com.eventmanager.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.GuestListOccupancy
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.*
import com.eventmanager.app.ui.utils.GuestListDefaultZoneId
import com.eventmanager.app.ui.utils.rememberGuestListEffectiveToday
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun DashboardScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    venues: List<VenueEntity> = emptyList(),
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    viewModel: EventManagerViewModel? = null,
    isPhone: Boolean = false,
    onLogout: () -> Unit = {},
    onOpenPosReport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    val isPeopleCounterVisible = settingsManager.isPeopleCounterVisible()
    val isStatisticsVisible = settingsManager.isStatisticsVisible()
    val dateChangeOffsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val guestListZone = GuestListDefaultZoneId
    val guestListEffectiveToday = rememberGuestListEffectiveToday(
        zone = guestListZone,
        offsetHours = dateChangeOffsetHours
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (isPhone) 12.dp else 16.dp)
    ) {
        DashboardClockCard(
            settingsManager = settingsManager,
            isPhone = isPhone,
        )
        Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))

        val occupancy = remember(
            guests,
            volunteers,
            jobs,
            jobTypeConfigs,
            dateChangeOffsetHours,
            guestListEffectiveToday,
        ) {
            GuestListOccupancy.snapshot(
                guests = guests,
                volunteers = volunteers,
                jobs = jobs,
                jobTypeConfigs = jobTypeConfigs,
                currentTime = System.currentTimeMillis(),
                offsetHours = dateChangeOffsetHours,
                zone = guestListZone,
                isTemporaryOnList = { it == guestListEffectiveToday },
            )
        }
        val permanentGuestCount = occupancy.permanentGuests
        val temporaryGuestCount = occupancy.temporaryGuests

        val (totalVolunteers, activeVolunteersCount, inactiveVolunteersCount) = remember(volunteers, jobs) {
            var active = 0
            var inactive = 0
            val jobsByVolunteer = VolunteerActivityManager.groupJobsByVolunteerId(jobs)
            volunteers.forEach { volunteer ->
                if (VolunteerActivityManager.isVolunteerActive(volunteer, jobsByVolunteer[volunteer.id])) {
                    active++
                } else {
                    inactive++
                }
            }
            Triple(volunteers.size, active, inactive)
        }

        val totalPeople = occupancy.totalList
        val totalFreeDrinks = remember(volunteers, jobs, jobTypeConfigs, dateChangeOffsetHours) {
            BenefitCalculator.calculateTotalFreeDrinks(
                volunteers = volunteers,
                jobs = jobs,
                jobTypeConfigs = jobTypeConfigs,
                offsetHours = dateChangeOffsetHours
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)) {
                StatCardV2(
                    title = stringResource(Res.string.permanent_guests),
                    value = permanentGuestCount.toString(),
                    icon = Icons.Default.Person,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                StatCardV2(
                    title = stringResource(Res.string.volunteers_total),
                    value = totalVolunteers.toString(),
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)) {
                StatCardV2(
                    title = stringResource(Res.string.filter_temporary_guests),
                    value = temporaryGuestCount.toString(),
                    icon = Icons.Default.Event,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                StatCardV2(
                    title = stringResource(Res.string.total_people),
                    value = totalPeople.toString(),
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }
            VolunteerActiveInactiveCard(
                activeCount = activeVolunteersCount,
                inactiveCount = inactiveVolunteersCount,
                isPhone = isPhone
            )
            StatCardV2(
                title = stringResource(Res.string.free_drinks_today),
                value = totalFreeDrinks.toString(),
                icon = Icons.Default.LocalBar,
                modifier = Modifier.fillMaxWidth(),
                isPhone = isPhone
            )
        }

        Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))

        LogoutCard(isPhone = isPhone, onLogout = onLogout)

        if (isPeopleCounterVisible) {
            Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))
            viewModel?.let { PeopleCounter(isPhone = isPhone, viewModel = it) }
        }

        viewModel?.let { vm ->
            Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))
            SendAnnouncementButton(isPhone = isPhone, onClick = { vm.openSendAnnouncementDialog() })
        }

        Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))
        PosAccountingReportEntryCard(
            onClick = onOpenPosReport,
            isPhone = isPhone,
        )

        if (isStatisticsVisible) {
            Spacer(Modifier.height(if (isPhone) 16.dp else 24.dp))
            val accountTransfers = viewModel?.let { it.accountTransfers.collectAsState().value } ?: emptyList()
            val salesSheetItems = viewModel?.let { it.salesSheetItems.collectAsState().value } ?: emptyList()
            StatsGraphsPanel(
                platformContext = platformContext,
                guests = guests,
                volunteers = volunteers,
                jobs = jobs,
                venues = venues,
                jobTypeConfigs = jobTypeConfigs,
                isPhone = isPhone,
                accountTransfers = accountTransfers,
                salesSheetItems = salesSheetItems,
            )
        }

        Spacer(Modifier.height(if (isPhone) 80.dp else 100.dp))
    }
}

@Composable
private fun VolunteerActiveInactiveCard(activeCount: Int, inactiveCount: Int, isPhone: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isPhone) 140.dp else 160.dp)
            .then(
                if (!isPhone) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPhone) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPhone) 2.dp else 6.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(if (isPhone) 14.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VolunteerStatHalf(
                count = activeCount,
                label = stringResource(Res.string.active_volunteers),
                icon = Icons.Default.CheckCircle,
                isPhone = isPhone,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                Modifier.height(if (isPhone) 80.dp else 100.dp).width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            VolunteerStatHalf(
                count = inactiveCount,
                label = stringResource(Res.string.inactive_volunteers),
                icon = Icons.Default.Warning,
                isPhone = isPhone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VolunteerStatHalf(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPhone: Boolean,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier.size(if (isPhone) 36.dp else 44.dp)
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(if (isPhone) 18.dp else 22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(if (isPhone) 8.dp else 10.dp))
        Text(
            text = count.toString(),
            style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(if (isPhone) 4.dp else 6.dp))
        Text(
            text = label,
            style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun LogoutCard(isPhone: Boolean, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_logout),
            style = if (isPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.settings_logout_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.settings_logout))
        }
    }
}
