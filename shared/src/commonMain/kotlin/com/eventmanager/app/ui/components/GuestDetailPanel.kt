package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@Composable
expect fun GuestDetailPanel(
    guest: Guest,
    venues: List<VenueEntity>,
    onEdit: (Guest) -> Unit,
    onAssignNfcUid: (Guest, String) -> Unit,
    onDelete: (Guest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    accountBalance: Double = 0.0,
    currencyCode: String = "CHF",
    recentTransfers: List<AccountTransfer> = emptyList(),
    onManualAccountAdjust: ((Double, String) -> Unit)? = null,
    viewModel: EventManagerViewModel? = null
)
