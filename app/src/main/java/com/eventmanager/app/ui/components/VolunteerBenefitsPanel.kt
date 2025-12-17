package com.eventmanager.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.DateTimeUtils
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import com.eventmanager.app.utils.QRCodeUtils
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerBenefitsPanel(
    volunteer: Volunteer,
    volunteerBenefitStatus: VolunteerBenefitStatus,
    volunteerJobs: List<Job>,
    venues: List<VenueEntity>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    var showQrDialog by remember { mutableStateOf(false) }
    
    val benefit = volunteerBenefitStatus.benefits
    
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
                // Header with volunteer info and close button (FIXED AT TOP)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Avatar circle
                                Card(
                                    modifier = Modifier.size(if (isPhone) 40.dp else 48.dp),
                                    shape = CircleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (volunteerBenefitStatus.rank) {
                                            VolunteerRank.NOVA -> MaterialTheme.colorScheme.primary
                                            VolunteerRank.ETOILE -> MaterialTheme.colorScheme.secondary
                                            VolunteerRank.GALAXIE -> MaterialTheme.colorScheme.tertiary
                                            VolunteerRank.ORION -> MaterialTheme.colorScheme.error
                                            VolunteerRank.VETERAN -> MaterialTheme.colorScheme.surfaceVariant
                                            VolunteerRank.SPECIAL -> MaterialTheme.colorScheme.primaryContainer
                                            null -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = volunteer.name.take(1).uppercase(),
                                            style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = when (volunteerBenefitStatus.rank) {
                                                VolunteerRank.NOVA -> MaterialTheme.colorScheme.onPrimary
                                                VolunteerRank.ETOILE -> MaterialTheme.colorScheme.onSecondary
                                                VolunteerRank.GALAXIE -> MaterialTheme.colorScheme.onTertiary
                                                VolunteerRank.ORION -> MaterialTheme.colorScheme.onError
                                                VolunteerRank.VETERAN -> MaterialTheme.colorScheme.onSurfaceVariant
                                                VolunteerRank.SPECIAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(if (isPhone) 8.dp else 12.dp))
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = volunteer.name,
                                        style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    Text(
                                        text = "${volunteer.lastNameAbbreviation} • ${volunteer.email}",
                                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showQrDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = context.getString(R.string.qr_code),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(onClick = onClose) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                        
                        // Rank badge with status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { },
                                label = { Text(getRankDisplayName(volunteerBenefitStatus.rank)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when (volunteerBenefitStatus.rank) {
                                        VolunteerRank.NOVA -> MaterialTheme.colorScheme.primaryContainer
                                        VolunteerRank.ETOILE -> MaterialTheme.colorScheme.secondaryContainer
                                        VolunteerRank.GALAXIE -> MaterialTheme.colorScheme.tertiaryContainer
                                        VolunteerRank.ORION -> MaterialTheme.colorScheme.errorContainer
                                        VolunteerRank.VETERAN -> MaterialTheme.colorScheme.surfaceVariant
                                        VolunteerRank.SPECIAL -> MaterialTheme.colorScheme.primaryContainer
                                        null -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            )
                            
                            // Status indicator
                            if (!benefit.isActive) {
                                Text(
                                    text = context.getString(R.string.expired),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (benefit.validUntil != null) {
                                val timeLeft = benefit.validUntil - System.currentTimeMillis()
                                val daysLeft = timeLeft / (1000 * 60 * 60 * 24)
                                Text(
                                    text = if (daysLeft > 0) context.getString(R.string.days_left, daysLeft.toInt()) else context.getString(R.string.expires_soon),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (daysLeft > 7) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                // Scrollable content (SCROLLS BELOW HEADER)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = responsivePadding,
                        end = responsivePadding,
                        bottom = responsivePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp)
                ) {
                    // Actions (QR Code)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showQrDialog = true },
                                modifier = if (isPhone) Modifier.fillMaxWidth() else Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.qr_code))
                            }
                        }
                    }
                    
                    // Benefits description
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isPhone) 12.dp else 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Diamond,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Text(
                                        text = context.getString(R.string.current_benefits),
                                        style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                                
                                Text(
                                    text = benefit.description,
                                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Benefit details
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isPhone) 12.dp else 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Text(
                                        text = context.getString(R.string.benefit_details),
                                        style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                                
                                // Show all individual active benefits if there are multiple
                                if (volunteerBenefitStatus.activeBenefits.size > 1) {
                                    Text(
                                        text = context.getString(R.string.active_benefits_multiple_ranks),
                                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    volunteerBenefitStatus.activeBenefits.forEachIndexed { index, activeBenefit ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = activeBenefit.rank?.name ?: "Benefit",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 6.dp),
                                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                            ) {
                                                listOfNotNull(
                                                    if (activeBenefit.freeEntry) context.getString(R.string.free_entry) else null,
                                                    if (activeBenefit.friendInvitation) context.getString(R.string.friend_invitation) else null,
                                                    if (activeBenefit.drinkTokens > 0) context.getString(R.string.drink_tokens, activeBenefit.drinkTokens) else null,
                                                    if (activeBenefit.barDiscount > 0) context.getString(R.string.bar_discount, activeBenefit.barDiscount) else null,
                                                    if (activeBenefit.extraordinaryBenefits) context.getString(R.string.extraordinary_benefits) else null
                                                ).forEach { benefitText ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(vertical = if (isPhone) 1.dp else 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(if (isPhone) 14.dp else 16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(if (isPhone) 6.dp else 8.dp))
                                                        Text(
                                                            text = benefitText,
                                                            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        if (index < volunteerBenefitStatus.activeBenefits.size - 1) {
                                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                                        }
                                    }
                                } else {
                                    // Single benefit - show as before
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(if (isPhone) 4.dp else 8.dp)
                                    ) {
                                        listOfNotNull(
                                            if (benefit.freeEntry) context.getString(R.string.free_entry) else null,
                                            if (benefit.friendInvitation) context.getString(R.string.friend_invitation) else null,
                                            if (benefit.drinkTokens > 0) context.getString(R.string.drink_tokens, benefit.drinkTokens) else null,
                                            if (benefit.barDiscount > 0) context.getString(R.string.bar_discount, benefit.barDiscount) else null,
                                            if (benefit.extraordinaryBenefits) context.getString(R.string.extraordinary_benefits) else null
                                        ).forEach { benefitText ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = if (isPhone) 1.dp else 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(if (isPhone) 14.dp else 16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(if (isPhone) 6.dp else 8.dp))
                                                Text(
                                                    text = benefitText,
                                                    style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Shift History Section
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp)
                        ) {
                            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                            
                            ShiftHistorySection(
                                jobs = volunteerJobs.sortedByDescending { it.date },
                                isPhone = isPhone,
                                venues = venues
                            )
                        }
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
                    text = context.getString(R.string.volunteer_qr_code),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val payload = remember(volunteer) {
                    Gson().toJson(
                        mapOf(
                            "type" to "volunteer",
                            "version" to 1,
                            "id" to volunteer.id,
                            "sheetsId" to (volunteer.sheetsId ?: ""),
                            "name" to volunteer.name,
                            "abbr" to volunteer.lastNameAbbreviation
                        )
                    )
                }
                val qrImage = remember(payload) { QRCodeUtils.generateQrImageBitmap(payload, 1024) }
                val context = LocalContext.current
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (qrImage != null) {
                        Image(
                            bitmap = qrImage,
                            contentDescription = context.getString(R.string.volunteer_qr_code),
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
                                            val file = File(context.cacheDir, "qr_code_${volunteer.id}.png")
                                            val outputStream = FileOutputStream(file)
                                            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                            outputStream.close()
                                            
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.qr_code_subject, volunteer.name))
                                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.qr_code_for_volunteer, volunteer.name))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_qr_code)))
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
                                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.share))
                            }
                            OutlinedButton(
                                onClick = { /* no-op for now */ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.send_by_mail))
                            }
                        }
                    } else {
                        Text(
                            text = context.getString(R.string.failed_to_generate_qr_code),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(context.getString(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ShiftHistorySection(
    jobs: List<Job>,
    isPhone: Boolean,
    venues: List<VenueEntity>
) {
    val context = LocalContext.current
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
                    text = context.getString(R.string.shift_history, jobs.size),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            if (jobs.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_shifts_recorded),
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
                        text = context.getString(R.string.more_shifts, jobs.size - 10),
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
                    text = DateFormatUtils.formatDate(job.date, LocalContext.current),
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
private fun getRankDisplayName(rank: VolunteerRank?): String {
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: "No Rank"
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
    val context = LocalContext.current
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
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        onClick = { /* no-op for now */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send by mail")
                    }
                }
            } else {
                Text(
                    text = "Failed to generate QR code",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
