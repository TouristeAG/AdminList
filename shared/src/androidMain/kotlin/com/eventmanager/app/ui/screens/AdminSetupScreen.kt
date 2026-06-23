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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.models.Gender
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.ui.components.AddNfcUidDialog
import com.eventmanager.app.utils.QRCodeUtils

enum class AdminSetupStep {
    INTRO,
    TYPE_SELECTION,
    CREATE_FORM,
    ACCESS_METHOD,
    QR_DISPLAY,
    NFC_ASSIGN,
    DONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSetupScreen(
    venues: List<VenueEntity>,
    onCreateAdminGuest: (Guest, (Boolean, String?) -> Unit) -> Unit,
    onCreateAdminVolunteer: (Volunteer, (Boolean, String?) -> Unit) -> Unit,
    onAssignNfcUid: (AdminType, String, String) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
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
                title = { Text(context.getString(R.string.admin_setup_title)) },
                actions = {
                    if (step != AdminSetupStep.DONE) {
                        TextButton(onClick = onSkip) {
                            Text(context.getString(R.string.admin_setup_btn_skip))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "adminSetupStep",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                when (currentStep) {
                    AdminSetupStep.INTRO -> IntroPage()
                    AdminSetupStep.TYPE_SELECTION -> TypeSelectionPage(
                        onSelectGuest = {
                            adminType = AdminType.GUEST
                            step = AdminSetupStep.CREATE_FORM
                        },
                        onSelectVolunteer = {
                            adminType = AdminType.VOLUNTEER
                            step = AdminSetupStep.CREATE_FORM
                        }
                    )
                    AdminSetupStep.CREATE_FORM -> when (adminType) {
                        AdminType.GUEST -> AdminGuestFormPage(
                            venues = venues,
                            creating = creating,
                            error = createError,
                            onSubmit = { guest ->
                                creating = true
                                createError = null
                                onCreateAdminGuest(guest) { success, err ->
                                    creating = false
                                    if (success) {
                                        createdGuest = guest
                                        step = AdminSetupStep.ACCESS_METHOD
                                    } else {
                                        createError = err?.let { context.getString(R.string.admin_setup_error, it) }
                                    }
                                }
                            }
                        )
                        AdminType.VOLUNTEER -> AdminVolunteerFormPage(
                            creating = creating,
                            error = createError,
                            onSubmit = { volunteer ->
                                creating = true
                                createError = null
                                onCreateAdminVolunteer(volunteer) { success, err ->
                                    creating = false
                                    if (success) {
                                        createdVolunteer = volunteer
                                        step = AdminSetupStep.ACCESS_METHOD
                                    } else {
                                        createError = err?.let { context.getString(R.string.admin_setup_error, it) }
                                    }
                                }
                            }
                        )
                        null -> Unit
                    }
                    AdminSetupStep.ACCESS_METHOD -> AccessMethodPage(
                        onQr = { step = AdminSetupStep.QR_DISPLAY },
                        onNfc = { showNfcDialog = true }
                    )
                    AdminSetupStep.QR_DISPLAY -> QrDisplayPage(
                        adminType = adminType,
                        guest = createdGuest,
                        volunteer = createdVolunteer,
                        onContinue = { step = AdminSetupStep.DONE }
                    )
                    AdminSetupStep.NFC_ASSIGN -> NfcDonePage(
                        assigned = nfcAssigned,
                        onContinue = { step = AdminSetupStep.DONE }
                    )
                    AdminSetupStep.DONE -> DonePage(adminName = adminName)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step != AdminSetupStep.INTRO && step != AdminSetupStep.DONE) {
                    OutlinedButton(onClick = {
                        step = when (step) {
                            AdminSetupStep.TYPE_SELECTION -> AdminSetupStep.INTRO
                            AdminSetupStep.CREATE_FORM -> AdminSetupStep.TYPE_SELECTION
                            AdminSetupStep.ACCESS_METHOD -> AdminSetupStep.CREATE_FORM
                            AdminSetupStep.QR_DISPLAY, AdminSetupStep.NFC_ASSIGN -> AdminSetupStep.ACCESS_METHOD
                            else -> step
                        }
                    }) {
                        Text(context.getString(R.string.setup_back))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                when (step) {
                    AdminSetupStep.INTRO -> Button(onClick = { step = AdminSetupStep.TYPE_SELECTION }, modifier = Modifier.fillMaxWidth(0.6f)) {
                        Text(context.getString(R.string.setup_continue))
                    }
                    AdminSetupStep.DONE -> Button(onClick = onComplete, modifier = Modifier.fillMaxWidth(0.6f)) {
                        Text(context.getString(R.string.admin_setup_done))
                    }
                    AdminSetupStep.QR_DISPLAY -> { /* QR page has its own continue */ }
                    AdminSetupStep.NFC_ASSIGN -> { /* NFC page has its own continue */ }
                    else -> Spacer(Modifier.width(8.dp))
                }
            }
        }
    }

    if (showNfcDialog && adminType != null && adminEntityId != null) {
        AddNfcUidDialog(
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

@Composable
private fun IntroPage() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(context.getString(R.string.admin_setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(context.getString(R.string.admin_setup_description), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))) {
            Text(
                text = context.getString(R.string.admin_setup_no_admin_warning),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun TypeSelectionPage(onSelectGuest: () -> Unit, onSelectVolunteer: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(context.getString(R.string.admin_setup_type_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.admin_setup_type_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionCard(Icons.Default.Person, context.getString(R.string.admin_setup_type_guest), context.getString(R.string.admin_setup_type_guest_desc), onSelectGuest)
        SelectionCard(Icons.Default.Group, context.getString(R.string.admin_setup_type_volunteer), context.getString(R.string.admin_setup_type_volunteer_desc), onSelectVolunteer)
    }
}

@Composable
private fun SelectionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
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

@Composable
private fun AdminGuestFormPage(
    venues: List<VenueEntity>,
    creating: Boolean,
    error: String?,
    onSubmit: (Guest) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf("0") }
    var venueName by remember { mutableStateOf(venues.firstOrNull()?.name ?: "BOTH") }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(context.getString(R.string.admin_setup_create_guest_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.admin_setup_create_guest_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(context.getString(R.string.name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(context.getString(R.string.email)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(context.getString(R.string.phone)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = invitations, onValueChange = { invitations = it.filter { c -> c.isDigit() } }, label = { Text(context.getString(R.string.invitations)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = venueName, onValueChange = { venueName = it }, label = { Text(context.getString(R.string.venue)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                onSubmit(
                    Guest(
                        name = name.trim(),
                        email = email.trim(),
                        phoneNumber = phone.trim(),
                        invitations = invitations.toIntOrNull() ?: 0,
                        venueName = venueName.ifBlank { "BOTH" }
                    )
                )
            },
            enabled = name.isNotBlank() && !creating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.admin_setup_creating))
            } else {
                Text(context.getString(R.string.setup_continue))
            }
        }
    }
}

@Composable
private fun AdminVolunteerFormPage(creating: Boolean, error: String?, onSubmit: (Volunteer) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var abbreviation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(context.getString(R.string.admin_setup_create_volunteer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.admin_setup_create_volunteer_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(context.getString(R.string.name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = abbreviation, onValueChange = { abbreviation = it }, label = { Text(context.getString(R.string.last_name_abbreviation)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(context.getString(R.string.email)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(context.getString(R.string.phone)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                onSubmit(
                    Volunteer(
                        name = name.trim(),
                        lastNameAbbreviation = abbreviation.trim(),
                        email = email.trim(),
                        phoneNumber = phone.trim(),
                        gender = Gender.PREFER_NOT_TO_DISCLOSE
                    )
                )
            },
            enabled = name.isNotBlank() && abbreviation.isNotBlank() && !creating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.admin_setup_creating))
            } else {
                Text(context.getString(R.string.setup_continue))
            }
        }
    }
}

@Composable
private fun AccessMethodPage(onQr: () -> Unit, onNfc: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(context.getString(R.string.admin_setup_access_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.admin_setup_access_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(context.getString(R.string.admin_setup_access_explained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionCard(Icons.Default.QrCode, context.getString(R.string.admin_setup_use_qr), context.getString(R.string.admin_setup_use_qr_desc), onQr)
        SelectionCard(Icons.Default.Nfc, context.getString(R.string.admin_setup_use_nfc), context.getString(R.string.admin_setup_use_nfc_desc), onNfc)
    }
}

@Composable
private fun QrDisplayPage(adminType: AdminType?, guest: Guest?, volunteer: Volunteer?, onContinue: () -> Unit) {
    val context = LocalContext.current
    val payload = when (adminType) {
        AdminType.VOLUNTEER -> volunteer?.id
        AdminType.GUEST -> guest?.nanoId
        null -> null
    }
    val qrBitmap = remember(payload) {
        payload?.let { QRCodeUtils.generateQrImageBitmap(it, 512) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(context.getString(R.string.admin_setup_qr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.admin_setup_qr_description), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = context.getString(R.string.admin_setup_qr_title),
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Text(guest?.name ?: volunteer?.name ?: "", fontWeight = FontWeight.SemiBold)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.admin_setup_done))
        }
    }
}

@Composable
private fun NfcDonePage(assigned: Boolean, onContinue: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = if (assigned) context.getString(R.string.admin_setup_nfc_assigned)
            else context.getString(R.string.admin_setup_nfc_linked_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.admin_setup_done))
        }
    }
}

@Composable
private fun DonePage(adminName: String) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        content = {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(context.getString(R.string.admin_setup_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(context.getString(R.string.admin_setup_created_line, adminName), textAlign = TextAlign.Center)
            Text(
                context.getString(R.string.admin_setup_done_message, adminName),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
