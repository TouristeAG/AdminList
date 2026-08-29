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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.eventmanager.app.data.models.Gender
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.VolunteerRank
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.AddNfcUidDialog
import com.eventmanager.app.ui.components.BirthdayDatePicker
import com.eventmanager.app.ui.components.GuestVenueDropdownField
import com.eventmanager.app.ui.components.NfcUidCaptureContent
import com.eventmanager.app.ui.components.NfcUidInfoRow
import com.eventmanager.app.ui.components.ProfileQrShareBridge
import com.eventmanager.app.ui.components.genderDisplayLabel
import com.eventmanager.app.utils.QRCodeUtils
import com.eventmanager.app.utils.ValidationUtils
import org.jetbrains.compose.resources.stringResource

enum class AdminSetupStep {
    INTRO, TYPE_SELECTION, CREATE_FORM, QR_DISPLAY, NFC_CAPTURE, DONE
}

private data class CreatedAdminProfile(
    val type: AdminType,
    val guest: Guest? = null,
    val volunteer: Volunteer? = null,
) {
    val displayName: String get() = guest?.name ?: volunteer?.name.orEmpty()
    val entityId: String get() = when (type) {
        AdminType.GUEST -> guest?.nanoId.orEmpty()
        AdminType.VOLUNTEER -> volunteer?.id.orEmpty()
    }
    val qrPayload: String get() = entityId
    val nfcUid: String get() = guest?.nfcCardUid ?: volunteer?.nfcCardUid.orEmpty()

    fun withNfc(uid: String): CreatedAdminProfile = when (type) {
        AdminType.GUEST -> copy(guest = guest?.copy(nfcCardUid = uid))
        AdminType.VOLUNTEER -> copy(volunteer = volunteer?.copy(nfcCardUid = uid))
    }
}

/**
 * Core first-admin / admin-recovery wizard. Used by full-screen [AdminSetupScreen] and
 * [NoAdminRecoveryDialog].
 */
