package com.eventmanager.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import com.eventmanager.app.ui.components.VolunteerDetailPanel
import com.eventmanager.app.ui.components.BirthdayDatePicker
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.ui.components.SearchBarWithFilter
import com.eventmanager.app.utils.ValidationUtils
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerScreen(
    volunteers: List<Volunteer>,
    volunteerJobs: List<Job>,
    venues: List<VenueEntity>,
    onAddVolunteer: (Volunteer) -> Unit,
    onUpdateVolunteer: (Volunteer) -> Unit,
    onDeleteVolunteer: (Volunteer) -> Unit,
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    headerPinned: Boolean = true
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Volunteer?>(null) }
    var showDetailPanel by remember { mutableStateOf<Volunteer?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val isCompact = isCompactScreen()
    val responsivePadding = getResponsivePadding()
    val responsiveSpacing = getResponsiveSpacing()

    // Compute filtered volunteers once for both layouts
    val filteredVolunteers = remember(volunteers, searchText, selectedFilter) {
        val lowerSearchText = searchText.lowercase()
        volunteers.filter { volunteer ->
            val matchesSearch = searchText.isEmpty() || 
                volunteer.name.lowercase().contains(lowerSearchText) ||
                volunteer.email.lowercase().contains(lowerSearchText) ||
                volunteer.lastNameAbbreviation.lowercase().contains(lowerSearchText)
            
            val matchesFilter = selectedFilter?.let { filter ->
                when (filter) {
                    context.getString(R.string.filter_all) -> true
                    context.getString(R.string.filter_active) -> volunteer.isActive
                    context.getString(R.string.filter_inactive) -> !volunteer.isActive
                    else -> volunteer.currentRank?.name == filter
                }
            } ?: true
            
            matchesSearch && matchesFilter
        }
    }
    
    if (headerPinned) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsivePadding)
        ) {
            // Header
            if (isCompact) {
                // Stack vertically on phones
                Column(
                    verticalArrangement = Arrangement.spacedBy(responsiveSpacing)
                ) {
                    Text(
                        text = context.getString(R.string.volunteers_title),
                        style = getResponsiveTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(getResponsiveButtonHeight())
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.add_volunteer))
                    }
                }
            } else {
                // Side by side on tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.volunteer_manager),
                        style = getResponsiveTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(getResponsiveButtonHeight())
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.add_volunteer))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(responsiveSpacing))
            
            // Search and Filter Section
            SearchBarWithFilter(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                placeholder = context.getString(R.string.search_volunteers_placeholder),
                filterOptions = listOf(context.getString(R.string.filter_active), context.getString(R.string.filter_inactive)) + VolunteerRank.values().map { it.name },
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${filteredVolunteers.size} of ${volunteers.size} volunteers",
                style = getResponsiveBodyTypography(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
            
            // Volunteers list - Use LazyColumn for lazy loading and better performance
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredVolunteers,
                    key = { volunteer -> volunteer.id }
                ) { volunteer ->
                    VolunteerCard(
                        volunteer = volunteer,
                        onClick = { showDetailPanel = volunteer }
                    )
                }
            }
        }
    } else {
        // Whole page (header + list) scrolls together
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(responsivePadding),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
        ) {
            item {
                if (isCompact) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(responsiveSpacing)
                    ) {
                        Text(
                            text = context.getString(R.string.volunteers_title),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(getResponsiveButtonHeight())
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.add_volunteer))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = context.getString(R.string.volunteer_manager),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.height(getResponsiveButtonHeight())
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.add_volunteer))
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(responsiveSpacing))
            }
            
            item {
                SearchBarWithFilter(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    placeholder = context.getString(R.string.search_volunteers_placeholder),
                    filterOptions = listOf(context.getString(R.string.filter_active), context.getString(R.string.filter_inactive)) + VolunteerRank.values().map { it.name },
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                Column {
                    Text(
                        text = "${filteredVolunteers.size} of ${volunteers.size} volunteers",
                        style = getResponsiveBodyTypography(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
                }
            }
            
            items(
                items = filteredVolunteers,
                key = { volunteer -> volunteer.id }
            ) { volunteer ->
                VolunteerCard(
                    volunteer = volunteer,
                    onClick = { showDetailPanel = volunteer }
                )
            }
        }
    }
    
    // Add Volunteer Dialog
    if (showAddDialog) {
        AddVolunteerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, abbreviation, email, phone, dateOfBirth, gender ->
                val newVolunteer = Volunteer(
                    name = name,
                    lastNameAbbreviation = abbreviation,
                    email = email,
                    phoneNumber = phone,
                    dateOfBirth = dateOfBirth,
                    gender = gender
                )
                onAddVolunteer(newVolunteer)
                showAddDialog = false
            }
        )
    }
    
    // Edit Volunteer Dialog
    showEditDialog?.let { volunteer ->
        EditVolunteerDialog(
            volunteer = volunteer,
            onDismiss = { showEditDialog = null },
            onConfirm = { updatedVolunteer ->
                onUpdateVolunteer(updatedVolunteer)
                showEditDialog = null
            }
        )
    }
    
    // Volunteer Detail Panel
    if (showDetailPanel != null) {
        // Memoize filtered jobs to prevent unnecessary recompositions
        val filteredJobsForVolunteer = remember(showDetailPanel?.id, volunteerJobs) {
            volunteerJobs.filter { it.volunteerId == showDetailPanel?.id }
        }
        
        Dialog(onDismissRequest = { showDetailPanel = null }) {
            VolunteerDetailPanel(
                volunteer = showDetailPanel!!,
                volunteerJobs = filteredJobsForVolunteer,
                venues = venues,
                jobTypeConfigs = jobTypeConfigs,
                onEdit = { volunteer ->
                    showDetailPanel = null
                    showEditDialog = volunteer
                },
                onDelete = { volunteer ->
                    showDetailPanel = null
                    onDeleteVolunteer(volunteer)
                },
                onClose = { showDetailPanel = null }
            )
        }
    }
    
    // Edit Volunteer Dialog
    if (showEditDialog != null) {
        EditVolunteerDialog(
            volunteer = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { updatedVolunteer ->
                onUpdateVolunteer(updatedVolunteer)
                showEditDialog = null
            }
        )
    }
}

