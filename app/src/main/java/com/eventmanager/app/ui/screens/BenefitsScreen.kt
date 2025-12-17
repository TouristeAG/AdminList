


package com.eventmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.*
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import androidx.compose.ui.platform.LocalContext

@Composable
fun getRankDisplayName(rank: VolunteerRank?): String {
    val context = LocalContext.current
    return when (rank) {
        VolunteerRank.SPECIAL -> "✨SPECIAL✨"
        else -> rank?.name ?: context.getString(R.string.no_rank)
    }
}

@Composable
fun BenefitsScreen(
    volunteers: List<Volunteer>,
    jobs: List<Job> = emptyList(),
    jobTypeConfigs: List<JobTypeConfig> = emptyList()
) {
    val context = LocalContext.current
    val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    
    val volunteerBenefits = remember(volunteers, jobs, jobTypeConfigs, offsetHours) {
        volunteers.map { volunteer ->
            val status = BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs, offsetHours = offsetHours)
            volunteer to status
        }
    }

    val isCompact = isCompactScreen()
    val responsivePadding = getResponsivePadding()
    val responsiveSpacing = getResponsiveSpacing()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(responsivePadding)
    ) {
        // Header
        Text(
            text = if (isCompact) context.getString(R.string.benefits_title) else context.getString(R.string.volunteer_benefits_overview),
            style = getResponsiveTypography(),
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
        
        if (!isCompact) {
            Text(
                text = context.getString(R.string.benefits_description),
                style = getResponsiveBodyTypography(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(responsiveSpacing))
        
        // Search and Filter Section
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = context.getString(R.string.search_volunteers_benefits_placeholder),
            filterOptions = VolunteerRank.values().map { it.name },
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Memoize filtered volunteer benefits to avoid recalculating on every recomposition
        val filteredVolunteerBenefits = remember(volunteerBenefits, searchText, selectedFilter) {
            val lowerSearchText = searchText.lowercase()
            volunteerBenefits.filter { (volunteer, status) ->
                val matchesSearch = searchText.isEmpty() || 
                    volunteer.name.lowercase().contains(lowerSearchText) ||
                    volunteer.email.lowercase().contains(lowerSearchText) ||
                    volunteer.lastNameAbbreviation.lowercase().contains(lowerSearchText)
                val matchesFilter = selectedFilter?.let { filter ->
                    status.rank?.name == filter
                } ?: true
                matchesSearch && matchesFilter
            }
        }
        
        // Statistics
        if (isCompact) {
            // Stack vertically on phones
            Column(
                verticalArrangement = Arrangement.spacedBy(responsiveSpacing)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
                ) {
                    Column(
                        modifier = Modifier.padding(getResponsiveCardPadding()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filteredVolunteerBenefits.count { (_, status) -> status.benefits.isActive }.toString(),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active Benefits",
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
                ) {
                    Column(
                        modifier = Modifier.padding(getResponsiveCardPadding()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filteredVolunteerBenefits.count { (volunteer, _) -> 
                                volunteer.currentRank == VolunteerRank.ORION || volunteer.currentRank == VolunteerRank.VETERAN 
                            }.toString(),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = context.getString(R.string.high_rank),
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Side by side on tablets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(responsiveSpacing)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
                ) {
                    Column(
                        modifier = Modifier.padding(getResponsiveCardPadding()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filteredVolunteerBenefits.count { (_, status) -> status.benefits.isActive }.toString(),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active Benefits",
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.weight(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
                ) {
                    Column(
                        modifier = Modifier.padding(getResponsiveCardPadding()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filteredVolunteerBenefits.count { (volunteer, _) -> 
                                volunteer.currentRank == VolunteerRank.ORION || volunteer.currentRank == VolunteerRank.VETERAN 
                            }.toString(),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = context.getString(R.string.high_rank),
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(responsiveSpacing))
        
        // Benefits list - Use LazyColumn for lazy loading and better performance
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(responsiveSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(
                items = filteredVolunteerBenefits,
                key = { (volunteer, _) -> volunteer.id }
            ) { (volunteer, status) ->
                BenefitCard(volunteer = volunteer, status = status)
            }
        }
    }
}

@Composable
fun BenefitCard(
    volunteer: Volunteer,
    status: VolunteerBenefitStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val benefit = status.benefits
    val isCompact = isCompactScreen()
    val responsivePadding = getResponsiveCardPadding()
    val responsiveAvatarSize = getResponsiveAvatarSize()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            // Volunteer header with avatar-like design
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle
                Card(
                    modifier = Modifier.size(responsiveAvatarSize),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = when (status.rank) {
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
                            style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = when (status.rank) {
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
                
                Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = volunteer.name,
                        style = getResponsiveTitleTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${volunteer.lastNameAbbreviation} • ${volunteer.email}",
                        style = getResponsiveBodyTypography(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Rank badge with status
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text(getRankDisplayName(status.rank)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (status.rank) {
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
                            text = "Expired",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (benefit.validUntil != null) {
                        val timeLeft = benefit.validUntil - System.currentTimeMillis()
                        val daysLeft = timeLeft / (1000 * 60 * 60 * 24)
                        Text(
                            text = if (daysLeft > 0) context.getString(R.string.days_left, daysLeft) else context.getString(R.string.expires_soon),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (daysLeft > 7) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))
            
            // Benefits description
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(if (isCompact) 8.dp else 12.dp)
                ) {
                        Text(
                            text = context.getString(R.string.current_benefits),
                            style = if (isCompact) MaterialTheme.typography.labelLarge else getResponsiveTitleTypography(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = benefit.description,
                            style = getResponsiveBodyTypography()
                        )
                }
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            
            // Benefit details in a column layout
            Column(
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp)
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
                        modifier = Modifier.padding(vertical = if (isCompact) 1.dp else 2.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 14.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(if (isCompact) 6.dp else 8.dp))
                        Text(
                            text = benefitText,
                            style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}




