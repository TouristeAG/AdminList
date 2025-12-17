package com.eventmanager.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.ui.components.SearchBarWithFilter
import androidx.compose.ui.res.stringResource
import com.eventmanager.app.R
import com.eventmanager.app.ui.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueManagementScreen(
    venues: List<VenueEntity>,
    onAddVenue: (VenueEntity) -> Unit,
    onUpdateVenue: (VenueEntity) -> Unit,
    onDeleteVenue: (VenueEntity) -> Unit,
    onUpdateVenueStatus: (Long, Boolean) -> Unit,
    onBack: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<VenueEntity?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val isCompact = isCompactScreen()
    val responsivePadding = getResponsivePadding()
    val responsiveSpacing = getResponsiveSpacing()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.venues_title),
                        style = getResponsiveTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(getResponsiveButtonHeight())
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_venue))
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.venue_management_title),
                        style = getResponsiveTypography(),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(getResponsiveButtonHeight())
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_venue))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(responsiveSpacing))
        
        // Search and Filter Section
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = stringResource(R.string.search_venues_placeholder),
            filterOptions = listOf(stringResource(R.string.filter_active), stringResource(R.string.filter_inactive)),
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Get string resources outside of remember block
        val filterActiveText = stringResource(R.string.filter_active)
        val filterInactiveText = stringResource(R.string.filter_inactive)
        
        // Memoize filtered venues to avoid recalculating on every recomposition
        val filteredVenues = remember(venues, searchText, selectedFilter, filterActiveText, filterInactiveText) {
            val lowerSearchText = searchText.lowercase()
            venues.filter { venue ->
                val matchesSearch = searchText.isEmpty() || 
                    venue.name.lowercase().contains(lowerSearchText) ||
                    venue.description.lowercase().contains(lowerSearchText)
                val matchesFilter = selectedFilter?.let { filter ->
                    when (filter) {
                        filterActiveText -> venue.isActive
                        filterInactiveText -> !venue.isActive
                        else -> true
                    }
                } ?: true
                matchesSearch && matchesFilter
            }
        }
        
        Text(
            text = stringResource(R.string.venues_count, filteredVenues.size, venues.size),
            style = getResponsiveBodyTypography(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
        
        // Venues list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(filteredVenues) { venue ->
                VenueConfigCard(
                    venue = venue,
                    onUpdate = { showEditDialog = venue },
                    onDelete = onDeleteVenue,
                    onToggleStatus = { isActive ->
                        onUpdateVenueStatus(venue.id, isActive)
                    }
                )
            }
        }
    }
    
    // Add Venue Dialog
    if (showAddDialog) {
        AddVenueConfigDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { venue ->
                onAddVenue(venue)
                showAddDialog = false
            }
        )
    }
    
    // Edit Venue Dialog
    showEditDialog?.let { venue ->
        EditVenueConfigDialog(
            venue = venue,
            onDismiss = { showEditDialog = null },
            onConfirm = { updatedVenue ->
                onUpdateVenue(updatedVenue)
                showEditDialog = null
            }
        )
    }
}

@Composable
fun VenueConfigCard(
    venue: VenueEntity,
    onUpdate: (VenueEntity) -> Unit,
    onDelete: (VenueEntity) -> Unit,
    onToggleStatus: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompact = isCompactScreen()
    val responsivePadding = getResponsiveCardPadding()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = getResponsiveCardElevation())
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            // Header with name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = venue.name,
                        style = getResponsiveTitleTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (venue.description.isNotEmpty()) {
                        Text(
                            text = venue.description,
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Switch(
                    checked = venue.isActive,
                    onCheckedChange = onToggleStatus
                )
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            
            // Status chip
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            if (venue.isActive) stringResource(R.string.venue_active) else stringResource(R.string.venue_inactive),
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            if (venue.isActive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdate(venue) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }
                
                OutlinedButton(
                    onClick = { onDelete(venue) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVenueConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (VenueEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()

    // Custom Dialog with proper scrolling
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
                            text = if (isCompact) stringResource(R.string.add_venue) else stringResource(R.string.add_new_venue),
                            style = getResponsiveTypography(),
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
                            label = { Text(stringResource(R.string.venue_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.venue_name_placeholder)) }
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.venue_description)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.venue_description_placeholder)) },
                            maxLines = 3
                        )
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
                            Text(stringResource(R.string.cancel))
                        }
                        
                            Button(
                                onClick = {
                                    val venue = VenueEntity(
                                        name = name,
                                        description = description
                                    )
                                    onConfirm(venue)
                                },
                                enabled = name.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.add_venue))
                            }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVenueConfigDialog(
    venue: VenueEntity,
    onDismiss: () -> Unit,
    onConfirm: (VenueEntity) -> Unit
) {
    var name by remember { mutableStateOf(venue.name) }
    var description by remember { mutableStateOf(venue.description) }

    val isCompact = isCompactScreen()
    val scrollState = rememberScrollState()

    // Custom Dialog with proper scrolling
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
                        text = if (isCompact) stringResource(R.string.edit) else stringResource(R.string.edit_venue_configuration),
                        style = getResponsiveTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
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
                        label = { Text(stringResource(R.string.venue_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.venue_name_placeholder)) }
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.venue_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.venue_description_placeholder)) },
                        maxLines = 3
                    )
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
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Button(
                        onClick = {
                            val updatedVenue = venue.copy(
                                name = name,
                                description = description
                            )
                            onConfirm(updatedVenue)
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.update_venue))
                    }
                }
            }
        }
    }
}
