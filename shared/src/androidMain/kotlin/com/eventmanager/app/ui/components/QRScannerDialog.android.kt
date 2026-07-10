package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun QRScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onMatchFound: (ScannerMatch) -> Unit,
    volunteers: List<Volunteer>,
    guests: List<Guest>
) {
    AndroidQrScannerDialog(
        onDismiss = onDismiss,
        onMatchFound = onMatchFound,
        volunteers = volunteers,
        guests = guests
    )
}
