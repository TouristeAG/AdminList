package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@Composable
expect fun VolunteerDetailPanel(
    volunteer: Volunteer,
    volunteerJobs: List<Job>,
    venues: List<VenueEntity>,
    onEdit: (Volunteer) -> Unit,
    onAssignNfcUid: (Volunteer, String) -> Unit,
    onDelete: (Volunteer) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    onConfirmFutureEntry: ((Job, Int) -> Unit)? = null,
    accountBalance: Double = 0.0,
    currencyCode: String = "CHF",
    recentTransfers: List<AccountTransfer> = emptyList(),
    onManualAccountAdjust: ((Double, String) -> Unit)? = null,
    viewModel: EventManagerViewModel? = null
)