@Composable
fun AdminSetupFlow(
    platformContext: PlatformContext,
    venues: List<VenueEntity>,
    onCreateAdminGuest: (Guest, (Boolean, Guest?, String?) -> Unit) -> Unit,
    onCreateAdminVolunteer: (Volunteer, (Boolean, Volunteer?, String?) -> Unit) -> Unit,
    onAssignNfcUid: (AdminType, String, String) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    allowSkip: Boolean = true,
    onSkip: () -> Unit = {},
    showIntroWarning: Boolean = true,
    skipIntro: Boolean = false,
    useInlineNfcCapture: Boolean = false,
) {
    var step by remember(skipIntro, showIntroWarning) {
        mutableStateOf(
            when {
                skipIntro -> AdminSetupStep.TYPE_SELECTION
                else -> AdminSetupStep.INTRO
            },
        )
    }
    var pendingAdminType by remember { mutableStateOf<AdminType?>(null) }
    var createdProfile by remember { mutableStateOf<CreatedAdminProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var nfcManualUid by remember { mutableStateOf("") }
    var nfcStatusMessage by remember { mutableStateOf<String?>(null) }
    var showNfcDialog by remember { mutableStateOf(false) }
    var accessConfigured by remember { mutableStateOf(false) }

    val adminName = createdProfile?.displayName.orEmpty()
    val canFinish = accessConfigured && createdProfile != null

    fun onProfileCreated(profile: CreatedAdminProfile) {
        createdProfile = profile
        accessConfigured = false
        nfcManualUid = ""
        nfcStatusMessage = null
        step = AdminSetupStep.QR_DISPLAY
    }

    fun assignNfc(uid: String) {
        val profile = createdProfile ?: return
        if (profile.entityId.isBlank()) return
        onAssignNfcUid(profile.type, profile.entityId, uid)
        createdProfile = profile.withNfc(uid)
        accessConfigured = true
        nfcStatusMessage = null
        if (step == AdminSetupStep.NFC_CAPTURE) {
            step = AdminSetupStep.QR_DISPLAY
        }
    }

    Column(modifier.fillMaxWidth().fillMaxHeight()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "adminSetupStep",
                modifier = Modifier.fillMaxWidth(),
            ) { currentStep ->
                when (currentStep) {
                    AdminSetupStep.INTRO -> IntroPage(showWarning = showIntroWarning)
                    AdminSetupStep.TYPE_SELECTION -> TypeSelectionPage(
                        onSelectGuest = {
                            pendingAdminType = AdminType.GUEST
                            step = AdminSetupStep.CREATE_FORM
                        },
                        onSelectVolunteer = {
                            pendingAdminType = AdminType.VOLUNTEER
                            step = AdminSetupStep.CREATE_FORM
                        },
                    )
                    AdminSetupStep.CREATE_FORM -> when (pendingAdminType) {
                        AdminType.GUEST -> AdminGuestFormPage(
                            venues = venues,
                            creating = creating,
                            error = createError,
                            onSubmit = { guest ->
                                creating = true
                                createError = null
                                onCreateAdminGuest(guest) { success, saved, err ->
                                    creating = false
                                    if (success && saved != null) {
                                        onProfileCreated(CreatedAdminProfile(AdminType.GUEST, guest = saved))
                                    } else {
                                        createError = err
                                    }
                                }
                            },
                        )
                        AdminType.VOLUNTEER -> AdminVolunteerFormPage(
                            creating = creating,
                            error = createError,
                            onSubmit = { volunteer ->
                                creating = true
                                createError = null
                                onCreateAdminVolunteer(volunteer) { success, saved, err ->
                                    creating = false
                                    if (success && saved != null) {
                                        onProfileCreated(CreatedAdminProfile(AdminType.VOLUNTEER, volunteer = saved))
                                    } else {
                                        createError = err
                                    }
                                }
                            },
                        )
                        null -> Unit
                    }
                    AdminSetupStep.QR_DISPLAY -> {
                        val profile = createdProfile
                        if (profile != null) {
                            QrDisplayPage(
                                platformContext = platformContext,
                                profile = profile,
                                onAssignNfc = {
                                    if (useInlineNfcCapture) {
                                        step = AdminSetupStep.NFC_CAPTURE
                                    } else {
                                        showNfcDialog = true
                                    }
                                },
                                onQrAcknowledged = { accessConfigured = true },
                                onContinue = {
                                    if (canFinish) step = AdminSetupStep.DONE
                                },
                                canContinue = canFinish,
                            )
                        }
                    }
                    AdminSetupStep.NFC_CAPTURE -> {
                        Column(
                            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                stringResource(Res.string.admin_setup_use_nfc),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(Res.string.admin_setup_use_nfc_desc),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NfcUidCaptureContent(
                                platformContext = platformContext,
                                manualUid = nfcManualUid,
                                onManualUidChange = { nfcManualUid = it },
                                onConfirmUid = ::assignNfc,
                                onCancel = { step = AdminSetupStep.QR_DISPLAY },
                                statusMessage = nfcStatusMessage,
                                onStatusMessageChange = { nfcStatusMessage = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    AdminSetupStep.DONE -> DonePage(adminName)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step != AdminSetupStep.INTRO && step != AdminSetupStep.DONE) {
                OutlinedButton(onClick = {
                    step = when (step) {
                        AdminSetupStep.TYPE_SELECTION -> AdminSetupStep.INTRO
                        AdminSetupStep.CREATE_FORM -> AdminSetupStep.TYPE_SELECTION
                        AdminSetupStep.QR_DISPLAY -> AdminSetupStep.CREATE_FORM
                        AdminSetupStep.NFC_CAPTURE -> AdminSetupStep.QR_DISPLAY
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
                else -> {
                    if (allowSkip && step == AdminSetupStep.INTRO) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(Res.string.admin_setup_btn_skip))
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }

    if (!useInlineNfcCapture && showNfcDialog && createdProfile != null) {
        AddNfcUidDialog(
            platformContext = platformContext,
            onDismiss = { showNfcDialog = false },
            onConfirmUid = { uid ->
                showNfcDialog = false
                assignNfc(uid)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSetupScreen(
    platformContext: PlatformContext,
    venues: List<VenueEntity>,
    onCreateAdminGuest: (Guest, (Boolean, Guest?, String?) -> Unit) -> Unit,
    onCreateAdminVolunteer: (Volunteer, (Boolean, Volunteer?, String?) -> Unit) -> Unit,
    onAssignNfcUid: (AdminType, String, String) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.admin_setup_title)) },
                actions = {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(Res.string.admin_setup_btn_skip))
                    }
                },
            )
        },
    ) { padding ->
        AdminSetupFlow(
            platformContext = platformContext,
            venues = venues,
            onCreateAdminGuest = onCreateAdminGuest,
            onCreateAdminVolunteer = onCreateAdminVolunteer,
            onAssignNfcUid = onAssignNfcUid,
            onComplete = onComplete,
            onSkip = onSkip,
            allowSkip = true,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun NoAdminRecoveryDialog(
    platformContext: PlatformContext,
    venues: List<VenueEntity>,
    onCreateAdminGuest: (Guest, (Boolean, Guest?, String?) -> Unit) -> Unit,
    onCreateAdminVolunteer: (Volunteer, (Boolean, Volunteer?, String?) -> Unit) -> Unit,
    onAssignNfcUid: (AdminType, String, String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.no_admin_recovery_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(Res.string.no_admin_recovery_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.setup_back))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AdminSetupFlow(
                    platformContext = platformContext,
                    venues = venues,
                    onCreateAdminGuest = onCreateAdminGuest,
                    onCreateAdminVolunteer = onCreateAdminVolunteer,
                    onAssignNfcUid = onAssignNfcUid,
                    onComplete = onComplete,
                    allowSkip = false,
                    showIntroWarning = false,
                    skipIntro = true,
                    useInlineNfcCapture = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable private fun IntroPage(showWarning: Boolean) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.admin_setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.admin_setup_description), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showWarning) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))) {
                Text(stringResource(Res.string.admin_setup_no_admin_warning), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminGuestFormPage(
    venues: List<VenueEntity>,
    creating: Boolean,
    error: String?,
    onSubmit: (Guest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lastNameAbbreviation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf("0") }
    var selectedVenueName by remember { mutableStateOf<String?>(venues.firstOrNull()?.name ?: "BOTH") }
    var notes by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.admin_setup_create_guest_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_create_guest_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.guest_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = lastNameAbbreviation,
            onValueChange = { lastNameAbbreviation = it.uppercase() },
            label = { Text(stringResource(Res.string.last_name_abbreviation)) },
            placeholder = { Text(stringResource(Res.string.last_name_abbreviation_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = if (it.isBlank()) null else ValidationUtils.getEmailErrorMessage(it)
            },
            label = { Text(stringResource(Res.string.guest_email)) },
            placeholder = { Text(stringResource(Res.string.guest_email_placeholder)) },
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(stringResource(Res.string.guest_phone_number)) },
            placeholder = { Text(stringResource(Res.string.guest_phone_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = invitations,
            onValueChange = { invitations = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(Res.string.number_of_invitations)) },
            modifier = Modifier.fillMaxWidth(),
        )
        GuestVenueDropdownField(
            venues = venues,
            selectedVenueName = selectedVenueName,
            onVenueSelected = { selectedVenueName = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(Res.string.notes)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val defaultVenue = venues.firstOrNull { it.isActive }?.name ?: "BOTH"
                onSubmit(
                    Guest(
                        name = name.trim(),
                        lastNameAbbreviation = lastNameAbbreviation.trim(),
                        email = email.trim(),
                        phoneNumber = phone.trim(),
                        invitations = invitations.toIntOrNull() ?: 0,
                        venueName = selectedVenueName?.ifBlank { defaultVenue } ?: defaultVenue,
                        notes = notes.trim(),
                    ),
                )
            },
            enabled = name.isNotBlank() && emailError == null && !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (creating) stringResource(Res.string.admin_setup_creating) else stringResource(Res.string.setup_continue))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminVolunteerFormPage(creating: Boolean, error: String?, onSubmit: (Volunteer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var abbreviation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<Gender?>(Gender.PREFER_NOT_TO_DISCLOSE) }
    var expandedGender by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.admin_setup_create_volunteer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_create_volunteer_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.full_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = abbreviation,
            onValueChange = { abbreviation = it.uppercase() },
            label = { Text(stringResource(Res.string.last_name_abbreviation)) },
            placeholder = { Text(stringResource(Res.string.last_name_abbreviation_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = ValidationUtils.getEmailErrorMessage(it)
            },
            label = { Text(stringResource(Res.string.email)) },
            placeholder = { Text(stringResource(Res.string.email_placeholder)) },
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(stringResource(Res.string.phone_number)) },
            modifier = Modifier.fillMaxWidth(),
        )
        BirthdayDatePicker(
            dateString = dateOfBirth,
            onDateSelected = {
                dateOfBirth = it
                dateError = ValidationUtils.getDateErrorMessage(it)
            },
            label = { Text(stringResource(Res.string.date_of_birth)) },
            placeholder = { Text(stringResource(Res.string.date_of_birth_placeholder)) },
            isError = dateError != null,
            supportingText = dateError?.let { { Text(it) } },
        )
        ExposedDropdownMenuBox(
            expanded = expandedGender,
            onExpandedChange = { expandedGender = !expandedGender },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selectedGender?.let { genderDisplayLabel(it) }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.gender)) },
                placeholder = { Text(stringResource(Res.string.select_gender)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGender) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expandedGender,
                onDismissRequest = { expandedGender = false },
            ) {
                Gender.values().forEach { gender ->
                    DropdownMenuItem(
                        text = { Text(genderDisplayLabel(gender)) },
                        onClick = {
                            selectedGender = gender
                            expandedGender = false
                        },
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val storageDate = ValidationUtils.convertDateToStorageFormat(dateOfBirth) ?: dateOfBirth
                onSubmit(
                    Volunteer(
                        name = name.trim(),
                        lastNameAbbreviation = abbreviation.trim(),
                        email = email.trim(),
                        phoneNumber = phone.trim(),
                        dateOfBirth = storageDate,
                        gender = selectedGender,
                        currentRank = VolunteerRank.NOVA,
                    ),
                )
            },
            enabled = name.isNotBlank() &&
                abbreviation.isNotBlank() &&
                email.isNotBlank() &&
                phone.isNotBlank() &&
                dateOfBirth.isNotBlank() &&
                emailError == null &&
                dateError == null &&
                !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (creating) stringResource(Res.string.admin_setup_creating) else stringResource(Res.string.setup_continue))
        }
    }
}

@Composable private fun QrDisplayPage(
    platformContext: PlatformContext,
    profile: CreatedAdminProfile,
    onAssignNfc: () -> Unit,
    onQrAcknowledged: () -> Unit,
    onContinue: () -> Unit,
    canContinue: Boolean,
) {
    val payload = profile.qrPayload
    val qrBitmap = remember(payload) { payload.takeIf { it.isNotBlank() }?.let { QRCodeUtils.generateQrImageBitmap(it, 512) } }
    val shareTitle = stringResource(Res.string.admin_setup_qr_title)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(Res.string.admin_setup_qr_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_qr_description), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (qrBitmap != null) {
            Image(bitmap = qrBitmap, contentDescription = stringResource(Res.string.admin_setup_qr_title), modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Text(
                stringResource(Res.string.admin_setup_qr_unavailable),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Text(profile.displayName, fontWeight = FontWeight.SemiBold)
        if (payload.isNotBlank()) {
            Text(
                payload,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    ProfileQrShareBridge.shareProfileQrCode(
                        platformContext = platformContext,
                        qrPayload = payload,
                        fileName = "admin_qr_${payload.take(12)}.png",
                        title = shareTitle,
                    )
                    onQrAcknowledged()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_qr_code))
            }
            OutlinedButton(onClick = onQrAcknowledged, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.admin_setup_qr_saved))
            }
        }
        if (profile.nfcUid.isNotBlank()) {
            NfcUidInfoRow(uid = profile.nfcUid, isPhone = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedButton(onClick = onAssignNfc, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Nfc, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.admin_setup_use_nfc))
            }
        }
        if (!canContinue) {
            Text(
                stringResource(Res.string.admin_setup_access_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onContinue, enabled = canContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.admin_setup_done))
        }
    }
}

@Composable private fun DonePage(adminName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.admin_setup_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(Res.string.admin_setup_created_line, adminName), textAlign = TextAlign.Center)
        Text(stringResource(Res.string.admin_setup_done_message, adminName), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun institutionHasLocalAdmin(guests: List<Guest>, volunteers: List<Volunteer>): Boolean =
    guests.any { it.isAdmin } || volunteers.any { it.isAdmin }
