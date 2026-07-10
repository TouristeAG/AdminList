package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.platform.NfcUidListenerEffect
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.eventmanager.app.data.sync.BiometricAdminProfileLink
import com.eventmanager.app.data.sync.BiometricAdminProfileType

fun ScannerMatch.toBiometricAdminProfileLink(): BiometricAdminProfileLink = when (this) {
    is ScannerMatch.VolunteerMatch -> BiometricAdminProfileLink(
        type = BiometricAdminProfileType.VOLUNTEER,
        profileId = volunteer.id
    )
    is ScannerMatch.GuestMatch -> BiometricAdminProfileLink(
        type = BiometricAdminProfileType.GUEST,
        profileId = guest.nanoId
    )
}

@Composable
fun BiometricAdminVerificationDialog(
    platformContext: PlatformContext,
    viewModel: EventManagerViewModel,
    onVerified: (ScannerMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val verifyScope = rememberCoroutineScope()
    val guests by viewModel.guests.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    var showQRScanner by remember { mutableStateOf(false) }
    var verificationMessage by remember { mutableStateOf<String?>(null) }
    var verifiedMatch by remember { mutableStateOf<ScannerMatch?>(null) }

    val notFoundMessage = stringResource(Res.string.admin_auth_not_found)
    val permanentGuests = remember(guests) { guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest } }
    val volunteersByNfcUid = remember(volunteers) {
        volunteers.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.trim().replace(" ", "").replace(":", "").uppercase() }
    }
    val guestsByNfcUid = remember(permanentGuests) {
        permanentGuests.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.trim().replace(" ", "").replace(":", "").uppercase() }
    }

    fun applyVerifiedAdminFromCandidates(candidates: List<ScannerMatch>) {
        if (candidates.isEmpty()) {
            verificationMessage = notFoundMessage
            return
        }
        verifyScope.launch {
            var grantedMatch: ScannerMatch? = null
            var deniedName: String? = null
            for (m in candidates) {
                val fresh = try {
                    viewModel.resolveFreshAdminScanMatch(m)
                } catch (_: Exception) {
                    m
                }
                when (fresh) {
                    is ScannerMatch.VolunteerMatch -> if (fresh.volunteer.isAdmin) {
                        grantedMatch = fresh
                        break
                    } else {
                        deniedName = fresh.volunteer.name
                    }
                    is ScannerMatch.GuestMatch -> if (fresh.guest.isAdmin) {
                        grantedMatch = fresh
                        break
                    } else {
                        deniedName = fresh.guest.name
                    }
                }
            }
            withContext(Dispatchers.Main) {
                if (grantedMatch != null) {
                    verifiedMatch = grantedMatch
                } else {
                    val name = deniedName ?: when (val m = candidates.first()) {
                        is ScannerMatch.VolunteerMatch -> m.volunteer.name
                        is ScannerMatch.GuestMatch -> m.guest.name
                    }
                    verificationMessage = getString(Res.string.admin_auth_denied, name)
                }
            }
        }
    }

    fun resolveUidMatch(rawUid: String) {
        val uid = rawUid.trim().replace(" ", "").replace(":", "").uppercase()
        if (uid.isBlank()) return
        val volunteerMatches = volunteersByNfcUid[uid].orEmpty()
        val guestMatches = guestsByNfcUid[uid].orEmpty()
        val allMatches: List<ScannerMatch> =
            volunteerMatches.map { ScannerMatch.VolunteerMatch(it) } +
                guestMatches.map { ScannerMatch.GuestMatch(it) }
        applyVerifiedAdminFromCandidates(allMatches)
    }

    LaunchedEffect(verifiedMatch) {
        verifiedMatch?.let { match ->
            delay(300)
            onVerified(match)
            verifiedMatch = null
        }
    }

    NfcUidListenerEffect(
        platformContext = platformContext,
        enabled = true,
        onUidRead = ::resolveUidMatch
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Card) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(Res.string.biometric_enrollment_verify_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(Res.string.biometric_enrollment_verify_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider()

                Icon(
                    Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )

                Text(
                    text = stringResource(Res.string.admin_auth_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (verificationMessage != null) {
                    Text(
                        text = verificationMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = { showQRScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.admin_auth_scan_qr), fontWeight = FontWeight.SemiBold)
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.biometric_warning_cancel))
                }
            }
        }
        }
    }

    if (showQRScanner) {
        QRScannerDialog(
            platformContext = platformContext,
            onDismiss = { showQRScanner = false },
            onMatchFound = { match ->
                showQRScanner = false
                applyVerifiedAdminFromCandidates(listOf(match))
            },
            volunteers = volunteers,
            guests = guests
        )
    }
}
