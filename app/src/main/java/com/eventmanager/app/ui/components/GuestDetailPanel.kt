package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.data.models.*
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDetailPanel(
    guest: Guest,
    venues: List<VenueEntity>,
    onEdit: (Guest) -> Unit,
    onDelete: (Guest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    
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
                // Modern header with guest name (FIXED AT TOP)
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
                                    text = guest.name,
                                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                if (guest.lastNameAbbreviation.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(if (isPhone) 2.dp else 4.dp))
                                    
                                    Text(
                                        text = guest.lastNameAbbreviation,
                                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = context.getString(R.string.close),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
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
                    // Guest Information Section
                    item {
                        GuestInformationSection(
                            guest = guest,
                            venues = venues,
                            isPhone = isPhone
                        )
                    }
                    
                    // Action Buttons Section
                    item {
                        ActionButtonsSection(
                            guest = guest,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            isPhone = isPhone
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestInformationSection(
    guest: Guest,
    venues: List<VenueEntity>,
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
                    text = LocalContext.current.getString(R.string.guest_information),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            // Name
            InfoRow(
                label = LocalContext.current.getString(R.string.name),
                value = guest.name,
                isPhone = isPhone
            )
            
            // Last name abbreviation (if exists)
            if (guest.lastNameAbbreviation.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.abbreviation),
                    value = guest.lastNameAbbreviation,
                    isPhone = isPhone
                )
            }
            
            // Invitations
            InfoRow(
                label = LocalContext.current.getString(R.string.invitations),
                value = guest.invitations.toString(),
                isPhone = isPhone
            )
            
            // Venue
            InfoRow(
                label = LocalContext.current.getString(R.string.venue),
                value = getVenueDisplayString(guest.venueName, venues),
                isPhone = isPhone
            )
            
            // Notes (if exists)
            if (guest.notes.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.notes),
                    value = guest.notes,
                    isPhone = isPhone
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    guest: Guest,
    onEdit: (Guest) -> Unit,
    onDelete: (Guest) -> Unit,
    isPhone: Boolean
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()
    val context = LocalContext.current
    
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
                    text = context.getString(R.string.actions),
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
                        onClick = { onEdit(guest) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.edit_guest))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(guest) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.delete_guest))
                    }
                }
            } else {
                // Row layout on tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onEdit(guest) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.edit_guest))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(guest) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.delete_guest))
                    }
                }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}



