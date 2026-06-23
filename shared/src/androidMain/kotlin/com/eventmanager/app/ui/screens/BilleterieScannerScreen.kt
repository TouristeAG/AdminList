package com.eventmanager.app.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.R
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.data.utils.effectiveBenefitFutureEntriesRemaining
import com.eventmanager.app.data.utils.groupFutureEntriesByInvites
import com.eventmanager.app.data.utils.jobTypeSupportsTrackedFutureEntries
import com.eventmanager.app.ui.components.NfcUidMatchOption
import com.eventmanager.app.ui.components.QRScannerView
import com.eventmanager.app.ui.components.ScannerMatch

sealed class ScanResult {
    data class FreeEntry(
        val volunteer: Volunteer,
        val benefitStatus: VolunteerBenefitStatus,
        val perkTexts: List<String>
    ) : ScanResult()

    data class GuestFound(val guest: Guest) : ScanResult()

    data class NoEntry(
        val volunteer: Volunteer,
        val benefitStatus: VolunteerBenefitStatus,
        val perkTexts: List<String>
    ) : ScanResult()

    data class TicketsAvailable(
        val volunteer: Volunteer,
        val benefitStatus: VolunteerBenefitStatus,
        val volunteerJobs: List<Job>
    ) : ScanResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleterieScannerScreen(
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    onBack: () -> Unit,
    onConfirmEntry: (Job, Int) -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { settingsManagerFor(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val configsByName = remember(jobTypeConfigs) { jobTypeConfigs.associateBy { it.name } }

    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var cameraActive by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var duplicateUidMatches by remember { mutableStateOf<List<NfcUidMatchOption>>(emptyList()) }
    var duplicateUid by remember { mutableStateOf<String?>(null) }

    val permanentGuests = remember(guests) { guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest } }
    val volunteersByNfcUid = remember(volunteers) {
        volunteers.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.normalizeScannerUid() }
    }
    val guestsByNfcUid = remember(permanentGuests) {
        permanentGuests.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.normalizeScannerUid() }
    }

    fun buildPerkTexts(benefit: Benefit): List<String> = listOfNotNull(
        if (benefit.freeEntry) context.getString(R.string.free_entry) else null,
        if (benefit.friendInvitation) {
            if (benefit.inviteCount > 1) context.getString(R.string.invites_n, benefit.inviteCount)
            else context.getString(R.string.friend_invitation)
        } else null,
        if (benefit.drinkTokens > 0) context.getString(R.string.drink_tokens, benefit.drinkTokens) else null,
        if (benefit.barDiscount > 0) context.getString(R.string.bar_discount, benefit.barDiscount) else null,
        if (benefit.extraordinaryBenefits) context.getString(R.string.extraordinary_benefits) else null
    )

    fun resolveScanMatch(match: ScannerMatch) {
        statusMessage = null
        when (match) {
            is ScannerMatch.VolunteerMatch -> {
                val volunteer = match.volunteer
                val volunteerJobs = jobs.filter { it.volunteerId == volunteer.id }
                val benefitStatus = BenefitCalculator.calculateVolunteerBenefitStatus(
                    volunteer, jobs, jobTypeConfigs, offsetHours = offsetHours
                )
                val perkTexts = buildPerkTexts(benefitStatus.benefits)
                val meetingExcluded = BenefitCalculator.isVolunteerOrionActive(
                    volunteerJobs, jobTypeConfigs, offsetHours = offsetHours
                )
                val futureJobs = volunteerJobs.filter { job ->
                    val config = configsByName[job.jobTypeName]
                    jobTypeSupportsTrackedFutureEntries(job, config) &&
                        effectiveBenefitFutureEntriesRemaining(
                            job, config, offsetHours = offsetHours, meetingNovaBenefitsExcludedForOrion = meetingExcluded
                        ) > 0
                }
                scanResult = when {
                    benefitStatus.benefits.freeEntry && benefitStatus.benefits.isActive ->
                        ScanResult.FreeEntry(volunteer, benefitStatus, perkTexts)
                    futureJobs.isNotEmpty() ->
                        ScanResult.TicketsAvailable(volunteer, benefitStatus, futureJobs)
                    else ->
                        ScanResult.NoEntry(volunteer, benefitStatus, perkTexts)
                }
            }
            is ScannerMatch.GuestMatch -> {
                val guest = match.guest
                if (guest.isTemporaryGuest || guest.isVolunteerBenefit) {
                    statusMessage = context.getString(R.string.billeterie_nfc_no_match)
                } else {
                    scanResult = ScanResult.GuestFound(guest)
                }
            }
        }
    }

