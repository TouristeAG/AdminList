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
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.BenefitSystemType
import com.eventmanager.app.data.models.ManualRewards
import com.eventmanager.app.ui.components.SearchBarWithFilter
import androidx.compose.ui.res.stringResource
import com.eventmanager.app.R
import com.eventmanager.app.ui.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobTypeManagementScreen(
    jobTypeConfigs: List<JobTypeConfig>,
    onAddJobTypeConfig: (JobTypeConfig) -> Unit,
    onUpdateJobTypeConfig: (JobTypeConfig) -> Unit,
    onDeleteJobTypeConfig: (JobTypeConfig) -> Unit,
    onUpdateJobTypeConfigStatus: (Long, Boolean) -> Unit,
    onBack: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<JobTypeConfig?>(null) }
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
                        text = stringResource(R.string.shift_types_title),
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
                    Text(stringResource(R.string.add_shift_type))
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
                        text = stringResource(R.string.shift_type_management_title),
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
                    Text(stringResource(R.string.add_shift_type))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(responsiveSpacing))
        
        // Search and Filter Section
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = stringResource(R.string.search_shift_types_placeholder),
            filterOptions = listOf(stringResource(R.string.filter_shift_types), stringResource(R.string.filter_orion_types)),
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val filteredJobTypes = jobTypeConfigs.filter { config ->
            val matchesSearch = searchText.isEmpty() || 
                config.name.contains(searchText, ignoreCase = true) ||
                config.description.contains(searchText, ignoreCase = true)
            val matchesFilter = selectedFilter?.let { filter ->
                when (filter) {
                    stringResource(R.string.filter_shift_types) -> config.isShiftJob
                    stringResource(R.string.filter_orion_types) -> config.isOrionJob
                    else -> true
                }
            } ?: true
            matchesSearch && matchesFilter
        }
        
        Text(
            text = stringResource(R.string.shift_types_count, filteredJobTypes.size, jobTypeConfigs.size),
            style = getResponsiveBodyTypography(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
        
        // Shift types list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(filteredJobTypes) { config ->
                JobTypeConfigCard(
                    config = config,
                    onUpdate = { showEditDialog = config },
                    onDelete = onDeleteJobTypeConfig,
                    onToggleStatus = { isActive ->
                        onUpdateJobTypeConfigStatus(config.id, isActive)
                    }
                )
            }
        }
    }
    
    // Add Shift Type Dialog
    if (showAddDialog) {
        AddJobTypeConfigDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { config ->
                onAddJobTypeConfig(config)
                showAddDialog = false
            }
        )
    }
    
    // Edit Shift Type Dialog
    showEditDialog?.let { config ->
        EditJobTypeConfigDialog(
            config = config,
            onDismiss = { showEditDialog = null },
            onConfirm = { updatedConfig ->
                onUpdateJobTypeConfig(updatedConfig)
                showEditDialog = null
            }
        )
    }
}

