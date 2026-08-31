package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.data.utils.DateTimeUtils
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.utils.getVenueDisplayString
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun GuestDetailPanel(
    guest: Guest,
    venues: List<VenueEntity>,
    onEdit: (Guest) -> Unit,
    onAssignNfcUid: (Guest, String) -> Unit,
    onDelete: (Guest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    readOnly: Boolean,
    accountBalance: Double,
    currencyCode: String,
    recentTransfers: List<AccountTransfer>,
    onManualAccountAdjust: ((Double, String) -> Unit)?,
    viewModel: EventManagerViewModel?
) {
    val platformContext = LocalPlatformContext.current
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showEmailConfirm by remember { mutableStateOf(false) }
    var showEmailInput by remember { mutableStateOf(false) }
    var showNoEmailStaff by remember { mutableStateOf(false) }
    var emailInputValue by remember { mutableStateOf("") }

    val staffSafeQrMode = readOnly && !guest.isTemporaryGuest
    val targetEmail = if (guest.email.isNotBlank()) guest.email else emailInputValue

    fun requestSendEmail() {
        showQrDialog = false
        when {
            guest.email.isNotBlank() -> showEmailConfirm = true
            readOnly -> showNoEmailStaff = true
            else -> {
                emailInputValue = ""
                showEmailInput = true
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (guest.isTemporaryGuest) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(Res.string.temp_guest_chip_label), fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, Modifier.size(14.dp)) }
                            )
                        } else {
                            Text(
                                stringResource(Res.string.guest_information),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            guest.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (guest.isTemporaryGuest) {
                            val artist = guest.temporaryArtistName.ifBlank { "-" }
                            Text(artist, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else if (guest.lastNameAbbreviation.isNotBlank()) {
                            Text(guest.lastNameAbbreviation, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Row {
                        if (staffSafeQrMode || !readOnly && !guest.isTemporaryGuest) {
                            IconButton(onClick = { showQrDialog = true }) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = stringResource(Res.string.qr_code),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                        }
                    }
                }
            }

            if (guest.isTemporaryGuest) {
                DesktopSectionCard(stringResource(Res.string.guest_information), Icons.Default.Person) {
                    DesktopDetailField(
                        stringResource(Res.string.temp_guest_event_date_label),
                        guest.temporaryEventDate?.let { DateTimeUtils.formatGenevaDateOnly(it) } ?: "-"
                    )
                    DesktopDetailField(stringResource(Res.string.temp_guest_artist_label), guest.temporaryArtistName.ifBlank { "-" })
                    DesktopDetailField(stringResource(Res.string.temp_guest_contact_phone_label), guest.temporaryContactPhone.ifBlank { "-" })
                    DesktopDetailField(stringResource(Res.string.notes), guest.notes.ifBlank { "-" })
                }
            } else {
                DesktopSectionCard(stringResource(Res.string.guest_information), Icons.Default.Person) {
                    DesktopInfoRow(stringResource(Res.string.invitations), guest.invitations.toString())
                    DesktopInfoRow(
                        stringResource(Res.string.venue),
                        getVenueDisplayString(guest.venueName, venues)
                    )
                    if (guest.email.isNotBlank()) DesktopInfoRow(stringResource(Res.string.guest_email), guest.email)
                    if (guest.phoneNumber.isNotBlank()) DesktopInfoRow(stringResource(Res.string.guest_phone_number), guest.phoneNumber)
                    if (guest.notes.isNotBlank()) DesktopInfoRow(stringResource(Res.string.notes), guest.notes)
                }
            }

            if (!readOnly && !guest.isTemporaryGuest && !guest.isVolunteerBenefit && onManualAccountAdjust != null && viewModel != null) {
                AccountInfoSection(
                    balance = accountBalance,
                    currencyCode = currencyCode,
                    recentTransfers = recentTransfers,
                    onManualAdjust = onManualAccountAdjust,
                    viewModel = viewModel,
                    allowAdjustment = true,
                    compactAdjust = true
                )
            }

            if (!readOnly && viewModel != null && !guest.isTemporaryGuest && !guest.isVolunteerBenefit) {
                LocalAdminRightsSection(
                    viewModel = viewModel,
                    isAdmin = guest.isAdmin,
                    displayName = guest.name,
                    kind = com.eventmanager.app.data.security.LocalAdminTargetKind.GUEST,
                    targetId = guest.nanoId,
                    guest = guest,
                )
            }

            NfcUidInfoRow(uid = guest.nfcCardUid, isPhone = false)

            if (!readOnly) {
                DesktopNanoIdRow("NanoID", guest.nanoId)
            }

            if (!readOnly) {
                DesktopSectionCard(stringResource(Res.string.actions), Icons.Default.Settings) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!guest.isTemporaryGuest) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { showNfcDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.add_nfc_card))
                                }
                                OutlinedButton(onClick = { showQrDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.qr_code))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { onEdit(guest) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.edit_guest))
                            }
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.delete_guest))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        DesktopQrCodeDialog(
            title = stringResource(Res.string.guest_qr_code),
            displayName = guest.name,
            qrPayload = guest.nanoId,
            onDismiss = { showQrDialog = false },
            onRequestSendEmail = { requestSendEmail() },
            staffSafeMode = staffSafeQrMode
        )
    }

    if (showEmailInput) {
        DesktopGuestEmailInputDialog(
            emailValue = emailInputValue,
            onEmailChange = { emailInputValue = it },
            onDismiss = { showEmailInput = false },
            onContinue = {
                showEmailInput = false
                showEmailConfirm = true
            }
        )
    }

    if (showEmailConfirm && targetEmail.isNotBlank()) {
        DesktopEmailConfirmDialog(
            profile = DesktopQrEmailProfile.Guest,
            recipientEmail = targetEmail,
            recipientName = guest.name,
            qrPayload = guest.nanoId,
            settingsManager = settingsManager,
            platformContext = platformContext,
            onDismiss = { showEmailConfirm = false },
            onSent = { showEmailConfirm = false }
        )
    }

    if (showNoEmailStaff) {
        AlertDialog(
            onDismissRequest = { showNoEmailStaff = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.email_no_email_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(Res.string.email_no_email_guest_staff_message)) },
            confirmButton = {
                TextButton(onClick = { showNoEmailStaff = false }) { Text(stringResource(Res.string.ok)) }
            }
        )
    }

    if (showNfcDialog) {
        AddNfcUidDialog(
            platformContext = platformContext,
            onDismiss = { showNfcDialog = false },
            onConfirmUid = { uid ->
                onAssignNfcUid(guest, uid)
                showNfcDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_guest)) },
            text = { Text(guest.name) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(guest)
                    showDeleteConfirm = false
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}
