package com.eventmanager.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.Gender
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.AddNfcUidDialog
import com.eventmanager.app.utils.QRCodeUtils
import org.jetbrains.compose.resources.stringResource

enum class AdminSetupStep {
    INTRO, TYPE_SELECTION, CREATE_FORM, ACCESS_METHOD, QR_DISPLAY, NFC_ASSIGN, DONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSetupScreen(
    platformContext: PlatformContext,
    venues: List<VenueEntity>,
    onCreateAdminGuest: (Guest, (Boolean, String?) -> Unit) -> Unit,
    onCreateAdminVolunteer: (Volunteer, (Boolean, String?) -> Unit) -> Unit,
    onAssignNfcUid: (AdminType, String, String) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableStateOf(AdminSetupStep.INTRO) }
    var adminType by remember { mutableStateOf<AdminType?>(null) }
    var createdGuest by remember { mutableStateOf<Guest?>(null) }
    var createdVolunteer by remember { mutableStateOf<Volunteer?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var nfcAssigned by remember { mutableStateOf(false) }

    val adminName = createdGuest?.name ?: createdVolunteer?.name ?: ""
    val adminEntityId = createdGuest?.nanoId ?: createdVolunteer?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.admin_setup_title)) },
                actions = {
                    if (step != AdminSetupStep.DONE) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(Res.string.admin_setup_btn_skip))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "adminSetupStep",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                when (currentStep) {
                    AdminSetupStep.INTRO -> IntroPage()
                    AdminSetupStep.TYPE_SELECTION -> TypeSelectionPage(
                        onSelectGuest = { adminType = AdminType.GUEST; step = AdminSetupStep.CREATE_FORM },
                        onSelectVolunteer = { adminType = AdminType.VOLUNTEER; step = AdminSetupStep.CREATE_FORM }
                    )
                    AdminSetupStep.CREATE_FORM -> when (adminType) {
                        AdminType.GUEST -> AdminGuestFormPage(
                            venues = venues, creating = creating, error = createError,
                            onSubmit = { guest ->
                                creating = true; createError = null
                                onCreateAdminGuest(guest) { success, err ->
                                    creating = false
                                    if (success) { createdGuest = guest; step = AdminSetupStep.ACCESS_METHOD }
                                    else createError = err
                                }
                            }
                        )
                        AdminType.VOLUNTEER -> AdminVolunteerFormPage(
                            creating = creating, error = createError,
                            onSubmit = { volunteer ->
                                creating = true; createError = null
                                onCreateAdminVolunteer(volunteer) { success, err ->
                                    creating = false
                                    if (success) { createdVolunteer = volunteer; step = AdminSetupStep.ACCESS_METHOD }
                                    else createError = err
                                }
                            }
                        )
                        null -> Unit
                    }
                    AdminSetupStep.ACCESS_METHOD -> AccessMethodPage(
                        onQr = { step = AdminSetupStep.QR_DISPLAY },
                        onNfc = { showNfcDialog = true }
                    )
                    AdminSetupStep.QR_DISPLAY -> QrDisplayPage(adminType, createdGuest, createdVolunteer) {
                        step = AdminSetupStep.DONE
                    }
                    AdminSetupStep.NFC_ASSIGN -> NfcDonePage(nfcAssigned) { step = AdminSetupStep.DONE }
                    AdminSetupStep.DONE -> DonePage(adminName)
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step != AdminSetupStep.INTRO && step != AdminSetupStep.DONE) {
                    OutlinedButton(onClick = {
                        step = when (step) {
                            AdminSetupStep.TYPE_SELECTION -> AdminSetupStep.INTRO
                            AdminSetupStep.CREATE_FORM -> AdminSetupStep.TYPE_SELECTION
                            AdminSetupStep.ACCESS_METHOD -> AdminSetupStep.CREATE_FORM
                            AdminSetupStep.QR_DISPLAY, AdminSetupStep.NFC_ASSIGN -> AdminSetupStep.ACCESS_METHOD
                            else -> step
                        }
                    }) { Text(stringResource(Res.string.setup_back)) }
                } else Spacer(Modifier.width(8.dp))

                when (step) {
                    AdminSetupStep.INTRO -> Button(onClick = { step = AdminSetupStep.TYPE_SELECTION }, modifier = Modifier.fillMaxWidth(0.6f)) {
                        Text(stringResource(Res.string.setup_continue))
                    }
                    AdminSetupStep.DONE -> Button(onClick = onComplete, modifier = Modifier.fillMaxWidth(0.6f)) {
                        Text(stringResource(Res.string.admin_setup_done))
                    }
                    else -> Spacer(Modifier.width(8.dp))
                }
            }
        }
    }

    if (showNfcDialog && adminType != null && adminEntityId != null) {
        AddNfcUidDialog(
            platformContext = platformContext,
            onDismiss = { showNfcDialog = false },
            onConfirmUid = { uid ->
                showNfcDialog = false
                onAssignNfcUid(adminType!!, adminEntityId, uid)
                nfcAssigned = true
                step = AdminSetupStep.NFC_ASSIGN
            }
        )
    }
}

