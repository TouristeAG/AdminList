package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.models.VolunteerBenefitStatus
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@Composable
expect fun VolunteerBenefitsPanel(
    volunteer: Volunteer,
    volunteerBenefitStatus: VolunteerBenefitStatus,
    volunteerJobs: List<Job>,
    venues: List<VenueEntity>,
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmEntry: ((Job, Int) -> Unit)? = null,
    onAssignNfcUid: ((Volunteer, String) -> Unit)? = null,
    readOnly: Boolean = false,
    accountBalance: Double = 0.0,
    currencyCode: String = "CHF",
    recentTransfers: List<AccountTransfer> = emptyList(),
    onManualAccountAdjust: ((Double, String) -> Unit)? = null,
    viewModel: EventManagerViewModel? = null
)
