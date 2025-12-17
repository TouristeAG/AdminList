package com.eventmanager.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.ui.components.VolunteerBenefitsPanel
import com.eventmanager.app.ui.components.GuestDetailPanel
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestListScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    venues: List<VenueEntity>,
    onAddGuest: (Guest) -> Unit,
    onUpdateGuest: (Guest) -> Unit,
    onDeleteGuest: (Guest) -> Unit,
    isSyncing: Boolean = false,
    lastSyncTime: Long = 0L,
    headerPinned: Boolean = true // currently not changing layout, but wired for future
) {
    val context = LocalContext.current
    var selectedVenue by remember { mutableStateOf<Venue?>(null) }
    var selectedVenueName by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showVolunteerBenefits by remember { mutableStateOf<Volunteer?>(null) }
    var showGuestDetailPanel by remember { mutableStateOf<Guest?>(null) }
    var showEditGuestDialog by remember { mutableStateOf<Guest?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    
    val isCompact = isCompactScreen()
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()

    // Create a map for O(1) volunteer lookup instead of O(n) find operations
    val volunteersMap = remember(volunteers) {
        volunteers.associateBy { it.id }
    }

    // Optimize filtering with derivedStateOf for better performance
    // derivedStateOf automatically tracks reads from state, so it will recompute when guests, searchText, selectedVenueName, or selectedFilter change
    val filteredGuests = remember(guests, selectedVenueName, searchText, selectedFilter) {
        derivedStateOf {
            val lowerSearchText = searchText.lowercase()
            guests.filter { guest ->
                val matchesVenue = if (selectedVenueName == null) {
                    true  // No filter selected, show all
                } else if (selectedVenueName == "BOTH") {
                    guest.venueName == "BOTH"  // Show only guests marked as "BOTH"
                } else {
                    guest.venueName == selectedVenueName || guest.venueName == "BOTH"  // Show matching or BOTH
                }
                val matchesSearch = searchText.isEmpty() || 
                    guest.name.lowercase().contains(lowerSearchText) ||
                    guest.notes.lowercase().contains(lowerSearchText)
                val matchesFilter = when (selectedFilter) {
                    context.getString(R.string.filter_volunteer_benefits) -> guest.isVolunteerBenefit
                    context.getString(R.string.filter_regular_guests) -> !guest.isVolunteerBenefit
                    else -> true
                }
                matchesVenue && matchesSearch && matchesFilter
            }
        }
    }
    
    // Memoize total invitations calculation
    val totalInvitations = remember(filteredGuests.value) {
        filteredGuests.value.sumOf { it.invitations }
    }
    
    if (headerPinned) {
        // Original behavior: header fixed, only list scrolls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsivePadding)
        ) {
            // Header
            Text(
                text = if (isCompact) context.getString(R.string.guest_list_title) else context.getString(R.string.guest_list_management),
                style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
            
            if (!isCompact) {
                Text(
                    text = context.getString(R.string.guest_list_description),
                    style = getResponsiveBodyTypography(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(responsiveSpacing))
            
            // Venue Selection and Stats
            val venueFilterOptions = generateVenueFilterOptions(venues)
            
            if (isCompact) {
                // Stack vertically on phones
                Column(
                    verticalArrangement = Arrangement.spacedBy(responsiveSpacing)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        venueFilterOptions.forEach { venueOption ->
                            FilterChip(
                                onClick = { 
                                    selectedVenueName = if (selectedVenueName == venueOption.venueName) null else venueOption.venueName
                                },
                                label = { Text(venueOption.displayName) },
                                selected = selectedVenueName == venueOption.venueName,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(getResponsiveButtonHeight())
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.add_guest))
                    }
                }
            } else {
                // Side by side on tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        venueFilterOptions.forEach { venueOption ->
                            FilterChip(
                                onClick = { 
                                    selectedVenueName = if (selectedVenueName == venueOption.venueName) null else venueOption.venueName
                                },
                                label = { Text(venueOption.displayName) },
                                selected = selectedVenueName == venueOption.venueName,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(getResponsiveButtonHeight())
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.add_guest))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Last sync time display
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.last_synced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (lastSyncTime > 0) {
                            val timeAgo = System.currentTimeMillis() - lastSyncTime
                            when {
                                timeAgo < 60000 -> context.getString(R.string.just_now)
                                timeAgo < 3600000 -> context.getString(R.string.minutes_ago, timeAgo / 60000)
                                timeAgo < 86400000 -> context.getString(R.string.hours_ago, timeAgo / 3600000)
                                else -> context.getString(R.string.days_ago, timeAgo / 86400000)
                            }
                        } else {
                            context.getString(R.string.never)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search and Filter Section
            SearchBarWithFilter(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = context.getString(R.string.search_guests_placeholder),
                filterOptions = listOf(context.getString(R.string.filter_volunteer_benefits), context.getString(R.string.filter_regular_guests)),
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(if (isPhone) 8.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = filteredGuests.value.size.toString(),
                            style = if (isPhone) MaterialTheme.typography.titleLarge else getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPhone) context.getString(R.string.guests_count) else context.getString(R.string.total_guests_count),
                            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.weight(1f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(if (isPhone) 8.dp else 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = totalInvitations.toString(),
                            style = if (isPhone) MaterialTheme.typography.titleLarge else getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPhone) context.getString(R.string.invitations_count) else context.getString(R.string.total_invitations_count),
                            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Guest list - Use LazyColumn for lazy loading and better performance
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredGuests.value,
                    key = { guest -> guest.id }
                ) { guest ->
                    GuestCard(
                        guest = guest,
                        volunteersMap = volunteersMap,
                        venues = venues,
                        onDelete = { onDeleteGuest(guest) },
                        onVolunteerClick = { volunteer ->
                            showVolunteerBenefits = volunteer
                        },
                        onGuestClick = { clickedGuest ->
                            showGuestDetailPanel = clickedGuest
                        }
                    )
                }
            }
        }
    } else {
        // New behavior: header and list scroll together
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsivePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = if (isCompact) context.getString(R.string.guest_list_title) else context.getString(R.string.guest_list_management),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
                
                if (!isCompact) {
                    Text(
                        text = context.getString(R.string.guest_list_description),
                        style = getResponsiveBodyTypography(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(responsiveSpacing))
            }
            
            item {
                val venueFilterOptions = generateVenueFilterOptions(venues)
                
                if (isCompact) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(responsiveSpacing)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            venueFilterOptions.forEach { venueOption ->
                                FilterChip(
                                    onClick = { 
                                        selectedVenueName = if (selectedVenueName == venueOption.venueName) null else venueOption.venueName
                                    },
                                    label = { Text(venueOption.displayName) },
                                    selected = selectedVenueName == venueOption.venueName,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(getResponsiveButtonHeight())
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.add_guest))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            venueFilterOptions.forEach { venueOption ->
                                FilterChip(
                                    onClick = { 
                                        selectedVenueName = if (selectedVenueName == venueOption.venueName) null else venueOption.venueName
                                    },
                                    label = { Text(venueOption.displayName) },
                                    selected = selectedVenueName == venueOption.venueName,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.height(getResponsiveButtonHeight())
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.add_guest))
                        }
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.last_synced),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (lastSyncTime > 0) {
                                val timeAgo = System.currentTimeMillis() - lastSyncTime
                                when {
                                    timeAgo < 60000 -> context.getString(R.string.just_now)
                                    timeAgo < 3600000 -> context.getString(R.string.minutes_ago, timeAgo / 60000)
                                    timeAgo < 86400000 -> context.getString(R.string.hours_ago, timeAgo / 3600000)
                                    else -> context.getString(R.string.days_ago, timeAgo / 86400000)
                                }
                            } else {
                                context.getString(R.string.never)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            item {
                SearchBarWithFilter(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    placeholder = context.getString(R.string.search_guests_placeholder),
                    filterOptions = listOf(context.getString(R.string.filter_volunteer_benefits), context.getString(R.string.filter_regular_guests)),
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it }
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isPhone) 8.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = filteredGuests.value.size.toString(),
                                style = if (isPhone) MaterialTheme.typography.titleLarge else getResponsiveTypography(),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPhone) context.getString(R.string.guests_count) else context.getString(R.string.total_guests_count),
                                style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isPhone) 8.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = totalInvitations.toString(),
                                style = if (isPhone) MaterialTheme.typography.titleLarge else getResponsiveTypography(),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPhone) context.getString(R.string.invitations_count) else context.getString(R.string.total_invitations_count),
                                style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            items(
                items = filteredGuests.value,
                key = { guest -> guest.id }
            ) { guest ->
                GuestCard(
                    guest = guest,
                    volunteersMap = volunteersMap,
                    venues = venues,
                    onDelete = { onDeleteGuest(guest) },
                    onVolunteerClick = { volunteer ->
                        showVolunteerBenefits = volunteer
                    },
                    onGuestClick = { clickedGuest ->
                        showGuestDetailPanel = clickedGuest
                    }
                )
            }
        }
    }
    
    // Add Guest Dialog
    if (showAddDialog) {
        AddGuestDialog(
            venues = venues,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, invitations, venueName, notes ->
                val newGuest = Guest(
                    name = name,
                    invitations = invitations,
                    venueName = venueName,
                    notes = notes
                )
                onAddGuest(newGuest)
                showAddDialog = false
            }
        )
    }
    
    // Volunteer Benefits Panel
    if (showVolunteerBenefits != null) {
        val volunteer = showVolunteerBenefits!!
        
        // Memoize benefit status and jobs to prevent unnecessary recompositions
        val context = LocalContext.current
        val settingsManager = remember { com.eventmanager.app.data.sync.SettingsManager(context) }
        val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
        val memoizedBenefitStatus = remember(volunteer.id, jobs, jobTypeConfigs, offsetHours) {
            BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs, offsetHours = offsetHours)
        }
        val memoizedVolunteerJobs = remember(volunteer.id, jobs) {
            jobs.filter { it.volunteerId == volunteer.id }
        }
        
        Dialog(onDismissRequest = { showVolunteerBenefits = null }) {
            VolunteerBenefitsPanel(
                volunteer = volunteer,
                volunteerBenefitStatus = memoizedBenefitStatus,
                volunteerJobs = memoizedVolunteerJobs,
                venues = venues,
                onClose = { showVolunteerBenefits = null }
            )
        }
    }
    
    // Guest Detail Panel
    if (showGuestDetailPanel != null) {
        Dialog(onDismissRequest = { showGuestDetailPanel = null }) {
            GuestDetailPanel(
                guest = showGuestDetailPanel!!,
                venues = venues,
                onEdit = { guest ->
                    showGuestDetailPanel = null
                    showEditGuestDialog = guest
                },
                onDelete = { guest ->
                    showGuestDetailPanel = null
                    onDeleteGuest(guest)
                },
                onClose = { showGuestDetailPanel = null }
            )
        }
    }
    
    // Edit Guest Dialog
    if (showEditGuestDialog != null) {
        EditGuestDialog(
            guest = showEditGuestDialog!!,
            venues = venues,
            onDismiss = { showEditGuestDialog = null },
            onConfirm = { updatedGuest ->
                onUpdateGuest(updatedGuest)
                showEditGuestDialog = null
            }
        )
    }
}