@Composable private fun IntroPage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.admin_setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.admin_setup_description), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))) {
            Text(stringResource(Res.string.admin_setup_no_admin_warning), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable private fun TypeSelectionPage(onSelectGuest: () -> Unit, onSelectVolunteer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.admin_setup_type_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_type_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionCard(Icons.Default.Person, stringResource(Res.string.admin_setup_type_guest), stringResource(Res.string.admin_setup_type_guest_desc), onSelectGuest)
        SelectionCard(Icons.Default.Group, stringResource(Res.string.admin_setup_type_volunteer), stringResource(Res.string.admin_setup_type_volunteer_desc), onSelectVolunteer)
    }
}

@Composable private fun SelectionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun AdminGuestFormPage(venues: List<VenueEntity>, creating: Boolean, error: String?, onSubmit: (Guest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf("0") }
    var venueName by remember { mutableStateOf(venues.firstOrNull()?.name ?: "BOTH") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.admin_setup_create_guest_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_create_guest_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(Res.string.email)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(Res.string.phone)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = invitations, onValueChange = { invitations = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(Res.string.invitations)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = venueName, onValueChange = { venueName = it }, label = { Text(stringResource(Res.string.venue)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                onSubmit(Guest(name = name.trim(), email = email.trim(), phoneNumber = phone.trim(), invitations = invitations.toIntOrNull() ?: 0, venueName = venueName.ifBlank { "BOTH" }))
            },
            enabled = name.isNotBlank() && !creating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (creating) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (creating) stringResource(Res.string.admin_setup_creating) else stringResource(Res.string.setup_continue))
        }
    }
}

@Composable private fun AdminVolunteerFormPage(creating: Boolean, error: String?, onSubmit: (Volunteer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var abbreviation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.admin_setup_create_volunteer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_create_volunteer_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = abbreviation, onValueChange = { abbreviation = it }, label = { Text(stringResource(Res.string.last_name_abbreviation)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(Res.string.email)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(Res.string.phone)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                onSubmit(Volunteer(name = name.trim(), lastNameAbbreviation = abbreviation.trim(), email = email.trim(), phoneNumber = phone.trim(), gender = Gender.PREFER_NOT_TO_DISCLOSE))
            },
            enabled = name.isNotBlank() && abbreviation.isNotBlank() && !creating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (creating) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (creating) stringResource(Res.string.admin_setup_creating) else stringResource(Res.string.setup_continue))
        }
    }
}

@Composable private fun AccessMethodPage(onQr: () -> Unit, onNfc: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.admin_setup_access_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_access_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.admin_setup_access_explained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionCard(Icons.Default.QrCode, stringResource(Res.string.admin_setup_use_qr), stringResource(Res.string.admin_setup_use_qr_desc), onQr)
        SelectionCard(Icons.Default.Nfc, stringResource(Res.string.admin_setup_use_nfc), stringResource(Res.string.admin_setup_use_nfc_desc), onNfc)
    }
}

@Composable private fun QrDisplayPage(adminType: AdminType?, guest: Guest?, volunteer: Volunteer?, onContinue: () -> Unit) {
    val payload = when (adminType) {
        AdminType.VOLUNTEER -> volunteer?.id
        AdminType.GUEST -> guest?.nanoId
        null -> null
    }
    val qrBitmap = remember(payload) { payload?.let { QRCodeUtils.generateQrImageBitmap(it, 512) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(Res.string.admin_setup_qr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_qr_description), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        qrBitmap?.let { bitmap ->
            Image(bitmap = bitmap, contentDescription = stringResource(Res.string.admin_setup_qr_title), modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
        }
        Text(guest?.name ?: volunteer?.name ?: "", fontWeight = FontWeight.SemiBold)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_setup_done))
        }
    }
}

@Composable private fun NfcDonePage(assigned: Boolean, onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = if (assigned) stringResource(Res.string.admin_setup_nfc_assigned) else stringResource(Res.string.admin_setup_nfc_linked_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_setup_done))
        }
    }
}

@Composable private fun DonePage(adminName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.admin_setup_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_created_line, adminName), textAlign = TextAlign.Center)
        Text(stringResource(Res.string.admin_setup_done_message, adminName), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
