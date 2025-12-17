package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.DateTimeUtils
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.utils.QRCodeUtils
import com.eventmanager.app.R
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*
import com.eventmanager.app.utils.ValidationUtils
import com.eventmanager.app.data.sync.DateFormatUtils

@Composable
private fun getStringResource(@StringRes stringRes: Int, vararg args: Any): String {
    val context = LocalContext.current
    return context.getString(stringRes, *args)
}

@Composable
private fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerDetailPanel(
    volunteer: Volunteer,
    volunteerJobs: List<Job>,
    venues: List<VenueEntity>,
    onEdit: (Volunteer) -> Unit,
    onDelete: (Volunteer) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    jobTypeConfigs: List<JobTypeConfig> = emptyList()
) {
    val context = LocalContext.current
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    var showQrDialog by remember { mutableStateOf(false) }
    
    // Calculate age from date of birth
    val age = calculateAge(volunteer.dateOfBirth)
    
    // Sort jobs by date (most recent first)
    val sortedJobs = volunteerJobs.sortedByDescending { it.date }
    
    // Calculate statistics
    val totalShifts = volunteerJobs.size
    val isActive = VolunteerActivityManager.isVolunteerActive(volunteer)
    val activityStatusText = VolunteerActivityManager.getActivityStatusText(volunteer)
    
    // Calculate current rank dynamically
    val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    val volunteerBenefitStatus = remember(volunteer.id, volunteerJobs, jobTypeConfigs, offsetHours) {
        BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, volunteerJobs, jobTypeConfigs, offsetHours = offsetHours)
    }
    val currentRank = volunteerBenefitStatus.rank
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Modern header with volunteer name and status (FIXED AT TOP)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(responsivePadding)
                        .padding(bottom = if (isPhone) 8.dp else 12.dp),
                    shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(if (isPhone) 12.dp else 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = volunteer.name,
                                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                Spacer(modifier = Modifier.height(if (isPhone) 2.dp else 4.dp))
                                
                                Text(
                                    text = "${volunteer.lastNameAbbreviation} • ${volunteer.email}",
                                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = getStringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                        
                        // Status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                        shape = CircleShape
                                    )
                            )
                            
                            Text(
                                text = activityStatusText,
                                style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                color = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Scrollable content (SCROLLS BELOW HEADER)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = responsivePadding,
                        end = responsivePadding,
                        bottom = responsivePadding
                    )
                ) {
                    // Personal Information Section
                    item {
                        PersonalInformationSection(
                            volunteer = volunteer,
                            age = age,
                            isPhone = isPhone
                        )
                    }
                    
                    // Volunteer-Specific Information Section
                    item {
                        VolunteerSpecificSection(
                            volunteer = volunteer,
                            isActive = isActive,
                            activityStatusText = activityStatusText,
                            totalShifts = totalShifts,
                            isPhone = isPhone,
                            currentRank = currentRank
                        )
                    }
                    
                    // Shift History Section
                    item {
                        ShiftHistorySection(
                            jobs = sortedJobs,
                            isPhone = isPhone,
                            venues = venues
                        )
                    }
                    
                    // Action Buttons Section
                    item {
                        ActionButtonsSection(
                            volunteer = volunteer,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onShowQr = { showQrDialog = true },
                            isPhone = isPhone
                        )
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text(
                    text = getStringResource(R.string.volunteer_qr_code),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val payload = remember(volunteer) {
                    val data = mapOf(
                        "type" to "volunteer",
                        "version" to 1,
                        "id" to volunteer.id,
                        "sheetsId" to (volunteer.sheetsId ?: ""),
                        "name" to volunteer.name,
                        "abbr" to volunteer.lastNameAbbreviation
                    )
                    val json = Gson().toJson(data)
                    println("🔍 Generating QR code for volunteer: ${volunteer.name} (ID: ${volunteer.id})")
                    println("🔍 QR code JSON: '$json'")
                    json
                }
                val qrImage = remember(payload) { QRCodeUtils.generateQrImageBitmap(payload, 1024) }
                val qrContext = LocalContext.current
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (qrImage != null) {
                        Image(
                            bitmap = qrImage,
                            contentDescription = getStringResource(R.string.volunteer_qr_code),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(if (isPhone) 8.dp else 12.dp))
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                        Text(
                            text = volunteer.name,
                            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    qrImage?.let { bitmap ->
                                        try {
                                            val file = File(qrContext.cacheDir, "qr_code_${volunteer.id}.png")
                                            val outputStream = FileOutputStream(file)
                                            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                            outputStream.close()
                                            
                                            val uri = FileProvider.getUriForFile(
                                                qrContext,
                                                "${qrContext.packageName}.fileprovider",
                                                file
                                            )
                                            
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, qrContext.getString(R.string.qr_code_subject, volunteer.name))
                                                putExtra(Intent.EXTRA_TEXT, qrContext.getString(R.string.qr_code_for_volunteer, volunteer.name))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            qrContext.startActivity(Intent.createChooser(shareIntent, qrContext.getString(R.string.share_qr_code)))
                                        } catch (e: Exception) {
                                            // Fallback to text sharing if image sharing fails
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "Volunteer QR")
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Volunteer: ${volunteer.name}\nID: ${volunteer.id}\nPayload: $payload"
                                                )
                                            }
                                            qrContext.startActivity(Intent.createChooser(shareIntent, qrContext.getString(R.string.share_qr_code)))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getStringResource(R.string.share))
                            }
                            OutlinedButton(
                                onClick = { /* no-op for now */ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getStringResource(R.string.send_by_mail))
                            }
                        }
                    } else {
                        Text(
                            text = getStringResource(R.string.failed_to_generate_qr_code),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(getStringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun PersonalInformationSection(
    volunteer: Volunteer,
    age: Int?,
    isPhone: Boolean
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = getStringResource(R.string.personal_information),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            // Name and abbreviation
            InfoRow(
                label = getStringResource(R.string.name),
                value = volunteer.name,
                isPhone = isPhone
            )
            
            InfoRow(
                label = getStringResource(R.string.abbreviation),
                value = volunteer.lastNameAbbreviation,
                isPhone = isPhone
            )
            
            // Gender
            InfoRow(
                label = getStringResource(R.string.gender),
                value = volunteer.gender?.let { gender ->
                    when (gender) {
                        Gender.FEMALE -> getStringResource(R.string.female)
                        Gender.MALE -> getStringResource(R.string.male)
                        Gender.NON_BINARY -> getStringResource(R.string.non_binary)
                        Gender.OTHER -> getStringResource(R.string.other)
                        Gender.PREFER_NOT_TO_DISCLOSE -> getStringResource(R.string.prefer_not_to_disclose)
                    }
                } ?: getStringResource(R.string.not_specified),
                isPhone = isPhone
            )
            
            // Birthday and age
            if (volunteer.dateOfBirth.isNotEmpty()) {
                InfoRow(
                    label = getStringResource(R.string.birthday),
                    value = formatBirthday(volunteer.dateOfBirth, LocalContext.current),
                    isPhone = isPhone
                )
                
                age?.let { calculatedAge ->
                    InfoRow(
                        label = getStringResource(R.string.age),
                        value = getStringResource(R.string.years_old, calculatedAge),
                        isPhone = isPhone
                    )
                }
            }
            
            // Contact information
            InfoRow(
                label = getStringResource(R.string.email),
                value = volunteer.email,
                isPhone = isPhone
            )
            
            InfoRow(
                label = getStringResource(R.string.phone),
                value = volunteer.phoneNumber,
                isPhone = isPhone
            )
        }
    }
}

@Composable
private fun VolunteerSpecificSection(
    volunteer: Volunteer,
    isActive: Boolean,
    activityStatusText: String,
    totalShifts: Int,
    isPhone: Boolean,
    currentRank: VolunteerRank?
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = getStringResource(R.string.volunteer_information),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            // Current rank
            InfoRow(
                label = getStringResource(R.string.current_rank),
                value = currentRank?.let { getRankDisplayName(it) } ?: getStringResource(R.string.no_rank),
                isPhone = isPhone
            )
            
            // Activity status with color indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getStringResource(R.string.status),
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = activityStatusText,
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Total shifts
            InfoRow(
                label = getStringResource(R.string.total_shifts),
                value = totalShifts.toString(),
                isPhone = isPhone
            )
            
            // Last shift date
            volunteer.lastShiftDate?.let { lastShift ->
                InfoRow(
                    label = getStringResource(R.string.last_shift),
                    value = DateFormatUtils.formatDateTime(lastShift, LocalContext.current),
                    isPhone = isPhone
                )
            }
        }
    }
}