    fun resolveUidMatch(rawUid: String) {
        val uid = rawUid.normalizeScannerUid()
        if (uid.isBlank()) return
        val allMatches = buildList {
            volunteersByNfcUid[uid].orEmpty().forEach { add(
                NfcUidMatchOption(ScannerMatch.VolunteerMatch(it), it.name, "${it.lastNameAbbreviation} • ${it.id}", context.getString(R.string.volunteer))
            ) }
            guestsByNfcUid[uid].orEmpty().forEach { add(
                NfcUidMatchOption(ScannerMatch.GuestMatch(it), it.name, it.email.ifBlank { it.phoneNumber }, context.getString(R.string.permanent_guest_label))
            ) }
        }
        when {
            allMatches.isEmpty() -> statusMessage = context.getString(R.string.billeterie_nfc_no_match)
            allMatches.size == 1 -> resolveScanMatch(allMatches.first().match)
            else -> {
                duplicateUid = uid
                duplicateUidMatches = allMatches
            }
        }
    }

    fun handleQrPayload(rawId: String) {
        val match = resolveNanoidScannerMatchForBilleterie(volunteers, permanentGuests, rawId)
        if (match != null) resolveScanMatch(match)
        else statusMessage = context.getString(R.string.billeterie_nfc_no_match)
    }

