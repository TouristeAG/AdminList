package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.Volunteer

@Composable
fun VolunteerFutureEntriesSection(
    volunteerJobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    onConfirmEntry: ((Job, Int) -> Unit)?,
    externalSelectedGroupInvites: Int? = null,
    onExternalGroupInvitesChanged: ((Int) -> Unit)? = null,
    showGroupSelector: Boolean = true,
    hasActiveFreeEntryBenefit: Boolean = false,
    meetingNovaBenefitsExcludedForOrion: Boolean = false,
    hideSecondaryGroupSummary: Boolean = false
) {
    val context = LocalContext.current
    val futureJobs = volunteerJobs.filter { (it.benefitFutureEntriesRemaining ?: 0) > 0 }
    if (futureJobs.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(context.getString(R.string.future_entries_group_label_solo, futureJobs.size), style = MaterialTheme.typography.titleMedium)
            futureJobs.take(5).forEach { job ->
                val typeName = jobTypeConfigs.find { it.name == job.jobTypeName }?.name ?: job.jobTypeName
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(typeName, modifier = Modifier.weight(1f))
                    if (onConfirmEntry != null) {
                        TextButton(onClick = { onConfirmEntry(job, 1) }) {
                            Text(context.getString(R.string.billeterie_scanner_entry_approved))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteVolunteerDialog(
    volunteer: Volunteer,
    shiftCount: Int,
    onConfirm: (deleteShifts: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var deleteShifts by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.delete_volunteer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(context.getString(R.string.delete_volunteer_confirm, volunteer.name))
                if (shiftCount > 0) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = deleteShifts, onCheckedChange = { deleteShifts = it })
                        Text(context.getString(R.string.delete_volunteer_also_delete_shifts_other, shiftCount))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteShifts) }) {
                Text(context.getString(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }
        }
    )
}

@Composable fun RetroSynthwaveGameDialog(
    onDismiss: () -> Unit,
    onHextrisSelected: () -> Unit = {},
    onPizzaUndeliverySelected: () -> Unit = {},
    onScrollSelected: () -> Unit = {},
    onWendolVillageSelected: () -> Unit = {},
    onCatculusSelected: () -> Unit = {}
) = ArcadeStubDialog(onDismiss)
@Composable fun HextrisGameDialog(onDismiss: () -> Unit) = ArcadeStubDialog(onDismiss)
@Composable fun PizzaUndeliveryGameDialog(onDismiss: () -> Unit) = ArcadeStubDialog(onDismiss)
@Composable fun ScrollGameDialog(onDismiss: () -> Unit) = ArcadeStubDialog(onDismiss)
@Composable fun CatculusGameDialog(onDismiss: () -> Unit) = ArcadeStubDialog(onDismiss)
@Composable fun WendolVillageGameDialog(onDismiss: () -> Unit) = ArcadeStubDialog(onDismiss)

@Composable
private fun ArcadeStubDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.benefits_help_dialog_title)) },
        text = { Text(context.getString(R.string.benefits_help_intro)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.close)) }
        }
    )
}
