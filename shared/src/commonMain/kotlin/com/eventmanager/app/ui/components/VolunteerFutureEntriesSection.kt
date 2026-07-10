package com.eventmanager.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.data.utils.FutureEntryGroup
import com.eventmanager.app.data.utils.groupFutureEntriesByInvites
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.utils.getPhonePortraitBodyTypography
import com.eventmanager.app.ui.utils.getPhonePortraitTypography
import com.eventmanager.app.ui.utils.getResponsiveBodyTypography
import com.eventmanager.app.ui.utils.isTablet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun VolunteerFutureEntriesSection(
    volunteerJobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    onConfirmEntry: ((Job, Int) -> Unit)?,
    modifier: Modifier = Modifier,
    externalSelectedGroupInvites: Int? = null,
    onExternalGroupInvitesChanged: ((Int) -> Unit)? = null,
    showGroupSelector: Boolean = true,
    hasActiveFreeEntryBenefit: Boolean = false,
    meetingNovaBenefitsExcludedForOrion: Boolean = false,
    hideSecondaryGroupSummary: Boolean = false,
) {
    if (hasActiveFreeEntryBenefit) return

    val platformContext = LocalPlatformContext.current
    val isPhone = !isTablet()
    val isDesktop = platformContext.isDesktop
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    val configsByName = remember(jobTypeConfigs) { jobTypeConfigs.associateBy { it.name } }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }

    val jobsVersion = remember(volunteerJobs) {
        volunteerJobs.fold(0L) { acc, j -> acc + j.lastModified + (j.benefitFutureEntriesRemaining ?: 0) }
    }
    val entryGroups = remember(volunteerJobs, configsByName, offsetHours, jobsVersion, meetingNovaBenefitsExcludedForOrion) {
        groupFutureEntriesByInvites(
            volunteerJobs,
            configsByName,
            offsetHours = offsetHours,
            meetingNovaBenefitsExcludedForOrion = meetingNovaBenefitsExcludedForOrion,
        )
    }

    var internalSelectedGroupInvites by remember { mutableStateOf<Int?>(null) }
    val selectedInvites = externalSelectedGroupInvites ?: internalSelectedGroupInvites ?: entryGroups.firstOrNull()?.invites
    val setSelectedInvites: (Int) -> Unit = { invites ->
        internalSelectedGroupInvites = invites
        onExternalGroupInvitesChanged?.invoke(invites)
    }
    LaunchedEffect(entryGroups) {
        val current = selectedInvites
        if (current == null || entryGroups.none { it.invites == current }) {
            entryGroups.firstOrNull()?.invites?.let { setSelectedInvites(it) }
        }
    }

    val selectedGroup = entryGroups.firstOrNull { it.invites == selectedInvites } ?: entryGroups.firstOrNull()
    val selectedGroupIndex = entryGroups.indexOfFirst { it.invites == selectedGroup?.invites }.coerceAtLeast(0)
    val swipeJob = selectedGroup?.jobs?.firstOrNull()
    val cardShape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp),
    ) {
        if (showGroupSelector && entryGroups.size > 1) {
            entryGroups.forEachIndexed { index, group ->
                val isSelected = index == selectedGroupIndex
                val label = formatFutureEntryGroupLabel(group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setSelectedInvites(group.invites) }
                        .padding(vertical = 4.dp, horizontal = if (isPhone) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (group.invites > 0) Icons.Default.People else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 16.dp else 18.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = label,
                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None,
                    )
                }
            }
        }

        var isConfirmed by remember { mutableStateOf(false) }
        LaunchedEffect(
            selectedGroup?.invites,
            selectedGroup?.totalRemaining,
            swipeJob?.id,
            swipeJob?.sheetsId,
        ) {
            isConfirmed = false
        }

        if (swipeJob != null && onConfirmEntry != null) {
            val confirmScope = rememberCoroutineScope()
            selectedGroup?.let { group ->
                val invites = group.invites

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                            shape = cardShape,
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(if (isPhone) 12.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                modifier = Modifier.size(if (isPhone) 20.dp else 22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(Res.string.validate_entry),
                                style = if (isPhone) getPhonePortraitTypography() else MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = when {
                                isDesktop && invites > 0 ->
                                    stringResource(Res.string.validate_entry_description_with_invites_desktop, invites)
                                isDesktop ->
                                    stringResource(Res.string.validate_entry_description_solo_desktop)
                                invites > 0 ->
                                    stringResource(Res.string.validate_entry_description_with_invites, invites)
                                else ->
                                    stringResource(Res.string.validate_entry_description_solo)
                            },
                            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        )

                        if (!hideSecondaryGroupSummary && entryGroups.size <= 1 && group.totalRemaining > 0) {
                            Text(
                                text = formatFutureEntryGroupLabel(group),
                                style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        EntryConfirmControl(
                            onConfirm = {
                                if (isConfirmed) return@EntryConfirmControl
                                isConfirmed = true
                                confirmScope.launch {
                                    onConfirmEntry(swipeJob, group.invites)
                                    delay(180L)
                                    isConfirmed = false
                                }
                            },
                            isConfirmed = isConfirmed,
                            enabled = !isConfirmed,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatFutureEntryGroupLabel(group: FutureEntryGroup): String {
    val rem = group.totalRemaining
    val inv = group.invites
    return if (inv > 0) {
        if (rem == 1) stringResource(Res.string.future_entry_group_label_invites, rem, inv)
        else stringResource(Res.string.future_entries_group_label_invites, rem, inv)
    } else {
        if (rem == 1) stringResource(Res.string.future_entry_group_label_solo, rem)
        else stringResource(Res.string.future_entries_group_label_solo, rem)
    }
}
