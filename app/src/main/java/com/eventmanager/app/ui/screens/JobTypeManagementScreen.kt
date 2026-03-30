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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        
        // Memoize filter strings and options to avoid recomputation on every recomposition
        val filterShiftTypes = stringResource(R.string.filter_shift_types)
        val filterOrionTypes = stringResource(R.string.filter_orion_types)
        val filterOptions = remember(filterShiftTypes, filterOrionTypes) { 
            listOf(filterShiftTypes, filterOrionTypes) 
        }
        
        // Search and Filter Section
        SearchBarWithFilter(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            placeholder = stringResource(R.string.search_shift_types_placeholder),
            filterOptions = filterOptions,
            selectedFilter = selectedFilter,
            onFilterChange = { selectedFilter = it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Memoize filtered job types to avoid recalculating on every recomposition
        val filteredJobTypes = remember(jobTypeConfigs, searchText, selectedFilter, filterShiftTypes, filterOrionTypes) {
            jobTypeConfigs.filter { config ->
                val matchesSearch = searchText.isEmpty() || 
                    config.name.contains(searchText, ignoreCase = true) ||
                    config.description.contains(searchText, ignoreCase = true)
                val matchesFilter = selectedFilter?.let { filter ->
                    when (filter) {
                        filterShiftTypes -> config.isShiftJob
                        filterOrionTypes -> config.isOrionJob
                        else -> true
                    }
                } ?: true
                matchesSearch && matchesFilter
            }
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
            items(
                items = filteredJobTypes,
                key = { config -> config.id }
            ) { config ->
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
    val isTabletDevice = isTablet()
    val tabletMaxWidth = getTabletConstrainedDialogMaxWidth()
    val tabletMaxHeight = getTabletConstrainedDialogMaxHeight()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isTabletDevice)
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isTabletDevice) Modifier.widthIn(max = tabletMaxWidth).heightIn(max = tabletMaxHeight)
                    else Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                )
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                JobTypeDialogHeader(
                    title = if (isCompact) stringResource(R.string.add_shift_type_title_compact)
                            else stringResource(R.string.add_shift_type_title_full),
                    isTabletDevice = isTabletDevice,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                ) {
                    JobTypeDialogFields(
                        name = name,
                        onNameChange = { name = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        benefitSystemType = benefitSystemType,
                        onBenefitSystemTypeChange = { benefitSystemType = it },
                        isShiftJob = isShiftJob,
                        onShiftJobChange = { checked ->
                            isShiftJob = checked
                            if (checked) isOrionJob = false
                        },
                        isOrionJob = isOrionJob,
                        onOrionJobChange = { checked ->
                            isOrionJob = checked
                            if (checked) { isShiftJob = false; requiresShiftTime = false }
                        },
                        requiresShiftTime = requiresShiftTime,
                        onRequiresShiftTimeChange = { requiresShiftTime = it },
                        manualRewards = manualRewards,
                        onManualRewardsChange = { manualRewards = it },
                        isCompact = isCompact
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                JobTypeConfig(
                                    name = name,
                                    description = description,
                                    isShiftJob = isShiftJob,
                                    isOrionJob = isOrionJob,
                                    requiresShiftTime = requiresShiftTime,
                                    benefitSystemType = benefitSystemType,
                                    manualRewards = if (benefitSystemType == BenefitSystemType.MANUAL) manualRewards else null
                                )
                            )
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
    val isTabletDevice = isTablet()
    val tabletMaxWidth = getTabletConstrainedDialogMaxWidth()
    val tabletMaxHeight = getTabletConstrainedDialogMaxHeight()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isTabletDevice)
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isTabletDevice) Modifier.widthIn(max = tabletMaxWidth).heightIn(max = tabletMaxHeight)
                    else Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                )
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                JobTypeDialogHeader(
                    title = if (isCompact) stringResource(R.string.edit_shift_type_title_compact)
                            else stringResource(R.string.edit_shift_type_title_full),
                    isTabletDevice = isTabletDevice,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
                ) {
                    JobTypeDialogFields(
                        name = name,
                        onNameChange = { name = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        benefitSystemType = benefitSystemType,
                        onBenefitSystemTypeChange = { benefitSystemType = it },
                        isShiftJob = isShiftJob,
                        onShiftJobChange = { checked ->
                            isShiftJob = checked
                            if (checked) isOrionJob = false
                        },
                        isOrionJob = isOrionJob,
                        onOrionJobChange = { checked ->
                            isOrionJob = checked
                            if (checked) { isShiftJob = false; requiresShiftTime = false }
                        },
                        requiresShiftTime = requiresShiftTime,
                        onRequiresShiftTimeChange = { requiresShiftTime = it },
                        manualRewards = manualRewards,
                        onManualRewardsChange = { manualRewards = it },
                        isCompact = isCompact
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onConfirm(
                                config.copy(
                                    name = name,
                                    description = description,
                                    isShiftJob = isShiftJob,
                                    isOrionJob = isOrionJob,
                                    requiresShiftTime = requiresShiftTime,
                                    benefitSystemType = benefitSystemType,
                                    manualRewards = if (benefitSystemType == BenefitSystemType.MANUAL) manualRewards else null
                                )
                            )
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.update_shift_type))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared private helpers used by both Add and Edit dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun JobTypeDialogHeader(
    title: String,
    isTabletDevice: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (isTabletDevice) getTabletConstrainedTitleTypography() else getResponsiveTypography(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
    }
}

@Composable
private fun JobTypeDialogFields(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    benefitSystemType: BenefitSystemType,
    onBenefitSystemTypeChange: (BenefitSystemType) -> Unit,
    isShiftJob: Boolean,
    onShiftJobChange: (Boolean) -> Unit,
    isOrionJob: Boolean,
    onOrionJobChange: (Boolean) -> Unit,
    requiresShiftTime: Boolean,
    onRequiresShiftTimeChange: (Boolean) -> Unit,
    manualRewards: ManualRewards,
    onManualRewardsChange: (ManualRewards) -> Unit,
    isCompact: Boolean
) {
    var showStellarInfoDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.shift_type_name_label)) },
        placeholder = { Text(stringResource(R.string.shift_type_name_placeholder)) },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text(stringResource(R.string.description_label)) },
        placeholder = { Text(stringResource(R.string.description_placeholder)) },
        modifier = Modifier.fillMaxWidth()
    )

    // Benefit System Selection card
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.benefit_system_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showStellarInfoDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.HelpOutline,
                        contentDescription = stringResource(R.string.stellar_benefits_info_title),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                BenefitSystemTab(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(R.string.stellar_benefits),
                    selected = benefitSystemType == BenefitSystemType.STELLAR,
                    onClick = { onBenefitSystemTypeChange(BenefitSystemType.STELLAR) }
                )
                BenefitSystemTab(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(R.string.manual_rewards),
                    selected = benefitSystemType == BenefitSystemType.MANUAL,
                    onClick = { onBenefitSystemTypeChange(BenefitSystemType.MANUAL) }
                )
            }
        }
    }

    // Configuration card
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (benefitSystemType == BenefitSystemType.STELLAR)
                    stringResource(R.string.stellar_config_label)
                else
                    stringResource(R.string.manual_rewards_config_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (benefitSystemType == BenefitSystemType.STELLAR) {
                SwitchRow(
                    label = stringResource(R.string.shift_job_label),
                    checked = isShiftJob,
                    onCheckedChange = onShiftJobChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                SwitchRow(
                    label = stringResource(R.string.orion_job_label),
                    checked = isOrionJob,
                    onCheckedChange = onOrionJobChange
                )

                // requiresShiftTime is only relevant when isShiftJob is true
                if (isShiftJob) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        label = stringResource(R.string.requires_shift_time_label),
                        checked = requiresShiftTime,
                        onCheckedChange = onRequiresShiftTimeChange
                    )
                }
            } else {
                NumberField(
                    value = manualRewards.durationDays,
                    label = stringResource(R.string.duration_days_label),
                    onValueChange = { onManualRewardsChange(manualRewards.copy(durationDays = it ?: 1)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                NumberField(
                    value = manualRewards.freeDrinks,
                    label = stringResource(R.string.free_drinks_label),
                    onValueChange = { onManualRewardsChange(manualRewards.copy(freeDrinks = it ?: 0)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                NumberField(
                    value = manualRewards.barDiscountPercentage,
                    label = stringResource(R.string.bar_discount_percent_label),
                    onValueChange = { onManualRewardsChange(manualRewards.copy(barDiscountPercentage = it ?: 0)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow(
                    label = stringResource(R.string.free_entry_label),
                    checked = manualRewards.freeEntry,
                    onCheckedChange = { onManualRewardsChange(manualRewards.copy(freeEntry = it)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                NumberField(
                    value = manualRewards.invites,
                    label = stringResource(R.string.number_of_invites_label),
                    onValueChange = { onManualRewardsChange(manualRewards.copy(invites = it ?: 0)) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualRewards.otherNotes,
                    onValueChange = { onManualRewardsChange(manualRewards.copy(otherNotes = it)) },
                    label = { Text(stringResource(R.string.other_notes_label)) },
                    placeholder = { Text(stringResource(R.string.other_notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }
    }

    if (showStellarInfoDialog) {
        AlertDialog(
            onDismissRequest = { showStellarInfoDialog = false },
            icon = {
                Icon(
                    Icons.Default.Stars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.stellar_benefits_info_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.stellar_benefits_info_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStellarInfoDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun BenefitSystemTab(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberField(value: Int, label: String, onValueChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onValueChange(it.toIntOrNull()) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    )
}