@Composable
fun JobTypeConfigCard(
    config: JobTypeConfig,
    onUpdate: (JobTypeConfig) -> Unit,
    onDelete: (JobTypeConfig) -> Unit,
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
                        text = config.name,
                        style = getResponsiveTitleTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (config.description.isNotEmpty()) {
                        Text(
                            text = config.description,
                            style = getResponsiveBodyTypography(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Switch(
                    checked = config.isActive,
                    onCheckedChange = onToggleStatus
                )
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            
            // Configuration chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (config.isShiftJob) {
                    AssistChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.shift_job), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Work,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                
                if (config.isOrionJob) {
                    AssistChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.orion_job), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                
                if (config.requiresShiftTime) {
                    AssistChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.requires_time), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                
                // Benefit system type chip
                AssistChip(
                    onClick = { },
                    label = { 
                        Text(
                            if (config.benefitSystemType == BenefitSystemType.STELLAR) stringResource(R.string.stellar_benefits) else stringResource(R.string.manual_rewards),
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            if (config.benefitSystemType == BenefitSystemType.STELLAR) Icons.Default.Star else Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            
            // Manual rewards details (only shown for manual benefit system)
            if (config.benefitSystemType == BenefitSystemType.MANUAL && config.manualRewards != null) {
                val rewards = config.manualRewards
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manual_rewards_details),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val rewardsDetails = mutableListOf<String>()
                        if (rewards.durationDays > 0) rewardsDetails.add(stringResource(R.string.days_n, rewards.durationDays))
                        if (rewards.freeDrinks > 0) rewardsDetails.add(stringResource(R.string.free_drinks_n, rewards.freeDrinks))
                        if (rewards.barDiscountPercentage > 0) rewardsDetails.add(stringResource(R.string.bar_discount_n, rewards.barDiscountPercentage))
                        if (rewards.freeEntry) rewardsDetails.add(stringResource(R.string.free_entry))
                        if (rewards.invites > 0) rewardsDetails.add(stringResource(R.string.invites_n, rewards.invites))
                        if (rewards.otherNotes.isNotEmpty()) rewardsDetails.add(rewards.otherNotes)
                        
                        if (rewardsDetails.isNotEmpty()) {
                            Text(
                                text = rewardsDetails.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.no_rewards_configured),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            }
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUpdate(config) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }
                
                OutlinedButton(
                    onClick = { onDelete(config) },
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
fun AddJobTypeConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (JobTypeConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isShiftJob by remember { mutableStateOf(true) }
    var isOrionJob by remember { mutableStateOf(false) }
    var requiresShiftTime by remember { mutableStateOf(true) }
    var benefitSystemType by remember { mutableStateOf(BenefitSystemType.STELLAR) }
    var manualRewards by remember { mutableStateOf(ManualRewards()) }

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
                        text = if (isCompact) "Add Shift Type" else "Add New Shift Type",
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
                    label = { Text("Shift Type Name") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., Bar Staff") }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional description") }
                )

                // Benefit System Selection
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Benefit System",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Segmented Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp)
                        ) {
                            // Stellar Benefits Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (benefitSystemType == BenefitSystemType.STELLAR) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { benefitSystemType = BenefitSystemType.STELLAR }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Stellar Benefits",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (benefitSystemType == BenefitSystemType.STELLAR) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (benefitSystemType == BenefitSystemType.STELLAR) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                            
                            // Manual Rewards Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (benefitSystemType == BenefitSystemType.MANUAL) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { benefitSystemType = BenefitSystemType.MANUAL }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Manual Rewards",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (benefitSystemType == BenefitSystemType.MANUAL) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (benefitSystemType == BenefitSystemType.MANUAL) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        }
                    }
                }

                // Configuration based on selected benefit system
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (benefitSystemType == BenefitSystemType.STELLAR) "Shift Type Configuration" else "Manual Rewards Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (benefitSystemType == BenefitSystemType.STELLAR) {
                            // Stellar Benefits Configuration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Shift Job (Nova/Etoile/Galaxie)",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Switch(
                                    checked = isShiftJob,
                                    onCheckedChange = { isShiftJob = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Orion Job (Committee/Coordination)",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Switch(
                                    checked = isOrionJob,
                                    onCheckedChange = { isOrionJob = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Requires Shift Time",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Switch(
                                    checked = requiresShiftTime,
                                    onCheckedChange = { requiresShiftTime = it }
                                )
                            }
                        } else {
                            // Manual Rewards Configuration
                            // Duration in days
                            OutlinedTextField(
                                value = manualRewards.durationDays.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        durationDays = it.toIntOrNull() ?: 1
                                    )
                                },
                                label = { Text("Duration (days)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Free drinks
                            OutlinedTextField(
                                value = manualRewards.freeDrinks.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        freeDrinks = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Free Drinks") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Bar discount percentage
                            OutlinedTextField(
                                value = manualRewards.barDiscountPercentage.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        barDiscountPercentage = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Bar Discount (%)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Free entry checkbox
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Free Entry",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Switch(
                                    checked = manualRewards.freeEntry,
                                    onCheckedChange = { 
                                        manualRewards = manualRewards.copy(freeEntry = it)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Invites
                            OutlinedTextField(
                                value = manualRewards.invites.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        invites = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Number of Invites") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Other notes
                            OutlinedTextField(
                                value = manualRewards.otherNotes,
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(otherNotes = it)
                                },
                                label = { Text("Other Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Additional notes...") },
                                maxLines = 3
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
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val config = JobTypeConfig(
                                name = name,
                                description = description,
                                isShiftJob = isShiftJob,
                                isOrionJob = isOrionJob,
                                requiresShiftTime = requiresShiftTime,
                                benefitSystemType = benefitSystemType,
                                manualRewards = if (benefitSystemType == BenefitSystemType.MANUAL) manualRewards else null
                            )
                            onConfirm(config)
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.add_shift_type))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditJobTypeConfigDialog(
    config: JobTypeConfig,
    onDismiss: () -> Unit,
    onConfirm: (JobTypeConfig) -> Unit
) {
    var name by remember { mutableStateOf(config.name) }
    var description by remember { mutableStateOf(config.description) }
    var isShiftJob by remember { mutableStateOf(config.isShiftJob) }
    var isOrionJob by remember { mutableStateOf(config.isOrionJob) }
    var requiresShiftTime by remember { mutableStateOf(config.requiresShiftTime) }
    var benefitSystemType by remember { mutableStateOf(config.benefitSystemType) }
    var manualRewards by remember { mutableStateOf(config.manualRewards ?: ManualRewards()) }

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
                        text = if (isCompact) "Edit Shift Type" else "Edit Shift Type Configuration",
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
                    label = { Text("Shift Type Name") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., Bar Staff") }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional description") }
                )

                // Benefit System Selection
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Benefit System",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Segmented Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp)
                        ) {
                            // Stellar Benefits Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (benefitSystemType == BenefitSystemType.STELLAR) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { benefitSystemType = BenefitSystemType.STELLAR }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Stellar Benefits",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (benefitSystemType == BenefitSystemType.STELLAR) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (benefitSystemType == BenefitSystemType.STELLAR) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                            
                            // Manual Rewards Option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (benefitSystemType == BenefitSystemType.MANUAL) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { benefitSystemType = BenefitSystemType.MANUAL }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Manual Rewards",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (benefitSystemType == BenefitSystemType.MANUAL) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (benefitSystemType == BenefitSystemType.MANUAL) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        }
                    }
                }

                // Configuration based on selected benefit system
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (benefitSystemType == BenefitSystemType.STELLAR) "Shift Type Configuration" else "Manual Rewards Configuration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (benefitSystemType == BenefitSystemType.STELLAR) {
                            // Stellar Benefits Configuration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Shift Job (Nova/Etoile/Galaxie)")
                                Switch(
                                    checked = isShiftJob,
                                    onCheckedChange = { isShiftJob = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Orion Job (Committee/Coordination)")
                                Switch(
                                    checked = isOrionJob,
                                    onCheckedChange = { isOrionJob = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Requires Shift Time")
                                Switch(
                                    checked = requiresShiftTime,
                                    onCheckedChange = { requiresShiftTime = it }
                                )
                            }
                        } else {
                            // Manual Rewards Configuration
                            // Duration in days
                            OutlinedTextField(
                                value = manualRewards.durationDays.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        durationDays = it.toIntOrNull() ?: 1
                                    )
                                },
                                label = { Text("Duration (days)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Free drinks
                            OutlinedTextField(
                                value = manualRewards.freeDrinks.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        freeDrinks = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Free Drinks") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Bar discount percentage
                            OutlinedTextField(
                                value = manualRewards.barDiscountPercentage.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        barDiscountPercentage = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Bar Discount (%)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Free entry checkbox
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Free Entry")
                                Switch(
                                    checked = manualRewards.freeEntry,
                                    onCheckedChange = { 
                                        manualRewards = manualRewards.copy(freeEntry = it)
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Invites
                            OutlinedTextField(
                                value = manualRewards.invites.toString(),
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(
                                        invites = it.toIntOrNull() ?: 0
                                    )
                                },
                                label = { Text("Number of Invites") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Other notes
                            OutlinedTextField(
                                value = manualRewards.otherNotes,
                                onValueChange = { 
                                    manualRewards = manualRewards.copy(otherNotes = it)
                                },
                                label = { Text("Other Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Additional notes...") },
                                maxLines = 3
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
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val updatedConfig = config.copy(
                                name = name,
                                description = description,
                                isShiftJob = isShiftJob,
                                isOrionJob = isOrionJob,
                                requiresShiftTime = requiresShiftTime,
                                benefitSystemType = benefitSystemType,
                                manualRewards = if (benefitSystemType == BenefitSystemType.MANUAL) manualRewards else null
                            )
                            onConfirm(updatedConfig)
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Update Shift Type")
                    }
                }
            }
        }
    }
}
