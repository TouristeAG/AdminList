package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.*
import com.eventmanager.app.R
import android.content.Context

@Composable
fun QuickActionsPanel(
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    jobs: List<Job>,
    venues: List<VenueEntity>,
    isPhone: Boolean = true,
    context: Context
) {
    // Calculate insights
    val totalVolunteers = volunteers.size
    val activeVolunteers = volunteers.count { it.isActive }
    val totalGuests = guests.size
    val totalShifts = jobs.size
    val totalVenues = venues.size
    val averageShiftsPerVenue = if (totalVenues > 0) totalShifts / totalVenues else 0
    
    // Recent volunteer who's been active
    val mostRecentActiveVolunteer = remember(volunteers) {
        volunteers.filter { it.isActive }
            .maxByOrNull { it.id } // Using ID as proxy for recency
    }
    
    // Venue with most shifts
    val venueMostShifts = remember(jobs, venues) {
        if (jobs.isEmpty() || venues.isEmpty()) null
        else {
            val shiftsPerVenue = jobs.groupingBy { it.venueName }.eachCount()
            val maxVenueName = shiftsPerVenue.maxByOrNull { it.value }?.key
            maxVenueName?.let { venueName -> venues.find { it.name == venueName } }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 16.dp else 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insights & Overview",
                    style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            
            // Insights Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp)
            ) {
                // Row 1: Volunteers & Guests
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp)
                ) {
                    InsightCard(
                        icon = Icons.Default.Group,
                        title = "Volunteers",
                        value = "$activeVolunteers / $totalVolunteers",
                        subtitle = "Active",
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f),
                        isPhone = isPhone
                    )
                    
                    InsightCard(
                        icon = Icons.Default.Person,
                        title = "Guests",
                        value = totalGuests.toString(),
                        subtitle = "Total",
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f),
                        isPhone = isPhone
                    )
                }
                
                // Row 2: Shifts & Venues
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp)
                ) {
                    InsightCard(
                        icon = Icons.Default.Build,
                        title = "Shifts",
                        value = totalShifts.toString(),
                        subtitle = "Total",
                        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f),
                        isPhone = isPhone
                    )
                    
                    InsightCard(
                        icon = Icons.Default.LocationOn,
                        title = "Venues",
                        value = totalVenues.toString(),
                        subtitle = "Active",
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.weight(1f),
                        isPhone = isPhone
                    )
                }
                
                // Highlight Cards
                if (mostRecentActiveVolunteer != null) {
                    HighlightCard(
                        icon = Icons.Default.Star,
                        label = "Top Volunteer",
                        name = mostRecentActiveVolunteer.name,
                        detail = "${mostRecentActiveVolunteer.email}",
                        isPhone = isPhone
                    )
                }
                
                if (venueMostShifts != null) {
                    HighlightCard(
                        icon = Icons.Default.CheckCircle,
                        label = "Busiest Venue",
                        name = venueMostShifts.name,
                        detail = "$averageShiftsPerVenue shifts avg",
                        isPhone = isPhone
                    )
                }
                
                // Quick Stat
                StatRowCard(
                    label = "Avg. Shifts per Venue",
                    value = averageShiftsPerVenue.toString(),
                    icon = Icons.Default.TrendingUp,
                    isPhone = isPhone
                )
            }
        }
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    backgroundColor: Color,
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(if (isPhone) 110.dp else 130.dp),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPhone) 12.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Text(
                text = value,
                style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = title,
                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HighlightCard(
    icon: ImageVector,
    label: String,
    name: String,
    detail: String,
    isPhone: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isPhone) 40.dp else 48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(if (isPhone) 8.dp else 10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = name,
                    style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatRowCard(
    label: String,
    value: String,
    icon: ImageVector,
    isPhone: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isPhone) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 18.dp else 20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = label,
                    style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Text(
                text = value,
                style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
