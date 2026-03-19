package com.eventmanager.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.ui.components.VolunteerBenefitsPanel
import com.eventmanager.app.ui.components.GuestDetailPanel
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

private val GENEVA_ZONE: ZoneId = ZoneId.of("Europe/Zurich")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GuestListScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    venues: List<VenueEntity>,
    onAddGuest: (Guest) -> Unit,
    onUpdateGuest: (Guest) -> Unit,
    onUpdateVolunteer: (Volunteer) -> Unit,
    onDeleteGuest: (Guest) -> Unit,
    onRefreshTemporaryGuests: () -> Unit = {},
    onConfirmEntry: ((Job) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") isSyncing: Boolean = false,
    @Suppress("UNUSED_PARAMETER") lastSyncTime: Long = 0L,
    scrollBehavior: String = SettingsManager.FULL_SCROLL
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsManager = remember { SettingsManager(context) }
    var selectedVenueName by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showVolunteerBenefits by remember { mutableStateOf<Volunteer?>(null) }
    var showGuestDetailPanel by remember { mutableStateOf<Guest?>(null) }
    var showEditGuestDialog by remember { mutableStateOf<Guest?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var showTemporaryGuestsTimeline by remember { mutableStateOf(false) }
    
    val isCompact = isCompactScreen()
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()

    // Create a map for O(1) volunteer lookup instead of O(n) find operations
    val volunteersMap = remember(volunteers) {
        volunteers.associateBy { it.id }
    }

    // Get filter strings once (getString is cheap, no need for remember)
    val filterVolunteerBenefits = context.getString(R.string.filter_volunteer_benefits)
    val filterRegularGuests = context.getString(R.string.filter_regular_guests)
    val guestFilterOptions = remember(filterVolunteerBenefits, filterRegularGuests) {
        listOf(filterVolunteerBenefits, filterRegularGuests)
    }
    val zone = GENEVA_ZONE
    val offsetHours = settingsManager.getDateChangeOffsetHours()
    val effectiveToday = rememberEffectiveToday(zone = zone, offsetHours = offsetHours)

    // Filter guests with proper dependency tracking on all inputs
    // Note: derivedStateOf only tracks Compose State objects, not function parameters like 'guests'
    // So we must include 'guests' as a key to remember() to re-filter when sync updates data
    val filteredGuests = remember(guests, selectedVenueName, searchText, selectedFilter, volunteersMap, effectiveToday) {
        val searchQuery = searchText.trim()
        val hasSearch = searchQuery.isNotEmpty()
        val selectedVenue = selectedVenueName
        val sortable = ArrayList<Pair<String, Guest>>(guests.size)

        for (guest in guests) {
            if (guest.isTemporaryGuest) {
                val eventTs = guest.temporaryEventDate ?: continue
                if (Instant.ofEpochMilli(eventTs).atZone(zone).toLocalDate() != effectiveToday) continue
            }

            if (selectedVenue != null) {
                val matchesVenue = if (selectedVenue == "BOTH") {
                    guest.venueName == "BOTH"
                } else {
                    guest.venueName == selectedVenue || guest.venueName == "BOTH"
                }
                if (!matchesVenue) continue
            }

            if (hasSearch) {
                val matchesSearch =
                    guest.name.contains(searchQuery, ignoreCase = true) ||
                    guest.email.contains(searchQuery, ignoreCase = true) ||
                    guest.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                    guest.notes.contains(searchQuery, ignoreCase = true) ||
                    guest.nfcCardUid.contains(searchQuery, ignoreCase = true)
                if (!matchesSearch) continue
            }

            val matchesFilter = when (selectedFilter) {
                filterVolunteerBenefits -> guest.isVolunteerBenefit
                filterRegularGuests -> !guest.isVolunteerBenefit
                else -> true
            }
            if (!matchesFilter) continue

            val sortKey = if (guest.isVolunteerBenefit && guest.volunteerId != null) {
                volunteersMap[guest.volunteerId]?.name ?: guest.name
            } else {
                guest.name
            }
            sortable.add(sortKey.lowercase() to guest)
        }

        sortable.sortBy { it.first }
        sortable.map { it.second }
    }
    
    // Memoize total invitations calculation
    val totalInvitations = remember(filteredGuests) {
        filteredGuests.sumOf { it.invitations }
    }
    
    // Generate venue filter options (composable function, cannot use remember)
    val venueFilterOptions = generateVenueFilterOptions(venues)

    // Keep temporary guests in sync when entering/re-entering this screen
    val refreshTemporaryGuestsLatest by rememberUpdatedState(onRefreshTemporaryGuests)
    LaunchedEffect(Unit) {
        refreshTemporaryGuestsLatest()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTemporaryGuestsLatest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    when (scrollBehavior) {
        SettingsManager.HEADER_PINNED -> {
            // Original behavior: header fixed, only list scrolls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(responsivePadding)
            ) {
            // Header
            Text(
                text = if (isCompact) context.getString(R.string.guest_list_title) else context.getString(R.string.guest_list_management),
                style = MaterialTheme.typography.headlineMedium,
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
            
            // Search and Filter Section
            SearchBarWithFilter(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = context.getString(R.string.search_guests_placeholder),
                filterOptions = guestFilterOptions,
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            GuestStatsAndTimelineRow(
                guestCount = filteredGuests.size,
                invitationCount = totalInvitations,
                onOpenTimeline = {
                    onRefreshTemporaryGuests()
                    showTemporaryGuestsTimeline = true
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Guest list - Use LazyColumn for lazy loading and better performance
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredGuests,
                    key = { guest -> 
                        guestStableKey(guest)
                    }
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
        }
        SettingsManager.STICKY_FILTERS -> {
            // Page scrolls but filters become sticky at the top
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(responsivePadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header section (scrolls away)
                item {
                    Text(
                        text = if (isCompact) context.getString(R.string.guest_list_title) else context.getString(R.string.guest_list_management),
                        style = MaterialTheme.typography.headlineMedium,
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
                
                // Sticky filter section - becomes pinned when scrolled to top
                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(bottom = 8.dp)
                    ) {
                        SearchBarWithFilter(
                            searchText = searchText,
                            onSearchTextChange = { searchText = it },
                            placeholder = context.getString(R.string.search_guests_placeholder),
                            filterOptions = guestFilterOptions,
                            selectedFilter = selectedFilter,
                            onFilterChange = { selectedFilter = it }
                        )
                    }
                }
                
                // Statistics section - scrolls with the list
                item {
                    GuestStatsAndTimelineRow(
                        guestCount = filteredGuests.size,
                        invitationCount = totalInvitations,
                        onOpenTimeline = {
                            onRefreshTemporaryGuests()
                            showTemporaryGuestsTimeline = true
                        }
                    )
                }
                
                items(
                    items = filteredGuests,
                    key = { guest -> 
                        guestStableKey(guest)
                    }
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
        else -> {
            // FULL_SCROLL: header and list scroll together
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(responsivePadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (isCompact) context.getString(R.string.guest_list_title) else context.getString(R.string.guest_list_management),
                        style = MaterialTheme.typography.headlineMedium,
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
                    SearchBarWithFilter(
                        searchText = searchText,
                        onSearchTextChange = { searchText = it },
                        placeholder = context.getString(R.string.search_guests_placeholder),
                        filterOptions = guestFilterOptions,
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it }
                    )
                }
                
                item {
                    GuestStatsAndTimelineRow(
                        guestCount = filteredGuests.size,
                        invitationCount = totalInvitations,
                        onOpenTimeline = {
                            onRefreshTemporaryGuests()
                            showTemporaryGuestsTimeline = true
                        }
                    )
                }
                
                items(
                    items = filteredGuests,
                    key = { guest -> 
                        guestStableKey(guest)
                    }
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
    }
    
    if (showTemporaryGuestsTimeline) {
        TemporaryGuestsTimelineDialog(
            guests = guests,
            volunteers = volunteers,
            jobs = jobs,
            jobTypeConfigs = jobTypeConfigs,
            onGuestClick = { guest ->
                showTemporaryGuestsTimeline = false
                showGuestDetailPanel = guest
            },
            onDismiss = { showTemporaryGuestsTimeline = false }
        )
    }

    // Add Guest Dialog
    if (showAddDialog) {
        AddGuestDialog(
            venues = venues,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, email, phoneNumber, invitations, venueName, notes ->
                val newGuest = Guest(
                    name = name,
                    email = email,
                    phoneNumber = phoneNumber,
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
        val benefitContext = LocalContext.current
        val settingsManager = remember { SettingsManager(benefitContext) }
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
                onClose = { showVolunteerBenefits = null },
                onConfirmEntry = onConfirmEntry,
                onAssignNfcUid = { updatedVolunteer, uid ->
                    onUpdateVolunteer(
                        updatedVolunteer.copy(
                            nfcCardUid = uid,
                            lastModified = System.currentTimeMillis()
                        )
                    )
                    showVolunteerBenefits = updatedVolunteer.copy(nfcCardUid = uid)
                }
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
                onAssignNfcUid = { guest, uid ->
                    onUpdateGuest(
                        guest.copy(
                            nfcCardUid = uid,
                            lastModified = System.currentTimeMillis()
                        )
                    )
                    showGuestDetailPanel = guest.copy(nfcCardUid = uid)
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
    volunteersMap: Map<String, Volunteer>,
    venues: List<VenueEntity>,
    @Suppress("UNUSED_PARAMETER") onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onVolunteerClick: (Volunteer) -> Unit = {},
    onGuestClick: (Guest) -> Unit = {}
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
                    } else if (guest.isTemporaryGuest) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    context.getString(R.string.temp_guest_chip_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
                
                if (guest.isTemporaryGuest && guest.temporaryArtistName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = guest.temporaryArtistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (!guest.isTemporaryGuest) {
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
                                text = context.getString(R.string.invitations_text, guest.invitations),
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
                }
                
            }
            
            // Click indicator for volunteer benefits or permanent guests
            if (guest.isVolunteerBenefit && guest.volunteerId != null) {
                @Suppress("DEPRECATION")
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = context.getString(R.string.view_benefits),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                // Permanent guest - show click indicator
                @Suppress("DEPRECATION")
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

@Composable
private fun GuestStatsAndTimelineRow(
    guestCount: Int,
    invitationCount: Int,
    onOpenTimeline: () -> Unit
) {
    val context = LocalContext.current
    val isPhone = !isTablet()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isPhone) 88.dp else 96.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isPhone) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = guestCount.toString(),
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPhone) context.getString(R.string.guests_count) else context.getString(R.string.total_guests_count),
                        style = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isPhone) 88.dp else 96.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isPhone) 8.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = invitationCount.toString(),
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPhone) context.getString(R.string.invitations_count) else context.getString(R.string.total_invitations_count),
                        style = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .height(if (isPhone) 88.dp else 96.dp)
                .clickable { onOpenTimeline() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPhone) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = context.getString(R.string.temp_guest_timeline_button),
                    style = if (isPhone) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class TempGuestRange(@androidx.annotation.StringRes val labelRes: Int, val days: Long?) {
    THREE_DAYS(R.string.temp_guest_range_three_days, 3),
    ONE_WEEK(R.string.temp_guest_range_one_week, 7),
    TWO_WEEKS(R.string.temp_guest_range_two_weeks, 14),
    ONE_MONTH(R.string.temp_guest_range_one_month, 30),
    SIX_MONTHS(R.string.temp_guest_range_six_months, 182),
    ONE_YEAR(R.string.temp_guest_range_one_year, 365),
    ALL_TIME(R.string.temp_guest_range_all_time, null)
}

private enum class TempGuestRangeShortLabel(@androidx.annotation.StringRes val labelRes: Int) {
    THREE_DAYS(R.string.temp_guest_range_three_days_short),
    ONE_WEEK(R.string.temp_guest_range_one_week_short),
    TWO_WEEKS(R.string.temp_guest_range_two_weeks_short),
    ONE_MONTH(R.string.temp_guest_range_one_month_short),
    SIX_MONTHS(R.string.temp_guest_range_six_months_short),
    ONE_YEAR(R.string.temp_guest_range_one_year_short),
    ALL_TIME(R.string.temp_guest_range_all_time_short)
}

private data class VolunteerAccessEntry(
    val volunteerName: String,
    val volunteerNameLower: String,
    val volunteerNfcUidLower: String,
    val accessStartDate: LocalDate,
    val accessEndDate: LocalDate
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemporaryGuestsTimelineDialog(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    onGuestClick: (Guest) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val zone = GENEVA_ZONE
    val offsetHours = remember(settingsManager) { settingsManager.getDateChangeOffsetHours() }
    val effectiveToday = rememberEffectiveToday(zone = zone, offsetHours = offsetHours)

    var searchText by remember { mutableStateOf("") }
    var selectedRange by remember { mutableStateOf(TempGuestRange.THREE_DAYS) }
    var selectedPage by remember { mutableStateOf(0) }
    val rangeToShortLabel = remember(context) {
        mapOf(
            TempGuestRange.THREE_DAYS to context.getString(TempGuestRangeShortLabel.THREE_DAYS.labelRes),
            TempGuestRange.ONE_WEEK to context.getString(TempGuestRangeShortLabel.ONE_WEEK.labelRes),
            TempGuestRange.TWO_WEEKS to context.getString(TempGuestRangeShortLabel.TWO_WEEKS.labelRes),
            TempGuestRange.ONE_MONTH to context.getString(TempGuestRangeShortLabel.ONE_MONTH.labelRes),
            TempGuestRange.SIX_MONTHS to context.getString(TempGuestRangeShortLabel.SIX_MONTHS.labelRes),
            TempGuestRange.ONE_YEAR to context.getString(TempGuestRangeShortLabel.ONE_YEAR.labelRes),
            TempGuestRange.ALL_TIME to context.getString(TempGuestRangeShortLabel.ALL_TIME.labelRes)
        )
    }
    val shortLabelToRange = remember(rangeToShortLabel) { rangeToShortLabel.entries.associate { it.value to it.key } }
    val rangeFilterOptions = remember(rangeToShortLabel) { TempGuestRange.values().mapNotNull { rangeToShortLabel[it] } }

    val temporaryGuestsWithDate = remember(guests, zone) {
        guests.asSequence()
            .filter { it.isTemporaryGuest }
            .mapNotNull { guest ->
                val ts = guest.temporaryEventDate ?: return@mapNotNull null
                guest to Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            }
            .toList()
    }

    val filtered = remember(temporaryGuestsWithDate, searchText, selectedRange, effectiveToday) {
        val searchQuery = searchText.trim()
        val hasSearch = searchQuery.isNotEmpty()
        val rangeDays = selectedRange.days
        temporaryGuestsWithDate.filter { (guest, eventDate) ->
            val inRange = if (rangeDays == null) {
                true
            } else {
                kotlin.math.abs(ChronoUnit.DAYS.between(effectiveToday, eventDate)) <= rangeDays
            }
            val matchesSearch = !hasSearch ||
                guest.name.contains(searchQuery, ignoreCase = true) ||
                guest.temporaryArtistName.contains(searchQuery, ignoreCase = true) ||
                guest.temporaryContactPhone.contains(searchQuery, ignoreCase = true) ||
                guest.notes.contains(searchQuery, ignoreCase = true) ||
                guest.nfcCardUid.contains(searchQuery, ignoreCase = true)
            inRange && matchesSearch
        }
    }

    val futureAndPastGuests = remember(filtered, effectiveToday) {
        filtered.partition { (_, eventDate) -> !eventDate.isBefore(effectiveToday) }
    }
    val futureGuests = remember(futureAndPastGuests) {
        futureAndPastGuests.first.map { it.first }
            .sortedWith(compareBy<Guest> { it.temporaryEventDate ?: Long.MAX_VALUE }.thenBy { it.name.lowercase() })
    }

    val pastGuests = remember(futureAndPastGuests) {
        futureAndPastGuests.second.map { it.first }
            .sortedWith(compareByDescending<Guest> { it.temporaryEventDate ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() })
    }

    val benefitRelevantJobTypeNames = remember(jobTypeConfigs) {
        jobTypeConfigs.asSequence()
            .filter { config ->
                config.isActive && (
                    config.isShiftJob ||
                        config.isOrionJob ||
                        (config.benefitSystemType == BenefitSystemType.MANUAL && config.manualRewards != null)
                    )
            }
            .map { it.name }
            .toSet()
    }
    val volunteersById = remember(volunteers) { volunteers.associateBy { it.id } }
    val volunteerAccessEntries = remember(jobs, benefitRelevantJobTypeNames, volunteersById, zone, offsetHours, jobTypeConfigs) {
        val rangesByVolunteer = mutableMapOf<String, MutableList<Pair<LocalDate, LocalDate>>>()
        val jobsByVolunteerId = jobs
            .asSequence()
            .filter { it.jobTypeName in benefitRelevantJobTypeNames }
            .groupBy { it.volunteerId }

        jobsByVolunteerId.forEach { (volunteerId, volunteerJobsRaw) ->
            val volunteer = volunteersById[volunteerId] ?: return@forEach
            if (volunteerJobsRaw.isEmpty()) return@forEach

            // Sort once and iterate prefix subLists to avoid copying lists.
            val volunteerJobs = volunteerJobsRaw.sortedBy { it.date }
            for (index in volunteerJobs.indices) {
                val checkpointTs = volunteerJobs[index].date
                val jobsUpToCheckpoint = volunteerJobs.subList(0, index + 1)

                val status = BenefitCalculator.calculateVolunteerBenefitStatusFromVolunteerJobs(
                    volunteer = volunteer,
                    volunteerJobs = jobsUpToCheckpoint,
                    jobTypeConfigs = jobTypeConfigs,
                    currentTime = checkpointTs,
                    offsetHours = offsetHours
                )

                val benefit = status.benefits
                val validUntil = benefit.validUntil
                if (!benefit.isActive || !benefit.guestListAccess || validUntil == null || validUntil <= checkpointTs) {
                    continue
                }

                val startDate = Instant.ofEpochMilli(checkpointTs).atZone(zone).toLocalDate()
                val endDate = Instant.ofEpochMilli(validUntil).atZone(zone).toLocalDate()
                rangesByVolunteer.getOrPut(volunteer.id) { mutableListOf() }.add(startDate to endDate)
            }
        }

        val entries = ArrayList<VolunteerAccessEntry>()
        rangesByVolunteer.forEach { (volunteerId, ranges) ->
            val volunteer = volunteersById[volunteerId] ?: return@forEach
            if (ranges.isEmpty()) return@forEach

            ranges.sortBy { it.first }
            var currentStart = ranges[0].first
            var currentEnd = ranges[0].second

            for (i in 1 until ranges.size) {
                val nextStart = ranges[i].first
                val nextEnd = ranges[i].second
                if (!nextStart.isAfter(currentEnd.plusDays(1))) {
                    if (nextEnd.isAfter(currentEnd)) currentEnd = nextEnd
                } else {
                    entries.add(
                        VolunteerAccessEntry(
                            volunteerName = volunteer.name,
                            volunteerNameLower = volunteer.name.lowercase(),
                            volunteerNfcUidLower = volunteer.nfcCardUid.lowercase(),
                            accessStartDate = currentStart,
                            accessEndDate = currentEnd
                        )
                    )
                    currentStart = nextStart
                    currentEnd = nextEnd
                }
            }

            entries.add(
                VolunteerAccessEntry(
                    volunteerName = volunteer.name,
                    volunteerNameLower = volunteer.name.lowercase(),
                    volunteerNfcUidLower = volunteer.nfcCardUid.lowercase(),
                    accessStartDate = currentStart,
                    accessEndDate = currentEnd
                )
            )
        }

        entries
    }

    val filteredVolunteerEntries = remember(volunteerAccessEntries, searchText, selectedRange, effectiveToday) {
        val searchQuery = searchText.trim().lowercase()
        val hasSearch = searchQuery.isNotEmpty()
        val rangeDays = selectedRange.days
        volunteerAccessEntries.filter { entry ->
            val inRange = if (rangeDays == null) {
                true
            } else {
                val fromDate = effectiveToday.minusDays(rangeDays)
                val toDate = effectiveToday.plusDays(rangeDays)
                !entry.accessEndDate.isBefore(fromDate) && !entry.accessStartDate.isAfter(toDate)
            }
            val matchesSearch = !hasSearch ||
                entry.volunteerNameLower.contains(searchQuery) ||
                entry.volunteerNfcUidLower.contains(searchQuery)
            inRange && matchesSearch
        }
    }

    val futureAndPastVolunteerEntries = remember(filteredVolunteerEntries, effectiveToday) {
        filteredVolunteerEntries.partition { !it.accessEndDate.isBefore(effectiveToday) }
    }
    val futureVolunteerEntries = remember(futureAndPastVolunteerEntries) {
        futureAndPastVolunteerEntries.first
            .sortedWith(compareBy<VolunteerAccessEntry> { it.accessStartDate }.thenBy { it.volunteerName.lowercase() })
    }

    val pastVolunteerEntries = remember(futureAndPastVolunteerEntries) {
        futureAndPastVolunteerEntries.second
            .sortedWith(compareByDescending<VolunteerAccessEntry> { it.accessEndDate }.thenBy { it.volunteerName.lowercase() })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.temp_guest_timeline_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                    }
                }

                SearchBarWithFilter(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    placeholder = context.getString(R.string.search_guests_placeholder),
                    filterOptions = rangeFilterOptions,
                    selectedFilter = rangeToShortLabel[selectedRange],
                    onFilterChange = { selected ->
                        selectedRange = selected?.let { shortLabelToRange[it] } ?: TempGuestRange.THREE_DAYS
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedPage) {
                    Tab(
                        selected = selectedPage == 0,
                        onClick = { selectedPage = 0 },
                        text = { Text(context.getString(R.string.temp_guest_tab_temp)) }
                    )
                    Tab(
                        selected = selectedPage == 1,
                        onClick = { selectedPage = 1 },
                        text = { Text(context.getString(R.string.temp_guest_tab_volunteer)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (selectedPage == 0) {
                        item {
                            TimelineSectionHeader(
                                title = context.getString(R.string.temp_guest_section_upcoming),
                                count = futureGuests.size,
                                isFuture = true
                            )
                        }
                        if (futureGuests.isEmpty()) {
                            item { Text(context.getString(R.string.temp_guest_none_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(
                                items = futureGuests,
                                key = { guest -> "temp_future_${guestStableKey(guest)}_${guest.temporaryEventDate}" }
                            ) { guest ->
                                TemporaryGuestTimelineItem(
                                    guest = guest,
                                    onClick = { onGuestClick(guest) }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            TimelineSectionHeader(
                                title = context.getString(R.string.temp_guest_section_past),
                                count = pastGuests.size,
                                isFuture = false
                            )
                        }
                        if (pastGuests.isEmpty()) {
                            item { Text(context.getString(R.string.temp_guest_none_past), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(
                                items = pastGuests,
                                key = { guest -> "temp_past_${guestStableKey(guest)}_${guest.temporaryEventDate}" }
                            ) { guest ->
                                TemporaryGuestTimelineItem(
                                    guest = guest,
                                    onClick = { onGuestClick(guest) }
                                )
                            }
                        }
                    } else {
                        item {
                            TimelineSectionHeader(
                                title = context.getString(R.string.temp_guest_section_upcoming),
                                count = futureVolunteerEntries.size,
                                isFuture = true
                            )
                        }
                        if (futureVolunteerEntries.isEmpty()) {
                            item { Text(context.getString(R.string.temp_guest_none_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(
                                items = futureVolunteerEntries,
                                key = { entry -> "vol_future_${entry.volunteerName}_${entry.accessStartDate}_${entry.accessEndDate}" }
                            ) { entry ->
                                VolunteerTimelineItem(entry = entry)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            TimelineSectionHeader(
                                title = context.getString(R.string.temp_guest_section_past),
                                count = pastVolunteerEntries.size,
                                isFuture = false
                            )
                        }
                        if (pastVolunteerEntries.isEmpty()) {
                            item { Text(context.getString(R.string.temp_guest_none_past), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(
                                items = pastVolunteerEntries,
                                key = { entry -> "vol_past_${entry.volunteerName}_${entry.accessStartDate}_${entry.accessEndDate}" }
                            ) { entry ->
                                VolunteerTimelineItem(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemporaryGuestTimelineItem(
    guest: Guest,
    onClick: () -> Unit
) {
    val eventDateText = remember(guest.temporaryEventDate) {
        guest.temporaryEventDate?.let { com.eventmanager.app.data.utils.DateTimeUtils.formatGenevaDateOnly(it) } ?: "-"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(42.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = guest.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guest.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = guest.temporaryArtistName.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = eventDateText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VolunteerTimelineItem(
    entry: VolunteerAccessEntry
) {
    val rangeText = remember(entry.accessStartDate, entry.accessEndDate) {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.getDefault())
        "${entry.accessStartDate.format(formatter)} - ${entry.accessEndDate.format(formatter)}"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(42.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.volunteerName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.volunteerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    title: String,
    count: Int,
    isFuture: Boolean
) {
    val containerColor = if (isFuture) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isFuture) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

private fun getEffectiveToday(zone: ZoneId, offsetHours: Int): LocalDate {
    val now = java.time.ZonedDateTime.now(zone)
    val effectiveNow = if (offsetHours != 0 && now.hour < offsetHours) {
        now.minusDays(1)
    } else {
        now
    }
    return effectiveNow.toLocalDate()
}

private fun guestStableKey(guest: Guest): String {
    val typePrefix = when {
        guest.isTemporaryGuest -> "temp"
        guest.isVolunteerBenefit -> "vol"
        else -> "reg"
    }
    return guest.sheetsId?.let { "$typePrefix:$it" }
        ?: "$typePrefix:${guest.id}_${guest.name}_${guest.venueName}_${guest.temporaryEventDate}"
}

@Composable
private fun rememberEffectiveToday(zone: ZoneId, offsetHours: Int): LocalDate {
    val effectiveTodayState = produceState(
        initialValue = getEffectiveToday(zone, offsetHours),
        key1 = zone,
        key2 = offsetHours
    ) {
        while (true) {
            delay(60_000)
            value = getEffectiveToday(zone, offsetHours)
        }
    }
    return effectiveTodayState.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGuestDialog(
    venues: List<VenueEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, String, String) -> Unit // name, email, phoneNumber, invitations, venueName, notes
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var invitations by remember { mutableStateOf("1") }
    var selectedVenueName by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var showVenueDropdown by remember { mutableStateOf(false) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()
    val isTabletDevice = isTablet()
    val tabletMaxWidth = getTabletConstrainedDialogMaxWidth()
    val tabletMaxHeight = getTabletConstrainedDialogMaxHeight()
    
    // Memoize active venues to avoid repeated filtering
    val activeVenues = remember(venues) { venues.filter { it.isActive } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = !isTabletDevice
        )
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isTabletDevice) {
                        Modifier
                            .widthIn(max = tabletMaxWidth)
                            .heightIn(max = tabletMaxHeight)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                    }
                )
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
                            style = if (isTabletDevice) getTabletConstrainedTitleTypography() else getResponsiveTypography(),
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
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(context.getString(R.string.guest_email)) },
                            placeholder = { Text(context.getString(R.string.guest_email_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text(context.getString(R.string.guest_phone_number)) },
                            placeholder = { Text(context.getString(R.string.guest_phone_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                                val allOptionText = if (activeVenues.size <= 2) {
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
                                
                                // Add individual venues (only active ones)
                                activeVenues.forEach { venue ->
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
                                val defaultVenue = activeVenues.firstOrNull()?.name ?: "GROOVE"
                                onConfirm(name, email, phoneNumber, invitationCount, selectedVenueName ?: defaultVenue, notes)
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
    val isTemporaryGuest = guest.isTemporaryGuest
    var name by remember { mutableStateOf(guest.name) }
    var email by remember { mutableStateOf(guest.email) }
    var phoneNumber by remember { mutableStateOf(guest.phoneNumber) }
    var invitations by remember { mutableStateOf(guest.invitations.toString()) }
    var selectedVenueName by remember { mutableStateOf<String?>(guest.venueName) }
    var notes by remember { mutableStateOf(guest.notes) }
    var temporaryArtistName by remember { mutableStateOf(guest.temporaryArtistName) }
    var temporaryContactPhone by remember { mutableStateOf(guest.temporaryContactPhone) }
    var temporaryEventDateInput by remember {
        mutableStateOf(
            guest.temporaryEventDate?.let {
                Instant.ofEpochMilli(it).atZone(GENEVA_ZONE).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            } ?: ""
        )
    }
    var showVenueDropdown by remember { mutableStateOf(false) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()
    val isTabletDevice = isTablet()
    val tabletMaxWidth = getTabletConstrainedDialogMaxWidth()
    val tabletMaxHeight = getTabletConstrainedDialogMaxHeight()
    
    // Memoize active venues to avoid repeated filtering
    val activeVenues = remember(venues) { venues.filter { it.isActive } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = !isTabletDevice
        )
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isTabletDevice) {
                        Modifier
                            .widthIn(max = tabletMaxWidth)
                            .heightIn(max = tabletMaxHeight)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                    }
                )
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
                            style = if (isTabletDevice) getTabletConstrainedTitleTypography() else getResponsiveTypography(),
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

                        if (isTemporaryGuest) {
                            OutlinedTextField(
                                value = temporaryArtistName,
                                onValueChange = { temporaryArtistName = it },
                                label = { Text(context.getString(R.string.temp_guest_artist_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = temporaryEventDateInput,
                                onValueChange = { temporaryEventDateInput = it },
                                label = { Text(context.getString(R.string.temp_guest_event_date_label)) },
                                placeholder = { Text("YYYY-MM-DD") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = temporaryContactPhone,
                                onValueChange = { temporaryContactPhone = it },
                                label = { Text(context.getString(R.string.temp_guest_contact_phone_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text(context.getString(R.string.guest_email)) },
                                placeholder = { Text(context.getString(R.string.guest_email_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text(context.getString(R.string.guest_phone_number)) },
                                placeholder = { Text(context.getString(R.string.guest_phone_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
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
                                    val allOptionText = if (activeVenues.size <= 2) {
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

                                    // Add individual venues (only active ones)
                                    activeVenues.forEach { venue ->
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
                        val parsedTempEventDate = if (isTemporaryGuest) {
                            runCatching {
                                LocalDate.parse(temporaryEventDateInput.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                            }.getOrNull()
                        } else {
                            null
                        }
                        TextButton(onClick = onDismiss) {
                            Text(context.getString(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                val updatedGuest = if (isTemporaryGuest) {
                                    guest.copy(
                                        name = name,
                                        notes = notes,
                                        temporaryArtistName = temporaryArtistName,
                                        temporaryContactPhone = temporaryContactPhone,
                                        temporaryEventDate = parsedTempEventDate?.atStartOfDay(GENEVA_ZONE)?.toInstant()?.toEpochMilli()
                                    )
                                } else {
                                    val invitationCount = invitations.toIntOrNull() ?: 1
                                    val defaultVenue = activeVenues.firstOrNull()?.name ?: "GROOVE"
                                    guest.copy(
                                        name = name,
                                        email = email,
                                        phoneNumber = phoneNumber,
                                        lastNameAbbreviation = "", // Permanent guests don't have abbreviations
                                        invitations = invitationCount,
                                        venueName = selectedVenueName ?: defaultVenue,
                                        notes = notes
                                    )
                                }
                                onConfirm(updatedGuest)
                            },
                            enabled = name.isNotBlank() && (!isTemporaryGuest || parsedTempEventDate != null)
                        ) {
                            Text(context.getString(R.string.update_guest))
                        }
                    }
                }
            }
        }
    }
}