@Composable
private fun ShiftHistorySection(
    jobs: List<Job>,
    isPhone: Boolean,
    venues: List<VenueEntity>
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Work,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = getStringResource(R.string.shift_history, jobs.size),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            if (jobs.isEmpty()) {
                Text(
                    text = getStringResource(R.string.no_shifts_recorded),
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                // Show last 10 shifts to avoid overwhelming the UI
                val recentJobs = jobs.take(10)
                
                recentJobs.forEach { job ->
                    ShiftHistoryItem(
                        job = job,
                        isPhone = isPhone,
                        venues = venues
                    )
                    
                    if (job != recentJobs.last()) {
                        Spacer(modifier = Modifier.height(if (isPhone) 4.dp else 6.dp))
                    }
                }
                
                if (jobs.size > 10) {
                    Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                    Text(
                        text = getStringResource(R.string.more_shifts, jobs.size - 10),
                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftHistoryItem(
    job: Job,
    isPhone: Boolean,
    venues: List<VenueEntity>
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (isPhone) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.jobTypeName,
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = DateFormatUtils.formatDate(job.date, context),
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 2.dp else 4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = getVenueDisplayString(job.venueName, venues),
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = job.shiftTime.name.replace("_", " "),
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (job.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (isPhone) 4.dp else 6.dp))
                Text(
                    text = job.notes,
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    volunteer: Volunteer,
    onEdit: (Volunteer) -> Unit,
    onDelete: (Volunteer) -> Unit,
    onShowQr: (Volunteer) -> Unit,
    isPhone: Boolean
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = getStringResource(R.string.actions),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(responsiveSpacing))
            
            if (isPhone) {
                // Stack vertically on phones
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onShowQr(volunteer) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.qr_code))
                    }
                    
                    OutlinedButton(
                        onClick = { onEdit(volunteer) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.edit_volunteer_button))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(volunteer) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.delete_volunteer_button))
                    }
                }
            } else {
                // Row layout on tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onShowQr(volunteer) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.qr_code))
                    }
                    
                    OutlinedButton(
                        onClick = { onEdit(volunteer) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.edit_volunteer_button))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(volunteer) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getStringResource(R.string.delete_volunteer_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolunteerQrCodeDialog(
    volunteer: Volunteer,
    onClose: () -> Unit
) {
    val dialogContext = LocalContext.current
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    val gson = remember { Gson() }
    val payload = remember(volunteer) {
        val data = mapOf(
            "type" to "volunteer",
            "version" to 1,
            "id" to volunteer.id,
            "sheetsId" to (volunteer.sheetsId ?: ""),
            "name" to volunteer.name,
            "abbr" to volunteer.lastNameAbbreviation
        )
        val json = gson.toJson(data)
        println("🔍 Generating QR code for volunteer: ${volunteer.name} (ID: ${volunteer.id})")
        println("🔍 QR code JSON: '$json'")
        json
    }
    val qrImage = remember(payload) { QRCodeUtils.generateQrImageBitmap(payload, 1024) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsivePadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dialogContext.getString(R.string.volunteer_qr_code),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(if (isPhone) 12.dp else 16.dp))
            if (qrImage != null) {
                Image(
                    bitmap = qrImage,
                    contentDescription = dialogContext.getString(R.string.volunteer_qr_code),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(if (isPhone) 8.dp else 12.dp))
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                Text(
                    text = volunteer.name,
                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Volunteer QR")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Volunteer: ${volunteer.name}\nID: ${volunteer.id}\nPayload: $payload"
                                )
                            }
                            dialogContext.startActivity(Intent.createChooser(shareIntent, dialogContext.getString(R.string.share_qr_code)))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(dialogContext.getString(R.string.share))
                    }
                    OutlinedButton(
                        onClick = { /* no-op for now */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(dialogContext.getString(R.string.send_by_mail))
                    }
                }
            } else {
                Text(
                    text = dialogContext.getString(R.string.failed_to_generate_qr_code),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isPhone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = value,
            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

private fun calculateAge(dateOfBirth: String): Int? {
    if (dateOfBirth.isEmpty()) return null
    
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = format.parse(dateOfBirth) ?: return null
        val today = Calendar.getInstance()
        val birthCalendar = Calendar.getInstance().apply { time = birthDate }
        
        var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        
        // Check if birthday hasn't occurred this year
        if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        age
    } catch (e: Exception) {
        null
    }
}

private fun formatBirthday(dateOfBirth: String, context: android.content.Context): String {
    if (dateOfBirth.isEmpty()) return "Not provided"
    
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateOfBirth) ?: return dateOfBirth
        DateFormatUtils.formatDate(date.time, context)
    } catch (e: Exception) {
        dateOfBirth // Return original if parsing fails
    }
}
