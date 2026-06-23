package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createCardReaderService
import com.eventmanager.app.platform.hardware.DesktopQrScanner
import kotlinx.coroutines.launch

@Composable
actual fun QRScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onMatchFound: (ScannerMatch) -> Unit,
    volunteers: List<Volunteer>,
    guests: List<Guest>
) {
    var scanning by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Point webcam at QR code or tap NFC reader…") }
    val scope = rememberCoroutineScope()
    val cardReader = remember(platformContext) { createCardReaderService(platformContext) }
    val qrScanner = remember { DesktopQrScanner() }

    LaunchedEffect(Unit) {
        launch {
            if (cardReader.isReaderConnected()) {
                status = "Tap card on reader…"
                when (val uid = cardReader.readUid()) {
                    is com.eventmanager.app.platform.UidReadResult.Success -> {
                        resolveDesktopMatch(uid.uid, volunteers, guests)?.let { onMatchFound(it); return@launch }
                    }
                    else -> Unit
                }
            }
            status = "Scanning webcam…"
            val payload = qrScanner.scanOnce()
            scanning = false
            if (payload != null) {
                resolveDesktopMatch(payload, volunteers, guests)?.let(onMatchFound) ?: run {
                    status = "No match for: $payload"
                }
            } else {
                status = "No QR detected"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanner") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scanning) CircularProgressIndicator()
                Text(status)
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    scanning = true
                    status = "Scanning…"
                    val payload = qrScanner.scanOnce()
                    scanning = false
                    payload?.let { resolveDesktopMatch(it, volunteers, guests)?.let(onMatchFound) }
                }
            }) { Text("Scan again") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun resolveDesktopMatch(
    raw: String,
    volunteers: List<Volunteer>,
    guests: List<Guest>
): ScannerMatch? {
    val trimmed = raw.trim()
    volunteers.firstOrNull { it.id == trimmed }?.let { return ScannerMatch.VolunteerMatch(it) }
    guests.firstOrNull { it.nanoId == trimmed }?.let { return ScannerMatch.GuestMatch(it) }
    return null
}
