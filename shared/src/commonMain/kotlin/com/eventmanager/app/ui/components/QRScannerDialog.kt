package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext

sealed class ScannerMatch {
    data class VolunteerMatch(val volunteer: Volunteer) : ScannerMatch()
    data class GuestMatch(val guest: Guest) : ScannerMatch()
}

data class NfcUidMatchOption(
    val match: ScannerMatch,
    val title: String,
    val subtitle: String,
    val typeLabel: String,
)

@Composable
expect fun QRScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onMatchFound: (ScannerMatch) -> Unit,
    volunteers: List<Volunteer>,
    guests: List<Guest>
)
