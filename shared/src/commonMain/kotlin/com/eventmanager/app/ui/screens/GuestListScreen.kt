package com.eventmanager.app.ui.screens

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.models.ManualTemporaryGuestBatch
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
expect fun GuestListScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    venues: List<VenueEntity>,
    onAddGuest: (Guest, ByteArray?) -> Unit,
    onAddTemporaryGuests: (ManualTemporaryGuestBatch) -> Unit,
    onUpdateGuest: (Guest) -> Unit,
    onUpdateVolunteer: (Volunteer) -> Unit,
    onDeleteGuest: (Guest) -> Unit,
    onRefreshTemporaryGuests: () -> Unit = {},
    onConfirmEntry: ((Job, Int) -> Unit)? = null,
    isSyncing: Boolean = false,
    lastSyncTime: Long = 0L,
    scrollBehavior: String = SettingsManager.FULL_SCROLL,
    readOnly: Boolean = false,
    searchFocusTick: Int = 0,
    viewModel: EventManagerViewModel? = null
)