@Composable
fun VolunteerCard(
    volunteer: Volunteer,
    onClick: (Volunteer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompact = isCompactScreen()
    val responsivePadding = getResponsiveCardPadding()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(volunteer) },
        elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            // Header with volunteer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Activity status - use the isActive field from the volunteer
                    val isActive = volunteer.isActive
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = volunteer.name,
                            style = getResponsiveTitleTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Activity indicator
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = if (isActive) context.getString(R.string.active_status) else context.getString(R.string.inactive_status),
                            modifier = Modifier.size(12.dp),
                            tint = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                    }
                    
                    Text(
                        text = "${volunteer.lastNameAbbreviation} • ${volunteer.email}",
                        style = getResponsiveBodyTypography(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = volunteer.phoneNumber,
                        style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Activity status text
                    Text(
                        text = if (isActive) context.getString(R.string.active_status) else context.getString(R.string.inactive_status),
                        style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = if (isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
                }
                
                // Click indicator
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = context.getString(R.string.view_details),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddVolunteerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, Gender?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var abbreviation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<Gender?>(null) }
    var expandedGender by remember { mutableStateOf(false) }
    
    // Validation states
    var emailError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

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
                            text = if (isCompact) context.getString(R.string.add_volunteer) else context.getString(R.string.add_new_volunteer),
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
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(context.getString(R.string.full_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = abbreviation,
                            onValueChange = { abbreviation = it.uppercase() },
                            label = { Text(context.getString(R.string.last_name_abbreviation)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(context.getString(R.string.last_name_abbreviation_placeholder)) }
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { 
                                email = it
                                emailError = ValidationUtils.getEmailErrorMessage(it)
                            },
                            label = { Text(context.getString(R.string.email)) },
                            placeholder = { Text(context.getString(R.string.email_placeholder)) },
                            isError = emailError != null,
                            supportingText = emailError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(context.getString(R.string.phone_number)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        BirthdayDatePicker(
                            dateString = dateOfBirth,
                            onDateSelected = { 
                                dateOfBirth = it
                                dateError = ValidationUtils.getDateErrorMessage(it)
                            },
                            label = { Text(context.getString(R.string.date_of_birth)) },
                            placeholder = { Text(context.getString(R.string.date_of_birth_placeholder)) },
                            isError = dateError != null,
                            supportingText = dateError?.let { { Text(it) } }
                        )

                        // Gender Dropdown
                        @OptIn(ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = expandedGender,
                            onExpandedChange = { expandedGender = !expandedGender },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedGender?.let { gender ->
                                    when (gender) {
                                        Gender.FEMALE -> context.getString(R.string.gender_female)
                                        Gender.MALE -> context.getString(R.string.gender_male)
                                        Gender.NON_BINARY -> context.getString(R.string.gender_non_binary)
                                        Gender.OTHER -> context.getString(R.string.gender_other)
                                        Gender.PREFER_NOT_TO_DISCLOSE -> context.getString(R.string.gender_prefer_not_to_disclose)
                                    }
                                } ?: "",
                                onValueChange = { },
                                readOnly = true,
                                label = { Text(context.getString(R.string.gender)) },
                                placeholder = { Text(context.getString(R.string.select_gender)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGender) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expandedGender,
                                onDismissRequest = { expandedGender = false }
                            ) {
                                Gender.values().forEach { gender ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                when (gender) {
                                                    Gender.FEMALE -> "Female"
                                                    Gender.MALE -> "Male"
                                                    Gender.NON_BINARY -> "Non-binary"
                                                    Gender.OTHER -> "Other"
                                                    Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                                                }
                                            )
                                        },
                                        onClick = {
                                            selectedGender = gender
                                            expandedGender = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(context.getString(R.string.cancel))
                        }
                        
                        Button(
                            onClick = { 
                                val storageDate = ValidationUtils.convertDateToStorageFormat(dateOfBirth) ?: dateOfBirth
                                onConfirm(name, abbreviation, email, phone, storageDate, selectedGender) 
                            },
                            enabled = name.isNotBlank() && abbreviation.isNotBlank() && 
                                     email.isNotBlank() && phone.isNotBlank() && dateOfBirth.isNotBlank() &&
                                     emailError == null && dateError == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(context.getString(R.string.add_volunteer))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditVolunteerDialog(
    volunteer: Volunteer,
    onDismiss: () -> Unit,
    onConfirm: (Volunteer) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(volunteer.name) }
    var abbreviation by remember { mutableStateOf(volunteer.lastNameAbbreviation) }
    var email by remember { mutableStateOf(volunteer.email) }
    var phone by remember { mutableStateOf(volunteer.phoneNumber) }
    var dateOfBirth by remember { mutableStateOf(ValidationUtils.convertDateToDisplayFormat(volunteer.dateOfBirth)) }
    var selectedGender by remember { mutableStateOf(volunteer.gender) }
    var expandedGender by remember { mutableStateOf(false) }
    
    // Validation states
    var emailError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp),
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
                        text = if (isCompact) context.getString(R.string.edit_volunteer) else context.getString(R.string.edit_volunteer_details),
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
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(context.getString(R.string.full_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = abbreviation,
                        onValueChange = { abbreviation = it.uppercase() },
                        label = { Text(context.getString(R.string.last_name_abbreviation)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(context.getString(R.string.last_name_abbreviation_placeholder)) }
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = ValidationUtils.getEmailErrorMessage(it)
                        },
                        label = { Text(context.getString(R.string.email)) },
                        placeholder = { Text(context.getString(R.string.email_placeholder)) },
                        isError = emailError != null,
                        supportingText = emailError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(context.getString(R.string.phone_number)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    BirthdayDatePicker(
                        dateString = dateOfBirth,
                        onDateSelected = { 
                            dateOfBirth = it
                            dateError = ValidationUtils.getDateErrorMessage(it)
                        },
                        label = { Text(context.getString(R.string.date_of_birth)) },
                        placeholder = { Text(context.getString(R.string.date_of_birth_placeholder)) },
                        isError = dateError != null,
                        supportingText = dateError?.let { { Text(it) } }
                    )

                    // Gender Dropdown
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = expandedGender,
                        onExpandedChange = { expandedGender = !expandedGender },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedGender?.let { gender ->
                                when (gender) {
                                    Gender.FEMALE -> context.getString(R.string.gender_female)
                                    Gender.MALE -> context.getString(R.string.gender_male)
                                    Gender.NON_BINARY -> context.getString(R.string.gender_non_binary)
                                    Gender.OTHER -> context.getString(R.string.gender_other)
                                    Gender.PREFER_NOT_TO_DISCLOSE -> context.getString(R.string.gender_prefer_not_to_disclose)
                                }
                            } ?: "",
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(context.getString(R.string.gender)) },
                            placeholder = { Text(context.getString(R.string.select_gender)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGender) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expandedGender,
                            onDismissRequest = { expandedGender = false }
                        ) {
                            Gender.values().forEach { gender ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            when (gender) {
                                                Gender.FEMALE -> "Female"
                                                Gender.MALE -> "Male"
                                                Gender.NON_BINARY -> "Non-binary"
                                                Gender.OTHER -> "Other"
                                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                                            }
                                        )
                                    },
                                    onClick = {
                                        selectedGender = gender
                                        expandedGender = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.cancel))
                    }
                    
                    Button(
                        onClick = { 
                            val storageDate = ValidationUtils.convertDateToStorageFormat(dateOfBirth) ?: dateOfBirth
                            val updatedVolunteer = volunteer.copy(
                                name = name,
                                lastNameAbbreviation = abbreviation,
                                email = email,
                                phoneNumber = phone,
                                dateOfBirth = storageDate,
                                gender = selectedGender
                            )
                            onConfirm(updatedVolunteer)
                        },
                        enabled = name.isNotBlank() && abbreviation.isNotBlank() && 
                                 email.isNotBlank() && phone.isNotBlank() && dateOfBirth.isNotBlank() &&
                                 emailError == null && dateError == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.update_volunteer))
                    }
                }
            }
        }
    }
}