@Composable
fun GuestCard(
    guest: Guest,
    volunteersMap: Map<Long, Volunteer>,
    venues: List<VenueEntity>,
    onDelete: () -> Unit,
    onVolunteerClick: (Volunteer) -> Unit = {},
    onGuestClick: (Guest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompact = isCompactScreen()
    val responsivePadding = getResponsivePadding()
    val responsiveAvatarSize = getResponsiveAvatarSize()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = true) {
                if (guest.isVolunteerBenefit && guest.volunteerId != null) {
                    // Lookup volunteer by ID using map for O(1) performance
                    volunteersMap[guest.volunteerId]?.let { onVolunteerClick(it) }
                } else {
                    // Permanent guest - open detail panel
                    onGuestClick(guest)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(responsivePadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Card(
                modifier = Modifier.size(responsiveAvatarSize),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (guest.isVolunteerBenefit) 
                        MaterialTheme.colorScheme.secondary 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = guest.name.take(1).uppercase(),
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (guest.isVolunteerBenefit) 
                            MaterialTheme.colorScheme.onSecondary 
                        else 
                            MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (guest.isVolunteerBenefit && guest.lastNameAbbreviation.isNotEmpty()) {
                            "${guest.name} (${guest.lastNameAbbreviation})"
                        } else {
                            guest.name
                        },
                        style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (guest.isVolunteerBenefit) {
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    context.getString(R.string.volunteer_label),
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = context.getString(R.string.invitations_text, guest.invitations, if (guest.invitations != 1) "s" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = getVenueDisplayString(guest.venueName, venues),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (guest.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = guest.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Click indicator for volunteer benefits or permanent guests
            if (guest.isVolunteerBenefit && guest.volunteerId != null) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = context.getString(R.string.view_benefits),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                // Permanent guest - show click indicator
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = context.getString(R.string.view_details),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGuestDialog(
    venues: List<VenueEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf("1") }
    var selectedVenueName by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var showVenueDropdown by remember { mutableStateOf(false) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCompact) context.getString(R.string.add_guest) else context.getString(R.string.add_new_guest),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                        }
                    }
                    
                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(context.getString(R.string.guest_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = invitations,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    invitations = it
                                }
                            },
                            label = { Text(context.getString(R.string.number_of_invitations)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = showVenueDropdown,
                            onExpandedChange = { showVenueDropdown = !showVenueDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedVenueName ?: context.getString(R.string.venue),
                                onValueChange = { },
                                readOnly = true,
                                label = { Text(context.getString(R.string.venue)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVenueDropdown)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = showVenueDropdown,
                                onDismissRequest = { showVenueDropdown = false }
                            ) {
                                // Add BOTH/ALL option
                                val allOptionText = if (venues.filter { it.isActive }.size <= 2) {
                                    context.getString(R.string.venue_both)
                                } else {
                                    context.getString(R.string.venue_all)
                                }
                                DropdownMenuItem(
                                    text = { Text(allOptionText) },
                                    onClick = {
                                        selectedVenueName = "BOTH"
                                        showVenueDropdown = false
                                    }
                                )
                                
                                // Add individual venues
                                venues.forEach { venue ->
                                    DropdownMenuItem(
                                        text = { Text(venue.name) },
                                        onClick = {
                                            selectedVenueName = venue.name
                                            showVenueDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(context.getString(R.string.notes)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(context.getString(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                val invitationCount = invitations.toIntOrNull() ?: 1
                                val defaultVenue = venues.firstOrNull { it.isActive }?.name ?: "GROOVE"
                                onConfirm(name, invitationCount, selectedVenueName ?: defaultVenue, notes)
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text(context.getString(R.string.add))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGuestDialog(
    guest: Guest,
    venues: List<VenueEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Guest) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(guest.name) }
    var invitations by remember { mutableStateOf(guest.invitations.toString()) }
    var selectedVenueName by remember { mutableStateOf<String?>(guest.venueName) }
    var notes by remember { mutableStateOf(guest.notes) }
    var showVenueDropdown by remember { mutableStateOf(false) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCompact) context.getString(R.string.edit_guest) else context.getString(R.string.edit_guest_details),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                        }
                    }
                    
                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(context.getString(R.string.guest_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = invitations,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    invitations = it
                                }
                            },
                            label = { Text(context.getString(R.string.number_of_invitations)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = showVenueDropdown,
                            onExpandedChange = { showVenueDropdown = !showVenueDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedVenueName ?: context.getString(R.string.venue),
                                onValueChange = { },
                                readOnly = true,
                                label = { Text(context.getString(R.string.venue)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVenueDropdown)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = showVenueDropdown,
                                onDismissRequest = { showVenueDropdown = false }
                            ) {
                                // Add BOTH/ALL option
                                val allOptionText = if (venues.filter { it.isActive }.size <= 2) {
                                    context.getString(R.string.venue_both)
                                } else {
                                    context.getString(R.string.venue_all)
                                }
                                DropdownMenuItem(
                                    text = { Text(allOptionText) },
                                    onClick = {
                                        selectedVenueName = "BOTH"
                                        showVenueDropdown = false
                                    }
                                )
                                
                                // Add individual venues
                                venues.forEach { venue ->
                                    DropdownMenuItem(
                                        text = { Text(venue.name) },
                                        onClick = {
                                            selectedVenueName = venue.name
                                            showVenueDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(context.getString(R.string.notes)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    // Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(context.getString(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                val invitationCount = invitations.toIntOrNull() ?: 1
                                val defaultVenue = venues.firstOrNull { it.isActive }?.name ?: "GROOVE"
                                val updatedGuest = guest.copy(
                                    name = name,
                                    lastNameAbbreviation = "", // Permanent guests don't have abbreviations
                                    invitations = invitationCount,
                                    venueName = selectedVenueName ?: defaultVenue,
                                    notes = notes
                                )
                                onConfirm(updatedGuest)
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text(context.getString(R.string.update_guest))
                        }
                    }
                }
            }
        }
    }
}