    val activity = remember(context) {
        var ctx: android.content.Context = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        ctx as? Activity
    }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(activity, nfcAdapter, scanResult) {
        if (activity == null || nfcAdapter == null || !nfcAdapter.isEnabled || scanResult != null) {
            onDispose { }
        } else {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val uid = tag.id?.joinToString(separator = "") { "%02X".format(it) }.orEmpty()
                mainHandler.post { resolveUidMatch(uid) }
            }
            try {
                nfcAdapter.enableReaderMode(
                    activity, callback,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
            } catch (_: Exception) { }
            onDispose {
                try { nfcAdapter.disableReaderMode(activity) } catch (_: Exception) { }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.billeterie_button_scanner), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.setup_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (scanResult == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = statusMessage ?: context.getString(R.string.billeterie_scanner_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onClick = { cameraActive = !cameraActive }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (cameraActive) {
                                QRScannerView(
                                    modifier = Modifier.fillMaxSize(),
                                    onQRCodeScanned = { data -> handleQrPayload(data.id) },
                                    onError = { statusMessage = it }
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(context.getString(R.string.billeterie_scanner_tap_to_activate), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        AssistChip(
                            onClick = { cameraActive = !cameraActive },
                            label = {
                                Text(
                                    if (cameraActive) context.getString(R.string.billeterie_scanner_tap_to_pause)
                                    else context.getString(R.string.billeterie_scanner_tap_to_activate)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (cameraActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.admin_auth_ready), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            scanResult?.let { result ->
                ScanResultOverlay(
                    result = result,
                    jobTypeConfigs = jobTypeConfigs,
                    offsetHours = offsetHours,
                    onConfirmEntry = { job, invites ->
                        onConfirmEntry(job, invites)
                        scanResult = null
                        cameraActive = false
                    },
                    onDismiss = {
                        scanResult = null
                        cameraActive = false
                    },
                    onScanNext = {
                        scanResult = null
                        statusMessage = null
                    }
                )
            }
        }
    }

    if (duplicateUidMatches.isNotEmpty()) {
        DuplicateUidPickerDialog(
            uid = duplicateUid.orEmpty(),
            matches = duplicateUidMatches,
            onSelect = { match ->
                duplicateUidMatches = emptyList()
                duplicateUid = null
                resolveScanMatch(match)
            },
            onDismiss = {
                duplicateUidMatches = emptyList()
                duplicateUid = null
            }
        )
    }
}

@Composable
private fun DuplicateUidPickerDialog(
    uid: String,
    matches: List<NfcUidMatchOption>,
    onSelect: (ScannerMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(context.getString(R.string.nfc_uid_multiple_matches_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    context.getString(R.string.nfc_uid_multiple_matches_message, uid, matches.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(matches) { option ->
                        Card(
                            onClick = { onSelect(option.match) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(option.title, fontWeight = FontWeight.SemiBold)
                                Text(option.typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (option.subtitle.isNotBlank()) {
                                    Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }
        }
    )
}

@Composable
private fun ScanResultOverlay(
    result: ScanResult,
    jobTypeConfigs: List<JobTypeConfig>,
    offsetHours: Int,
    onConfirmEntry: (Job, Int) -> Unit,
    onDismiss: () -> Unit,
    onScanNext: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            when (result) {
                is ScanResult.FreeEntry -> FreeEntryContent(result, onScanNext)
                is ScanResult.GuestFound -> GuestFoundContent(result.guest, onScanNext)
                is ScanResult.NoEntry -> NoEntryContent(result, onScanNext)
                is ScanResult.TicketsAvailable -> TicketValidationContent(
                    volunteer = result.volunteer,
                    benefitStatus = result.benefitStatus,
                    volunteerJobs = result.volunteerJobs,
                    jobTypeConfigs = jobTypeConfigs,
                    offsetHours = offsetHours,
                    onConfirmEntry = onConfirmEntry,
                    onScanNext = onScanNext
                )
            }
        }
    }
}

@Composable
private fun FreeEntryContent(result: ScanResult.FreeEntry, onScanNext: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(context.getString(R.string.billeterie_scanner_entry_approved), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(result.volunteer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        result.perkTexts.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = onScanNext, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.billeterie_scanner_scan_next))
        }
    }
}

@Composable
private fun GuestFoundContent(guest: Guest, onScanNext: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(context.getString(R.string.billeterie_scanner_guest_found), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(guest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (guest.invitations > 0) {
            Text(context.getString(R.string.billeterie_scanner_with_invites, guest.invitations), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onScanNext, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.billeterie_scanner_validate_ticket))
        }
    }
}

@Composable
private fun NoEntryContent(result: ScanResult.NoEntry, onScanNext: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Text(context.getString(R.string.billeterie_scanner_no_entry), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Text(result.volunteer.name, style = MaterialTheme.typography.titleMedium)
        Text(context.getString(R.string.billeterie_scanner_no_benefits), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        result.perkTexts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedButton(onClick = onScanNext, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.billeterie_scanner_scan_next))
        }
    }
}

@Composable
private fun TicketValidationContent(
    volunteer: Volunteer,
    benefitStatus: VolunteerBenefitStatus,
    volunteerJobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    offsetHours: Int,
    onConfirmEntry: (Job, Int) -> Unit,
    onScanNext: () -> Unit
) {
    val context = LocalContext.current
    val configsByName = remember(jobTypeConfigs) { jobTypeConfigs.associateBy { it.name } }
    val meetingExcluded = remember(volunteerJobs, jobTypeConfigs, offsetHours) {
        BenefitCalculator.isVolunteerOrionActive(volunteerJobs, jobTypeConfigs, offsetHours = offsetHours)
    }
    val futureGroups = remember(volunteerJobs, configsByName, offsetHours, meetingExcluded) {
        groupFutureEntriesByInvites(volunteerJobs, configsByName, offsetHours = offsetHours, meetingNovaBenefitsExcludedForOrion = meetingExcluded)
    }
    var selectedInvites by remember(futureGroups) { mutableStateOf(futureGroups.firstOrNull()?.invites ?: 0) }
    val selectedGroup = futureGroups.firstOrNull { it.invites == selectedInvites }
    val selectedJob = selectedGroup?.jobs?.firstOrNull()

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(volunteer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(context.getString(R.string.billeterie_scanner_validate_ticket), color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (futureGroups.size > 1) {
            Text(context.getString(R.string.billeterie_scanner_with_invites, selectedInvites), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                futureGroups.forEach { group ->
                    FilterChip(
                        selected = selectedInvites == group.invites,
                        onClick = { selectedInvites = group.invites },
                        label = { Text("+${group.invites}") }
                    )
                }
            }
        } else if (selectedInvites > 0) {
            Text(context.getString(R.string.billeterie_scanner_with_invites, selectedInvites))
        }

        selectedGroup?.jobs?.forEach { job ->
            val remaining = effectiveBenefitFutureEntriesRemaining(
                job, configsByName[job.jobTypeName], offsetHours = offsetHours, meetingNovaBenefitsExcludedForOrion = meetingExcluded
            )
            Text("• ${job.jobTypeName} ($remaining)", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                selectedJob?.let { onConfirmEntry(it, selectedInvites) }
            },
            enabled = selectedJob != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.billeterie_scanner_entry_validated))
        }
        TextButton(onClick = onScanNext, modifier = Modifier.fillMaxWidth()) {
            Text(context.getString(R.string.billeterie_scanner_scan_next))
        }
    }
}

private fun String.normalizeScannerUid(): String =
    trim().replace(" ", "").replace(":", "").uppercase()

/** Resolves NanoID QR for billeterie (volunteer id or guest nanoId). */
private fun resolveNanoidScannerMatchForBilleterie(
    volunteers: List<Volunteer>,
    permanentGuests: List<Guest>,
    rawId: String
): ScannerMatch? {
    val trimmed = rawId.trim()
    if (trimmed.isEmpty()) return null
    volunteers.firstOrNull { it.id == trimmed }?.let { return ScannerMatch.VolunteerMatch(it) }
    permanentGuests.firstOrNull { it.nanoId == trimmed }?.let { return ScannerMatch.GuestMatch(it) }
    return null
}
